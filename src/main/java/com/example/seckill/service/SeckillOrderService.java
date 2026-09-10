package com.example.seckill.service;

import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.mq.SeckillMessage;

/**
 * 秒杀订单服务接口（DB 层：异步落库 / 超时取消）
 */
public interface SeckillOrderService {

    /**
     * 异步消费者调用：真正落库创建秒杀订单
     *
     * <p>事务内顺序：查重(orderNo 幂等) -> 校验活动/商品 -> DB 条件扣库存(兜底防超卖)
     * -> INSERT 订单。DB 扣减失败会抛业务异常，由监听器负责回补 Redis。
     *
     * @param message 秒杀消息
     * @return 已落库的订单（含自增主键 orderId）
     */
    SeckillOrder createOrder(SeckillMessage message);

    /**
     * 超时未支付取消（CAS）：仅当订单仍为"待支付"才允许取消并回滚 DB 库存
     *
     * @param orderId 订单ID
     * @return 取消成功返回订单（调用方据此回滚 Redis 库存）；已支付/已取消/不存在返回 null
     */
    SeckillOrder cancelIfUnpaid(Long orderId);

    /**
     * 查询 DB 剩余库存（Redis/DB 不一致时校准用）
     */
    Integer getDbStock(Long activityId);
}