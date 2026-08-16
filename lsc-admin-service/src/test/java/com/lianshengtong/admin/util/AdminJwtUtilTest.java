package com.lianshengtong.admin.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("管理员 JWT 工具测试")
class AdminJwtUtilTest {

    private AdminJwtUtil jwtUtil;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new AdminJwtUtil();
        setField(jwtUtil, "secret", "01234567890123456789012345678901");
        setField(jwtUtil, "expireMillis", 7200000L);
        setField(jwtUtil, "issuer", "lsc-admin-service");
        jwtUtil.init();
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    // ============== generateToken 测试 ==============

    @Test
    @DisplayName("generateToken: 正常生成令牌包含正确信息")
    void generateToken_normalToken() {
        String token = jwtUtil.generateToken(1001L, 0);
        assertNotNull(token);
        assertFalse(token.isEmpty());

        Claims claims = jwtUtil.parseToken(token);
        assertEquals("1001", claims.getSubject());
        assertEquals("lsc-admin-service", claims.getIssuer());
        assertEquals(0, claims.get("role", Integer.class));
        assertEquals("admin", claims.get("userType"));
    }

    @Test
    @DisplayName("generateToken: 非零角色正确编码")
    void generateToken_nonZeroRole() {
        String token = jwtUtil.generateToken(2002L, 3);
        Claims claims = jwtUtil.parseToken(token);
        assertEquals(3, claims.get("role", Integer.class));
    }

    @Test
    @DisplayName("generateToken: 所有管理员可生成有效令牌")
    void generateToken_multipleAdminIds() {
        for (long id : new long[]{1L, 999L, 99999L}) {
            String token = jwtUtil.generateToken(id, 1);
            Long parsedId = jwtUtil.getAdminId(token);
            assertEquals(id, parsedId);
        }
    }

    // ============== parseToken 测试 ==============

    @Test
    @DisplayName("parseToken: 解析合法令牌返回正确Claims")
    void parseToken_validToken() {
        String token = jwtUtil.generateToken(3003L, 2);
        Claims claims = jwtUtil.parseToken(token);
        assertNotNull(claims);
        assertEquals("3003", claims.getSubject());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("parseToken: 解析过期令牌抛出异常")
    void parseToken_expiredToken() throws Exception {
        setField(jwtUtil, "expireMillis", 1L);
        jwtUtil.init();
        String token = jwtUtil.generateToken(4004L, 1);
        Thread.sleep(50);
        assertThrows(Exception.class, () -> jwtUtil.parseToken(token));
    }

    @Test
    @DisplayName("parseToken: 格式错误令牌抛出异常")
    void parseToken_malformedToken() {
        assertThrows(Exception.class, () -> jwtUtil.parseToken("invalid-token-format"));
    }

    @Test
    @DisplayName("parseToken: 被篡改令牌抛出异常")
    void parseToken_tamperedToken() {
        String token = jwtUtil.generateToken(5005L, 0);
        String[] parts = token.split("\\.");
        StringBuilder tampered = new StringBuilder();
        tampered.append(parts[0]).append('.').append(parts[1]).append('.');
        byte[] sigBytes = java.util.Base64.getUrlDecoder().decode(parts[2]);
        sigBytes[sigBytes.length - 1] ^= 0x01;
        tampered.append(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes));
        assertThrows(Exception.class, () -> jwtUtil.parseToken(tampered.toString()));
    }

    // ============== validateToken 测试 ==============

    @Test
    @DisplayName("validateToken: 合法令牌返回true")
    void validateToken_validToken() {
        String token = jwtUtil.generateToken(6006L, 1);
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("validateToken: 过期令牌返回false")
    void validateToken_expiredToken() throws Exception {
        setField(jwtUtil, "expireMillis", 1L);
        jwtUtil.init();
        String token = jwtUtil.generateToken(7007L, 0);
        Thread.sleep(50);
        assertFalse(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("validateToken: 非法令牌返回false")
    void validateToken_invalidToken() {
        assertFalse(jwtUtil.validateToken("garbage-token"));
    }

    // ============== getAdminId 测试 ==============

    @Test
    @DisplayName("getAdminId: 正确解析管理员ID")
    void getAdminId_validToken() {
        String token = jwtUtil.generateToken(8080L, 0);
        assertEquals(8080L, jwtUtil.getAdminId(token));
    }

    @Test
    @DisplayName("getAdminId: 大数值管理员ID正确解析")
    void getAdminId_largeAdminId() {
        String token = jwtUtil.generateToken(99999999L, 1);
        assertEquals(99999999L, jwtUtil.getAdminId(token));
    }

    // ============== getRole 测试 ==============

    @Test
    @DisplayName("getRole: 正确解析角色")
    void getRole_validToken() {
        String token = jwtUtil.generateToken(9009L, 4);
        assertEquals(4, jwtUtil.getRole(token));
    }

    @Test
    @DisplayName("getRole: 角色为0时正确解析")
    void getRole_zeroRole() {
        String token = jwtUtil.generateToken(1010L, 0);
        assertEquals(0, jwtUtil.getRole(token));
    }

    // ============== init 测试 ==============

    @Test
    @DisplayName("init: secret为空抛出IllegalStateException")
    void init_emptySecret_throws() throws Exception {
        AdminJwtUtil util = new AdminJwtUtil();
        setField(util, "secret", "");
        assertThrows(IllegalStateException.class, util::init);
    }

    @Test
    @DisplayName("init: secret为null抛出IllegalStateException")
    void init_nullSecret_throws() throws Exception {
        AdminJwtUtil util = new AdminJwtUtil();
        setField(util, "secret", null);
        assertThrows(IllegalStateException.class, util::init);
    }

    @Test
    @DisplayName("init: secret长度不足32字节抛出异常")
    void init_shortSecret_throws() throws Exception {
        AdminJwtUtil util = new AdminJwtUtil();
        setField(util, "secret", "short-secret");
        assertThrows(IllegalArgumentException.class, util::init);
    }

    @Test
    @DisplayName("init: secret恰好32字节正常初始化")
    void init_exact32ByteSecret() throws Exception {
        AdminJwtUtil util = new AdminJwtUtil();
        setField(util, "secret", "01234567890123456789012345678901");
        setField(util, "expireMillis", 7200000L);
        setField(util, "issuer", "lsc-admin-service");
        assertDoesNotThrow(util::init);
    }

    @Test
    @DisplayName("init: secret为33字节也正常初始化")
    void init_longerSecret() throws Exception {
        AdminJwtUtil util = new AdminJwtUtil();
        setField(util, "secret", "0123456789012345678901234567890123");
        setField(util, "expireMillis", 7200000L);
        setField(util, "issuer", "lsc-admin-service");
        assertDoesNotThrow(util::init);
    }
}
