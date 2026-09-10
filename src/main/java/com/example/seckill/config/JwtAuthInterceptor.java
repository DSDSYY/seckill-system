package com.example.seckill.config;

import com.example.seckill.common.api.ResultCode;
import com.example.seckill.common.context.UserContext;
import com.example.seckill.common.exception.BusinessException;
import com.example.seckill.common.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器
 *
 * <p>职责：从 Header 取 {@code Authorization: Bearer {token}}，
 * 解析出 userId 写入 {@link UserContext}，供 Controller/Service 使用；
 * 请求结束后清理 ThreadLocal，防止线程池复用串号。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行预检请求（浏览器 CORS 场景；小程序可不配）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        String token = auth.substring(7).trim();
        try {
            Long userId = jwtUtil.parseUserId(token);
            UserContext.setUserId(userId);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[JWT] token 校验失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 关键：清理 ThreadLocal，防止 Tomcat 线程复用导致下个请求读到别人的 userId
        UserContext.clear();
    }
}