package com.example.seckill.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单超时消息生产者：把"待取消"任务投递到延时队列
 *
 * <p>延时实现：消息进入 delay 队列后不消费，等 TTL 到期由 broker 自动转入死信(cancel)队列，
 * 从而"15 分钟后触发取消"，无需自己写定时器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderTimeoutProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(SeckillOrderTimeoutMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ORDER_EXCHANGE,
                    RabbitMQConfig.ROUTING_DELAY,
                    message);
            log.info("[延时消息] 已投递 15 分钟延时取消 orderNo={}, orderId={}",
                    message.orderNo(), message.orderId());
        } catch (Exception e) {
            // 投递失败：该订单将不会被自动取消。
            // 生产环境必须配"DB 定时扫描超时未支付订单"作为兜底（双保险），这里先记录告警。
            log.error("[延时消息] 投递失败，需兜底扫描 orderNo={}", message.orderNo(), e);
        }
    }
}