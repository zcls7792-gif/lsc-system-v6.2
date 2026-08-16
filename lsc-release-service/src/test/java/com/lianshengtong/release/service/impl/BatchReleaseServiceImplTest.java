package com.lianshengtong.release.service.impl;

import com.lianshengtong.common.enums.ReleaseTaskStatusEnum;
import com.lianshengtong.common.result.R;
import com.lianshengtong.release.entity.DailyReleaseSummary;
import com.lianshengtong.release.feign.LscLedgerFeignClient;
import com.lianshengtong.release.mapper.DailyReleaseSummaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("批量释放服务单元测试")
class BatchReleaseServiceImplTest {

    @Mock
    private LscLedgerFeignClient lscLedgerFeignClient;
    @Mock
    private DailyReleaseSummaryMapper dailyReleaseSummaryMapper;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rLock;

    @InjectMocks
    private BatchReleaseServiceImpl batchReleaseService;

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 8, 7);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(batchReleaseService, "batchSize", 100000);
        ReflectionTestUtils.setField(batchReleaseService, "lockKey", "lsc:release:lock:daily");
        ReflectionTestUtils.setField(batchReleaseService, "lockExpireSeconds", 7200L);

        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            lenient().when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        } catch (InterruptedException e) {
            // ignore
        }
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    private DailyReleaseSummary buildSummary(long tRelease, BigDecimal rate) {
        DailyReleaseSummary s = new DailyReleaseSummary();
        s.setDate(TEST_DATE);
        s.setTRelease(tRelease);
        s.setRate(rate);
        s.setActualReleased(0L);
        s.setBatchCount(0);
        s.setFailedBatchCount(0);
        return s;
    }

    private void setupLockedSummary(long totalLocked, Map<String, Object>... accounts) {
        Map<String, Object> data = new HashMap<>();
        data.put("totalLocked", totalLocked);
        data.put("userCount", accounts.length);
        List<Map<String, Object>> accountList = new ArrayList<>();
        Collections.addAll(accountList, accounts);
        data.put("accounts", accountList);
        R<Map<String, Object>> resp = R.ok(data);
        lenient().when(lscLedgerFeignClient.lockedSummary()).thenReturn(resp);
    }

    private final Map<String, Object> accountMap(Long userId, Long totalLocked) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", userId);
        m.put("totalLocked", totalLocked);
        return m;
    }

    private void setupReleaseBatchSuccess(long releasedAmount) {
        Map<String, Object> data = new HashMap<>();
        data.put("releasedAmount", releasedAmount);
        R<Map<String, Object>> resp = R.ok(data);
        lenient().when(lscLedgerFeignClient.releaseBatch(anyList())).thenReturn(resp);
    }

    private void setupReleaseBatchFailure() {
        R<Map<String, Object>> resp = R.fail("账本批量释放接口失败");
        lenient().when(lscLedgerFeignClient.releaseBatch(anyList())).thenReturn(resp);
    }

    private void setupReleaseBatchAmountMismatch(long actualBatchReleased) {
        Map<String, Object> data = new HashMap<>();
        data.put("releasedAmount", actualBatchReleased);
        R<Map<String, Object>> resp = R.ok(data);
        lenient().when(lscLedgerFeignClient.releaseBatch(anyList())).thenReturn(resp);
    }

    // ==================== executeBatchRelease 测试 ====================

    @Test
    @DisplayName("executeBatchRelease: 完整成功流程 - 分布式锁获取、批次处理、汇总校验通过")
    void executeBatchRelease_success() {
        DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));

        setupLockedSummary(1000L,
                accountMap(1L, 1_000_000L),
                accountMap(2L, 1_000_000L));

        setupReleaseBatchSuccess(1000L);

        when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

        DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);

        assertNotNull(result);
        assertEquals(ReleaseTaskStatusEnum.SUCCESS.getCode(), result.getStatus());
        assertEquals(1000L, result.getActualReleased());
        assertEquals(1, result.getBatchCount());
        assertEquals(0, result.getFailedBatchCount());
        assertNull(result.getFailReason());

        verify(rLock).unlock();
    }

    @Test
    @DisplayName("executeBatchRelease: 锁获取失败 - 已有实例在执行，直接返回并标记原因")
    void executeBatchRelease_lockFailed() {
        try {
            doReturn(false).when(rLock).tryLock(anyLong(), anyLong(), any());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));

        DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);

        assertNotNull(result);
        assertEquals("已有实例在执行，跳过", result.getFailReason());
        assertNotEquals(ReleaseTaskStatusEnum.SUCCESS.getCode(), result.getStatus());

        verify(rLock, never()).unlock();
    }

    @Test
    @DisplayName("executeBatchRelease: 锁获取被中断 - 恢复中断标记并抛 IllegalStateException")
    void executeBatchRelease_lockInterrupted() {
        InterruptedException ie = new InterruptedException("test interrupt");
        try {
            doThrow(ie).when(rLock).tryLock(anyLong(), anyLong(), any());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> batchReleaseService.executeBatchRelease(summary));

        assertEquals("获取释放分布式锁被中断", ex.getMessage());
        assertSame(ie, ex.getCause());
        assertTrue(Thread.currentThread().isInterrupted());

        verify(rLock, never()).unlock();
    }

    @Test
    @DisplayName("executeBatchRelease: 无待释放记录 - rate为0且planTotal为0导致加载空列表，reconcile通过")
    void executeBatchRelease_emptyItems() {
        DailyReleaseSummary summary = buildSummary(0L, BigDecimal.ZERO);

        R<Map<String, Object>> emptyResp = R.ok(new HashMap<>());
        lenient().when(lscLedgerFeignClient.lockedSummary()).thenReturn(emptyResp);

        when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

        DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);

        assertNotNull(result);
        assertEquals(0L, result.getActualReleased());
        assertEquals(ReleaseTaskStatusEnum.SUCCESS.getCode(), result.getStatus());
    }

    @Test
    @DisplayName("executeBatchRelease: 部分批次失败 - 第二个批次失败后停止处理，标记FAILED")
    void executeBatchRelease_partialFailure() {
        ReflectionTestUtils.setField(batchReleaseService, "batchSize", 1);

        DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));

        setupLockedSummary(1000L,
                accountMap(1L, 1_000_000L),
                accountMap(2L, 1_000_000L));

        AtomicInteger callCount = new AtomicInteger(0);
        when(lscLedgerFeignClient.releaseBatch(anyList())).thenAnswer(invocation -> {
            int call = callCount.incrementAndGet();
            if (call == 1) {
                Map<String, Object> data = new HashMap<>();
                data.put("releasedAmount", 500L);
                return R.ok(data);
            } else {
                return R.fail("模拟第二批次失败");
            }
        });

        when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

        DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);

        assertNotNull(result);
        assertEquals(ReleaseTaskStatusEnum.FAILED.getCode(), result.getStatus());
        assertEquals(500L, result.getActualReleased());
        assertEquals(2, result.getBatchCount());
        assertEquals(1, result.getFailedBatchCount());
        assertNotNull(result.getFailReason());
        assertTrue(result.getFailReason().contains("批次#2失败"));
    }

    @Test
    @DisplayName("executeBatchRelease: 汇总校验不匹配 - 实际释放量 < 计划量，标记FAILED")
    void executeBatchRelease_reconcileMismatch() {
        DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));

        setupLockedSummary(600L,
                accountMap(1L, 600_000L));

        setupReleaseBatchSuccess(300L);

        when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

        DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);

        assertNotNull(result);
        assertEquals(ReleaseTaskStatusEnum.FAILED.getCode(), result.getStatus());
        assertEquals(300L, result.getActualReleased());
        assertNotNull(result.getFailReason());
        assertTrue(result.getFailReason().contains("汇总校验不一致"));
    }

    @Test
    @DisplayName("executeBatchRelease: 断点续跑 - actualReleased > 0 从断点继续并最终对账成功")
    void executeBatchRelease_resumeAfterInterruption() {
        DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));
        summary.setActualReleased(500L);
        summary.setBatchCount(1);
        summary.setFailedBatchCount(0);

        setupLockedSummary(500L,
                accountMap(1L, 1_000_000L));

        setupReleaseBatchSuccess(500L);

        when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

        DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);

        assertNotNull(result);
        assertEquals(ReleaseTaskStatusEnum.SUCCESS.getCode(), result.getStatus());
        assertEquals(1000L, result.getActualReleased());
        assertEquals(2, result.getBatchCount());
        assertEquals(0, result.getFailedBatchCount());
    }

    // ==================== reconcile 测试 ====================

    @Test
    @DisplayName("reconcile: 实际释放量 == 计划量 - 返回true")
    void reconcile_match() {
        DailyReleaseSummary summary = new DailyReleaseSummary();
        summary.setTRelease(1000L);
        summary.setActualReleased(1000L);

        assertTrue(batchReleaseService.reconcile(summary));
    }

    @Test
    @DisplayName("reconcile: 实际释放量 != 计划量 - 返回false")
    void reconcile_mismatch() {
        DailyReleaseSummary summary = new DailyReleaseSummary();
        summary.setTRelease(1000L);
        summary.setActualReleased(800L);

        assertFalse(batchReleaseService.reconcile(summary));
    }

    @Test
    @DisplayName("reconcile: 字段为null时按0处理 - 两者都为null时匹配")
    void reconcile_nullFields() {
        DailyReleaseSummary summary = new DailyReleaseSummary();

        assertTrue(batchReleaseService.reconcile(summary));

        summary.setTRelease(500L);
        assertFalse(batchReleaseService.reconcile(summary));

        summary.setActualReleased(500L);
        assertTrue(batchReleaseService.reconcile(summary));
    }

    // ==================== toLong 测试 ====================

    @Test
    @DisplayName("toLong: 多种类型转换 - Number/String/null 均正确处理")
    void toLong_variousTypes() {
        assertEquals(123L, (long) ReflectionTestUtils.invokeMethod(
                batchReleaseService, "toLong", 123));

        assertEquals(456L, (long) ReflectionTestUtils.invokeMethod(
                batchReleaseService, "toLong", 456L));

        assertEquals(789L, (long) ReflectionTestUtils.invokeMethod(
                batchReleaseService, "toLong", "789"));

        assertEquals(0L, (long) ReflectionTestUtils.invokeMethod(
                batchReleaseService, "toLong", (Object) null));

        assertEquals(0L, (long) ReflectionTestUtils.invokeMethod(
                batchReleaseService, "toLong", "not_a_number"));
    }

    // ==================== processBatch 相关测试(通过 executeBatchRelease 间接覆盖) ====================

    @Test
    @DisplayName("processBatch: 账本接口调用失败 - 返回null响应导致抛异常，批次标记失败")
    void processBatch_ledgerFailure() {
        DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));

        setupLockedSummary(1000L,
                accountMap(1L, 1_000_000L),
                accountMap(2L, 1_000_000L));

        setupReleaseBatchFailure();

        when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

        DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);

        assertNotNull(result);
        assertEquals(ReleaseTaskStatusEnum.FAILED.getCode(), result.getStatus());
        assertEquals(1, result.getFailedBatchCount());
        assertNotNull(result.getFailReason());
        assertTrue(result.getFailReason().contains("账本批量释放接口失败"));
    }

    @Test
    @DisplayName("processBatch: 批次实际释放量与计划量不一致 - 抛异常并标记FAILED")
    void processBatch_amountMismatch() {
        DailyReleaseSummary summary = buildSummary(1000L, new BigDecimal("0.0005"));

        setupLockedSummary(1000L,
                accountMap(1L, 1_000_000L),
                accountMap(2L, 1_000_000L));

        setupReleaseBatchAmountMismatch(800L);

        when(dailyReleaseSummaryMapper.updateById(any(DailyReleaseSummary.class))).thenReturn(1);

        DailyReleaseSummary result = batchReleaseService.executeBatchRelease(summary);

        assertNotNull(result);
        assertEquals(ReleaseTaskStatusEnum.FAILED.getCode(), result.getStatus());
        assertEquals(1, result.getFailedBatchCount());
        assertNotNull(result.getFailReason());
        assertTrue(result.getFailReason().contains("总量不一致"));
    }
}