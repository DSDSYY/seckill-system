package com.example.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.seckill.common.api.ResultCode;
import com.example.seckill.common.constant.OrderStatus;
import com.example.seckill.common.exception.BusinessException;
import com.example.seckill.entity.Product;
import com.example.seckill.entity.SeckillActivity;
import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.mapper.ProductMapper;
import com.example.seckill.mapper.SeckillActivityMapper;
import com.example.seckill.mapper.SeckillOrderMapper;
import com.example.seckill.mq.SeckillMessage;
import com.example.seckill.service.SeckillOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 秒杀订单服务实现（DB 层）
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@code createOrder} 在"同一个事务"里完成 DB 扣库存 + 插订单，
 *       任一失败整体回滚——这是数据库层防超卖/防不一致的最终兜底；
 *   <li>订单冗余商品名/价格快照（下单那一刻的值），与活动/商品解耦；
 *   <li>{@code cancelIfUnpaid} 用 CAS（status=0 -> 2）更新，杜绝与"支付"并发的竞态。
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderServiceImpl implements SeckillOrderService {

    private final SeckillOrderMapper orderMapper;
    private final SeckillActivityMapper activityMapper;
    private final ProductMapper productMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillOrder createOrder(SeckillMessage message) {
        // ---------- ① 幂等：订单号已存在则直接返回 ----------
        // MQ 是 at-least-once 投递，消费者可能收到重复消息（如消费后 ack 前宕机）
        SeckillOrder exists = orderMapper.selectOne(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getOrderNo, message.orderNo()));
        if (exists != null) {
            log.info("[下单] 订单已存在，幂等返回 orderNo={}", message.orderNo());
            return exists;
        }

        // ---------- ② 读取活动/商品（生成订单快照）----------
        SeckillActivity activity = activityMapper.selectById(message.activityId());
        if (activity == null) {
            throw new BusinessException(ResultCode.ACTIVITY_NOT_FOUND);
        }
        Product product = productMapper.selectById(activity.getProductId());
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // ---------- ③ DB 兜底扣库存（条件更新，防超卖最终防线）----------
        // Redis 预扣在请求链路已完成；这里保证 DB 与 Redis 最终一致。
        // 影响行数 = 0 说明 DB 库存不足（理论上仅发生在 Redis/DB 不一致时），抛异常触发回滚。
        int rows = activityMapper.deductStock(message.activityId());
        if (rows == 0) {
            throw new BusinessException(ResultCode.SOLD_OUT, "数据库库存不足");
        }

        // ---------- ④ INSERT 秒杀订单（唯一索引 uk_user_activity 兜底一人一单）----------
        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(message.orderNo());
        order.setUserId(message.userId());
        order.setActivityId(message.activityId());
        order.setProductId(activity.getProductId());
        order.setProductName(product.getProductName());      // 名称快照
        order.setSeckillPrice(activity.getSeckillPrice());   // 价格快照
        order.setQuantity(1);
        order.setStatus(OrderStatus.PENDING_PAY);            // 待支付
        orderMapper.insert(order);

        log.info("[下单] 订单落库成功 orderNo={}, userId={}, activityId={}, orderId={}",
                order.getOrderNo(), order.getUserId(), order.getActivityId(), order.getId());
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillOrder cancelIfUnpaid(Long orderId) {
        SeckillOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("[取消订单] 订单不存在 orderId={}", orderId);
            return null;
        }

        // CAS：只有"待支付(0)"才能被取消，防止与"已支付(1)"并发时误取消
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<SeckillOrder>()
                .set(SeckillOrder::getStatus, OrderStatus.CANCELED)
                .set(SeckillOrder::getUpdateTime, LocalDateTime.now())
                .eq(SeckillOrder::getId, orderId)
                .eq(SeckillOrder::getStatus, OrderStatus.PENDING_PAY)
                .eq(SeckillOrder::getDeleted, 0));
        if (rows == 0) {
            log.info("[取消订单] 订单已支付或已取消，跳过 orderId={}", orderId);
            return null;   // 幂等：不重复回滚库存
        }

        // 下单时 DB 也扣过库存，取消必须一并回滚，保证 DB 与 Redis 一致
        activityMapper.rollbackStock(order.getActivityId());
        log.info("[取消订单] 订单已取消 orderId={}, orderNo={}", orderId, order.getOrderNo());
        return order;
    }

    @Override
    public Integer getDbStock(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        return activity == null ? null : activity.getSeckillStock();
    }
}