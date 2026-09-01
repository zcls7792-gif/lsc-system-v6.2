package com.lianshengtong.common.util;

import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.lock.DistributedLock;
import com.lianshengtong.common.mq.RabbitMQConfig;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.common.security.AdminRoleAspect;
import com.lianshengtong.common.security.LogSanitizer;
import com.lianshengtong.common.security.RequireAdminRole;
import com.lianshengtong.common.security.XssRequestWrapper;
import com.lianshengtong.common.tracing.TracingConfig;
import com.lianshengtong.common.utils.AesEncryptUtil;
import com.lianshengtong.common.utils.JwtUtil;
import com.lianshengtong.common.utils.JwtUtil.JwtValidationException;
import com.lianshengtong.common.utils.OptimisticLockHelper;
import com.lianshengtong.common.utils.SnowflakeIdUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LSC Common P5 低覆盖率类单元测试")
@ExtendWith(MockitoExtension.class)
class CommonP5Test {

    private static boolean jwtUtilAvailable = false;

    static {
        try {
            Class.forName("com.lianshengtong.common.utils.JwtUtil");
            jwtUtilAvailable = true;
        } catch (Throwable e) {
            jwtUtilAvailable = false;
        }
    }

    @BeforeAll
    static void initJwtUtil() {
        if (!jwtUtilAvailable) {
            throw new RuntimeException("JwtUtil类加载失败，请确保JWT_SECRET环境变量已设置");
        }
    }

    // ==================== RabbitMQConfig 测试 ====================

    @Test
    @DisplayName("RabbitMQConfig: evidenceExchange 名称和持久化验证")
    void rabbitMQConfig_evidenceExchange_nameAndDurable() {
        RabbitMQConfig config = new RabbitMQConfig();
        DirectExchange ex = config.evidenceExchange();
        assertEquals(RabbitMQConfig.EXCHANGE_EVIDENCE, ex.getName());
        assertTrue(ex.isDurable());
    }

    @Test
    @DisplayName("RabbitMQConfig: evidenceQueue TTL和DLX参数验证")
    void rabbitMQConfig_evidenceQueue_ttlAndDlx() {
        RabbitMQConfig config = new RabbitMQConfig();
        Queue q = config.evidenceQueue();
        assertEquals(RabbitMQConfig.QUEUE_EVIDENCE, q.getName());
        Map<String, Object> args = q.getArguments();
        assertNotNull(args);
        assertEquals(86400000, args.get("x-message-ttl"));
        assertEquals(RabbitMQConfig.EXCHANGE_EVIDENCE, args.get("x-dead-letter-exchange"));
        assertEquals(RabbitMQConfig.RK_EVIDENCE_FAILOVER, args.get("x-dead-letter-routing-key"));
    }

    @Test
    @DisplayName("RabbitMQConfig: evidenceBinding 绑定关系验证")
    void rabbitMQConfig_evidenceBinding_binding关系() {
        RabbitMQConfig config = new RabbitMQConfig();
        Binding b = config.evidenceBinding();
        assertNotNull(b);
        assertEquals(Binding.DestinationType.QUEUE, b.getDestinationType());
        assertEquals(RabbitMQConfig.QUEUE_EVIDENCE, b.getDestination());
        assertEquals(RabbitMQConfig.EXCHANGE_EVIDENCE, b.getExchange());
        assertEquals(RabbitMQConfig.RK_EVIDENCE, b.getRoutingKey());
    }

    @Test
    @DisplayName("RabbitMQConfig: evidenceFailoverQueue 和 Binding")
    void rabbitMQConfig_evidenceFailoverQueue_andBinding() {
        RabbitMQConfig config = new RabbitMQConfig();
        Queue q = config.evidenceFailoverQueue();
        assertEquals(RabbitMQConfig.QUEUE_EVIDENCE_FAILOVER, q.getName());
        assertFalse(q.getArguments() != null && q.getArguments().containsKey("x-message-ttl"));

        Binding b = config.evidenceFailoverBinding();
        assertEquals(RabbitMQConfig.QUEUE_EVIDENCE_FAILOVER, b.getDestination());
        assertEquals(RabbitMQConfig.RK_EVIDENCE_FAILOVER, b.getRoutingKey());
    }

    @Test
    @DisplayName("RabbitMQConfig: releaseExchange 持久化验证")
    void rabbitMQConfig_releaseExchange_durable() {
        RabbitMQConfig config = new RabbitMQConfig();
        DirectExchange ex = config.releaseExchange();
        assertEquals(RabbitMQConfig.EXCHANGE_RELEASE, ex.getName());
        assertTrue(ex.isDurable());
    }

    @Test
    @DisplayName("RabbitMQConfig: dailyReleaseQueue 和 Binding")
    void rabbitMQConfig_dailyReleaseQueue_andBinding() {
        RabbitMQConfig config = new RabbitMQConfig();
        Queue q = config.dailyReleaseQueue();
        assertEquals(RabbitMQConfig.QUEUE_DAILY_RELEASE, q.getName());

        Binding b = config.dailyReleaseBinding();
        assertEquals(RabbitMQConfig.QUEUE_DAILY_RELEASE, b.getDestination());
        assertEquals(RabbitMQConfig.RK_DAILY_RELEASE, b.getRoutingKey());
        assertEquals(RabbitMQConfig.EXCHANGE_RELEASE, b.getExchange());
    }

    @Test
    @DisplayName("RabbitMQConfig: expireTransferQueue 和 Binding")
    void rabbitMQConfig_expireTransferQueue_andBinding() {
        RabbitMQConfig config = new RabbitMQConfig();
        Queue q = config.expireTransferQueue();
        assertEquals(RabbitMQConfig.QUEUE_EXPIRE_TRANSFER, q.getName());

        Binding b = config.expireTransferBinding();
        assertEquals(RabbitMQConfig.QUEUE_EXPIRE_TRANSFER, b.getDestination());
        assertEquals(RabbitMQConfig.RK_EXPIRE_TRANSFER, b.getRoutingKey());
    }

    @Test
    @DisplayName("RabbitMQConfig: orderExchange 持久化验证")
    void rabbitMQConfig_orderExchange_durable() {
        RabbitMQConfig config = new RabbitMQConfig();
        DirectExchange ex = config.orderExchange();
        assertEquals(RabbitMQConfig.EXCHANGE_ORDER, ex.getName());
        assertTrue(ex.isDurable());
    }

    @Test
    @DisplayName("RabbitMQConfig: firstOrderNotifyQueue 和 Binding")
    void rabbitMQConfig_firstOrderNotifyQueue_andBinding() {
        RabbitMQConfig config = new RabbitMQConfig();
        Queue q = config.firstOrderNotifyQueue();
        assertEquals(RabbitMQConfig.QUEUE_FIRST_ORDER_NOTIFY, q.getName());

        Binding b = config.firstOrderBinding();
        assertEquals(RabbitMQConfig.QUEUE_FIRST_ORDER_NOTIFY, b.getDestination());
        assertEquals(RabbitMQConfig.RK_FIRST_ORDER, b.getRoutingKey());
        assertEquals(RabbitMQConfig.EXCHANGE_ORDER, b.getExchange());
    }

    @Test
    @DisplayName("RabbitMQConfig: promotionFillQueue 和 Binding")
    void rabbitMQConfig_promotionFillQueue_andBinding() {
        RabbitMQConfig config = new RabbitMQConfig();
        Queue q = config.promotionFillQueue();
        assertEquals(RabbitMQConfig.QUEUE_PROMOTION_FILL, q.getName());

        Binding b = config.promotionFillBinding();
        assertEquals(RabbitMQConfig.QUEUE_PROMOTION_FILL, b.getDestination());
        assertEquals(RabbitMQConfig.RK_PROMOTION_FILL, b.getRoutingKey());
    }

    @Test
    @DisplayName("RabbitMQConfig: riskExchange riskAlertQueue riskAlertBinding")
    void rabbitMQConfig_riskExchange_andQueueAndBinding() {
        RabbitMQConfig config = new RabbitMQConfig();
        DirectExchange ex = config.riskExchange();
        assertEquals(RabbitMQConfig.EXCHANGE_RISK, ex.getName());
        assertTrue(ex.isDurable());

        Queue q = config.riskAlertQueue();
        assertEquals(RabbitMQConfig.QUEUE_RISK_ALERT, q.getName());

        Binding b = config.riskAlertBinding();
        assertEquals(RabbitMQConfig.QUEUE_RISK_ALERT, b.getDestination());
        assertEquals(RabbitMQConfig.RK_RISK_ALERT, b.getRoutingKey());
    }

    @Test
    @DisplayName("RabbitMQConfig: aiReviewExchange 持久化")
    void rabbitMQConfig_aiReviewExchange_durable() {
        RabbitMQConfig config = new RabbitMQConfig();
        DirectExchange ex = config.aiReviewExchange();
        assertEquals(RabbitMQConfig.EXCHANGE_AI_REVIEW, ex.getName());
        assertTrue(ex.isDurable());
    }

    @Test
    @DisplayName("RabbitMQConfig: productAiReviewQueue 和 Binding")
    void rabbitMQConfig_productAiReviewQueue_andBinding() {
        RabbitMQConfig config = new RabbitMQConfig();
        Queue q = config.productAiReviewQueue();
        assertEquals(RabbitMQConfig.QUEUE_PRODUCT_AI_REVIEW, q.getName());

        Binding b = config.productAiReviewBinding();
        assertEquals(RabbitMQConfig.QUEUE_PRODUCT_AI_REVIEW, b.getDestination());
        assertEquals(RabbitMQConfig.RK_PRODUCT_REVIEW, b.getRoutingKey());
        assertEquals(RabbitMQConfig.EXCHANGE_AI_REVIEW, b.getExchange());
    }

    @Test
    @DisplayName("RabbitMQConfig: b2bAiVerifyQueue 和 Binding")
    void rabbitMQConfig_b2bAiVerifyQueue_andBinding() {
        RabbitMQConfig config = new RabbitMQConfig();
        Queue q = config.b2bAiVerifyQueue();
        assertEquals(RabbitMQConfig.QUEUE_B2B_AI_VERIFY, q.getName());

        Binding b = config.b2bAiVerifyBinding();
        assertEquals(RabbitMQConfig.QUEUE_B2B_AI_VERIFY, b.getDestination());
        assertEquals(RabbitMQConfig.RK_B2B_VERIFY, b.getRoutingKey());
    }

    @Test
    @DisplayName("RabbitMQConfig: 所有交换机名称以lsc前缀开头")
    void rabbitMQConfig_allExchangeNames_startWithLsc() {
        RabbitMQConfig config = new RabbitMQConfig();
        assertTrue(config.evidenceExchange().getName().startsWith("lsc."));
        assertTrue(config.releaseExchange().getName().startsWith("lsc."));
        assertTrue(config.orderExchange().getName().startsWith("lsc."));
        assertTrue(config.riskExchange().getName().startsWith("lsc."));
        assertTrue(config.aiReviewExchange().getName().startsWith("lsc."));
    }

    @Test
    @DisplayName("RabbitMQConfig: 所有队列均可持久化且为durable")
    void rabbitMQConfig_allQueues_durable() {
        RabbitMQConfig config = new RabbitMQConfig();
        assertTrue(config.evidenceQueue().isDurable());
        assertTrue(config.evidenceFailoverQueue().isDurable());
        assertTrue(config.dailyReleaseQueue().isDurable());
        assertTrue(config.expireTransferQueue().isDurable());
        assertTrue(config.firstOrderNotifyQueue().isDurable());
        assertTrue(config.promotionFillQueue().isDurable());
        assertTrue(config.riskAlertQueue().isDurable());
        assertTrue(config.productAiReviewQueue().isDurable());
        assertTrue(config.b2bAiVerifyQueue().isDurable());
    }

    // ==================== JwtUtil 测试 ====================

    @Test
    @DisplayName("JwtUtil: generateToken 生成有效Token并可解析")
    void jwtUtil_generateToken_validToken() {
        String token = JwtUtil.generateToken(123L, 2, "lsc-user-service");
        assertNotNull(token);
        assertFalse(token.isBlank());
        assertTrue(JwtUtil.isValid(token));
    }

    @Test
    @DisplayName("JwtUtil: generateToken 带自定义过期时间")
    void jwtUtil_generateToken_customExpiration() {
        String token = JwtUtil.generateToken(456L, 1, "lsc-admin-service", 3600_000L);
        assertNotNull(token);
        assertTrue(JwtUtil.isValid(token));
    }

    @Test
    @DisplayName("JwtUtil: parseToken 解析有效Token")
    void jwtUtil_parseToken_validToken() {
        String token = JwtUtil.generateToken(100L, 3, "lsc-admin-service");
        Claims claims = JwtUtil.parseToken(token);
        assertNotNull(claims);
        assertEquals(100L, ((Number) claims.get("userId")).longValue());
        assertEquals(3, ((Number) claims.get("userType")).intValue());
        assertEquals("lsc-admin-service", claims.getIssuer());
    }

    @Test
    @DisplayName("JwtUtil: parseToken 解析无效Token抛JwtValidationException")
    void jwtUtil_parseToken_invalidToken() {
        assertThrows(JwtValidationException.class,
                () -> JwtUtil.parseToken("invalid-token-string"));
    }

    @Test
    @DisplayName("JwtUtil: parseToken 空字符串抛JwtValidationException")
    void jwtUtil_parseToken_emptyToken() {
        assertThrows(JwtValidationException.class,
                () -> JwtUtil.parseToken(""));
    }

    @Test
    @DisplayName("JwtUtil: parseToken 格式错误Token抛异常")
    void jwtUtil_parseToken_malformedToken() {
        assertThrows(JwtValidationException.class,
                () -> JwtUtil.parseToken("not-a-valid-jwt-token-just-random"));
    }

    @Test
    @DisplayName("JwtUtil: isValid 有效Token返回true")
    void jwtUtil_isValid_validToken() {
        String token = JwtUtil.generateToken(1L, 1, "lsc-user-service");
        assertTrue(JwtUtil.isValid(token));
    }

    @Test
    @DisplayName("JwtUtil: isValid 无效Token返回false")
    void jwtUtil_isValid_invalidToken() {
        assertFalse(JwtUtil.isValid("invalid-token"));
        assertFalse(JwtUtil.isValid(""));
    }

    @Test
    @DisplayName("JwtUtil: getUserId 正确提取用户ID")
    void jwtUtil_getUserId_extractCorrectly() {
        String token = JwtUtil.generateToken(999L, 2, "lsc-user-service");
        Long userId = JwtUtil.getUserId(token);
        assertEquals(999L, userId);
    }

    @Test
    @DisplayName("JwtUtil: getUserType 正确提取用户类型")
    void jwtUtil_getUserType_extractCorrectly() {
        String token = JwtUtil.generateToken(1L, 3, "lsc-admin-service");
        Integer userType = JwtUtil.getUserType(token);
        assertEquals(3, userType);
    }

    @Test
    @DisplayName("JwtUtil: getIssuer 正确提取签发者")
    void jwtUtil_getIssuer_extractCorrectly() {
        String token = JwtUtil.generateToken(1L, 1, "lsc-user-service");
        String issuer = JwtUtil.getIssuer(token);
        assertEquals("lsc-user-service", issuer);
    }

    @Test
    @DisplayName("JwtUtil: JwtValidationException 单参数构造")
    void jwtUtil_jwtValidationException_messageConstructor() {
        JwtValidationException ex = new JwtValidationException("测试错误");
        assertEquals("测试错误", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("JwtUtil: JwtValidationException 双参数构造")
    void jwtUtil_jwtValidationException_causeConstructor() {
        Throwable cause = new RuntimeException("原始异常");
        JwtValidationException ex = new JwtValidationException("包装异常", cause);
        assertEquals("包装异常", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    @DisplayName("JwtUtil: generateToken 不同userId生成不同Token")
    void jwtUtil_generateToken_uniqueForDifferentUsers() {
        String token1 = JwtUtil.generateToken(1L, 1, "issuer");
        String token2 = JwtUtil.generateToken(2L, 1, "issuer");
        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("JwtUtil: generateToken 不同userType生成不同Token")
    void jwtUtil_generateToken_uniqueForDifferentTypes() {
        String token1 = JwtUtil.generateToken(1L, 1, "issuer");
        String token2 = JwtUtil.generateToken(1L, 2, "issuer");
        assertNotEquals(token1, token2);
    }

    // ==================== XssRequestWrapper 测试 ====================

    @Test
    @DisplayName("XssRequestWrapper: getParameter 清除XSS脚本标签但保留文本")
    void xssRequestWrapper_getParameter_sanitizeScript() {
        Map<String, String[]> params = new HashMap<>();
        params.put("name", new String[]{"<script>alert(1)</script>"});
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getParameterMap()).thenReturn(params);

        XssRequestWrapper wrapper = new XssRequestWrapper(mockReq);
        String sanitized = wrapper.getParameter("name");
        assertNotNull(sanitized);
        assertFalse(sanitized.contains("<"));
        assertFalse(sanitized.contains(">"));
    }

    @Test
    @DisplayName("XssRequestWrapper: getParameter 无参数返回null")
    void xssRequestWrapper_getParameter_noParam() {
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getParameterMap()).thenReturn(new HashMap<>());

        XssRequestWrapper wrapper = new XssRequestWrapper(mockReq);
        assertNull(wrapper.getParameter("nonexistent"));
    }

    @Test
    @DisplayName("XssRequestWrapper: getParameterMap 返回不可修改的Map")
    void xssRequestWrapper_getParameterMap_unmodifiable() {
        Map<String, String[]> params = new HashMap<>();
        params.put("key", new String[]{"value"});
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getParameterMap()).thenReturn(params);

        XssRequestWrapper wrapper = new XssRequestWrapper(mockReq);
        Map<String, String[]> map = wrapper.getParameterMap();
        assertThrows(UnsupportedOperationException.class,
                () -> map.put("newKey", new String[]{"newValue"}));
    }

    @Test
    @DisplayName("XssRequestWrapper: getParameterNames 返回正确枚举")
    void xssRequestWrapper_getParameterNames_correct() {
        Map<String, String[]> params = new HashMap<>();
        params.put("a", new String[]{"1"});
        params.put("b", new String[]{"2"});
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getParameterMap()).thenReturn(params);

        XssRequestWrapper wrapper = new XssRequestWrapper(mockReq);
        Enumeration<String> names = wrapper.getParameterNames();
        List<String> nameList = new ArrayList<>();
        while (names.hasMoreElements()) {
            nameList.add(names.nextElement());
        }
        assertTrue(nameList.contains("a"));
        assertTrue(nameList.contains("b"));
    }

    @Test
    @DisplayName("XssRequestWrapper: getParameterValues 返回正确值")
    void xssRequestWrapper_getParameterValues_correct() {
        Map<String, String[]> params = new HashMap<>();
        params.put("colors", new String[]{"red", "blue"});
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getParameterMap()).thenReturn(params);

        XssRequestWrapper wrapper = new XssRequestWrapper(mockReq);
        String[] values = wrapper.getParameterValues("colors");
        assertNotNull(values);
        assertEquals(2, values.length);
        assertEquals("red", values[0]);
        assertEquals("blue", values[1]);
    }

    @Test
    @DisplayName("XssRequestWrapper: getParameterValues 不存在返回null")
    void xssRequestWrapper_getParameterValues_notExist() {
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getParameterMap()).thenReturn(new HashMap<>());

        XssRequestWrapper wrapper = new XssRequestWrapper(mockReq);
        assertNull(wrapper.getParameterValues("missing"));
    }

    @Test
    @DisplayName("XssRequestWrapper: getHeader 清除XSS标签")
    void xssRequestWrapper_getHeader_sanitized() {
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getParameterMap()).thenReturn(new HashMap<>());
        when(mockReq.getHeader("X-Custom")).thenReturn("<img src=x onerror=alert(1)>");

        XssRequestWrapper wrapper = new XssRequestWrapper(mockReq);
        String header = wrapper.getHeader("X-Custom");
        assertNotNull(header);
        assertFalse(header.contains("<"));
    }

    @Test
    @DisplayName("XssRequestWrapper: getHeaders 清除所有XSS标签")
    void xssRequestWrapper_getHeaders_sanitized() {
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getParameterMap()).thenReturn(new HashMap<>());
        Vector<String> headers = new Vector<>();
        headers.add("<script>alert('xss')</script>");
        headers.add("normal-value");
        when(mockReq.getHeaders("X-Test")).thenReturn(headers.elements());

        XssRequestWrapper wrapper = new XssRequestWrapper(mockReq);
        Enumeration<String> result = wrapper.getHeaders("X-Test");
        List<String> list = new ArrayList<>();
        while (result.hasMoreElements()) {
            list.add(result.nextElement());
        }
        assertEquals(2, list.size());
        assertFalse(list.get(0).contains("<"));
        assertEquals("normal-value", list.get(1));
    }

    @Test
    @DisplayName("XssRequestWrapper: 构造函数清除参数名中的XSS标签")
    void xssRequestWrapper_constructor_sanitizeParamNames() {
        Map<String, String[]> params = new HashMap<>();
        params.put("<script>key</script>", new String[]{"val"});
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getParameterMap()).thenReturn(params);

        XssRequestWrapper wrapper = new XssRequestWrapper(mockReq);
        String[] values = wrapper.getParameterValues("key");
        assertNotNull(values);
    }

    @Test
    @DisplayName("XssRequestWrapper: 复杂HTML标签XSS清除")
    void xssRequestWrapper_complexXSS_sanitized() {
        Map<String, String[]> params = new HashMap<>();
        params.put("content", new String[]{
                "<div onmouseover=alert(1)>Click Me</div>",
                "<a href='javascript:alert(1)'>Link</a>"
        });
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getParameterMap()).thenReturn(params);

        XssRequestWrapper wrapper = new XssRequestWrapper(mockReq);
        String[] values = wrapper.getParameterValues("content");
        assertNotNull(values);
        for (String v : values) {
            assertFalse(v.contains("<"));
            assertFalse(v.contains(">"));
        }
    }

    // ==================== AdminRoleAspect 测试 ====================

    @Test
    @DisplayName("AdminRoleAspect: 无参构造函数")
    void adminRoleAspect_noArgConstructor() {
        AdminRoleAspect aspect = new AdminRoleAspect();
        assertNotNull(aspect);
        assertNull(aspect.getRequest());
    }

    @Test
    @DisplayName("AdminRoleAspect: 带参构造函数")
    void adminRoleAspect_argConstructor() {
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        AdminRoleAspect aspect = new AdminRoleAspect(mockReq);
        assertEquals(mockReq, aspect.getRequest());
    }

    @Test
    @DisplayName("AdminRoleAspect: setRequest/getRequest")
    void adminRoleAspect_setGetRequest() {
        AdminRoleAspect aspect = new AdminRoleAspect();
        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        aspect.setRequest(mockReq);
        assertEquals(mockReq, aspect.getRequest());
    }

    @Test
    @DisplayName("AdminRoleAspect: checkRole 缺少请求上下文抛异常")
    void adminRoleAspect_checkRole_noRequestContext() throws Exception {
        AdminRoleAspect aspect = new AdminRoleAspect();
        RequireAdminRole requireAdminRole = mock(RequireAdminRole.class);
        when(requireAdminRole.value()).thenReturn(1);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(TestAdminRoleAspect.class.getDeclaredMethod("dummyMethod"));
        when(pjp.getSignature()).thenReturn(sig);

        try (MockedStatic<RequestContextHolder> ctx = mockStatic(RequestContextHolder.class)) {
            ctx.when(RequestContextHolder::getRequestAttributes).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> aspect.checkRole(pjp, requireAdminRole));
            assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("AdminRoleAspect: checkRole 缺少角色头抛异常")
    void adminRoleAspect_checkRole_missingRoleHeader() throws Exception {
        AdminRoleAspect aspect = new AdminRoleAspect();
        RequireAdminRole requireAdminRole = mock(RequireAdminRole.class);
        when(requireAdminRole.value()).thenReturn(1);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(TestAdminRoleAspect.class.getDeclaredMethod("dummyMethod"));
        when(pjp.getSignature()).thenReturn(sig);

        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getHeader("X-Admin-Role")).thenReturn(null);

        ServletRequestAttributes attrs = mock(ServletRequestAttributes.class);
        when(attrs.getRequest()).thenReturn(mockReq);

        try (MockedStatic<RequestContextHolder> ctx = mockStatic(RequestContextHolder.class)) {
            ctx.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);

            BizException ex = assertThrows(BizException.class,
                    () -> aspect.checkRole(pjp, requireAdminRole));
            assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("AdminRoleAspect: checkRole 角色头格式错误抛异常")
    void adminRoleAspect_checkRole_invalidRoleFormat() throws Exception {
        AdminRoleAspect aspect = new AdminRoleAspect();
        RequireAdminRole requireAdminRole = mock(RequireAdminRole.class);
        when(requireAdminRole.value()).thenReturn(1);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(TestAdminRoleAspect.class.getDeclaredMethod("dummyMethod"));
        when(pjp.getSignature()).thenReturn(sig);

        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getHeader("X-Admin-Role")).thenReturn("not_a_number");

        ServletRequestAttributes attrs = mock(ServletRequestAttributes.class);
        when(attrs.getRequest()).thenReturn(mockReq);

        try (MockedStatic<RequestContextHolder> ctx = mockStatic(RequestContextHolder.class)) {
            ctx.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);

            BizException ex = assertThrows(BizException.class,
                    () -> aspect.checkRole(pjp, requireAdminRole));
            assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("AdminRoleAspect: checkRole 权限不足抛异常")
    void adminRoleAspect_checkRole_insufficientRole() throws Exception {
        AdminRoleAspect aspect = new AdminRoleAspect();
        RequireAdminRole requireAdminRole = mock(RequireAdminRole.class);
        when(requireAdminRole.value()).thenReturn(3);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(TestAdminRoleAspect.class.getDeclaredMethod("dummyMethod"));
        when(pjp.getSignature()).thenReturn(sig);

        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getHeader("X-Admin-Role")).thenReturn("1");
        when(mockReq.getHeader("X-User-Id")).thenReturn("100");

        ServletRequestAttributes attrs = mock(ServletRequestAttributes.class);
        when(attrs.getRequest()).thenReturn(mockReq);

        try (MockedStatic<RequestContextHolder> ctx = mockStatic(RequestContextHolder.class)) {
            ctx.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);

            BizException ex = assertThrows(BizException.class,
                    () -> aspect.checkRole(pjp, requireAdminRole));
            assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("AdminRoleAspect: checkRole 角色足够时正常执行")
    void adminRoleAspect_checkRole_sufficientRole() throws Throwable {
        AdminRoleAspect aspect = new AdminRoleAspect();
        RequireAdminRole requireAdminRole = mock(RequireAdminRole.class);
        when(requireAdminRole.value()).thenReturn(1);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(TestAdminRoleAspect.class.getDeclaredMethod("dummyMethod"));
        when(pjp.getSignature()).thenReturn(sig);

        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        when(mockReq.getHeader("X-Admin-Role")).thenReturn("2");

        ServletRequestAttributes attrs = mock(ServletRequestAttributes.class);
        when(attrs.getRequest()).thenReturn(mockReq);

        try (MockedStatic<RequestContextHolder> ctx = mockStatic(RequestContextHolder.class)) {
            ctx.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);
            when(pjp.proceed()).thenReturn("success");

            Object result = aspect.checkRole(pjp, requireAdminRole);
            assertEquals("success", result);
        }
    }

    static class TestAdminRoleAspect {
        public void dummyMethod() {}
    }

    // ==================== SnowflakeIdUtil 测试 ====================

    @Test
    @DisplayName("SnowflakeIdUtil: 私有构造函数正常创建实例")
    void snowflakeIdUtil_constructor_validParams() throws Exception {
        Constructor<SnowflakeIdUtil> ctor = SnowflakeIdUtil.class.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);
        SnowflakeIdUtil idGen = ctor.newInstance(1L, 1L);
        assertNotNull(idGen);
        assertEquals(1L, idGen.getWorkerId());
        assertEquals(1L, idGen.getDatacenterId());
    }

    @Test
    @DisplayName("SnowflakeIdUtil: workerId超过最大值抛异常")
    void snowflakeIdUtil_constructor_workerIdTooLarge() throws Exception {
        Constructor<SnowflakeIdUtil> ctor = SnowflakeIdUtil.class.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> ctor.newInstance(32L, 1L));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Test
    @DisplayName("SnowflakeIdUtil: workerId为负数抛异常")
    void snowflakeIdUtil_constructor_workerIdNegative() throws Exception {
        Constructor<SnowflakeIdUtil> ctor = SnowflakeIdUtil.class.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> ctor.newInstance(-1L, 1L));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Test
    @DisplayName("SnowflakeIdUtil: datacenterId超过最大值抛异常")
    void snowflakeIdUtil_constructor_datacenterIdTooLarge() throws Exception {
        Constructor<SnowflakeIdUtil> ctor = SnowflakeIdUtil.class.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> ctor.newInstance(1L, 32L));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Test
    @DisplayName("SnowflakeIdUtil: datacenterId为负数抛异常")
    void snowflakeIdUtil_constructor_datacenterIdNegative() throws Exception {
        Constructor<SnowflakeIdUtil> ctor = SnowflakeIdUtil.class.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> ctor.newInstance(1L, -1L));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Test
    @DisplayName("SnowflakeIdUtil: nextId 生成唯一递增ID")
    void snowflakeIdUtil_nextId_uniqueAndIncremental() throws Exception {
        Constructor<SnowflakeIdUtil> ctor = SnowflakeIdUtil.class.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);
        SnowflakeIdUtil idGen = ctor.newInstance(1L, 1L);

        long id1 = idGen.nextId();
        long id2 = idGen.nextId();
        long id3 = idGen.nextId();

        assertNotEquals(id1, id2);
        assertNotEquals(id2, id3);
        assertTrue(id2 > id1);
        assertTrue(id3 > id2);
    }

    @Test
    @DisplayName("SnowflakeIdUtil: getInstance 返回单例")
    void snowflakeIdUtil_getInstance_singleton() {
        SnowflakeIdUtil inst1 = SnowflakeIdUtil.getInstance();
        SnowflakeIdUtil inst2 = SnowflakeIdUtil.getInstance();
        assertSame(inst1, inst2);
        assertNotNull(inst1);
    }

    @Test
    @DisplayName("SnowflakeIdUtil: id() 静态方法生成ID")
    void snowflakeIdUtil_id_staticWorks() {
        long id = SnowflakeIdUtil.id();
        assertTrue(id > 0);
    }

    @Test
    @DisplayName("SnowflakeIdUtil: id() 与 getInstance().nextId() 都能生成有效ID")
    void snowflakeIdUtil_idAndInstance_nextIdConsistency() {
        long staticId = SnowflakeIdUtil.id();
        long instanceId = SnowflakeIdUtil.getInstance().nextId();
        assertTrue(staticId > 0);
        assertTrue(instanceId > 0);
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 不同workerId/datacenterId组合生成不同ID")
    void snowflakeIdUtil_differentWorkerDatacenter_combo() throws Exception {
        Constructor<SnowflakeIdUtil> ctor = SnowflakeIdUtil.class.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);

        SnowflakeIdUtil gen1 = ctor.newInstance(0L, 0L);
        SnowflakeIdUtil gen2 = ctor.newInstance(31L, 31L);

        long id1 = gen1.nextId();
        long id2 = gen2.nextId();

        assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 批量生成ID无重复")
    void snowflakeIdUtil_batch_noDuplicates() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(SnowflakeIdUtil.id());
        }
        assertEquals(1000, ids.size());
    }

    @Test
    @DisplayName("SnowflakeIdUtil: getWorkerId/getDatacenterId 返回正确值")
    void snowflakeIdUtil_getters_returnCorrect() throws Exception {
        Constructor<SnowflakeIdUtil> ctor = SnowflakeIdUtil.class.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);
        SnowflakeIdUtil idGen = ctor.newInstance(5L, 10L);
        assertEquals(5L, idGen.getWorkerId());
        assertEquals(10L, idGen.getDatacenterId());
    }

    @Test
    @DisplayName("SnowflakeIdUtil: sequence和lastTimestamp 非空")
    void snowflakeIdUtil_atomicFields_nonNull() throws Exception {
        Constructor<SnowflakeIdUtil> ctor = SnowflakeIdUtil.class.getDeclaredConstructor(long.class, long.class);
        ctor.setAccessible(true);
        SnowflakeIdUtil idGen = ctor.newInstance(2L, 3L);
        assertNotNull(idGen.getSequence());
        assertNotNull(idGen.getLastTimestamp());
    }

    // ==================== AesEncryptUtil 测试 ====================

    @Test
    @DisplayName("AesEncryptUtil: encrypt/decrypt 往返正确")
    void aesEncryptUtil_encryptDecrypt_roundTrip() {
        String plain = "敏感数据13812345678";
        String encrypted = AesEncryptUtil.encrypt(plain);
        assertNotNull(encrypted);
        assertNotEquals(plain, encrypted);
        String decrypted = AesEncryptUtil.decrypt(encrypted);
        assertEquals(plain, decrypted);
    }

    @Test
    @DisplayName("AesEncryptUtil: encrypt null返回null")
    void aesEncryptUtil_encryptNull_returnsNull() {
        assertNull(AesEncryptUtil.encrypt(null));
    }

    @Test
    @DisplayName("AesEncryptUtil: decrypt null返回null")
    void aesEncryptUtil_decryptNull_returnsNull() {
        assertNull(AesEncryptUtil.decrypt(null));
    }

    @Test
    @DisplayName("AesEncryptUtil: encrypt 空字符串加密")
    void aesEncryptUtil_encryptEmptyString() {
        String encrypted = AesEncryptUtil.encrypt("");
        assertNotNull(encrypted);
        String decrypted = AesEncryptUtil.decrypt(encrypted);
        assertEquals("", decrypted);
    }

    @Test
    @DisplayName("AesEncryptUtil: 中文加解密正确")
    void aesEncryptUtil_chinese_encryptDecrypt() {
        String chinese = "张三 李四 王五";
        String encrypted = AesEncryptUtil.encrypt(chinese);
        String decrypted = AesEncryptUtil.decrypt(encrypted);
        assertEquals(chinese, decrypted);
    }

    @Test
    @DisplayName("AesEncryptUtil: maskMobile 各种长度")
    void aesEncryptUtil_maskMobile_variousLengths() {
        assertNull(AesEncryptUtil.maskMobile(null));
        assertEquals("138", AesEncryptUtil.maskMobile("138"));
        assertEquals("138123", AesEncryptUtil.maskMobile("138123"));
        assertEquals("138****5678", AesEncryptUtil.maskMobile("13812345678"));
    }

    @Test
    @DisplayName("AesEncryptUtil: maskIdCard 各种长度")
    void aesEncryptUtil_maskIdCard_variousLengths() {
        assertNull(AesEncryptUtil.maskIdCard(null));
        assertEquals("1101", AesEncryptUtil.maskIdCard("1101"));
        assertEquals("123456789", AesEncryptUtil.maskIdCard("123456789"));
        assertEquals("110***********1234", AesEncryptUtil.maskIdCard("110101199001011234"));
    }

    @Test
    @DisplayName("AesEncryptUtil: maskName 各种长度")
    void aesEncryptUtil_maskName_variousLengths() {
        assertNull(AesEncryptUtil.maskName(null));
        assertEquals("张", AesEncryptUtil.maskName("张"));
        assertEquals("张*", AesEncryptUtil.maskName("张三"));
        assertEquals("张*丰", AesEncryptUtil.maskName("张三丰"));
        assertEquals("张**丰", AesEncryptUtil.maskName("张三四丰"));
        assertEquals("张***五", AesEncryptUtil.maskName("张三四丰五"));
    }

    // ==================== DistributedLock 测试 ====================

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Test
    @DisplayName("DistributedLock: 无参构造函数")
    void distributedLock_noArgConstructor() {
        DistributedLock lock = new DistributedLock();
        assertNotNull(lock);
        assertNull(lock.getRedissonClient());
    }

    @Test
    @DisplayName("DistributedLock: 带参构造函数设置RedissonClient")
    void distributedLock_argConstructor() {
        DistributedLock lock = new DistributedLock(redissonClient);
        assertEquals(redissonClient, lock.getRedissonClient());
    }

    @Test
    @DisplayName("DistributedLock: setRedissonClient/getRedissonClient")
    void distributedLock_setGetRedissonClient() {
        DistributedLock lock = new DistributedLock();
        lock.setRedissonClient(redissonClient);
        assertEquals(redissonClient, lock.getRedissonClient());
    }

    @Test
    @DisplayName("DistributedLock: executeWithLock Supplier成功获取锁并执行")
    void distributedLock_executeWithLock_supplierSuccess() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLock lock = new DistributedLock(redissonClient);
        String result = lock.executeWithLock("key1", () -> "success");
        assertEquals("success", result);
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("DistributedLock: executeWithLock 自定义TTL参数")
    void distributedLock_executeWithLock_customTTL() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(eq(5L), eq(60L), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLock lock = new DistributedLock(redissonClient);
        Integer result = lock.executeWithLock("custom", 5L, 60L, () -> 42);
        assertEquals(Integer.valueOf(42), result);
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("DistributedLock: executeWithLock Runnable版本")
    void distributedLock_executeWithLock_runnable() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLock lock = new DistributedLock(redissonClient);
        StringBuilder sb = new StringBuilder();
        lock.executeWithLock("rnKey", () -> sb.append("done"));
        assertEquals("done", sb.toString());
    }

    @Test
    @DisplayName("DistributedLock: 锁被中断时恢复中断标志")
    void distributedLock_lockInterrupted_restoresFlag() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new InterruptedException("测试中断"));

        DistributedLock lock = new DistributedLock(redissonClient);
        try {
            lock.executeWithLock("intKey", () -> "test");
            fail("期望RuntimeException");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("获取锁被中断"));
            assertTrue(Thread.currentThread().isInterrupted());
        }
    }

    @Test
    @DisplayName("DistributedLock: 锁未被当前线程持有时不释放")
    void distributedLock_lockNotHeld_noUnlock() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        DistributedLock lock = new DistributedLock(redissonClient);
        lock.executeWithLock("notHeld", () -> "val");
        verify(rLock, never()).unlock();
    }

    @Test
    @DisplayName("DistributedLock: executeWithMultiLock 双锁正确获取")
    void distributedLock_executeWithMultiLock_acquireBoth() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLock lock = new DistributedLock(redissonClient);
        String result = lock.executeWithMultiLock(100L, 200L, () -> "multi");
        assertEquals("multi", result);

        verify(redissonClient).getLock("lsc:lock:user:100");
        verify(redissonClient).getLock("lsc:lock:user:200");
    }

    @Test
    @DisplayName("DistributedLock: executeWithMultiLock 逆序ID仍排序加锁")
    void distributedLock_executeWithMultiLock_reverseIds_sorted() {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        } catch (InterruptedException e) {
            fail("不应抛出异常");
        }
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLock lock = new DistributedLock(redissonClient);
        lock.executeWithMultiLock(500L, 200L, () -> null);

        verify(redissonClient).getLock("lsc:lock:user:200");
        verify(redissonClient).getLock("lsc:lock:user:500");
    }

    // ==================== TracingConfig 测试 ====================

    @Test
    @DisplayName("TracingConfig: 无参构造函数默认值")
    void tracingConfig_noArgConstructor_defaults() {
        TracingConfig config = new TracingConfig();
        assertFalse(config.getTracingEnabled());
    }

    @Test
    @DisplayName("TracingConfig: 带参构造函数设置值")
    void tracingConfig_argConstructor() {
        TracingConfig config = new TracingConfig(true, "test-service");
        assertTrue(config.getTracingEnabled());
        assertEquals("test-service", config.getServiceName());
    }

    @Test
    @DisplayName("TracingConfig: setTracingEnabled/getTracingEnabled")
    void tracingConfig_setGetTracingEnabled() {
        TracingConfig config = new TracingConfig();
        config.setTracingEnabled(true);
        assertTrue(config.getTracingEnabled());
    }

    @Test
    @DisplayName("TracingConfig: setServiceName/getServiceName")
    void tracingConfig_setGetServiceName() {
        TracingConfig config = new TracingConfig();
        config.setServiceName("my-service");
        assertEquals("my-service", config.getServiceName());
    }

    @Test
    @DisplayName("TracingConfig: init tracingEnabled=true 正常初始化")
    void tracingConfig_init_enabled() {
        TracingConfig config = new TracingConfig(true, "test-svc");
        assertDoesNotThrow(config::init);
    }

    @Test
    @DisplayName("TracingConfig: init tracingEnabled=false 正常初始化")
    void tracingConfig_init_disabled() {
        TracingConfig config = new TracingConfig(false, "test-svc");
        assertDoesNotThrow(config::init);
    }

    // ==================== LogSanitizer (security) 测试 ====================

    @Test
    @DisplayName("LogSanitizer: sanitize null返回null")
    void logSanitizer_sanitize_null() {
        assertNull(LogSanitizer.sanitize(null));
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 清除script标签")
    void logSanitizer_sanitize_scriptTag() {
        String result = LogSanitizer.sanitize("<script>alert(1)</script>");
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 清除HTML标签")
    void logSanitizer_sanitize_htmlTags() {
        String result = LogSanitizer.sanitize("<div><p>文本</p></div>");
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 替换换行符为下划线")
    void logSanitizer_sanitize_newlines() {
        String result = LogSanitizer.sanitize("line1\r\nline2\nline3");
        assertFalse(result.contains("\r"));
        assertFalse(result.contains("\n"));
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 清除img标签onerror")
    void logSanitizer_sanitize_imgOnerror() {
        String result = LogSanitizer.sanitize("<img src=x onerror=alert(1)>");
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 正常文本不改变")
    void logSanitizer_sanitize_normalText() {
        String normal = "这是一段正常的日志文本 没有任何HTML标签";
        String result = LogSanitizer.sanitize(normal);
        assertEquals(normal, result);
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 去除前后空格")
    void logSanitizer_sanitize_trimSpaces() {
        String result = LogSanitizer.sanitize("  <b>粗体</b>  ");
        assertFalse(result.startsWith(" "));
        assertFalse(result.endsWith(" "));
    }

    @Test
    @DisplayName("LogSanitizer: sanitize 多个标签连续清除")
    void logSanitizer_sanitize_multipleTags() {
        String result = LogSanitizer.sanitize("<script>alert(1)</script><img src=x><a href='#'>link</a>");
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
    }

    // ==================== OptimisticLockHelper 测试 ====================

    @Test
    @DisplayName("OptimisticLockHelper: 首次成功直接返回")
    void optimisticLockHelper_firstTrySuccess() {
        int result = OptimisticLockHelper.execute("test", () -> 1);
        assertEquals(1, result);
    }

    @Test
    @DisplayName("OptimisticLockHelper: 重试后成功")
    void optimisticLockHelper_retryThenSuccess() {
        AtomicInteger counter = new AtomicInteger(0);
        int result = OptimisticLockHelper.execute("test", 5, () -> {
            int c = counter.incrementAndGet();
            return c == 3 ? 1 : 0;
        });
        assertEquals(1, result);
        assertEquals(3, counter.get());
    }

    @Test
    @DisplayName("OptimisticLockHelper: 重试耗尽抛OptimisticLockingFailureException")
    void optimisticLockHelper_retryExhausted() {
        assertThrows(OptimisticLockingFailureException.class, () ->
                OptimisticLockHelper.execute(OptimisticLockHelper.SUPPRESSED_OP_PREFIX + "test", 3, () -> 0));
    }

    @Test
    @DisplayName("OptimisticLockHelper: 默认重试3次")
    void optimisticLockHelper_defaultRetries_3() {
        assertThrows(OptimisticLockingFailureException.class, () ->
                OptimisticLockHelper.execute(OptimisticLockHelper.SUPPRESSED_OP_PREFIX + "test", () -> 0));
    }

    @Test
    @DisplayName("OptimisticLockHelper: 零次重试立即失败")
    void optimisticLockHelper_zeroRetry_immediateFail() {
        assertThrows(OptimisticLockingFailureException.class, () ->
                OptimisticLockHelper.execute(OptimisticLockHelper.SUPPRESSED_OP_PREFIX + "zero", 0, () -> 0));
    }

    @Test
    @DisplayName("OptimisticLockHelper: 异常消息包含操作名称和重试次数")
    void optimisticLockHelper_exceptionMessageContainsDetails() {
        OptimisticLockingFailureException ex = assertThrows(
                OptimisticLockingFailureException.class, () ->
                OptimisticLockHelper.execute(OptimisticLockHelper.SUPPRESSED_OP_PREFIX + "my_op", 5, () -> 0));
        assertTrue(ex.getMessage().contains("my_op"));
        assertTrue(ex.getMessage().contains("5"));
    }

    @Test
    @DisplayName("OptimisticLockHelper: 重试间有间隔")
    void optimisticLockHelper_retryWithInterval() {
        AtomicInteger counter = new AtomicInteger(0);
        long start = System.currentTimeMillis();
        OptimisticLockHelper.execute("test", 10, () -> {
            int c = counter.incrementAndGet();
            return c >= 5 ? 1 : 0;
        });
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= 50 * 4, "重试应至少有200ms间隔");
    }

    @Test
    @DisplayName("OptimisticLockHelper: 被中断时抛异常")
    void optimisticLockHelper_interrupted() {
        AtomicInteger counter = new AtomicInteger(0);
        assertThrows(OptimisticLockingFailureException.class, () -> {
            OptimisticLockHelper.execute("interrupt_test", 10, () -> {
                counter.incrementAndGet();
                if (counter.get() == 2) {
                    Thread.currentThread().interrupt();
                }
                return 0;
            });
        });
    }
}