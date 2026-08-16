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

@ExtendWith(MockitoExtension.class)
@DisplayName("核销服务扩展测试 - 并发/边界/异常场景")
class WriteOffServiceImplExtendedTest {

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

    private MerchantInfoDTO createMerchant(Integer penalty, Integer dailyLimit, LocalDate lastNh, String accountNo) {
        MerchantInfoDTO info = new MerchantInfoDTO();
        info.setMerchantId(1001L);
        info.setPenaltyStatus(penalty);
        info.setDailyNhLimit(dailyLimit);
        info.setLastNhDate(lastNh);
        info.setMainAccountNo(accountNo == null ? "ACC001" : accountNo);
        return info;
    }

    private Map<String, Object> balanceData(long total) {
        Map<String, Object> d = new HashMap<>();
        d.put("totalAvailable", total);
        return d;
    }

    @Test
    @DisplayName("applyWriteOff: InterruptException路径 - 正确恢复中断标志")
    void applyWriteOff_interruptedException_restoresFlag() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException("被中断"));

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertTrue(ex.getMessage().contains("被中断"));
        assertTrue(Thread.currentThread().isInterrupted(), "中断标志应被设置");
    }

    @Test
    @DisplayName("applyWriteOff: 处罚等级为null时使用默认NORMAL")
    void applyWriteOff_nullPenalty_usesNormalDefault() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchant(null, 200, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));
        when(lscLedgerFeignClient.getBalance(anyLong())).thenReturn(R.ok(balanceData(500L)));
        when(merchantNhRecordMapper.insert(any())).thenReturn(1);
        when(lscLedgerFeignClient.writeOffLsc(any())).thenReturn(R.ok(new HashMap<>()));
        when(merchantNhRecordMapper.updateById(any())).thenReturn(1);
        when(merchantFeignClient.updateLastNhDate(anyLong(), any())).thenReturn(R.ok());

        MerchantNhRecord result = writeOffService.applyWriteOff(dto);
        assertNotNull(result);
        assertEquals(WriteOffStatusEnum.SUCCESS.getCode(), result.getStatus());
    }

    @Test
    @DisplayName("applyWriteOff: dailyNhLimit为null时使用0导致限额校验失败")
    void applyWriteOff_nullDailyLimit_default0_rejects() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchant(MerchantPenaltyStatusEnum.NORMAL.getCode(), null, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertEquals(ResultCode.WRITE_OFF_LIMIT_EXCEEDED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("applyWriteOff: 账本余额查询返回null抛异常")
    void applyWriteOff_balanceReturnsNull_throws() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchant(MerchantPenaltyStatusEnum.NORMAL.getCode(), 200, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));
        when(lscLedgerFeignClient.getBalance(anyLong())).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertTrue(ex.getMessage().contains("账本余额查询失败"));
    }

    @Test
    @DisplayName("applyWriteOff: 账本余额查询失败(isSuccess=false)抛异常")
    void applyWriteOff_balanceFailed_throws() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchant(MerchantPenaltyStatusEnum.NORMAL.getCode(), 200, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));
        when(lscLedgerFeignClient.getBalance(anyLong())).thenReturn(R.fail(500, "余额查询失败"));

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertTrue(ex.getMessage().contains("账本余额查询失败"));
    }

    @Test
    @DisplayName("applyWriteOff: updateById返回0 - 乐观锁冲突")
    void applyWriteOff_updateByIdZero_throwsOptimisticLock() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchant(MerchantPenaltyStatusEnum.NORMAL.getCode(), 200, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));
        when(lscLedgerFeignClient.getBalance(anyLong())).thenReturn(R.ok(balanceData(500L)));
        when(merchantNhRecordMapper.insert(any())).thenReturn(1);
        when(lscLedgerFeignClient.writeOffLsc(any())).thenReturn(R.ok(new HashMap<>()));
        when(merchantNhRecordMapper.updateById(any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertTrue(ex.getMessage().contains("乐观锁"));
    }

    @Test
    @DisplayName("applyWriteOff: 更新lastNhDate失败不影响主流程")
    void applyWriteOff_updateLastNhDateFails_ignored() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchant(MerchantPenaltyStatusEnum.NORMAL.getCode(), 200, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));
        when(lscLedgerFeignClient.getBalance(anyLong())).thenReturn(R.ok(balanceData(500L)));
        when(merchantNhRecordMapper.insert(any())).thenReturn(1);
        when(lscLedgerFeignClient.writeOffLsc(any())).thenReturn(R.ok(new HashMap<>()));
        when(merchantNhRecordMapper.updateById(any())).thenReturn(1);
        when(merchantFeignClient.updateLastNhDate(anyLong(), any())).thenThrow(new RuntimeException("Feign失败"));

        MerchantNhRecord result = writeOffService.applyWriteOff(dto);
        assertNotNull(result);
        assertEquals(WriteOffStatusEnum.SUCCESS.getCode(), result.getStatus());
    }

    @Test
    @DisplayName("applyWriteOff: 核销失败时self.markRecordFailed也异常 - 主异常仍抛出")
    void applyWriteOff_markRecordFailedItselfFails_throwsOriginal() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(100L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchant(MerchantPenaltyStatusEnum.NORMAL.getCode(), 200, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));
        when(lscLedgerFeignClient.getBalance(anyLong())).thenReturn(R.ok(balanceData(500L)));
        when(merchantNhRecordMapper.insert(any())).thenReturn(1);
        when(lscLedgerFeignClient.writeOffLsc(any())).thenReturn(R.fail(500, "LSC销毁失败"));
        doThrow(new RuntimeException("标记失败也失败")).when(self).markRecordFailed(anyLong(), any(), anyString());

        BizException ex = assertThrows(BizException.class, () -> writeOffService.applyWriteOff(dto));
        assertEquals(ResultCode.SEATA_TRANSACTION_EXCEPTION.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("applyWriteOff: 一级处罚 - 成功流程验证限额减半")
    void applyWriteOff_level1Penalty_successWithHalfLimit() throws Exception {
        WriteOffApplyDTO dto = new WriteOffApplyDTO();
        dto.setMerchantId(1001L);
        dto.setLscAmount(50L);

        mockLockSuccess();
        MerchantInfoDTO merchant = createMerchant(MerchantPenaltyStatusEnum.LEVEL1.getCode(), 100, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));
        when(lscLedgerFeignClient.getBalance(anyLong())).thenReturn(R.ok(balanceData(500L)));
        when(merchantNhRecordMapper.insert(any())).thenReturn(1);
        when(lscLedgerFeignClient.writeOffLsc(any())).thenReturn(R.ok(new HashMap<>()));
        when(merchantNhRecordMapper.updateById(any())).thenReturn(1);
        when(merchantFeignClient.updateLastNhDate(anyLong(), any())).thenReturn(R.ok());

        MerchantNhRecord result = writeOffService.applyWriteOff(dto);
        assertNotNull(result);
        // 50 * 87 / 100 = 43.5
        assertEquals(0, new BigDecimal("43.50").compareTo(result.getCashAmount()));
    }

    // ==================== listRecords ====================

    @Test
    @DisplayName("listRecords: 分页参数null使用默认")
    void listRecords_nullPageParams_usesDefaults() {
        MerchantNhRecord record = new MerchantNhRecord();
        when(merchantNhRecordMapper.selectPage(any(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

        writeOffService.listRecords(null, null, null, null);

        verify(merchantNhRecordMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("listRecords: 完整过滤条件")
    void listRecords_withAllFilters() {
        writeOffService.listRecords(1, 10, 1001L, WriteOffStatusEnum.SUCCESS.getCode(),
                "BATCH001", "2026-08-01", "2026-08-31");

        verify(merchantNhRecordMapper).selectPage(any(), any());
    }

    // ==================== quota ====================

    @Test
    @DisplayName("quota: 每日限额为null使用默认80")
    void quota_nullDailyLimit_default80() {
        MerchantInfoDTO merchant = createMerchant(MerchantPenaltyStatusEnum.NORMAL.getCode(), null, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));
        when(merchantNhRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = writeOffService.quota(1001L);
        assertEquals(80, result.get("dailyLimit"));
    }

    @Test
    @DisplayName("quota: 剩余额度为负数时归0")
    void quota_overUsed_remainingIsZero() {
        MerchantInfoDTO merchant = createMerchant(MerchantPenaltyStatusEnum.NORMAL.getCode(), 100, null, "ACC001");
        when(merchantFeignClient.getMerchantInfo(anyLong())).thenReturn(R.ok(merchant));
        MerchantNhRecord rec = new MerchantNhRecord();
        rec.setLscAmount(200L);
        rec.setStatus(WriteOffStatusEnum.SUCCESS.getCode());
        when(merchantNhRecordMapper.selectList(any())).thenReturn(Arrays.asList(rec));

        Map<String, Object> result = writeOffService.quota(1001L);
        assertEquals(0L, result.get("todayRemaining"));
    }

    // ==================== toLong / calculateNhLimitLevel ====================

    @Test
    @DisplayName("toLong: null/字符串/数字转换正确")
    void toLong_variousInputs() {
        long r1 = org.springframework.test.util.ReflectionTestUtils.invokeMethod(writeOffService, "toLong", (Object) null);
        assertEquals(0L, r1);
        long r2 = org.springframework.test.util.ReflectionTestUtils.invokeMethod(writeOffService, "toLong", "123");
        assertEquals(123L, r2);
        long r3 = org.springframework.test.util.ReflectionTestUtils.invokeMethod(writeOffService, "toLong", 456L);
        assertEquals(456L, r3);
    }

    @Test
    @DisplayName("calculateNhLimitLevel: 超过最大档位返回16")
    void calculateNhLimitLevel_beyondMax_returns16() {
        Integer level = org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(writeOffService, "calculateNhLimitLevel", Integer.MAX_VALUE);
        assertEquals(16, level);
    }

    @Test
    @DisplayName("generateOrderNo: 格式验证")
    void generateOrderNo_format() {
        String orderNo = org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(writeOffService, "generateOrderNo", 123456789L);
        assertNotNull(orderNo);
        assertTrue(orderNo.startsWith("NH"));
        // 长度: NH(2) + yyyyMMddHHmmss(14) + 6 位 = 22
        assertEquals(22, orderNo.length());
    }
}
