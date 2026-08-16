package com.lianshengtong.evidence.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EvidenceServiceImpl 附加覆盖率测试：补齐未覆盖的分支
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EvidenceServiceImpl 覆盖率补充测试")
class EvidenceServiceImplExtraTest {

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
        Mockito.reset(blockchainRecordMapper, dailySnapshotRecordMapper, evidenceFailoverMapper,
                smartContractService, smartContractServiceImpl, stringRedisTemplate, asyncChainWriter, valueOperations);
        evidenceService = new EvidenceServiceImpl(blockchainRecordMapper, dailySnapshotRecordMapper,
                evidenceFailoverMapper, smartContractService, asyncChainWriter, stringRedisTemplate);
        evidenceService.setSmartContractServiceImpl(smartContractServiceImpl);
        evidenceService.setAsyncEnabled(false);
        evidenceService.setBatchCount(3000);
        evidenceService.setMaxRetry(3);
        EvidenceCache cache = new EvidenceCaffeineCache(10000, 30_000L);
        ReflectionTestUtils.setField(evidenceService, "evidenceLocalCache", cache);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(blockchainRecordMapper.updateById(any(BlockchainRecord.class))).thenReturn(1);
        when(dailySnapshotRecordMapper.updateById(any(DailySnapshotRecord.class))).thenReturn(1);
    }

    // ============ query 分支补齐 ============

    @Test
    @DisplayName("query - 仅按 bizId 查询")
    void testQuery_ByBizIdOnly() {
        BlockchainRecord r = new BlockchainRecord();
        r.setId(1L);
        r.setBizType("ORDER");
        r.setBizId("ORD_ONLY");
        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r));

        List<BlockchainRecord> result = evidenceService.query(null, "ORD_ONLY");
        assertEquals(1, result.size());
        assertEquals("ORD_ONLY", result.get(0).getBizId());
    }

    @Test
    @DisplayName("query - 两个参数均为 null 时不过滤，返回全部记录")
    void testQuery_AllRecords() {
        BlockchainRecord r1 = new BlockchainRecord();
        r1.setId(1L);
        BlockchainRecord r2 = new BlockchainRecord();
        r2.setId(2L);
        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r1, r2));

        List<BlockchainRecord> result = evidenceService.query(null, null);
        assertEquals(2, result.size());
    }

    // ============ verify 分支补齐 ============

    @Test
    @DisplayName("verify - 链上查询返回 null 时校验失败")
    void testVerify_ChainResultNull() {
        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        record.setDataPayload("payload");
        record.setDataHash(EvidenceHashUtil.sha256Hex("payload"));
        record.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(smartContractService.queryByHash(anyString())).thenReturn(null);

        boolean result = evidenceService.verify(LocalDate.of(2026, 9, 1));
        assertFalse(result);
    }

    @Test
    @DisplayName("verify - 空记录列表时直接通过")
    void testVerify_EmptyList() {
        when(blockchainRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        boolean result = evidenceService.verify(LocalDate.of(2026, 9, 2));
        assertTrue(result);
    }

    @Test
    @DisplayName("verify - dataPayload 为 null 时使用空串哈希（NPE-fix）")
    void testVerify_NullDataPayload() {
        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        // dataPayload 为 null
        record.setDataPayload(null);
        record.setDataHash(EvidenceHashUtil.sha256Hex(""));
        record.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(smartContractService.queryByHash(anyString())).thenReturn("0xverified");

        boolean result = evidenceService.verify(LocalDate.of(2026, 9, 1));
        assertTrue(result, "null dataPayload 不应导致 NPE");
    }

    @Test
    @DisplayName("verify - dataHash 为 null 时校验失败（NPE-fix）")
    void testVerify_NullDataHash() {
        BlockchainRecord record = new BlockchainRecord();
        record.setId(1L);
        record.setDataPayload("payload");
        record.setDataHash(null);
        record.setStatus(1);

        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(record));

        boolean result = evidenceService.verify(LocalDate.of(2026, 9, 1));
        assertFalse(result, "null dataHash 应判定为校验失败");
    }

    // ============ verifyReport 补齐 ============

    @Test
    @DisplayName("verifyReport - 空记录时 total=0 verified=false")
    void testVerifyReport_EmptyList() {
        when(blockchainRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> report = evidenceService.verifyReport(LocalDate.of(2026, 9, 3));
        assertEquals(0L, report.get("total"));
        assertEquals(0L, report.get("passed"));
        assertEquals(0L, report.get("failed"));
        assertFalse((Boolean) report.get("verified"));
    }

    @Test
    @DisplayName("verifyReport - dataPayload 为 null 时使用空串哈希（NPE-fix）")
    void testVerifyReport_NullDataPayload() {
        BlockchainRecord r = new BlockchainRecord();
        r.setId(1L);
        r.setDataPayload(null);
        r.setDataHash(EvidenceHashUtil.sha256Hex(""));
        r.setStatus(1);
        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r));

        Map<String, Object> report = evidenceService.verifyReport(LocalDate.of(2026, 9, 3));
        assertEquals(1L, report.get("total"));
        assertEquals(1L, report.get("passed"));
        assertEquals(0L, report.get("failed"));
        assertTrue((Boolean) report.get("verified"));
    }

    @Test
    @DisplayName("verifyReport - dataHash 为 null 时判定为失败")
    void testVerifyReport_NullDataHash() {
        BlockchainRecord r = new BlockchainRecord();
        r.setId(1L);
        r.setDataPayload("payload");
        r.setDataHash(null);
        r.setStatus(1);
        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r));

        Map<String, Object> report = evidenceService.verifyReport(LocalDate.of(2026, 9, 3));
        assertEquals(1L, report.get("total"));
        assertEquals(0L, report.get("passed"));
        assertEquals(1L, report.get("failed"));
        assertFalse((Boolean) report.get("verified"));
    }

    // ============ getMetrics ============

    @Test
    @DisplayName("getMetrics - 返回全链路性能指标")
    void testGetMetrics_ReturnsAllMetrics() {
        when(asyncChainWriter.getQueueSize()).thenReturn(10);
        when(asyncChainWriter.getPendingCount()).thenReturn(5);
        when(asyncChainWriter.getSuccessRate()).thenReturn(95L);
        when(asyncChainWriter.getTotalProcessed()).thenReturn(200L);
        when(asyncChainWriter.getTotalFailed()).thenReturn(10L);
        when(smartContractServiceImpl.getCacheHitRate()).thenReturn(80L);
        when(smartContractServiceImpl.getAverageLatency()).thenReturn(120L);
        when(smartContractServiceImpl.getTotalRpcCalls()).thenReturn(500L);

        Map<String, Object> metrics = evidenceService.getMetrics();

        assertEquals(10, ((Number) metrics.get("queueSize")).intValue());
        assertEquals(5, ((Number) metrics.get("pendingCount")).intValue());
        assertEquals(95L, ((Number) metrics.get("successRate")).longValue());
        assertEquals(200L, ((Number) metrics.get("processedTotal")).longValue());
        assertEquals(10L, ((Number) metrics.get("failedTotal")).longValue());
        assertEquals(80L, ((Number) metrics.get("chainCacheHitRate")).longValue());
        assertEquals(120L, ((Number) metrics.get("chainAverageLatencyMs")).longValue());
        assertEquals(500L, ((Number) metrics.get("chainTotalRpcCalls")).longValue());
        assertNotNull(metrics.get("totalSaved"));
        assertNotNull(metrics.get("averageLatencyMs"));
    }

    @Test
    @DisplayName("getMetrics - totalSaved 为 0 时平均延迟为 0")
    void testGetMetrics_ZeroSaved() {
        when(asyncChainWriter.getQueueSize()).thenReturn(0);
        when(asyncChainWriter.getPendingCount()).thenReturn(0);
        when(asyncChainWriter.getSuccessRate()).thenReturn(100L);
        when(asyncChainWriter.getTotalProcessed()).thenReturn(0L);
        when(asyncChainWriter.getTotalFailed()).thenReturn(0L);
        when(smartContractServiceImpl.getCacheHitRate()).thenReturn(0L);
        when(smartContractServiceImpl.getAverageLatency()).thenReturn(0L);
        when(smartContractServiceImpl.getTotalRpcCalls()).thenReturn(0L);

        Map<String, Object> metrics = evidenceService.getMetrics();
        assertEquals(0L, metrics.get("averageLatencyMs"));
        assertEquals(0L, metrics.get("totalSaved"));
    }

    // ============ listPage 过滤补齐 ============

    @Test
    @DisplayName("listPage - 各过滤条件组合")
    void testListPage_WithAllFilters() {
        Page<BlockchainRecord> page = new Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(blockchainRecordMapper.selectPage(any(), any())).thenReturn(page);

        IPage<BlockchainRecord> result = evidenceService.listPage(1, 10,
                "BATCH001", "0xhash", "tx123", "2026-08-01", "2026-08-31");

        assertNotNull(result);
        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("listPage - 无过滤条件时返回默认页")
    void testListPage_NoFilters() {
        Page<BlockchainRecord> page = new Page<>(1, 20);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(blockchainRecordMapper.selectPage(any(), any())).thenReturn(page);

        IPage<BlockchainRecord> result = evidenceService.listPage(null, null, null, null, null, null, null);
        assertNotNull(result);
    }

    @Test
    @DisplayName("listPage - page 为 null 时默认第1页")
    void testListPage_DefaultPage() {
        Page<BlockchainRecord> page = new Page<>(1, 20);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(blockchainRecordMapper.selectPage(any(), any())).thenReturn(page);

        IPage<BlockchainRecord> result = evidenceService.listPage(null, 20, null, null, null, null, null);
        assertNotNull(result);
    }

    @Test
    @DisplayName("listPage - size 为 null 时默认20条")
    void testListPage_DefaultSize() {
        Page<BlockchainRecord> page = new Page<>(1, 20);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(blockchainRecordMapper.selectPage(any(), any())).thenReturn(page);

        IPage<BlockchainRecord> result = evidenceService.listPage(1, null, null, null, null, null, null);
        assertNotNull(result);
    }

    // ============ flushPending / chainWriteWithRetry 补齐 ============

    @Test
    @DisplayName("flushPending - 同步模式下 queryBlockNumberWithRetry 抛异常不影响结果")
    void testFlushPending_QueryBlockNumberExceptionIgnored() {
        BlockchainRecord r = new BlockchainRecord();
        r.setId(1L);
        r.setDataHash("h1");
        r.setBizType("ORDER");
        r.setBizId("ORD1");
        r.setStatus(0);
        r.setRetryCount(0);
        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r));
        when(smartContractService.writeHash("h1", "ORD1")).thenReturn("tx_ok");
        when(smartContractService.queryBlockNumberWithRetry("tx_ok", 3))
                .thenThrow(new RuntimeException("RPC 超时"));

        evidenceService.flushPending();

        ArgumentCaptor<BlockchainRecord> captor = ArgumentCaptor.forClass(BlockchainRecord.class);
        verify(blockchainRecordMapper).updateById(captor.capture());
        BlockchainRecord updated = captor.getValue();
        assertEquals(1, updated.getStatus());
        assertEquals("tx_ok", updated.getChainTxHash());
        assertNull(updated.getBlockNumber());
    }

    @Test
    @DisplayName("chainWriteWithRetry - record.retryCount>0 时从指定重试次数开始")
    void testChainWriteWithRetry_StartFromRetryCount() {
        BlockchainRecord r = new BlockchainRecord();
        r.setId(1L);
        r.setDataHash("h2");
        r.setBizType("ORDER");
        r.setBizId("ORD2");
        r.setStatus(0);
        r.setRetryCount(2); // 已失败2次
        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r));
        when(smartContractService.writeHash("h2", "ORD2")).thenReturn("tx_ok");
        when(smartContractService.queryBlockNumberWithRetry("tx_ok", 3)).thenReturn(99L);

        evidenceService.flushPending();

        verify(smartContractService).writeHash("h2", "ORD2");
    }

    @Test
    @DisplayName("flushPending - 批量数达上限触发 flush 后 count 重置")
    void testFlushPending_CountResetAfterFlush() {
        BlockchainRecord r = new BlockchainRecord();
        r.setId(1L);
        r.setDataHash("h3");
        r.setBizType("ORDER");
        r.setBizId("ORD3");
        r.setStatus(0);
        r.setRetryCount(0);
        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r));
        when(smartContractService.writeHash("h3", "ORD3")).thenReturn("tx_ok");
        when(smartContractService.queryBlockNumberWithRetry("tx_ok", 3)).thenReturn(1L);

        evidenceService.flushPending();

        verify(valueOperations).set(eq("lsc:evidence:pending-count"), eq("0"));
    }

    // ============ dailySnapshot 分支 ============

    @Test
    @DisplayName("dailySnapshot - 重试 sleep 被中断后 break 跳出循环，status 根据上次错误设置")
    void testDailySnapshot_InterruptedExceptionBreak() {
        BlockchainRecord r = new BlockchainRecord();
        r.setId(1L);
        r.setDataHash("h_int");
        r.setStatus(1);
        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r));
        when(dailySnapshotRecordMapper.insert(any(DailySnapshotRecord.class))).thenAnswer(inv -> {
            DailySnapshotRecord rec = inv.getArgument(0);
            rec.setId(1000L);
            return 1;
        });
        // 前两次均失败，第2次 sleep 时中断
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("失败1"))
                .thenThrow(new RuntimeException("失败2"));

        // 直接注入中断标志，令 sleep 线程感知中断
        Thread.currentThread().interrupt();
        try {
            LocalDate date = LocalDate.of(2026, 9, 10);
            DailySnapshotRecord result = evidenceService.dailySnapshot(date);
            // 中断后 break，lastError 保留为 "失败2"，status 设置为 2
            assertNotNull(result);
            assertEquals(2, result.getStatus());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("dailySnapshot - 重试全部3次失败后 status=2，remark 包含错误信息")
    void testDailySnapshot_AllRetriesFail_Status2() {
        BlockchainRecord r = new BlockchainRecord();
        r.setId(1L);
        r.setDataHash("h_fail_all");
        r.setStatus(1);
        when(blockchainRecordMapper.selectList(any())).thenReturn(List.of(r));
        when(dailySnapshotRecordMapper.insert(any(DailySnapshotRecord.class))).thenAnswer(inv -> {
            DailySnapshotRecord rec = inv.getArgument(0);
            rec.setId(1001L);
            return 1;
        });
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("链上不可用"));

        // 不中断，让 sleep 走完所有重试（3次）
        // 由于 sleep(500) 在测试中会实际耗时，使用较短的超时
        LocalDate date = LocalDate.of(2026, 9, 11);
        DailySnapshotRecord result = evidenceService.dailySnapshot(date);
        assertEquals(2, result.getStatus());
        assertNotNull(result.getRemark());
        assertTrue(result.getRemark().contains("链上不可用"));
    }

    // ============ failoverScan 分支 ============

    @Test
    @DisplayName("failoverScan - 所有 recordId 均为空时跳过 selectBatchIds")
    void testFailoverScan_AllRecordIdsNull() throws Exception {
        EvidenceFailover f = new EvidenceFailover();
        f.setId(1L);
        f.setBlockchainRecordId(null);
        f.setBizType("ORDER");
        f.setBizId("ORD_NULL");
        f.setDataHash("h_null");
        f.setStatus(0);
        f.setNextRetryAt(LocalDateTime.now().minusMinutes(10));
        when(evidenceFailoverMapper.selectList(any())).thenReturn(List.of(f));

        Method m = EvidenceServiceImpl.class.getDeclaredMethod("failoverScan");
        m.setAccessible(true);
        m.invoke(evidenceService);

        verify(blockchainRecordMapper, never()).selectBatchIds(anyList());
        verify(smartContractService, never()).writeHash(anyString(), anyString());
    }

    @Test
    @DisplayName("failoverScan - writeHash 失败时 retryCount+1，status 保持 0 继续重试")
    void testFailoverScan_WriteHashFails_RecordStatus2() throws Exception {
        EvidenceFailover f = new EvidenceFailover();
        f.setId(2L);
        f.setBlockchainRecordId(200L);
        f.setBizType("ORDER");
        f.setBizId("ORD2");
        f.setDataHash("h2");
        f.setStatus(0);
        f.setRetryCount(0);
        f.setNextRetryAt(LocalDateTime.now().minusMinutes(10));

        BlockchainRecord r = new BlockchainRecord();
        r.setId(200L);
        when(evidenceFailoverMapper.selectList(any())).thenReturn(List.of(f));
        when(blockchainRecordMapper.selectBatchIds(any())).thenReturn(List.of(r));
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("链上服务故障"));

        Method m = EvidenceServiceImpl.class.getDeclaredMethod("failoverScan");
        m.setAccessible(true);
        m.invoke(evidenceService);

        ArgumentCaptor<EvidenceFailover> captor = ArgumentCaptor.forClass(EvidenceFailover.class);
        verify(evidenceFailoverMapper).updateById(captor.capture());
        EvidenceFailover updated = captor.getValue();
        assertEquals(1, updated.getRetryCount());
        // B4-fix: retryCount < MAX_FAILOVER_RETRY(10) 时保持 status=0 继续重试
        assertEquals(0, updated.getStatus());
        assertNotNull(updated.getNextRetryAt());
        assertTrue(updated.getNextRetryAt().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("failoverScan - 空列表直接返回")
    void testFailoverScan_EmptyList_ImmediateReturn() throws Exception {
        when(evidenceFailoverMapper.selectList(any())).thenReturn(Collections.emptyList());

        Method m = EvidenceServiceImpl.class.getDeclaredMethod("failoverScan");
        m.setAccessible(true);
        m.invoke(evidenceService);

        verify(blockchainRecordMapper, never()).selectBatchIds(any());
        verify(smartContractService, never()).writeHash(anyString(), anyString());
    }

    @Test
    @DisplayName("failoverScan - 部分 recordId 为 null 过滤后 selectBatchIds 仅查有效ID")
    void testFailoverScan_PartialNullRecordIds() throws Exception {
        EvidenceFailover f1 = new EvidenceFailover();
        f1.setId(1L);
        f1.setBlockchainRecordId(null);
        f1.setBizType("ORDER");
        f1.setBizId("ORD_NULL");
        f1.setDataHash("h_null");
        f1.setStatus(0);
        f1.setNextRetryAt(LocalDateTime.now().minusMinutes(10));

        EvidenceFailover f2 = new EvidenceFailover();
        f2.setId(2L);
        f2.setBlockchainRecordId(300L);
        f2.setBizType("ORDER");
        f2.setBizId("ORD300");
        f2.setDataHash("h300");
        f2.setStatus(0);
        f2.setNextRetryAt(LocalDateTime.now().minusMinutes(10));

        BlockchainRecord r = new BlockchainRecord();
        r.setId(300L);
        when(evidenceFailoverMapper.selectList(any())).thenReturn(List.of(f1, f2));
        when(blockchainRecordMapper.selectBatchIds(argThat(ids -> ids != null && ids.size() == 1 && ids.contains(300L))))
                .thenReturn(List.of(r));
        when(smartContractService.writeHash("h300", "ORD300")).thenReturn("tx_partial");

        Method m = EvidenceServiceImpl.class.getDeclaredMethod("failoverScan");
        m.setAccessible(true);
        m.invoke(evidenceService);

        verify(blockchainRecordMapper).selectBatchIds(anyList());
        verify(smartContractService).writeHash("h300", "ORD300");
    }

    // ============ 其他补齐 ============

    @Test
    @DisplayName("snapshotCompensation - 多条混合成功失败后状态正确")
    void testSnapshotCompensation_MixedSuccessFailure() {
        DailySnapshotRecord s1 = new DailySnapshotRecord();
        s1.setId(1L);
        s1.setSnapshotDate(LocalDate.of(2026, 9, 1));
        s1.setStatus(2);
        s1.setMerkleRoot("m1");

        DailySnapshotRecord s2 = new DailySnapshotRecord();
        s2.setId(2L);
        s2.setSnapshotDate(LocalDate.of(2026, 9, 2));
        s2.setStatus(0);
        s2.setMerkleRoot("m2");

        when(dailySnapshotRecordMapper.selectList(any())).thenReturn(List.of(s1, s2));
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenReturn("tx_s1")
                .thenThrow(new RuntimeException("链上异常"));

        evidenceService.snapshotCompensation();

        verify(dailySnapshotRecordMapper, times(2)).updateById(any(DailySnapshotRecord.class));
    }

    @Test
    @DisplayName("snapshotCompensation - 状态为 2 的快照被补偿成功")
    void testSnapshotCompensation_Status2_Compensated() {
        DailySnapshotRecord s = new DailySnapshotRecord();
        s.setId(3L);
        s.setSnapshotDate(LocalDate.of(2026, 9, 3));
        s.setStatus(2);
        s.setMerkleRoot("m3");
        when(dailySnapshotRecordMapper.selectList(any())).thenReturn(List.of(s));
        when(smartContractService.writeHash(eq("m3"), anyString())).thenReturn("tx_ok");

        evidenceService.snapshotCompensation();

        verify(dailySnapshotRecordMapper).updateById(argThat(r ->
                r.getId().equals(3L) && r.getStatus() == 1));
    }

    @Test
    @DisplayName("snapshotCompensation - 补偿全部失败时状态保持 2")
    void testSnapshotCompensation_AllFail_Status2() {
        DailySnapshotRecord s = new DailySnapshotRecord();
        s.setId(4L);
        s.setSnapshotDate(LocalDate.of(2026, 9, 4));
        s.setStatus(0);
        s.setMerkleRoot("m4");
        when(dailySnapshotRecordMapper.selectList(any())).thenReturn(List.of(s));
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("链上不可用"));

        evidenceService.snapshotCompensation();

        verify(dailySnapshotRecordMapper).updateById(argThat(r ->
                r.getId().equals(4L) && r.getStatus() == 2));
    }

    // ============ saveEvidence 边界 ============

    @Test
    @DisplayName("saveEvidence - payload 为空字符串仍能生成哈希")
    void testSaveEvidence_EmptyPayload() {
        when(blockchainRecordMapper.insert(any(BlockchainRecord.class))).thenAnswer(inv -> {
            BlockchainRecord rec = inv.getArgument(0);
            rec.setId(11L);
            return 1;
        });
        when(valueOperations.increment(anyString())).thenReturn(0L);

        String result = evidenceService.saveEvidence("ORDER", "ORD_EMPTY", null, "");
        assertEquals("11", result);
        verify(blockchainRecordMapper).insert(argThat(rec ->
                rec.getDataHash() != null && !rec.getDataHash().isEmpty()));
    }

    @Test
    @DisplayName("saveEvidence - dataHash 为空字符串时自动生成哈希")
    void testSaveEvidence_BlankDataHash() {
        when(blockchainRecordMapper.insert(any(BlockchainRecord.class))).thenAnswer(inv -> {
            BlockchainRecord rec = inv.getArgument(0);
            rec.setId(12L);
            return 1;
        });
        when(valueOperations.increment(anyString())).thenReturn(0L);

        String result = evidenceService.saveEvidence("ORDER", "ORD_BLANK", "   ", "payload");
        assertEquals("12", result);
        verify(blockchainRecordMapper).insert(argThat(rec ->
                rec.getDataHash() != null && !rec.getDataHash().isEmpty()));
    }

    // ============ scheduledFlush ============

    @Test
    @DisplayName("scheduledFlush - 定时触发批量上链")
    void testScheduledFlush_DirectCall() {
        evidenceService.scheduledFlush();
        // 不抛异常即可
    }
}
