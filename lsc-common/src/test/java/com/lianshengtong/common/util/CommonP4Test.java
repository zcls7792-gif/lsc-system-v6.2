package com.lianshengtong.common.util;

import com.lianshengtong.common.mq.MessageProducer.EvidenceMessage;
import com.lianshengtong.common.mq.MessageProducer.FirstOrderMessage;
import com.lianshengtong.common.mq.MessageProducer.RiskAlertMessage;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.security.AdminRoleAspect;
import com.lianshengtong.common.tracing.TracingConfig;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LSC Common 低覆盖率类综合测试")
class CommonP4Test {

    private static boolean jwtUtilAvailable = false;

    static {
        try {
            Class.forName("com.lianshengtong.common.utils.JwtUtil");
            jwtUtilAvailable = true;
        } catch (Throwable e) {
            jwtUtilAvailable = false;
        }
    }

    // ============== LogSanitizer (utils) 测试 ==============

    @Test
    @DisplayName("LogSanitizer: sanitize null输入返回null")
    void logSanitizerUtils_sanitize_nullInput() {
        assertNull(com.lianshengtong.common.utils.LogSanitizer.sanitize(null));
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 清除换行符")
    void logSanitizerUtils_sanitize_newline() {
        String result = com.lianshengtong.common.utils.LogSanitizer.sanitize("hello\nworld");
        assertFalse(result.contains("\n"));
        assertTrue(result.contains("hello"));
        assertTrue(result.contains("world"));
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 清除回车符")
    void logSanitizerUtils_sanitize_carriageReturn() {
        String result = com.lianshengtong.common.utils.LogSanitizer.sanitize("hello\rworld");
        assertFalse(result.contains("\r"));
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 清除ANSI转义序列")
    void logSanitizerUtils_sanitize_ansiEscape() {
        String input = "hello\u001b[31mred\u001b[0m world";
        String result = com.lianshengtong.common.utils.LogSanitizer.sanitize(input);
        assertFalse(result.contains("\u001b"));
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 合并多余空格")
    void logSanitizerUtils_sanitize_collapseSpaces() {
        String result = com.lianshengtong.common.utils.LogSanitizer.sanitize("hello   world");
        assertEquals("hello world", result);
    }

    @Test
    @DisplayName("LogSanitizer: sanitizeForLog 截断长字符串")
    void logSanitizerUtils_sanitizeForLog_truncate() {
        String input = "ThisIsAVeryLongStringThatExceedsTheMaxLengthLimit";
        String result = com.lianshengtong.common.utils.LogSanitizer.sanitizeForLog(input, 10);
        assertTrue(result.endsWith("...(truncated)"));
        assertEquals(10 + "...(truncated)".length(), result.length());
    }

    @Test
    @DisplayName("LogSanitizer: sanitizeForLog 短字符串不截断")
    void logSanitizerUtils_sanitizeForLog_noTruncate() {
        String input = "short";
        String result = com.lianshengtong.common.utils.LogSanitizer.sanitizeForLog(input, 50);
        assertEquals("short", result);
    }

    @Test
    @DisplayName("LogSanitizer: sanitizeForLog null输入返回null")
    void logSanitizerUtils_sanitizeForLog_null() {
        assertNull(com.lianshengtong.common.utils.LogSanitizer.sanitizeForLog(null, 10));
    }

    @Test
    @DisplayName("LogSanitizer: containsInjection 检测到注入")
    void logSanitizerUtils_containsInjection_detected() {
        assertTrue(com.lianshengtong.common.utils.LogSanitizer.containsInjection("test\ninjection"));
        assertTrue(com.lianshengtong.common.utils.LogSanitizer.containsInjection("test\rinjection"));
    }

    @Test
    @DisplayName("LogSanitizer: containsInjection 无注入返回false")
    void logSanitizerUtils_containsInjection_clean() {
        assertFalse(com.lianshengtong.common.utils.LogSanitizer.containsInjection("normal input"));
    }

    @Test
    @DisplayName("LogSanitizer: containsInjection null/空输入返回false")
    void logSanitizerUtils_containsInjection_nullEmpty() {
        assertFalse(com.lianshengtong.common.utils.LogSanitizer.containsInjection(null));
        assertFalse(com.lianshengtong.common.utils.LogSanitizer.containsInjection(""));
    }

    @Test
    @DisplayName("LogSanitizer: sanitizeUserInput 含危险字符时清洗")
    void logSanitizerUtils_sanitizeUserInput_dangerous() {
        String result = com.lianshengtong.common.utils.LogSanitizer.sanitizeUserInput("hello\nworld");
        assertFalse(result.contains("\n"));
    }

    @Test
    @DisplayName("LogSanitizer: sanitizeUserInput null输入返回null")
    void logSanitizerUtils_sanitizeUserInput_null() {
        assertNull(com.lianshengtong.common.utils.LogSanitizer.sanitizeUserInput(null));
    }

    @Test
    @DisplayName("LogSanitizer: maskSensitive 手机号脱敏")
    void logSanitizerUtils_maskSensitive_phone() {
        String result = com.lianshengtong.common.utils.LogSanitizer.maskSensitive("13812345678", "phone");
        assertEquals("138****5678", result);
    }

    @Test
    @DisplayName("LogSanitizer: maskSensitive 身份证脱敏")
    void logSanitizerUtils_maskSensitive_idcard() {
        String result = com.lianshengtong.common.utils.LogSanitizer.maskSensitive("110101199003071234", "idcard");
        assertEquals("110101********1234", result);
    }

    @Test
    @DisplayName("LogSanitizer: maskSensitive 邮箱脱敏")
    void logSanitizerUtils_maskSensitive_email() {
        String result = com.lianshengtong.common.utils.LogSanitizer.maskSensitive("test@example.com", "email");
        assertEquals("t***@example.com", result);
    }

    @Test
    @DisplayName("LogSanitizer: maskSensitive token脱敏")
    void logSanitizerUtils_maskSensitive_token() {
        String result = com.lianshengtong.common.utils.LogSanitizer.maskSensitive("abcdefghijklmnop", "token");
        assertEquals("abcd****mnop", result);
    }

    @Test
    @DisplayName("LogSanitizer: maskSensitive password/secret/key返回****")
    void logSanitizerUtils_maskSensitive_password() {
        assertEquals("****", com.lianshengtong.common.utils.LogSanitizer.maskSensitive("mysecret", "password"));
        assertEquals("****", com.lianshengtong.common.utils.LogSanitizer.maskSensitive("mysecret", "secret"));
        assertEquals("****", com.lianshengtong.common.utils.LogSanitizer.maskSensitive("mysecret", "key"));
    }

    @Test
    @DisplayName("LogSanitizer: maskSensitive 默认类型脱敏")
    void logSanitizerUtils_maskSensitive_default() {
        String result = com.lianshengtong.common.utils.LogSanitizer.maskSensitive("hello", "unknown");
        assertEquals("h***o", result);
    }

    @Test
    @DisplayName("LogSanitizer: maskSensitive null/空输入返回空字符串")
    void logSanitizerUtils_maskSensitive_nullEmpty() {
        assertEquals("", com.lianshengtong.common.utils.LogSanitizer.maskSensitive(null, "phone"));
        assertEquals("", com.lianshengtong.common.utils.LogSanitizer.maskSensitive("", "phone"));
    }

    @Test
    @DisplayName("LogSanitizer: maskSensitive 短手机号脱敏不截断")
    void logSanitizerUtils_maskSensitive_shortPhone() {
        String result = com.lianshengtong.common.utils.LogSanitizer.maskSensitive("138", "phone");
        assertEquals("138", result);
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 空字符串处理")
    void logSanitizerUtils_sanitize_emptyString() {
        String result = com.lianshengtong.common.utils.LogSanitizer.sanitize("");
        assertEquals("", result);
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 多危险字符综合替换")
    void logSanitizerUtils_sanitize_mixedDangerous() {
        String input = "line1\nline2\rline3\u0000end";
        String result = com.lianshengtong.common.utils.LogSanitizer.sanitize(input);
        assertFalse(result.contains("\n"));
        assertFalse(result.contains("\r"));
        assertFalse(result.contains("\u0000"));
    }

    @Test
    @DisplayName("LogSanitizer: maskSensitive idcard短于10位不脱敏")
    void logSanitizerUtils_maskSensitive_shortIdcard() {
        String result = com.lianshengtong.common.utils.LogSanitizer.maskSensitive("123456789", "idcard");
        assertEquals("123456789", result);
    }

    @Test
    @DisplayName("LogSanitizer: maskSensitive token短于8位返回****")
    void logSanitizerUtils_maskSensitive_shortToken() {
        String result = com.lianshengtong.common.utils.LogSanitizer.maskSensitive("1234567", "token");
        assertEquals("****", result);
    }

    @Test
    @DisplayName("LogSanitizer: maskSensitive default类型短于5位返回****")
    void logSanitizerUtils_maskSensitive_shortDefault() {
        String result = com.lianshengtong.common.utils.LogSanitizer.maskSensitive("abcd", "unknown");
        assertEquals("****", result);
    }

    // ============== LogSanitizer (security) 测试 ==============

    @Test
    @DisplayName("LogSanitizer(security): sanitize null输入返回null")
    void logSanitizerSecurity_sanitize_null() {
        assertNull(com.lianshengtong.common.security.LogSanitizer.sanitize(null));
    }

    @Test
    @DisplayName("LogSanitizer(security): sanitize 清除换行和回车")
    void logSanitizerSecurity_sanitize_newlineCarriageReturn() {
        String result = com.lianshengtong.common.security.LogSanitizer.sanitize("hello\n\rworld");
        assertFalse(result.contains("\n"));
        assertFalse(result.contains("\r"));
    }

    @Test
    @DisplayName("LogSanitizer(security): sanitize 清除HTML标签")
    void logSanitizerSecurity_sanitize_htmlTags() {
        String result = com.lianshengtong.common.security.LogSanitizer.sanitize("<script>alert(1)</script>hello");
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
        assertTrue(result.contains("hello"));
    }

    @Test
    @DisplayName("LogSanitizer(security): sanitize 综合清洗")
    void logSanitizerSecurity_sanitize_combined() {
        String input = " <b>bold</b>\nnewline\rnext ";
        String result = com.lianshengtong.common.security.LogSanitizer.sanitize(input);
        assertFalse(result.contains("\n"));
        assertFalse(result.contains("\r"));
        assertFalse(result.contains("<"));
        assertTrue(result.contains("bold"));
    }

    @Test
    @DisplayName("LogSanitizer(security): sanitize 普通文本不变")
    void logSanitizerSecurity_sanitize_plainText() {
        String result = com.lianshengtong.common.security.LogSanitizer.sanitize("normal text");
        assertEquals("normal text", result);
    }

    @Test
    @DisplayName("LogSanitizer(security): sanitize 空字符串返回空")
    void logSanitizerSecurity_sanitize_empty() {
        String result = com.lianshengtong.common.security.LogSanitizer.sanitize("");
        assertEquals("", result);
    }

    // ============== JwtUtil 测试 ==============

    @Test
    @DisplayName("JwtUtil: generateToken 成功生成token")
    void jwtUtil_generateToken_success() {
        if (!jwtUtilAvailable) {
            Assumptions.assumeTrue(false, "JWT_SECRET not set, skipping JwtUtil test");
            return;
        }
        String token = com.lianshengtong.common.utils.JwtUtil.generateToken(1001L, 0, "lsc-user-service");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("JwtUtil: parseToken 成功解析并返回正确声明")
    void jwtUtil_parseToken_success() {
        if (!jwtUtilAvailable) {
            Assumptions.assumeTrue(false, "JWT_SECRET not set, skipping JwtUtil test");
            return;
        }
        String token = com.lianshengtong.common.utils.JwtUtil.generateToken(1001L, 0, "lsc-user-service");
        io.jsonwebtoken.Claims claims = com.lianshengtong.common.utils.JwtUtil.parseToken(token);
        assertNotNull(claims);
        assertEquals("1001", claims.getSubject());
        assertEquals("lsc-user-service", claims.getIssuer());
    }

    @Test
    @DisplayName("JwtUtil: isValid 有效token返回true")
    void jwtUtil_isValid_validToken() {
        if (!jwtUtilAvailable) {
            Assumptions.assumeTrue(false, "JWT_SECRET not set, skipping JwtUtil test");
            return;
        }
        String token = com.lianshengtong.common.utils.JwtUtil.generateToken(1001L, 0, "lsc-user-service");
        assertTrue(com.lianshengtong.common.utils.JwtUtil.isValid(token));
    }

    @Test
    @DisplayName("JwtUtil: isValid 无效token返回false")
    void jwtUtil_isValid_invalidToken() {
        if (!jwtUtilAvailable) {
            Assumptions.assumeTrue(false, "JWT_SECRET not set, skipping JwtUtil test");
            return;
        }
        assertFalse(com.lianshengtong.common.utils.JwtUtil.isValid("invalid-token-string"));
        assertFalse(com.lianshengtong.common.utils.JwtUtil.isValid(null));
    }

    @Test
    @DisplayName("JwtUtil: parseToken 过期token抛出JwtValidationException")
    void jwtUtil_parseToken_expiredToken() {
        if (!jwtUtilAvailable) {
            Assumptions.assumeTrue(false, "JWT_SECRET not set, skipping JwtUtil test");
            return;
        }
        String token = com.lianshengtong.common.utils.JwtUtil.generateToken(1001L, 0, "lsc-user-service", 1);
        assertThrows(com.lianshengtong.common.utils.JwtUtil.JwtValidationException.class, () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            com.lianshengtong.common.utils.JwtUtil.parseToken(token);
        });
    }

    @Test
    @DisplayName("JwtUtil: parseToken 签名被篡改抛出异常")
    void jwtUtil_parseToken_tamperedToken() {
        if (!jwtUtilAvailable) {
            Assumptions.assumeTrue(false, "JWT_SECRET not set, skipping JwtUtil test");
            return;
        }
        String token = com.lianshengtong.common.utils.JwtUtil.generateToken(1001L, 0, "lsc-user-service");
        String[] parts = token.split("\\.");
        StringBuilder tampered = new StringBuilder();
        tampered.append(parts[0]).append('.').append(parts[1]).append('.');
        byte[] sigBytes = java.util.Base64.getUrlDecoder().decode(parts[2]);
        if (sigBytes.length == 0) {
            Assumptions.assumeTrue(false, "签名为空,跳过");
            return;
        }
        sigBytes[sigBytes.length - 1] ^= 0x01;
        tampered.append(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes));
        assertThrows(com.lianshengtong.common.utils.JwtUtil.JwtValidationException.class,
                () -> com.lianshengtong.common.utils.JwtUtil.parseToken(tampered.toString()));
    }

    @Test
    @DisplayName("JwtUtil: getUserId 正确提取用户ID")
    void jwtUtil_getUserId_success() {
        if (!jwtUtilAvailable) {
            Assumptions.assumeTrue(false, "JWT_SECRET not set, skipping JwtUtil test");
            return;
        }
        String token = com.lianshengtong.common.utils.JwtUtil.generateToken(2002L, 1, "lsc-user-service");
        assertEquals(2002L, com.lianshengtong.common.utils.JwtUtil.getUserId(token));
    }

    @Test
    @DisplayName("JwtUtil: getUserType 正确提取用户类型")
    void jwtUtil_getUserType_success() {
        if (!jwtUtilAvailable) {
            Assumptions.assumeTrue(false, "JWT_SECRET not set, skipping JwtUtil test");
            return;
        }
        String token = com.lianshengtong.common.utils.JwtUtil.generateToken(2002L, 1, "lsc-user-service");
        assertEquals(1, com.lianshengtong.common.utils.JwtUtil.getUserType(token));
    }

    @Test
    @DisplayName("JwtUtil: getIssuer 正确提取签发者")
    void jwtUtil_getIssuer_success() {
        if (!jwtUtilAvailable) {
            Assumptions.assumeTrue(false, "JWT_SECRET not set, skipping JwtUtil test");
            return;
        }
        String token = com.lianshengtong.common.utils.JwtUtil.generateToken(2002L, 0, "lsc-admin-service");
        assertEquals("lsc-admin-service", com.lianshengtong.common.utils.JwtUtil.getIssuer(token));
    }

    @Test
    @DisplayName("JwtUtil: generateToken 使用自定义过期时间")
    void jwtUtil_generateToken_customExpiration() {
        if (!jwtUtilAvailable) {
            Assumptions.assumeTrue(false, "JWT_SECRET not set, skipping JwtUtil test");
            return;
        }
        String token = com.lianshengtong.common.utils.JwtUtil.generateToken(1001L, 0, "lsc-user-service", 3600000L);
        assertNotNull(token);
        assertTrue(com.lianshengtong.common.utils.JwtUtil.isValid(token));
    }

    // ============== TracingConfig 测试 ==============

    @Test
    @DisplayName("TracingConfig: 默认构造函数初始化")
    void tracingConfig_defaultConstructor() {
        TracingConfig config = new TracingConfig();
        assertFalse(config.getTracingEnabled());
        assertNull(config.getServiceName());
    }

    @Test
    @DisplayName("TracingConfig: 带参构造函数正确设置字段")
    void tracingConfig_parameterizedConstructor() {
        TracingConfig config = new TracingConfig(true, "test-service");
        assertTrue(config.getTracingEnabled());
        assertEquals("test-service", config.getServiceName());
    }

    @Test
    @DisplayName("TracingConfig: setTracingEnabled 正确设置和获取")
    void tracingConfig_setTracingEnabled() {
        TracingConfig config = new TracingConfig();
        config.setTracingEnabled(true);
        assertTrue(config.getTracingEnabled());
        config.setTracingEnabled(false);
        assertFalse(config.getTracingEnabled());
    }

    @Test
    @DisplayName("TracingConfig: setServiceName 正确设置和获取")
    void tracingConfig_setServiceName() {
        TracingConfig config = new TracingConfig();
        config.setServiceName("my-service");
        assertEquals("my-service", config.getServiceName());
    }

    // ============== R class 测试 ==============

    @Test
    @DisplayName("R: ok() 无data返回成功状态")
    void r_ok_noData() {
        R<String> result = R.ok();
        assertTrue(result.isSuccess());
        assertEquals(0, result.getCode());
        assertEquals("success", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("R: ok(data) 返回携带数据的成功状态")
    void r_ok_withData() {
        String data = "hello";
        R<String> result = R.ok(data);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals(data, result.getData());
    }

    @Test
    @DisplayName("R: ok(message, data) 返回自定义消息的成功状态")
    void r_ok_withMessageAndData() {
        R<Integer> result = R.ok("created", 201);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getCode());
        assertEquals("created", result.getMessage());
        assertEquals(Integer.valueOf(201), result.getData());
    }

    @Test
    @DisplayName("R: fail(message) 返回默认500错误")
    void r_fail_message() {
        R<Void> result = R.fail("something wrong");
        assertFalse(result.isSuccess());
        assertEquals(500, result.getCode());
        assertEquals("something wrong", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("R: fail(code, message) 返回自定义错误码")
    void r_fail_codeAndMessage() {
        R<Void> result = R.fail(404, "not found");
        assertFalse(result.isSuccess());
        assertEquals(404, result.getCode());
        assertEquals("not found", result.getMessage());
    }

    @Test
    @DisplayName("R: isSuccess code非0返回false")
    void r_isSuccess_nonZeroCode() {
        R<Void> result = R.fail(403, "forbidden");
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("R: getTimestamp 返回正值")
    void r_getTimestamp_positive() {
        long before = System.currentTimeMillis();
        R<?> result = R.ok();
        long after = System.currentTimeMillis();
        assertTrue(result.getTimestamp() >= before);
        assertTrue(result.getTimestamp() <= after);
    }

    @Test
    @DisplayName("R: setData 和 getData 正确工作")
    void r_setData_getData() {
        R<String> result = R.ok();
        assertNull(result.getData());
        result.setData("new-data");
        assertEquals("new-data", result.getData());
    }

    @Test
    @DisplayName("R: setMessage 和 getMessage 正确工作")
    void r_setMessage_getMessage() {
        R<String> result = R.ok();
        result.setMessage("custom");
        assertEquals("custom", result.getMessage());
    }

    @Test
    @DisplayName("R: setCode 和 getCode 正确工作")
    void r_setCode_getCode() {
        R<String> result = R.ok();
        result.setCode(200);
        assertEquals(200, result.getCode());
    }

    @Test
    @DisplayName("R: setTimestamp 和 getTimestamp 正确工作")
    void r_setTimestamp_getTimestamp() {
        R<String> result = R.ok();
        long ts = 1700000000000L;
        result.setTimestamp(ts);
        assertEquals(ts, result.getTimestamp());
    }

    // ============== AdminRoleAspect 测试 ==============

    @Test
    @DisplayName("AdminRoleAspect: 默认构造函数")
    void adminRoleAspect_defaultConstructor() {
        AdminRoleAspect aspect = new AdminRoleAspect();
        assertNull(aspect.getRequest());
    }

    @Test
    @DisplayName("AdminRoleAspect: 带参构造函数正确设置request")
    void adminRoleAspect_parameterizedConstructor() {
        jakarta.servlet.http.HttpServletRequest mockRequest =
                org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        AdminRoleAspect aspect = new AdminRoleAspect(mockRequest);
        assertEquals(mockRequest, aspect.getRequest());
    }

    @Test
    @DisplayName("AdminRoleAspect: setRequest 和 getRequest 正确工作")
    void adminRoleAspect_setGetRequest() {
        AdminRoleAspect aspect = new AdminRoleAspect();
        assertNull(aspect.getRequest());
        jakarta.servlet.http.HttpServletRequest mockRequest =
                org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        aspect.setRequest(mockRequest);
        assertEquals(mockRequest, aspect.getRequest());
    }

    // ============== MessageProducer 内部类测试 ==============

    @Test
    @DisplayName("EvidenceMessage: 无参构造+setter/getter")
    void evidenceMessage_defaultConstructor() {
        EvidenceMessage msg = new EvidenceMessage();
        msg.setBatchNo("B001");
        msg.setOperationType("CREATE");
        msg.setBusinessId("BIZ-123");
        msg.setDataHash("abc123");
        msg.setRawData("{\"key\":\"value\"}");

        assertEquals("B001", msg.getBatchNo());
        assertEquals("CREATE", msg.getOperationType());
        assertEquals("BIZ-123", msg.getBusinessId());
        assertEquals("abc123", msg.getDataHash());
        assertEquals("{\"key\":\"value\"}", msg.getRawData());
    }

    @Test
    @DisplayName("EvidenceMessage: 全参构造函数")
    void evidenceMessage_allArgsConstructor() {
        EvidenceMessage msg = new EvidenceMessage("B002", "UPDATE", "BIZ-456", "def456", "raw-data");

        assertEquals("B002", msg.getBatchNo());
        assertEquals("UPDATE", msg.getOperationType());
        assertEquals("BIZ-456", msg.getBusinessId());
        assertEquals("def456", msg.getDataHash());
        assertEquals("raw-data", msg.getRawData());
    }

    @Test
    @DisplayName("FirstOrderMessage: 无参构造+setter/getter")
    void firstOrderMessage_defaultConstructor() {
        FirstOrderMessage msg = new FirstOrderMessage();
        msg.setOrderNo("ORD-001");
        msg.setConsumerId(1001L);
        msg.setReferrerId(2002L);
        msg.setOrderAmount(5000L);

        assertEquals("ORD-001", msg.getOrderNo());
        assertEquals(Long.valueOf(1001L), msg.getConsumerId());
        assertEquals(Long.valueOf(2002L), msg.getReferrerId());
        assertEquals(Long.valueOf(5000L), msg.getOrderAmount());
    }

    @Test
    @DisplayName("FirstOrderMessage: 全参构造函数")
    void firstOrderMessage_allArgsConstructor() {
        FirstOrderMessage msg = new FirstOrderMessage("ORD-002", 1001L, 2002L, 3000L);

        assertEquals("ORD-002", msg.getOrderNo());
        assertEquals(Long.valueOf(1001L), msg.getConsumerId());
        assertEquals(Long.valueOf(2002L), msg.getReferrerId());
        assertEquals(Long.valueOf(3000L), msg.getOrderAmount());
    }

    @Test
    @DisplayName("RiskAlertMessage: 无参构造+setter/getter")
    void riskAlertMessage_defaultConstructor() {
        RiskAlertMessage msg = new RiskAlertMessage();
        msg.setUserId(5005L);
        msg.setRiskType("批量下单");
        msg.setRiskDetail("短时间内大量下单");
        msg.setAiRiskLevel(3);
        msg.setActionTaken("自动限制");

        assertEquals(Long.valueOf(5005L), msg.getUserId());
        assertEquals("批量下单", msg.getRiskType());
        assertEquals("短时间内大量下单", msg.getRiskDetail());
        assertEquals(Integer.valueOf(3), msg.getAiRiskLevel());
        assertEquals("自动限制", msg.getActionTaken());
    }

    @Test
    @DisplayName("RiskAlertMessage: 全参构造函数")
    void riskAlertMessage_allArgsConstructor() {
        RiskAlertMessage msg = new RiskAlertMessage(5005L, "高频套利", "IP异常", 2, "人工审核");

        assertEquals(Long.valueOf(5005L), msg.getUserId());
        assertEquals("高频套利", msg.getRiskType());
        assertEquals("IP异常", msg.getRiskDetail());
        assertEquals(Integer.valueOf(2), msg.getAiRiskLevel());
        assertEquals("人工审核", msg.getActionTaken());
    }

    @Test
    @DisplayName("EvidenceMessage: 所有字段均为null时正确处理")
    void evidenceMessage_nullFields() {
        EvidenceMessage msg = new EvidenceMessage();
        assertNull(msg.getBatchNo());
        assertNull(msg.getOperationType());
        assertNull(msg.getBusinessId());
        assertNull(msg.getDataHash());
        assertNull(msg.getRawData());
    }

    @Test
    @DisplayName("FirstOrderMessage: referrerId为null的场景")
    void firstOrderMessage_nullReferrer() {
        FirstOrderMessage msg = new FirstOrderMessage("ORD-003", 1001L, null, 1000L);
        assertNull(msg.getReferrerId());
        assertEquals("ORD-003", msg.getOrderNo());
    }

    @Test
    @DisplayName("RiskAlertMessage: aiRiskLevel为null的场景")
    void riskAlertMessage_nullAiLevel() {
        RiskAlertMessage msg = new RiskAlertMessage(1L, "type", "detail", null, "action");
        assertNull(msg.getAiRiskLevel());
        assertEquals(Long.valueOf(1L), msg.getUserId());
    }

    @Test
    @DisplayName("EvidenceMessage: builder模式等效于setters")
    void evidenceMessage_setterConsistency() {
        EvidenceMessage msg = new EvidenceMessage();
        msg.setBatchNo("B001");
        msg.setOperationType("CREATE");
        msg.setBusinessId("BIZ-123");
        msg.setDataHash("hash123");
        msg.setRawData("raw");

        EvidenceMessage msg2 = new EvidenceMessage("B001", "CREATE", "BIZ-123", "hash123", "raw");

        assertEquals(msg.getBatchNo(), msg2.getBatchNo());
        assertEquals(msg.getOperationType(), msg2.getOperationType());
        assertEquals(msg.getBusinessId(), msg2.getBusinessId());
        assertEquals(msg.getDataHash(), msg2.getDataHash());
        assertEquals(msg.getRawData(), msg2.getRawData());
    }

    @Test
    @DisplayName("R: ok()返回不同数据类型")
    void r_ok_differentDataTypes() {
        R<Integer> intResult = R.ok(42);
        assertEquals(Integer.valueOf(42), intResult.getData());

        R<Boolean> boolResult = R.ok(true);
        assertEquals(Boolean.TRUE, boolResult.getData());

        R<List<String>> listResult = R.ok(Arrays.asList("a", "b", "c"));
        assertEquals(3, listResult.getData().size());
    }

    @Test
    @DisplayName("R: fail()静态工厂方法多次调用返回独立实例")
    void r_fail_independentInstances() {
        R<String> r1 = R.fail("error1");
        R<String> r2 = R.fail(400, "error2");

        assertNotSame(r1, r2);
        assertEquals(500, r1.getCode());
        assertEquals(400, r2.getCode());
        assertEquals("error1", r1.getMessage());
        assertEquals("error2", r2.getMessage());
    }

    @Test
    @DisplayName("TracingConfig: 无参构造默认值为disabled和null")
    void tracingConfig_defaultValues() {
        TracingConfig config = new TracingConfig();
        assertFalse(config.getTracingEnabled());
        assertNull(config.getServiceName());
    }

    @Test
    @DisplayName("AdminRoleAspect: 多次setRequest返回最新值")
    void adminRoleAspect_multipleSetRequest() {
        AdminRoleAspect aspect = new AdminRoleAspect();
        jakarta.servlet.http.HttpServletRequest req1 =
                org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        jakarta.servlet.http.HttpServletRequest req2 =
                org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);

        aspect.setRequest(req1);
        assertEquals(req1, aspect.getRequest());

        aspect.setRequest(req2);
        assertEquals(req2, aspect.getRequest());
    }
}