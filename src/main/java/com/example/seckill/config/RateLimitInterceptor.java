package com.example.seckill.config;

import com.example.seckill.common.api.ResultCode;
import com.example.seckill.common.context.UserContext;
import com.example.seckill.common.exception.BusinessException;
import com.example.seckill.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 单用户接口限流拦截器（令牌桶，5 次/秒）
 *
 * <p>只作用于抢购接口（/api/seckill/do）：
 * 同一用户超过 5 次/秒 -> 抛 TOO_MANY_REQUESTS(429)，由全局异常处理器统一返回。
 * 限流放在拦截器层：在进入 Controller/Service 之前就拦截，节约后端资源。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = UserContext.getUserId();
        if (userId != null && !rateLimitService.tryAcquire(userId)) {
            log.warn("[限流] 用户 {} 请求过于频繁", userId);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        return true;
    }
}