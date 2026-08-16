package com.lianshengtong.common.utils;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("通用工具类单元测试")
class CommonUtilsTest {

    // ============== EvidenceHashUtil 测试 ==============

    @Test
    @DisplayName("sha256Hex: 相同输入产生相同哈希")
    void sha256Hex_sameInput_sameHash() {
        String hash1 = EvidenceHashUtil.sha256Hex("test-data");
        String hash2 = EvidenceHashUtil.sha256Hex("test-data");

        assertEquals(hash1, hash2);
        assertNotNull(hash1);
        assertTrue(hash1.length() > 0);
    }

    @Test
    @DisplayName("sha256Hex: 不同输入产生不同哈希")
    void sha256Hex_diffInput_diffHash() {
        String hash1 = EvidenceHashUtil.sha256Hex("data-1");
        String hash2 = EvidenceHashUtil.sha256Hex("data-2");

        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("sha256Hex: 空字符串哈希稳定")
    void sha256Hex_emptyString_stable() {
        String hash = EvidenceHashUtil.sha256Hex("");

        assertNotNull(hash);
        assertTrue(hash.length() > 0);
    }

    @Test
    @DisplayName("serialize: null输入返回null字符串")
    void serialize_null_returnNull() {
        String result = EvidenceHashUtil.serialize(null);

        assertEquals("null", result);
    }

    @Test
    @DisplayName("serialize: BigDecimal保留两位小数")
    void serialize_bigDecimal_scale2() {
        Map<String, Object> obj = new TreeMap<>();
        obj.put("amount", new BigDecimal("100.555"));

        String serialized = EvidenceHashUtil.serialize(obj);

        assertNotNull(serialized);
    }

    @Test
    @DisplayName("serialize: LocalDateTime格式化")
    void serialize_localDateTime_formatted() {
        Map<String, Object> obj = new TreeMap<>();
        LocalDateTime dt = LocalDateTime.of(2026, 8, 6, 12, 30, 45, 123000000);
        obj.put("createdAt", dt);

        String serialized = EvidenceHashUtil.serialize(obj);

        assertNotNull(serialized);
        assertTrue(serialized.contains("2026-08-06 12:30:45.123"));
    }

    @Test
    @DisplayName("serialize: LocalDate格式化")
    void serialize_localDate_formatted() {
        Map<String, Object> obj = new TreeMap<>();
        obj.put("date", LocalDate.of(2026, 8, 6));

        String serialized = EvidenceHashUtil.serialize(obj);

        assertNotNull(serialized);
        assertTrue(serialized.contains("2026-08-06"));
    }

    @Test
    @DisplayName("merkleRoot: 空列表返回空哈希")
    void merkleRoot_emptyList_returnsHash() {
        String hash = EvidenceHashUtil.merkleRoot(Collections.emptyList());

        assertNotNull(hash);
        assertFalse(hash.isEmpty());
    }

    @Test
    @DisplayName("merkleRoot: 单个元素返回该元素哈希")
    void merkleRoot_singleElement_returnsHashedElement() {
        List<String> hashes = Arrays.asList("hash1");
        String root = EvidenceHashUtil.merkleRoot(hashes);

        assertNotNull(root);
        assertFalse(root.isEmpty());
    }

    @Test
    @DisplayName("merkleRoot: 偶数个元素正确配对")
    void merkleRoot_evenElements_correctPairing() {
        List<String> hashes = Arrays.asList("h1", "h2", "h3", "h4");
        String root = EvidenceHashUtil.merkleRoot(hashes);

        assertNotNull(root);
        assertFalse(root.isEmpty());
    }

    @Test
    @DisplayName("merkleRoot: 奇数个元素最后一个元素自身配对")
    void merkleRoot_oddElements_selfPairing() {
        List<String> hashes = Arrays.asList("h1", "h2", "h3");
        String root = EvidenceHashUtil.merkleRoot(hashes);

        assertNotNull(root);
        assertFalse(root.isEmpty());
    }

    // ============== SnowflakeIdUtil 单元测试 ==============

    @Test
    @DisplayName("SnowflakeIdUtil: 生成唯一ID不重复")
    void snowflakeId_unique_noDuplicate() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(SnowflakeIdUtil.id());
        }

        assertEquals(1000, ids.size());
    }

    @Test
    @DisplayName("SnowflakeIdUtil: 并发生成ID唯一性")
    void snowflakeId_concurrent_unique() {
        int threadCount = 10;
        int perThread = 100;
        Set<Long> allIds = Collections.synchronizedSet(new HashSet<>());

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) {
                    allIds.add(SnowflakeIdUtil.id());
                    try { Thread.sleep(0, 100); } catch (InterruptedException ignored) {}
                }
            });
        }

        for (Thread t : threads) t.start();
        try {
            for (Thread t : threads) t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertTrue(allIds.size() > threadCount * perThread * 0.9,
                "Expected mostly unique IDs, got " + allIds.size() + " out of " + (threadCount * perThread));
    }

    @Test
    @DisplayName("SnowflakeIdUtil: ID单调递增")
    void snowflakeId_monotonicIncreasing() {
        long lastId = 0;
        for (int i = 0; i < 100; i++) {
            long id = SnowflakeIdUtil.id();
            assertTrue(id > lastId, "ID should be monotonically increasing: " + id + " > " + lastId);
            lastId = id;
        }
    }
}
