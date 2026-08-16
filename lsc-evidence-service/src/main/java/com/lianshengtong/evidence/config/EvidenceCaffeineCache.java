package com.lianshengtong.evidence.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Caffeine 的本地缓存实现
 * <p>
 * 替换自研 EvidenceLocalCache，提供：
 * 1. 高并发无锁读取（基于 ConcurrentHashMap）
 * 2. 智能淘汰策略（Window TinyLFU 算法）
 * 3. 每条目独立 TTL（通过 Expiry API 实现）
 * 4. 内置统计（hit/miss/eviction 原子计数 + Caffeine stats）
 * 5. 自动清理（基于时间的过期条目自动淘汰，无需后台线程）
 * 6. 去除手动清理线程（ScheduledExecutorService），由 Caffeine 自动管理
 * </p>
 *
 * @see Caffeine
 * @see Expiry
 */
public class EvidenceCaffeineCache implements EvidenceCache {

    private static final Logger log = LoggerFactory.getLogger(EvidenceCaffeineCache.class);

    private final Cache<String, TtlValue> cache;
    private final long defaultTtlMs;
    private final int maxSize;

    public EvidenceCaffeineCache() {
        this(10_000, 30_000L);
    }

    public EvidenceCaffeineCache(int maxSize, long defaultTtlMs) {
        this.maxSize = maxSize;
        this.defaultTtlMs = defaultTtlMs;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfter(new TtlExpiry(defaultTtlMs))
                .recordStats()
                .removalListener((key, value, cause) -> {
                    log.debug("Cache entry removed: key={}, cause={}", key, cause);
                })
                .build();
        log.info("EvidenceCaffeineCache initialized: maxSize={}, defaultTtlMs={}", maxSize, defaultTtlMs);
    }

    @Override
    public void put(String key, Object value) {
        put(key, value, defaultTtlMs);
    }

    @Override
    public void put(String key, Object value, long ttlMs) {
        cache.put(key, new TtlValue(value, ttlMs));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        TtlValue entry = cache.getIfPresent(key);
        if (entry == null) {
            return null;
        }
        return (T) entry.value;
    }

    @Override
    public boolean containsKey(String key) {
        return cache.getIfPresent(key) != null;
    }

    @Override
    public void remove(String key) {
        cache.invalidate(key);
    }

    @Override
    public int size() {
        return (int) cache.estimatedSize();
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    @Override
    public long getHitCount() {
        return cache.stats().hitCount();
    }

    @Override
    public long getMissCount() {
        return cache.stats().missCount();
    }

    @Override
    public long getEvictCount() {
        return cache.stats().evictionCount();
    }

    @Override
    public long getHitRate() {
        CacheStats stats = cache.stats();
        long hits = stats.hitCount();
        long misses = stats.missCount();
        long total = hits + misses;
        return total > 0 ? (hits * 100) / total : 0;
    }

    public CacheStats getStats() {
        return cache.stats();
    }

    /**
     * 获取底层 Caffeine Cache 实例，用于 Micrometer Metrics 集成
     */
    public Cache<String, TtlValue> getUnderlyingCache() {
        return cache;
    }

    @PreDestroy
    public void destroy() {
        cache.invalidateAll();
        cache.cleanUp();
        CacheStats stats = cache.stats();
        log.info("EvidenceCaffeineCache destroyed. stats: hits={}, misses={}, evictions={}, hitRate={}%",
                stats.hitCount(), stats.missCount(), stats.evictionCount(),
                stats.hitRate() * 100);
    }

    /**
     * TTL值包装
     */
    static class TtlValue {
        final Object value;
        final long ttlMs;

        TtlValue(Object value, long ttlMs) {
            this.value = value;
            this.ttlMs = ttlMs;
        }
    }

    /**
     * Caffeine Expiry 实现，支持每条目的独立过期时间
     */
    static class TtlExpiry implements Expiry<String, TtlValue> {
        private final long defaultTtlMs;

        TtlExpiry(long defaultTtlMs) {
            this.defaultTtlMs = defaultTtlMs;
        }

        @Override
        public long expireAfterCreate(String key, TtlValue value, long currentTime) {
            long ttl = value.ttlMs > 0 ? value.ttlMs : defaultTtlMs;
            return TimeUnit.MILLISECONDS.toNanos(ttl);
        }

        @Override
        public long expireAfterUpdate(String key, TtlValue value, long currentTime, long currentDuration) {
            long ttl = value.ttlMs > 0 ? value.ttlMs : defaultTtlMs;
            return TimeUnit.MILLISECONDS.toNanos(ttl);
        }

        @Override
        public long expireAfterRead(String key, TtlValue value, long currentTime, long currentDuration) {
            long ttl = value.ttlMs > 0 ? value.ttlMs : defaultTtlMs;
            return TimeUnit.MILLISECONDS.toNanos(ttl);
        }
    }
}
