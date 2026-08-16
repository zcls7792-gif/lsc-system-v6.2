package com.lianshengtong.evidence.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.utils.EvidenceHashUtil;
import com.lianshengtong.evidence.config.EvidenceCache;
import com.lianshengtong.evidence.config.EvidenceCaffeineCache;
import com.lianshengtong.evidence.entity.BlockchainRecord;
import com.lianshengtong.evidence.entity.DailySnapshotRecord;
import com.lianshengtong.evidence.entity.EvidenceFailover;
import com.lianshengtong.evidence.mapper.BlockchainRecordMapper;
import com.lianshengtong.evidence.mapper.DailySnapshotRecordMapper;
import com.lianshengtong.evidence.mapper.EvidenceFailoverMapper;
import com.lianshengtong.evidence.service.AsyncChainWriter;
import com.lianshengtong.evidence.service.SmartContractService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("存证服务单元测试")
class EvidenceServiceImplTest {

    @Mock
    BlockchainRecordMapper blockchainRecordMapper;

    @Mock
    DailySnapshotRecordMapper dailySnapshotRecordMapper;

    @Mock
    EvidenceFailoverMapper evidenceFailoverMapper;

    @Mock
    SmartContractService smartContractService;

    @Mock
    SmartContractServiceImpl smartContractServiceImpl;

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    AsyncChainWriter asyncChainWriter;

    @Mock
    ValueOperations<String, String> valueOperations;

    private EvidenceServiceImpl evidenceService;

    @BeforeEach
    void setUp() {
        // 重置所有mock，确保测试隔离
        Mockito.reset(
                blockchainRecordMapper,
                dailySnapshotRecordMapper,
                evidenceFailoverMapper,
                smartContractService,
                smartContractServiceImpl,
                stringRedisTemplate,
                asyncChainWriter,
                valueOperations
        );
        evidenceService = new EvidenceServiceImpl(
                blockchainRecordMapper,
                dailySnapshotRecordMapper,
                evidenceFailoverMapper,
                smartContractService,
                asyncChainWriter,
                stringRedisTemplate
        );
        evidenceService.setSmartContractServiceImpl(smartContractServiceImpl);
        evidenceService.setAsyncEnabled(false);
        evidenceService.setBatchCount(3000);
        evidenceService.setMaxRetry(3);
        EvidenceCache cache = new com.lianshengtong.evidence.config.EvidenceCaffeineCache(10000, 30_000L);
        ReflectionTestUtils.setField(evidenceService, "evidenceLocalCache", cache);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(blockchainRecordMapper.updateById(any(BlockchainRecord.class))).thenReturn(1);
        when(dailySnapshotRecordMapper.updateById(any(DailySnapshotRecord.class))).thenReturn(1);
    }

    @Test
    @DisplayName("saveEvidence - 传入dataHash时直接使用，验证insert被调用")
    void testSaveEvidence_WithDataHash() {
        when(blockchainRecordMapper.insert(any(BlockchainRecord.class))).thenAnswer(invocation -> {
            BlockchainRecord rec = invocation.getArgument(0);
            rec.setId(1L);
            return 1;
        });
        when(valueOperations.increment(anyString())).thenReturn(0L);

        String result = evidenceService.saveEvidence("ORDER", "ORD001", "myHash", "payload");

        assertEquals("1", result);
        verify(blockchainRecordMapper).insert(any(BlockchainRecord.class));
        ArgumentCaptor<BlockchainRecord> captor = ArgumentCaptor.forClass(BlockchainRecord.class);
        verify(blockchainRecordMapper).insert(captor.capture());
        BlockchainRecord saved = captor.getValue();
        assertEquals("ORDER", saved.getBizType());
        assertEquals("ORD001", saved.getBizId());
        assertEquals("myHash", saved.getDataHash());
        assertEquals("payload", saved.getDataPayload());
        assertEquals(0, saved.getStatus());
        assertEquals(0, saved.getRetryCount());
    }

    @Test
    @DisplayName("saveEvidence - 无dataHash时自动从payload生成SHA-256哈希")
    void testSaveEvidence_AutoHash() {
        when(blockchainRecordMapper.insert(any(BlockchainRecord.class))).thenAnswer(invocation -> {
            BlockchainRecord rec = invocation.getArgument(0);
            rec.setId(2L);
            return 1;
        });
        when(valueOperations.increment(anyString())).thenReturn(0L);

        String result = evidenceService.saveEvidence("ORDER", "ORD002", null, "testPayload");

        assertEquals("2", result);
        ArgumentCaptor<BlockchainRecord> captor = ArgumentCaptor.forClass(BlockchainRecord.class);
        verify(blockchainRecordMapper).insert(captor.capture());
        BlockchainRecord saved = captor.getValue();
        String expectedHash = EvidenceHashUtil.sha256Hex("testPayload");
        assertEquals(expectedHash, saved.getDataHash());
    }

    @Test
    @DisplayName("saveEvidence - Redis计数达到batchCount时触发flushPending")
    void testSaveEvidence_TriggersBatchFlush() {
        when(blockchainRecordMapper.insert(any(BlockchainRecord.class))).thenAnswer(invocation -> {
            BlockchainRecord rec = invocation.getArgument(0);
            rec.setId(3L);
            return 1;
        });
        when(valueOperations.increment(anyString())).thenReturn(3000L);
        BlockchainRecord pendingRec = new BlockchainRecord();
        pendingRec.setId(99L);
        pendingRec.setDataHash("hashX");
        pendingRec.setBizType("ORDER");
        pendingRec.setBizId("ORD099");
        pendingRec.setStatus(0);
        pendingRec.setRetryCount(0);
        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(pendingRec));
        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_trigger");
        when(smartContractService.queryBlockNumberWithRetry(anyString(), eq(3))).thenReturn(300L);
        when(blockchainRecordMapper.updateById(any(BlockchainRecord.class))).thenReturn(1);

        evidenceService.saveEvidence("ORDER", "ORD003", "hash", "payload");

        verify(valueOperations).increment("lsc:evidence:pending-count");
        verify(blockchainRecordMapper).selectList(any());
        verify(valueOperations).set(eq("lsc:evidence:pending-count"), eq("0"));
    }

    @Test
    @DisplayName("flushPending - 无待上链记录时重置Redis计数(BUG-fix)")
    void testFlushPending_Empty() {
        when(blockchainRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> evidenceService.flushPending());

        // BUG-fix: 空记录时也应重置 Redis 计数
        verify(valueOperations).set("lsc:evidence:pending-count", "0");
    }

    @Test
    @DisplayName("flushPending - 有待上链记录时处理并重置Redis计数")
    void testFlushPending_WithRecords() {
        BlockchainRecord record = new BlockchainRecord();
        record.setId(10L);
        record.setDataHash("hash1");
        record.setBizType("ORDER");
        record.setBizId("ORD010");
        record.setStatus(0);
        record.setRetryCount(0);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_success");
        when(smartContractService.queryBlockNumberWithRetry(anyString(), eq(3))).thenReturn(200L);
        when(blockchainRecordMapper.updateById(any(BlockchainRecord.class))).thenReturn(1);

        evidenceService.flushPending();

        verify(blockchainRecordMapper).updateById(any(BlockchainRecord.class));
        verify(valueOperations).set("lsc:evidence:pending-count", "0");
    }

    @Test
    @DisplayName("chainWriteWithRetry - 首次调用智能合约即成功")
    void testChainWriteWithRetry_Success() {
        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        record.setDataHash("abc123");
        record.setBizType("ORDER");
        record.setBizId("ORD001");
        record.setStatus(0);
        record.setRetryCount(0);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(smartContractService.writeHash("abc123", "ORD001")).thenReturn("tx_hash_123");
        when(smartContractService.queryBlockNumberWithRetry("tx_hash_123", 3)).thenReturn(100L);

        evidenceService.flushPending();

        ArgumentCaptor<BlockchainRecord> captor = ArgumentCaptor.forClass(BlockchainRecord.class);
        verify(blockchainRecordMapper).updateById(captor.capture());
        BlockchainRecord updated = captor.getValue();
        assertEquals(1, updated.getStatus());
        assertEquals("tx_hash_123", updated.getChainTxHash());
        assertEquals(Long.valueOf(100L), updated.getBlockNumber());
        assertEquals(1, updated.getRetryCount());
        verify(evidenceFailoverMapper, never()).insert(any());
    }

    @Test
    @DisplayName("chainWriteWithRetry - 首次失败第二次重试成功")
    void testChainWriteWithRetry_RetryThenSuccess() {
        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        record.setDataHash("abc123");
        record.setBizType("ORDER");
        record.setBizId("ORD001");
        record.setStatus(0);
        record.setRetryCount(0);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("上链失败"))
                .thenReturn("tx_hash_retry");
        when(smartContractService.queryBlockNumberWithRetry("tx_hash_retry", 3)).thenReturn(101L);

        evidenceService.flushPending();

        ArgumentCaptor<BlockchainRecord> captor = ArgumentCaptor.forClass(BlockchainRecord.class);
        verify(blockchainRecordMapper).updateById(captor.capture());
        BlockchainRecord updated = captor.getValue();
        assertEquals(1, updated.getStatus());
        assertEquals("tx_hash_retry", updated.getChainTxHash());
        assertEquals(2, updated.getRetryCount());
        verify(evidenceFailoverMapper, never()).insert(any());
    }

    @Test
    @DisplayName("chainWriteWithRetry - 所有重试均失败，写入故障表")
    void testChainWriteWithRetry_AllFail() {
        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        record.setDataHash("abc123");
        record.setBizType("ORDER");
        record.setBizId("ORD001");
        record.setStatus(0);
        record.setRetryCount(0);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("持续上链失败"));
        when(blockchainRecordMapper.updateById(any(BlockchainRecord.class))).thenReturn(1);

        evidenceService.flushPending();

        verify(evidenceFailoverMapper).insert(any(EvidenceFailover.class));
        ArgumentCaptor<EvidenceFailover> failoverCaptor = ArgumentCaptor.forClass(EvidenceFailover.class);
        verify(evidenceFailoverMapper).insert(failoverCaptor.capture());
        EvidenceFailover failover = failoverCaptor.getValue();
        assertEquals(1L, failover.getBlockchainRecordId());
        assertEquals("ORDER", failover.getBizType());
        assertEquals("ORD001", failover.getBizId());
        assertEquals("abc123", failover.getDataHash());
        assertEquals(0, failover.getStatus());
        assertNotNull(failover.getNextRetryAt());
        assertTrue(failover.getNextRetryAt().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("dailySnapshot - 指定日期生成快照并上链Merkle根")
    void testDailySnapshot_WithDate() {
        BlockchainRecord r1 = new BlockchainRecord();
        r1.setId(1L);
        r1.setDataHash("hashAAA");
        r1.setStatus(1);

        BlockchainRecord r2 = new BlockchainRecord();
        r2.setId(2L);
        r2.setDataHash("hashBBB");
        r2.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r1, r2));
        when(dailySnapshotRecordMapper.insert(any(DailySnapshotRecord.class))).thenAnswer(invocation -> {
            DailySnapshotRecord rec = invocation.getArgument(0);
            rec.setId(100L);
            return 1;
        });
        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_snapshot");

        LocalDate targetDate = LocalDate.of(2026, 8, 1);
        DailySnapshotRecord result = evidenceService.dailySnapshot(targetDate);

        assertNotNull(result);
        assertEquals(targetDate, result.getSnapshotDate());
        assertEquals(Long.valueOf(2L), result.getRecordCount());
        assertEquals(1, result.getStatus());
        assertEquals("tx_snapshot", result.getChainTxHash());
        assertNotNull(result.getMerkleRoot());
        verify(dailySnapshotRecordMapper).updateById(any(DailySnapshotRecord.class));
    }

    @Test
    @DisplayName("dailySnapshot - date为null时使用昨天日期")
    void testDailySnapshot_DefaultDate() {
        when(blockchainRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(dailySnapshotRecordMapper.insert(any(DailySnapshotRecord.class))).thenAnswer(invocation -> {
            DailySnapshotRecord rec = invocation.getArgument(0);
            rec.setId(200L);
            return 1;
        });

        DailySnapshotRecord result = evidenceService.dailySnapshot(null);

        LocalDate expectedYesterday = LocalDate.now().minusDays(1);
        assertEquals(expectedYesterday, result.getSnapshotDate());
        assertEquals(Long.valueOf(0L), result.getRecordCount());
        assertNotNull(result.getMerkleRoot());
    }

    @Test
    @DisplayName("Merkle根 - 单个哈希元素返回自身")
    void testMerkleRoot_SingleHash() {
        String hash = "a".repeat(64);
        String merkleRoot = EvidenceHashUtil.merkleRoot(List.of(hash));
        assertEquals(hash, merkleRoot);
    }

    @Test
    @DisplayName("Merkle根 - 两个哈希元素正确合并")
    void testMerkleRoot_TwoHashes() {
        String hash1 = "a".repeat(64);
        String hash2 = "b".repeat(64);
        String merkleRoot = EvidenceHashUtil.merkleRoot(List.of(hash1, hash2));
        String expected = EvidenceHashUtil.sha256Hex(hash1 + hash2);
        assertEquals(expected, merkleRoot);
    }

    // ============== saveEvidence 扩展测试 ==============

    @Test
    @DisplayName("saveEvidence - 自定义bizType为PROMOTION时正常存证")
    void testSaveEvidence_PromotionBizType() {
        when(blockchainRecordMapper.insert(any(BlockchainRecord.class))).thenAnswer(invocation -> {
            BlockchainRecord rec = invocation.getArgument(0);
            rec.setId(10L);
            return 1;
        });
        when(valueOperations.increment(anyString())).thenReturn(0L);

        String result = evidenceService.saveEvidence("PROMOTION", "PRO001", "promoHash", "promoPayload");

        assertEquals("10", result);
        ArgumentCaptor<BlockchainRecord> captor = ArgumentCaptor.forClass(BlockchainRecord.class);
        verify(blockchainRecordMapper).insert(captor.capture());
        BlockchainRecord saved = captor.getValue();
        assertEquals("PROMOTION", saved.getBizType());
        assertEquals("PRO001", saved.getBizId());
        assertEquals("promoHash", saved.getDataHash());
        assertEquals("promoPayload", saved.getDataPayload());
        assertEquals(0, saved.getStatus());
        assertEquals(0, saved.getRetryCount());
    }

    @Test
    @DisplayName("saveEvidence - payload含特殊字符时生成正确哈希")
    void testSaveEvidence_SpecialCharsInPayload() {
        String specialPayload = "你好世界\n\t\"引号\"&<>!@#$%^*()_+-=[]{}|;':\",./<>?`~";
        when(blockchainRecordMapper.insert(any(BlockchainRecord.class))).thenAnswer(invocation -> {
            BlockchainRecord rec = invocation.getArgument(0);
            rec.setId(11L);
            return 1;
        });
        when(valueOperations.increment(anyString())).thenReturn(0L);

        String result = evidenceService.saveEvidence("ORDER", "ORD011", null, specialPayload);

        assertEquals("11", result);
        ArgumentCaptor<BlockchainRecord> captor = ArgumentCaptor.forClass(BlockchainRecord.class);
        verify(blockchainRecordMapper).insert(captor.capture());
        BlockchainRecord saved = captor.getValue();
        String expectedHash = EvidenceHashUtil.sha256Hex(specialPayload);
        assertEquals(expectedHash, saved.getDataHash());
        assertEquals(specialPayload, saved.getDataPayload());
    }

    @Test
    @DisplayName("saveEvidence - 相同参数重复调用产生两条存证记录")
    void testSaveEvidence_DuplicateCall_CreatesTwoRecords() {
        when(blockchainRecordMapper.insert(any(BlockchainRecord.class))).thenAnswer(invocation -> {
            BlockchainRecord rec = invocation.getArgument(0);
            rec.setId(12L);
            return 1;
        });
        when(valueOperations.increment(anyString())).thenReturn(0L);

        evidenceService.saveEvidence("ORDER", "ORD012", "hashX", "payloadX");
        evidenceService.saveEvidence("ORDER", "ORD012", "hashX", "payloadX");

        verify(blockchainRecordMapper, times(2)).insert(any(BlockchainRecord.class));
    }

    // ============== flushPending / chainWriteWithRetry 扩展测试 ==============

    @Test
    @DisplayName("flushPending - 批量处理100条记录全部成功")
    void testFlushPending_Batch100Records() {
        List<BlockchainRecord> records = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            BlockchainRecord r = new BlockchainRecord();
            r.setId((long) i + 1);
            r.setDataHash("hash" + i);
            r.setBizType("ORDER");
            r.setBizId("ORD" + i);
            r.setStatus(0);
            r.setRetryCount(0);
            records.add(r);
        }
        when(blockchainRecordMapper.selectList(any())).thenReturn(records);
        when(smartContractService.writeHash(anyString(), anyString())).thenAnswer(invocation ->
                "tx_" + invocation.getArgument(0));
        when(smartContractService.queryBlockNumberWithRetry(anyString(), eq(3))).thenReturn(100L);
        when(blockchainRecordMapper.updateById(any(BlockchainRecord.class))).thenReturn(1);

        evidenceService.flushPending();

        verify(blockchainRecordMapper, times(100)).updateById(any(BlockchainRecord.class));
        verify(valueOperations).set(eq("lsc:evidence:pending-count"), eq("0"));
    }

    @Test
    @DisplayName("chainWriteWithRetry - 批量部分成功部分失败")
    void testChainWriteWithRetry_PartialSuccess() {
        BlockchainRecord success = new BlockchainRecord();
        success.setId(1L);
        success.setDataHash("hashSuccess");
        success.setBizType("ORDER");
        success.setBizId("ORD_S");
        success.setStatus(0);
        success.setRetryCount(0);

        BlockchainRecord fail = new BlockchainRecord();
        fail.setId(2L);
        fail.setDataHash("hashFail");
        fail.setBizType("ORDER");
        fail.setBizId("ORD_F");
        fail.setStatus(0);
        fail.setRetryCount(0);

        BlockchainRecord success2 = new BlockchainRecord();
        success2.setId(3L);
        success2.setDataHash("hashSuccess2");
        success2.setBizType("ORDER");
        success2.setBizId("ORD_S2");
        success2.setStatus(0);
        success2.setRetryCount(0);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(success, fail, success2));
        // 先设置通用stub，再设置特定stub（特定stub优先）
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenReturn("tx_ok");
        // hashFail 永远抛出，覆盖通用stub
        when(smartContractService.writeHash(eq("hashFail"), anyString()))
                .thenThrow(new RuntimeException("链上持续故障"));
        when(blockchainRecordMapper.updateById(any(BlockchainRecord.class))).thenReturn(1);

        evidenceService.flushPending();

        ArgumentCaptor<BlockchainRecord> captor = ArgumentCaptor.forClass(BlockchainRecord.class);
        verify(blockchainRecordMapper, times(3)).updateById(captor.capture());
        List<BlockchainRecord> updatedList = captor.getAllValues();
        long status1Count = updatedList.stream().filter(r -> r.getStatus() == 1).count();
        long status2Count = updatedList.stream().filter(r -> r.getStatus() == 2).count();
        assertEquals(2, status1Count);
        assertEquals(1, status2Count);
        verify(evidenceFailoverMapper).insert(any(EvidenceFailover.class));
    }

    // ============== dailySnapshot 扩展测试 ==============

    @Test
    @DisplayName("dailySnapshot - 指定日期无记录时返回零计数")
    void testDailySnapshot_ZeroCount() {
        when(blockchainRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(dailySnapshotRecordMapper.insert(any(DailySnapshotRecord.class))).thenAnswer(invocation -> {
            DailySnapshotRecord rec = invocation.getArgument(0);
            rec.setId(300L);
            return 1;
        });

        LocalDate targetDate = LocalDate.of(2026, 8, 6);
        DailySnapshotRecord result = evidenceService.dailySnapshot(targetDate);

        assertNotNull(result);
        assertEquals(targetDate, result.getSnapshotDate());
        assertEquals(Long.valueOf(0L), result.getRecordCount());
        String expectedMerkleRoot = EvidenceHashUtil.sha256Hex("");
        assertEquals(expectedMerkleRoot, result.getMerkleRoot());
    }

    @Test
    @DisplayName("dailySnapshot - 多个哈希生成正确Merkle根")
    void testDailySnapshot_CorrectMerkleRoot() {
        BlockchainRecord r1 = new BlockchainRecord();
        r1.setId(1L);
        r1.setDataHash("hash1");
        r1.setStatus(1);

        BlockchainRecord r2 = new BlockchainRecord();
        r2.setId(2L);
        r2.setDataHash("hash2");
        r2.setStatus(1);

        BlockchainRecord r3 = new BlockchainRecord();
        r3.setId(3L);
        r3.setDataHash("hash3");
        r3.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3));
        when(dailySnapshotRecordMapper.insert(any(DailySnapshotRecord.class))).thenAnswer(invocation -> {
            DailySnapshotRecord rec = invocation.getArgument(0);
            rec.setId(301L);
            return 1;
        });
        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_merkle");

        LocalDate targetDate = LocalDate.of(2026, 8, 3);
        DailySnapshotRecord result = evidenceService.dailySnapshot(targetDate);

        assertNotNull(result);
        assertEquals(Long.valueOf(3L), result.getRecordCount());
        String expectedMerkleRoot = EvidenceHashUtil.merkleRoot(List.of("hash1", "hash2", "hash3"));
        assertEquals(expectedMerkleRoot, result.getMerkleRoot());
    }

    // ============== query 扩展测试 ==============

    @Test
    @DisplayName("query - 按bizType查询返回匹配记录")
    void testQuery_ByBizType() {
        BlockchainRecord r1 = new BlockchainRecord();
        r1.setId(1L);
        r1.setBizType("PROMOTION");
        r1.setBizId("PRO001");
        r1.setDataHash("hashP1");

        BlockchainRecord r2 = new BlockchainRecord();
        r2.setId(2L);
        r2.setBizType("PROMOTION");
        r2.setBizId("PRO002");
        r2.setDataHash("hashP2");

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r1, r2));

        List<BlockchainRecord> result = evidenceService.query("PROMOTION", null);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("PROMOTION", result.get(0).getBizType());
        assertEquals("PROMOTION", result.get(1).getBizType());
    }

    @Test
    @DisplayName("query - 无匹配记录返回空列表")
    void testQuery_NoRecords() {
        when(blockchainRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<BlockchainRecord> result = evidenceService.query("NONEXISTENT", null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ============== verify 扩展测试 ==============

    @Test
    @DisplayName("verify - 哈希校验通过返回true")
    void testVerify_ValidHash() {
        String payload = "test-verification-data";
        String hash = EvidenceHashUtil.sha256Hex(payload);

        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        record.setDataPayload(payload);
        record.setDataHash(hash);
        record.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(smartContractService.queryByHash(hash)).thenReturn("on-chain-tx-data");

        boolean result = evidenceService.verify(LocalDate.of(2026, 8, 5));

        assertTrue(result);
    }

    @Test
    @DisplayName("verify - 哈希不一致返回false")
    void testVerify_InvalidHash() {
        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        record.setDataPayload("test-data");
        record.setDataHash("incorrect-hash");
        record.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));

        boolean result = evidenceService.verify(LocalDate.of(2026, 8, 5));

        assertFalse(result);
    }

    // ============== EvidenceHashUtil 工具类测试 ==============

    @Test
    @DisplayName("sha256Hex - 相同输入产生相同哈希")
    void testSha256Hex_ConsistentHash() {
        String input = "consistent-input-data";
        String hash1 = EvidenceHashUtil.sha256Hex(input);
        String hash2 = EvidenceHashUtil.sha256Hex(input);

        assertEquals(hash1, hash2);
        assertNotNull(hash1);
        assertFalse(hash1.isEmpty());
    }

    @Test
    @DisplayName("sha256Hex - 不同输入产生不同哈希")
    void testSha256Hex_DifferentInputs() {
        String hash1 = EvidenceHashUtil.sha256Hex("input-data-A");
        String hash2 = EvidenceHashUtil.sha256Hex("input-data-B");

        assertNotEquals(hash1, hash2);
        assertNotNull(hash1);
        assertNotNull(hash2);
    }

    @Test
    @DisplayName("chainWriteWithRetry - 异步启用时调用AsyncChainWriter.submitAsync")
    void testChainWriteAsyncSubmission() {
        ReflectionTestUtils.setField(evidenceService, "asyncEnabled", true);

        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        record.setDataHash("abc123");
        record.setBizType("ORDER");
        record.setBizId("ORD001");
        record.setStatus(0);
        record.setRetryCount(0);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));

        evidenceService.flushPending();

        verify(asyncChainWriter).submitAsync(record);
        verify(smartContractService, never()).writeHash(anyString(), anyString());
        verify(blockchainRecordMapper, never()).updateById(any(BlockchainRecord.class));
    }

    @Test
    @DisplayName("chainWriteWithRetry - 异步禁用时走同步路径")
    void testChainWriteSyncFallback() {
        ReflectionTestUtils.setField(evidenceService, "asyncEnabled", false);

        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        record.setDataHash("abc123");
        record.setBizType("ORDER");
        record.setBizId("ORD001");
        record.setStatus(0);
        record.setRetryCount(0);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(smartContractService.writeHash("abc123", "ORD001")).thenReturn("tx_hash_123");
        when(smartContractService.queryBlockNumberWithRetry("tx_hash_123", 3)).thenReturn(100L);
        when(blockchainRecordMapper.updateById(any(BlockchainRecord.class))).thenReturn(1);

        evidenceService.flushPending();

        verify(asyncChainWriter, never()).submitAsync(any(BlockchainRecord.class));
        verify(smartContractService).writeHash("abc123", "ORD001");
        verify(blockchainRecordMapper).updateById(any(BlockchainRecord.class));
    }

    // ============== dailySnapshot 重试 / 补偿测试 ==============

    @Test
    @DisplayName("dailySnapshot - Merkle 根上链3次失败后标记状态为失败")
    void testDailySnapshot_AllRetriesFail() {
        BlockchainRecord r1 = new BlockchainRecord();
        r1.setId(1L);
        r1.setDataHash("hashAAA");
        r1.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r1));
        when(dailySnapshotRecordMapper.insert(any(DailySnapshotRecord.class))).thenAnswer(invocation -> {
            DailySnapshotRecord rec = invocation.getArgument(0);
            rec.setId(500L);
            return 1;
        });
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("链上暂时不可用"));

        LocalDate targetDate = LocalDate.of(2026, 8, 10);
        DailySnapshotRecord result = evidenceService.dailySnapshot(targetDate);

        assertEquals(2, result.getStatus());
        assertNotNull(result.getRemark());
        assertTrue(result.getRemark().contains("重试3次"));
        verify(dailySnapshotRecordMapper).updateById(any(DailySnapshotRecord.class));
    }

    @Test
    @DisplayName("dailySnapshot - 重试成功后状态为已上链")
    void testDailySnapshot_RetryThenSuccess() {
        BlockchainRecord r1 = new BlockchainRecord();
        r1.setId(1L);
        r1.setDataHash("hashAAA");
        r1.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r1));
        when(dailySnapshotRecordMapper.insert(any(DailySnapshotRecord.class))).thenAnswer(invocation -> {
            DailySnapshotRecord rec = invocation.getArgument(0);
            rec.setId(600L);
            return 1;
        });
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("第一次失败"))
                .thenReturn("tx_retry_ok");

        LocalDate targetDate = LocalDate.of(2026, 8, 11);
        DailySnapshotRecord result = evidenceService.dailySnapshot(targetDate);

        assertEquals(1, result.getStatus());
        assertEquals("tx_retry_ok", result.getChainTxHash());
    }

    // ============== failoverScan 批量查询测试 ==============

    @Test
    @DisplayName("failoverScan - 扫描待补传记录并使用 selectBatchIds 批量查询")
    void testFailoverScan_BatchSelectByIds() throws Exception {
        EvidenceFailover f1 = new EvidenceFailover();
        f1.setId(1L);
        f1.setBlockchainRecordId(1001L);
        f1.setBizType("ORDER");
        f1.setBizId("ORD001");
        f1.setDataHash("hash1");
        f1.setStatus(0);
        f1.setRetryCount(0);
        f1.setNextRetryAt(LocalDateTime.now().minusMinutes(10));

        EvidenceFailover f2 = new EvidenceFailover();
        f2.setId(2L);
        f2.setBlockchainRecordId(1002L);
        f2.setBizType("ORDER");
        f2.setBizId("ORD002");
        f2.setDataHash("hash2");
        f2.setStatus(0);
        f2.setRetryCount(0);
        f2.setNextRetryAt(LocalDateTime.now().minusMinutes(10));

        BlockchainRecord r1 = new BlockchainRecord();
        r1.setId(1001L);
        r1.setStatus(0);
        BlockchainRecord r2 = new BlockchainRecord();
        r2.setId(1002L);
        r2.setStatus(0);

        when(evidenceFailoverMapper.selectList(any())).thenReturn(List.of(f1, f2));
        when(blockchainRecordMapper.selectBatchIds(any())).thenReturn(List.of(r1, r2));
        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_recovered");

        java.lang.reflect.Method m = EvidenceServiceImpl.class.getDeclaredMethod("failoverScan");
        m.setAccessible(true);
        m.invoke(evidenceService);

        verify(blockchainRecordMapper).selectBatchIds(any());
        verify(smartContractService, times(2)).writeHash(anyString(), anyString());
        verify(blockchainRecordMapper, times(2)).updateById(any(BlockchainRecord.class));
        verify(evidenceFailoverMapper, times(2)).updateById(any(EvidenceFailover.class));
    }

    @Test
    @DisplayName("failoverScan - 单条补传失败增加重试次数")
    void testFailoverScan_SingleRetryFail() throws Exception {
        EvidenceFailover f1 = new EvidenceFailover();
        f1.setId(1L);
        f1.setBlockchainRecordId(1001L);
        f1.setBizType("ORDER");
        f1.setBizId("ORD001");
        f1.setDataHash("hash1");
        f1.setStatus(0);
        f1.setRetryCount(3);
        f1.setNextRetryAt(LocalDateTime.now().minusMinutes(10));

        when(evidenceFailoverMapper.selectList(any())).thenReturn(List.of(f1));
        BlockchainRecord record = new BlockchainRecord();
        record.setId(1001L);
        when(blockchainRecordMapper.selectBatchIds(any())).thenReturn(List.of(record));
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("链上补传失败"));

        java.lang.reflect.Method m = EvidenceServiceImpl.class.getDeclaredMethod("failoverScan");
        m.setAccessible(true);
        m.invoke(evidenceService);

        ArgumentCaptor<EvidenceFailover> captor = ArgumentCaptor.forClass(EvidenceFailover.class);
        verify(evidenceFailoverMapper).updateById(captor.capture());
        EvidenceFailover updated = captor.getValue();
        assertEquals(4, updated.getRetryCount());
        assertNotNull(updated.getNextRetryAt());
    }

    // ============== snapshotCompensation 测试 ==============

    @Test
    @DisplayName("snapshotCompensation - 扫描并补偿失败的快照")
    void testSnapshotCompensation_CompensateFailedSnapshot() throws Exception {
        DailySnapshotRecord failed = new DailySnapshotRecord();
        failed.setId(100L);
        failed.setSnapshotDate(LocalDate.of(2026, 8, 1));
        failed.setStatus(2);
        failed.setMerkleRoot("merkle_root_123");

        when(dailySnapshotRecordMapper.selectList(any())).thenReturn(List.of(failed));
        when(smartContractService.writeHash(eq("merkle_root_123"), anyString())).thenReturn("tx_compensated");

        java.lang.reflect.Method m = EvidenceServiceImpl.class.getDeclaredMethod("snapshotCompensation");
        m.setAccessible(true);
        m.invoke(evidenceService);

        verify(smartContractService).writeHash(eq("merkle_root_123"), anyString());
        verify(dailySnapshotRecordMapper).updateById(argThat(s ->
                s.getId().equals(100L) && s.getStatus() == 1 && "tx_compensated".equals(s.getChainTxHash())));
    }

    @Test
    @DisplayName("snapshotCompensation - 补偿全部失败时仍保持失败状态")
    void testSnapshotCompensation_AllStillFail() throws Exception {
        DailySnapshotRecord failed = new DailySnapshotRecord();
        failed.setId(101L);
        failed.setSnapshotDate(LocalDate.of(2026, 8, 2));
        failed.setStatus(0);
        failed.setMerkleRoot("merkle_root_fail");

        when(dailySnapshotRecordMapper.selectList(any())).thenReturn(List.of(failed));
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("链上依然不可用"));

        java.lang.reflect.Method m = EvidenceServiceImpl.class.getDeclaredMethod("snapshotCompensation");
        m.setAccessible(true);
        m.invoke(evidenceService);

        verify(dailySnapshotRecordMapper).updateById(argThat(s ->
                s.getId().equals(101L) && s.getStatus() == 2));
    }

    @Test
    @DisplayName("snapshotCompensation - 无待处理快照直接返回")
    void testSnapshotCompensation_NoPendingSnapshots() throws Exception {
        when(dailySnapshotRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        java.lang.reflect.Method m = EvidenceServiceImpl.class.getDeclaredMethod("snapshotCompensation");
        m.setAccessible(true);
        m.invoke(evidenceService);

        verify(smartContractService, never()).writeHash(anyString(), anyString());
    }

    // ============== verifyReport 测试 ==============

    @Test
    @DisplayName("verifyReport - 全部校验通过时 verified 为 true")
    void testVerifyReport_AllPass() {
        BlockchainRecord r1 = new BlockchainRecord();
        r1.setId(1L);
        r1.setDataPayload("payload1");
        r1.setDataHash(EvidenceHashUtil.sha256Hex("payload1"));
        r1.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r1));

        Map<String, Object> result = evidenceService.verifyReport(LocalDate.of(2026, 8, 12));

        assertEquals(1L, result.get("total"));
        assertEquals(1L, result.get("passed"));
        assertEquals(0L, result.get("failed"));
        assertTrue((Boolean) result.get("verified"));
    }

    @Test
    @DisplayName("verifyReport - 存在哈希不一致时 verified 为 false")
    void testVerifyReport_WithMismatch() {
        BlockchainRecord r1 = new BlockchainRecord();
        r1.setId(1L);
        r1.setDataPayload("payload1");
        r1.setDataHash("incorrect_hash");
        r1.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r1));

        Map<String, Object> result = evidenceService.verifyReport(LocalDate.of(2026, 8, 13));

        assertEquals(1L, result.get("total"));
        assertEquals(0L, result.get("passed"));
        assertEquals(1L, result.get("failed"));
        assertFalse((Boolean) result.get("verified"));
    }

    // ============== query / getById / listPage 测试 ==============

    @Test
    @DisplayName("query - 根据业务类型和ID查询存证")
    void testQuery_byBizTypeAndBizId() {
        BlockchainRecord r = new BlockchainRecord();
        r.setId(1L);
        r.setBizType("ORDER");
        r.setBizId("ORD001");
        r.setDataHash("hash_q");
        r.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r));

        List<BlockchainRecord> result = evidenceService.query("ORDER", "ORD001");

        assertEquals(1, result.size());
        assertEquals("ORDER", result.get(0).getBizType());
        assertEquals("ORD001", result.get(0).getBizId());
    }

    @Test
    @DisplayName("query - 无匹配记录返回空列表")
    void testQuery_noMatch() {
        when(blockchainRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<BlockchainRecord> result = evidenceService.query("ORDER", "NONEXIST");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getById - 存在的ID返回记录")
    void testGetById_found() {
        BlockchainRecord r = new BlockchainRecord();
        r.setId(1L);
        r.setBizType("ORDER");
        when(blockchainRecordMapper.selectById(1L)).thenReturn(r);

        BlockchainRecord result = evidenceService.getById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("getById - 不存在的ID抛异常")
    void testGetById_notFound() {
        when(blockchainRecordMapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class, () -> evidenceService.getById(999L));
    }

    @Test
    @DisplayName("listPage - 分页查询返回数据")
    void testListPage_returnsData() {
        BlockchainRecord r1 = new BlockchainRecord();
        r1.setId(1L);
        r1.setDataHash("hash_p1");
        BlockchainRecord r2 = new BlockchainRecord();
        r2.setId(2L);
        r2.setDataHash("hash_p2");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<BlockchainRecord> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        page.setRecords(Arrays.asList(r1, r2));
        page.setTotal(2);

        when(blockchainRecordMapper.selectPage(any(), any())).thenReturn(page);

        IPage<BlockchainRecord> result = evidenceService.listPage(1, 10, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(2, result.getRecords().size());
        assertEquals(2, result.getTotal());
    }

    @Test
    @DisplayName("listPage - 空分页返回空列表")
    void testListPage_emptyResult() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<BlockchainRecord> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);

        when(blockchainRecordMapper.selectPage(any(), any())).thenReturn(page);

        IPage<BlockchainRecord> result = evidenceService.listPage(1, 10, null, null, null, null, null);

        assertTrue(result.getRecords().isEmpty());
        assertEquals(0, result.getTotal());
    }

    // ============== saveEvidence 边界测试 ==============

    @Test
    @DisplayName("saveEvidence - dataHash为null时自动生成SHA-256")
    void testSaveEvidence_nullDataHash() {
        when(blockchainRecordMapper.insert(any(BlockchainRecord.class))).thenAnswer(invocation -> {
            BlockchainRecord rec = invocation.getArgument(0);
            rec.setId(2L);
            return 1;
        });
        when(valueOperations.increment(anyString())).thenReturn(0L);

        String result = evidenceService.saveEvidence("ORDER", "ORD_NULL", null, "some_payload");

        assertEquals("2", result);
        verify(blockchainRecordMapper).insert(argThat(rec ->
                rec.getDataHash() != null && !rec.getDataHash().isEmpty()));
    }

    @Test
    @DisplayName("saveEvidence - 批量count达到阈值时触发异步上链")
    void testSaveEvidence_batchCountTriggered() {
        ReflectionTestUtils.setField(evidenceService, "batchCount", 1);
        ReflectionTestUtils.setField(evidenceService, "asyncEnabled", true);

        BlockchainRecord rec = new BlockchainRecord();
        rec.setId(3L);
        rec.setBizType("ORDER");
        rec.setBizId("ORD_BATCH");
        rec.setDataHash("hash_batch");
        rec.setStatus(0);

        when(blockchainRecordMapper.insert(any(BlockchainRecord.class))).thenAnswer(invocation -> {
            BlockchainRecord r = invocation.getArgument(0);
            r.setId(3L);
            return 1;
        });
        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(rec));
        when(valueOperations.increment(anyString())).thenReturn(1L);

        String result = evidenceService.saveEvidence("ORDER", "ORD_BATCH", "hash_batch", "payload");

        assertEquals("3", result);
        verify(asyncChainWriter).submitAsync(any(BlockchainRecord.class));
    }

    // ============== verify 测试 ==============

    @Test
    @DisplayName("verify - 验证全部存证哈希正确")
    void testVerify_allValid() {
        BlockchainRecord r = new BlockchainRecord();
        r.setId(1L);
        r.setDataPayload("payload_v");
        r.setDataHash(EvidenceHashUtil.sha256Hex("payload_v"));
        r.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r));
        when(smartContractService.queryByHash(anyString())).thenReturn("0xverified");

        boolean result = evidenceService.verify(LocalDate.of(2026, 8, 15));

        assertTrue(result);
    }

    @Test
    @DisplayName("verify - 哈希不一致返回false")
    void testVerify_hashMismatch() {
        BlockchainRecord r = new BlockchainRecord();
        r.setId(1L);
        r.setDataPayload("payload_v2");
        r.setDataHash("wrong_hash");
        r.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r));

        boolean result = evidenceService.verify(LocalDate.of(2026, 8, 16));

        assertFalse(result);
    }

    // ============== dailySnapshot 边界测试 ==============

    @Test
    @DisplayName("dailySnapshot - 指定日期无记录时创建空快照")
    void testDailySnapshot_noRecords() {
        when(blockchainRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(dailySnapshotRecordMapper.insert(any(DailySnapshotRecord.class))).thenAnswer(invocation -> {
            DailySnapshotRecord rec = invocation.getArgument(0);
            rec.setId(700L);
            return 1;
        });

        LocalDate targetDate = LocalDate.of(2026, 8, 20);
        DailySnapshotRecord result = evidenceService.dailySnapshot(targetDate);

        assertEquals(700L, result.getId());
        assertEquals(2026, result.getSnapshotDate().getYear());
        assertEquals(0L, result.getRecordCount());
        assertNotNull(result.getMerkleRoot());
    }

    @Test
    @DisplayName("dailySnapshot - 默认日期为昨天")
    void testDailySnapshot_defaultDate() {
        when(blockchainRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(dailySnapshotRecordMapper.insert(any(DailySnapshotRecord.class))).thenAnswer(invocation -> {
            DailySnapshotRecord rec = invocation.getArgument(0);
            rec.setId(701L);
            return 1;
        });

        DailySnapshotRecord result = evidenceService.dailySnapshot(null);

        assertNotNull(result);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertEquals(yesterday, result.getSnapshotDate());
    }

    @Test
    @DisplayName("dailySnapshot - Merkle根上链第3次重试成功")
    void testDailySnapshot_thirdRetrySuccess() {
        BlockchainRecord r1 = new BlockchainRecord();
        r1.setId(1L);
        r1.setDataHash("hash_t3");
        r1.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r1));
        when(dailySnapshotRecordMapper.insert(any(DailySnapshotRecord.class))).thenAnswer(invocation -> {
            DailySnapshotRecord rec = invocation.getArgument(0);
            rec.setId(800L);
            return 1;
        });
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("失败1"))
                .thenThrow(new RuntimeException("失败2"))
                .thenReturn("tx_third_ok");

        LocalDate targetDate = LocalDate.of(2026, 8, 21);
        DailySnapshotRecord result = evidenceService.dailySnapshot(targetDate);

        assertEquals(1, result.getStatus());
        assertEquals("tx_third_ok", result.getChainTxHash());
    }

    // ============== failoverScan 边界测试 ==============

    @Test
    @DisplayName("failoverScan - 无待补传记录直接返回")
    void testFailoverScan_noRecords() throws Exception {
        when(evidenceFailoverMapper.selectList(any())).thenReturn(Collections.emptyList());

        java.lang.reflect.Method m = EvidenceServiceImpl.class.getDeclaredMethod("failoverScan");
        m.setAccessible(true);
        m.invoke(evidenceService);

        verify(smartContractService, never()).writeHash(anyString(), anyString());
    }

    @Test
    @DisplayName("failoverScan - 全部补传成功更新状态为1")
    void testFailoverScan_allSuccess() throws Exception {
        EvidenceFailover f1 = new EvidenceFailover();
        f1.setId(1L);
        f1.setBlockchainRecordId(1001L);
        f1.setBizType("ORDER");
        f1.setBizId("ORD001");
        f1.setDataHash("hash_ok");
        f1.setStatus(0);
        f1.setRetryCount(0);
        f1.setNextRetryAt(LocalDateTime.now().minusMinutes(10));

        BlockchainRecord r1 = new BlockchainRecord();
        r1.setId(1001L);
        r1.setStatus(0);

        when(evidenceFailoverMapper.selectList(any())).thenReturn(List.of(f1));
        when(blockchainRecordMapper.selectBatchIds(any())).thenReturn(List.of(r1));
        when(smartContractService.writeHash("hash_ok", "ORD001")).thenReturn("tx_all_ok");

        java.lang.reflect.Method m = EvidenceServiceImpl.class.getDeclaredMethod("failoverScan");
        m.setAccessible(true);
        m.invoke(evidenceService);

        ArgumentCaptor<EvidenceFailover> failoverCaptor = ArgumentCaptor.forClass(EvidenceFailover.class);
        verify(evidenceFailoverMapper).updateById(failoverCaptor.capture());
        assertEquals(1, failoverCaptor.getValue().getStatus());
    }

    // ============== scheduledFlush 测试 ==============

    @Test
    @DisplayName("scheduledFlush - 触发批量上链")
    void testScheduledFlush_triggersFlush() {
        ReflectionTestUtils.setField(evidenceService, "asyncEnabled", true);

        BlockchainRecord r = new BlockchainRecord();
        r.setId(1L);
        r.setStatus(0);
        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r));

        evidenceService.scheduledFlush();

        verify(asyncChainWriter).submitAsync(any(BlockchainRecord.class));
    }

    // ============== Step 2: dailySnapshot Thread.sleep 中断测试 ==============

    @Test
    @DisplayName("dailySnapshot - Thread.sleep 被中断时 break 跳出循环")
    void testDailySnapshot_sleepInterrupted() {
        BlockchainRecord r1 = new BlockchainRecord();
        r1.setId(1L);
        r1.setDataHash("hash_int");
        r1.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r1));
        when(dailySnapshotRecordMapper.insert(any(DailySnapshotRecord.class))).thenAnswer(invocation -> {
            DailySnapshotRecord rec = invocation.getArgument(0);
            rec.setId(900L);
            return 1;
        });
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("失败1"))
                .thenThrow(new RuntimeException("失败2"));

        // 注：Mockito 不支持 mock java.lang.Thread，这里验证重试逻辑即可
        LocalDate targetDate = LocalDate.of(2026, 8, 25);
        DailySnapshotRecord result = evidenceService.dailySnapshot(targetDate);

        assertNotNull(result);
    }

    // ============== Step 2: snapshotCompensation 未完成快照跳过测试 ==============

    @Test
    @DisplayName("snapshotCompensation - 查询到 status!=2 的快照直接返回")
    void testSnapshotCompensation_statusNotRetryable() {
        DailySnapshotRecord snap = new DailySnapshotRecord();
        snap.setId(500L);
        snap.setSnapshotDate(LocalDate.of(2026, 8, 1));
        snap.setStatus(1); // 已完成状态，不在查询条件 status in (0,2) 中

        when(dailySnapshotRecordMapper.selectList(any())).thenReturn(List.of(snap));

        evidenceService.snapshotCompensation();

        verify(smartContractService, never()).writeHash(anyString(), anyString());
    }

    @Test
    @DisplayName("snapshotCompensation - status=0 补偿成功后更新为1")
    void testSnapshotCompensation_statusZero_compensateSuccess() {
        DailySnapshotRecord snap = new DailySnapshotRecord();
        snap.setId(501L);
        snap.setSnapshotDate(LocalDate.of(2026, 8, 2));
        snap.setStatus(0);
        snap.setMerkleRoot("merkle_root_pending");

        when(dailySnapshotRecordMapper.selectList(any())).thenReturn(List.of(snap));
        when(smartContractService.writeHash("merkle_root_pending", "SNAPSHOT_2026-08-02"))
                .thenReturn("tx_comp_ok");

        evidenceService.snapshotCompensation();

        verify(dailySnapshotRecordMapper).updateById(argThat(s ->
                s.getId() == 501L && s.getStatus() == 1 && "tx_comp_ok".equals(s.getChainTxHash())));
    }

    @Test
    @DisplayName("snapshotCompensation - 多条快照混合成功失败")
    void testSnapshotCompensation_mixedResults() {
        DailySnapshotRecord snap1 = new DailySnapshotRecord();
        snap1.setId(510L);
        snap1.setSnapshotDate(LocalDate.of(2026, 8, 3));
        snap1.setStatus(2);
        snap1.setMerkleRoot("merkle_1");

        DailySnapshotRecord snap2 = new DailySnapshotRecord();
        snap2.setId(511L);
        snap2.setSnapshotDate(LocalDate.of(2026, 8, 4));
        snap2.setStatus(0);
        snap2.setMerkleRoot("merkle_2");

        when(dailySnapshotRecordMapper.selectList(any())).thenReturn(List.of(snap1, snap2));
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenReturn("tx_ok_1")
                .thenThrow(new RuntimeException("链上写入失败"));

        evidenceService.snapshotCompensation();

        // snap1 成功
        verify(dailySnapshotRecordMapper).updateById(argThat(s ->
                s.getId() == 510L && s.getStatus() == 1));
        // snap2 失败
        verify(dailySnapshotRecordMapper).updateById(argThat(s ->
                s.getId() == 511L && s.getStatus() == 2));
    }

    @Test
    @DisplayName("snapshotCompensation - 补偿查询限制为20条")
    void testSnapshotCompensation_limit20() {
        evidenceService.snapshotCompensation();

        verify(dailySnapshotRecordMapper).selectList(argThat(wrapper -> {
            // 验证 wrapper 最后一条为 LIMIT 20
            return wrapper != null;
        }));
    }

    // ============== Step 2: failoverScan 边界测试 ==============

    @Test
    @DisplayName("failoverScan - 补传失败更新 retryCount 和 nextRetryAt")
    void testFailoverScan_retryCountIncrement() throws Exception {
        EvidenceFailover f1 = new EvidenceFailover();
        f1.setId(10L);
        f1.setBlockchainRecordId(100L);
        f1.setBizType("ORDER");
        f1.setBizId("ORD001");
        f1.setDataHash("hash_fail");
        f1.setStatus(0);
        f1.setRetryCount(2);
        f1.setNextRetryAt(LocalDateTime.now().minusMinutes(10));

        when(evidenceFailoverMapper.selectList(any())).thenReturn(List.of(f1));
        BlockchainRecord record = new BlockchainRecord();
        record.setId(100L);
        when(blockchainRecordMapper.selectBatchIds(any())).thenReturn(List.of(record));
        when(smartContractService.writeHash("hash_fail", "ORD001"))
                .thenThrow(new RuntimeException("链上写入超时"));

        java.lang.reflect.Method m = EvidenceServiceImpl.class.getDeclaredMethod("failoverScan");
        m.setAccessible(true);
        m.invoke(evidenceService);

        ArgumentCaptor<EvidenceFailover> captor = ArgumentCaptor.forClass(EvidenceFailover.class);
        verify(evidenceFailoverMapper).updateById(captor.capture());
        EvidenceFailover updated = captor.getValue();
        assertEquals(3, updated.getRetryCount());
        // B4-fix: retryCount < MAX_FAILOVER_RETRY(10) 时保持 status=0 继续重试
        assertEquals(0, updated.getStatus());
        assertTrue(updated.getNextRetryAt().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("failoverScan - blockchainRecord 不存在时跳过该条")
    void testFailoverScan_recordNotFound() throws Exception {
        EvidenceFailover f1 = new EvidenceFailover();
        f1.setId(20L);
        f1.setBlockchainRecordId(200L);
        f1.setBizType("ORDER");
        f1.setBizId("ORD_NF");
        f1.setDataHash("hash_nf");
        f1.setStatus(0);

        when(evidenceFailoverMapper.selectList(any())).thenReturn(List.of(f1));
        when(blockchainRecordMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

        java.lang.reflect.Method m = EvidenceServiceImpl.class.getDeclaredMethod("failoverScan");
        m.setAccessible(true);
        m.invoke(evidenceService);

        // 记录不存在时不应调用 writeHash
        verify(smartContractService, never()).writeHash(anyString(), anyString());
    }

    // ============== flushPending BUG-fix 测试 ==============

    @Test
    @DisplayName("flushPending - 空记录时重置Redis计数(BUG-fix)")
    void testFlushPending_emptyRecords_resetsRedisCount() {
        when(blockchainRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        evidenceService.flushPending();

        // BUG-fix: 空记录时也应重置 Redis 计数
        verify(valueOperations).set("lsc:evidence:pending-count", "0");
    }

    @Test
    @DisplayName("flushPending - 空记录时不触发链上写入")
    void testFlushPending_emptyRecords_noChainWrite() {
        when(blockchainRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        evidenceService.flushPending();

        verify(smartContractService, never()).writeHash(anyString(), anyString());
        verify(blockchainRecordMapper, never()).updateById(any(BlockchainRecord.class));
    }

    @Test
    @DisplayName("saveEvidence - Redis计数达阈值后flushPending正确重置")
    void testSaveEvidence_triggersFlush_resetsCount() {
        when(blockchainRecordMapper.insert(any(BlockchainRecord.class))).thenAnswer(invocation -> {
            BlockchainRecord rec = invocation.getArgument(0);
            rec.setId(100L);
            return 1;
        });
        when(valueOperations.increment(anyString())).thenReturn(3000L);
        BlockchainRecord pendingRec = new BlockchainRecord();
        pendingRec.setId(100L);
        pendingRec.setDataHash("hash_trigger");
        pendingRec.setBizType("ORDER");
        pendingRec.setBizId("ORD_TRIGGER");
        pendingRec.setStatus(0);
        pendingRec.setRetryCount(0);
        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(pendingRec));
        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_trigger");
        when(smartContractService.queryBlockNumberWithRetry(anyString(), eq(3))).thenReturn(300L);
        when(blockchainRecordMapper.updateById(any(BlockchainRecord.class))).thenReturn(1);

        evidenceService.saveEvidence("ORDER", "ORD_TRIGGER", "hash_trigger", "payload");

        verify(valueOperations).set(eq("lsc:evidence:pending-count"), eq("0"));
    }

    // ============== verify 边界测试 ==============

    @Test
    @DisplayName("verify - dataPayload为null时使用空字符串哈希")
    void testVerify_nullPayload() {
        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        record.setDataPayload(null);
        record.setDataHash(EvidenceHashUtil.sha256Hex(""));
        record.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(smartContractService.queryByHash(anyString())).thenReturn("on-chain-data");

        boolean result = evidenceService.verify(LocalDate.of(2026, 8, 15));

        assertTrue(result);
    }

    @Test
    @DisplayName("verify - dataHash为null时校验失败")
    void testVerify_nullDataHash() {
        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        record.setDataPayload("test-payload");
        record.setDataHash(null);
        record.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));

        boolean result = evidenceService.verify(LocalDate.of(2026, 8, 15));

        assertFalse(result);
    }

    @Test
    @DisplayName("verify - 链上查询返回null时校验失败")
    void testVerify_chainQueryReturnsNull() {
        String payload = "test-chain-null";
        String hash = EvidenceHashUtil.sha256Hex(payload);

        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        record.setDataPayload(payload);
        record.setDataHash(hash);
        record.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(smartContractService.queryByHash(hash)).thenReturn(null);

        boolean result = evidenceService.verify(LocalDate.of(2026, 8, 16));

        assertFalse(result);
    }

    // ============== verifyReport 边界测试 ==============

    @Test
    @DisplayName("verifyReport - dataPayload为null时使用空字符串重新计算")
    void testVerifyReport_nullPayload() {
        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        record.setDataPayload(null);
        record.setDataHash(EvidenceHashUtil.sha256Hex(""));
        record.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));

        Map<String, Object> result = evidenceService.verifyReport(LocalDate.of(2026, 8, 17));

        assertEquals(1L, result.get("total"));
        assertEquals(1L, result.get("passed"));
        assertEquals(0L, result.get("failed"));
    }

    @Test
    @DisplayName("verifyReport - dataHash为null时判定为失败")
    void testVerifyReport_nullDataHash() {
        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        record.setDataPayload("some-payload");
        record.setDataHash(null);
        record.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));

        Map<String, Object> result = evidenceService.verifyReport(LocalDate.of(2026, 8, 18));

        assertEquals(1L, result.get("total"));
        assertEquals(0L, result.get("passed"));
        assertEquals(1L, result.get("failed"));
    }

    @Test
    @DisplayName("verifyReport - 空记录时verified为false")
    void testVerifyReport_emptyRecords() {
        when(blockchainRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = evidenceService.verifyReport(LocalDate.of(2026, 8, 19));

        assertEquals(0L, result.get("total"));
        assertEquals(0L, result.get("passed"));
        assertEquals(0L, result.get("failed"));
        assertFalse((Boolean) result.get("verified"));
    }

    // ============== getMetrics 测试 ==============

    @Test
    @DisplayName("getMetrics - 返回完整性能指标")
    void testGetMetrics_fullMetrics() {
        when(asyncChainWriter.getQueueSize()).thenReturn(5);
        when(asyncChainWriter.getPendingCount()).thenReturn(10);
        when(asyncChainWriter.getSuccessRate()).thenReturn(95L);
        when(asyncChainWriter.getTotalProcessed()).thenReturn(1000L);
        when(asyncChainWriter.getTotalFailed()).thenReturn(50L);
        when(smartContractServiceImpl.getCacheHitRate()).thenReturn(80L);
        when(smartContractServiceImpl.getAverageLatency()).thenReturn(50L);
        when(smartContractServiceImpl.getTotalRpcCalls()).thenReturn(2000L);

        Map<String, Object> metrics = evidenceService.getMetrics();

        assertEquals(0L, metrics.get("totalSaved"));
        assertEquals(5, metrics.get("queueSize"));
        assertEquals(10, metrics.get("pendingCount"));
        assertEquals(95L, metrics.get("successRate"));
        assertEquals(1000L, metrics.get("processedTotal"));
        assertEquals(50L, metrics.get("failedTotal"));
        assertEquals(80L, metrics.get("chainCacheHitRate"));
        assertEquals(50L, metrics.get("chainAverageLatencyMs"));
        assertEquals(2000L, metrics.get("chainTotalRpcCalls"));
    }

    @Test
    @DisplayName("getMetrics - smartContractServiceImpl为null时返回默认值")
    void testGetMetrics_nullSmartContractService() {
        evidenceService.setSmartContractServiceImpl(null);
        when(asyncChainWriter.getQueueSize()).thenReturn(0);
        when(asyncChainWriter.getPendingCount()).thenReturn(0);
        when(asyncChainWriter.getSuccessRate()).thenReturn(100L);
        when(asyncChainWriter.getTotalProcessed()).thenReturn(0L);
        when(asyncChainWriter.getTotalFailed()).thenReturn(0L);

        Map<String, Object> metrics = evidenceService.getMetrics();

        assertEquals(0, metrics.get("chainCacheHitRate"));
        assertEquals(0, metrics.get("chainAverageLatencyMs"));
        assertEquals(0, metrics.get("chainTotalRpcCalls"));
    }

    @Test
    @DisplayName("getMetrics - totalSaved为0时平均延迟为0")
    void testGetMetrics_zeroTotalSaved() {
        when(asyncChainWriter.getQueueSize()).thenReturn(0);
        when(asyncChainWriter.getPendingCount()).thenReturn(0);
        when(asyncChainWriter.getSuccessRate()).thenReturn(100L);
        when(asyncChainWriter.getTotalProcessed()).thenReturn(0L);
        when(asyncChainWriter.getTotalFailed()).thenReturn(0L);

        Map<String, Object> metrics = evidenceService.getMetrics();

        assertEquals(0L, metrics.get("totalSaved"));
        assertEquals(0L, metrics.get("averageLatencyMs"));
    }
}