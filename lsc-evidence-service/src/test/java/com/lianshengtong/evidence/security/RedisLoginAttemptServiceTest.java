package com.lianshengtong.evidence.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Redis登录尝试服务测试
 * 覆盖率提升目标: 0% → 85%+
 */
@ExtendWith(MockitoExtension.class)
class RedisLoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private RedisLoginAttemptService service;

    @BeforeEach
    void setUp() throws Exception {
        // 使用反射设置私有字段
        setField(service, "maxLoginAttempts", 5);
        setField(service, "lockoutDurationMs", 300000L);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Nested
    @DisplayName("isLocked 检查")
    class IsLockedTests {

        @Test
        @DisplayName("账户未锁定 - Redis无Key")
        void notLocked_whenNoKey() {
            when(redisTemplate.hasKey("lsc:evidence:login:fail:lock:testuser")).thenReturn(false);
            assertFalse(service.isLocked("testuser"));
        }

        @Test
        @DisplayName("账户已锁定 - Redis有Key")
        void locked_whenKeyExists() {
            when(redisTemplate.hasKey("lsc:evidence:login:fail:lock:lockeduser")).thenReturn(true);
            assertTrue(service.isLocked("lockeduser"));
        }

        @Test
        @DisplayName("空Key处理 - 不会抛NPE")
        void handleNullGracefully() {
            when(redisTemplate.hasKey(anyString())).thenReturn(null);
            assertFalse(service.isLocked("anyuser"));
        }
    }

    @Nested
    @DisplayName("remainingLockMs 剩余锁定时间")
    class RemainingLockMsTests {

        @Test
        @DisplayName("未锁定时返回0")
        void returnsZeroWhenNotLocked() {
            when(redisTemplate.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(-1L);
            assertEquals(0, service.remainingLockMs("user"));
        }

        @Test
        @DisplayName("锁定中返回剩余时间")
        void returnsRemainingMsWhenLocked() {
            when(redisTemplate.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(12345L);
            assertEquals(12345L, service.remainingLockMs("user"));
        }

        @Test
        @DisplayName("TTL为null返回0")
        void returnsZeroWhenTtlNull() {
            when(redisTemplate.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(null);
            assertEquals(0, service.remainingLockMs("user"));
        }

        @Test
        @DisplayName("TTL为负返回0")
        void returnsZeroWhenTtlNegative() {
            when(redisTemplate.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(-2L);
            assertEquals(0, service.remainingLockMs("user"));
        }
    }

    @Nested
    @DisplayName("recordFailure 记录失败")
    class RecordFailureTests {

        @Test
        @DisplayName("首次失败 - 计数递增但不锁定")
        void firstFailureIncrementsCount() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.increment("lsc:evidence:login:fail:count:user1")).thenReturn(1L);

            service.recordFailure("user1");

            verify(valueOps).increment("lsc:evidence:login:fail:count:user1");
            // BUG-fix: 实际 expire TTL = lockoutDurationMs + 60_000L 缓冲
            verify(redisTemplate).expire(eq("lsc:evidence:login:fail:count:user1"), eq(360000L), eq(TimeUnit.MILLISECONDS));
            // 首次失败 count=1 < 5，不触发锁定
            verify(valueOps, never()).set(eq("lsc:evidence:login:fail:lock:user1"), any(), anyLong(), any());
        }

        @Test
        @DisplayName("达到阈值 - 触发账户锁定")
        void locksWhenReachThreshold() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.increment("lsc:evidence:login:fail:count:user5")).thenReturn(5L);

            service.recordFailure("user5");

            verify(valueOps).set(
                eq("lsc:evidence:login:fail:lock:user5"),
                eq("5"),
                eq(300000L),
                eq(TimeUnit.MILLISECONDS)
            );
        }

        @Test
        @DisplayName("超过阈值 - 继续锁定")
        void staysLockedAboveThreshold() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.increment("lsc:evidence:login:fail:count:user6")).thenReturn(6L);

            service.recordFailure("user6");

            verify(valueOps).set(
                eq("lsc:evidence:login:fail:lock:user6"),
                eq("6"),
                eq(300000L),
                eq(TimeUnit.MILLISECONDS)
            );
        }

        @Test
        @DisplayName("Redis返回null - 静默处理")
        void handlesNullIncrement() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.increment(anyString())).thenReturn(null);

            assertDoesNotThrow(() -> service.recordFailure("user"));
        }

        @Test
        @DisplayName("TTL计算 - 锁定时长+60秒缓冲用于失败计数Key")
        void ttlCalculationForCountKey() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.increment("lsc:evidence:login:fail:count:ttluser")).thenReturn(1L);

            service.recordFailure("ttluser");

            // countKey TTL = lockoutDurationMs + 60_000L 缓冲
            verify(redisTemplate).expire(
                    eq("lsc:evidence:login:fail:count:ttluser"),
                    eq(360000L),
                    eq(TimeUnit.MILLISECONDS)
            );
        }

        @Test
        @DisplayName("锁定Key TTL - 使用lockoutDurationMs")
        void ttlCalculationForLockKey() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.increment("lsc:evidence:login:fail:count:lockuser")).thenReturn(5L);

            service.recordFailure("lockuser");

            // lockKey TTL = lockoutDurationMs (不含额外60秒缓冲)
            verify(valueOps).set(
                    eq("lsc:evidence:login:fail:lock:lockuser"),
                    eq("5"),
                    eq(300000L),
                    eq(TimeUnit.MILLISECONDS)
            );
        }
    }

    @Nested
    @DisplayName("recordSuccess 记录成功")
    class RecordSuccessTests {

        @Test
        @DisplayName("登录成功清除所有记录")
        void clearsAllOnSuccess() {
            service.recordSuccess("testuser");

            verify(redisTemplate).delete("lsc:evidence:login:fail:count:testuser");
            verify(redisTemplate).delete("lsc:evidence:login:fail:lock:testuser");
        }

        @Test
        @DisplayName("重复登录成功 - 幂等操作")
        void idempotentOnRepeatedSuccess() {
            service.recordSuccess("user");
            service.recordSuccess("user");

            verify(redisTemplate, times(2)).delete("lsc:evidence:login:fail:count:user");
            verify(redisTemplate, times(2)).delete("lsc:evidence:login:fail:lock:user");
        }

        @Test
        @DisplayName("登录成功在锁定后 - 清除锁定标记")
        void clearsLockOnSuccessAfterLockout() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.increment("lsc:evidence:login:fail:count:lockuser")).thenReturn(5L);

            service.recordFailure("lockuser");
            service.recordSuccess("lockuser");

            verify(redisTemplate).delete("lsc:evidence:login:fail:count:lockuser");
            verify(redisTemplate).delete("lsc:evidence:login:fail:lock:lockuser");
        }
    }

    @Nested
    @DisplayName("边界与高并发")
    class EdgeAndConcurrencyTests {

        @Test
        @DisplayName("高并发recordFailure - 确保线程安全")
        void concurrentRecordFailure() throws Exception {
            int threadCount = 50;
            Thread[] threads = new Thread[threadCount];

            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.increment(anyString())).thenReturn(1L);

            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    service.recordFailure("concurrent-user");
                });
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) {
                try { t.join(2000); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // 验证所有线程都能正常执行，没有死锁或异常
            verify(valueOps, times(threadCount)).increment("lsc:evidence:login:fail:count:concurrent-user");
        }

        @Test
        @DisplayName("isLocked - 并发访问确保线程安全")
        void concurrentIsLocked() throws Exception {
            int threadCount = 50;
            Thread[] threads = new Thread[threadCount];

            when(redisTemplate.hasKey(anyString())).thenReturn(false);

            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    service.isLocked("user");
                });
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) {
                try { t.join(2000); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // 验证所有线程都能正常执行
            verify(redisTemplate, times(threadCount)).hasKey("lsc:evidence:login:fail:lock:user");
        }
    }
}