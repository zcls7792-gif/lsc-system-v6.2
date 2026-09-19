package com.lianshengtong.ledger.service.impl;

import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.enums.LscTransactionTypeEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.ledger.entity.AvailableLscDetail;
import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.entity.LscTransaction;
import com.lianshengtong.ledger.mapper.AvailableLscDetailMapper;
import com.lianshengtong.ledger.mapper.LscAccountMapper;
import com.lianshengtong.ledger.mapper.LscTransactionMapper;
import com.lianshengtong.ledger.service.LscAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("账本服务单元测试 (V7.3)")
class LscLedgerServiceImplTest {

    @Mock
    private LscAccountMapper accountMapper;
    @Mock
    private LscTransactionMapper transactionMapper;
    @Mock
    private AvailableLscDetailMapper detailMapper;
    @Mock
    private LscAccountService accountService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private RLock rLock;

    @InjectMocks
    private LscLedgerServiceImpl ledgerService;

    @BeforeEach
    void setUp() throws Exception {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        ReflectionTestUtils.setField(ledgerService, "transactionTemplate", txTemplate);
        ReflectionTestUtils.setField(ledgerService, "lockWaitMs", 3000L);
        ReflectionTestUtils.setField(ledgerService, "lockLeaseMs", 10000L);
        ReflectionTestUtils.setField(ledgerService, "detailValidityDays", 365);
        ReflectionTestUtils.setField(ledgerService, "optimisticLockEnabled", false);

        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        lenient().when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    private LscAccount buildAccount(Long userId, long locked, long available) {
        LscAccount acc = new LscAccount();
        acc.setUserId(userId);
        acc.setTotalLocked(locked);
        acc.setTotalAvailable(available);
        acc.setVersion(1);
        return acc;
    }

    // ============== issueLsc 测试 ==============

    @Test
    @DisplayName("issueLsc: amount 为 null 或 <= 0 应抛异常")
    void issueLsc_invalidAmount() {
        assertThrows(BizException.class, () -> ledgerService.issueLsc(1001L, null, "OD001"));
        assertThrows(BizException.class, () -> ledgerService.issueLsc(1001L, 0L, "OD001"));
        assertThrows(BizException.class, () -> ledgerService.issueLsc(1001L, -100L, "OD001"));
    }

    @Test
    @DisplayName("issueLsc: 成功发行创建账户和流水")
    void issueLsc_success() {
        Long userId = 1001L;
        Long amount = 200L;
        String orderNo = "ISSUE_001";

        LscAccount acc = buildAccount(userId, 0L, 0L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        LscAccount result = ledgerService.issueLsc(userId, amount, orderNo);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(200L, result.getTotalLocked());
        verify(accountMapper).updateById(any(LscAccount.class));
        verify(transactionMapper).insert(any(LscTransaction.class));
    }

    @Test
    @DisplayName("issueLsc: 幂等键重复时返回已有账户")
    void issueLsc_idempotentKeyExists() {
        Long userId = 1001L;
        Long amount = 200L;
        String orderNo = "ISSUE_001";

        LscAccount existingAcc = buildAccount(userId, 200L, 0L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(new LscTransaction());
        when(accountMapper.selectById(userId)).thenReturn(existingAcc);

        LscAccount result = ledgerService.issueLsc(userId, amount, orderNo);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(200L, result.getTotalLocked());
        verify(accountMapper, never()).updateById(any(LscAccount.class));
        verify(transactionMapper, never()).insert(any(LscTransaction.class));
    }

    @Test
    @DisplayName("issueLsc: 账户更新乐观锁冲突抛异常")
    void issueLsc_optimisticLockConflict() {
        LscAccount acc = buildAccount(1001L, 0L, 500L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(0);

        assertThrows(BizException.class,
                () -> ledgerService.issueLsc(1001L, 200L, "OD001"));
    }

    // ============== refundLsc 测试 ==============

    @Test
    @DisplayName("refundLsc: amount <= 0 应抛异常")
    void refundLsc_invalidAmount() {
        assertThrows(BizException.class, () -> ledgerService.refundLsc(1001L, 0L, "OD001"));
        assertThrows(BizException.class, () -> ledgerService.refundLsc(1001L, -1L, "OD001"));
    }

    @Test
    @DisplayName("refundLsc: 成功退款恢复可用余额")
    void refundLsc_success() {
        Long userId = 1001L;
        Long amount = 100L;
        String orderNo = "REFUND_001";

        LscAccount acc = buildAccount(userId, 0L, 50L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);
        when(detailMapper.insert(any(AvailableLscDetail.class))).thenReturn(1);

        LscAccount result = ledgerService.refundLsc(userId, amount, orderNo);

        assertNotNull(result);
        assertEquals(150L, result.getTotalAvailable());
        verify(accountMapper).updateById(any(LscAccount.class));
        verify(detailMapper).insert(any(AvailableLscDetail.class));
    }

    @Test
    @DisplayName("refundLsc: 幂等键重复返回已有账户")
    void refundLsc_idempotentKeyExists() {
        Long userId = 1001L;
        Long amount = 100L;
        String orderNo = "REFUND_DUPLICATE";

        LscAccount existingAcc = buildAccount(userId, 0L, 200L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(new LscTransaction());
        when(accountMapper.selectById(userId)).thenReturn(existingAcc);

        LscAccount result = ledgerService.refundLsc(userId, amount, orderNo);

        assertNotNull(result);
        assertEquals(200L, result.getTotalAvailable());
    }

    // ============== releaseLsc 测试 ==============

    @Test
    @DisplayName("releaseLsc: amount <= 0 应抛异常")
    void releaseLsc_invalidAmount() {
        assertThrows(BizException.class, () -> ledgerService.releaseLsc(1001L, 0L, "OD001"));
        assertThrows(BizException.class, () -> ledgerService.releaseLsc(1001L, -1L, "OD001"));
    }

    @Test
    @DisplayName("releaseLsc: 成功释放锁定转可用")
    void releaseLsc_success() {
        Long userId = 1001L;
        Long amount = 100L;
        String orderNo = "RELEASE_001";

        LscAccount acc = buildAccount(userId, 200L, 0L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);
        when(detailMapper.insert(any(AvailableLscDetail.class))).thenReturn(1);

        LscAccount result = ledgerService.releaseLsc(userId, amount, orderNo);

        assertNotNull(result);
        assertEquals(100L, result.getTotalLocked());
        assertEquals(100L, result.getTotalAvailable());
    }

    @Test
    @DisplayName("releaseLsc: 锁定不足抛异常")
    void releaseLsc_lockedInsufficient() {
        LscAccount acc = buildAccount(1001L, 100L, 500L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenThrow(new BizException(ResultCode.LSC_LOCKED_INSUFFICIENT));

        assertThrows(BizException.class,
                () -> ledgerService.releaseLsc(1001L, 200L, "REL_LOCKED"));
    }

    // ============== releaseBatch 测试 ==============

    @Test
    @DisplayName("releaseBatch: 空列表返回 0 统计")
    void releaseBatch_emptyList() {
        List<LscLedgerOpDTO> empty = Collections.emptyList();
        Map<String, Object> result = ledgerService.releaseBatch(empty);

        assertEquals(0, result.get("total"));
        assertEquals(0, result.get("successCount"));
        assertEquals(0, result.get("failedCount"));
        assertEquals(0L, result.get("releasedAmount"));
    }

    @Test
    @DisplayName("releaseBatch: null 参数返回 0 统计")
    void releaseBatch_nullList() {
        Map<String, Object> result = ledgerService.releaseBatch(null);

        assertEquals(0, result.get("total"));
        assertEquals(0, result.get("successCount"));
    }

    @Test
    @DisplayName("releaseBatch: 多用户分组批量释放")
    void releaseBatch_multiUser() {
        LscAccount acc1 = buildAccount(1001L, 500L, 0L);
        LscAccount acc2 = buildAccount(1002L, 300L, 0L);

        List<LscLedgerOpDTO> ops = Arrays.asList(
                LscLedgerOpDTO.builder().userId(1001L).lockedDelta(-100L).availableDelta(100L).orderNo("REL_1").build(),
                LscLedgerOpDTO.builder().userId(1001L).lockedDelta(-50L).availableDelta(50L).orderNo("REL_2").build(),
                LscLedgerOpDTO.builder().userId(1002L).lockedDelta(-200L).availableDelta(200L).orderNo("REL_3").build()
        );

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc1);
        when(accountService.getOrCreateAccount(1002L)).thenReturn(acc2);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);
        when(detailMapper.insert(any(AvailableLscDetail.class))).thenReturn(1);

        Map<String, Object> result = ledgerService.releaseBatch(ops);

        assertEquals(3, result.get("total"));
        assertEquals(3, result.get("successCount"));
        assertEquals(0, result.get("failedCount"));
        assertEquals(350L, result.get("releasedAmount"));
    }

    @Test
    @DisplayName("releaseBatch: 单个用户释放")
    void releaseBatch_singleUser() {
        LscAccount acc = buildAccount(1001L, 200L, 0L);

        List<LscLedgerOpDTO> ops = Collections.singletonList(
                LscLedgerOpDTO.builder().userId(1001L).lockedDelta(-80L).availableDelta(80L).orderNo("REL_SINGLE").build()
        );

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);
        when(detailMapper.insert(any(AvailableLscDetail.class))).thenReturn(1);

        Map<String, Object> result = ledgerService.releaseBatch(ops);

        assertEquals(1, result.get("successCount"));
        assertEquals(80L, result.get("releasedAmount"));
    }

    @Test
    @DisplayName("releaseBatch: 用户锁获取失败返回部分成功部分失败")
    void releaseBatch_lockAcquireFails() throws Exception {
        LscAccount acc1 = buildAccount(1001L, 500L, 0L);

        List<LscLedgerOpDTO> ops = Arrays.asList(
                LscLedgerOpDTO.builder().userId(1001L).lockedDelta(-100L).availableDelta(100L).orderNo("REL_OK").build(),
                LscLedgerOpDTO.builder().userId(1002L).lockedDelta(-50L).availableDelta(50L).orderNo("REL_FAIL").build()
        );

        RLock lock1 = mock(RLock.class);
        RLock lock2 = mock(RLock.class);
        when(redissonClient.getLock("lsc:ledger:lock:1001")).thenReturn(lock1);
        when(redissonClient.getLock("lsc:ledger:lock:1002")).thenReturn(lock2);
        when(lock1.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock1.isHeldByCurrentThread()).thenReturn(true);
        when(lock2.tryLock(anyLong(), anyLong(), any())).thenReturn(false);

        lenient().when(accountService.getOrCreateAccount(anyLong())).thenReturn(acc1);
        lenient().when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        lenient().when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);
        lenient().when(detailMapper.insert(any(AvailableLscDetail.class))).thenReturn(1);
        lenient().when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);

        Map<String, Object> result = ledgerService.releaseBatch(ops);

        assertEquals(2, result.get("total"));
        assertEquals(1, result.get("failedCount"));
        verify(lock2, never()).unlock();
    }

    @Test
    @DisplayName("releaseBatch: 锁获取被中断时正确处理")
    void releaseBatch_lockInterruptedException() throws Exception {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("lsc:ledger:lock:1001")).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new InterruptedException("锁获取被中断"));

        List<LscLedgerOpDTO> ops = Collections.singletonList(
                LscLedgerOpDTO.builder().userId(1001L).lockedDelta(-100L).availableDelta(100L).orderNo("REL_INT").build()
        );

        Map<String, Object> result = ledgerService.releaseBatch(ops);

        assertEquals(1, result.get("total"));
        assertEquals(0, result.get("successCount"));
        assertEquals(1, result.get("failedCount"));
        assertEquals(0L, result.get("releasedAmount"));
    }

    @Test
    @DisplayName("releaseBatch: 用户处理异常时不影响其他用户")
    void releaseBatch_userExceptionIsolated() {
        LscAccount acc = buildAccount(1001L, 500L, 0L);

        List<LscLedgerOpDTO> ops = Arrays.asList(
                LscLedgerOpDTO.builder().userId(1001L).lockedDelta(-100L).availableDelta(100L).orderNo("REL_OK").build(),
                LscLedgerOpDTO.builder().userId(1002L).lockedDelta(-50L).availableDelta(50L).orderNo("REL_ERR").build()
        );

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);
        when(accountService.getOrCreateAccount(1002L))
                .thenThrow(new RuntimeException("账户获取异常"));
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);
        when(detailMapper.insert(any(AvailableLscDetail.class))).thenReturn(1);

        Map<String, Object> result = ledgerService.releaseBatch(ops);

        assertEquals(2, result.get("total"));
        assertEquals(1, result.get("successCount"));
        assertEquals(1, result.get("failedCount"));
        assertEquals(100L, result.get("releasedAmount"));
    }

    // ============== getBalance 测试 ==============

    @Test
    @DisplayName("getBalance: 不存在的用户返回零余额账户")
    void getBalance_nonExistentUser() {
        when(accountMapper.selectById(9999L)).thenReturn(null);

        LscAccount result = ledgerService.getBalance(9999L);
        assertNotNull(result);
        assertEquals(9999L, result.getUserId());
        assertEquals(0L, result.getTotalLocked());
        assertEquals(0L, result.getTotalAvailable());
        assertEquals(0L, result.getTotalFrozen());
    }

    @Test
    @DisplayName("getBalance: 已存在用户正常返回")
    void getBalance_existingUser() {
        LscAccount account = new LscAccount();
        account.setUserId(1001L);
        account.setTotalLocked(5000L);
        account.setTotalAvailable(3000L);
        when(accountMapper.selectById(1001L)).thenReturn(account);

        LscAccount result = ledgerService.getBalance(1001L);
        assertNotNull(result);
        assertEquals(1001L, result.getUserId());
        assertEquals(5000L, result.getTotalLocked());
        assertEquals(3000L, result.getTotalAvailable());
    }

    @Test
    @DisplayName("getBalance: 不存在用户返回零余额")
    void getBalance_nonExistentUserZeroBalance() {
        when(accountMapper.selectById(8888L)).thenReturn(null);

        LscAccount result = ledgerService.getBalance(8888L);

        assertNotNull(result);
        assertEquals(8888L, result.getUserId());
        assertEquals(0L, result.getTotalLocked());
        assertEquals(0L, result.getTotalAvailable());
        assertEquals(0, result.getVersion());
    }

    @Test
    @DisplayName("getBalance: 返回 totalLocked/totalAvailable 正确")
    void getBalance_returnsCorrectTotals() {
        LscAccount account = new LscAccount();
        account.setUserId(1001L);
        account.setTotalLocked(12000L);
        account.setTotalAvailable(8000L);
        account.setVersion(3);
        when(accountMapper.selectById(1001L)).thenReturn(account);

        LscAccount result = ledgerService.getBalance(1001L);

        assertNotNull(result);
        assertEquals(1001L, result.getUserId());
        assertEquals(12000L, result.getTotalLocked());
        assertEquals(8000L, result.getTotalAvailable());
    }

    // ============== dailySummary 测试 ==============

    @Test
    @DisplayName("dailySummary: 无数据返回 0")
    void dailySummary_noData() {
        when(transactionMapper.aggregateByTimeRange(any(), any(), any())).thenReturn(null);

        Map<String, Object> result = ledgerService.dailySummary(null, null);

        assertEquals(0L, result.get("totalAmount"));
        assertEquals(0L, result.get("totalCount"));
    }

    @Test
    @DisplayName("dailySummary: 有数据正确聚合")
    void dailySummary_withData() {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("totalAmount", 5000L);
        row.put("totalCount", 10L);
        List<java.util.Map<String, Object>> rows = Collections.singletonList(row);
        when(transactionMapper.aggregateByTimeRange(any(), any(), any())).thenReturn(rows);

        Map<String, Object> result = ledgerService.dailySummary(null, null);

        assertEquals(5000L, result.get("totalAmount"));
        assertEquals(10L, result.get("totalCount"));
    }

    // ============== lockedSummary 测试 ==============

    @Test
    @DisplayName("lockedSummary: 正确聚合锁定余额")
    void lockedSummary_aggregatesCorrectly() {
        LscAccount acc1 = buildAccount(1001L, 500L, 0L);
        LscAccount acc2 = buildAccount(1002L, 300L, 0L);
        LscAccount acc3 = buildAccount(1003L, 0L, 100L);
        when(accountMapper.selectAllLockedAccounts())
                .thenReturn(Arrays.asList(acc1, acc2, acc3));

        Map<String, Object> result = ledgerService.lockedSummary();

        assertEquals(800L, result.get("totalLocked"));
        assertEquals(3, result.get("userCount"));
    }

    @Test
    @DisplayName("lockedSummary: 无锁定账户返回 0")
    void lockedSummary_noLockedAccounts() {
        when(accountMapper.selectAllLockedAccounts()).thenReturn(Collections.emptyList());

        Map<String, Object> result = ledgerService.lockedSummary();

        assertEquals(0L, result.get("totalLocked"));
        assertEquals(0, result.get("userCount"));
    }

    // ============== recentTrend 测试 ==============

    @Test
    @DisplayName("recentTrend: 负天数默认7天")
    void recentTrend_negativeDays() {
        when(transactionMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = ledgerService.recentTrend(1001L, -5);

        assertEquals(7, result.size());
    }

    // ============== deductLsc 测试 (V7.3 新增) ==============

    @Test
    @DisplayName("deductLsc: amount <= 0 应抛异常")
    void deductLsc_invalidAmount() {
        assertThrows(BizException.class, () -> ledgerService.deductLsc(1001L, 0L, "OD001"));
        assertThrows(BizException.class, () -> ledgerService.deductLsc(1001L, -1L, "OD001"));
    }

    @Test
    @DisplayName("deductLsc: 成功抵扣扣减可用余额")
    void deductLsc_success() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 0L, 500L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        LscAccount result = ledgerService.deductLsc(userId, 200L, "DED_001");

        assertEquals(300L, result.getTotalAvailable());
        verify(transactionMapper).insert(argThat(tx ->
                tx.getType() == LscTransactionTypeEnum.ORDER_DEDUCT.getCode()
                        && tx.getAmount() == 200L));
    }

    @Test
    @DisplayName("deductLsc: 可用余额不足抛异常")
    void deductLsc_insufficientAvailable() {
        LscAccount acc = buildAccount(1001L, 0L, 100L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);

        BizException ex = assertThrows(BizException.class,
                () -> ledgerService.deductLsc(1001L, 200L, "DED_INSUF"));
        assertEquals(ResultCode.LSC_BALANCE_INSUFFICIENT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("deductLsc: 幂等键重复返回已有账户")
    void deductLsc_idempotent() {
        LscAccount existing = buildAccount(1001L, 0L, 300L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(new LscTransaction());
        when(accountMapper.selectById(1001L)).thenReturn(existing);

        LscAccount result = ledgerService.deductLsc(1001L, 200L, "DED_DUP");
        assertEquals(300L, result.getTotalAvailable());
        verify(accountMapper, never()).updateById(any());
    }

    // ============== refundDeductLsc 测试 (V7.3 新增) ==============

    @Test
    @DisplayName("refundDeductLsc: 成功扣回锁定池赠送LSC")
    void refundDeductLsc_success() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 500L, 0L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        LscAccount result = ledgerService.refundDeductLsc(userId, 200L, "RD_001");

        assertEquals(300L, result.getTotalLocked());
        verify(transactionMapper).insert(argThat(tx ->
                tx.getType() == LscTransactionTypeEnum.REFUND_DEDUCT.getCode()
                        && tx.getAmount() == 200L));
    }

    @Test
    @DisplayName("refundDeductLsc: 锁定余额不足抛异常")
    void refundDeductLsc_insufficientLocked() {
        LscAccount acc = buildAccount(1001L, 100L, 0L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);

        BizException ex = assertThrows(BizException.class,
                () -> ledgerService.refundDeductLsc(1001L, 200L, "RD_LOCK_NEG"));
        assertEquals(ResultCode.LSC_LOCKED_INSUFFICIENT.getCode(), ex.getCode());
    }

    // ============== expireWriteoff 测试 (V7.3 新增) ==============

    @Test
    @DisplayName("expireWriteoff: 成功作废扣减可用余额")
    void expireWriteoff_success() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 0L, 500L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        LscAccount result = ledgerService.expireWriteoff(userId, 100L, "EXP_001");

        assertEquals(400L, result.getTotalAvailable());
        verify(transactionMapper).insert(argThat(tx ->
                tx.getType() == LscTransactionTypeEnum.EXPIRE_WRITEOFF.getCode()));
    }

    // ============== freezeLsc 测试 (V7.3 新增) ==============

    @Test
    @DisplayName("freezeLsc: 成功冻结可用转冻结池")
    void freezeLsc_success() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 0L, 500L);
        acc.setTotalFrozen(0L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        LscAccount result = ledgerService.freezeLsc(userId, 200L, "FRZ_001");

        assertEquals(300L, result.getTotalAvailable());
        assertEquals(200L, result.getTotalFrozen());
        verify(transactionMapper).insert(argThat(tx ->
                tx.getType() == LscTransactionTypeEnum.RISK_FREEZE.getCode()
                        && tx.getBeforeFrozen() == 0L
                        && tx.getAfterFrozen() == 200L));
    }

    @Test
    @DisplayName("freezeLsc: 可用余额不足抛异常")
    void freezeLsc_insufficientAvailable() {
        LscAccount acc = buildAccount(1001L, 0L, 100L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);

        BizException ex = assertThrows(BizException.class,
                () -> ledgerService.freezeLsc(1001L, 200L, "FRZ_INSUF"));
        assertEquals(ResultCode.LSC_BALANCE_INSUFFICIENT.getCode(), ex.getCode());
    }

    // ============== unfreezeLsc 测试 (V7.3 新增) ==============

    @Test
    @DisplayName("unfreezeLsc: 成功解冻冻结池转可用")
    void unfreezeLsc_success() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 0L, 100L);
        acc.setTotalFrozen(500L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        LscAccount result = ledgerService.unfreezeLsc(userId, 200L, "UFRZ_001");

        assertEquals(300L, result.getTotalAvailable());
        assertEquals(300L, result.getTotalFrozen());
        verify(transactionMapper).insert(argThat(tx ->
                tx.getType() == LscTransactionTypeEnum.RISK_UNFREEZE.getCode()
                        && tx.getBeforeFrozen() == 500L
                        && tx.getAfterFrozen() == 300L));
    }

    @Test
    @DisplayName("unfreezeLsc: 冻结余额不足抛异常")
    void unfreezeLsc_insufficientFrozen() {
        LscAccount acc = buildAccount(1001L, 0L, 100L);
        acc.setTotalFrozen(50L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);

        BizException ex = assertThrows(BizException.class,
                () -> ledgerService.unfreezeLsc(1001L, 200L, "UFRZ_INSUF"));
        assertEquals(ResultCode.LSC_FROZEN_INSUFFICIENT.getCode(), ex.getCode());
    }

    // ============== promotionRewardLsc 测试 (V7.3 新增) ==============

    @Test
    @DisplayName("promotionRewardLsc: 成功奖励入锁定池")
    void promotionRewardLsc_success() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 100L, 0L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        LscAccount result = ledgerService.promotionRewardLsc(userId, 300L, "PR_001");

        assertEquals(400L, result.getTotalLocked());
        verify(transactionMapper).insert(argThat(tx ->
                tx.getType() == LscTransactionTypeEnum.PROMOTION_REWARD_LOCKED.getCode()
                        && tx.getAmount() == 300L));
    }

    @Test
    @DisplayName("promotionRewardLsc: amount <= 0 应抛异常")
    void promotionRewardLsc_invalidAmount() {
        assertThrows(BizException.class, () -> ledgerService.promotionRewardLsc(1001L, 0L, "PR001"));
        assertThrows(BizException.class, () -> ledgerService.promotionRewardLsc(1001L, -1L, "PR001"));
    }
}
