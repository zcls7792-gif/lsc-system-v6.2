package com.lianshengtong.evidence.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单机内存登录尝试跟踪实现
 * <p>
 * 适用于 standalone / dev 环境单节点部署。
 * 生产环境多副本部署应使用 {@link RedisLoginAttemptService}。
 */
@Service
@ConditionalOnMissingBean(RedisLoginAttemptService.class)
public class InMemoryLoginAttemptService implements LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryLoginAttemptService.class);

    private static final Map<String, LoginAttempt> ATTEMPTS = new ConcurrentHashMap<>();

    @Value("${lsc.evidence.auth.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${lsc.evidence.auth.lockout-duration-ms:300000}")
    private long lockoutDurationMs;

    @Override
    public boolean isLocked(String username) {
        LoginAttempt attempt = ATTEMPTS.get(username);
        if (attempt == null) return false;
        return attempt.isLocked();
    }

    @Override
    public long remainingLockMs(String username) {
        LoginAttempt attempt = ATTEMPTS.get(username);
        if (attempt == null) return 0;
        return attempt.remainingLockMs();
    }

    @Override
    public void recordFailure(String username) {
        LoginAttempt attempt = ATTEMPTS.computeIfAbsent(username,
                k -> new LoginAttempt(maxLoginAttempts, lockoutDurationMs));
        attempt.increment();
        if (attempt.isLocked()) {
            log.warn("账户锁定 username={} 失败次数={} 锁定时长={}ms",
                    username, attempt.getCount(), lockoutDurationMs);
        }
    }

    @Override
    public void recordSuccess(String username) {
        ATTEMPTS.remove(username);
    }

    /**
     * 登录失败次数跟踪 (线程安全)
     */
    private static class LoginAttempt {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long firstFailTime = System.currentTimeMillis();
        private volatile long lockUntil = 0;
        private final int maxAttempts;
        private final long lockDurationMs;

        LoginAttempt(int maxAttempts, long lockDurationMs) {
            this.maxAttempts = maxAttempts;
            this.lockDurationMs = lockDurationMs;
        }

        void increment() {
            int c = count.incrementAndGet();
            if (c >= maxAttempts) {
                lockUntil = System.currentTimeMillis() + lockDurationMs;
            }
        }

        boolean isLocked() {
            if (lockUntil == 0) return false;
            if (System.currentTimeMillis() > lockUntil) {
                lockUntil = 0;
                count.set(0);
                firstFailTime = 0;
                return false;
            }
            return true;
        }

        long remainingLockMs() {
            return Math.max(0, lockUntil - System.currentTimeMillis());
        }

        int getCount() {
            return count.get();
        }
    }
}
