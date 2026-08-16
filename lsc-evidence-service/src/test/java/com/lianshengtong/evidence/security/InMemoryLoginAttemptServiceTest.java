package com.lianshengtong.evidence.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内存登录尝试服务测试
 */
class InMemoryLoginAttemptServiceTest {

    private InMemoryLoginAttemptService service;

    @BeforeEach
    void setUp() throws Exception {
        // 清理静态 ATTEMPTS Map，避免测试间状态污染
        Field attemptsField = InMemoryLoginAttemptService.class.getDeclaredField("ATTEMPTS");
        attemptsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ?> attempts = (Map<String, ?>) attemptsField.get(null);
        attempts.clear();

        service = new InMemoryLoginAttemptService();
        setField(service, "maxLoginAttempts", 3);
        setField(service, "lockoutDurationMs", 1000L);
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
        @DisplayName("用户未出现过 - 未锁定")
        void notLockedWhenNoAttempt() {
            assertFalse(service.isLocked("newuser"));
        }

        @Test
        @DisplayName("用户未达阈值 - 未锁定")
        void notLockedBelowThreshold() {
            service.recordFailure("user1");
            assertFalse(service.isLocked("user1"));
        }

        @Test
        @DisplayName("达到阈值 - 已锁定")
        void lockedAtThreshold() {
            service.recordFailure("user1");
            service.recordFailure("user1");
            service.recordFailure("user1");
            assertTrue(service.isLocked("user1"));
        }

        @Test
        @DisplayName("超过阈值 - 持续锁定")
        void lockedAboveThreshold() {
            for (int i = 0; i < 5; i++) {
                service.recordFailure("user2");
            }
            assertTrue(service.isLocked("user2"));
        }

        @Test
        @DisplayName("锁定过期后自动解锁")
        void autoUnlockAfterExpiry() throws Exception {
            setField(service, "lockoutDurationMs", 50L);
            service.recordFailure("user3");
            service.recordFailure("user3");
            service.recordFailure("user3");
            assertTrue(service.isLocked("user3"));

            Thread.sleep(100);
            assertFalse(service.isLocked("user3"));
        }
    }

    @Nested
    @DisplayName("remainingLockMs 剩余锁定时间")
    class RemainingLockMsTests {

        @Test
        @DisplayName("未锁定返回0")
        void returnsZeroWhenNotLocked() {
            assertEquals(0, service.remainingLockMs("newuser"));
        }

        @Test
        @DisplayName("锁定中返回正值")
        void returnsPositiveWhenLocked() {
            service.recordFailure("user");
            service.recordFailure("user");
            service.recordFailure("user");
            long remaining = service.remainingLockMs("user");
            assertTrue(remaining > 0);
            assertTrue(remaining <= 1000L);
        }

        @Test
        @DisplayName("锁定过期返回0")
        void returnsZeroAfterExpiry() throws Exception {
            setField(service, "lockoutDurationMs", 50L);
            service.recordFailure("user");
            service.recordFailure("user");
            service.recordFailure("user");
            Thread.sleep(100);
            assertEquals(0, service.remainingLockMs("user"));
        }
    }

    @Nested
    @DisplayName("recordFailure 记录失败")
    class RecordFailureTests {

        @Test
        @DisplayName("首次失败创建记录")
        void firstFailureCreatesAttempt() {
            service.recordFailure("user");
            assertFalse(service.isLocked("user"));
        }

        @Test
        @DisplayName("连续失败累加")
        void consecutiveFailuresAccumulate() {
            service.recordFailure("user");
            service.recordFailure("user");
            assertFalse(service.isLocked("user"));
            service.recordFailure("user");
            assertTrue(service.isLocked("user"));
        }

        @Test
        @DisplayName("不同用户独立计数")
        void differentUsersCountedSeparately() {
            service.recordFailure("userA");
            service.recordFailure("userA");
            service.recordFailure("userB");

            assertFalse(service.isLocked("userA"));
            assertFalse(service.isLocked("userB"));

            service.recordFailure("userA");
            service.recordFailure("userB");
            service.recordFailure("userB");

            assertTrue(service.isLocked("userA"));
            assertTrue(service.isLocked("userB"));
        }

        @Test
        @DisplayName("达到阈值触发锁定")
        void triggersLockAtThreshold() {
            for (int i = 0; i < 3; i++) {
                service.recordFailure("lockme");
            }
            assertTrue(service.isLocked("lockme"));
            assertTrue(service.remainingLockMs("lockme") > 0);
        }
    }

    @Nested
    @DisplayName("recordSuccess 记录成功")
    class RecordSuccessTests {

        @Test
        @DisplayName("成功登录清除记录")
        void successClearsAttempt() {
            service.recordFailure("user");
            service.recordSuccess("user");
            assertFalse(service.isLocked("user"));
            assertEquals(0, service.remainingLockMs("user"));
        }

        @Test
        @DisplayName("锁定后成功登录 - 解锁")
        void successUnlocksAfterLockout() {
            for (int i = 0; i < 3; i++) {
                service.recordFailure("user");
            }
            assertTrue(service.isLocked("user"));

            service.recordSuccess("user");
            assertFalse(service.isLocked("user"));
        }

        @Test
        @DisplayName("从未失败的用户成功 - 不报错")
        void successOnNeverFailedUserNoError() {
            assertDoesNotThrow(() -> service.recordSuccess("newwuser"));
        }
    }

    @Nested
    @DisplayName("边界场景")
    class EdgeCases {

        @Test
        @DisplayName("高并发场景 - 单用户")
        void concurrentAccessSingleUser() throws Exception {
            int threadCount = 20;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    service.recordFailure("concurrent-user");
                    service.isLocked("concurrent-user");
                    service.remainingLockMs("concurrent-user");
                });
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join(2000);

            // 最终状态应该是锁定（因为有多次失败）
            assertTrue(service.isLocked("concurrent-user"));
        }

        @Test
        @DisplayName("高并发场景 - 多用户隔离")
        void concurrentAccessMultipleUsers() throws Exception {
            int threadCount = 15;
            Thread[] threads = new Thread[threadCount];
            String[] users = {"userA", "userB", "userC", "userD", "userE"};

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                threads[i] = new Thread(() -> {
                    String user = users[idx % users.length];
                    service.recordFailure(user);
                    service.isLocked(user);
                });
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join(2000);

            // 验证所有用户状态
            for (String user : users) {
                // 每个用户有 3 次失败 (15 threads / 5 users)，达到阈值 3 -> 已锁定
                assertTrue(service.isLocked(user), 
                    user + " should be locked (3 failures >= threshold 3)");
            }
        }

        @Test
        @DisplayName("锁定过期后重新开始计数")
        void resetAfterLockExpiry() throws Exception {
            setField(service, "lockoutDurationMs", 50L);

            for (int i = 0; i < 3; i++) {
                service.recordFailure("user");
            }
            assertTrue(service.isLocked("user"));

            Thread.sleep(100);
            assertFalse(service.isLocked("user"));

            // 过期后重新记录失败
            service.recordFailure("user");
            assertFalse(service.isLocked("user"));
            service.recordFailure("user");
            service.recordFailure("user");
            assertTrue(service.isLocked("user"));
        }

        @Test
        @DisplayName("达到阈值后 recordSuccess 重置计数")
        void recordSuccessResetsCountAfterThreshold() {
            for (int i = 0; i < 3; i++) {
                service.recordFailure("user");
            }
            assertTrue(service.isLocked("user"));

            service.recordSuccess("user");

            // 重置后再记录 2 次失败不应锁定
            service.recordFailure("user");
            service.recordFailure("user");
            assertFalse(service.isLocked("user"));
        }
    }
}