package com.lianshengtong.user.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtUtil 单元测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtUtil, "expireMillis", 86400000L);
        ReflectionTestUtils.setField(jwtUtil, "issuer", "lsc-user-service");
        jwtUtil.init();
    }

    @Test
    @DisplayName("init: 合法密钥初始化成功")
    void init_validSecret_success() {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(util, "expireMillis", 86400000L);
        ReflectionTestUtils.setField(util, "issuer", "lsc-user-service");
        assertDoesNotThrow(util::init);
    }

    @Test
    @DisplayName("init: 短密钥抛出异常")
    void init_shortSecret_throwsException() {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", "short-key");
        ReflectionTestUtils.setField(util, "expireMillis", 86400000L);
        ReflectionTestUtils.setField(util, "issuer", "lsc-user-service");
        assertThrows(IllegalArgumentException.class, util::init);
    }

    @Test
    @DisplayName("init: 空密钥抛出异常")
    void init_emptySecret_throwsException() {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", "");
        ReflectionTestUtils.setField(util, "expireMillis", 86400000L);
        ReflectionTestUtils.setField(util, "issuer", "lsc-user-service");
        assertThrows(IllegalArgumentException.class, util::init);
    }

    @Test
    @DisplayName("generateToken: 无额外声明生成成功")
    void generateToken_withoutExtraClaims_success() {
        String token = jwtUtil.generateToken("user-001", "consumer", null);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("generateToken: 有额外声明生成成功")
    void generateToken_withExtraClaims_success() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("mobile", "13800138000");
        extra.put("level", "vip");
        String token = jwtUtil.generateToken("user-001", "consumer", extra);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("generateToken: 生成的Token可被解析")
    void generateToken_generatedTokenParsable() {
        String token = jwtUtil.generateToken("user-001", "consumer", null);
        Claims claims = jwtUtil.parseToken(token);
        assertEquals("user-001", claims.getSubject());
        assertEquals("lsc-user-service", claims.getIssuer());
    }

    @Test
    @DisplayName("parseToken: 有效Token返回正确Claims")
    void parseToken_validToken_returnsClaims() {
        String token = jwtUtil.generateToken("user-001", "consumer", null);
        Claims claims = jwtUtil.parseToken(token);
        assertNotNull(claims);
        assertEquals("user-001", claims.getSubject());
        assertEquals("consumer", claims.get("userType", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("parseToken: 无效Token抛出异常")
    void parseToken_invalidToken_throwsException() {
        assertThrows(Exception.class, () -> jwtUtil.parseToken("invalid-token-string"));
    }

    @Test
    @DisplayName("parseToken: 被篡改Token抛出异常")
    void parseToken_tamperedToken_throwsException() {
        String token = jwtUtil.generateToken("user-001", "consumer", null);
        // Replace entire signature portion (last part after final dot)
        int lastDot = token.lastIndexOf('.');
        String tampered = token.substring(0, lastDot + 1) + "invalid-signature";
        assertThrows(Exception.class, () -> jwtUtil.parseToken(tampered));
    }

    @Test
    @DisplayName("validateToken: 有效Token返回true")
    void validateToken_validToken_returnsTrue() {
        String token = jwtUtil.generateToken("user-001", "consumer", null);
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("validateToken: 无效Token返回false")
    void validateToken_invalidToken_returnsFalse() {
        assertFalse(jwtUtil.validateToken("invalid-token"));
    }

    @Test
    @DisplayName("validateToken: null Token返回false")
    void validateToken_nullToken_returnsFalse() {
        assertFalse(jwtUtil.validateToken(null));
    }

    @Test
    @DisplayName("validateToken: 过期Token返回false")
    void validateToken_expiredToken_returnsFalse() {
        JwtUtil shortExpireUtil = new JwtUtil();
        ReflectionTestUtils.setField(shortExpireUtil, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(shortExpireUtil, "expireMillis", 1L);
        ReflectionTestUtils.setField(shortExpireUtil, "issuer", "lsc-user-service");
        shortExpireUtil.init();
        String token = shortExpireUtil.generateToken("user-001", "consumer", null);
        assertDoesNotThrow(() -> Thread.sleep(10));
        assertFalse(shortExpireUtil.validateToken(token));
    }

    @Test
    @DisplayName("getSubject: 有效Token返回subject")
    void getSubject_validToken_returnsSubject() {
        String token = jwtUtil.generateToken("user-001", "consumer", null);
        assertEquals("user-001", jwtUtil.getSubject(token));
    }

    @Test
    @DisplayName("getSubject: 无效Token抛出异常")
    void getSubject_invalidToken_throwsException() {
        assertThrows(Exception.class, () -> jwtUtil.getSubject("invalid-token"));
    }

    @Test
    @DisplayName("getUserType: 有效Token返回用户类型")
    void getUserType_validToken_returnsUserType() {
        String token = jwtUtil.generateToken("user-001", "consumer", null);
        assertEquals("consumer", jwtUtil.getUserType(token));
    }

    @Test
    @DisplayName("getUserType: 商家类型正确返回")
    void getUserType_merchantType_returnsCorrectValue() {
        String token = jwtUtil.generateToken("merchant-001", "merchant", null);
        assertEquals("merchant", jwtUtil.getUserType(token));
    }

    @Test
    @DisplayName("getExpireMillis: 返回配置的过期时间")
    void getExpireMillis_returnsConfiguredValue() {
        assertEquals(86400000L, jwtUtil.getExpireMillis());
    }

    @Test
    @DisplayName("generateToken: 多个额外声明均可解析")
    void generateToken_multipleExtraClaims_parsedCorrectly() {
        Map<String, Object> extra = new HashMap<>();
        extra.put("mobile", "13800138000");
        extra.put("level", "vip");
        extra.put("custom", "value");
        String token = jwtUtil.generateToken("user-001", "consumer", extra);
        Claims claims = jwtUtil.parseToken(token);
        assertEquals("13800138000", claims.get("mobile", String.class));
        assertEquals("vip", claims.get("level", String.class));
        assertEquals("value", claims.get("custom", String.class));
    }

    @Test
    @DisplayName("generateToken: 不同用户生成不同Token")
    void generateToken_differentUsers_differentTokens() {
        String token1 = jwtUtil.generateToken("user-001", "consumer", null);
        String token2 = jwtUtil.generateToken("user-002", "consumer", null);
        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("generateToken: 自定义过期时间生效")
    void generateToken_customExpireMillis_works() {
        JwtUtil customUtil = new JwtUtil();
        ReflectionTestUtils.setField(customUtil, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(customUtil, "expireMillis", 3600000L);
        ReflectionTestUtils.setField(customUtil, "issuer", "custom-issuer");
        customUtil.init();
        assertEquals(3600000L, customUtil.getExpireMillis());
        String token = customUtil.generateToken("user-001", "consumer", null);
        Claims claims = customUtil.parseToken(token);
        assertEquals("custom-issuer", claims.getIssuer());
    }
}