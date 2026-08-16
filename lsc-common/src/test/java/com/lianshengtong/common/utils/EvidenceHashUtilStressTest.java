package com.lianshengtong.common.utils;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EvidenceHashUtil & 通用工具压力测试")
class EvidenceHashUtilStressTest {

    // ==================== serialize ====================

    @Test
    @DisplayName("serialize: null/基本类型/字符串")
    void serialize_primitives() {
        assertEquals("null", EvidenceHashUtil.serialize(null));
        assertEquals("123", EvidenceHashUtil.serialize(123));
        assertEquals("true", EvidenceHashUtil.serialize(true));
        assertEquals("abc", EvidenceHashUtil.serialize("abc"));
    }

    @Test
    @DisplayName("serialize: Map按FastJSON序列化")
    void serialize_map() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("b", 2);
        map.put("a", 1);
        String s = EvidenceHashUtil.serialize(map);
        assertNotNull(s);
        // FastJSON 保持插入顺序
        assertTrue(s.contains("\"b\""));
    }

    @Test
    @DisplayName("serialize: 对象字段按字母排序")
    void serialize_sortedFields() {
        TestDto dto = new TestDto("z", 1);
        String s = EvidenceHashUtil.serialize(dto);
        assertNotNull(s);
        // 字段应按字母序: age 在 name 前
        int ageIdx = s.indexOf("age");
        int nameIdx = s.indexOf("name");
        assertTrue(ageIdx >= 0 && nameIdx >= 0);
        assertTrue(ageIdx < nameIdx, "age 应在 name 之前");
    }

    @Test
    @DisplayName("serialize: 继承的私有字段也被包含")
    void serialize_inheritedFields() {
        ChildDto child = new ChildDto("child", 10, "ext");
        String s = EvidenceHashUtil.serialize(child);
        assertNotNull(s);
        assertTrue(s.contains("extField"));
    }

    @Test
    @DisplayName("serialize: BigDecimal保留2位小数")
    void serialize_bigDecimalTwoScale() {
        TestDto dto = new TestDto("x", 1);
        dto.setAmount(new BigDecimal("123.456"));
        String s = EvidenceHashUtil.serialize(dto);
        assertTrue(s.contains("123.46"), "BigDecimal应保留2位小数");
    }

    @Test
    @DisplayName("serialize: LocalDateTime格式化")
    void serialize_localDateTime() {
        TestDto dto = new TestDto("x", 1);
        dto.setTs(LocalDateTime.of(2026, 8, 13, 10, 30, 45, 123000000));
        String s = EvidenceHashUtil.serialize(dto);
        assertTrue(s.contains("2026-08-13 10:30:45.123"));
    }

    @Test
    @DisplayName("serialize: LocalDate格式化")
    void serialize_localDate() {
        TestDto dto = new TestDto("x", 1);
        dto.setDate(LocalDate.of(2026, 8, 13));
        String s = EvidenceHashUtil.serialize(dto);
        assertTrue(s.contains("2026-08-13"));
    }

    @Test
    @DisplayName("serialize: java.util.Date格式化")
    void serialize_javaUtilDate() {
        TestDto dto = new TestDto("x", 1);
        // 2026-08-13 10:30:45.123 UTC in millis
        dto.setUtilDate(new Date(1786699845123L));
        String s = EvidenceHashUtil.serialize(dto);
        assertNotNull(s);
    }

    // ==================== sha256Hex ====================

    @Test
    @DisplayName("sha256Hex: null对象序列化后哈希一致")
    void sha256Hex_nullStable() {
        String h1 = EvidenceHashUtil.sha256Hex((Object) null);
        String h2 = EvidenceHashUtil.sha256Hex((Object) null);
        assertEquals(h1, h2);
        assertNotNull(h1);
        assertEquals(64, h1.length()); // sha256 hex
    }

    @Test
    @DisplayName("sha256Hex: 相同对象多次哈希结果一致")
    void sha256Hex_sameObjectStable() {
        TestDto dto = new TestDto("n", 20);
        String h1 = EvidenceHashUtil.sha256Hex(dto);
        String h2 = EvidenceHashUtil.sha256Hex(dto);
        assertEquals(h1, h2);
    }

    @Test
    @DisplayName("sha256Hex: 不同对象哈希不同")
    void sha256Hex_differentObjects() {
        TestDto a = new TestDto("a", 1);
        TestDto b = new TestDto("b", 1);
        assertNotEquals(EvidenceHashUtil.sha256Hex(a), EvidenceHashUtil.sha256Hex(b));
    }

    @Test
    @DisplayName("sha256Hex: 字符串重载")
    void sha256Hex_stringOverload() {
        String h1 = EvidenceHashUtil.sha256Hex("hello");
        String h2 = EvidenceHashUtil.sha256Hex("hello");
        assertEquals(h1, h2);
        assertNotEquals(h1, EvidenceHashUtil.sha256Hex("world"));
    }

    // ==================== merkleRoot ====================

    @Test
    @DisplayName("merkleRoot: 空列表返回sha256(空字符串)")
    void merkleRoot_emptyList() {
        String root = EvidenceHashUtil.merkleRoot(Collections.emptyList());
        assertEquals(EvidenceHashUtil.sha256Hex(""), root);
    }

    @Test
    @DisplayName("merkleRoot: 单元素直接返回该哈希")
    void merkleRoot_singleElement() {
        List<String> list = List.of("abc");
        String root = EvidenceHashUtil.merkleRoot(list);
        assertEquals("abc", root);
    }

    @Test
    @DisplayName("merkleRoot: 奇数长度最后一元素自行配对")
    void merkleRoot_oddLength() {
        List<String> list = List.of("a", "b", "c");
        String root = EvidenceHashUtil.merkleRoot(list);
        assertNotNull(root);
        assertEquals(64, root.length());
    }

    @Test
    @DisplayName("merkleRoot: 大量元素稳定")
    void merkleRoot_manyElements() {
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            hashes.add(EvidenceHashUtil.sha256Hex("item-" + i));
        }
        String r1 = EvidenceHashUtil.merkleRoot(hashes);
        String r2 = EvidenceHashUtil.merkleRoot(hashes);
        assertEquals(r1, r2);
    }

    // ==================== 并发压力 ====================

    @Test
    @DisplayName("并发压力: 100线程同时计算sha256Hex - 无冲突")
    void concurrent_sha256Hex_noConflict() throws Exception {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);
        Map<String, String> results = new ConcurrentHashMap<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    String input = "stress-test-" + idx;
                    String hash = EvidenceHashUtil.sha256Hex(input);
                    results.put(input, hash);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(0, errors.get(), "无并发异常");
        assertEquals(threadCount, results.size(), "所有线程都成功");

        // 校验稳定性
        for (Map.Entry<String, String> e : results.entrySet()) {
            assertEquals(EvidenceHashUtil.sha256Hex(e.getKey()), e.getValue());
        }
    }

    @Test
    @DisplayName("并发压力: 50线程同时计算merkleRoot")
    void concurrent_merkleRoot_stable() throws Exception {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);
        List<String> base = new ArrayList<>();
        for (int i = 0; i < 10; i++) base.add(EvidenceHashUtil.sha256Hex("h" + i));

        Map<Integer, String> results = new ConcurrentHashMap<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    results.put(idx, EvidenceHashUtil.merkleRoot(base));
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(0, errors.get());
        // 所有线程结果一致
        assertFalse(results.isEmpty());
        String first = results.get(0);
        for (String v : results.values()) assertEquals(first, v);
    }

    @Test
    @DisplayName("压力测试: 1000次串行哈希性能 - 耗时<1s")
    void stress_1000Hashes_performant() {
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            EvidenceHashUtil.sha256Hex("perf-test-" + i);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 1000, "1000次哈希应在1s内完成, 实际: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("压力测试: 大对象序列化")
    void stress_largeObjectSerialization() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) sb.append("x".repeat(10));
        String large = sb.toString();

        long start = System.nanoTime();
        String hash = EvidenceHashUtil.sha256Hex(large);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(elapsedMs < 3000, "100KB数据哈希应在3s内, 实际: " + elapsedMs + "ms");
    }

    // ==================== 辅助测试 DTO ====================

    public static class BaseDto {
        private String name;
        private Integer age;

        public BaseDto() {}
        public BaseDto(String name, Integer age) {
            this.name = name;
            this.age = age;
        }
        public String getName() { return name; }
        public Integer getAge() { return age; }
        public void setName(String name) { this.name = name; }
        public void setAge(Integer age) { this.age = age; }
    }

    public static class TestDto extends BaseDto {
        private BigDecimal amount;
        private LocalDateTime ts;
        private LocalDate date;
        private Date utilDate;

        public TestDto() {}
        public TestDto(String name, Integer age) {
            super(name, age);
        }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public LocalDateTime getTs() { return ts; }
        public void setTs(LocalDateTime ts) { this.ts = ts; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public Date getUtilDate() { return utilDate; }
        public void setUtilDate(Date utilDate) { this.utilDate = utilDate; }
    }

    public static class ChildDto extends TestDto {
        private String extField;
        public ChildDto() {}
        public ChildDto(String name, Integer age, String ext) {
            super(name, age);
            this.extField = ext;
        }
        public String getExtField() { return extField; }
    }
}
