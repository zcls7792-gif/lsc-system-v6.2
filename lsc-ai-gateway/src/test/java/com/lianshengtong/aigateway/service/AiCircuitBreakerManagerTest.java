package com.lianshengtong.aigateway.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI降级熔断管理器单元测试")
class AiCircuitBreakerManagerTest {

    private AiCircuitBreakerManager breaker;

    @BeforeEach
    void setUp() {
        breaker = new AiCircuitBreakerManager();
        ReflectionTestUtils.setField(breaker, "modelTimeoutMs", 100L);
        ReflectionTestUtils.setField(breaker, "failureThreshold", 3);
        ReflectionTestUtils.setField(breaker, "retryAfterSeconds", 1L);
        ReflectionTestUtils.setField(breaker, "fallbackEnabled", true);
        ReflectionTestUtils.setField(breaker, "corePoolSize", 2);
        ReflectionTestUtils.setField(breaker, "maxPoolSize", 4);
        ReflectionTestUtils.setField(breaker, "queueCapacity", 64);
        breaker.init();
    }

    @AfterEach
    void tearDown() {
        breaker.destroy();
    }

    @SuppressWarnings("unchecked")
    private <T> T getBreakerState(String capability) {
        Map<String, ?> breakers = (Map<String, ?>) ReflectionTestUtils.getField(breaker, "breakers");
        return (T) breakers.get(capability);
    }

    private void setOpenedAt(String capability, long openedAt) {
        Object state = getBreakerState(capability);
        ReflectionTestUtils.setField(state, "openedAt", openedAt);
    }

    @Test
    @DisplayName("execute() 成功 - 返回 action 结果")
    void execute_success() {
        String result = breaker.execute("test-cap",
                () -> "ok-result",
                () -> "fallback-result");

        assertEquals("ok-result", result);
    }

    @Test
    @DisplayName("execute() 超时 - 触发降级返回 fallback 结果")
    void execute_timeout_fallback() {
        String result = breaker.execute("test-timeout",
                () -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "ok";
                },
                () -> "timeout-fallback");

        assertEquals("timeout-fallback", result);
    }

    @Test
    @DisplayName("execute() 异常 - 触发降级返回 fallback 结果")
    void execute_exception_fallback() {
        String result = breaker.execute("test-exception",
                () -> {
                    throw new RuntimeException("外部模型异常");
                },
                () -> "exception-fallback");

        assertEquals("exception-fallback", result);
    }

    @Test
    @DisplayName("熔断器在失败阈值后打开")
    void circuitOpensAfterFailureThreshold() {
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            breaker.execute("test-open",
                    () -> {
                        throw new RuntimeException("fail-" + idx);
                    },
                    () -> "fallback");
        }

        assertTrue(breaker.isCircuitOpen("test-open"));
        assertEquals("OPEN", breaker.getState("test-open"));
    }

    @Test
    @DisplayName("熔断器打开期间保持 OPEN 状态")
    void circuitStaysOpenDuringCooldown() {
        for (int i = 0; i < 3; i++) {
            breaker.execute("test-stay-open",
                    () -> {
                        throw new RuntimeException("fail");
                    },
                    () -> "fallback");
        }

        setOpenedAt("test-stay-open", System.currentTimeMillis());

        assertTrue(breaker.isCircuitOpen("test-stay-open"));
        assertEquals("OPEN", breaker.getState("test-stay-open"));
    }

    @Test
    @DisplayName("熔断器经过 retry-after 时间后转为 HALF_OPEN")
    void circuitTransitionsToHalfOpen() {
        for (int i = 0; i < 3; i++) {
            breaker.execute("test-half-open",
                    () -> {
                        throw new RuntimeException("fail");
                    },
                    () -> "fallback");
        }

        setOpenedAt("test-half-open", System.currentTimeMillis() - 2000L);

        assertFalse(breaker.isCircuitOpen("test-half-open"));
        assertEquals("HALF_OPEN", breaker.getState("test-half-open"));
    }

    @Test
    @DisplayName("HALF_OPEN 试探成功后熔断器关闭")
    void halfOpenSuccessClosesCircuit() {
        for (int i = 0; i < 3; i++) {
            breaker.execute("test-half-success",
                    () -> {
                        throw new RuntimeException("fail");
                    },
                    () -> "fallback");
        }

        setOpenedAt("test-half-success", System.currentTimeMillis() - 2000L);

        breaker.execute("test-half-success",
                () -> "success",
                () -> "fallback");

        assertEquals("CLOSED", breaker.getState("test-half-success"));
        assertFalse(breaker.isCircuitOpen("test-half-success"));
    }

    @Test
    @DisplayName("HALF_OPEN 试探失败后熔断器重新打开")
    void halfOpenFailureReopensCircuit() {
        for (int i = 0; i < 3; i++) {
            breaker.execute("test-half-fail",
                    () -> {
                        throw new RuntimeException("fail");
                    },
                    () -> "fallback");
        }

        setOpenedAt("test-half-fail", System.currentTimeMillis() - 2000L);

        breaker.execute("test-half-fail",
                () -> {
                    throw new RuntimeException("fail-again");
                },
                () -> "fallback");

        assertTrue(breaker.isCircuitOpen("test-half-fail"));
        assertEquals("OPEN", breaker.getState("test-half-fail"));
    }

    @Test
    @DisplayName("isCircuitOpen 当 CLOSED 时返回 false")
    void isCircuitOpen_closed_returnsFalse() {
        breaker.execute("test-closed",
                () -> "ok",
                () -> "fallback");

        assertFalse(breaker.isCircuitOpen("test-closed"));
    }

    @Test
    @DisplayName("isCircuitOpen 当 OPEN 时返回 true")
    void isCircuitOpen_open_returnsTrue() {
        for (int i = 0; i < 3; i++) {
            breaker.execute("test-open-check",
                    () -> {
                        throw new RuntimeException("fail");
                    },
                    () -> "fallback");
        }

        assertTrue(breaker.isCircuitOpen("test-open-check"));
    }

    @Test
    @DisplayName("getState 初始返回 CLOSED")
    void getState_initially_closed() {
        breaker.execute("test-init",
                () -> "ok",
                () -> "fallback");

        assertEquals("CLOSED", breaker.getState("test-init"));
    }

    @Test
    @DisplayName("getState 失败后返回 OPEN")
    void getState_afterFailures_open() {
        for (int i = 0; i < 3; i++) {
            breaker.execute("test-state-open",
                    () -> {
                        throw new RuntimeException("fail");
                    },
                    () -> "fallback");
        }

        assertEquals("OPEN", breaker.getState("test-state-open"));
    }

    @Test
    @DisplayName("metrics() 返回正确的统计计数")
    void metrics_returnsCorrectCounts() {
        breaker.execute("test-metrics",
                () -> "success",
                () -> "fallback");
        breaker.execute("test-metrics",
                () -> {
                    throw new RuntimeException("fail");
                },
                () -> "fallback");
        breaker.execute("test-metrics",
                () -> "success-2",
                () -> "fallback");

        Map<String, Object> metrics = breaker.metrics();
        assertNotNull(metrics);
        assertTrue(metrics.containsKey("test-metrics"));

        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) metrics.get("test-metrics");
        assertEquals(3L, ((Long) m.get("totalCalls")).longValue());
        assertEquals(2L, ((Long) m.get("successCount")).longValue());
        assertEquals(1L, ((Long) m.get("failureCount")).longValue());
        assertEquals(1L, ((Long) m.get("fallbackCount")).longValue());
    }

    @Test
    @DisplayName("reset() 返回 true 并清除状态")
    void reset_returnsTrue_andClearsState() {
        for (int i = 0; i < 3; i++) {
            breaker.execute("test-reset",
                    () -> {
                        throw new RuntimeException("fail");
                    },
                    () -> "fallback");
        }

        assertTrue(breaker.isCircuitOpen("test-reset"));
        assertEquals("OPEN", breaker.getState("test-reset"));

        boolean resetResult = breaker.reset("test-reset");

        assertTrue(resetResult);
        assertEquals("CLOSED", breaker.getState("test-reset"));
        assertFalse(breaker.isCircuitOpen("test-reset"));
    }

    @Test
    @DisplayName("reset() 不存在的能力返回 false")
    void reset_nonExistentCapability_returnsFalse() {
        boolean result = breaker.reset("non-existent-capability");
        assertFalse(result);
    }

    @Test
    @DisplayName("熔断开启时 execute 直接返回 fallback 不调用 action")
    void circuitOpen_directFallback() {
        for (int i = 0; i < 3; i++) {
            breaker.execute("test-direct-fallback",
                    () -> {
                        throw new RuntimeException("fail");
                    },
                    () -> "fallback");
        }

        setOpenedAt("test-direct-fallback", System.currentTimeMillis());

        String result = breaker.execute("test-direct-fallback",
                () -> "should-not-be-called",
                () -> "direct-fallback");

        assertEquals("direct-fallback", result);
    }

    @Test
    @DisplayName("metrics() 对未调用的能力返回空 map")
    void metrics_untouchedCapability_empty() {
        Map<String, Object> metrics = breaker.metrics();
        assertNotNull(metrics);
        assertFalse(metrics.containsKey("never-called"));
    }

    @Test
    @DisplayName("多次成功调用后熔断器保持 CLOSED")
    void multipleSuccesses_staysClosed() {
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            breaker.execute("test-multi-success",
                    () -> "ok-" + idx,
                    () -> "fallback");
        }

        assertEquals("CLOSED", breaker.getState("test-multi-success"));
        assertFalse(breaker.isCircuitOpen("test-multi-success"));
    }
}