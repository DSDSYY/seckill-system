package com.example.seckill.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发 / 解析登录令牌
 *
 * <p>设计：
 * <ul>
 *   <li>Payload 只放 userId（subject）+ 签发/过期时间，不放敏感信息；
 *   <li>签名算法 HS256，密钥必须 >= 32 字节，生产用环境变量注入；
 *   <li>微信小程序流程：wx.login 拿 code -> 后端 code2session 换 openid
 *       -> 映射系统 userId -> 签发 JWT 返回小程序；后续请求 Header 携带。
 * </ul>
 */
@Slf4j
@Component
public class JwtUtil {

    /** 密钥：长度必须 >= 32 字节（HS256 要求） */
    @Value("${seckill.jwt.secret}")
    private String secret;

    /** 令牌有效期（小时） */
    @Value("${seckill.jwt.expire-hours:24}")
    private long expireHours;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        log.info("JwtUtil 初始化完成，有效期 {} 小时", expireHours);
    }

    /** 为指定用户签发 token */
    public String createToken(Long userId) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析 token 获取 userId
     *
     * @throws JwtException token 非法/过期/被篡改时抛出
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }
}