package com.example.seckill.common.constant;

/**
 * Redis Key 统一管理：禁止魔法字符串散落在业务代码里
 *
 * <p>命名规范：业务域:子域:{id}
 */
public final class RedisKeyConstant {

    private RedisKeyConstant() {
    }

    /** 秒杀剩余库存 Key：value 为剩余库存（int） */
    public static String seckillStock(Long activityId) {
        return "seckill:stock:" + activityId;
    }

    /**
     * 用户参与资格 Key：user:activity:{userId}:{activityId}
     * 用 SETNX + TTL 实现"一人一单"，TTL = 活动剩余时长
     */
    public static String seckillUserActivity(Long userId, Long activityId) {
        return "user:activity:" + userId + ":" + activityId;
    }

    /** 单用户接口限流 Key：value = "当前令牌数:上次补充时间戳"（令牌桶，阶段四） */
    public static String rateLimit(Long userId) {
        return "seckill:rate:user:" + userId;
    }
}