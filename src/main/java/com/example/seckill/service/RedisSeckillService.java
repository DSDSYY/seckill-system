package com.example.seckill.service;

import com.example.seckill.common.constant.RedisKeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

/**
 * Redis 秒杀库存/资格服务（基础设施层，被请求链路与消费/取消链路复用）
 *
 * <p>职责：
 * <ul>
 *   <li>库存预热、Lua 原子扣减、库存回补/校准；
 *   <li>用户资格 SETNX（一人一单的 Redis 前置防线）。
 * </ul>
 * 把 Redis 操作收敛到这一个类，避免"扣库存/回补"逻辑在多个 Service 里重复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSeckillService {

    private final StringRedisTemplate stringRedisTemplate;

    /** Lua 扣减脚本：类加载时一次性加载，避免每次执行都读磁盘 */
    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>();

    static {
        DEDUCT_SCRIPT.setLocation(new ClassPathResource("lua/seckill_deduct.lua"));
        DEDUCT_SCRIPT.setResultType(Long.class);
    }

    /** 预热/重置库存：写入剩余库存（Key: seckill:stock:{id}） */
    public void initStock(Long activityId, int stock) {
        stringRedisTemplate.opsForValue()
                .set(RedisKeyConstant.seckillStock(activityId), String.valueOf(stock));
    }

    /**
     * Lua 原子扣减库存
     *
     * @return true=扣减成功；false=库存不足/未预热
     */
    public boolean deductStock(Long activityId) {
        Long result = stringRedisTemplate.execute(
                DEDUCT_SCRIPT,
                Collections.singletonList(RedisKeyConstant.seckillStock(activityId)),  // KEYS[1]
                "1"                                                                   // ARGV[1]
        );
        return result != null && result == 1L;
    }

    /** 回补库存 +1（MQ 投递失败 / 超时取消时调用，与扣减一一对应） */
    public void rollbackStock(Long activityId) {
        stringRedisTemplate.opsForValue()
                .increment(RedisKeyConstant.seckillStock(activityId), 1);
    }

    /** 以 DB 为准校准 Redis 库存（Redis/DB 不一致时自愈） */
    public void resetStock(Long activityId, int stock) {
        initStock(activityId, stock);
        log.warn("[库存校准] activityId={} 已重置为 DB 值 stock={}", activityId, stock);
    }

    /** 查询剩余库存（测试/联调） */
    public Integer getRemainingStock(Long activityId) {
        String value = stringRedisTemplate.opsForValue()
                .get(RedisKeyConstant.seckillStock(activityId));
        return value == null ? null : Integer.valueOf(value);
    }

    /**
     * 用户资格 SETNX：同活动一人一单（防重复下单的 Redis 前置防线）
     * Key: user:activity:{userId}:{activityId}，TTL=活动剩余时长
     *
     * @return true=首次抢购，资格获取成功；false=已参与过/正在处理
     */
    public boolean tryAcquireUserQuota(Long userId, Long activityId, Duration ttl) {
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeyConstant.seckillUserActivity(userId, activityId),
                "1",
                ttl);
        return Boolean.TRUE.equals(ok);
    }

    /** 释放用户资格（扣减失败/投递失败时调用，允许用户稍后重试） */
    public void releaseUserQuota(Long userId, Long activityId) {
        stringRedisTemplate.delete(RedisKeyConstant.seckillUserActivity(userId, activityId));
    }
}