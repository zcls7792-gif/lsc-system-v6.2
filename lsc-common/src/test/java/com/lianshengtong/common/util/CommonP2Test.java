package com.lianshengtong.common.util;

import com.lianshengtong.common.aop.IdempotentAspect;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.idempotent.Idempotent;
import com.lianshengtong.common.mq.MessageProducer;
import com.lianshengtong.common.mq.RabbitMQConfig;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.common.security.CsrfTokenManager;
import com.lianshengtong.common.security.XssProtectionFilter;
import com.lianshengtong.common.security.XssRequestWrapper;
import com.lianshengtong.common.utils.RedisKeyPrefix;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LSC Common P2 核心类单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommonP2Test {

    // ==================== CsrfTokenManager 测试 ====================

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CsrfTokenManager csrfTokenManager;

    @BeforeEach
    void setUp() {
        csrfTokenManager = new CsrfTokenManager(stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("CsrfTokenManager: generateToken 带userId正确存储到Redis")
    void csrf_generateToken_withUserId_storesInRedis() {
        String sessionId = "session-001";
        String userId = "user-123";

        String token = csrfTokenManager.generateToken(sessionId, userId);

        assertNotNull(token);
        assertFalse(token.isEmpty());

        String expectedKey = RedisKeyPrefix.key("auth", "csrf", sessionId);
        verify(valueOperations).set(eq(expectedKey), contains("user-123:"), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("CsrfTokenManager: generateToken 无userId时仅存储token")
    void csrf_generateToken_withoutUserId_storesTokenOnly() {
        String sessionId = "session-002";

        String token = csrfTokenManager.generateToken(sessionId, null);

        assertNotNull(token);
        String expectedKey = RedisKeyPrefix.key("auth", "csrf", sessionId);
        verify(valueOperations).set(eq(expectedKey), eq(token), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("CsrfTokenManager: generateToken 返回32字节的Base64 URL安全编码令牌")
    void csrf_generateToken_tokenFormat() {
        String token = csrfTokenManager.generateToken("s1", "u1");

        assertNotNull(token);
        assertFalse(token.contains("="));
        assertFalse(token.contains("+"));
        assertFalse(token.contains("/"));
        assertTrue(token.length() > 40, "32 bytes without padding should be > 40 chars");
    }

    @Test
    @DisplayName("CsrfTokenManager: validateToken 有效令牌返回true")
    void csrf_validateToken_validToken_returnsTrue() {
        String sessionId = "session-003";
        String userId = "user-456";
        String token = csrfTokenManager.generateToken(sessionId, userId);

        String storedValue = userId + ":" + token;
        when(valueOperations.get(anyString())).thenReturn(storedValue);

        boolean valid = csrfTokenManager.validateToken(sessionId, token);

        assertTrue(valid);
    }

    @Test
    @DisplayName("CsrfTokenManager: validateToken 令牌不匹配返回false")
    void csrf_validateToken_wrongToken_returnsFalse() {
        String sessionId = "session-004";
        String userId = "user-789";
        String correctToken = csrfTokenManager.generateToken(sessionId, userId);

        when(valueOperations.get(anyString())).thenReturn(userId + ":" + correctToken);

        boolean valid = csrfTokenManager.validateToken(sessionId, "wrong-token");

        assertFalse(valid);
    }

    @Test
    @DisplayName("CsrfTokenManager: validateToken null sessionId返回false")
    void csrf_validateToken_nullSessionId_returnsFalse() {
        assertFalse(csrfTokenManager.validateToken(null, "token"));
    }

    @Test
    @DisplayName("CsrfTokenManager: validateToken null token返回false")
    void csrf_validateToken_nullToken_returnsFalse() {
        assertFalse(csrfTokenManager.validateToken("session", null));
    }

    @Test
    @DisplayName("CsrfTokenManager: validateToken Redis无值返回false")
    void csrf_validateToken_tokenNotFound_returnsFalse() {
        when(valueOperations.get(anyString())).thenReturn(null);

        assertFalse(csrfTokenManager.validateToken("session", "token"));
    }

    @Test
    @DisplayName("CsrfTokenManager: validateToken 无userId存储格式(仅token)正确校验")
    void csrf_validateToken_tokenOnlyFormat_returnsTrue() {
        String sessionId = "session-005";
        String token = csrfTokenManager.generateToken(sessionId, null);

        when(valueOperations.get(anyString())).thenReturn(token);

        boolean valid = csrfTokenManager.validateToken(sessionId, token);

        assertTrue(valid);
    }

    @Test
    @DisplayName("CsrfTokenManager: invalidateToken 有效sessionId删除Redis键")
    void csrf_invalidateToken_validSessionId_deletesKey() {
        String sessionId = "session-006";

        csrfTokenManager.invalidateToken(sessionId);

        String expectedKey = RedisKeyPrefix.key("auth", "csrf", sessionId);
        verify(stringRedisTemplate).delete(expectedKey);
    }

    @Test
    @DisplayName("CsrfTokenManager: invalidateToken null sessionId不执行删除")
    void csrf_invalidateToken_nullSessionId_doesNothing() {
        csrfTokenManager.invalidateToken(null);

        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("CsrfTokenManager: getTokenHeaderName返回X-CSRF-Token")
    void csrf_getTokenHeaderName_returnsCorrectValue() {
        assertEquals("X-CSRF-Token", CsrfTokenManager.getTokenHeaderName());
    }

    @Test
    @DisplayName("CsrfTokenManager: getTokenCookieName返回XSRF-TOKEN")
    void csrf_getTokenCookieName_returnsCorrectValue() {
        assertEquals("XSRF-TOKEN", CsrfTokenManager.getTokenCookieName());
    }

    @Test
    @DisplayName("CsrfTokenManager: 无参构造函数正常工作")
    void csrf_constructor_noArg_works() {
        CsrfTokenManager manager = new CsrfTokenManager();
        assertNotNull(manager);
        assertNull(manager.getStringRedisTemplate());
    }

    @Test
    @DisplayName("CsrfTokenManager: 带参构造函数正确设置RedisTemplate")
    void csrf_constructor_withRedisTemplate_works() {
        CsrfTokenManager manager = new CsrfTokenManager(stringRedisTemplate);
        assertNotNull(manager);
        assertEquals(stringRedisTemplate, manager.getStringRedisTemplate());
    }

    // ==================== MessageProducer 测试 ====================

    @Mock
    private RabbitTemplate rabbitTemplate;

    private MessageProducer messageProducer;

    @BeforeEach
    void setUpMessageProducer() {
        messageProducer = new MessageProducer();
        try {
            var field = MessageProducer.class.getDeclaredField("rabbitTemplate");
            field.setAccessible(true);
            field.set(messageProducer, rabbitTemplate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("MessageProducer: sendEvidenceMessage 正确调用存证交换机和路由键")
    void mq_sendEvidenceMessage_callsCorrectExchangeAndRoutingKey() {
        MessageProducer.EvidenceMessage msg = new MessageProducer.EvidenceMessage(
                "BATCH-001", "CREATE", "biz-1", "hash-abc", "{}");

        messageProducer.sendEvidenceMessage(msg);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_EVIDENCE),
                eq(RabbitMQConfig.RK_EVIDENCE),
                anyString(),
                (MessagePostProcessor) any());
    }

    @Test
    @DisplayName("MessageProducer: sendReleaseMessage 正确调用释放交换机")
    void mq_sendReleaseMessage_callsCorrectExchangeAndRoutingKey() {
        String routingKey = RabbitMQConfig.RK_DAILY_RELEASE;
        String payload = "{\"orderId\":123}";

        messageProducer.sendReleaseMessage(routingKey, payload);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_RELEASE),
                eq(routingKey),
                anyString(),
                (MessagePostProcessor) any());
    }

    @Test
    @DisplayName("MessageProducer: sendFirstOrderNotify 正确调用订单交换机和路由键")
    void mq_sendFirstOrderNotify_callsCorrectExchangeAndRoutingKey() {
        MessageProducer.FirstOrderMessage msg = new MessageProducer.FirstOrderMessage(
                "ORD-001", 100L, 200L, 5000L);

        messageProducer.sendFirstOrderNotify(msg);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_ORDER),
                eq(RabbitMQConfig.RK_FIRST_ORDER),
                anyString(),
                (MessagePostProcessor) any());
    }

    @Test
    @DisplayName("MessageProducer: sendRiskAlert 正确调用风控交换机和路由键")
    void mq_sendRiskAlert_callsCorrectExchangeAndRoutingKey() {
        MessageProducer.RiskAlertMessage msg = new MessageProducer.RiskAlertMessage(
                1001L, "FRAUD", "异常行为检测", 2, "BLOCKED");

        messageProducer.sendRiskAlert(msg);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_RISK),
                eq(RabbitMQConfig.RK_RISK_ALERT),
                anyString(),
                (MessagePostProcessor) any());
    }

    @Test
    @DisplayName("MessageProducer: sendProductAiReview 正确调用AI审核交换机和路由键")
    void mq_sendProductAiReview_callsCorrectExchangeAndRoutingKey() {
        Long productId = 999L;
        String productJson = "{\"name\":\"商品A\"}";

        messageProducer.sendProductAiReview(productId, productJson);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_AI_REVIEW),
                eq(RabbitMQConfig.RK_PRODUCT_REVIEW),
                anyString(),
                (MessagePostProcessor) any());
    }

    @Test
    @DisplayName("MessageProducer: sendB2bAiVerify 正确调用AI审核交换机和B2B路由键")
    void mq_sendB2bAiVerify_callsCorrectExchangeAndRoutingKey() {
        Long orderId = 888L;
        String orderJson = "{\"orderNo\":\"B2B-001\"}";

        messageProducer.sendB2bAiVerify(orderId, orderJson);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_AI_REVIEW),
                eq(RabbitMQConfig.RK_B2B_VERIFY),
                anyString(),
                (MessagePostProcessor) any());
    }

    @Test
    @DisplayName("MessageProducer: sendEvidenceMessage 消息体序列化为JSON字符串")
    void mq_sendEvidenceMessage_serializesAsJson() {
        MessageProducer.EvidenceMessage msg = new MessageProducer.EvidenceMessage(
                "BATCH-002", "UPDATE", "biz-2", "hash-def", "{\"key\":\"val\"}");

        messageProducer.sendEvidenceMessage(msg);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), messageCaptor.capture(), (MessagePostProcessor) any());

        String json = messageCaptor.getValue();
        assertNotNull(json);
        assertTrue(json.contains("BATCH-002"));
        assertTrue(json.contains("UPDATE"));
        assertTrue(json.contains("biz-2"));
    }

    @Test
    @DisplayName("MessageProducer: sendFirstOrderNotify 消息体序列化为JSON字符串")
    void mq_sendFirstOrderNotify_serializesAsJson() {
        MessageProducer.FirstOrderMessage msg = new MessageProducer.FirstOrderMessage(
                "ORD-002", 300L, 400L, 10000L);

        messageProducer.sendFirstOrderNotify(msg);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), messageCaptor.capture(), (MessagePostProcessor) any());

        String json = messageCaptor.getValue();
        assertNotNull(json);
        assertTrue(json.contains("ORD-002"));
        assertTrue(json.contains("300"));
    }

    @Test
    @DisplayName("MessageProducer: sendRiskAlert 消息体序列化为JSON字符串")
    void mq_sendRiskAlert_serializesAsJson() {
        MessageProducer.RiskAlertMessage msg = new MessageProducer.RiskAlertMessage(
                2002L, "SUSPICIOUS", "可疑交易", 1, "REVIEW");

        messageProducer.sendRiskAlert(msg);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), messageCaptor.capture(), (MessagePostProcessor) any());

        String json = messageCaptor.getValue();
        assertNotNull(json);
        assertTrue(json.contains("2002"));
        assertTrue(json.contains("SUSPICIOUS"));
    }

    // ==================== XssProtectionFilter 测试 ====================

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private HttpServletResponse httpResponse;

    @Mock
    private FilterChain filterChain;

    @Test
    @DisplayName("XssProtectionFilter: disabled时直接放行不包装请求")
    void xssFilter_disabled_passesThrough() throws Exception {
        XssProtectionFilter filter = new XssProtectionFilter(false);

        filter.doFilter(httpRequest, httpResponse, filterChain);

        verify(filterChain).doFilter(httpRequest, httpResponse);
        verify(filterChain, never()).doFilter(any(XssRequestWrapper.class), any());
    }

    @Test
    @DisplayName("XssProtectionFilter: enabled且非multipart时包装请求")
    void xssFilter_enabled_wrapsRequest() throws Exception {
        XssProtectionFilter filter = new XssProtectionFilter(true);
        when(httpRequest.getContentType()).thenReturn("application/json");

        filter.doFilter(httpRequest, httpResponse, filterChain);

        ArgumentCaptor<jakarta.servlet.ServletRequest> requestCaptor =
                ArgumentCaptor.forClass(jakarta.servlet.ServletRequest.class);
        verify(filterChain).doFilter(requestCaptor.capture(), eq(httpResponse));
        assertInstanceOf(XssRequestWrapper.class, requestCaptor.getValue());
    }

    @Test
    @DisplayName("XssProtectionFilter: multipart/form-data时跳过包装直接放行")
    void xssFilter_multipart_skipsWrapping() throws Exception {
        XssProtectionFilter filter = new XssProtectionFilter(true);
        when(httpRequest.getContentType()).thenReturn("multipart/form-data; boundary=something");

        filter.doFilter(httpRequest, httpResponse, filterChain);

        verify(filterChain).doFilter(httpRequest, httpResponse);
        verify(filterChain, never()).doFilter(any(XssRequestWrapper.class), any());
    }

    @Test
    @DisplayName("XssProtectionFilter: 非HttpServletRequest实例直接放行")
    void xssFilter_nonHttpRequest_passesThrough() throws Exception {
        XssProtectionFilter filter = new XssProtectionFilter(true);
        jakarta.servlet.ServletRequest servletRequest = mock(jakarta.servlet.ServletRequest.class);

        filter.doFilter(servletRequest, httpResponse, filterChain);

        verify(filterChain).doFilter(servletRequest, httpResponse);
    }

    @Test
    @DisplayName("XssProtectionFilter: 无参构造函数enabled默认为false(Spring管理时由@Value设为true)")
    void xssFilter_defaultConstructor_defaultEnabledFalse() {
        XssProtectionFilter filter = new XssProtectionFilter();
        assertFalse(filter.getEnabled());
    }

    @Test
    @DisplayName("XssProtectionFilter: setEnabled/getEnabled正常工作")
    void xssFilter_setGetEnabled_works() {
        XssProtectionFilter filter = new XssProtectionFilter();
        filter.setEnabled(false);
        assertFalse(filter.getEnabled());
        filter.setEnabled(true);
        assertTrue(filter.getEnabled());
    }

    // ==================== IdempotentAspect 测试 ====================

    @Mock
    private StringRedisTemplate idemRedisTemplate;

    @Mock
    private ValueOperations<String, String> idemValueOperations;

    @Mock
    private ProceedingJoinPoint proceedingJoinPoint;

    @Mock
    private MethodSignature methodSignature;

    private IdempotentAspect idempotentAspect;

    @BeforeEach
    void setUpIdempotent() {
        idempotentAspect = new IdempotentAspect();
        idempotentAspect.setRedisTemplate(idemRedisTemplate);
        when(idemRedisTemplate.opsForValue()).thenReturn(idemValueOperations);
    }

    @Test
    @DisplayName("IdempotentAspect: 首次请求成功获取幂等锁")
    void idempotent_firstRequest_acquiresLock() throws Throwable {
        Method method = TestIdempotentService.class.getDeclaredMethod("createOrder", String.class);
        when(proceedingJoinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{"ORD-001"});
        when(idemValueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(proceedingJoinPoint.proceed()).thenReturn("success");

        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn("#orderNo");
        when(idempotent.expireSeconds()).thenReturn(300);
        when(idempotent.message()).thenReturn("请勿重复提交");

        Object result = idempotentAspect.around(proceedingJoinPoint, idempotent);

        assertEquals("success", result);
        verify(proceedingJoinPoint).proceed();
        verify(idemValueOperations).setIfAbsent(
                eq("lsc:idempotent:createOrder:ORD-001"),
                eq("1"),
                eq(300L),
                eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("IdempotentAspect: 重复请求抛出BizException")
    void idempotent_duplicateRequest_throwsBizException() throws Throwable {
        Method method = TestIdempotentService.class.getDeclaredMethod("createOrder", String.class);
        when(proceedingJoinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{"ORD-002"});
        when(idemValueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn("#orderNo");
        when(idempotent.expireSeconds()).thenReturn(300);
        when(idempotent.message()).thenReturn("请勿重复提交");

        BizException ex = assertThrows(BizException.class,
                () -> idempotentAspect.around(proceedingJoinPoint, idempotent));

        assertEquals(ResultCode.IDEMPOTENT_DUPLICATE.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("请勿重复提交"));
        verify(proceedingJoinPoint, never()).proceed();
    }

    @Test
    @DisplayName("IdempotentAspect: 业务异常时释放幂等锁")
    void idempotent_businessException_releasesLock() throws Throwable {
        Method method = TestIdempotentService.class.getDeclaredMethod("createOrder", String.class);
        when(proceedingJoinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{"ORD-003"});
        when(idemValueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(proceedingJoinPoint.proceed()).thenThrow(new RuntimeException("业务异常"));

        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn("#orderNo");
        when(idempotent.expireSeconds()).thenReturn(300);
        when(idempotent.message()).thenReturn("请勿重复提交");

        assertThrows(RuntimeException.class,
                () -> idempotentAspect.around(proceedingJoinPoint, idempotent));

        verify(idemRedisTemplate).delete("lsc:idempotent:createOrder:ORD-003");
    }

    @Test
    @DisplayName("IdempotentAspect: 字面量key直接使用不解析SpEL")
    void idempotent_literalKey_usedDirectly() throws Throwable {
        Method method = TestIdempotentService.class.getDeclaredMethod("simpleMethod");
        when(proceedingJoinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{});
        when(idemValueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(proceedingJoinPoint.proceed()).thenReturn("ok");

        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn("LITERAL_KEY");
        when(idempotent.expireSeconds()).thenReturn(60);
        when(idempotent.message()).thenReturn("重复");

        idempotentAspect.around(proceedingJoinPoint, idempotent);

        verify(idemValueOperations).setIfAbsent(
                eq("lsc:idempotent:simpleMethod:LITERAL_KEY"),
                eq("1"),
                eq(60L),
                eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("IdempotentAspect: key为空时直接放行不拦截")
    void idempotent_emptyKey_passesThrough() throws Throwable {
        Method method = TestIdempotentService.class.getDeclaredMethod("noKeyMethod");
        when(proceedingJoinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(proceedingJoinPoint.getArgs()).thenReturn(new Object[]{});
        when(proceedingJoinPoint.proceed()).thenReturn("no-key-result");

        Idempotent idempotent = mock(Idempotent.class);
        when(idempotent.key()).thenReturn("");
        when(idempotent.expireSeconds()).thenReturn(300);
        when(idempotent.message()).thenReturn("重复");

        Object result = idempotentAspect.around(proceedingJoinPoint, idempotent);

        assertEquals("no-key-result", result);
        verify(idemValueOperations, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("IdempotentAspect: 无参构造函数正常创建")
    void idempotent_constructor_noArg_works() {
        IdempotentAspect aspect = new IdempotentAspect();
        assertNotNull(aspect);
        assertNull(aspect.getRedisTemplate());
    }

    @Test
    @DisplayName("IdempotentAspect: setRedisTemplate正确注入")
    void idempotent_setRedisTemplate_works() {
        IdempotentAspect aspect = new IdempotentAspect();
        StringRedisTemplate mockTemplate = mock(StringRedisTemplate.class);
        aspect.setRedisTemplate(mockTemplate);
        assertEquals(mockTemplate, aspect.getRedisTemplate());
    }

    // ==================== 辅助类 ====================

    private static class TestIdempotentService {
        public String createOrder(String orderNo) {
            return "created:" + orderNo;
        }

        public String simpleMethod() {
            return "simple";
        }

        public String noKeyMethod() {
            return "nokey";
        }
    }
}