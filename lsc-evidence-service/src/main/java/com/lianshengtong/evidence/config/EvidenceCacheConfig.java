package com.lianshengtong.evidence.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 存证服务缓存配置（Caffeine版）
 * <p>
 * 使用 Caffeine 高性能本地缓存替代自研 EvidenceLocalCache，
 * 优化热点数据访问，减少区块链节点 RPC 调用次数。
 *
 * 缓存策略：
 * 1. 区块号缓存：txHash -> blockNumber，TTL 60s，最大 10000 条
 * 2. 链上验证缓存：dataHash -> verified(Boolean)，TTL 30s
 * 3. 存证状态缓存：recordId -> status，TTL 60s
 * </p>
 */
@Configuration
public class EvidenceCacheConfig {

    @Value("${lsc.evidence.cache.max-size:10000}")
    private int maxSize;

    @Value("${lsc.evidence.cache.default-ttl-ms:30000}")
    private long defaultTtlMs;

    @Bean
    public EvidenceCache evidenceCache() {
        return new EvidenceCaffeineCache(maxSize, defaultTtlMs);
    }

    /**
     * @deprecated 保留旧 Bean 名兼容，实际返回 Caffeine 实现
     */
    @Bean
    @Deprecated
    public EvidenceCache evidenceLocalCache() {
        return evidenceCache();
    }
}
