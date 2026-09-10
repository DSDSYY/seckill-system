package com.example.seckill.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 抢购请求入参：小程序 POST /api/seckill/do
 *
 * @param activityId 秒杀活动ID（必填）
 */
public record SeckillRequest(
        @NotNull(message = "活动ID不能为空")
        Long activityId) {
}