package com.example.seckill.service.impl;

import com.example.seckill.common.api.ResultCode;
import com.example.seckill.common.exception.BusinessException;
import com.example.seckill.common.util.SnowflakeIdGenerator;
import com.example.seckill.entity.SeckillActivity;
import com.example.seckill.mapper.SeckillActivityMapper;
import com.example.seckill.mq.SeckillMessage;
import com.example.seckill.mq.SeckillMessageProducer;
import com.example.seckill.service.RedisSeckillService;
import com.example.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 秒杀核心业务实现（阶段三：SETNX 一人一单 + Lua 预扣 + RabbitMQ 异步落库）
 *
 * <p>请求入口链路：
 * <pre>
 *  ① 活动合法性校验（时间窗+状态）
 *  ② SETNX user:activity:{userId}:{activityId}   ← 一人一单(Redis前置防线)
 *  ③ Lua 原子扣减 seckill:stock:{id}            ← 防超卖闸门
 *  ④ 雪花算法生成临时订单号
 *  ⑤ 投递 RabbitMQ
 *      └ 失败 → 回补 Redis 库存 + 释放用户资格（关键：防止库存/资格"蒸发"）
 * </pre>
 * 真正的 DB 落库在 {@code SeckillOrderCreateListener}（异步消费者）中完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final SeckillActivityMapper activityMapper;
    private final RedisSeckillService redisSeckillService;
    private final SeckillMessageProducer messageProducer;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public boolean initStock(Long activityId) {
        // ① 从 DB 读取活动（阶段四预热定时任务会改为批量扫描"即将开始"的活动）
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_FOUND);
        }

        // ② 只允许对"未结束/未下架"的活动预热
        boolean ended = activity.getEndTime() != null
                && LocalDateTime.now().isAfter(activity.getEndTime());
        boolean offShelf = Integer.valueOf(2).equals(activity.getStatus())
                || Integer.valueOf(3).equals(activity.getStatus());
        if (ended || offShelf) {
            throw new BusinessException(ResultCode.ENDED);
        }

        // ③ 写入 Redis（覆盖写 = 可重复执行；仅限活动开始前/管理端调用）
        redisSeckillService.initStock(activityId, activity.getSeckillStock());
        log.info("[库存预热] activityId={}, stock={}", activityId, activity.getSeckillStock());
        return true;
    }

    @Override
    public String doSeckill(Long userId, Long activityId) {
        // ---------- ① 活动合法性校验（时间窗 + 状态）----------
        // 说明：阶段三仍查库便于演示；高并发热路径不能每次查库，
        // 阶段四将活动元数据预热到 Redis/本地缓存，这里改为读缓存校验。
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
            throw new BusinessException(ResultCode.NOT_START);
        }
        if ((activity.getEndTime() != null && now.isAfter(activity.getEndTime()))
                || Integer.valueOf(2).equals(activity.getStatus())
                || Integer.valueOf(3).equals(activity.getStatus())) {
            throw new BusinessException(ResultCode.ENDED);
        }

        // ---------- ② 一人一单：SETNX 获取用户资格 ----------
        // Key: user:activity:{userId}:{activityId}
        // TTL = 活动剩余时长：活动结束后自动过期，无需手动清理
        long ttlSeconds = activity.getEndTime() == null
                ? Duration.ofMinutes(15).getSeconds()   // 兜底：endTime 为空给 15 分钟
                : Math.max(1, Duration.between(now, activity.getEndTime()).getSeconds());
        boolean acquired = redisSeckillService.tryAcquireUserQuota(
                userId, activityId, Duration.ofSeconds(ttlSeconds));
        if (!acquired) {
            throw new BusinessException(ResultCode.REPEAT_SUBMIT);
        }

        // ---------- ③ Redis Lua 原子扣减 ----------
        if (!redisSeckillService.deductStock(activityId)) {
            // 库存不足：释放资格，允许用户稍后重试（如有人超时取消回滚库存）
            redisSeckillService.releaseUserQuota(userId, activityId);
            throw new BusinessException(ResultCode.SOLD_OUT);
        }

        // ---------- ④ 生成临时订单号 ----------
        String orderNo = String.valueOf(snowflakeIdGenerator.nextId());

        // ---------- ⑤ 投递 RabbitMQ，等待异步落库 ----------
        SeckillMessage message = new SeckillMessage(orderNo, userId, activityId, activity.getProductId());
        boolean sent = messageProducer.send(message);
        if (!sent) {
            // 投递失败（如 MQ 不可用）：回补库存 + 释放资格，让用户稍后可重试
            redisSeckillService.rollbackStock(activityId);
            redisSeckillService.releaseUserQuota(userId, activityId);
            log.warn("[秒杀] MQ 投递失败，已回补库存并释放资格 activityId={}, orderNo={}",
                    activityId, orderNo);
            throw new BusinessException(ResultCode.QUEUE_FULL);
        }

        log.info("[秒杀] 扣减成功 userId={}, activityId={}, orderNo={}", userId, activityId, orderNo);
        return orderNo;
    }

    @Override
    public Integer getRemainingStock(Long activityId) {
        return redisSeckillService.getRemainingStock(activityId);
    }
}