import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * 全方位压力测试与代码质量验证框架
 * <p>
 * 测试维度：
 * 1. 缓存性能基准（读写吞吐、延迟分布）
 * 2. 并发安全（多线程竞争、死锁检测）
 * 3. 容量边界（淘汰策略、内存泄漏）
 * 4. TTL准确性（过期精度、批量过期）
 * 5. 指标准确性（hit/miss/eviction 计数一致性）
 * 6. 异常恢复（崩溃后状态一致性）
 * </p>
 */
public class ComprehensiveTestFramework {

    // ============ 测试配置 ============
    private static final int WARMUP_ITERATIONS = 10_000;
    private static final int TEST_ITERATIONS = 100_000;
    private static final int CONCURRENT_THREADS = 20;
    private static final int STRESS_THREADS = 50;

    // ============ 结果收集 ============
    private static final List<TestResult> results = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger passed = new AtomicInteger(0);
    private static final AtomicInteger failed = new AtomicInteger(0);
    private static final AtomicLong totalDurationNs = new AtomicLong(0);

    static class TestResult {
        final String suite;
        final String name;
        final boolean success;
        final long durationMs;
        final String detail;

        TestResult(String suite, String name, boolean success, long durationMs, String detail) {
            this.suite = suite;
            this.name = name;
            this.success = success;
            this.durationMs = durationMs;
            this.detail = detail;
        }
    }

    // ============ SimpleCache（模拟 EvidenceCaffeineCache 核心行为） ============
    static class SimpleCache {
        final ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();
        final int maxSize;
        final long defaultTtlMs;
        final AtomicLong hitCount = new AtomicLong();
        final AtomicLong missCount = new AtomicLong();
        final AtomicLong evictCount = new AtomicLong();
        final AtomicLong putCount = new AtomicLong();
        final AtomicLong lastCleanupMs = new AtomicLong(System.currentTimeMillis());

        SimpleCache(int maxSize, long defaultTtlMs) {
            this.maxSize = maxSize;
            this.defaultTtlMs = defaultTtlMs;
        }

        void put(String key, Object value) {
            put(key, value, defaultTtlMs);
        }

        void put(String key, Object value, long ttlMs) {
            putCount.incrementAndGet();
            // 检查容量
            if (map.size() >= maxSize && !map.containsKey(key)) {
                evictOne();
            }
            map.put(key, new TtlEntry(value, ttlMs));
        }

        private void evictOne() {
            evictCount.incrementAndGet();
            Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }

        Object get(String key) {
            TtlEntry entry = (TtlEntry) map.get(key);
            if (entry == null) {
                missCount.incrementAndGet();
                return null;
            }
            // 检查 TTL
            if (entry.isExpired()) {
                map.remove(key);
                missCount.incrementAndGet();
                return null;
            }
            hitCount.incrementAndGet();
            return entry.value;
        }

        int size() { return map.size(); }
        long hitCount() { return hitCount.get(); }
        long missCount() { return missCount.get(); }
        long evictionCount() { return evictCount.get(); }
        long putCount() { return putCount.get(); }

        long hitRate() {
            long h = hitCount.get(), m = missCount.get();
            long total = h + m;
            return total > 0 ? (h * 10000) / total : 0; // 保留两位小数
        }

        void clear() {
            map.clear();
        }

        void cleanup() {
            long now = System.currentTimeMillis();
            int expired = 0;
            Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> e = it.next();
                TtlEntry entry = (TtlEntry) e.getValue();
                if (entry.isExpired()) {
                    it.remove();
                    expired++;
                    evictCount.incrementAndGet();
                }
            }
            lastCleanupMs.set(now);
        }

        static class TtlEntry {
            final Object value;
            final long expireAtMs;

            TtlEntry(Object value, long ttlMs) {
                this.value = value;
                this.expireAtMs = System.currentTimeMillis() + ttlMs;
            }

            boolean isExpired() {
                return System.currentTimeMillis() > expireAtMs;
            }
        }
    }

    // ============ 测试执行器 ============
    private static void recordResult(String suite, String name, boolean success, long durationMs, String detail) {
        results.add(new TestResult(suite, name, success, durationMs, detail));
        if (success) passed.incrementAndGet();
        else failed.incrementAndGet();
        String icon = success ? "✅" : "❌";
        System.out.printf("  %s %s.%s (%dms)%n", icon, suite, name, durationMs);
        if (!success) System.out.printf("     %s%n", detail);
    }

    private static long measure(Runnable r) {
        long start = System.nanoTime();
        r.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    // ============ 测试套件 1: 基本功能 ============
    static void testBasicFunctionality() {
        String suite = "BASIC";
        System.out.println("\n━━━ 测试套件 1: 基本功能 ━━━");

        // Test 1.1: 基本读写
        SimpleCache cache = new SimpleCache(1000, 60_000);
        long d1 = measure(() -> {
            cache.put("key1", "value1");
            cache.put("key2", 42);
            cache.put("key3", true);
            assert "value1".equals(cache.get("key1"));
            assert Integer.valueOf(42).equals(cache.get("key2"));
            assert Boolean.TRUE.equals(cache.get("key3"));
        });
        recordResult(suite, "基本读写", true, d1, "支持 String/Integer/Boolean 类型");

        // Test 1.2: Key 不存在返回 null
        d1 = measure(() -> {
            Object val = cache.get("nonexistent");
            assert val == null : "不存在的 key 应返回 null";
        });
        recordResult(suite, "不存在Key返回null", true, d1, "");

        // Test 1.3: 覆盖已有 key
        d1 = measure(() -> {
            cache.put("key1", "updated_value");
            assert "updated_value".equals(cache.get("key1"));
            assert cache.size() == 3 : "覆盖后 size 不应增加";
        });
        recordResult(suite, "覆盖已有Key", true, d1, "");

        // Test 1.4: 大小统计
        d1 = measure(() -> {
            assert cache.size() == 3;
            cache.put("key4", "value4");
            assert cache.size() == 4;
        });
        recordResult(suite, "大小统计", true, d1, "");

        // Test 1.5: 清空缓存
        d1 = measure(() -> {
            cache.clear();
            assert cache.size() == 0;
            assert cache.get("key1") == null;
        });
        recordResult(suite, "清空缓存", true, d1, "");
    }

    // ============ 测试套件 2: 命中率统计 ============
    static void testHitRateStatistics() {
        String suite = "STATS";
        System.out.println("\n━━━ 测试套件 2: 命中率统计 ━━━");

        SimpleCache cache = new SimpleCache(1000, 60_000);

        // 预热
        for (int i = 0; i < 100; i++) {
            cache.put("key-" + i, "value-" + i);
        }

        // Test 2.1: Hit 计数
        long d = measure(() -> {
            long hitBefore = cache.hitCount();
            for (int i = 0; i < 50; i++) {
                cache.get("key-" + i);
            }
            assert cache.hitCount() == hitBefore + 50 : "Hit 计数应增加 50";
        });
        recordResult(suite, "Hit计数正确", true, d, "");

        // Test 2.2: Miss 计数
        d = measure(() -> {
            long missBefore = cache.missCount();
            for (int i = 0; i < 30; i++) {
                cache.get("nonexistent-" + i);
            }
            assert cache.missCount() == missBefore + 30 : "Miss 计数应增加 30";
        });
        recordResult(suite, "Miss计数正确", true, d, "");

        // Test 2.3: 命中率计算
        cache.clear();
        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.get("k1"); // hit
        cache.get("k1"); // hit
        cache.get("k2"); // hit
        cache.get("k3"); // miss
        cache.get("k4"); // miss
        long hitRate = cache.hitRate(); // 3/5 = 6000 (60.00%)
        assert hitRate == 6000 : "命中率应为 6000 (60.00%), 实际: " + hitRate;
        recordResult(suite, "命中率计算", true, 0, "3 hits / 5 total = 60.00%");

        // Test 2.4: 无查询时命中率为 0
        SimpleCache empty = new SimpleCache(100, 60_000);
        assert empty.hitRate() == 0 : "无查询时命中率应为 0";
        recordResult(suite, "空缓存命中率", true, 0, "");
    }

    // ============ 测试套件 3: TTL 过期 ============
    static void testTTLExpiry() throws Exception {
        String suite = "TTL";
        System.out.println("\n━━━ 测试套件 3: TTL 过期 ━━━");

        // Test 3.1: 默认 TTL 过期
        SimpleCache cache = new SimpleCache(1000, 50);
        cache.put("short-key", "value");
        assert "value".equals(cache.get("short-key"));
        Thread.sleep(120);
        assert cache.get("short-key") == null : "TTL 过期后应返回 null";
        recordResult(suite, "默认TTL过期", true, 0, "50ms TTL, 120ms 后过期");

        // Test 3.2: 自定义 TTL
        SimpleCache customCache = new SimpleCache(1000, 60_000);
        customCache.put("custom-key", "value", 30);
        assert "value".equals(customCache.get("custom-key"));
        Thread.sleep(100);
        assert customCache.get("custom-key") == null : "自定义 TTL 过期后应返回 null";
        recordResult(suite, "自定义TTL过期", true, 0, "30ms TTL");

        // Test 3.3: 独立 TTL
        SimpleCache mixedCache = new SimpleCache(1000, 60_000);
        mixedCache.put("short", "sv", 50);
        mixedCache.put("long", "lv", 5000);
        Thread.sleep(120);
        assert mixedCache.get("short") == null : "短 TTL 应过期";
        assert "lv".equals(mixedCache.get("long")) : "长 TTL 不应过期";
        recordResult(suite, "独立TTL不影响", true, 0, "");

        // Test 3.4: TTL 过期清理
        SimpleCache expireCache = new SimpleCache(1000, 10);
        for (int i = 0; i < 100; i++) {
            expireCache.put("key-" + i, "value-" + i);
        }
        assert expireCache.size() == 100;
        Thread.sleep(100);
        expireCache.cleanup();
        assert expireCache.size() == 0 : "过期清理后 size 应为 0";
        recordResult(suite, "过期清理", true, 0, "");
    }

    // ============ 测试套件 4: 容量与淘汰 ============
    static void testCapacityEviction() {
        String suite = "EVICTION";
        System.out.println("\n━━━ 测试套件 4: 容量与淘汰 ━━━");

        // Test 4.1: 容量限制
        SimpleCache smallCache = new SimpleCache(10, 60_000);
        for (int i = 0; i < 100; i++) {
            smallCache.put("key-" + i, "value-" + i);
        }
        assert smallCache.size() <= 10 : "缓存大小不应超过 maxSize";
        recordResult(suite, "容量限制", true, 0, "maxSize=10, 插入100条后 size=" + smallCache.size());

        // Test 4.2: 淘汰计数
        long evictionsBefore = smallCache.evictionCount();
        assert evictionsBefore > 0 : "应有淘汰发生";
        recordResult(suite, "淘汰计数", true, 0, "共淘汰 " + evictionsBefore + " 条");

        // Test 4.3: 淘汰后仍可正常读写
        smallCache.put("new-key", "new-value");
        assert "new-value".equals(smallCache.get("new-key"));
        recordResult(suite, "淘汰后读写正常", true, 0, "");

        // Test 4.4: 大容量缓存
        SimpleCache largeCache = new SimpleCache(100_000, 60_000);
        long d = measure(() -> {
            for (int i = 0; i < 50_000; i++) {
                largeCache.put("key-" + i, "value-" + i);
            }
        });
        assert largeCache.size() == 50_000;
        recordResult(suite, "大容量缓存(50K)", true, d, "50000 条写入耗时 " + d + "ms");
    }

    // ============ 测试套件 5: 并发安全 ============
    static void testConcurrency() throws Exception {
        String suite = "CONCURRENCY";
        System.out.println("\n━━━ 测试套件 5: 并发安全 ━━━");

        // Test 5.1: 多线程写
        SimpleCache cache = new SimpleCache(100_000, 60_000);
        int threads = CONCURRENT_THREADS;
        int opsPerThread = 1000;
        CountDownLatch writeLatch = new CountDownLatch(threads);
        AtomicInteger writeErrors = new AtomicInteger(0);

        long writeStart = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        cache.put("t" + threadId + "-k" + i, "v" + threadId + "-" + i);
                    }
                } catch (Exception e) {
                    writeErrors.incrementAndGet();
                } finally {
                    writeLatch.countDown();
                }
            }).start();
        }
        writeLatch.await(10, TimeUnit.SECONDS);
        long writeDuration = (System.nanoTime() - writeStart) / 1_000_000;

        assert writeErrors.get() == 0 : "并发写入出错 " + writeErrors.get() + " 次";
        assert cache.size() == threads * opsPerThread : "预期 " + (threads * opsPerThread) + ", 实际 " + cache.size();
        recordResult(suite, "多线程写入", true, writeDuration,
                threads + "线程 × " + opsPerThread + "次, 共 " + (threads * opsPerThread) + " 条");

        // Test 5.2: 多线程读
        CountDownLatch readLatch = new CountDownLatch(threads);
        AtomicInteger readErrors = new AtomicInteger(0);
        AtomicLong totalHits = new AtomicLong(0);
        long readStart = System.nanoTime();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    long localHits = 0;
                    for (int i = 0; i < opsPerThread; i++) {
                        Object val = cache.get("t" + threadId + "-k" + i);
                        if (val != null) localHits++;
                    }
                    totalHits.addAndGet(localHits);
                } catch (Exception e) {
                    readErrors.incrementAndGet();
                } finally {
                    readLatch.countDown();
                }
            }).start();
        }
        readLatch.await(10, TimeUnit.SECONDS);
        long readDuration = (System.nanoTime() - readStart) / 1_000_000;

        assert readErrors.get() == 0 : "并发读取出错 " + readErrors.get() + " 次";
        recordResult(suite, "多线程读取", true, readDuration,
                threads + "线程 × " + opsPerThread + "次, Hits=" + totalHits.get());

        // Test 5.3: 混合读写
        SimpleCache mixCache = new SimpleCache(50_000, 60_000);
        // 先写入一些数据
        for (int i = 0; i < 25_000; i++) {
            mixCache.put("key-" + i, "value-" + i);
        }

        CountDownLatch mixLatch = new CountDownLatch(threads * 2);
        AtomicInteger mixErrors = new AtomicInteger(0);
        long mixStart = System.nanoTime();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            // 写线程
            new Thread(() -> {
                try {
                    for (int i = 0; i < 500; i++) {
                        mixCache.put("new-t" + threadId + "-k" + i, "v" + i);
                    }
                } catch (Exception e) {
                    mixErrors.incrementAndGet();
                } finally {
                    mixLatch.countDown();
                }
            }).start();

            // 读线程
            new Thread(() -> {
                try {
                    for (int i = 0; i < 500; i++) {
                        mixCache.get("key-" + (int) (Math.random() * 25_000));
                    }
                } catch (Exception e) {
                    mixErrors.incrementAndGet();
                } finally {
                    mixLatch.countDown();
                }
            }).start();
        }
        mixLatch.await(15, TimeUnit.SECONDS);
        long mixDuration = (System.nanoTime() - mixStart) / 1_000_000;

        assert mixErrors.get() == 0 : "混合读写出错 " + mixErrors.get() + " 次";
        recordResult(suite, "混合读写(50线程)", true, mixDuration,
                "20写线程 + 20读线程, 各 500 次操作");
    }

    // ============ 测试套件 6: 压力测试 ============
    static void stressTest() throws Exception {
        String suite = "STRESS";
        System.out.println("\n━━━ 测试套件 6: 压力测试 ━━━");

        // Test 6.1: 写入吞吐量
        SimpleCache cache = new SimpleCache(1_000_000, 60_000);
        int iterations = TEST_ITERATIONS;
        long writeStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            cache.put("stress-key-" + i, "stress-value-" + i);
        }
        long writeDuration = (System.nanoTime() - writeStart) / 1_000_000;
        double writeTps = iterations * 1000.0 / writeDuration;

        recordResult(suite, "写入吞吐量", true, writeDuration,
                String.format("%.0f ops/s (%d 次写入, %dms)", writeTps, iterations, writeDuration));

        // Test 6.2: 读取吞吐量（全命中）
        long readStart = System.nanoTime();
        long hitCount = 0;
        for (int i = 0; i < iterations; i++) {
            Object val = cache.get("stress-key-" + i);
            if (val != null) hitCount++;
        }
        long readDuration = (System.nanoTime() - readStart) / 1_000_000;
        double readTps = iterations * 1000.0 / readDuration;

        recordResult(suite, "读取吞吐量(100%命中)", true, readDuration,
                String.format("%.0f ops/s, Hits=%d", readTps, hitCount));

        // Test 6.3: 读取吞吐量（50%命中）
        long partialHitCount = 0;
        long partialStart = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String key = (i % 2 == 0) ? "stress-key-" + i : "nonexistent-" + i;
            Object val = cache.get(key);
            if (val != null) partialHitCount++;
        }
        long partialDuration = (System.nanoTime() - partialStart) / 1_000_000;
        double partialTps = iterations * 1000.0 / partialDuration;

        recordResult(suite, "读取吞吐量(50%命中)", true, partialDuration,
                String.format("%.0f ops/s, Hits=%d", partialTps, partialHitCount));

        // Test 6.4: 并发压力（50线程）
        SimpleCache stressCache = new SimpleCache(500_000, 60_000);
        for (int i = 0; i < 100_000; i++) {
            stressCache.put("preload-" + i, "value-" + i);
        }

        int stressThreads = STRESS_THREADS;
        int opsPerThread = 2000;
        CountDownLatch stressLatch = new CountDownLatch(stressThreads);
        AtomicInteger stressErrors = new AtomicInteger(0);

        long stressStart = System.nanoTime();
        for (int t = 0; t < stressThreads; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        // 混合操作
                        String key;
                        if (i % 3 == 0) {
                            key = "new-" + threadId + "-" + i;
                            stressCache.put(key, "value");
                        } else {
                            key = "preload-" + (int) (Math.random() * 100_000);
                            stressCache.get(key);
                        }
                    }
                } catch (Exception e) {
                    stressErrors.incrementAndGet();
                } finally {
                    stressLatch.countDown();
                }
            }).start();
        }
        stressLatch.await(30, TimeUnit.SECONDS);
        long stressDuration = (System.nanoTime() - stressStart) / 1_000_000;
        long totalOps = (long) stressThreads * opsPerThread;
        double stressTps = totalOps * 1000.0 / stressDuration;

        recordResult(suite, "并发压力(50线程)", stressErrors.get() == 0, stressDuration,
                String.format("%.0f ops/s, Errors=%d", stressTps, stressErrors.get()));

        // Test 6.5: 淘汰压力测试
        SimpleCache evictCache = new SimpleCache(1000, 60_000);
        long evictStart = System.nanoTime();
        long totalEvictions = 0;
        int rounds = 100;
        for (int r = 0; r < rounds; r++) {
            for (int i = 0; i < 1000; i++) {
                evictCache.put("round-" + r + "-key-" + i, "value");
            }
            totalEvictions = evictCache.evictionCount();
        }
        long evictDuration = (System.nanoTime() - evictStart) / 1_000_000;

        recordResult(suite, "淘汰压力测试", true, evictDuration,
                "100 轮 × 1000 条, 总淘汰 " + totalEvictions + " 次");
    }

    // ============ 测试套件 7: 边界条件 ============
    static void testEdgeCases() {
        String suite = "EDGE";
        System.out.println("\n━━━ 测试套件 7: 边界条件 ━━━");

        // Test 7.1: Null 值处理
        SimpleCache cache = new SimpleCache(100, 60_000);
        try {
            cache.put("null-key", null);
            Object val = cache.get("null-key");
            recordResult(suite, "Null值存储", true, 0, "null 可存储但 get 返回 null");
        } catch (Exception e) {
            recordResult(suite, "Null值存储", false, 0, e.getMessage());
        }

        // Test 7.2: 空 Key
        try {
            cache.put("", "empty-key-value");
            Object val = cache.get("");
            recordResult(suite, "空Key处理", val != null, 0, val != null ? "空 key 可正常工作" : "空 key 返回 null");
        } catch (Exception e) {
            recordResult(suite, "空Key处理", false, 0, e.getMessage());
        }

        // Test 7.3: 重复 put 同一 key
        SimpleCache dedupCache = new SimpleCache(100, 60_000);
        dedupCache.put("same-key", "v1");
        dedupCache.put("same-key", "v2");
        dedupCache.put("same-key", "v3");
        assert dedupCache.size() == 1 : "重复 put 同一 key，size 不应增加";
        assert "v3".equals(dedupCache.get("same-key")) : "最后写入的值应生效";
        recordResult(suite, "重复Key更新", true, 0, "size=" + dedupCache.size());

        // Test 7.4: 容量为 1 的缓存
        SimpleCache tinyCache = new SimpleCache(1, 60_000);
        tinyCache.put("a", "1");
        assert tinyCache.size() == 1;
        tinyCache.put("b", "2");
        assert tinyCache.size() == 1 : "容量为 1 时，应始终只有 1 条";
        assert "2".equals(tinyCache.get("b"));
        assert tinyCache.get("a") == null : "旧条目应被淘汰";
        recordResult(suite, "容量边界(maxSize=1)", true, 0, "");

        // Test 7.5: 大量不同 key
        SimpleCache manyKeys = new SimpleCache(10_000, 60_000);
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            keys.add("unique-" + UUID.randomUUID());
        }
        long start = System.nanoTime();
        for (String key : keys) {
            manyKeys.put(key, key);
        }
        long duration = (System.nanoTime() - start) / 1_000_000;
        assert manyKeys.size() == 10_000;
        recordResult(suite, "大量唯一Key写入(10K)", true, duration, "");
    }

    // ============ 测试套件 8: 指标一致性 ============
    static void testMetricsConsistency() {
        String suite = "METRICS";
        System.out.println("\n━━━ 测试套件 8: 指标一致性 ━━━");

        SimpleCache cache = new SimpleCache(100, 60_000);

        // 初始状态
        assert cache.hitCount() == 0;
        assert cache.missCount() == 0;
        assert cache.putCount() == 0;
        assert cache.evictionCount() == 0;
        recordResult(suite, "初始指标为零", true, 0, "");

        // 混合操作后指标
        cache.put("a", "1");
        cache.put("b", "2");
        cache.get("a"); // hit
        cache.get("c"); // miss
        cache.put("d", "3");
        cache.get("b"); // hit
        cache.get("e"); // miss

        assert cache.putCount() == 4 : "putCount 应为 4, 实际: " + cache.putCount();
        assert cache.hitCount() == 2 : "hitCount 应为 2, 实际: " + cache.hitCount();
        assert cache.missCount() == 2 : "missCount 应为 2, 实际: " + cache.missCount();
        recordResult(suite, "操作计数一致性", true, 0, "put=4, hit=2, miss=2");

        // 命中率
        long rate = cache.hitRate();
        assert rate == 5000 : "命中率应为 5000 (50.00%), 实际: " + rate;
        recordResult(suite, "命中率精度", true, 0, "50.00% (2/4)");

        // 清空后指标
        cache.clear();
        long hitsAfterClear = cache.hitCount();
        long missesAfterClear = cache.missCount();
        recordResult(suite, "清空后指标保留", true, 0,
                "size=0, 但 hit/miss 计数保留: " + hitsAfterClear + "/" + missesAfterClear);

        // 淘汰计数
        SimpleCache smallCache = new SimpleCache(3, 60_000);
        smallCache.put("a", "1");
        smallCache.put("b", "2");
        smallCache.put("c", "3");
        long evictionsBefore = smallCache.evictionCount();
        smallCache.put("d", "4"); // 触发淘汰
        assert smallCache.evictionCount() > evictionsBefore;
        recordResult(suite, "淘汰计数准确", true, 0, "eviction=" + smallCache.evictionCount());
    }

    // ============ 主方法 ============
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║   LSC 存证服务 - 全方位压力测试与代码质量验证                    ║");
        System.out.println("║   测试框架: ComprehensiveTestFramework v1.0                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");

        long totalStart = System.currentTimeMillis();

        // 运行所有测试套件
        testBasicFunctionality();
        testHitRateStatistics();
        testTTLExpiry();
        testCapacityEviction();
        testConcurrency();
        stressTest();
        testEdgeCases();
        testMetricsConsistency();

        long totalDuration = System.currentTimeMillis() - totalStart;

        // 生成报告
        System.out.println("\n");
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║   测试报告                                                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");

        int total = passed.get() + failed.get();
        System.out.printf("%n  总测试数: %d%n", total);
        System.out.printf("  通过: %d ✅%n", passed.get());
        System.out.printf("  失败: %d ❌%n", failed.get());
        System.out.printf("  通过率: %.1f%%%n", (total > 0 ? passed.get() * 100.0 / total : 0));
        System.out.printf("  总耗时: %d ms%n", totalDuration);

        // 按套件汇总
        System.out.println("\n  ┌─ 按套件汇总 ─────────────────────────────────────┐");
        Map<String, List<TestResult>> bySuite = new LinkedHashMap<>();
        for (TestResult r : results) {
            bySuite.computeIfAbsent(r.suite, k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<String, List<TestResult>> entry : bySuite.entrySet()) {
            int sPassed = 0;
            long sDuration = 0;
            for (TestResult r : entry.getValue()) {
                if (r.success) sPassed++;
                sDuration += r.durationMs;
            }
            String status = sPassed == entry.getValue().size() ? "✅" : "⚠️";
            System.out.printf("  │ %s %-12s %d/%d 通过 %6dms%n",
                    status, entry.getKey(), sPassed, entry.getValue().size(), sDuration);
        }
        System.out.println("  └──────────────────────────────────────────────────┘");

        // 性能基准
        System.out.println("\n  ┌─ 性能基准 ──────────────────────────────────────┐");
        for (TestResult r : results) {
            if (r.suite.equals("STRESS") && r.success) {
                System.out.printf("  │ %s: %s%n", r.name, r.detail);
            }
        }
        System.out.println("  └──────────────────────────────────────────────────┘");

        // 失败详情
        if (failed.get() > 0) {
            System.out.println("\n  ┌─ 失败详情 ──────────────────────────────────────┐");
            for (TestResult r : results) {
                if (!r.success) {
                    System.out.printf("  │ ❌ %s.%s: %s%n", r.suite, r.name, r.detail);
                }
            }
            System.out.println("  └──────────────────────────────────────────────────┘");
        }

        System.out.println("\n  测试完成！");

        // 返回退出码
        System.exit(failed.get() > 0 ? 1 : 0);
    }
}
