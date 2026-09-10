package com.example.seckill.mq;

import com.example.seckill.common.api.ResultCode;
import com.example.seckill.common.exception.BusinessException;
import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.service.RedisSeckillService;
import com.example.seckill.service.SeckillOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 下单消息消费者（RabbitMQ 监听器）——真正把订单写入 DB
 *
 * <p><b>可靠性设计：</b>
 * <ul>
 *   <li>自动 ack：方法正常返回 = 消息确认；抛异常 = 重试(3次)后进入死信队列；
 *   <li>幂等：DB 按 order_no 判重，MQ 重复投递不会产生重复订单；
 *   <li>补偿：Redis 已预扣、但 DB 成单失败时，回补 Redis 库存 + 释放用户资格，
 *       防止库存/资格"凭空蒸发"（下单唯一"允许 Redis 与 DB 短暂不一致再自愈"的地方）。
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderCreateListener {

    private final SeckillOrderService orderService;
    private final RedisSeckillService redisSeckillService;
    private final SeckillOrderTimeoutProducer timeoutProducer;

    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    public void onCreate(SeckillMessage message) {
        try {
            // ① DB 事务内：判重 -> 校验 -> DB 扣库存 -> INSERT 订单
            SeckillOrder order = orderService.createOrder(message);

            // ② 下单成功：投递 15 分钟延时消息，超时未支付则自动取消
            timeoutProducer.send(new SeckillOrderTimeoutMessage(
                    order.getId(), order.getOrderNo(),
                    order.getActivityId(), order.getUserId()));
        } catch (DuplicateKeyException e) {
            // uk_user_activity / uk_order_no 冲突：说明订单已存在，幂等 ACK（不补偿、不重试）
            log.warn("[下单] 检测到重复订单，幂等忽略 orderNo={}, userId={}, activityId={}",
                    message.orderNo(), message.userId(), message.activityId());
        } catch (BusinessException e) {
            // Redis 已预扣、DB 成单失败：必须回补，防止库存蒸发
            if (e.getCode() == ResultCode.SOLD_OUT.getCode()) {
                // DB 库存不足（Redis/DB 不一致）：以 DB 为准校准 Redis 库存（自愈）
                Integer dbStock = orderService.getDbStock(message.activityId());
                redisSeckillService.resetStock(message.activityId(),
                        dbStock == null ? 0 : dbStock);
            } else {
                // 活动/商品不存在等：回补 +1（DB 尚未扣减，一一对应回补）
                redisSeckillService.rollbackStock(message.activityId());
            }
            redisSeckillService.releaseUserQuota(message.userId(), message.activityId());
            log.error("[下单] DB 成单失败，已回补库存并释放资格 activityId={}, orderNo={}, code={}, msg={}",
                    message.activityId(), message.orderNo(), e.getCode(), e.getMessage());
        } catch (Exception e) {
            // 未知异常（如 DB 瞬时抖动）：抛出 -> 重试 3 次 -> 进入死信队列人工处理
            log.error("[下单] 未知异常 orderNo={}", message.orderNo(), e);
            throw new AmqpRejectAndDontRequeueException("下单失败，进入死信队列", e);
        }
    }
}