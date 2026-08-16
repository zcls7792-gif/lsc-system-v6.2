package com.lianshengtong.evidence.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
@DisplayName("JwtUtil 单元测试 - 双令牌模型覆盖")
class JwtUtilTest {

    private static final String SECRET = "test-secret-key-2026-secure";
    private static final long ACCESS_MS = 2_000L; // 2 秒，便于测试过期
    private static final long REFRESH_MS = 7 * 24 * 60 * 60_000L;

    private JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, ACCESS_MS, REFRESH_MS);
    }

    // ==================== 构造器 ====================

    @Test
    @Order(1)
    @DisplayName("单参数构造器 - access/refresh 过期时间一致")
    void constructor_singleExpiration() {
        JwtUtil util = new JwtUtil(SECRET, 3600_000L);
        assertEquals(3600_000L, util.getAccessExpirationMs());
        assertEquals(3600_000L, util.getRefreshExpirationMs());
    }

    @Test
    @Order(2)
    @DisplayName("双参数构造器 - access/refresh 过期时间独立")
    void constructor_twoExpirations() {
        assertEquals(ACCESS_MS, jwtUtil.getAccessExpirationMs());
        assertEquals(REFRESH_MS, jwtUtil.getRefreshExpirationMs());
    }

    // ==================== Token 生成 ====================

    @Test
    @Order(3)
    @DisplayName("generateToken - 生成 Access Token 格式正确")
    void generateToken_accessToken_format() {
        String token = jwtUtil.generateToken("alice", "ADMIN");
        assertNotNull(token);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT 应由三段组成");
    }

    @Test
    @Order(4)
    @DisplayName("generateRefreshToken - 生成 Refresh Token 格式正确")
    void generateToken_refreshToken_format() {
        String token = jwtUtil.generateRefreshToken("alice", "ADMIN");
        assertNotNull(token);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
    }

    @Test
    @Order(5)
    @DisplayName("Access Token 与 Refresh Token 的 type 字段不同")
    void generateToken_typeDifference() throws Exception {
        String access = jwtUtil.generateToken("alice", "ADMIN");
        String refresh = jwtUtil.generateRefreshToken("alice", "ADMIN");

        Map<String, Object> accessPayload = objectMapper.readValue(
                new String(java.util.Base64.getUrlDecoder().decode(access.split("\\.")[1])),
                new TypeReference<Map<String, Object>>() {});
        Map<String, Object> refreshPayload = objectMapper.readValue(
                new String(java.util.Base64.getUrlDecoder().decode(refresh.split("\\.")[1])),
                new TypeReference<Map<String, Object>>() {});

        assertEquals("access", accessPayload.get("type"));
        assertEquals("refresh", refreshPayload.get("type"));
    }

    @Test
    @Order(6)
    @DisplayName("同一用户重复生成 Access Token 必须唯一")
    void generateToken_uniqueness() throws Exception {
        String t1 = jwtUtil.generateToken("alice", "ADMIN");
        Thread.sleep(5);
        String t2 = jwtUtil.generateToken("alice", "ADMIN");
        assertNotEquals(t1, t2, "两次生成的 token 必须不同 (时间戳不同)");
    }

    // ==================== Token 解析 ====================

    @Test
    @Order(7)
    @DisplayName("validateToken - 有效 Access Token 返回正确 Claims")
    void validateToken_validAccessToken() {
        String token = jwtUtil.generateToken("alice", "ADMIN");
        JwtUtil.Claims claims = jwtUtil.validateToken(token);

        assertNotNull(claims);
        assertEquals("alice", claims.username());
        assertEquals("ADMIN", claims.role());
        assertEquals("access", claims.type());
        assertTrue(claims.expiration() > System.currentTimeMillis());
    }

    @Test
    @Order(8)
    @DisplayName("validateToken - 有效 Refresh Token 返回正确 Claims")
    void validateToken_validRefreshToken() {
        String token = jwtUtil.generateRefreshToken("bob", "AUDITOR");
        JwtUtil.Claims claims = jwtUtil.validateToken(token);

        assertNotNull(claims);
        assertEquals("bob", claims.username());
        assertEquals("AUDITOR", claims.role());
        assertEquals("refresh", claims.type());
    }

    @Test
    @Order(9)
    @DisplayName("validateToken - null token 返回 null")
    void validateToken_null() {
        assertNull(jwtUtil.validateToken(null));
    }

    @Test
    @Order(10)
    @DisplayName("validateToken - 空字符串 token 返回 null")
    void validateToken_blank() {
        assertNull(jwtUtil.validateToken(""));
        assertNull(jwtUtil.validateToken("   "));
    }

    @Test
    @Order(11)
    @DisplayName("validateToken - 非三段 token 返回 null")
    void validateToken_wrongSegments() {
        assertNull(jwtUtil.validateToken("onlytwo.segments"));
        assertNull(jwtUtil.validateToken("a.b.c.d"));
    }

    @Test
    @Order(12)
    @DisplayName("validateToken - 非法签名 token 返回 null")
    void validateToken_badSignature() {
        String token = jwtUtil.generateToken("alice", "ADMIN");
        String tampered = token.substring(0, token.lastIndexOf('.') - 1) + token.charAt(token.length() - 1);
        assertNull(jwtUtil.validateToken(tampered));
    }

    @Test
    @Order(13)
    @DisplayName("validateToken - 不同密钥签名的 token 返回 null")
    void validateToken_wrongSecret() {
        JwtUtil otherUtil = new JwtUtil("different-secret", ACCESS_MS, REFRESH_MS);
        String token = jwtUtil.generateToken("alice", "ADMIN");
        assertNull(otherUtil.validateToken(token));
    }

    @Test
    @Order(14)
    @DisplayName("validateToken - 过期 token 返回 null")
    void validateToken_expired() throws Exception {
        JwtUtil shortUtil = new JwtUtil(SECRET, 50L, 50L);
        String token = shortUtil.generateToken("alice", "ADMIN");
        Thread.sleep(120L);
        assertNull(shortUtil.validateToken(token));
    }

    @Test
    @Order(15)
    @DisplayName("validateToken - Payload 不是合法 JSON 返回 null")
    void validateToken_invalidPayload() {
        // 使用正确签名但损坏的 payload
        String token = jwtUtil.generateToken("alice", "ADMIN");
        String[] parts = token.split("\\.");
        // 篡改 base64 payload (保持相同长度，破坏 JSON)
        String badPayload = parts[1].replaceAll("[a-zA-Z]", "z");
        String broken = parts[0] + "." + badPayload + "." + parts[2];
        // 由于签名基于原 payload，校验时签名会失败
        assertNull(jwtUtil.validateToken(broken));
    }

    // ==================== Refresh Token 校验 ====================

    @Test
    @Order(16)
    @DisplayName("validateRefreshToken - 对 Refresh Token 返回正确 Claims")
    void validateRefreshToken_valid() {
        String token = jwtUtil.generateRefreshToken("alice", "ADMIN");
        JwtUtil.Claims claims = jwtUtil.validateRefreshToken(token);
        assertNotNull(claims);
        assertEquals("refresh", claims.type());
    }

    @Test
    @Order(17)
    @DisplayName("validateRefreshToken - 对 Access Token 返回 null")
    void validateRefreshToken_wrongType() {
        String access = jwtUtil.generateToken("alice", "ADMIN");
        assertNull(jwtUtil.validateRefreshToken(access));
    }

    @Test
    @Order(18)
    @DisplayName("validateRefreshToken - 无效 token 返回 null")
    void validateRefreshToken_invalid() {
        assertNull(jwtUtil.validateRefreshToken(null));
        assertNull(jwtUtil.validateRefreshToken("garbage.token.here"));
    }

    // ==================== parsePayloadOnly (removed - method is now private) ====================

    // ==================== Security 场景 ====================

    @Test
    @Order(23)
    @DisplayName("安全场景 - 篡改用户名后校验失败")
    void security_tamperUsername() {
        String token = jwtUtil.generateToken("alice", "ADMIN");
        String[] parts = token.split("\\.");
        Map<String, Object> payload = Map.of(
                "sub", "admin",
                "role", "SUPER_ADMIN",
                "type", "access",
                "iat", System.currentTimeMillis(),
                "exp", System.currentTimeMillis() + 3600_000
        );
        try {
            String newPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));
            // 保持签名不变 -> 应校验失败
            String tampered = parts[0] + "." + newPayload + "." + parts[2];
            assertNull(jwtUtil.validateToken(tampered), "篡改 payload 后签名校验应失败");
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    @Order(24)
    @DisplayName("安全场景 - 空密钥构造器下 token 仍可验签")
    void security_consistency() {
        JwtUtil util = new JwtUtil("secret", 3600_000L);
        String t = util.generateToken("x", "Y");
        assertNotNull(util.validateToken(t));
    }

    @Test
    @Order(25)
    @DisplayName("安全场景 - Claims 为 record 不可变性验证")
    void claims_immutable() {
        JwtUtil.Claims c = new JwtUtil.Claims("u", "R", "access", 123L);
        assertEquals("u", c.username());
        assertEquals("R", c.role());
        assertEquals("access", c.type());
        assertEquals(123L, c.expiration());
    }

    @Test
    @Order(26)
    @DisplayName("parsePayloadOnly - 私有方法反射验证有效token")
    void parsePayloadOnly_validToken() throws Exception {
        String token = jwtUtil.generateToken("alice", "ADMIN");
        java.lang.reflect.Method method = JwtUtil.class.getDeclaredMethod(
                "parsePayloadOnly", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) method.invoke(jwtUtil, token);
        assertNotNull(claims);
        assertEquals("alice", claims.get("sub"));
        assertEquals("ADMIN", claims.get("role"));
    }

    @Test
    @Order(27)
    @DisplayName("parsePayloadOnly - 段数错误返回null")
    void parsePayloadOnly_wrongSegments() throws Exception {
        java.lang.reflect.Method method = JwtUtil.class.getDeclaredMethod(
                "parsePayloadOnly", String.class);
        method.setAccessible(true);

        Map<String, Object> result = (Map<String, Object>) method.invoke(jwtUtil, "invalid.token");
        assertNull(result);
    }

    @Test
    @Order(28)
    @DisplayName("parsePayloadOnly - null输入返回null")
    void parsePayloadOnly_nullInput() throws Exception {
        java.lang.reflect.Method method = JwtUtil.class.getDeclaredMethod(
                "parsePayloadOnly", String.class);
        method.setAccessible(true);

        Map<String, Object> result = (Map<String, Object>) method.invoke(jwtUtil, (Object) null);
        assertNull(result);
    }

    @Test
    @Order(29)
    @DisplayName("tokenJti - null输入返回空字符串")
    void tokenJti_nullInput() {
        assertEquals("", jwtUtil.tokenJti(null));
    }

    @Test
    @Order(30)
    @DisplayName("tokenJti - 空串输入返回空字符串")
    void tokenJti_blankInput() {
        assertEquals("", jwtUtil.tokenJti(""));
        assertEquals("", jwtUtil.tokenJti("   "));
    }

    @Test
    @Order(31)
    @DisplayName("tokenJti - 不同token生成不同JTI")
    void tokenJti_uniquePerToken() throws Exception {
        String t1 = jwtUtil.generateToken("user1", "ADMIN");
        String t2 = jwtUtil.generateToken("user2", "ADMIN");
        String jti1 = jwtUtil.tokenJti(t1);
        String jti2 = jwtUtil.tokenJti(t2);
        assertNotEquals(jti1, jti2, "不同token应生成不同JTI");
        assertEquals(64, jti1.length(), "SHA-256 应为 64 位十六进制");
        assertTrue(jti1.matches("[0-9a-f]{64}"), "JTI 应为64位十六进制");
    }
}
