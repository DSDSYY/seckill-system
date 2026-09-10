package com.example.seckill.mq;

/**
 * 【已废弃】阶段二的内存队列消费者 —— 仅保留作为对照参考，不注册为 Spring Bean。
 *
 * <p>真实"异步落库"逻辑已迁移到 {@link SeckillOrderCreateListener}（RabbitMQ 监听器），
 * 支持消息持久化不丢失、失败重试、死信队列、多实例水平扩容。
 *
 * @deprecated 由 RabbitMQ 监听器取代
 */
@Deprecated
public class SeckillOrderConsumer {

    private final SeckillOrderQueue orderQueue;

    public SeckillOrderConsumer(SeckillOrderQueue orderQueue) {
        this.orderQueue = orderQueue;
    }

    /**
     * 已停用：真正落库请使用 {@link SeckillOrderCreateListener}。
     */
    @Deprecated
    public void consume() {
        // 阶段二参考实现；当前订单落库由 RabbitMQ 监听器负责
    }
}