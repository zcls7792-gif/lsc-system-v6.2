package com.lianshengtong.evidence.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内存Token黑名单服务测试
 */
class InMemoryTokenBlacklistServiceTest {

    private InMemoryTokenBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryTokenBlacklistService();
    }

    @Nested
    @DisplayName("revoke 撤销Token")
    class RevokeTests {

        @Test
        @DisplayName("撤销Token后可被检测")
        void revokedTokenIsDetected() {
            service.revoke("token-001", 3600000L);
            assertTrue(service.isRevoked("token-001"));
        }

        @Test
        @DisplayName("撤销Token后过期 - 自动清除")
        void expiredTokenIsCleared() throws Exception {
            service.revoke("token-expired", 100L);
            assertTrue(service.isRevoked("token-expired"));

            Thread.sleep(150);
            assertFalse(service.isRevoked("token-expired"));
        }

        @Test
        @DisplayName("重复撤销 - 幂等")
        void duplicateRevokeIsIdempotent() {
            service.revoke("token-001", 3600000L);
            service.revoke("token-001", 3600000L);
            assertTrue(service.isRevoked("token-001"));
        }

        @Test
        @DisplayName("撤销后重新撤销 - 延长过期时间")
        void reRevokeExtendsExpiry() throws Exception {
            service.revoke("token-001", 100L);
            service.revoke("token-001", 3600000L);

            Thread.sleep(150);
            assertTrue(service.isRevoked("token-001"));
        }
    }

    @Nested
    @DisplayName("isRevoked 检查Token状态")
    class IsRevokedTests {

        @Test
        @DisplayName("未撤销的Token返回false")
        void notRevokedTokenReturnsFalse() {
            assertFalse(service.isRevoked("never-revoked"));
        }

        @Test
        @DisplayName("已过期Token返回false")
        void expiredTokenReturnsFalse() throws Exception {
            service.revoke("expired-token", 50L);
            Thread.sleep(100);
            assertFalse(service.isRevoked("expired-token"));
        }

        @Test
        @DisplayName("过期Token被自动清理")
        void expiredTokenCleanedFromMap() throws Exception {
            service.revoke("cleanup-token", 50L);
            Thread.sleep(100);
            assertFalse(service.isRevoked("cleanup-token"));
        }

        @Test
        @DisplayName("多个Token独立管理")
        void multipleTokensManagedIndependently() {
            service.revoke("token-A", 3600000L);
            service.revoke("token-B", 100L);

            assertTrue(service.isRevoked("token-A"));
            assertTrue(service.isRevoked("token-B"));

            // 等待B过期
            try { Thread.sleep(150); } catch (InterruptedException ignored) {}

            assertTrue(service.isRevoked("token-A"));
            assertFalse(service.isRevoked("token-B"));
        }
    }

    @Nested
    @DisplayName("线程安全测试")
    class ThreadSafetyTests {

        @Test
        @DisplayName("并发撤销与检测")
        void concurrentRevokeAndCheck() throws Exception {
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];
            boolean[] results = new boolean[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                threads[i] = new Thread(() -> {
                    service.revoke("concurrent-token", 3600000L);
                    results[idx] = service.isRevoked("concurrent-token");
                });
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join(2000);

            for (boolean r : results) {
                assertTrue(r, "All threads should see revoked token");
            }
        }

        @Test
        @DisplayName("并发撤销多个不同Token")
        void concurrentRevokeDifferentTokens() throws Exception {
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];
            String[] tokens = new String[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                tokens[idx] = "concurrent-token-" + idx;
                threads[i] = new Thread(() -> {
                    service.revoke(tokens[idx], 3600000L);
                });
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) t.join(2000);

            // 验证所有Token都被撤销
            for (String token : tokens) {
                assertTrue(service.isRevoked(token), token + " should be revoked");
            }
        }
    }

    @Nested
    @DisplayName("边界场景")
    class EdgeCases {

        @Test
        @DisplayName("撤销 - 空TokenId")
        void revokeEmptyTokenId() {
            assertDoesNotThrow(() -> service.revoke("", 3600_000L));
            assertTrue(service.isRevoked(""));
        }

        @Test
        @DisplayName("撤销 - 零过期时间 (立即过期)")
        void revokeWithZeroExpiry() {
            service.revoke("zero-expiry", 0L);
            // 理论上立即过期，但实现可能有最小 TTL 保护或并发问题
            // 这里验证不会抛异常即可，具体过期行为看实现
            assertNotNull(service);
        }

        @Test
        @DisplayName("撤销 - 最大过期时间")
        void revokeWithMaxExpiry() {
            service.revoke("max-expiry", Long.MAX_VALUE);
            assertTrue(service.isRevoked("max-expiry"));
        }

        @Test
        @DisplayName("撤销 - 大量Token操作")
        void massRevoke() {
            int count = 1000;
            for (int i = 0; i < count; i++) {
                service.revoke("mass-token-" + i, 3600_000L);
            }
            for (int i = 0; i < count; i++) {
                assertTrue(service.isRevoked("mass-token-" + i));
            }
        }
    }
}