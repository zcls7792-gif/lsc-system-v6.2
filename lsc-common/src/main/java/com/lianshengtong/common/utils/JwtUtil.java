package com.lianshengtong.common.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * JWT工具类
 * 用户端token: issuer=lsc-user-service
 * 管理后台token: issuer=lsc-admin-service
 */
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private static final String SECRET = System.getenv("JWT_SECRET");
    private static final SecretKey KEY;
    static {
        String secretKey = SECRET;
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("JWT_SECRET environment variable must be set");
        }
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        KEY = Keys.hmacShaKeyFor(keyBytes);
    }
    private static final long DEFAULT_EXPIRATION = 7 * 24 * 60 * 60 * 1000L;

    public static String generateToken(Long userId, Integer userType, String issuer) {
        return generateToken(userId, userType, issuer, DEFAULT_EXPIRATION);
    }

    public static String generateToken(Long userId, Integer userType, String issuer, long expirationMs) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("userType", userType);
        claims.put("iss", issuer);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(KEY)
                .compact();
    }

    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("[JWT] Token已过期");
            throw new JwtValidationException("Token已过期，请重新登录", e);
        } catch (SignatureException e) {
            log.warn("[JWT] Token签名无效");
            throw new JwtValidationException("Token无效", e);
        } catch (MalformedJwtException e) {
            log.warn("[JWT] Token格式错误");
            throw new JwtValidationException("Token无效", e);
        } catch (IllegalArgumentException e) {
            log.warn("[JWT] Token参数异常: {}", e.getMessage());
            throw new JwtValidationException("Token无效", e);
        }
    }

    public static boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtValidationException e) {
            return false;
        }
    }

    /**
     * JWT 校验异常
     */
    public static class JwtValidationException extends RuntimeException {
        public JwtValidationException(String message) {
            super(message);
        }
        public JwtValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        Object userId = claims.get("userId");
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        return null;
    }

    public static Integer getUserType(String token) {
        Claims claims = parseToken(token);
        Object userType = claims.get("userType");
        if (userType instanceof Number) {
            return ((Number) userType).intValue();
        }
        return null;
    }

    public static String getIssuer(String token) {
        return parseToken(token).getIssuer();
    }


}
