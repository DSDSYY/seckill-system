package com.example.seckill.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求入参
 *
 * @param code     wx.login 获取的临时登录凭证 code
 * @param nickname 昵称（可选，展示用）
 */
public record LoginRequest(
        @NotBlank(message = "code 不能为空")
        String code,
        String nickname) {
}