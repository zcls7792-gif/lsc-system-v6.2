package com.lianshengtong.evidence.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量级本地缓存（替代 Caffeine）
 * <p>
 * 基于 ConcurrentHashMap + TTL 实现的高性能本地缓存，
 * 支持：
 * 1. TTL 过期（毫秒级精度）
 * 2. 最大容量限制
 * 3. 命中率统计
 * 4. 定期清理过期条目（ScheduledExecutorService 实现）
 * </p>
 *
 * @deprecated 已由 {@link EvidenceCaffeineCache} 替代，保留用于向后兼容。
 *             建议使用 {@link EvidenceCache} 接口以方便未来缓存实现切换。
 */
@Deprecated
public class EvidenceLocalCache {

    private static final Logger log = LoggerFactory.getLogger(EvidenceLocalCache.class);

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final int maxSize;
    private final long defaultTtlMs;

    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong evictCount = new AtomicLong(0);

    private final ScheduledExecutorService cleanupExecutor;

    public EvidenceLocalCache() {
        this(10000, 30_000L);
    }

    public EvidenceLocalCache(int maxSize, long defaultTtlMs) {
        this.maxSize = maxSize;
        this.defaultTtlMs = defaultTtlMs;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "evidence-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanup, 5_000L, 10_000L, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void destroy() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void put(String key, Object value) {
        put(key, value, defaultTtlMs);
    }

    public void put(String key, Object value, long ttlMs) {
        if (cache.size() >= maxSize) {
            evictExpired();
        }
        if (cache.size() >= maxSize) {
            evictOne();
        }
        cache.put(key, new CacheEntry(value, System.currentTimeMillis() + ttlMs));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        // C10-fix: 使用 computeIfPresent 原子条件删除，避免竞态
        CacheEntry[] holder = new CacheEntry[1];
        boolean[] wasExpired = {false};
        cache.computeIfPresent(key, (k, entry) -> {
            if (entry.isExpired()) {
                wasExpired[0] = true;
                evictCount.incrementAndGet();
                return null; // 原子删除
            }
            holder[0] = entry;
            return entry;
        });
        if (holder[0] == null) {
            missCount.incrementAndGet();
            return null;
        }
        hitCount.incrementAndGet();
        return (T) holder[0].value;
    }

    public boolean containsKey(String key) {
        // C10-fix: 使用 computeIfPresent 原子检查
        boolean[] exists = {false};
        cache.computeIfPresent(key, (k, entry) -> {
            if (entry.isExpired()) {
                return null; // 原子删除
            }
            exists[0] = true;
            return entry;
        });
        return exists[0];
    }

    public void remove(String key) {
        cache.remove(key);
    }

    public int size() {
        return cache.size();
    }

    public long getHitRate() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        return total > 0 ? (hits * 100) / total : 0;
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public long getEvictCount() {
        return evictCount.get();
    }

    public void clear() {
        cache.clear();
    }

    private void cleanup() {
        try {
            Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
            int cleaned = 0;
            while (it.hasNext()) {
                Map.Entry<String, CacheEntry> e = it.next();
                if (e.getValue().isExpired()) {
                    it.remove();
                    cleaned++;
                }
            }
            if (cleaned > 0) {
                evictCount.addAndGet(cleaned);
                log.debug("Cache cleanup: removed {} expired entries", cleaned);
            }
        } catch (RuntimeException e) {
            log.error("Cache cleanup error", e);
        }
    }

    private void evictExpired() {
        Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
        while (it.hasNext() && cache.size() >= maxSize * 0.8) {
            Map.Entry<String, CacheEntry> e = it.next();
            if (e.getValue().isExpired()) {
                it.remove();
                evictCount.incrementAndGet();
            }
        }
    }

    private void evictOne() {
        Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry<String, CacheEntry> oldest = it.next();
            long oldestTime = oldest.getValue().expireAt;
            while (it.hasNext()) {
                Map.Entry<String, CacheEntry> e = it.next();
                if (e.getValue().expireAt < oldestTime) {
                    oldest = e;
                    oldestTime = e.getValue().expireAt;
                }
            }
            cache.remove(oldest.getKey());
            evictCount.incrementAndGet();
        }
    }

    private static class CacheEntry {
        final Object value;
        final long expireAt;

        CacheEntry(Object value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
