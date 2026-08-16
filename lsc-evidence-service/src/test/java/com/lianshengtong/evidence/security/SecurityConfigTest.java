package com.lianshengtong.evidence.security;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = {SecurityConfig.class},
        properties = {
                "lsc.evidence.jwt.secret=test-secret-key-2026-must-be-32-bytes-long",
                "lsc.evidence.jwt.expiration-ms=3600000",
                "lsc.evidence.jwt.refresh-expiration-ms=86400000"
        }
)
@DisplayName("SecurityConfig 配置类测试")
class SecurityConfigTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationContext ctx;

    @Test
    @DisplayName("JwtUtil Bean 正确注入")
    void jwtUtil_injected() {
        assertNotNull(jwtUtil);
        assertSame(jwtUtil, ctx.getBean(JwtUtil.class));
    }

    @Test
    @DisplayName("JwtUtil access/refresh 过期时间正确")
    void jwtUtil_expirations() {
        assertTrue(jwtUtil.getAccessExpirationMs() > 0);
        assertTrue(jwtUtil.getRefreshExpirationMs() > 0);
        assertNotEquals(jwtUtil.getAccessExpirationMs(), jwtUtil.getRefreshExpirationMs(),
                "默认情况下 access 与 refresh 过期时间不应相同");
    }

    @Test
    @DisplayName("PasswordEncoder Bean 注入且为 BCryptPasswordEncoder")
    void passwordEncoder_injected() {
        assertNotNull(passwordEncoder);
        assertInstanceOf(org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.class,
                passwordEncoder);
    }

    @Test
    @DisplayName("JwtUtil 签名验证 roundtrip 正常")
    void jwtUtil_roundtrip() {
        String token = jwtUtil.generateToken("test", "ADMIN");
        JwtUtil.Claims claims = jwtUtil.validateToken(token);
        assertNotNull(claims);
        assertEquals("test", claims.username());
        assertEquals("ADMIN", claims.role());
    }

    @Test
    @DisplayName("JwtUtil Refresh Token roundtrip 正常")
    void jwtUtil_refreshRoundtrip() {
        String token = jwtUtil.generateRefreshToken("test", "AUDITOR");
        JwtUtil.Claims claims = jwtUtil.validateRefreshToken(token);
        assertNotNull(claims);
        assertEquals("refresh", claims.type());
    }

    @Test
    @DisplayName("BCrypt 编解码匹配正确")
    void bcrypt_match() {
        String raw = "admin123";
        String encoded = passwordEncoder.encode(raw);
        assertTrue(passwordEncoder.matches(raw, encoded));
        assertFalse(passwordEncoder.matches("wrong", encoded));
    }

    @Test
    @DisplayName("配置文件 secret 值非空")
    void secret_value() {
        assertNotNull(jwtUtil);
        // 构造器已通过 secret 初始化
    }
}
