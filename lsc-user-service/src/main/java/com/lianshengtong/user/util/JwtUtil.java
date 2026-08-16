package com.lianshengtong.user.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
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
 * JWT 生成与验证工具
 * - Token 有效期 24 小时
 * - HS256 签名算法
 *
 * @author lsc
 */
@Component
public class JwtUtil {

    @Value("${lsc.jwt.secret}")
    private String secret;

    @Value("${lsc.jwt.expire-millis:86400000}")
    private long expireMillis;

    @Value("${lsc.jwt.issuer:lsc-user-service}")
    private String issuer;

    private SecretKey signKey;

    @PostConstruct
    public void init() {
        // 密钥字节数必须 >= 32 (HS256要求)
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret 长度不足 32 字节");
        }
        this.signKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT Token
     *
     * @param subject    主体 (用户ID/管理员ID)
     * @param userType   用户类型 0消费者 1商家; 管理员固定为 admin
     * @param extraClaims 额外声明
     * @return JWT Token
     */
    public String generateToken(String subject, String userType, Map<String, Object> extraClaims) {
        long now = System.currentTimeMillis();
        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", userType);
        if (extraClaims != null) {
            claims.putAll(extraClaims);
        }
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuer(issuer)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMillis))
                .signWith(signKey)
                .compact();
    }

    /**
     * 解析并校验 Token
     *
     * @param token JWT Token
     * @return Claims
     */
    public Claims parseToken(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(signKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    /**
     * 校验 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 从 Token 中提取 subject (用户ID)
     */
    public String getSubject(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * 从 Token 中提取用户类型
     */
    public String getUserType(String token) {
        return parseToken(token).get("userType", String.class);
    }

    /**
     * 获取 Token 剩余有效期 (毫秒)
     */
    public long getExpireMillis() {
        return expireMillis;
    }
}
