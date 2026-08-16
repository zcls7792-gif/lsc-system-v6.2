package com.lianshengtong.release.job;

import com.lianshengtong.common.result.R;
import com.lianshengtong.release.alert.AlertChannel;
import com.lianshengtong.release.alert.LoggingAlertChannel;
import com.lianshengtong.release.dto.AiReleasePredictDTO;
import com.lianshengtong.release.entity.DailyReleaseSummary;
import com.lianshengtong.release.feign.AiGatewayFeignClient;
import com.lianshengtong.release.feign.LscLedgerFeignClient;
import com.lianshengtong.release.mapper.DailyReleaseSummaryMapper;
import com.lianshengtong.release.service.BatchReleaseService;
import com.lianshengtong.release.service.ReleaseCalcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseJobHandler 单元测试")
class ReleaseJobHandlerTest {

    @Mock
    private ReleaseCalcService releaseCalcService;
    @Mock
    private BatchReleaseService batchReleaseService;
    @Mock
    private DailyReleaseSummaryMapper dailyReleaseSummaryMapper;
    @Mock
    private AiGatewayFeignClient aiGatewayFeignClient;
    @Mock
    private LscLedgerFeignClient lscLedgerFeignClient;

    @InjectMocks
    private ReleaseJobHandler releaseJobHandler;

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 8, 7);

    private DailyReleaseSummary existingSummary;
    private Map<String, Object> lockData;
    private Map<String, Object> dailySummaryData;

    @BeforeEach
    void setUp() {
        existingSummary = new DailyReleaseSummary();
        existingSummary.setDate(TEST_DATE);
        existingSummary.setLLocked(5000L);
        existingSummary.setNTotal(new BigDecimal("2500.00"));
        existingSummary.setMTotal(new BigDecimal("500000.00"));
        existingSummary.setK(new BigDecimal("0.005000"));
        existingSummary.setRate(new BigDecimal("0.000500"));
        existingSummary.setTRelease(250L);
        existingSummary.setActualReleased(250L);
        existingSummary.setBatchCount(3);
        existingSummary.setFailedBatchCount(0);
        existingSummary.setStatus(2);

        lockData = new HashMap<>();
        lockData.put("totalLocked", 10000L);
        lockData.put("userCount", 50);

        dailySummaryData = new HashMap<>();
        dailySummaryData.put("totalAmount", 3000.00);
        dailySummaryData.put("totalCount", 300);
    }

    // ==================== dailyReleaseJob 测试 ====================

    @Test
    @DisplayName("dailyReleaseJob: 正常执行 - 已有汇总记录，全流程成功")
    void dailyReleaseJob_normal() {
        when(dailyReleaseSummaryMapper.findByDate(any())).thenReturn(existingSummary);
        when(lscLedgerFeignClient.lockedSummary()).thenReturn(R.ok(lockData));
        when(lscLedgerFeignClient.dailySummary(anyString(), anyString())).thenReturn(R.ok(dailySummaryData));

        AiReleasePredictDTO.Response predictResp = AiReleasePredictDTO.Response.builder()
                .predictedK7d(new BigDecimal("0.004500"))
                .predictedK30d(new BigDecimal("0.004800"))
                .confidence(new BigDecimal("0.95"))
                .trend("FLAT")
                .fallback(false)
                .build();
        when(aiGatewayFeignClient.releasePredict(any(AiReleasePredictDTO.Request.class)))
                .thenReturn(R.ok(predictResp));
        when(releaseCalcService.calcDailyRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);
        when(batchReleaseService.executeBatchRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);

        releaseJobHandler.dailyReleaseJob();

        verify(dailyReleaseSummaryMapper).findByDate(any());
        verify(lscLedgerFeignClient).lockedSummary();
        verify(lscLedgerFeignClient).dailySummary(anyString(), anyString());
        verify(aiGatewayFeignClient).releasePredict(any(AiReleasePredictDTO.Request.class));
        verify(releaseCalcService).calcDailyRelease(any(DailyReleaseSummary.class));
        verify(batchReleaseService).executeBatchRelease(any(DailyReleaseSummary.class));
    }

    @Test
    @DisplayName("dailyReleaseJob: 无当日数据 - 创建新汇总记录(fresh=true)")
    void dailyReleaseJob_noExistingData() {
        when(dailyReleaseSummaryMapper.findByDate(any())).thenReturn(null);
        when(lscLedgerFeignClient.lockedSummary()).thenReturn(R.ok(lockData));
        when(lscLedgerFeignClient.dailySummary(anyString(), anyString())).thenReturn(R.ok(dailySummaryData));
        when(releaseCalcService.calcDailyRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);
        when(batchReleaseService.executeBatchRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);

        releaseJobHandler.dailyReleaseJob();

        verify(dailyReleaseSummaryMapper).insert(any(DailyReleaseSummary.class));
        verify(dailyReleaseSummaryMapper, never()).updateById(any(DailyReleaseSummary.class));
        verify(releaseCalcService).calcDailyRelease(any(DailyReleaseSummary.class));
        verify(batchReleaseService).executeBatchRelease(any(DailyReleaseSummary.class));
    }

    @Test
    @DisplayName("dailyReleaseJob: 账本锁定汇总接口异常 - 降级为0继续执行")
    void dailyReleaseJob_ledgerLockThrowsException() {
        when(dailyReleaseSummaryMapper.findByDate(any())).thenReturn(existingSummary);
        when(lscLedgerFeignClient.lockedSummary()).thenThrow(new RuntimeException("连接超时"));
        when(lscLedgerFeignClient.dailySummary(anyString(), anyString())).thenReturn(R.ok(dailySummaryData));
        when(releaseCalcService.calcDailyRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);
        when(batchReleaseService.executeBatchRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);

        releaseJobHandler.dailyReleaseJob();

        verify(lscLedgerFeignClient).lockedSummary();
        verify(releaseCalcService).calcDailyRelease(any(DailyReleaseSummary.class));
        verify(batchReleaseService).executeBatchRelease(any(DailyReleaseSummary.class));
    }

    @Test
    @DisplayName("dailyReleaseJob: 账本核销汇总接口异常 - 降级为0继续执行")
    void dailyReleaseJob_ledgerDailySummaryThrowsException() {
        when(dailyReleaseSummaryMapper.findByDate(any())).thenReturn(existingSummary);
        when(lscLedgerFeignClient.lockedSummary()).thenReturn(R.ok(lockData));
        when(lscLedgerFeignClient.dailySummary(anyString(), anyString())).thenThrow(new RuntimeException("服务不可用"));
        when(releaseCalcService.calcDailyRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);
        when(batchReleaseService.executeBatchRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);

        releaseJobHandler.dailyReleaseJob();

        verify(lscLedgerFeignClient).dailySummary(anyString(), anyString());
        verify(releaseCalcService).calcDailyRelease(any(DailyReleaseSummary.class));
        verify(batchReleaseService).executeBatchRelease(any(DailyReleaseSummary.class));
    }

    @Test
    @DisplayName("dailyReleaseJob: AI预测接口返回null - 降级跳过不影响主流程")
    void dailyReleaseJob_aiPredictReturnsNull() {
        when(dailyReleaseSummaryMapper.findByDate(any())).thenReturn(existingSummary);
        when(lscLedgerFeignClient.lockedSummary()).thenReturn(R.ok(lockData));
        when(lscLedgerFeignClient.dailySummary(anyString(), anyString())).thenReturn(R.ok(dailySummaryData));
        when(aiGatewayFeignClient.releasePredict(any(AiReleasePredictDTO.Request.class))).thenReturn(null);
        when(releaseCalcService.calcDailyRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);
        when(batchReleaseService.executeBatchRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);

        releaseJobHandler.dailyReleaseJob();

        verify(releaseCalcService).calcDailyRelease(any(DailyReleaseSummary.class));
        verify(batchReleaseService).executeBatchRelease(any(DailyReleaseSummary.class));
    }

    @Test
    @DisplayName("dailyReleaseJob: AI预测接口抛异常 - 降级跳过不影响主流程")
    void dailyReleaseJob_aiPredictThrowsException() {
        when(dailyReleaseSummaryMapper.findByDate(any())).thenReturn(existingSummary);
        when(lscLedgerFeignClient.lockedSummary()).thenReturn(R.ok(lockData));
        when(lscLedgerFeignClient.dailySummary(anyString(), anyString())).thenReturn(R.ok(dailySummaryData));
        when(aiGatewayFeignClient.releasePredict(any(AiReleasePredictDTO.Request.class)))
                .thenThrow(new RuntimeException("AI网关超时"));
        when(releaseCalcService.calcDailyRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);
        when(batchReleaseService.executeBatchRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);

        releaseJobHandler.dailyReleaseJob();

        verify(aiGatewayFeignClient).releasePredict(any(AiReleasePredictDTO.Request.class));
        verify(releaseCalcService).calcDailyRelease(any(DailyReleaseSummary.class));
        verify(batchReleaseService).executeBatchRelease(any(DailyReleaseSummary.class));
    }

    @Test
    @DisplayName("dailyReleaseJob: 释放计算抛异常(rate越界) - 终止任务不执行批量释放")
    void dailyReleaseJob_calcThrowsException() {
        when(dailyReleaseSummaryMapper.findByDate(any())).thenReturn(existingSummary);
        when(lscLedgerFeignClient.lockedSummary()).thenReturn(R.ok(lockData));
        when(lscLedgerFeignClient.dailySummary(anyString(), anyString())).thenReturn(R.ok(dailySummaryData));
        when(releaseCalcService.calcDailyRelease(any(DailyReleaseSummary.class)))
                .thenThrow(new RuntimeException("rate越界"));

        releaseJobHandler.dailyReleaseJob();

        verify(releaseCalcService).calcDailyRelease(any(DailyReleaseSummary.class));
        verify(batchReleaseService, never()).executeBatchRelease(any(DailyReleaseSummary.class));
        verify(dailyReleaseSummaryMapper, times(2)).updateById(any(DailyReleaseSummary.class));
    }

    @Test
    @DisplayName("dailyReleaseJob: 账本锁定汇总返回null - 降级为0")
    void dailyReleaseJob_ledgerReturnsNull() {
        when(dailyReleaseSummaryMapper.findByDate(any())).thenReturn(existingSummary);
        when(lscLedgerFeignClient.lockedSummary()).thenReturn(null);
        when(lscLedgerFeignClient.dailySummary(anyString(), anyString())).thenReturn(R.ok(dailySummaryData));
        when(releaseCalcService.calcDailyRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);
        when(batchReleaseService.executeBatchRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);

        releaseJobHandler.dailyReleaseJob();

        verify(releaseCalcService).calcDailyRelease(any(DailyReleaseSummary.class));
        verify(batchReleaseService).executeBatchRelease(any(DailyReleaseSummary.class));
    }

    @Test
    @DisplayName("dailyReleaseJob: 账本锁定汇总返回失败 - 降级为0")
    void dailyReleaseJob_ledgerReturnsFailure() {
        when(dailyReleaseSummaryMapper.findByDate(any())).thenReturn(existingSummary);
        when(lscLedgerFeignClient.lockedSummary()).thenReturn(R.fail("服务异常"));
        when(lscLedgerFeignClient.dailySummary(anyString(), anyString())).thenReturn(R.ok(dailySummaryData));
        when(releaseCalcService.calcDailyRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);
        when(batchReleaseService.executeBatchRelease(any(DailyReleaseSummary.class))).thenReturn(existingSummary);

        releaseJobHandler.dailyReleaseJob();

        verify(releaseCalcService).calcDailyRelease(any(DailyReleaseSummary.class));
        verify(batchReleaseService).executeBatchRelease(any(DailyReleaseSummary.class));
    }

    // ==================== expireTransferJob 测试 ====================

    @Test
    @DisplayName("expireTransferJob: 正常执行 - 过期LSC转回成功")
    void expireTransferJob_success() {
        Map<String, Object> transferData = new HashMap<>();
        transferData.put("userCount", 150);
        transferData.put("transferAmount", 50000L);
        when(lscLedgerFeignClient.expireTransfer()).thenReturn(R.ok(transferData));

        releaseJobHandler.expireTransferJob();

        verify(lscLedgerFeignClient).expireTransfer();
    }

    @Test
    @DisplayName("expireTransferJob: 账本服务返回null - 安全处理不抛异常")
    void expireTransferJob_returnsNull() {
        when(lscLedgerFeignClient.expireTransfer()).thenReturn(null);

        assertDoesNotThrow(() -> releaseJobHandler.expireTransferJob());

        verify(lscLedgerFeignClient).expireTransfer();
    }

    @Test
    @DisplayName("expireTransferJob: 账本服务返回失败 - 记录warn日志")
    void expireTransferJob_returnsFailure() {
        when(lscLedgerFeignClient.expireTransfer()).thenReturn(R.fail("转账服务暂不可用"));

        assertDoesNotThrow(() -> releaseJobHandler.expireTransferJob());

        verify(lscLedgerFeignClient).expireTransfer();
    }

    @Test
    @DisplayName("expireTransferJob: 账本接口抛异常 - 记录error日志不传播")
    void expireTransferJob_throwsException() {
        when(lscLedgerFeignClient.expireTransfer()).thenThrow(new RuntimeException("网络异常"));

        assertDoesNotThrow(() -> releaseJobHandler.expireTransferJob());

        verify(lscLedgerFeignClient).expireTransfer();
    }

    // ==================== reconcileJob 测试 ====================

    @Test
    @DisplayName("reconcileJob: 无当日汇总记录 - warn日志后直接返回")
    void reconcileJob_noRecords() {
        when(dailyReleaseSummaryMapper.findByDate(any())).thenReturn(null);

        releaseJobHandler.reconcileJob();

        verify(dailyReleaseSummaryMapper).findByDate(any());
        verify(batchReleaseService, never()).reconcile(any(DailyReleaseSummary.class));
    }

    @Test
    @DisplayName("reconcileJob: 账务比对一致 - 记录info日志")
    void reconcileJob_match() {
        when(dailyReleaseSummaryMapper.findByDate(any())).thenReturn(existingSummary);
        when(batchReleaseService.reconcile(any(DailyReleaseSummary.class))).thenReturn(true);

        releaseJobHandler.reconcileJob();

        verify(batchReleaseService).reconcile(existingSummary);
    }

    @Test
    @DisplayName("reconcileJob: 账务比对不一致 - 记录error日志需人工介入")
    void reconcileJob_mismatch() {
        when(dailyReleaseSummaryMapper.findByDate(any())).thenReturn(existingSummary);
        when(batchReleaseService.reconcile(any(DailyReleaseSummary.class))).thenReturn(false);

        releaseJobHandler.reconcileJob();

        verify(batchReleaseService).reconcile(existingSummary);
    }

    // ==================== LoggingAlertChannel 测试 ====================

    @Nested
    @DisplayName("LoggingAlertChannel 测试")
    class LoggingAlertChannelTest {

        private LoggingAlertChannel channel;

        @BeforeEach
        void setUp() {
            channel = new LoggingAlertChannel();
        }

        @Test
        @DisplayName("send: 正常发送告警 - 不抛异常")
        void send_normal() {
            assertDoesNotThrow(() -> channel.send("admin-001,admin-002", "释放任务异常", "释放速率越界"));
        }

        @Test
        @DisplayName("send: 空接收人 - 不抛异常")
        void send_emptyReceivers() {
            assertDoesNotThrow(() -> channel.send("", "告警", "内容"));
            assertDoesNotThrow(() -> channel.send(null, "告警", "内容"));
        }

        @Test
        @DisplayName("send: null消息内容 - 不抛异常")
        void send_nullMessage() {
            assertDoesNotThrow(() -> channel.send("admin-001", null, null));
        }

        @Test
        @DisplayName("name: 返回 LOGGING")
        void name_returnsLogging() {
            assertEquals("LOGGING", channel.name());
        }
    }

    // ==================== AlertChannel 接口校验 ====================

    @Nested
    @DisplayName("AlertChannel 接口签名校验")
    class AlertChannelInterfaceTest {

        @Test
        @DisplayName("LoggingAlertChannel 实现 AlertChannel 接口")
        void implementsAlertChannel() {
            assertTrue(AlertChannel.class.isAssignableFrom(LoggingAlertChannel.class),
                    "LoggingAlertChannel 应实现 AlertChannel 接口");
        }

        @Test
        @DisplayName("AlertChannel 接口包含 send 方法签名")
        void sendMethodSignature() throws NoSuchMethodException {
            java.lang.reflect.Method sendMethod = AlertChannel.class
                    .getMethod("send", String.class, String.class, String.class);
            assertEquals(void.class, sendMethod.getReturnType(), "send 返回值应为 void");
        }

        @Test
        @DisplayName("AlertChannel 接口包含 name 默认方法")
        void nameMethodSignature() throws NoSuchMethodException {
            java.lang.reflect.Method nameMethod = AlertChannel.class.getMethod("name");
            assertEquals(String.class, nameMethod.getReturnType(), "name 返回值应为 String");
            assertTrue(java.lang.reflect.Modifier.isPublic(nameMethod.getModifiers()),
                    "name 应为 public 方法");
        }
    }
}