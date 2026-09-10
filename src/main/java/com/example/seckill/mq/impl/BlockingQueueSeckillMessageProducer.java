package com.example.seckill.mq.impl;

import com.example.seckill.mq.SeckillMessage;
import com.example.seckill.mq.SeckillMessageProducer;
import com.example.seckill.mq.SeckillOrderQueue;

/**
 * 【已废弃】阶段二的内存阻塞队列生产者 —— 仅保留作为对照参考，不注册为 Spring Bean。
 *
 * <p>已由 {@link RabbitMQSeckillMessageProducer} 取代（消息持久化、死信、多实例）。
 * 本类不再注册为 Bean，避免与 RabbitMQ 生产者造成依赖歧义。
 *
 * @deprecated 由 RabbitMQ 实现取代
 */
@Deprecated
public class BlockingQueueSeckillMessageProducer implements SeckillMessageProducer {

    private final SeckillOrderQueue orderQueue;

    public BlockingQueueSeckillMessageProducer(SeckillOrderQueue orderQueue) {
        this.orderQueue = orderQueue;
    }

    @Override
    public boolean send(SeckillMessage message) {
        return orderQueue.offer(message);
    }
}