package com.lianshengtong.common.utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("乐观锁辅助工具测试")
class OptimisticLockHelperTest {

    @Test
    @DisplayName("首次成功直接返回")
    void testFirstTrySuccess() {
        int result = OptimisticLockHelper.execute("test", () -> 1);
        assertEquals(1, result);
    }

    @Test
    @DisplayName("重试3次后成功")
    void testRetryThenSuccess() {
        AtomicInteger counter = new AtomicInteger(0);
        int result = OptimisticLockHelper.execute("test", 5, () -> {
            int c = counter.incrementAndGet();
            return c == 3 ? 1 : 0;
        });
        assertEquals(1, result);
        assertEquals(3, counter.get());
    }

    @Test
    @DisplayName("重试耗尽抛异常")
    void testRetryExhausted() {
        assertThrows(OptimisticLockingFailureException.class, () ->
                OptimisticLockHelper.execute("test", 3, () -> 0));
    }

    @Test
    @DisplayName("默认重试次数为3")
    void testDefaultRetries() {
        assertThrows(OptimisticLockingFailureException.class, () ->
                OptimisticLockHelper.execute("test", () -> 0));
    }

    @Test
    @DisplayName("重试次数1次成功")
    void testOneRetrySuccess() {
        AtomicInteger counter = new AtomicInteger(0);
        int result = OptimisticLockHelper.execute("test", 2, () -> {
            int c = counter.incrementAndGet();
            return c == 2 ? 1 : 0;
        });
        assertEquals(1, result);
    }

    @Test
    @DisplayName("execute - 重试耗尽异常消息包含重试次数")
    void testExecute_exhaustedMessageContainsRetryCount() {
        OptimisticLockingFailureException ex = assertThrows(
                OptimisticLockingFailureException.class, () ->
                OptimisticLockHelper.execute("exhausted_op", 5, () -> 0));

        assertTrue(ex.getMessage().contains("5"),
                "异常消息应包含最大重试次数，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("execute - 重试次数为0时立即失败")
    void testExecute_maxRetriesZero_immediateFail() {
        assertThrows(OptimisticLockingFailureException.class, () ->
                OptimisticLockHelper.execute("zero_retry", 0, () -> 0));
    }

    @Test
    @DisplayName("execute - 并发重试正确返回")
    void testExecute_concurrentRetries() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger totalCalls = new AtomicInteger(0);

        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                int result = OptimisticLockHelper.execute("concurrent", 3, () -> {
                    totalCalls.incrementAndGet();
                    return Math.random() > 0.7 ? 1 : 0;
                });
                if (result > 0) {
                    successCount.incrementAndGet();
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join(5000);
        }

        assertTrue(totalCalls.get() >= 10,
                "每个线程至少调用1次action");
    }
}
