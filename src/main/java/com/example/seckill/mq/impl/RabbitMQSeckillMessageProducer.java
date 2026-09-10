package com.example.seckill.mq.impl;

import com.example.seckill.mq.RabbitMQConfig;
import com.example.seckill.mq.SeckillMessage;
import com.example.seckill.mq.SeckillMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 秒杀消息生产者（阶段三起使用）
 *
 * <p>相比阶段二的内存队列：
 * <ul>
 *   <li>消息持久化：broker/应用重启不丢消息；
 *   <li>支持多实例水平扩容消费；
 *   <li>配合死信队列，失败消息可追踪、可人工处理。
 * </ul>
 *
 * <p>注意：本实现是"尽力投递"。生产环境应开启 publisher-confirm（发布确认），
 * 在回调中处理确认失败的消息（重发/落本地表），这里返回 false 由调用方回补库存兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQSeckillMessageProducer implements SeckillMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public boolean send(SeckillMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ORDER_EXCHANGE,
                    RabbitMQConfig.ROUTING_CREATE,
                    message);
            return true;
        } catch (Exception e) {
            // MQ 连接异常/不可用：返回 false，由调用方"回补 Redis 库存 + 释放用户资格"
            log.error("[秒杀] 消息发送失败 orderNo={}", message.orderNo(), e);
            return false;
        }
    }
}