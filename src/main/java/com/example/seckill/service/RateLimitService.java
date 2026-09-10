package com.example.seckill.service;

import com.example.seckill.common.constant.RedisKeyConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 单用户接口限流服务（Redis 令牌桶 + Lua，阶段四）
 *
 * <p>为什么不用 Guava RateLimiter：
 * <ul>
 *   <li>Guava 是 JVM 内单机限流，多实例部署时每台机器各自满速，总 QPS = N 倍；
 *   <li>Redis + Lua 是分布式限流：所有实例共享同一个桶，规则全局一致；
 *   <li>Lua 原子执行，无竞态，算法与业务解耦、可复用。
 * </ul>
 *
 * <p>令牌桶参数：capacity=5（允许瞬间突发 5 次），refill=5/秒（稳态 5 QPS），
 * 即"单用户每秒不超过 5 次，且允许少量突发"，兼顾体验与防护。
 */
@Slf4j
@Service
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;

    /** 桶容量：突发上限 */
    @Value("${seckill.rate-limit.capacity:5}")
    private double capacity;

    /** 每秒补充令牌数：稳态速率 */
    @Value("${seckill.rate-limit.refill-per-second:5}")
    private double refillPerSecond;

    /** 令牌桶 Lua 脚本：类加载时加载一次 */
    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>();

    static {
        TOKEN_BUCKET_SCRIPT.setLocation(new ClassPathResource("lua/rate_limit_token_bucket.lua"));
        TOKEN_BUCKET_SCRIPT.setResultType(Long.class);
    }

    public RateLimitService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 尝试获取 1 个令牌
     *
     * @param userId 用户ID
     * @return true=放行；false=限流（超过 5 次/秒）
     */
    public boolean tryAcquire(Long userId) {
        if (userId == null) {
            return false;
        }
        Long result = stringRedisTemplate.execute(
                TOKEN_BUCKET_SCRIPT,
                Collections.singletonList(RedisKeyConstant.rateLimit(userId)),
                String.valueOf(capacity),
                String.valueOf(refillPerSecond),
                String.valueOf(System.currentTimeMillis()));
        return result != null && result == 1L;
    }
}