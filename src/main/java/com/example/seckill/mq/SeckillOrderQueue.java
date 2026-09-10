package com.example.seckill.mq;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 【已废弃】阶段二的内存版秒杀订单队列 —— 仅保留作为对照参考，不注册为 Spring Bean。
 *
 * <p>为什么废弃：消息在 JVM 内存中，应用重启即丢失、无法多实例水平扩容；
 * 阶段三起已切换为 RabbitMQ（{@link RabbitMQConfig}），支持持久化/死信/多消费者。
 *
 * @deprecated 由 RabbitMQ 方案取代，仅作学习对照
 */
@Deprecated
public class SeckillOrderQueue {

    /** 队列容量 */
    private static final int CAPACITY = 10_000;

    private final BlockingQueue<SeckillMessage> queue = new LinkedBlockingQueue<>(CAPACITY);

    /** 非阻塞入队：队列满立即返回 false */
    public boolean offer(SeckillMessage message) {
        return queue.offer(message);
    }

    /** 阻塞取出：队列为空时消费者线程在此等待 */
    public SeckillMessage take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }
}