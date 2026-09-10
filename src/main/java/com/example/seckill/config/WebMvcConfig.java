package com.example.seckill.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册拦截器及生效路径
 *
 * <p>执行顺序 = 注册顺序：
 * <ol>
 *   <li>JwtAuthInterceptor：/api/** 全部要求登录（登录接口除外）；
 *   <li>RateLimitInterceptor：仅抢购接口做单用户限流（此时 userId 已就绪）。
 * </ol>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // ① JWT 认证：除登录外，所有 /api/** 都需要合法 token
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login");

        // ② 单用户限流：只对抢购接口生效（5 次/秒）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/seckill/do");
    }
}