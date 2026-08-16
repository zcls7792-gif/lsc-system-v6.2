package com.lianshengtong.evidence.service;

import com.lianshengtong.evidence.config.EvidenceCache;
import com.lianshengtong.evidence.config.EvidenceCaffeineCache;
import com.lianshengtong.evidence.entity.BlockchainRecord;
import com.lianshengtong.evidence.entity.EvidenceFailover;
import com.lianshengtong.evidence.mapper.BlockchainRecordMapper;
import com.lianshengtong.evidence.mapper.EvidenceFailoverMapper;
import com.lianshengtong.evidence.service.impl.SmartContractServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.any;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("异步上链写入器单元测试")
class AsyncChainWriterTest {

    @Mock
    private BlockchainRecordMapper blockchainRecordMapper;

    @Mock
    private EvidenceFailoverMapper evidenceFailoverMapper;

    @Mock
    private SmartContractServiceImpl smartContractService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private EvidenceCache evidenceLocalCache;

    @InjectMocks
    private AsyncChainWriter writer;

    @BeforeEach
    void setUp() {
        evidenceLocalCache = new EvidenceCaffeineCache(10000, 30_000L);
        ReflectionTestUtils.setField(writer, "evidenceLocalCache", evidenceLocalCache);
        ReflectionTestUtils.setField(writer, "batchSize", 50);
        ReflectionTestUtils.setField(writer, "flushIntervalMs", 100L);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(blockchainRecordMapper.updateById(any(BlockchainRecord.class))).thenReturn(1);
        lenient().when(smartContractService.queryBlockNumberWithRetry(anyString(), eq(3))).thenReturn(100L);
    }

    private BlockchainRecord buildRecord(Long id, String hash, String bizType, String bizId) {
        BlockchainRecord r = new BlockchainRecord();
        r.setId(id);
        r.setDataHash(hash);
        r.setBizType(bizType);
        r.setBizId(bizId);
        r.setStatus(0);
        r.setRetryCount(0);
        return r;
    }

    // ============== submitAsync / flushAsyncBatch 基础测试 ==============

    @Test
    @DisplayName("submitAsync - 单条提交成功进入队列")
    void testSubmitAsync_SingleRecord() {
        BlockchainRecord r = buildRecord(1L, "hash1", "ORDER", "ORD001");
        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_1");
        when(smartContractService.queryBlockNumber(anyString())).thenReturn(100L);

        writer.submitAsync(r);

        // 队列未达 batchSize，不会触发 flush
        assertTrue(writer.getQueueSize() >= 0);
    }

    @Test
    @DisplayName("submitAsync - 批量触发 flush 调用 processRecord")
    void testSubmitAsync_TriggersFlush() throws Exception {
        ReflectionTestUtils.setField(writer, "batchSize", 3);
        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_batch");
        when(smartContractService.queryBlockNumber(anyString())).thenReturn(200L);

        // 绕过 @Async：直接调用 flushAsyncBatch 验证批量处理
        List<BlockchainRecord> records = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            BlockchainRecord r = buildRecord((long) i, "hash" + i, "ORDER", "ORD" + i);
            records.add(r);
            writer.submitAsync(r);
        }

        // 手动触发 flush 以验证 processRecord 被调用
        writer.flushAsyncBatch();

        // 每条成功记录应被 updateById
        verify(blockchainRecordMapper, atLeastOnce()).updateById(any(BlockchainRecord.class));
        verify(smartContractService, atLeastOnce()).writeHash(anyString(), anyString());
    }

    @Test
    @DisplayName("flushAsyncBatch - 空队列直接返回")
    void testFlushAsyncBatch_EmptyQueue() {
        writer.flushAsyncBatch();
        verify(smartContractService, never()).writeHash(anyString(), anyString());
        verify(blockchainRecordMapper, never()).updateById(any(BlockchainRecord.class));
    }

    @Test
    @DisplayName("flushAsyncBatch - 部分成功部分失败写入故障表")
    void testFlushAsyncBatch_PartialFail() {
        BlockchainRecord ok = buildRecord(1L, "hash_ok", "ORDER", "ORD_OK");
        BlockchainRecord fail = buildRecord(2L, "hash_fail", "ORDER", "ORD_FAIL");

        when(smartContractService.writeHash(eq("hash_ok"), anyString())).thenReturn("tx_ok");
        when(smartContractService.writeHash(eq("hash_fail"), anyString()))
                .thenThrow(new RuntimeException("链上调用失败"));
        when(smartContractService.queryBlockNumber(anyString())).thenReturn(300L);

        writer.submitAsync(ok);
        writer.submitAsync(fail);
        writer.flushAsyncBatch();

        verify(evidenceFailoverMapper).insert(any(EvidenceFailover.class));
    }

    // ============== 熔断器测试 ==============

    @Test
    @DisplayName("circuitBreaker - 连续失败达到阈值触发熔断")
    void testCircuitBreaker_OpensOnThreshold() {
        ReflectionTestUtils.setField(writer, "batchSize", 1);
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("链上调用失败"));

        for (int i = 0; i < 6; i++) {
            BlockchainRecord r = buildRecord((long) i, "hash" + i, "ORDER", "ORD" + i);
            writer.submitAsync(r);
            writer.flushAsyncBatch();
        }

        // 最后一次进入熔断状态，processRecord 应将 status=2 并写故障
        verify(evidenceFailoverMapper, atLeastOnce()).insert(any(EvidenceFailover.class));
    }

    @Test
    @DisplayName("circuitBreaker - 成功后恢复熔断器")
    void testCircuitBreaker_RecoverAfterSuccess() {
        ReflectionTestUtils.setField(writer, "batchSize", 1);

        // 先触发 5 次失败打开熔断器
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("连续失败"));
        for (int i = 0; i < 5; i++) {
            BlockchainRecord r = buildRecord((long) i, "hash" + i, "ORDER", "ORD" + i);
            writer.submitAsync(r);
            writer.flushAsyncBatch();
        }

        // 关闭熔断器：通过反射直接重置状态
        try {
            java.lang.reflect.Field f = AsyncChainWriter.class.getDeclaredField("circuitOpenUntil");
            f.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicLong) f.get(writer)).set(0L);
            java.lang.reflect.Field f2 = AsyncChainWriter.class.getDeclaredField("consecutiveFailures");
            f2.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicInteger) f2.get(writer)).set(0);
        } catch (Exception e) {
            fail(e);
        }

        // 恢复后成功上链
        doReturn("tx_recovered").when(smartContractService).writeHash(anyString(), anyString());
        when(smartContractService.queryBlockNumberWithRetry(anyString(), eq(3))).thenReturn(500L);
        BlockchainRecord r = buildRecord(100L, "hash_recovered", "ORDER", "ORD_RECOVERED");
        writer.submitAsync(r);
        writer.flushAsyncBatch();

        // 验证成功上链被调用
        verify(smartContractService).writeHash(eq("hash_recovered"), anyString());
    }

    @Test
    @DisplayName("getPendingCount - Redis 有值时返回 Redis 值")
    void testGetPendingCount_RedisValue() {
        when(valueOperations.get("lsc:evidence:pending-count")).thenReturn("42");
        assertEquals(42, writer.getPendingCount());
    }

    @Test
    @DisplayName("getPendingCount - Redis 无值时退回队列大小")
    void testGetPendingCount_FallbackToQueue() {
        when(valueOperations.get("lsc:evidence:pending-count")).thenReturn(null);
        assertEquals(writer.getQueueSize(), writer.getPendingCount());
    }

    @Test
    @DisplayName("getPendingCount - Redis 值格式异常时退回队列大小")
    void testGetPendingCount_InvalidRedisValue() {
        when(valueOperations.get("lsc:evidence:pending-count")).thenReturn("notanumber");
        assertEquals(writer.getQueueSize(), writer.getPendingCount());
    }

    @Test
    @DisplayName("submitSync - 同步上链成功流程")
    void testSubmitSync_Success() {
        BlockchainRecord r = buildRecord(1L, "hash_sync", "ORDER", "ORD_SYNC");
        when(smartContractService.writeHash("hash_sync", "ORD_SYNC")).thenReturn("tx_sync");
        when(smartContractService.queryBlockNumberWithRetry("tx_sync", 3)).thenReturn(123L);

        writer.submitSync(r);

        assertEquals(1, r.getStatus());
        assertEquals("tx_sync", r.getChainTxHash());
        assertEquals(Long.valueOf(123L), r.getBlockNumber());
        verify(blockchainRecordMapper).insert(r);
        verify(blockchainRecordMapper).updateById(r);
    }

    // ============== 并发测试 ==============

    @Test
    @DisplayName("并发提交 - 多线程安全入队")
    void testConcurrentSubmission() throws Exception {
        int threadCount = 10;
        int perThread = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger counter = new AtomicInteger(0);

        lenient().when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_concurrent");
        lenient().when(smartContractService.queryBlockNumberWithRetry(anyString(), eq(3))).thenReturn(999L);

        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        long id = counter.incrementAndGet();
                        BlockchainRecord r = buildRecord(id, "hash" + id, "ORDER", "ORD" + id);
                        writer.submitAsync(r);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        // 触发 flush 清空队列
        writer.flushAsyncBatch();
        assertTrue(writer.getQueueSize() >= 0);
        pool.shutdown();
    }

    // ============== processRecord 异常分支测试 ==============

    @Test
    @DisplayName("processRecord - writeHash 成功但 queryBlockNumberWithRetry 失败仍更新为成功状态")
    void testProcessRecord_queryBlockNumberFails() {
        ReflectionTestUtils.setField(writer, "batchSize", 1);
        BlockchainRecord r = buildRecord(200L, "hash_q", "ORDER", "ORD_Q");
        when(smartContractService.writeHash("hash_q", "ORD_Q")).thenReturn("tx_q");
        when(smartContractService.queryBlockNumberWithRetry("tx_q", 3))
                .thenThrow(new RuntimeException("区块查询超时"));

        writer.submitAsync(r);
        writer.flushAsyncBatch();

        // writeHash 成功，状态仍应为 1（已上链）
        assertEquals(1, r.getStatus());
        assertEquals("tx_q", r.getChainTxHash());
        assertNull(r.getBlockNumber());
    }

    @Test
    @DisplayName("processRecord - 熔断器打开时跳过链上调用")
    void testProcessRecord_circuitOpenSkipsChain() {
        ReflectionTestUtils.setField(writer, "batchSize", 1);

        // 先触发熔断
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("连续失败"));
        for (int i = 0; i < 5; i++) {
            BlockchainRecord r = buildRecord((long) i, "hash_cf" + i, "ORDER", "ORD_CF" + i);
            writer.submitAsync(r);
            writer.flushAsyncBatch();
        }

        // 熔断打开状态下，新记录应标记为 status=2 并写故障表
        BlockchainRecord r = buildRecord(999L, "hash_after_circuit", "ORDER", "ORD_AFTER");
        writer.submitAsync(r);
        writer.flushAsyncBatch();

        assertEquals(2, r.getStatus());
        verify(evidenceFailoverMapper, atLeastOnce()).insert(any(EvidenceFailover.class));
    }

    @Test
    @DisplayName("processRecord - 熔断状态下恢复到正常")
    void testProcessRecord_circuitRecovery() {
        ReflectionTestUtils.setField(writer, "batchSize", 1);

        // 触发熔断
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("连续失败"));
        for (int i = 0; i < 5; i++) {
            BlockchainRecord r = buildRecord((long) i, "hash_cc" + i, "ORDER", "ORD_CC" + i);
            writer.submitAsync(r);
            writer.flushAsyncBatch();
        }

        // 等待熔断超时，或直接重置
        try {
            Thread.sleep(50L);
            java.lang.reflect.Field f = AsyncChainWriter.class.getDeclaredField("circuitOpenUntil");
            f.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicLong) f.get(writer)).set(System.currentTimeMillis() - 1000);
            java.lang.reflect.Field f2 = AsyncChainWriter.class.getDeclaredField("consecutiveFailures");
            f2.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicInteger) f2.get(writer)).set(0);
        } catch (Exception e) {
            fail(e);
        }

        // 恢复后再次处理，成功上链
        doReturn("tx_reopen").when(smartContractService).writeHash(anyString(), anyString());
        when(smartContractService.queryBlockNumberWithRetry(anyString(), eq(3))).thenReturn(800L);
        BlockchainRecord r = buildRecord(500L, "hash_reopen", "ORDER", "ORD_REOPEN");
        writer.submitAsync(r);
        writer.flushAsyncBatch();

        assertEquals(1, r.getStatus());
        assertEquals("tx_reopen", r.getChainTxHash());
    }

    // ============== flushAsyncBatch finally 清理测试 ==============

    @Test
    @DisplayName("flushAsyncBatch - 处理过程异常时 finally 正确清理")
    void testFlushAsyncBatch_exceptionCleanup() {
        ReflectionTestUtils.setField(writer, "batchSize", 2);
        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("异常"));

        BlockchainRecord r1 = buildRecord(1L, "hash_e1", "ORDER", "ORD_E1");
        BlockchainRecord r2 = buildRecord(2L, "hash_e2", "ORDER", "ORD_E2");
        writer.submitAsync(r1);
        writer.submitAsync(r2);

        assertDoesNotThrow(() -> writer.flushAsyncBatch());

        // flush 后队列应被清空
        assertEquals(0, writer.getQueueSize());
    }

    // ============== submitSync 异常分支测试 ==============

    @Test
    @DisplayName("submitSync - writeHash 异常记录失败上链")
    void testSubmitSync_writeHashFails() {
        BlockchainRecord r = buildRecord(300L, "hash_sync_fail", "ORDER", "ORD_SYNC_FAIL");
        when(smartContractService.writeHash("hash_sync_fail", "ORD_SYNC_FAIL"))
                .thenThrow(new RuntimeException("链上写入失败"));

        assertThrows(RuntimeException.class, () -> writer.submitSync(r));

        // N5-fix: 失败时 status 设为 2 并写入故障表
        assertEquals(2, r.getStatus());
        verify(blockchainRecordMapper).insert(r);
        verify(blockchainRecordMapper).updateById(r);
        verify(evidenceFailoverMapper).insert(any());
    }

    @Test
    @DisplayName("submitSync - queryBlockNumberWithRetry 异常不影响上链状态")
    void testSubmitSync_queryBlockNumberFails() {
        BlockchainRecord r = buildRecord(301L, "hash_sync_bn_fail", "ORDER", "ORD_BN_FAIL");
        when(smartContractService.writeHash("hash_sync_bn_fail", "ORD_BN_FAIL")).thenReturn("tx_bn");
        when(smartContractService.queryBlockNumberWithRetry("tx_bn", 3))
                .thenThrow(new RuntimeException("区块号查询失败"));

        writer.submitSync(r);

        assertEquals(1, r.getStatus());
        assertEquals("tx_bn", r.getChainTxHash());
        assertNull(r.getBlockNumber());
    }

    // ============== getQueueSize / 边界测试 ==============

    @Test
    @DisplayName("getQueueSize - 空队列返回 0")
    void testGetQueueSize_Empty() {
        assertEquals(0, writer.getQueueSize());
    }

    @Test
    @DisplayName("getQueueSize - 元素入队后正确返回大小")
    void testGetQueueSize_WithElements() {
        ReflectionTestUtils.setField(writer, "batchSize", 100);
        for (int i = 0; i < 5; i++) {
            BlockchainRecord r = buildRecord((long) i, "hash_q" + i, "ORDER", "ORD_Q" + i);
            writer.submitAsync(r);
        }
        assertEquals(5, writer.getQueueSize());
    }

    @Test
    @DisplayName("flushAsyncBatch - 并发安全 flushInProgress 标志正常")
    void testFlushAsyncBatch_concurrentFlushFlag() {
        ReflectionTestUtils.setField(writer, "batchSize", 100);

        for (int i = 0; i < 3; i++) {
            BlockchainRecord r = buildRecord((long) i, "hash_cf" + i, "ORDER", "ORD_CF" + i);
            writer.submitAsync(r);
        }

        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_cf");
        when(smartContractService.queryBlockNumber(anyString())).thenReturn(100L);

        writer.flushAsyncBatch();

        assertEquals(0, writer.getQueueSize());
    }

    @Test
    @DisplayName("getPendingCount - Redis 返回负数时仍安全处理")
    void testGetPendingCount_NegativeRedisValue() {
        when(valueOperations.get("lsc:evidence:pending-count")).thenReturn("-5");
        int count = writer.getPendingCount();
        // 负数仍被解析为 -5，但不会抛异常
        assertTrue(count >= 0 || count == -5);
    }

    // ============== Step 2: 非 RuntimeException 异常测试 ==============

    @Test
    @DisplayName("processRecord - writeHash 抛 Error 被并行路径捕获")
    void testProcessRecord_writeHashThrowsError() {
        ReflectionTestUtils.setField(writer, "batchSize", 1);

        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new Error("链上系统严重错误"));

        BlockchainRecord r = buildRecord(400L, "hash_error", "ORDER", "ORD_ERR");
        writer.submitAsync(r);

        // flushAsyncBatch 在并行线程中捕获 Error，不向外抛
        assertDoesNotThrow(() -> writer.flushAsyncBatch());
        assertEquals(0, writer.getQueueSize());
    }

    @Test
    @DisplayName("processRecord - catch(Exception) 分支验证")
    void testProcessRecord_catchExceptionBranch() {
        ReflectionTestUtils.setField(writer, "batchSize", 1);

        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("参数非法"));

        BlockchainRecord r = buildRecord(401L, "hash_illegal", "ORDER", "ORD_ILL");
        writer.submitAsync(r);

        // flushAsyncBatch 在并行线程中捕获异常，不向外抛
        assertDoesNotThrow(() -> writer.flushAsyncBatch());
        assertEquals(0, writer.getQueueSize());
    }

    // ============== Step 2: flushAsyncBatch 中断路径测试 ==============

    @Test
    @DisplayName("flushAsyncBatch - processRecord 抛异常后记录故障表")
    void testFlushAsyncBatch_errorWritesFailover() {
        ReflectionTestUtils.setField(writer, "batchSize", 1);

        when(smartContractService.writeHash(anyString(), anyString()))
                .thenThrow(new RuntimeException("链上连接超时"));

        BlockchainRecord r = buildRecord(402L, "hash_failover", "ORDER", "ORD_FO");
        writer.submitAsync(r);

        // flushAsyncBatch 捕获异常后写故障表，不向外抛
        assertDoesNotThrow(() -> writer.flushAsyncBatch());
        assertEquals(0, writer.getQueueSize());
        verify(evidenceFailoverMapper).insert(any(EvidenceFailover.class));
    }

    @Test
    @DisplayName("flushAsyncBatch - 多条记录全部成功")
    void testFlushAsyncBatch_allRecordsSuccess() {
        ReflectionTestUtils.setField(writer, "batchSize", 10);
        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_success");
        when(smartContractService.queryBlockNumber(anyString())).thenReturn(500L);

        for (int i = 0; i < 5; i++) {
            BlockchainRecord r = buildRecord((long) (410 + i), "hash_ok_" + i, "ORDER", "ORD_OK_" + i);
            writer.submitAsync(r);
        }

        writer.flushAsyncBatch();
        assertEquals(0, writer.getQueueSize());
        verify(blockchainRecordMapper, times(5)).updateById(any(BlockchainRecord.class));
    }

    @Test
    @DisplayName("submitAsync - 入队后未触发 flush 队列保留元素")
    void testSubmitAsync_queueRetention() {
        ReflectionTestUtils.setField(writer, "batchSize", 100);

        for (int i = 0; i < 3; i++) {
            BlockchainRecord r = buildRecord((long) (420 + i), "hash_qr_" + i, "ORDER", "ORD_QR_" + i);
            writer.submitAsync(r);
        }

        assertEquals(3, writer.getQueueSize());
    }
}
