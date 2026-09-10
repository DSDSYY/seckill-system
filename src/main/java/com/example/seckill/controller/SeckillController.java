package com.example.seckill.controller;

import com.example.seckill.common.api.Result;
import com.example.seckill.common.context.UserContext;
import com.example.seckill.dto.SeckillRequest;
import com.example.seckill.service.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 秒杀对外接口（阶段四收口）
 *
 * <p>对外（微信小程序）：
 * <pre>
 *   POST /api/seckill/do
 *     Header: Authorization: Bearer {JWT}   （JwtAuthInterceptor 解析出 userId）
 *     Body  : { "activityId": 1 }
 * </pre>
 * 开发/联调（同样要求登录态，登录后可调用）：
 * <pre>
 *   POST /api/seckill/stock/init/{activityId}   预热库存
 *   GET  /api/seckill/stock/{activityId}        查 Redis 剩余库存
 * </pre>
 */
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /**
     * 用户抢购（小程序入口）
     * 限流：单用户 5 次/秒（RateLimitInterceptor 令牌桶）
     *
     * @return code=200 成功，data=临时订单号（前端轮询订单状态）；
     *         1004 售罄 / 1005 繁忙 / 1006 重复下单 / 429 限流 / 401 未登录
     */
    @PostMapping("/do")
    public Result<String> doSeckill(@Valid @RequestBody SeckillRequest request) {
        // userId 由 JwtAuthInterceptor 从 token 解析后放入 UserContext
        Long userId = UserContext.getUserId();
        String orderNo = seckillService.doSeckill(userId, request.activityId());
        return Result.ok("抢购成功，正在排队出票", orderNo);
    }

    /** 预热库存（开发/管理用） */
    @PostMapping("/stock/init/{activityId}")
    public Result<Boolean> initStock(@PathVariable Long activityId) {
        return Result.ok(seckillService.initStock(activityId));
    }

    /** 查 Redis 剩余库存（联调用） */
    @GetMapping("/stock/{activityId}")
    public Result<Integer> remainingStock(@PathVariable Long activityId) {
        return Result.ok(seckillService.getRemainingStock(activityId));
    }
}