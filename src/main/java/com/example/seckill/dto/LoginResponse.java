package com.example.seckill.dto;

/**
 * 登录响应
 *
 * @param userId 系统用户ID
 * @param token  JWT（后续请求放入 Header: Authorization: Bearer {token}）
 */
public record LoginResponse(Long userId, String token) {
}