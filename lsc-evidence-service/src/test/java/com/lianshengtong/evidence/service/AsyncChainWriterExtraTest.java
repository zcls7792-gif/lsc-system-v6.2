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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AsyncChainWriter 附加覆盖率测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("异步上链写入器覆盖率补充测试")
class AsyncChainWriterExtraTest {

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
        lenient().when(smartContractService.queryBlockNumber(anyString())).thenReturn(100L);
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

    // ============ submitSync 缓存命中 ============

    @Test
    @DisplayName("submitSync - 本地缓存已存在且状态=1 时直接标记为成功")
    void testSubmitSync_CacheHit() {
        BlockchainRecord r = buildRecord(1L, "hash_cached", "ORDER", "ORD_CACHED");
        r.setId(1L);
        evidenceLocalCache.put("record:1", 1, 60_000L);

        writer.submitSync(r);

        assertEquals(1, r.getStatus());
        // B13-fix: 缓存命中时不再构造伪 txHash
        assertNull(r.getChainTxHash());
        // 缓存命中时不应调用 writeHash
        verify(smartContractService, never()).writeHash(anyString(), anyString());
    }

    // ============ flushAsyncBatch 熔断器打开降级 ============

    @Test
    @DisplayName("flushAsyncBatch - 熔断器打开时批量降级写入故障表")
    void testFlushAsyncBatch_CircuitOpenDegrade() {
        ReflectionTestUtils.setField(writer, "batchSize", 2);

        // 触发熔断打开（通过反射直接设置状态）
        try {
            java.lang.reflect.Field f = AsyncChainWriter.class.getDeclaredField("circuitOpenUntil");
            f.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicLong) f.get(writer))
                    .set(System.currentTimeMillis() + 60_000L);
            java.lang.reflect.Field f2 = AsyncChainWriter.class.getDeclaredField("consecutiveFailures");
            f2.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicInteger) f2.get(writer)).set(5);
        } catch (Exception e) {
            fail(e);
        }

        BlockchainRecord r1 = buildRecord(1L, "h1", "ORDER", "ORD1");
        BlockchainRecord r2 = buildRecord(2L, "h2", "ORDER", "ORD2");
        writer.submitAsync(r1);
        writer.submitAsync(r2);

        writer.flushAsyncBatch();

        assertEquals(2, r1.getStatus());
        assertEquals(2, r2.getStatus());
        verify(evidenceFailoverMapper, times(2)).insert(any(EvidenceFailover.class));
    }

    // ============ flushAsyncBatch semaphore 获取失败串行降级 ============

    @Test
    @DisplayName("flushAsyncBatch - 信号量获取失败时串行降级处理")
    void testFlushAsyncBatch_SemaphoreFail_Sequential() {
        ReflectionTestUtils.setField(writer, "batchSize", 3);

        // 通过 setMaxConcurrent(0) 让信号量许可为 0，使 tryAcquire 失败
        writer.setMaxConcurrent(0);

        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_seq");

        BlockchainRecord r1 = buildRecord(1L, "h1", "ORDER", "ORD1");
        BlockchainRecord r2 = buildRecord(2L, "h2", "ORDER", "ORD2");
        writer.submitAsync(r1);
        writer.submitAsync(r2);

        writer.flushAsyncBatch();

        assertEquals(1, r1.getStatus());
        assertEquals(1, r2.getStatus());
    }

    // ============ flushAsyncBatch 并行处理 ============

    @Test
    @DisplayName("flushAsyncBatch - 使用 TaskExecutor 并行处理成功")
    void testFlushAsyncBatch_WithExecutor() throws Exception {
        ReflectionTestUtils.setField(writer, "batchSize", 2);

        // C2-fix: 使用 TaskExecutor 代替 ExecutorService
        org.springframework.core.task.TaskExecutor executor = mock(org.springframework.core.task.TaskExecutor.class);
        doAnswer(inv -> {
            Runnable r = inv.getArgument(0);
            r.run(); // 同步执行
            return null;
        }).when(executor).execute(any(Runnable.class));
        ReflectionTestUtils.setField(writer, "evidenceExecutor", executor);

        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_par");

        BlockchainRecord r1 = buildRecord(1L, "h1", "ORDER", "ORD1");
        BlockchainRecord r2 = buildRecord(2L, "h2", "ORDER", "ORD2");
        writer.submitAsync(r1);
        writer.submitAsync(r2);

        writer.flushAsyncBatch();

        assertEquals(1, r1.getStatus());
        assertEquals(1, r2.getStatus());
    }

    @Test
    @DisplayName("flushAsyncBatch - 无线程池时使用单线程串行执行 Callable")
    void testFlushAsyncBatch_NoExecutor_SingleThread() {
        ReflectionTestUtils.setField(writer, "batchSize", 2);
        ReflectionTestUtils.setField(writer, "evidenceExecutor", null);

        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_noexec");

        BlockchainRecord r1 = buildRecord(1L, "h1", "ORDER", "ORD1");
        BlockchainRecord r2 = buildRecord(2L, "h2", "ORDER", "ORD2");
        writer.submitAsync(r1);
        writer.submitAsync(r2);

        writer.flushAsyncBatch();

        assertEquals(1, r1.getStatus());
        assertEquals(1, r2.getStatus());
    }

    // ============ processRecord 缓存命中 ============

    @Test
    @DisplayName("processRecord - 链上状态缓存命中直接标记为成功")
    void testProcessRecord_StatusCacheHit() {
        ReflectionTestUtils.setField(writer, "batchSize", 2);

        BlockchainRecord r = buildRecord(1L, "h_cache", "ORDER", "ORD_CACHE");
        r.setId(1L);
        evidenceLocalCache.put("record:1", 1, 60_000L);

        writer.submitAsync(r);
        writer.flushAsyncBatch();

        assertEquals(1, r.getStatus());
        // B13-fix: 缓存命中时不再构造伪 txHash
        assertNull(r.getChainTxHash());
        verify(smartContractService, never()).writeHash(anyString(), anyString());
    }

    // ============ resetMetrics ============

    @Test
    @DisplayName("resetMetrics - 重置所有性能指标")
    void testResetMetrics() {
        writer.resetMetrics();

        assertEquals(0L, writer.getTotalProcessed());
        assertEquals(0L, writer.getTotalFailed());
    }

    // ============ getAverageProcessLatency ============

    @Test
    @DisplayName("getAverageProcessLatency - 无处理时返回 0")
    void testGetAverageProcessLatency_NoProcessed() {
        assertEquals(0L, writer.getAverageProcessLatency());
    }

    @Test
    @DisplayName("getAverageProcessLatency - 有处理时返回正确均值")
    void testGetAverageProcessLatency_WithProcessed() {
        // 通过 flushAsyncBatch 处理记录，验证延迟计算
        ReflectionTestUtils.setField(writer, "batchSize", 2);
        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_lat");

        BlockchainRecord r = buildRecord(1L, "h_lat", "ORDER", "ORD_LAT");
        writer.submitAsync(r);
        writer.flushAsyncBatch();

        assertTrue(writer.getAverageProcessLatency() >= 0);
    }

    // ============ 熔断恢复到正常 ============

    @Test
    @DisplayName("flushAsyncBatch - 熔断期间的 processRecord 降级逻辑")
    void testProcessRecord_CircuitOpenInProcess() {
        ReflectionTestUtils.setField(writer, "batchSize", 2);

        // 设置熔断器打开
        try {
            java.lang.reflect.Field f = AsyncChainWriter.class.getDeclaredField("circuitOpenUntil");
            f.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicLong) f.get(writer))
                    .set(System.currentTimeMillis() + 60_000L);
            java.lang.reflect.Field f2 = AsyncChainWriter.class.getDeclaredField("consecutiveFailures");
            f2.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicInteger) f2.get(writer)).set(5);
        } catch (Exception e) {
            fail(e);
        }

        // 让 flushAsyncBatch 的批量流程进入熔断降级
        // 先清空队列
        writer.flushAsyncBatch(); // 空队列

        // 提交一条，让它走 flushAsyncBatch 的熔断分支
        BlockchainRecord r = buildRecord(1L, "h_cf", "ORDER", "ORD_CF");
        writer.submitAsync(r);
        writer.flushAsyncBatch();

        assertEquals(2, r.getStatus());
        verify(evidenceFailoverMapper, atLeastOnce()).insert(any(EvidenceFailover.class));
    }

    // ============ submitAsync 达到阈值触发 flush ============

    @Test
    @DisplayName("submitAsync - 刚好达到 batchSize 触发 flush")
    void testSubmitAsync_ReachBatchSize() {
        ReflectionTestUtils.setField(writer, "batchSize", 3);
        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_th");

        writer.submitAsync(buildRecord(1L, "h1", "ORDER", "ORD1"));
        writer.submitAsync(buildRecord(2L, "h2", "ORDER", "ORD2"));
        writer.submitAsync(buildRecord(3L, "h3", "ORDER", "ORD3"));

        assertEquals(0, writer.getQueueSize());
    }

    // ============ getPendingCount Redis 空白 ============

    @Test
    @DisplayName("getPendingCount - Redis 返回空白字符串时退回队列大小")
    void testGetPendingCount_BlankRedisValue() {
        when(valueOperations.get("lsc:evidence:pending-count")).thenReturn("");
        int count = writer.getPendingCount();
        assertEquals(writer.getQueueSize(), count);
    }

    // ============ flushAsyncBatch 异常被捕获 ============

    @Test
    @DisplayName("flushAsyncBatch - semaphore acquire 被中断时 fallback")
    void testFlushAsyncBatch_SemaphoreInterrupted() {
        ReflectionTestUtils.setField(writer, "batchSize", 2);

        // 通过 setMaxConcurrent(0) 让信号量许可为 0，使 tryAcquire 返回 false
        writer.setMaxConcurrent(0);

        when(smartContractService.writeHash(anyString(), anyString())).thenReturn("tx_sem");

        BlockchainRecord r = buildRecord(1L, "h_sem", "ORDER", "ORD_SEM");
        writer.submitAsync(r);

        assertDoesNotThrow(() -> writer.flushAsyncBatch());
        assertEquals(1, r.getStatus());
    }
}
