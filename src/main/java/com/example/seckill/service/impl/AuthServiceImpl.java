package com.example.seckill.service.impl;

import com.example.seckill.common.api.ResultCode;
import com.example.seckill.common.exception.BusinessException;
import com.example.seckill.common.util.JwtUtil;
import com.example.seckill.dto.LoginRequest;
import com.example.seckill.dto.LoginResponse;
import com.example.seckill.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 登录服务实现
 *
 * <p>mock 模式（默认）：为方便本地联调，把 code 稳定映射成一个 userId 并签发 token，
 * 同一 code 每次登录得到同一个 userId（String.hashCode 确定性），便于测试限流/一人一单。
 * 生产：接入微信 code2session + 用户表（后续阶段或真实项目完成）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final JwtUtil jwtUtil;

    @Value("${seckill.wechat.mock-enabled:true}")
    private boolean mockEnabled;

    @Override
    public LoginResponse login(LoginRequest request) {
        if (mockEnabled) {
            // 演示映射：code 稳定哈希 -> userId（1000~101000 区间）
            long userId = (request.code().hashCode() & 0x7fffffffL) % 100_000L + 1000L;
            String token = jwtUtil.createToken(userId);
            log.info("[登录-MOCK] code={} -> userId={}", request.code(), userId);
            return new LoginResponse(userId, token);
        }
        // TODO 真实微信登录：调用 code2session(appid, secret, code) 换 openid
        throw new BusinessException(ResultCode.SERVER_ERROR, "未接入真实微信登录，请开启 mock");
    }
}