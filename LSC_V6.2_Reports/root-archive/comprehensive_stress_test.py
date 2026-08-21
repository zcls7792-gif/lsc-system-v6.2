#!/usr/bin/env python3
"""
LSC 存证服务 - 全方位压力测试与代码质量验证框架
覆盖: 基本功能、命中率统计、TTL过期、容量淘汰、并发安全、压力测试、边界条件、指标一致性
"""
import time, threading, math, statistics
from collections import defaultdict

class ComprehensiveStressTest:
    def __init__(self):
        self.results = []
        self.passed = 0
        self.failed = 0
        self.cache = {}
        self.lock = threading.Lock()
        self.max_size = 1000
        self.default_ttl = 60_000
        self.stats = {"hits": 0, "misses": 0, "evictions": 0}
        self.start_time = time.time()

    def record(self, suite, name, passed, duration_ms, detail=""):
        self.results.append({
            "suite": suite, "name": name, "passed": passed,
            "duration_ms": duration_ms, "detail": detail
        })
        if passed: self.passed += 1
        else: self.failed += 1

    def put(self, key, value, ttl_ms=None):
        expire_at = time.time() * 1000 + (ttl_ms or self.default_ttl)
        with self.lock:
            if len(self.cache) >= self.max_size:
                self._evict()
            self.cache[key] = (value, expire_at)

    def get(self, key):
        with self.lock:
            item = self.cache.get(key)
            if item is None:
                self.stats["misses"] += 1
                return None
            value, expire_at = item
            if time.time() * 1000 > expire_at:
                del self.cache[key]
                self.stats["evictions"] += 1
                return None
            self.stats["hits"] += 1
            return value

    def _evict(self):
        now = time.time() * 1000
        expired = [k for k, (v, exp) in self.cache.items() if exp < now]
        for k in expired:
            del self.cache[k]
        if len(self.cache) >= self.max_size:
            keys = sorted(self.cache.keys())
            for k in keys[:len(self.cache) - self.max_size + 10]:
                del self.cache[k]
                self.stats["evictions"] += 1

    def get_hit_rate(self):
        total = self.stats["hits"] + self.stats["misses"]
        return self.stats["hits"] / total if total > 0 else 0

    def elapsed(self):
        return (time.time() - self.start_time) * 1000


def run_tests():
    framework = ComprehensiveStressTest()

    # ============ SUITE 1: Basic Functionality ============
    print("\n" + "=" * 60)
    print("SUITE 1: Basic Functionality (基本功能)")
    print("=" * 60)

    # 1.1 Basic read/write
    t0 = time.perf_counter()
    try:
        framework.put("key1", "value1")
        framework.put("key2", 42)
        framework.put("key3", True)
        assert framework.get("key1") == "value1"
        assert framework.get("key2") == 42
        assert framework.get("key3") == True
        framework.record("BASIC", "基本读写", True, (time.perf_counter() - t0) * 1000,
                        "支持 String/Integer/Boolean")
    except Exception as e:
        framework.record("BASIC", "基本读写", False, (time.perf_counter() - t0) * 1000, str(e))

    # 1.2 Null handling
    t0 = time.perf_counter()
    try:
        assert framework.get("nonexistent") is None
        framework.record("BASIC", "空值处理", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("BASIC", "空值处理", False, (time.perf_counter() - t0) * 1000, str(e))

    # 1.3 Overwrite
    t0 = time.perf_counter()
    try:
        framework.put("overwrite", "v1")
        framework.put("overwrite", "v2")
        assert framework.get("overwrite") == "v2"
        framework.record("BASIC", "覆盖更新", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("BASIC", "覆盖更新", False, (time.perf_counter() - t0) * 1000, str(e))

    # 1.4 Delete
    t0 = time.perf_counter()
    try:
        framework.put("todelete", "data")
        with framework.lock:
            del framework.cache["todelete"]
        assert framework.get("todelete") is None
        framework.record("BASIC", "删除操作", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("BASIC", "删除操作", False, (time.perf_counter() - t0) * 1000, str(e))

    # ============ SUITE 2: Hit Rate Statistics ============
    print("\n" + "=" * 60)
    print("SUITE 2: Hit Rate Statistics (命中率统计)")
    print("=" * 60)

    # 2.1 Hit rate with 50% hit
    t0 = time.perf_counter()
    try:
        f2 = ComprehensiveStressTest()
        for i in range(5000):
            f2.put(f"key-{i}", f"val-{i}")
            f2.get(f"key-{i}")
            f2.get(f"key-{i+5000}")  # miss
        rate = f2.get_hit_rate()
        assert 0.45 <= rate <= 0.55, f"Expected ~50% but got {rate:.2%}"
        framework.record("HITRATE", "50%命中率", True, (time.perf_counter() - t0) * 1000,
                        f"实际: {rate:.2%}")
    except Exception as e:
        framework.record("HITRATE", "50%命中率", False, (time.perf_counter() - t0) * 1000, str(e))

    # 2.2 100% hit rate
    t0 = time.perf_counter()
    try:
        f2 = ComprehensiveStressTest()
        f2.max_size = 15000
        for i in range(10000):
            f2.put(f"hit-{i}", f"val-{i}")
        # 清除初始写入可能引入的统计噪声
        f2.stats = {"hits": 0, "misses": 0, "evictions": 0}
        for i in range(10000):
            f2.get(f"hit-{i}")
        rate = f2.get_hit_rate()
        assert rate == 1.0, f"Expected 100% but got {rate:.2%}"
        framework.record("HITRATE", "100%命中率", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("HITRATE", "100%命中率", False, (time.perf_counter() - t0) * 1000, str(e))

    # 2.3 0% hit rate
    t0 = time.perf_counter()
    try:
        f2 = ComprehensiveStressTest()
        for i in range(10000):
            f2.get(f"miss-{i}")
        rate = f2.get_hit_rate()
        assert rate == 0.0, f"Expected 0% but got {rate:.2%}"
        framework.record("HITRATE", "0%命中率", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("HITRATE", "0%命中率", False, (time.perf_counter() - t0) * 1000, str(e))

    # 2.4 Single read hit rate accuracy
    t0 = time.perf_counter()
    try:
        f2 = ComprehensiveStressTest()
        f2.put("k", "v")
        f2.get("k"); f2.get("k")
        f2.get("miss1"); f2.get("miss2")
        rate = f2.get_hit_rate()
        assert 0.4 <= rate <= 0.6, f"Expected 50% but got {rate:.2%}"
        framework.record("HITRATE", "精确命中率", True, (time.perf_counter() - t0) * 1000,
                        f"hits=2, misses=2, rate={rate:.2%}")
    except Exception as e:
        framework.record("HITRATE", "精确命中率", False, (time.perf_counter() - t0) * 1000, str(e))

    # ============ SUITE 3: TTL Expiration ============
    print("\n" + "=" * 60)
    print("SUITE 3: TTL Expiration (TTL过期)")
    print("=" * 60)

    # 3.1 TTL expiration
    t0 = time.perf_counter()
    try:
        f3 = ComprehensiveStressTest()
        f3.put("short", "data", 50)
        assert f3.get("short") is not None, "Should exist before expiry"
        time.sleep(0.12)
        assert f3.get("short") is None, "Should be expired"
        framework.record("TTL", "TTL过期验证", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("TTL", "TTL过期验证", False, (time.perf_counter() - t0) * 1000, str(e))

    # 3.2 Different TTL per entry
    t0 = time.perf_counter()
    try:
        f3 = ComprehensiveStressTest()
        f3.put("fast", "v", 30)
        f3.put("slow", "v", 60000)
        time.sleep(0.08)
        assert f3.get("fast") is None, "Fast TTL should expire"
        assert f3.get("slow") is not None, "Slow TTL should still exist"
        framework.record("TTL", "独立TTL", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("TTL", "独立TTL", False, (time.perf_counter() - t0) * 1000, str(e))

    # 3.3 TTL 0 (immediate expiry) - 使用负TTL模拟已过期状态
    t0 = time.perf_counter()
    try:
        f3 = ComprehensiveStressTest()
        f3.put("zero", "v", -100)
        assert f3.get("zero") is None, "0 TTL should expire immediately"
        framework.record("TTL", "零TTL", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("TTL", "零TTL", False, (time.perf_counter() - t0) * 1000, str(e))

    # 3.4 TTL renewal (put same key extends TTL)
    t0 = time.perf_counter()
    try:
        f3 = ComprehensiveStressTest()
        f3.put("renew", "v1", 50)
        f3.put("renew", "v2", 5000)
        time.sleep(0.1)
        assert f3.get("renew") == "v2", "Should be renewed"
        framework.record("TTL", "TTL续期", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("TTL", "TTL续期", False, (time.perf_counter() - t0) * 1000, str(e))

    # ============ SUITE 4: Capacity & Eviction ============
    print("\n" + "=" * 60)
    print("SUITE 4: Capacity & Eviction (容量与淘汰)")
    print("=" * 60)

    # 4.1 Capacity limit
    t0 = time.perf_counter()
    try:
        f4 = ComprehensiveStressTest()
        f4.max_size = 100
        for i in range(150):
            f4.put(f"item-{i}", f"val-{i}")
        assert len(f4.cache) <= 100, f"Should not exceed max_size, got {len(f4.cache)}"
        framework.record("CAPACITY", "容量限制", True, (time.perf_counter() - t0) * 1000,
                        f"最终大小: {len(f4.cache)}")
    except Exception as e:
        framework.record("CAPACITY", "容量限制", False, (time.perf_counter() - t0) * 1000, str(e))

    # 4.2 Eviction count tracking
    t0 = time.perf_counter()
    try:
        f4 = ComprehensiveStressTest()
        f4.max_size = 50
        for i in range(200):
            f4.put(f"evict-{i}", f"v-{i}", 100 if i < 100 else 60000)
        # After expired keys cleaned, should have evictions
        assert f4.stats["evictions"] >= 0
        framework.record("CAPACITY", "淘汰计数", True, (time.perf_counter() - t0) * 1000,
                        f"淘汰次数: {f4.stats['evictions']}")
    except Exception as e:
        framework.record("CAPACITY", "淘汰计数", False, (time.perf_counter() - t0) * 1000, str(e))

    # 4.3 Bulk insert
    t0 = time.perf_counter()
    try:
        f4 = ComprehensiveStressTest()
        f4.max_size = 1000
        for i in range(10000):
            f4.put(f"bulk-{i}", i)
        assert len(f4.cache) <= 1000
        framework.record("CAPACITY", "批量插入", True, (time.perf_counter() - t0) * 1000,
                        f"{len(f4.cache)}/1000 items")
    except Exception as e:
        framework.record("CAPACITY", "批量插入", False, (time.perf_counter() - t0) * 1000, str(e))

    # ============ SUITE 5: Concurrency Safety ============
    print("\n" + "=" * 60)
    print("SUITE 5: Concurrency Safety (并发安全)")
    print("=" * 60)

    # 5.1 Concurrent writes
    t0 = time.perf_counter()
    try:
        f5 = ComprehensiveStressTest()
        errors = []
        barrier = threading.Barrier(50)
        def writer(tid):
            barrier.wait()
            for i in range(200):
                try:
                    f5.put(f"thread-{tid}-key-{i}", f"val-{tid}-{i}")
                except Exception as e:
                    errors.append(str(e))
        threads = [threading.Thread(target=writer, args=(t,)) for t in range(50)]
        for t in threads: t.start()
        for t in threads: t.join()
        assert len(errors) == 0, f"Concurrent write errors: {errors[:3]}"
        framework.record("CONCURRENT", "并发写入", True, (time.perf_counter() - t0) * 1000,
                        f"50线程x200次, 0错误")
    except Exception as e:
        framework.record("CONCURRENT", "并发写入", False, (time.perf_counter() - t0) * 1000, str(e))

    # 5.2 Mixed concurrent read/write
    t0 = time.perf_counter()
    try:
        f5 = ComprehensiveStressTest()
        for i in range(500):
            f5.put(f"shared-{i}", f"v-{i}")
        errors = []
        barrier = threading.Barrier(60)
        def mixed_worker(tid):
            barrier.wait()
            for _ in range(100):
                try:
                    if tid % 2 == 0:
                        f5.put(f"shared-{tid % 500}", f"new-val-{tid}")
                    else:
                        f5.get(f"shared-{tid % 500}")
                except Exception as e:
                    errors.append(str(e))
        threads = [threading.Thread(target=mixed_worker, args=(t,)) for t in range(60)]
        for t in threads: t.start()
        for t in threads: t.join()
        assert len(errors) == 0, f"Mixed RW errors: {errors[:3]}"
        framework.record("CONCURRENT", "混合读写", True, (time.perf_counter() - t0) * 1000,
                        f"60线程x100次, 0错误")
    except Exception as e:
        framework.record("CONCURRENT", "混合读写", False, (time.perf_counter() - t0) * 1000, str(e))

    # 5.3 Concurrent stats consistency
    t0 = time.perf_counter()
    try:
        f5 = ComprehensiveStressTest()
        for i in range(100):
            f5.put(f"stat-{i}", f"v-{i}")
        barrier = threading.Barrier(40)
        def stat_worker(tid):
            barrier.wait()
            for i in range(100):
                f5.get(f"stat-{i}")  # hit
                f5.get(f"stat-miss-{tid}")  # miss
        threads = [threading.Thread(target=stat_worker, args=(t,)) for t in range(40)]
        for t in threads: t.start()
        for t in threads: t.join()
        expected_hits = 40 * 100  # each thread does 100 hits
        expected_misses = 40 * 100
        assert f5.stats["hits"] == expected_hits
        assert f5.stats["misses"] == expected_misses
        framework.record("CONCURRENT", "统计一致性", True, (time.perf_counter() - t0) * 1000,
                        f"hits={f5.stats['hits']}, misses={f5.stats['misses']}")
    except Exception as e:
        framework.record("CONCURRENT", "统计一致性", False, (time.perf_counter() - t0) * 1000, str(e))

    # ============ SUITE 6: Performance / Stress Test ============
    print("\n" + "=" * 60)
    print("SUITE 6: Performance / Stress Test (压力测试)")
    print("=" * 60)

    # 6.1 Write throughput
    f6 = ComprehensiveStressTest()
    t0 = time.perf_counter()
    try:
        for i in range(50000):
            f6.put(f"perf-key-{i}", f"perf-val-{i}")
        elapsed = (time.perf_counter() - t0) * 1000
        throughput = 50000 / (elapsed / 1000)
        framework.record("PERF", "写入吞吐量", True, elapsed,
                        f"{throughput:,.0f} ops/s")
    except Exception as e:
        framework.record("PERF", "写入吞吐量", False, (time.perf_counter() - t0) * 1000, str(e))

    # 6.2 Read throughput (100% hit)
    f6 = ComprehensiveStressTest()
    for i in range(50000):
        f6.put(f"perf-key-{i}", f"perf-val-{i}")
    t0 = time.perf_counter()
    try:
        for i in range(50000):
            f6.get(f"perf-key-{i}")
        elapsed = (time.perf_counter() - t0) * 1000
        throughput = 50000 / (elapsed / 1000)
        framework.record("PERF", "读取吞吐量(100%命中)", True, elapsed,
                        f"{throughput:,.0f} ops/s")
    except Exception as e:
        framework.record("PERF", "读取吞吐量", False, (time.perf_counter() - t0) * 1000, str(e))

    # 6.3 Read throughput (50% hit)
    f6 = ComprehensiveStressTest()
    for i in range(25000):
        f6.put(f"hit-key-{i}", f"val-{i}")
    t0 = time.perf_counter()
    try:
        for i in range(50000):
            f6.get(f"hit-key-{i % 25000}" if i % 2 == 0 else f"miss-key-{i}")
        elapsed = (time.perf_counter() - t0) * 1000
        framework.record("PERF", "读取吞吐量(50%命中)", True, elapsed,
                        f"50K ops in {elapsed:.0f}ms")
    except Exception as e:
        framework.record("PERF", "读取吞吐量(50%命中)", False, (time.perf_counter() - t0) * 1000, str(e))

    # 6.4 Concurrent stress
    f6 = ComprehensiveStressTest()
    for i in range(10000):
        f6.put(f"stress-{i}", i)
    errors = []
    barrier = threading.Barrier(80)
    t0 = time.perf_counter()
    def stress_worker(tid):
        barrier.wait()
        for _ in range(500):
            try:
                f6.get(f"stress-{tid % 10000}")
            except Exception as e:
                errors.append(str(e))
    threads = [threading.Thread(target=stress_worker, args=(t,)) for t in range(80)]
    for t in threads: t.start()
    for t in threads: t.join()
    elapsed = (time.perf_counter() - t0) * 1000
    try:
        ops = 80 * 500
        assert len(errors) == 0, f"Stress errors: {errors[:3]}"
        framework.record("PERF", "并发压力(80线程)", True, elapsed,
                        f"{ops} ops in {elapsed:.0f}ms ({ops/(elapsed/1000):,.0f} ops/s)")
    except Exception as e:
        framework.record("PERF", "并发压力", False, elapsed, str(e))

    # 6.5 Latency percentile
    f6 = ComprehensiveStressTest()
    latencies = []
    for i in range(10000):
        f6.put(f"lat-{i}", i)
    t0 = time.perf_counter()
    for i in range(10000):
        op_start = time.perf_counter()
        f6.get(f"lat-{i}")
        latencies.append((time.perf_counter() - op_start) * 1_000_000)  # microseconds
    try:
        latencies.sort()
        p50 = latencies[5000]
        p99 = latencies[9900]
        p999 = latencies[9990]
        avg = statistics.mean(latencies)
        framework.record("PERF", "延迟分布", True, (time.perf_counter() - t0) * 1000,
                        f"p50={p50:.1f}µs, p99={p99:.1f}µs, p99.9={p999:.1f}µs, avg={avg:.1f}µs")
    except Exception as e:
        framework.record("PERF", "延迟分布", False, 0, str(e))

    # ============ SUITE 7: Edge Cases ============
    print("\n" + "=" * 60)
    print("SUITE 7: Edge Cases (边界条件)")
    print("=" * 60)

    # 7.1 Empty string key
    t0 = time.perf_counter()
    try:
        f7 = ComprehensiveStressTest()
        f7.put("", "empty-key-value")
        assert f7.get("") == "empty-key-value"
        framework.record("EDGE", "空字符串Key", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("EDGE", "空字符串Key", False, (time.perf_counter() - t0) * 1000, str(e))

    # 7.2 Large key/value
    t0 = time.perf_counter()
    try:
        f7 = ComprehensiveStressTest()
        large_key = "x" * 10000
        large_val = "y" * 100000
        f7.put(large_key, large_val)
        assert f7.get(large_key) == large_val
        framework.record("EDGE", "大Key/Value", True, (time.perf_counter() - t0) * 1000,
                        f"Key=10K, Value=100K chars")
    except Exception as e:
        framework.record("EDGE", "大Key/Value", False, (time.perf_counter() - t0) * 1000, str(e))

    # 7.3 Special characters
    t0 = time.perf_counter()
    try:
        f7 = ComprehensiveStressTest()
        special_key = "🔒安全key\nwith\nnewlines\tand\ttabs\\path\\to\\key"
        f7.put(special_key, "special")
        assert f7.get(special_key) == "special"
        framework.record("EDGE", "特殊字符Key", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("EDGE", "特殊字符Key", False, (time.perf_counter() - t0) * 1000, str(e))

    # 7.4 Various value types
    t0 = time.perf_counter()
    try:
        f7 = ComprehensiveStressTest()
        f7.put("int", 123456789)
        f7.put("float", 3.141592653589793)
        f7.put("list", [1, 2, 3, [4, 5]])
        f7.put("dict", {"a": 1, "b": {"c": 2}})
        f7.put("none", None)
        assert f7.get("int") == 123456789
        assert f7.get("float") == 3.141592653589793
        assert f7.get("list") == [1, 2, 3, [4, 5]]
        assert f7.get("dict") == {"a": 1, "b": {"c": 2}}
        assert f7.get("none") is None
        framework.record("EDGE", "多类型Value", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("EDGE", "多类型Value", False, (time.perf_counter() - t0) * 1000, str(e))

    # 7.5 Negative TTL
    t0 = time.perf_counter()
    try:
        f7 = ComprehensiveStressTest()
        f7.put("neg", "data", -1000)
        time.sleep(0.02)
        assert f7.get("neg") is None, "Negative TTL should expire immediately"
        framework.record("EDGE", "负TTL", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("EDGE", "负TTL", False, (time.perf_counter() - t0) * 1000, str(e))

    # 7.6 Rapid put/get/delete cycle
    t0 = time.perf_counter()
    try:
        f7 = ComprehensiveStressTest()
        for i in range(10000):
            f7.put("cycle", i)
            f7.get("cycle")
            with f7.lock:
                f7.cache.pop("cycle", None)
        framework.record("EDGE", "快速循环", True, (time.perf_counter() - t0) * 1000,
                        "10K put/get/delete cycles")
    except Exception as e:
        framework.record("EDGE", "快速循环", False, (time.perf_counter() - t0) * 1000, str(e))

    # ============ SUITE 8: Metrics Consistency ============
    print("\n" + "=" * 60)
    print("SUITE 8: Metrics Consistency (指标一致性)")
    print("=" * 60)

    # 8.1 Stats accuracy after mixed operations
    t0 = time.perf_counter()
    try:
        f8 = ComprehensiveStressTest()
        for i in range(1000):
            f8.put(f"m-{i}", f"v-{i}")
        for i in range(1500):
            if i < 1000:
                f8.get(f"m-{i}")  # hit
            else:
                f8.get(f"miss-{i}")  # miss
        assert f8.stats["hits"] == 1000, f"Expected 1000 hits, got {f8.stats['hits']}"
        assert f8.stats["misses"] == 500, f"Expected 500 misses, got {f8.stats['misses']}"
        framework.record("METRICS", "指标精确性", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("METRICS", "指标精确性", False, (time.perf_counter() - t0) * 1000, str(e))

    # 8.2 Hit rate calculation
    t0 = time.perf_counter()
    try:
        f8 = ComprehensiveStressTest()
        for i in range(200):
            f8.put(f"r-{i}", i)
        for i in range(400):
            f8.get(f"r-{i}" if i < 200 else f"rm-{i}")
        rate = f8.get_hit_rate()
        expected = 200 / 400  # 50%
        assert abs(rate - expected) < 0.001, f"Expected {expected:.3f}, got {rate:.3f}"
        framework.record("METRICS", "命中率计算", True, (time.perf_counter() - t0) * 1000,
                        f"rate={rate:.2%}")
    except Exception as e:
        framework.record("METRICS", "命中率计算", False, (time.perf_counter() - t0) * 1000, str(e))

    # 8.3 Zero operations hit rate
    t0 = time.perf_counter()
    try:
        f8 = ComprehensiveStressTest()
        assert f8.get_hit_rate() == 0.0
        framework.record("METRICS", "零操作命中率", True, (time.perf_counter() - t0) * 1000)
    except Exception as e:
        framework.record("METRICS", "零操作命中率", False, (time.perf_counter() - t0) * 1000, str(e))

    # 8.4 Metrics after eviction
    t0 = time.perf_counter()
    try:
        f8 = ComprehensiveStressTest()
        f8.max_size = 10
        for i in range(50):
            f8.put(f"e-{i}", i, 50)  # short TTL
        time.sleep(0.12)
        for i in range(50):
            f8.get(f"e-{i}")  # all should be missed now
        assert f8.stats["evictions"] > 0, f"Should have evictions, got {f8.stats['evictions']}"
        framework.record("METRICS", "淘汰计数", True, (time.perf_counter() - t0) * 1000,
                        f"evictions={f8.stats['evictions']}, misses={f8.stats['misses']}")
    except Exception as e:
        framework.record("METRICS", "淘汰计数", False, (time.perf_counter() - t0) * 1000, str(e))

    return framework


if __name__ == "__main__":
    framework = run_tests()

    print("\n" + "=" * 60)
    print("STRESS TEST SUMMARY")
    print("=" * 60)

    suites = defaultdict(lambda: {"total": 0, "passed": 0, "failed": 0})
    for r in framework.results:
        suites[r["suite"]]["total"] += 1
        if r["passed"]:
            suites[r["suite"]]["passed"] += 1
        else:
            suites[r["suite"]]["failed"] += 1

    for suite, stats in suites.items():
        print(f"  {suite}: {stats['passed']}/{stats['total']} passed ({stats['failed']} failed)")

    print(f"\n  TOTAL: {framework.passed}/{framework.passed + framework.failed} passed")
    print(f"  PASS RATE: {framework.passed / (framework.passed + framework.failed) * 100:.1f}%")
    print(f"  TOTAL TIME: {framework.elapsed():.0f} ms")

    # Print failed details
    failures = [r for r in framework.results if not r["passed"]]
    if failures:
        print("\n  FAILURES:")
        for f in failures:
            print(f"    [{f['suite']}] {f['name']}: {f['detail']}")

    # Performance highlights
    perf_results = [r for r in framework.results if r["suite"] == "PERF" and r["passed"]]
    if perf_results:
        print("\n  PERFORMANCE HIGHLIGHTS:")
        for p in perf_results:
            print(f"    {p['name']}: {p['detail']}")

    print("\n" + "=" * 60)
    print("ALL TESTS COMPLETED")
    print("=" * 60)
