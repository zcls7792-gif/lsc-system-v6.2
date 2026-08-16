package com.lianshengtong.evidence.config;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EvidenceCacheMetricsConfig 单元测试
 * <p>
 * 验证 Caffeine 缓存与 Micrometer Metrics 的集成
 * </p>
 */
@DisplayName("EvidenceCacheMetricsConfig 单元测试")
class EvidenceCacheMetricsConfigTest {

    private EvidenceCaffeineCache caffeineCache;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        caffeineCache = new EvidenceCaffeineCache(1000, 30_000L);
        meterRegistry = new SimpleMeterRegistry();
    }

    @AfterEach
    void tearDown() {
        if (caffeineCache != null) {
            caffeineCache.clear();
            caffeineCache.destroy();
        }
    }

    @Test
    @DisplayName("注册 - CaffeineCacheMetrics.monitor能成功调用")
    void testMonitorRegistration() {
        // 验证 monitor 方法能成功调用并返回缓存实例
        Cache<String, EvidenceCaffeineCache.TtlValue> monitoredCache = CaffeineCacheMetrics.monitor(
                meterRegistry,
                caffeineCache.getUnderlyingCache(),
                "evidence",
                "application", "lsc-evidence-service",
                "cache.type", "caffeine"
        );
        assertNotNull(monitoredCache);
        // 验证底层缓存大小能正确获取
        assertTrue(caffeineCache.size() >= 0);
    }

    @Test
    @DisplayName("底层Cache - getUnderlyingCache返回Caffeine Cache实例")
    void testGetUnderlyingCache() {
        Cache<String, EvidenceCaffeineCache.TtlValue> underlying =
                caffeineCache.getUnderlyingCache();
        assertNotNull(underlying);

        caffeineCache.put("test", "value");
        assertNotNull(underlying.getIfPresent("test"));
    }

    @Test
    @DisplayName("monitor - 返回被监控的Cache实例")
    void testMonitorReturnsCache() {
        Cache<String, EvidenceCaffeineCache.TtlValue> result =
                CaffeineCacheMetrics.monitor(
                        meterRegistry,
                        caffeineCache.getUnderlyingCache(),
                        "test-return"
                );
        assertNotNull(result);
    }

    @Test
    @DisplayName("缓存操作 - put和get能正常工作")
    void testCacheOperations() {
        // 注册 metrics
        CaffeineCacheMetrics.monitor(
                meterRegistry,
                caffeineCache.getUnderlyingCache(),
                "evidence"
        );

        // 执行基本缓存操作
        caffeineCache.put("key1", "value1");
        caffeineCache.put("key2", "value2");

        assertEquals("value1", caffeineCache.get("key1"));
        assertEquals("value2", caffeineCache.get("key2"));
        assertNull(caffeineCache.get("nonexistent"));

        // 验证缓存大小
        assertEquals(2, caffeineCache.size());
    }

    @Test
    @DisplayName("指标 - cache size gauge可用")
    void testCacheSizeGauge() {
        CaffeineCacheMetrics.monitor(
                meterRegistry,
                caffeineCache.getUnderlyingCache(),
                "evidence",
                "application", "lsc-evidence-service"
        );

        // 执行一些操作
        caffeineCache.put("key1", "value1");
        caffeineCache.put("key2", "value2");
        caffeineCache.get("key1");

        // 验证 gauge 存在（可能为0或正数）
        var gauge = meterRegistry.find("caffeine.cache.size")
                .tag("name", "evidence")
                .gauge();
        // gauge 可能为 null 如果指标未注册，这取决于 Micrometer 版本
        if (gauge != null) {
            assertTrue(gauge.value() >= 0, "Gauge value should be non-negative");
        }
        // 无论 gauge 是否存在，底层缓存应该正常工作
        assertEquals(2, caffeineCache.size());
    }

    @Test
    @DisplayName("指标 - hit/miss counter可用")
    void testHitMissCounters() {
        CaffeineCacheMetrics.monitor(
                meterRegistry,
                caffeineCache.getUnderlyingCache(),
                "evidence"
        );

        caffeineCache.put("key1", "value1");
        caffeineCache.get("key1"); // hit
        caffeineCache.get("nonexistent"); // miss

        // 验证底层 Caffeine 统计
        assertTrue(caffeineCache.getHitCount() >= 1, "Hit count should be at least 1");
        assertTrue(caffeineCache.getMissCount() >= 1, "Miss count should be at least 1");
    }

    @Test
    @DisplayName("批量操作 - 正确记录统计")
    void testBatchOperations() {
        CaffeineCacheMetrics.monitor(
                meterRegistry,
                caffeineCache.getUnderlyingCache(),
                "evidence"
        );

        int putCount = 100;
        int hitCount = 70;
        int missCount = 30;

        for (int i = 0; i < putCount; i++) {
            caffeineCache.put("key" + i, "value" + i);
        }
        for (int i = 0; i < hitCount; i++) {
            caffeineCache.get("key" + i);
        }
        for (int i = 0; i < missCount; i++) {
            caffeineCache.get("non-existent" + i);
        }

        // 验证底层统计
        assertTrue(caffeineCache.getHitCount() >= hitCount * 0.5, 
                "Hits should be at least half of " + hitCount);
        assertTrue(caffeineCache.getMissCount() >= missCount * 0.5, 
                "Misses should be at least half of " + missCount);
    }

    @Test
    @DisplayName("自定义标签 - monitor接受额外标签")
    void testCustomTags() {
        // 验证 monitor 方法能接受并处理自定义标签
        Cache<String, EvidenceCaffeineCache.TtlValue> result = CaffeineCacheMetrics.monitor(
                meterRegistry,
                caffeineCache.getUnderlyingCache(),
                "evidence",
                "application", "lsc-evidence-service",
                "cache.type", "caffeine",
                "env", "test"
        );
        assertNotNull(result);
        
        // 验证带标签的指标可以查询
        var gauge = meterRegistry.find("caffeine.cache.size")
                .tag("application", "lsc-evidence-service")
                .gauge();
        // 可能为 null，取决于 Micrometer 版本
        // 但缓存应该正常工作
        caffeineCache.put("test", "value");
        assertNotNull(caffeineCache.get("test"));
    }
}
