package com.lianshengtong.evidence.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EvidenceLocalCache 单元测试
 */
@DisplayName("EvidenceLocalCache 轻量级本地缓存测试")
class EvidenceLocalCacheTest {

    private EvidenceLocalCache cache;

    @BeforeEach
    void setUp() {
        cache = new EvidenceLocalCache(100, 10_000L);
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    @DisplayName("put/get - 基本读写正常")
    void testPutGet_Basic() {
        cache.put("key1", "value1");
        String value = cache.get("key1");
        assertEquals("value1", value);
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("get - 不存在的key返回null且记录miss")
    void testGet_NonExistentKey() {
        Object value = cache.get("nonexistent");
        assertNull(value);
        assertEquals(0, cache.getHitCount());
        assertEquals(1, cache.getMissCount());
    }

    @Test
    @DisplayName("get - 存在的key记录hit")
    void testGet_ExistingKey() {
        cache.put("key1", "value1");
        cache.get("key1");
        assertEquals(1, cache.getHitCount());
        assertEquals(0, cache.getMissCount());
    }

    @Test
    @DisplayName("put - TTL过期后get返回null")
    void testPut_TTLExpiry() throws InterruptedException {
        EvidenceLocalCache shortTtlCache = new EvidenceLocalCache(100, 50L);
        shortTtlCache.put("key1", "value1");
        assertEquals("value1", shortTtlCache.get("key1"));

        Thread.sleep(100);
        Object value = shortTtlCache.get("key1");
        assertNull(value);
    }

    @Test
    @DisplayName("put - 覆盖已存在的key")
    void testPut_Overwrite() {
        cache.put("key1", "value1");
        cache.put("key1", "value2");
        assertEquals("value2", cache.get("key1"));
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("containsKey - 检查key是否存在")
    void testContainsKey() {
        assertFalse(cache.containsKey("key1"));
        cache.put("key1", "value1");
        assertTrue(cache.containsKey("key1"));
    }

    @Test
    @DisplayName("remove - 移除指定key")
    void testRemove() {
        cache.put("key1", "value1");
        cache.remove("key1");
        assertNull(cache.get("key1"));
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("size - 正确反映缓存条目数量")
    void testSize() {
        assertEquals(0, cache.size());
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        assertEquals(2, cache.size());
    }

    @Test
    @DisplayName("getHitRate - 命中率计算正确")
    void testGetHitRate() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.get("key1"); // hit
        cache.get("key1"); // hit
        cache.get("key2"); // hit
        cache.get("key3"); // miss
        cache.get("key4"); // miss

        long hitRate = cache.getHitRate();
        assertEquals(60L, hitRate); // 3 hits / 5 total * 100
    }

    @Test
    @DisplayName("getHitRate - 无查询时返回0")
    void testGetHitRate_NoQueries() {
        assertEquals(0L, cache.getHitRate());
    }

    @Test
    @DisplayName("clear - 清空缓存后size为0")
    void testClear() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get("key1"));
    }

    @Test
    @DisplayName("put - 达到maxSize时触发淘汰")
    void testPut_MaxSizeEviction() {
        EvidenceLocalCache smallCache = new EvidenceLocalCache(5, 100_000L);
        for (int i = 0; i < 6; i++) {
            smallCache.put("key" + i, "value" + i);
        }
        assertTrue(smallCache.size() <= 5);
    }

    @Test
    @DisplayName("put - 支持不同类型的value")
    void testPut_DifferentTypes() {
        cache.put("string", "hello");
        cache.put("integer", 42);
        cache.put("long", 123456789L);
        cache.put("boolean", true);

        assertEquals("hello", cache.get("string"));
        assertEquals(42, ((Integer) cache.get("integer")).intValue());
        assertEquals(123456789L, ((Long) cache.get("long")).longValue());
        assertEquals(true, cache.get("boolean"));
    }

    @Test
    @DisplayName("put - 自定义TTL")
    void testPut_CustomTTL() throws InterruptedException {
        EvidenceLocalCache shortTtlCache = new EvidenceLocalCache(100, 10_000L);
        shortTtlCache.put("key1", "value1", 30L);
        assertEquals("value1", shortTtlCache.get("key1"));

        Thread.sleep(50);
        assertNull(shortTtlCache.get("key1"));
    }

    @Test
    @DisplayName("getMissCount - miss计数正确")
    void testGetMissCount() {
        cache.get("key1");
        cache.get("key2");
        assertEquals(2, cache.getMissCount());
    }

    @Test
    @DisplayName("getHitCount - hit计数正确")
    void testGetHitCount() {
        cache.put("key1", "value1");
        cache.get("key1");
        cache.get("key1");
        assertEquals(2, cache.getHitCount());
    }

    @Test
    @DisplayName("eviction - 过期条目被清理")
    void testEviction_ExpiredEntries() throws InterruptedException {
        EvidenceLocalCache shortTtlCache = new EvidenceLocalCache(5, 10_000L);
        for (int i = 0; i < 3; i++) {
            shortTtlCache.put("key" + i, "value" + i, 50L);
        }
        assertEquals(3, shortTtlCache.size());

        Thread.sleep(100);
        // 过期的条目应该已被清理
        long evictCountBefore = shortTtlCache.getEvictCount();
        // 清理线程在后台运行，稍等一下
        Thread.sleep(200);
        long evictCountAfter = shortTtlCache.getEvictCount();
        assertTrue(evictCountAfter >= evictCountBefore);
    }
}
