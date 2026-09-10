package com.example.seckill.mq;

/**
 * 秒杀消息生产者（策略/依赖倒置）
 *
 * <p>业务只依赖该接口：
 * <ul>
 *   <li>阶段二：BlockingQueueSeckillMessageProducer（内存队列，已废弃仅作对照）；
 *   <li>阶段三起：{@link com.example.seckill.mq.impl.RabbitMQSeckillMessageProducer}（RabbitMQ）。
 * </ul>
 * 切换实现时 Service 层零改动 —— 这就是"面向接口编程"的价值。
 */
public interface SeckillMessageProducer {

    /**
     * 发送秒杀消息
     *
     * @return true 发送成功；false 发送失败（调用方必须回补 Redis 库存并释放用户资格）
     */
    boolean send(SeckillMessage message);
}