package com.lianshengtong.writeoff.service.impl;

import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.enums.MerchantPenaltyStatusEnum;
import com.lianshengtong.common.enums.WriteOffStatusEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.writeoff.dto.MerchantInfoDTO;
import com.lianshengtong.writeoff.dto.WriteOffApplyDTO;
import com.lianshengtong.writeoff.entity.MerchantNhRecord;
import com.lianshengtong.writeoff.feign.LscLedgerFeignClient;
import com.lianshengtong.writeoff.feign.MerchantFeignClient;
import com.lianshengtong.writeoff.mapper.MerchantNhRecordMapper;
import com.lianshengtong.writeoff.service.WriteOffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 核销服务单元测试
 * <p>
 * 覆盖参数校验、并发锁、资格校验、次数校验、限额校验、余额校验、核销成功/失败流程、
 * 查询、统计、限额预览、失败标记、档位计算等关键路径。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("核销服务单元测试")
class WriteOffServiceImplTest {

    @Mock
    private MerchantNhRecordMapper merchantNhRecordMapper;
    @Mock
    private LscLedgerFeignClient lscLedgerFeignClient;
    @Mock
    private MerchantFeignClient merchantFeignClient;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rLock;
    @Mock
    private WriteOffService self;

    @InjectMocks
    private WriteOffServiceImpl writeOffService;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils
                .setField(writeOffService, "lockWaitMs", 3000L);
        org.springframework.test.util.ReflectionTestUtils
                .setField(writeOffService, "lockLeaseMs", 10000L);
        org.springframework.test.util.ReflectionTestUtils
                .setField(writeOffService, "cashRatioNumerator", 87);
        org.springframework.test.util.ReflectionTestUtils
                .setField(writeOffService, "cashRatioDenominator", 100);
        org.springframework.test.util.ReflectionTestUtils
                .setField(writeOffService, "level1LimitRatio", 50);
        org.springframework.test.util.ReflectionTestUtils
                .setField(writeOffService, "self", self);
    }

    private void mockLockSuccess() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    private MerchantInfoDTO createMerchantInfo(Integer penaltyStatus, Integer dailyNhLimit,
                                                LocalDate lastNhDate, String mainAccountNo) {
        MerchantInfoDTO info = new MerchantInfoDTO();
        info.setMerchantId(1001L);
        info.setPenaltyStatus(penaltyStatus);
        info.setDailyNhLimit(dailyNhLimit);
        info.setLastNhDate(lastNhDate);
        info.setMainAccountNo(mainAccountNo != null ? mainAccountNo : "ACC001");
        info.setCreditScore(100);
        return info;
    }

    private Map<String, Object> createBalanceData(long totalAvailable) {
        Map<String, Object> data = new HashMap<>();
        data.put("totalAvailable", totalAvailable);
        data.put("totalLocked", 0L);
        return data;
    }

    // ==================== applyWriteOff: 参数校验 ====================

    @Test
    @DisplayName("applyWriteOff: merchantId 为 null 应抛异常")
    void applyWriteOff_nullMerchantId() {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(null);
        dto.setLscAmount(100L);

        assertThrows(Exception.class, () -> writeOffService.applyWriteOff(dto));
    }

    @Test
    @DisplayName("applyWriteOff: lscAmount <= 0 应抛异常")
    void applyWriteOff_invalidAmount() {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(0L);

        assertThrows(Exception.class, () -> writeOffService.applyWriteOff(dto));
    }

    @Test
    @DisplayName("applyWriteOff: 获取锁失败应抛异常")
    void applyWriteOff_lockAcquireFailed() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertTrue(ex.getMessage().contains("核销处理中"));
    }

    // ==================== applyWriteOff: 商家信息查询 ====================

    @Test
    @DisplayName("applyWriteOff: 商家信息 Feign 返回 null 应抛 BizException")
    void applyWriteOff_merchantInfoReturnsNull_throwsBizException() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        mockLockSuccess();
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertEquals(ResultCode.MERCHANT_NOT_QUALIFIED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("applyWriteOff: 商家信息 Feign 调用失败应抛 BizException")
    void applyWriteOff_merchantInfoFeignFails_throwsBizException() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        mockLockSuccess();
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.fail(500, "服务异常"));

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertEquals(ResultCode.MERCHANT_NOT_QUALIFIED.getCode(), ex.getCode());
    }

    // ==================== applyWriteOff: 资格/次数/限额/余额校验 ====================

    @Test
    @DisplayName("applyWriteOff: 处罚等级 >= 2 应抛 MERCHANT_NOT_QUALIFIED")
    void applyWriteOff_penaltyLevel2_throwsMERCHANT_NOT_QUALIFIED() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchantInfo(
                MerchantPenaltyStatusEnum.LEVEL2.getCode(), 80, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertEquals(ResultCode.MERCHANT_NOT_QUALIFIED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("不具备核销资格"));
    }

    @Test
    @DisplayName("applyWriteOff: 今日已核销应抛 WRITE_OFF_DAILY_LIMIT")
    void applyWriteOff_dailyLimitExceeded_throwsWRITE_OFF_DAILY_LIMIT() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchantInfo(
                MerchantPenaltyStatusEnum.NORMAL.getCode(), 200, LocalDate.now(), "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertEquals(ResultCode.WRITE_OFF_DAILY_LIMIT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("applyWriteOff: lscAmount 超过有效限额应抛 WRITE_OFF_LIMIT_EXCEEDED")
    void applyWriteOff_lscAmountExceedsEffectiveLimit_throwsWRITE_OFF_LIMIT_EXCEEDED() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(60L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchantInfo(
                MerchantPenaltyStatusEnum.NORMAL.getCode(), 50, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertEquals(ResultCode.WRITE_OFF_LIMIT_EXCEEDED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("applyWriteOff: 一级处罚时有效限额减半，lscAmount 超限额应抛异常")
    void applyWriteOff_level1Penalty_limitHalved_throwsWRITE_OFF_LIMIT_EXCEEDED() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(60L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchantInfo(
                MerchantPenaltyStatusEnum.LEVEL1.getCode(), 100, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertEquals(ResultCode.WRITE_OFF_LIMIT_EXCEEDED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("applyWriteOff: LSC 余额不足应抛 LSC_BALANCE_INSUFFICIENT")
    void applyWriteOff_balanceInsufficient_throwsLSC_BALANCE_INSUFFICIENT() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchantInfo(
                MerchantPenaltyStatusEnum.NORMAL.getCode(), 200, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));
        when(lscLedgerFeignClient.getBalance(anyLong()))
                .thenReturn(R.ok(createBalanceData(50L)));

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertEquals(ResultCode.LSC_BALANCE_INSUFFICIENT.getCode(), ex.getCode());
    }

    // ==================== applyWriteOff: 成功/失败流程 ====================

    @Test
    @DisplayName("applyWriteOff: 完整核销成功流程")
    void applyWriteOff_successfulFlow() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchantInfo(
                MerchantPenaltyStatusEnum.NORMAL.getCode(), 200, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));
        when(lscLedgerFeignClient.getBalance(anyLong()))
                .thenReturn(R.ok(createBalanceData(500L)));
        when(merchantNhRecordMapper.insert(any(MerchantNhRecord.class))).thenReturn(1);
        when(lscLedgerFeignClient.writeOffLsc(any(LscLedgerOpDTO.class)))
                .thenReturn(R.ok(new HashMap<>()));
        when(merchantNhRecordMapper.updateById(any(MerchantNhRecord.class))).thenReturn(1);
        when(merchantFeignClient.updateLastNhDate(anyLong(), any(LocalDate.class)))
                .thenReturn(R.ok());

        MerchantNhRecord result = writeOffService.applyWriteOff(dto);

        assertNotNull(result);
        assertEquals(1001L, result.getMerchantId());
        assertEquals(Long.valueOf(100L), result.getLscAmount());
        assertEquals(WriteOffStatusEnum.SUCCESS.getCode(), result.getStatus());
        assertNotNull(result.getOrderNo());
        assertNotNull(result.getId());
        assertEquals(Integer.valueOf(1), result.getVersion());
        BigDecimal expectedCash = BigDecimal.valueOf(100L)
                .multiply(BigDecimal.valueOf(87))
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        assertEquals(0, expectedCash.compareTo(result.getCashAmount()));

        verify(merchantNhRecordMapper).insert(any(MerchantNhRecord.class));
        verify(lscLedgerFeignClient).writeOffLsc(any(LscLedgerOpDTO.class));
        verify(merchantNhRecordMapper).updateById(any(MerchantNhRecord.class));
        verify(merchantFeignClient).updateLastNhDate(eq(1001L), eq(LocalDate.now()));
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("applyWriteOff: 账本 LSC 销毁失败应标记记录失败")
    void applyWriteOff_writeOffLscFails_callsMarkRecordFailed() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchantInfo(
                MerchantPenaltyStatusEnum.NORMAL.getCode(), 200, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));
        when(lscLedgerFeignClient.getBalance(anyLong()))
                .thenReturn(R.ok(createBalanceData(500L)));
        when(merchantNhRecordMapper.insert(any(MerchantNhRecord.class))).thenReturn(1);
        when(lscLedgerFeignClient.writeOffLsc(any(LscLedgerOpDTO.class)))
                .thenReturn(R.fail(500, "LSC销毁失败"));

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertEquals(ResultCode.SEATA_TRANSACTION_EXCEPTION.getCode(), ex.getCode());

        verify(self).markRecordFailed(anyLong(), any(), anyString());
        verify(rLock).unlock();
    }

    // ==================== getByOrderNo ====================

    @Test
    @DisplayName("getByOrderNo: 记录存在应成功返回")
    void getByOrderNo_recordExists_returnsSuccessfully() {
        MerchantNhRecord record = new MerchantNhRecord();
        record.setId(1L);
        record.setOrderNo("NH20260806000001");
        record.setMerchantId(1001L);
        record.setLscAmount(100L);
        record.setStatus(WriteOffStatusEnum.SUCCESS.getCode());
        record.setVersion(1);

        when(merchantNhRecordMapper.selectOne(any())).thenReturn(record);

        MerchantNhRecord result = writeOffService.getByOrderNo("NH20260806000001");

        assertNotNull(result);
        assertEquals("NH20260806000001", result.getOrderNo());
        assertEquals(Long.valueOf(1001L), result.getMerchantId());
        assertEquals(WriteOffStatusEnum.SUCCESS.getCode(), result.getStatus());
    }

    @Test
    @DisplayName("getByOrderNo: 记录不存在应抛 BizException")
    void getByOrderNo_recordNotFound_throwsBizException() {
        when(merchantNhRecordMapper.selectOne(any())).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> writeOffService.getByOrderNo("NOT_EXIST_ORDER"));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== getById ====================

    @Test
    @DisplayName("getById: 记录存在应成功返回")
    void getById_recordExists_returnsSuccessfully() {
        MerchantNhRecord record = new MerchantNhRecord();
        record.setId(999L);
        record.setOrderNo("NH20260806000099");
        record.setMerchantId(1001L);
        record.setLscAmount(50L);
        record.setStatus(WriteOffStatusEnum.PROCESSING.getCode());

        when(merchantNhRecordMapper.selectById(999L)).thenReturn(record);

        MerchantNhRecord result = writeOffService.getById(999L);

        assertNotNull(result);
        assertEquals(Long.valueOf(999L), result.getId());
        assertEquals("NH20260806000099", result.getOrderNo());
    }

    // ==================== stats ====================

    @Test
    @DisplayName("stats: 按商家ID和日期范围过滤统计")
    void stats_filtersByMerchantIdAndDateRange() {
        MerchantNhRecord record1 = new MerchantNhRecord();
        record1.setMerchantId(1001L);
        record1.setLscAmount(100L);
        record1.setCashAmount(new BigDecimal("87.00"));
        record1.setStatus(WriteOffStatusEnum.SUCCESS.getCode());

        MerchantNhRecord record2 = new MerchantNhRecord();
        record2.setMerchantId(1001L);
        record2.setLscAmount(50L);
        record2.setCashAmount(new BigDecimal("43.50"));
        record2.setStatus(WriteOffStatusEnum.SUCCESS.getCode());

        MerchantNhRecord record3 = new MerchantNhRecord();
        record3.setMerchantId(1001L);
        record3.setLscAmount(30L);
        record3.setCashAmount(new BigDecimal("26.10"));
        record3.setStatus(WriteOffStatusEnum.FAILED.getCode());

        List<MerchantNhRecord> records = Arrays.asList(record1, record2, record3);
        when(merchantNhRecordMapper.selectList(any())).thenReturn(records);

        Map<String, Object> result = writeOffService.stats(1001L, "2026-08-01", "2026-08-31");

        assertNotNull(result);
        assertEquals(3L, result.get("totalCount"));
        assertEquals(180L, result.get("totalLscAmount"));
        BigDecimal totalCash = (BigDecimal) result.get("totalCashAmount");
        assertEquals(0, new BigDecimal("156.60").compareTo(totalCash));

        @SuppressWarnings("unchecked")
        Map<Integer, Long> byStatus = (Map<Integer, Long>) result.get("byStatus");
        assertNotNull(byStatus);
        assertEquals(2L, byStatus.get(WriteOffStatusEnum.SUCCESS.getCode()));
        assertEquals(1L, byStatus.get(WriteOffStatusEnum.FAILED.getCode()));
    }

    // ==================== quota ====================

    @Test
    @DisplayName("quota: 返回每日限额和剩余额度")
    void quota_returnsDailyLimitAndRemaining() {
        MerchantInfoDTO merchant = createMerchantInfo(
                MerchantPenaltyStatusEnum.NORMAL.getCode(), 200, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));

        MerchantNhRecord todayRecord = new MerchantNhRecord();
        todayRecord.setMerchantId(1001L);
        todayRecord.setLscAmount(50L);
        todayRecord.setStatus(WriteOffStatusEnum.SUCCESS.getCode());

        when(merchantNhRecordMapper.selectList(any()))
                .thenReturn(Arrays.asList(todayRecord));

        Map<String, Object> result = writeOffService.quota(1001L);

        assertNotNull(result);
        assertEquals(200, result.get("dailyLimit"));
        assertEquals(50L, result.get("todayUsed"));
        assertEquals(150L, result.get("todayRemaining"));
        assertEquals(3, result.get("nhLimitLevel"));
        assertEquals(new BigDecimal("0.87"), result.get("cashRate"));
    }

    @Test
    @DisplayName("quota: 商家信息查询失败应抛 BizException")
    void quota_merchantInfoFails_throwsBizException() {
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> writeOffService.quota(999L));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== markRecordFailed ====================

    @Test
    @DisplayName("markRecordFailed: 更新状态为失败并记录失败原因")
    void markRecordFailed_updatesStatusAndFailReason() {
        writeOffService.markRecordFailed(1L, 1, "测试失败原因");

        verify(merchantNhRecordMapper).updateById(argThat(record -> {
            boolean statusFailed = record.getStatus() == WriteOffStatusEnum.FAILED.getCode();
            boolean reasonMatch = "测试失败原因".equals(record.getFailReason());
            boolean idMatch = Long.valueOf(1L).equals(record.getId());
            return statusFailed && reasonMatch && idMatch;
        }));
    }

    // ==================== calculateNhLimitLevel ====================

    @Test
    @DisplayName("calculateNhLimitLevel:  Various daily limit values produce correct levels")
    void calculateNhLimitLevel_variousValues() {
        assertEquals(0L, ((Number) org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(writeOffService, "calculateNhLimitLevel", 0)).longValue());
        assertEquals(1L, ((Number) org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(writeOffService, "calculateNhLimitLevel", 80)).longValue());
        assertEquals(2L, ((Number) org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(writeOffService, "calculateNhLimitLevel", 81)).longValue());
        assertEquals(2L, ((Number) org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(writeOffService, "calculateNhLimitLevel", 160)).longValue());
        assertEquals(3L, ((Number) org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(writeOffService, "calculateNhLimitLevel", 161)).longValue());
        assertEquals(5L, ((Number) org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(writeOffService, "calculateNhLimitLevel", 1280)).longValue());
        assertEquals(6L, ((Number) org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(writeOffService, "calculateNhLimitLevel", 1281)).longValue());
        assertEquals(9L, ((Number) org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(writeOffService, "calculateNhLimitLevel", 20000)).longValue());
        assertEquals(15L, ((Number) org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(writeOffService, "calculateNhLimitLevel", 1000000)).longValue());
        assertEquals(16L, ((Number) org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(writeOffService, "calculateNhLimitLevel", 2000000)).longValue());
    }
}