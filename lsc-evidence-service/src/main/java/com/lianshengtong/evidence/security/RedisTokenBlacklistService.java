package com.lianshengtong.evidence.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式 Token 黑名单实现
 * <p>
 * 适用于多副本生产部署，所有实例共享黑名单。
 * <p>
 * Redis Key 设计：
 * <ul>
 *   <li>{@code lsc:evidence:token:blacklist:{tokenJti}} - 黑名单标记 (存在即表示已撤销)</li>
 * </ul>
 * <p>
 * TTL 与 Token 剩余有效期一致，过期后 Redis 自动清理。
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBlacklistService.class);

    private static final String BLACKLIST_KEY_PREFIX = "lsc:evidence:token:blacklist:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void revoke(String tokenJti, long expireMs) {
        String key = BLACKLIST_KEY_PREFIX + tokenJti;
        redisTemplate.opsForValue().set(key, "1", expireMs, TimeUnit.MILLISECONDS);
        log.info("Token 已加入 Redis 黑名单 jti={} 过期时间={}ms", tokenJti, expireMs);
    }

    @Override
    public boolean isRevoked(String tokenJti) {
        Boolean exists = redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + tokenJti);
        return Boolean.TRUE.equals(exists);
    }
}
