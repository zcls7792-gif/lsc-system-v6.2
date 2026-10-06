package com.zcls.lsc.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发 / 解析 / 校验。
 *
 * Payload 标准字段：
 *  - sub: userId（字符串形式，避免 Long 精度问题）
 *  - exp: 过期时间（秒）
 *  - iat: 签发时间
 */
public final class JwtUtil {

    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            JwtKeys.activeSecret().getBytes(StandardCharsets.UTF_8));

    /** 签发 token（默认 7 天过期）。 */
    public static String issue(long userId) {
        long now = System.currentTimeMillis();
        long exp = now + 7L * 24 * 3600 * 1000;
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(KEY)
                .compact();
    }

    /** 签发 token（自定义过期毫秒）。 */
    public static String issue(long userId, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(KEY)
                .compact();
    }

    /**
     * 解析 token，返回 userId。
     * @throws io.jsonwebtoken.JwtException token 无效/过期
     */
    public static long parseUserId(String token) {
        Claims c = Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(c.getSubject());
    }

    private JwtUtil() {}
}
