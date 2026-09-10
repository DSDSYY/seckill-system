package com.example.seckill.service;

import com.example.seckill.dto.LoginRequest;
import com.example.seckill.dto.LoginResponse;

/**
 * 登录服务接口（微信小程序）
 */
public interface AuthService {

    /**
     * 登录：code -> 用户ID -> 签发 JWT
     *
     * <p>真实流程（mock-enabled=false 时）：
     * wx.login 的 code 调用微信 code2session 接口换取 openid/session_key，
     * 用 openid 查/建用户表得到系统 userId，再签发 JWT。
     * 本阶段默认 mock-enabled=true，仅用 code 做本地演示。
     */
    LoginResponse login(LoginRequest request);
}