package com.example.seckill.mq;

/**
 * 订单超时取消消息：下单成功后被投递到"延时队列"，15 分钟后到期进入取消队列。
 *
 * @param orderId    订单ID（DB 自增主键，取消时按主键 CAS 更新）
 * @param orderNo    业务订单号（日志/排查用）
 * @param activityId 秒杀活动ID
 * @param userId     用户ID
 */
public record SeckillOrderTimeoutMessage(Long orderId, String orderNo,
                                         Long activityId, Long userId) {
}