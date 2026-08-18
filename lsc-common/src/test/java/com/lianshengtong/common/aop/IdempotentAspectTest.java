package com.lianshengtong.common.aop;

import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.idempotent.Idempotent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("幂等切面测试")
class IdempotentAspectTest {

    private IdempotentAspect aspect;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    @Mock
    private Idempotent idempotent;

    @BeforeEach
    void setUp() {
        aspect = new IdempotentAspect();
        aspect.setRedisTemplate(redisTemplate);
    }

    private Method getTestMethod() throws NoSuchMethodException {
        return TestService.class.getDeclaredMethod("processOrder", String.class, int.class);
    }

    private void setupJoinPoint(String key, Object[] args) throws NoSuchMethodException {
        Method method = getTestMethod();
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.getMethod()).thenReturn(method);
        lenient().when(joinPoint.getArgs()).thenReturn(args);
        lenient().when(idempotent.key()).thenReturn(key);
        lenient().when(idempotent.expireSeconds()).thenReturn(300);
        lenient().when(idempotent.message()).thenReturn("请勿重复提交");
    }

    @Nested
    @DisplayName("parseKey 方法测试 (通过反射)")
    class ParseKeyTests {

        @Test
        @DisplayName("key 为 null 返回 null")
        void parseKeyWithNullKey() throws Exception {
            Method parseKey = IdempotentAspect.class.getDeclaredMethod(
                    "parseKey", String.class, Method.class, Object[].class);
            parseKey.setAccessible(true);

            Method method = getTestMethod();
            String result = (String) parseKey.invoke(aspect, null, method, new Object[]{"order123", 5});
            assertNull(result);
        }

        @Test
        @DisplayName("key 为空字符串返回 null")
        void parseKeyWithEmptyKey() throws Exception {
            Method parseKey = IdempotentAspect.class.getDeclaredMethod(
                    "parseKey", String.class, Method.class, Object[].class);
            parseKey.setAccessible(true);

            Method method = getTestMethod();
            String result = (String) parseKey.invoke(aspect, "", method, new Object[]{"order123", 5});
            assertNull(result);
        }

        @Test
        @DisplayName("key 为字面量 (不以 # 开头) 直接返回")
        void parseKeyWithLiteralKey() throws Exception {
            Method parseKey = IdempotentAspect.class.getDeclaredMethod(
                    "parseKey", String.class, Method.class, Object[].class);
            parseKey.setAccessible(true);

            Method method = getTestMethod();
            String result = (String) parseKey.invoke(aspect, "literal_key", method, new Object[]{"order123", 5});
            assertEquals("literal_key", result);
        }

        @Test
        @DisplayName("key 为 SpEL 表达式 - 解析成功")
        void parseKeyWithSpelExpression() throws Exception {
            Method parseKey = IdempotentAspect.class.getDeclaredMethod(
                    "parseKey", String.class, Method.class, Object[].class);
            parseKey.setAccessible(true);

            Method method = getTestMethod();
            String result = (String) parseKey.invoke(aspect, "#orderNo", method, new Object[]{"order123", 5});
            assertEquals("order123", result);
        }

        @Test
        @DisplayName("key 为 SpEL 表达式 - 数字参数")
        void parseKeyWithSpelNumberArg() throws Exception {
            Method parseKey = IdempotentAspect.class.getDeclaredMethod(
                    "parseKey", String.class, Method.class, Object[].class);
            parseKey.setAccessible(true);

            Method method = getTestMethod();
            String result = (String) parseKey.invoke(aspect, "#count", method, new Object[]{"order123", 42});
            assertEquals("42", result);
        }

        @Test
        @DisplayName("key 为 SpEL 表达式 - 引用不存在的参数返回 null")
        void parseKeyWithSpelUnknownParam() throws Exception {
            Method parseKey = IdempotentAspect.class.getDeclaredMethod(
                    "parseKey", String.class, Method.class, Object[].class);
            parseKey.setAccessible(true);

            Method method = getTestMethod();
            String result = (String) parseKey.invoke(aspect, "#unknown", method, new Object[]{"order123", 5});
            assertNull(result);
        }

        @Test
        @DisplayName("key 为无效 SpEL 表达式返回原始表达式")
        void parseKeyWithInvalidSpel() throws Exception {
            Method parseKey = IdempotentAspect.class.getDeclaredMethod(
                    "parseKey", String.class, Method.class, Object[].class);
            parseKey.setAccessible(true);

            Method method = getTestMethod();
            String invalidExpr = "#{'invalid expression syntax";
            String result = (String) parseKey.invoke(aspect, invalidExpr, method, new Object[]{"order123", 5});
            assertEquals(invalidExpr, result);
        }

        @Test
        @DisplayName("key 为 SpEL 表达式 - 复杂拼接")
        void parseKeyWithComplexSpel() throws Exception {
            Method parseKey = IdempotentAspect.class.getDeclaredMethod(
                    "parseKey", String.class, Method.class, Object[].class);
            parseKey.setAccessible(true);

            Method method = getTestMethod();
            String result = (String) parseKey.invoke(aspect, "#orderNo + '_' + #count", method, new Object[]{"order123", 5});
            assertEquals("order123_5", result);
        }

        @Test
        @DisplayName("key 以 # 开头但无参数方法 - 无参数名")
        void parseKeyWithSpelNoParamMethod() throws Exception {
            Method parseKey = IdempotentAspect.class.getDeclaredMethod(
                    "parseKey", String.class, Method.class, Object[].class);
            parseKey.setAccessible(true);

            Method noArgMethod = TestService.class.getDeclaredMethod("noArgMethod");
            String result = (String) parseKey.invoke(aspect, "#anything", noArgMethod, new Object[]{});
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("around 方法测试 - 正常流程")
    class AroundSuccessTests {

        @Test
        @DisplayName("成功执行 - setIfAbsent 返回 true")
        void aroundWithSuccessfulExecution() throws Throwable {
            setupJoinPoint("#orderNo", new Object[]{"order123", 5});
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);
            when(joinPoint.proceed()).thenReturn("result_value");

            Object result = aspect.around(joinPoint, idempotent);

            assertEquals("result_value", result);
            verify(valueOperations).setIfAbsent(
                    eq("lsc:idempotent:processOrder:order123"),
                    eq("1"),
                    eq(300L),
                    eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("成功执行 - 字面量 key")
        void aroundWithLiteralKey() throws Throwable {
            setupJoinPoint("literal_key", new Object[]{"order123", 5});
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);
            when(joinPoint.proceed()).thenReturn("success");

            Object result = aspect.around(joinPoint, idempotent);

            assertEquals("success", result);
            verify(valueOperations).setIfAbsent(
                    eq("lsc:idempotent:processOrder:literal_key"),
                    eq("1"),
                    eq(300L),
                    eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("keyValue 为 null 时直接 proceed")
        void aroundWithNullKeyValue() throws Throwable {
            setupJoinPoint(null, new Object[]{"order123", 5});

            when(joinPoint.proceed()).thenReturn("direct_result");

            Object result = aspect.around(joinPoint, idempotent);

            assertEquals("direct_result", result);
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("keyValue 为空字符串时直接 proceed")
        void aroundWithEmptyKeyValue() throws Throwable {
            setupJoinPoint("", new Object[]{"order123", 5});

            when(joinPoint.proceed()).thenReturn("direct_result");

            Object result = aspect.around(joinPoint, idempotent);

            assertEquals("direct_result", result);
            verify(redisTemplate, never()).opsForValue();
        }
    }

    @Nested
    @DisplayName("around 方法测试 - 重复请求")
    class AroundDuplicateTests {

        @Test
        @DisplayName("重复请求 - setIfAbsent 返回 Boolean.FALSE 抛出 BizException")
        void aroundWithDuplicateRequest() throws Throwable {
            setupJoinPoint("#orderNo", new Object[]{"order123", 5});
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(Boolean.FALSE);

            BizException exception = assertThrows(BizException.class,
                    () -> aspect.around(joinPoint, idempotent));

            assertEquals(1001, exception.getCode());
            assertEquals("请勿重复提交", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("around 方法测试 - 异常处理")
    class AroundExceptionTests {

        @Test
        @DisplayName("RuntimeException 时删除 key 并重新抛出")
        void aroundWithRuntimeException() throws Throwable {
            setupJoinPoint("#orderNo", new Object[]{"order123", 5});
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);
            when(joinPoint.proceed()).thenThrow(new RuntimeException("业务异常"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> aspect.around(joinPoint, idempotent));

            assertEquals("业务异常", ex.getMessage());
            verify(redisTemplate).delete("lsc:idempotent:processOrder:order123");
        }

        @Test
        @DisplayName("BizException 时删除 key 并重新抛出")
        void aroundWithBizException() throws Throwable {
            setupJoinPoint("#orderNo", new Object[]{"order123", 5});
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);
            when(joinPoint.proceed()).thenThrow(new BizException(4001, "业务错误"));

            BizException ex = assertThrows(BizException.class,
                    () -> aspect.around(joinPoint, idempotent));

            assertEquals(4001, ex.getCode());
            assertEquals("业务错误", ex.getMessage());
            verify(redisTemplate).delete("lsc:idempotent:processOrder:order123");
        }

        @Test
        @DisplayName("非 RuntimeException (受检异常) 时不删除 key")
        void aroundWithCheckedException() throws Throwable {
            setupJoinPoint("#orderNo", new Object[]{"order123", 5});
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);
            when(joinPoint.proceed()).thenThrow(new Exception("受检异常"));

            assertThrows(Exception.class,
                    () -> aspect.around(joinPoint, idempotent));

            verify(redisTemplate, never()).delete(anyString());
        }
    }

    @Nested
    @DisplayName("around 方法测试 - 边界场景")
    class AroundEdgeCaseTests {

        @Test
        @DisplayName("setIfAbsent 返回 null 时不视为重复 (Boolean.FALSE.equals(null) 为 false)")
        void aroundWithNullSetIfAbsentResult() throws Throwable {
            setupJoinPoint("#orderNo", new Object[]{"order123", 5});
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(null);
            when(joinPoint.proceed()).thenReturn("result");

            Object result = aspect.around(joinPoint, idempotent);

            assertEquals("result", result);
        }

        @Test
        @DisplayName("SpEL 解析返回 null 时 keyValue 为 null 直接 proceed")
        void aroundWithSpelReturningNull() throws Throwable {
            setupJoinPoint("#nonExistent", new Object[]{"order123", 5});
            when(joinPoint.proceed()).thenReturn("direct");

            Object result = aspect.around(joinPoint, idempotent);

            assertEquals("direct", result);
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("无效 SpEL 返回原始表达式作为 keyValue")
        void aroundWithInvalidSpel() throws Throwable {
            String invalidSpel = "#{'invalid'";
            setupJoinPoint(invalidSpel, new Object[]{"order123", 5});
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenReturn(true);
            when(joinPoint.proceed()).thenReturn("result");

            Object result = aspect.around(joinPoint, idempotent);

            assertEquals("result", result);
            verify(valueOperations).setIfAbsent(
                    eq("lsc:idempotent:processOrder:" + invalidSpel),
                    eq("1"),
                    eq(300L),
                    eq(TimeUnit.SECONDS));
        }
    }

    static class TestService {
        public String processOrder(String orderNo, int count) {
            return "processed";
        }

        public void noArgMethod() {
        }
    }
}