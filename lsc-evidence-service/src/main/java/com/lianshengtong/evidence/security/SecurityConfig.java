package com.lianshengtong.evidence.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    @Value("${lsc.evidence.jwt.secret}")
    private String jwtSecret;

    @Value("${lsc.evidence.jwt.expiration-ms:7200000}")
    private long jwtExpirationMs;

    @Value("${lsc.evidence.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    @Bean
    public JwtUtil jwtUtil() {
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT 密钥必须配置且长度 >= 32 字节，请设置环境变量 JWT_SECRET");
        }
        return new JwtUtil(jwtSecret, jwtExpirationMs, refreshExpirationMs);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
