package com.example.seckill.controller;

import com.example.seckill.common.api.Result;
import com.example.seckill.dto.LoginRequest;
import com.example.seckill.dto.LoginResponse;
import com.example.seckill.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录接口（微信小程序）
 *
 * <p>POST /api/auth/login —— 入参 {code}，返回 {userId, token}。
 * 小程序端：wx.login -> 拿 code -> 调本接口 -> 存 token，抢购时放入 Header。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok("登录成功", authService.login(request));
    }
}