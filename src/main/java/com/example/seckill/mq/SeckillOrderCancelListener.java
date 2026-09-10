package com.example.seckill.mq;

import com.example.seckill.entity.SeckillOrder;
import com.example.seckill.service.RedisSeckillService;
import com.example.seckill.service.SeckillOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 超时未支付取消监听器
 *
 * <p>延时队列中的消息躺满 15 分钟后，由 broker 自动转入 cancel 队列触发本监听器：
 * <ol>
 *   <li>DB：CAS 把"待支付(0)"订单置为"已取消(2)"，并回滚 DB 库存（同一事务）；
 *   <li>Redis：取消成功后再回滚预扣库存（DB 事务提交后执行，避免把 Redis 操作包进 DB 事务）。
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderCancelListener {

    private final SeckillOrderService orderService;
    private final RedisSeckillService redisSeckillService;

    @RabbitListener(queues = RabbitMQConfig.CANCEL_QUEUE)
    public void onCancel(SeckillOrderTimeoutMessage message) {
        // ① DB 事务内取消（仅当仍为待支付），并回滚 DB 库存
        SeckillOrder canceled = orderService.cancelIfUnpaid(message.orderId());
        if (canceled == null) {
            // 已支付 / 已取消 / 不存在：幂等跳过（不重复回滚）
            log.info("[取消订单] 无需取消 orderId={}（可能已支付或已取消）", message.orderId());
            return;
        }

        // ② DB 事务已提交，回滚 Redis 预扣库存，让库存回到可抢池
        redisSeckillService.rollbackStock(canceled.getActivityId());
        log.info("[取消订单] 超时未支付已取消，回滚库存 orderNo={}, activityId={}",
                canceled.getOrderNo(), canceled.getActivityId());
    }
}