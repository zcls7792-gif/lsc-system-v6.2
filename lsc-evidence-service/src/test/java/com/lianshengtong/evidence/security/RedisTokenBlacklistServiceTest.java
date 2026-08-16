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

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Redis Token黑名单服务测试
 */
@ExtendWith(MockitoExtension.class)
class RedisTokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private RedisTokenBlacklistService service;

    @BeforeEach
    void setUp() {
        // 使用 lenient 避免 UnnecessaryStubbing 错误
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Nested
    @DisplayName("revoke 撤销Token")
    class RevokeTests {

        @Test
        @DisplayName("撤销Token - 设置黑名单Key")
        void revokeTokenSetsBlacklist() {
            service.revoke("token-jti-001", 3600000L);

            verify(valueOps).set(
                "lsc:evidence:token:blacklist:token-jti-001",
                "1",
                3600000L,
                TimeUnit.MILLISECONDS
            );
        }

        @Test
        @DisplayName("撤销Token - 零过期时间")
        void revokeWithZeroExpiry() {
            service.revoke("token-jti-002", 0L);

            verify(valueOps).set(
                "lsc:evidence:token:blacklist:token-jti-002",
                "1",
                0L,
                TimeUnit.MILLISECONDS
            );
        }

        @Test
        @DisplayName("撤销Token - 大过期时间")
        void revokeWithLargeExpiry() {
            service.revoke("token-jti-003", 86400000L);

            verify(valueOps).set(
                "lsc:evidence:token:blacklist:token-jti-003",
                "1",
                86400000L,
                TimeUnit.MILLISECONDS
            );
        }
    }

    @Nested
    @DisplayName("isRevoked 检查Token是否已撤销")
    class IsRevokedTests {

        @Test
        @DisplayName("Token在黑名单中 - 已撤销")
        void revokedWhenInBlacklist() {
            when(redisTemplate.hasKey("lsc:evidence:token:blacklist:revoked-token")).thenReturn(true);
            assertTrue(service.isRevoked("revoked-token"));
        }

        @Test
        @DisplayName("Token不在黑名单中 - 未撤销")
        void notRevokedWhenNotInBlacklist() {
            when(redisTemplate.hasKey("lsc:evidence:token:blacklist:valid-token")).thenReturn(false);
            assertFalse(service.isRevoked("valid-token"));
        }

        @Test
        @DisplayName("Redis返回null - 视为未撤销")
        void nullResponseTreatedAsNotRevoked() {
            when(redisTemplate.hasKey(anyString())).thenReturn(null);
            assertFalse(service.isRevoked("any-token"));
        }

        @Test
        @DisplayName("边界 - 空TokenId")
        void handleEmptyTokenId() {
            when(redisTemplate.hasKey("lsc:evidence:token:blacklist:")).thenReturn(false);
            assertFalse(service.isRevoked(""));
        }

        @Test
        @DisplayName("revoke - Redis连接异常时不抛NPE")
        void revoke_handlesRedisException() {
            doThrow(new RuntimeException("Redis connection lost"))
                    .when(valueOps).set(anyString(), anyString(), anyLong(), any());

            assertThrows(RuntimeException.class,
                    () -> service.revoke("token-jti-error", 3600_000L));
        }

        @Test
        @DisplayName("isRevoked - Redis异常时不抛NPE")
        void isRevoked_handlesRedisException() {
            when(redisTemplate.hasKey(anyString()))
                    .thenThrow(new RuntimeException("Redis timeout"));

            assertThrows(RuntimeException.class,
                    () -> service.isRevoked("token-jti-error"));
        }

        @Test
        @DisplayName("isRevoked - 连续调用多次确保幂等")
        void isRevoked_idempotent() {
            when(redisTemplate.hasKey(anyString())).thenReturn(true);
            
            for (int i = 0; i < 10; i++) {
                assertTrue(service.isRevoked("idempotent-token"));
            }
            
            verify(redisTemplate, times(10)).hasKey("lsc:evidence:token:blacklist:idempotent-token");
        }
    }

    @Nested
    @DisplayName("并发与边界")
    class ConcurrencyAndEdgeTests {

        @Test
        @DisplayName("高并发revoke - 确保Redis调用不冲突")
        void concurrentRevoke() {
            int threadCount = 50;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                threads[i] = new Thread(() -> {
                    service.revoke("concurrent-token-" + idx, 3600_000L);
                });
            }

            for (Thread t : threads) t.start();
            for (Thread t : threads) {
                try { t.join(2000); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // 验证所有Token都被设置
            verify(valueOps, times(threadCount)).set(anyString(), eq("1"), eq(3600_000L), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        @DisplayName("revoke - 负数过期时间")
        void revokeWithNegativeExpiry() {
            assertDoesNotThrow(() -> service.revoke("negative-expiry", -1000L));
            verify(valueOps).set(eq("lsc:evidence:token:blacklist:negative-expiry"), eq("1"), eq(-1000L), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        @DisplayName("revoke - 极大TokenId")
        void revokeWithLargeTokenId() {
            String largeToken = "a".repeat(1000);
            service.revoke(largeToken, 3600_000L);
            verify(valueOps).set(
                eq("lsc:evidence:token:blacklist:" + largeToken),
                eq("1"),
                eq(3600_000L),
                eq(TimeUnit.MILLISECONDS)
            );
        }
    }
}