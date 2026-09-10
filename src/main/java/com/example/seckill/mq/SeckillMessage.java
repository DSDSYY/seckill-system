package com.example.seckill.mq;

import java.io.Serializable;

/**
 * 秒杀异步消息：Redis 扣减成功后投递，由异步消费者负责真正落库
 *
 * <p>为什么用 record：
 * 消息体不可变、天然线程安全，跨线程/跨进程传输（如序列化到 MQ）都安全。
 *
 * @param orderNo    临时订单号（雪花算法生成）
 * @param userId     用户ID
 * @param activityId 秒杀活动ID
 * @param productId  商品ID（冗余，方便消费者直接落订单，无需再查活动）
 */
public record SeckillMessage(String orderNo, Long userId, Long activityId, Long productId)
        implements Serializable {
}