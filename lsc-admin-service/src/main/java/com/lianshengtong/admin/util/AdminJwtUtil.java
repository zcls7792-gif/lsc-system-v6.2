package com.lianshengtong.admin.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理员 JWT 工具
 * <p>Token 有效期 2 小时，HS256 签名。userType 固定为 admin。</p>
 */
@Component
public class AdminJwtUtil {

    @Value("${lsc.admin.jwt.secret}")
    private String secret;

    @Value("${lsc.admin.jwt.expire-millis:7200000}")
    private long expireMillis;

    @Value("${lsc.admin.jwt.issuer:lsc-admin-service}")
    private String issuer;

    private SecretKey signKey;

    @PostConstruct
    public void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("生产环境必须配置 lsc.admin.jwt.secret，禁止使用默认值");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret 长度不足 32 字节");
        }
        this.signKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long adminId, Integer role) {
        long now = System.currentTimeMillis();
        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", "admin");
        claims.put("role", role);
        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(adminId))
                .issuer(issuer)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMillis))
                .signWith(signKey)
                .compact();
    }

    public Claims parseToken(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(signKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public Long getAdminId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    public Integer getRole(String token) {
        return parseToken(token).get("role", Integer.class);
    }
}
