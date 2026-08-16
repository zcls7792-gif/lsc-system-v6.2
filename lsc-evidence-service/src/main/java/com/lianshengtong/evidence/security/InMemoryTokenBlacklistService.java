package com.lianshengtong.evidence.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机内存 Token 黑名单实现
 * <p>
 * 适用于 standalone / dev 环境单节点部署。
 * 生产环境多副本部署应使用 {@link RedisTokenBlacklistService}。
 * <p>
 * 注意：内存方案下，登出仅对当前实例生效，其它实例仍接受该 Token。
 */
@Service
@ConditionalOnMissingBean(RedisTokenBlacklistService.class)
public class InMemoryTokenBlacklistService implements TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTokenBlacklistService.class);

    private static final Map<String, Long> BLACKLIST = new ConcurrentHashMap<>();

    @Override
    public void revoke(String tokenJti, long expireMs) {
        long expireAt;
        // 防止溢出
        if (expireMs > Long.MAX_VALUE - System.currentTimeMillis()) {
            expireAt = Long.MAX_VALUE;
        } else {
            expireAt = System.currentTimeMillis() + expireMs;
        }
        BLACKLIST.put(tokenJti, expireAt);
        log.info("Token 已加入黑名单 jti={} 过期时间={}ms", tokenJti, expireMs);
    }

    @Override
    public boolean isRevoked(String tokenJti) {
        Long expireAt = BLACKLIST.get(tokenJti);
        if (expireAt == null) return false;
        if (System.currentTimeMillis() > expireAt) {
            BLACKLIST.remove(tokenJti);
            return false;
        }
        return true;
    }
}
