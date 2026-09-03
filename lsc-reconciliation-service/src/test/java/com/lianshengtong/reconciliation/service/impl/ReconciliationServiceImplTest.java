package com.lianshengtong.reconciliation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.reconciliation.entity.ReconcileReport;
import com.lianshengtong.reconciliation.feign.EvidenceFeignClient;
import com.lianshengtong.reconciliation.feign.LscLedgerFeignClient;
import com.lianshengtong.reconciliation.feign.OrderFeignClient;
import com.lianshengtong.reconciliation.mapper.ReconcileReportMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("对账服务单元测试")
class ReconciliationServiceImplTest {

    @Mock
    private ReconcileReportMapper reconcileReportMapper;
    @Mock
    private EvidenceFeignClient evidenceFeignClient;
    @Mock
    private LscLedgerFeignClient lscLedgerFeignClient;
    @Mock
    private OrderFeignClient orderFeignClient;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rLock;

    @InjectMocks
    private ReconciliationServiceImpl reconciliationService;

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 8, 5);

    private Map<String, Object> buildPaymentSummary(BigDecimal amount, long count) {
        Map<String, Object> map = new HashMap<>();
        map.put("totalAmount", amount);
        map.put("totalCount", count);
        return map;
    }

    private Map<String, Object> buildLedgerSummary(long amount, long count) {
        Map<String, Object> map = new HashMap<>();
        map.put("totalAmount", amount);
        map.put("totalCount", count);
        return map;
    }

    /** Sets up common mocks for generateReport tests where a new report is inserted. */
    private void stubNewReportInsert(Long id) {
        stubNewReportInsert(id, false);
    }

    private void stubNewReportInsert(Long id, boolean withExisting) {
        ReconcileReport existing = withExisting ? new ReconcileReport() : null;
        lenient().when(reconcileReportMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        lenient().when(reconcileReportMapper.insert(any(ReconcileReport.class))).thenAnswer(inv -> {
            ReconcileReport r = inv.getArgument(0);
            r.setId(id);
            return 1;
        });
        lenient().when(reconcileReportMapper.updateById(any(ReconcileReport.class))).thenReturn(1);
    }

    private void stubPaymentAndLedger(Map<String, Object> paymentData, Map<String, Object> ledgerData) {
        lenient().when(orderFeignClient.dailySummary(anyString())).thenReturn(R.ok(paymentData));
        lenient().when(lscLedgerFeignClient.dailySummary(any(LocalDate.class), anyString())).thenReturn(R.ok(ledgerData));
    }

    private void stubEvidenceOk(String txHash) {
        lenient().when(evidenceFeignClient.saveEvidence(anyString(), anyString(), anyString()))
                .thenReturn(R.ok(txHash));
    }

    private void stubReportSelectById(ReconcileReport report) {
        lenient().when(reconcileReportMapper.selectById(anyLong())).thenReturn(report);
    }

    // ============== generateReport 测试 ==============

    @Test
    @DisplayName("generateReport: 支付与账本一致生成一致报告并上链")
    void generateReport_consistent_success() {
        Long reportId = 1L;
        stubNewReportInsert(reportId);
        Map<String, Object> paymentData = buildPaymentSummary(new BigDecimal("1000.00"), 10L);
        Map<String, Object> ledgerData = buildLedgerSummary(1000L, 10L);
        stubPaymentAndLedger(paymentData, ledgerData);
        stubEvidenceOk("0xchainTxHash123");
        ReconcileReport reportWithHash = new ReconcileReport();
        reportWithHash.setId(reportId);
        stubReportSelectById(reportWithHash);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(TEST_DATE, report.getReconcileDate());
        assertEquals(1, report.getStatus());
        assertEquals(0, report.getDiffCount());
    }

    @Test
    @DisplayName("generateReport: 金额不一致生成差异报告")
    void generateReport_amountDiff_status2() {
        stubNewReportInsert(1L);
        Map<String, Object> paymentData = buildPaymentSummary(new BigDecimal("1000.00"), 10L);
        Map<String, Object> ledgerData = buildLedgerSummary(900L, 10L);
        stubPaymentAndLedger(paymentData, ledgerData);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(2, report.getStatus());
        assertTrue(report.getDiffAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("generateReport: 笔数不一致生成差异报告")
    void generateReport_countDiff_status2() {
        stubNewReportInsert(1L);
        Map<String, Object> paymentData = buildPaymentSummary(new BigDecimal("1000.00"), 10L);
        Map<String, Object> ledgerData = buildLedgerSummary(1000L, 8L);
        stubPaymentAndLedger(paymentData, ledgerData);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(2, report.getStatus());
        assertEquals(Long.valueOf(2L), report.getDiffCount());
    }

    @Test
    @DisplayName("generateReport: 日期为空抛异常")
    void generateReport_nullDate_throws() {
        assertThrows(BizException.class, () -> reconciliationService.generateReport(null));
    }

    @Test
    @DisplayName("generateReport: 支付侧Feign失败使用默认值")
    void generateReport_paymentFeignFail_defaults() {
        stubNewReportInsert(1L);
        Map<String, Object> ledgerData = buildLedgerSummary(500L, 5L);
        lenient().when(orderFeignClient.dailySummary(anyString())).thenThrow(new RuntimeException("Feign调用失败"));
        lenient().when(lscLedgerFeignClient.dailySummary(any(LocalDate.class), anyString())).thenReturn(R.ok(ledgerData));

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(0, report.getPaymentCount().longValue());
    }

    @Test
    @DisplayName("generateReport: 账本侧Feign失败使用默认值")
    void generateReport_ledgerFeignFail_defaults() {
        stubNewReportInsert(1L);
        Map<String, Object> paymentData = buildPaymentSummary(new BigDecimal("500.00"), 5L);
        lenient().when(orderFeignClient.dailySummary(anyString())).thenReturn(R.ok(paymentData));
        lenient().when(lscLedgerFeignClient.dailySummary(any(LocalDate.class), anyString())).thenThrow(new RuntimeException("Feign调用失败"));

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(0, report.getLedgerCount().longValue());
    }

    @Test
    @DisplayName("generateReport: 已有报告且非初始状态直接返回")
    void generateReport_existingNonZeroStatus_returnsAsIs() {
        ReconcileReport existing = new ReconcileReport();
        existing.setId(1L);
        existing.setReconcileDate(TEST_DATE);
        existing.setStatus(1);
        existing.setPaymentTotalAmount(new BigDecimal("1000"));
        existing.setPaymentCount(10L);

        when(reconcileReportMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertEquals(1L, report.getId().longValue());
        assertEquals(1, report.getStatus());
    }

    @Test
    @DisplayName("generateReport: 不同月份日期正常生成报告")
    void generateReport_differentMonthDate() {
        LocalDate julyDate = LocalDate.of(2026, 7, 15);
        stubNewReportInsert(1L);
        Map<String, Object> paymentData = buildPaymentSummary(new BigDecimal("2000.00"), 20L);
        Map<String, Object> ledgerData = buildLedgerSummary(2000L, 20L);
        stubPaymentAndLedger(paymentData, ledgerData);
        stubEvidenceOk("0xchainTxHashJuly");
        ReconcileReport reportWithHash = new ReconcileReport();
        reportWithHash.setId(1L);
        stubReportSelectById(reportWithHash);

        ReconcileReport report = reconciliationService.generateReport(julyDate);

        assertNotNull(report);
        assertEquals(julyDate, report.getReconcileDate());
        assertEquals(1, report.getStatus());
    }

    @Test
    @DisplayName("generateReport: 上链存证失败仍成功生成报告")
    void generateReport_evidenceSaveFails_stillCreates() {
        stubNewReportInsert(1L);
        Map<String, Object> paymentData = buildPaymentSummary(new BigDecimal("500.00"), 5L);
        Map<String, Object> ledgerData = buildLedgerSummary(500L, 5L);
        stubPaymentAndLedger(paymentData, ledgerData);
        // Simulate hashOnChain -> reportNotFound BizException because selectById returns null
        lenient().when(reconcileReportMapper.selectById(anyLong())).thenReturn(null);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(TEST_DATE, report.getReconcileDate());
        assertEquals(1, report.getStatus());
    }

    @Test
    @DisplayName("generateReport: 查询不存在日期创建新报告")
    void generateReport_newDate_createsNew() {
        LocalDate newDate = LocalDate.of(2026, 8, 6);
        stubNewReportInsert(200L);
        Map<String, Object> paymentData = buildPaymentSummary(new BigDecimal("1500.00"), 15L);
        Map<String, Object> ledgerData = buildLedgerSummary(1500L, 15L);
        stubPaymentAndLedger(paymentData, ledgerData);
        stubEvidenceOk("0xchainTxNew");
        ReconcileReport reportWithHash = new ReconcileReport();
        reportWithHash.setId(200L);
        stubReportSelectById(reportWithHash);

        ReconcileReport report = reconciliationService.generateReport(newDate);

        assertNotNull(report);
        assertEquals(newDate, report.getReconcileDate());
        assertEquals(1, report.getStatus());
        assertEquals(Long.valueOf(15L), report.getPaymentCount());
    }

    @Test
    @DisplayName("generateReport: 查询已有报告返回该报告")
    void generateReport_existingReport_returnsAsIs() {
        ReconcileReport existing = new ReconcileReport();
        existing.setId(50L);
        existing.setReconcileDate(TEST_DATE);
        existing.setStatus(1);
        existing.setPaymentTotalAmount(new BigDecimal("800"));
        existing.setPaymentCount(8L);
        existing.setLedgerTotalAmount(new BigDecimal("800"));
        existing.setLedgerCount(8L);

        when(reconcileReportMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertEquals(50L, report.getId().longValue());
        assertEquals(TEST_DATE, report.getReconcileDate());
        assertEquals(1, report.getStatus());
    }

    // ============== dailyReconcile 测试 ==============

    @Test
    @DisplayName("dailyReconcile: 加锁成功生成报告")
    void dailyReconcile_lockSuccess_generatesReport() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MINUTES))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        Long reportId = 1L;
        stubNewReportInsert(reportId);
        Map<String, Object> paymentData = buildPaymentSummary(new BigDecimal("1000.00"), 10L);
        Map<String, Object> ledgerData = buildLedgerSummary(1000L, 10L);
        stubPaymentAndLedger(paymentData, ledgerData);
        stubEvidenceOk("0xtxHash");
        ReconcileReport reportWithHash = new ReconcileReport();
        reportWithHash.setId(reportId);
        stubReportSelectById(reportWithHash);

        ReconcileReport report = reconciliationService.dailyReconcile(TEST_DATE);

        assertNotNull(report);
        assertEquals(1, report.getStatus());
    }

    @Test
    @DisplayName("dailyReconcile: 加锁失败抛异常")
    void dailyReconcile_lockFail_throws() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MINUTES))).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> reconciliationService.dailyReconcile(TEST_DATE));
        assertTrue(ex.getMessage().contains("对账任务正在执行"));
    }

    @Test
    @DisplayName("dailyReconcile: null日期使用昨日日期")
    void dailyReconcile_nullDate_usesYesterday() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MINUTES))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        Long reportId = 1L;
        stubNewReportInsert(reportId);
        Map<String, Object> paymentData = buildPaymentSummary(new BigDecimal("300.00"), 3L);
        Map<String, Object> ledgerData = buildLedgerSummary(300L, 3L);
        stubPaymentAndLedger(paymentData, ledgerData);
        stubEvidenceOk("0xtxHashNullDate");
        ReconcileReport reportWithHash = new ReconcileReport();
        reportWithHash.setId(reportId);
        stubReportSelectById(reportWithHash);

        ReconcileReport report = reconciliationService.dailyReconcile(null);

        assertNotNull(report);
        LocalDate expectedYesterday = LocalDate.now().minusDays(1);
        assertEquals(expectedYesterday, report.getReconcileDate());
        assertEquals(1, report.getStatus());
    }

    @Test
    @DisplayName("dailyReconcile: 存证上链失败仍完成对账")
    void dailyReconcile_evidenceSaveFails_completes() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MINUTES))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        Long reportId = 1L;
        stubNewReportInsert(reportId);
        Map<String, Object> paymentData = buildPaymentSummary(new BigDecimal("100.00"), 1L);
        Map<String, Object> ledgerData = buildLedgerSummary(100L, 1L);
        stubPaymentAndLedger(paymentData, ledgerData);
        // Simulate hashOnChain -> reportNotFound BizException
        lenient().when(reconcileReportMapper.selectById(anyLong())).thenReturn(null);

        ReconcileReport report = reconciliationService.dailyReconcile(TEST_DATE);

        assertNotNull(report);
        assertEquals(TEST_DATE, report.getReconcileDate());
        assertEquals(1, report.getStatus());
    }

    // ============== hashOnChain 测试 ==============

    @Test
    @DisplayName("hashOnChain: 成功上链")
    void hashOnChain_success() {
        ReconcileReport report = new ReconcileReport();
        report.setId(1L);
        report.setResultHash("sha256hash");

        when(reconcileReportMapper.selectById(1L)).thenReturn(report);
        when(evidenceFeignClient.saveEvidence(anyString(), anyString(), anyString()))
                .thenReturn(R.ok("0xchainTxHash"));
        when(reconcileReportMapper.updateById(any(ReconcileReport.class))).thenReturn(1);

        String chainTxHash = reconciliationService.hashOnChain(1L);

        assertEquals("0xchainTxHash", chainTxHash);
    }

    @Test
    @DisplayName("hashOnChain: 报告不存在抛异常")
    void hashOnChain_reportNotFound_throws() {
        when(reconcileReportMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> reconciliationService.hashOnChain(999L));

        assertTrue(ex.getMessage().contains("对账报告不存在"));
    }

    @Test
    @DisplayName("hashOnChain: 已上链报告仍执行上链操作")
    void hashOnChain_alreadyOnChain_stillChains() {
        ReconcileReport report = new ReconcileReport();
        report.setId(100L);
        report.setResultHash("existingHash");
        report.setStatus(1);

        when(reconcileReportMapper.selectById(100L)).thenReturn(report);
        when(evidenceFeignClient.saveEvidence(anyString(), anyString(), anyString()))
                .thenReturn(R.ok("0xnewChainTxHash"));
        when(reconcileReportMapper.updateById(any(ReconcileReport.class))).thenReturn(1);

        String chainTxHash = reconciliationService.hashOnChain(100L);

        assertEquals("0xnewChainTxHash", chainTxHash);
        verify(evidenceFeignClient).saveEvidence(eq("RECONCILE"), eq("100"), eq("existingHash"));
    }

    @Test
    @DisplayName("hashOnChain: resultHash为null自动计算")
    void hashOnChain_nullHash_calculates() {
        ReconcileReport report = new ReconcileReport();
        report.setId(200L);
        report.setResultHash(null);

        when(reconcileReportMapper.selectById(200L)).thenReturn(report);
        when(evidenceFeignClient.saveEvidence(anyString(), anyString(), anyString()))
                .thenReturn(R.ok("0xautoHashTx"));
        when(reconcileReportMapper.updateById(any(ReconcileReport.class))).thenReturn(1);

        String tx = reconciliationService.hashOnChain(200L);

        assertEquals("0xautoHashTx", tx);
        assertNotNull(report.getResultHash());
    }

    // ============== 数据转换边界测试 ==============

    @Test
    @DisplayName("toBigDecimal: null返回零")
    void toBigDecimal_null_returnsZero() {
        stubNewReportInsert(1L);
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("totalAmount", null);
        paymentData.put("totalCount", 10L);
        Map<String, Object> ledgerData = buildLedgerSummary(1000L, 10L);
        stubPaymentAndLedger(paymentData, ledgerData);
        stubEvidenceOk("0xhash");
        ReconcileReport reportWithHash = new ReconcileReport();
        reportWithHash.setId(1L);
        stubReportSelectById(reportWithHash);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(BigDecimal.ZERO, report.getPaymentTotalAmount());
    }

    @Test
    @DisplayName("toBigDecimal: 非数值字符串返回零")
    void toBigDecimal_invalidString_returnsZero() {
        stubNewReportInsert(1L);
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("totalAmount", "not_a_number");
        paymentData.put("totalCount", 10L);
        Map<String, Object> ledgerData = buildLedgerSummary(1000L, 10L);
        stubPaymentAndLedger(paymentData, ledgerData);
        stubEvidenceOk("0xhash");
        ReconcileReport reportWithHash = new ReconcileReport();
        reportWithHash.setId(1L);
        stubReportSelectById(reportWithHash);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(BigDecimal.ZERO, report.getPaymentTotalAmount());
    }

    @Test
    @DisplayName("toLong: null返回零")
    void toLong_null_returnsZero() {
        stubNewReportInsert(1L);
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("totalAmount", new BigDecimal("100"));
        paymentData.put("totalCount", null);
        Map<String, Object> ledgerData = buildLedgerSummary(100L, 1L);
        stubPaymentAndLedger(paymentData, ledgerData);
        stubEvidenceOk("0xhash");
        ReconcileReport reportWithHash = new ReconcileReport();
        reportWithHash.setId(1L);
        stubReportSelectById(reportWithHash);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(Long.valueOf(0L), report.getPaymentCount());
    }

    @Test
    @DisplayName("toLong: 非数值字符串返回零")
    void toLong_invalidString_returnsZero() {
        stubNewReportInsert(1L);
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("totalAmount", new BigDecimal("100"));
        paymentData.put("totalCount", "invalid");
        Map<String, Object> ledgerData = buildLedgerSummary(100L, 1L);
        stubPaymentAndLedger(paymentData, ledgerData);
        stubEvidenceOk("0xhash");
        ReconcileReport reportWithHash = new ReconcileReport();
        reportWithHash.setId(1L);
        stubReportSelectById(reportWithHash);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(Long.valueOf(0L), report.getPaymentCount());
    }

    @Test
    @DisplayName("toLong: Number类型正确转换")
    void toLong_numberType_convertsCorrectly() {
        stubNewReportInsert(1L);
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("totalAmount", new BigDecimal("100"));
        paymentData.put("totalCount", 42L);
        Map<String, Object> ledgerData = buildLedgerSummary(100L, 42L);
        stubPaymentAndLedger(paymentData, ledgerData);
        stubEvidenceOk("0xhash");
        ReconcileReport reportWithHash = new ReconcileReport();
        reportWithHash.setId(1L);
        stubReportSelectById(reportWithHash);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(Long.valueOf(42L), report.getPaymentCount());
    }

    // ============== 边界条件与异常场景 ==============

    @Test
    @DisplayName("generateReport: 微小差异(0.01)判断为一致")
    void generateReport_minuteDiff_consideredConsistent() {
        stubNewReportInsert(1L);
        Map<String, Object> paymentData = buildPaymentSummary(new BigDecimal("1000.005"), 10L);
        Map<String, Object> ledgerData = buildLedgerSummary(1000L, 10L);
        stubPaymentAndLedger(paymentData, ledgerData);
        stubEvidenceOk("0xchainHash");
        ReconcileReport reportWithHash = new ReconcileReport();
        reportWithHash.setId(1L);
        stubReportSelectById(reportWithHash);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(1, report.getStatus());
    }

    @Test
    @DisplayName("generateReport: 支付成功但data为null使用默认值")
    void generateReport_paymentSuccessNoData_defaults() {
        stubNewReportInsert(1L);
        lenient().when(orderFeignClient.dailySummary(anyString())).thenReturn(R.ok(null));
        Map<String, Object> ledgerData = buildLedgerSummary(500L, 5L);
        lenient().when(lscLedgerFeignClient.dailySummary(any(LocalDate.class), anyString())).thenReturn(R.ok(ledgerData));

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(BigDecimal.ZERO, report.getPaymentTotalAmount());
    }

    @Test
    @DisplayName("generateReport: 账本成功但data为null使用默认值")
    void generateReport_ledgerSuccessNoData_defaults() {
        stubNewReportInsert(1L);
        Map<String, Object> paymentData = buildPaymentSummary(new BigDecimal("500.00"), 5L);
        lenient().when(orderFeignClient.dailySummary(anyString())).thenReturn(R.ok(paymentData));
        lenient().when(lscLedgerFeignClient.dailySummary(any(LocalDate.class), anyString())).thenReturn(R.ok(null));

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(Long.valueOf(0L), report.getLedgerCount());
    }

    @Test
    @DisplayName("generateReport: 已存在报告状态为0重新生成")
    void generateReport_existingZeroStatus_regenerates() {
        ReconcileReport existing = new ReconcileReport();
        existing.setId(10L);
        existing.setReconcileDate(TEST_DATE);
        existing.setStatus(0);
        when(reconcileReportMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        Map<String, Object> paymentData = buildPaymentSummary(new BigDecimal("1000.00"), 10L);
        Map<String, Object> ledgerData = buildLedgerSummary(1000L, 10L);
        stubPaymentAndLedger(paymentData, ledgerData);
        stubEvidenceOk("0xchainHash");
        ReconcileReport reportWithHash = new ReconcileReport();
        reportWithHash.setId(10L);
        stubReportSelectById(reportWithHash);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);

        assertNotNull(report);
        assertEquals(10L, report.getId().longValue());
        assertEquals(1, report.getStatus());
    }

    @Test
    @DisplayName("dailyReconcile: 锁被占用但不是当前线程不解锁")
    void dailyReconcile_lockNotHeld_noUnlock() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MINUTES))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        Long reportId = 1L;
        stubNewReportInsert(reportId);
        Map<String, Object> paymentData = buildPaymentSummary(new BigDecimal("100.00"), 1L);
        Map<String, Object> ledgerData = buildLedgerSummary(100L, 1L);
        stubPaymentAndLedger(paymentData, ledgerData);
        stubEvidenceOk("0xtxHash");
        ReconcileReport reportWithHash = new ReconcileReport();
        reportWithHash.setId(reportId);
        stubReportSelectById(reportWithHash);

        ReconcileReport report = reconciliationService.dailyReconcile(TEST_DATE);

        assertNotNull(report);
        verify(rLock, never()).unlock();
    }

    @Test
    @DisplayName("hashOnChain: evidenceFeignClient返回失败")
    void hashOnChain_evidenceFails_stillUpdates() {
        ReconcileReport report = new ReconcileReport();
        report.setId(300L);
        report.setResultHash("sha256hash300");

        when(reconcileReportMapper.selectById(300L)).thenReturn(report);
        when(evidenceFeignClient.saveEvidence(anyString(), anyString(), anyString()))
                .thenReturn(R.fail("存证服务不可用"));
        when(reconcileReportMapper.updateById(any(ReconcileReport.class))).thenReturn(1);

        String chainTxHash = reconciliationService.hashOnChain(300L);

        assertNull(chainTxHash);
        verify(reconcileReportMapper).updateById(any(ReconcileReport.class));
    }

    // ============== 追加：深处未覆盖 branch ==============

    @Test
    @DisplayName("dailyReconcile: tryLock 抛 InterruptedException -> 恢复中断标记 + 抛 BizException")
    void dailyReconcile_interrupted_throwBiz() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MINUTES)))
                .thenThrow(new InterruptedException("lock wakeup"));

        BizException ex = assertThrows(BizException.class,
                () -> reconciliationService.dailyReconcile(TEST_DATE));
        assertTrue(ex.getMessage().contains("对账任务被中断"));
        // 中断标记应当恢复
        assertTrue(Thread.currentThread().isInterrupted(), "中断标记应当恢复");
        // 清除中断防止污染其他测试
        Thread.interrupted();
    }

    @Test
    @DisplayName("generateReport: 支付 Feign 返回 R(ok=true, data=Map 中 totalAmount=String 数值) 走 String 分支")
    void generateReport_amountAsString_converts() {
        stubNewReportInsert(2L);
        Map<String, Object> payment = new HashMap<>();
        payment.put("totalAmount", "1500.00");
        payment.put("totalCount", "15");
        Map<String, Object> ledger = buildLedgerSummary(1500L, 15L);
        stubPaymentAndLedger(payment, ledger);
        stubEvidenceOk("0xtxStr");
        ReconcileReport rp = new ReconcileReport();
        rp.setId(2L);
        stubReportSelectById(rp);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);
        assertEquals(0, report.getDiffAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0L, report.getDiffCount());
        assertEquals(1, report.getStatus());
    }

    @Test
    @DisplayName("toLong: totalCount 为 Integer(小数字) 也应正确转为 long")
    void toLong_integerType_convertsCorrectly() {
        stubNewReportInsert(3L);
        Map<String, Object> payment = new HashMap<>();
        payment.put("totalAmount", new BigDecimal("100"));
        payment.put("totalCount", Integer.valueOf(7));
        Map<String, Object> ledger = buildLedgerSummary(100L, 7L);
        stubPaymentAndLedger(payment, ledger);
        stubEvidenceOk("0x");
        ReconcileReport rp = new ReconcileReport();
        rp.setId(3L);
        stubReportSelectById(rp);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);
        assertEquals(Long.valueOf(7L), report.getPaymentCount());
    }

    @Test
    @DisplayName("generateReport: orderFeign 返回 null resp -> 默认 0")
    void generateReport_paymentRespNull_defaults() {
        stubNewReportInsert(4L);
        when(orderFeignClient.dailySummary(anyString())).thenReturn(null);
        Map<String, Object> ledger = buildLedgerSummary(100L, 1L);
        when(lscLedgerFeignClient.dailySummary(any(LocalDate.class), anyString()))
                .thenReturn(R.ok(ledger));

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);
        assertEquals(BigDecimal.ZERO, report.getPaymentTotalAmount());
    }

    @Test
    @DisplayName("generateReport: ledger Feign resp=null -> 默认")
    void generateReport_ledgerRespNull_defaults() {
        stubNewReportInsert(5L);
        Map<String, Object> payment = buildPaymentSummary(new BigDecimal("100"), 1L);
        when(orderFeignClient.dailySummary(anyString())).thenReturn(R.ok(payment));
        when(lscLedgerFeignClient.dailySummary(any(LocalDate.class), anyString()))
                .thenReturn(null);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);
        assertEquals(Long.valueOf(0L), report.getLedgerCount());
    }

    @Test
    @DisplayName("generateReport: 一致情况 evidenceFeignClient.saveEvidence 抛 RuntimeException 不影响报告返回")
    void generateReport_evidenceThrowsCaughtSilently() {
        stubNewReportInsert(6L);
        Map<String, Object> payment = buildPaymentSummary(new BigDecimal("800"), 8L);
        Map<String, Object> ledger = buildLedgerSummary(800L, 8L);
        stubPaymentAndLedger(payment, ledger);
        ReconcileReport rp = new ReconcileReport();
        rp.setId(6L);
        stubReportSelectById(rp);
        when(evidenceFeignClient.saveEvidence(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("on chain RPC timeout"));

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);
        assertEquals(1, report.getStatus());
        assertNull(report.getChainTxHash());
    }

    @Test
    @DisplayName("generateReport: R(ok=false)/失败 -> 默认 0")
    void generateReport_paymentRFail_defaults() {
        stubNewReportInsert(7L);
        when(orderFeignClient.dailySummary(anyString())).thenReturn(R.fail("no data"));
        Map<String, Object> ledger = buildLedgerSummary(200L, 2L);
        when(lscLedgerFeignClient.dailySummary(any(LocalDate.class), anyString()))
                .thenReturn(R.ok(ledger));

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);
        assertEquals(BigDecimal.ZERO, report.getPaymentTotalAmount());
    }

    @Test
    @DisplayName("toBigDecimal: 整型 Double 数值也能正确转换")
    void toBigDecimal_fromIntegerConverts() {
        stubNewReportInsert(8L);
        Map<String, Object> payment = new HashMap<>();
        payment.put("totalAmount", 250);  // Integer
        payment.put("totalCount", 2L);
        Map<String, Object> ledger = buildLedgerSummary(250L, 2L);
        stubPaymentAndLedger(payment, ledger);
        stubEvidenceOk("0x");
        ReconcileReport rp = new ReconcileReport();
        rp.setId(8L);
        stubReportSelectById(rp);

        ReconcileReport report = reconciliationService.generateReport(TEST_DATE);
        assertEquals(0, report.getDiffAmount().compareTo(BigDecimal.ZERO));
    }
}
