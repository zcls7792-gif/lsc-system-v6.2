package com.lianshengtong.evidence.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式登录尝试跟踪实现
 * <p>
 * 适用于多副本生产部署，所有实例共享登录失败计数。
 * <p>
 * Redis Key 设计：
 * <ul>
 *   <li>{@code lsc:evidence:login:fail:count:{username}} - 失败次数 (计数器)</li>
 *   <li>{@code lsc:evidence:login:fail:lock:{username}} - 锁定标记 (存在即表示锁定)</li>
 * </ul>
 * <p>
 * 失败计数 TTL 为锁定时长 + 缓冲期，避免计数永不消失。
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisLoginAttemptService implements LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(RedisLoginAttemptService.class);

    private static final String FAIL_COUNT_KEY_PREFIX = "lsc:evidence:login:fail:count:";
    private static final String LOCK_KEY_PREFIX = "lsc:evidence:login:fail:lock:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${lsc.evidence.auth.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${lsc.evidence.auth.lockout-duration-ms:300000}")
    private long lockoutDurationMs;

    @Override
    public boolean isLocked(String username) {
        Boolean locked = redisTemplate.hasKey(LOCK_KEY_PREFIX + username);
        return Boolean.TRUE.equals(locked);
    }

    @Override
    public long remainingLockMs(String username) {
        Long ttl = redisTemplate.getExpire(LOCK_KEY_PREFIX + username, TimeUnit.MILLISECONDS);
        if (ttl == null || ttl < 0) return 0;
        return ttl;
    }

    @Override
    public void recordFailure(String username) {
        String countKey = FAIL_COUNT_KEY_PREFIX + username;
        String lockKey = LOCK_KEY_PREFIX + username;

        Long count = redisTemplate.opsForValue().increment(countKey);
        if (count == null) return;

        // 失败计数 TTL = 锁定时长 + 1 分钟缓冲 (避免计数永久留存)
        redisTemplate.expire(countKey, lockoutDurationMs + 60_000L, TimeUnit.MILLISECONDS);

        if (count >= maxLoginAttempts) {
            // 触发锁定 - 设置锁定 key，TTL = 锁定时长
            redisTemplate.opsForValue().set(lockKey, String.valueOf(count),
                    lockoutDurationMs, TimeUnit.MILLISECONDS);
            log.warn("账户锁定 (Redis 分布式) username={} 失败次数={} 锁定时长={}ms",
                    username, count, lockoutDurationMs);
        }
    }

    @Override
    public void recordSuccess(String username) {
        redisTemplate.delete(FAIL_COUNT_KEY_PREFIX + username);
        redisTemplate.delete(LOCK_KEY_PREFIX + username);
    }
}
