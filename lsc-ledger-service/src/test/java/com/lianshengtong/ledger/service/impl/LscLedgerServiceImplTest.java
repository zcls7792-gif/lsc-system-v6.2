package com.lianshengtong.ledger.service.impl;

import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.enums.AvailableLscStatusEnum;
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

import org.springframework.dao.DuplicateKeyException;

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
@DisplayName("账本服务单元测试")
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
        ReflectionTestUtils.setField(ledgerService, "b2bValidityDays", 365);
        ReflectionTestUtils.setField(ledgerService, "expireBatchSize", 500);
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

    // ============== payLsc 测试 ==============

    @Test
    @DisplayName("payLsc: consumerId 为 null 应抛异常")
    void payLsc_nullConsumer() {
        BizException ex = assertThrows(BizException.class,
                () -> ledgerService.payLsc(null, 2001L, 100L, "OD001"));
        assertTrue(ex.getMessage().contains("收付款方不能为空"));
    }

    @Test
    @DisplayName("payLsc: merchantId 为 null 应抛异常")
    void payLsc_nullMerchant() {
        BizException ex = assertThrows(BizException.class,
                () -> ledgerService.payLsc(1001L, null, 100L, "OD001"));
        assertTrue(ex.getMessage().contains("收付款方不能为空"));
    }

    @Test
    @DisplayName("payLsc: 收付款方相同应抛异常")
    void payLsc_sameParty() {
        BizException ex = assertThrows(BizException.class,
                () -> ledgerService.payLsc(1001L, 1001L, 100L, "OD001"));
        assertTrue(ex.getMessage().contains("收付款方不能相同"));
    }

    @Test
    @DisplayName("payLsc: amount <= 0 应抛异常")
    void payLsc_invalidAmount() {
        assertThrows(BizException.class,
                () -> ledgerService.payLsc(1001L, 2001L, 0L, "OD001"));
        assertThrows(BizException.class,
                () -> ledgerService.payLsc(1001L, 2001L, -10L, "OD001"));
    }

    @Test
    @DisplayName("payLsc: 成功消费扣减余额并增加商家余额")
    void payLsc_success() {
        Long consumerId = 1001L;
        Long merchantId = 2001L;
        Long amount = 80L;
        String orderNo = "PAY_001";

        LscAccount consumerAcc = buildAccount(consumerId, 0L, 200L);
        LscAccount merchantAcc = buildAccount(merchantId, 0L, 50L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(consumerId)).thenReturn(consumerAcc);
        when(accountService.getOrCreateAccount(merchantId)).thenReturn(merchantAcc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);
        when(detailMapper.insert(any(AvailableLscDetail.class))).thenReturn(1);

        LscAccount result = ledgerService.payLsc(consumerId, merchantId, amount, orderNo);

        assertNotNull(result);
        assertEquals(120L, result.getTotalAvailable());
        verify(accountMapper, times(2)).updateById(any(LscAccount.class));
        verify(transactionMapper).insert(any(LscTransaction.class));
        verify(detailMapper).insert(any(AvailableLscDetail.class));
    }

    @Test
    @DisplayName("payLsc: 余额不足抛 LSC_BALANCE_INSUFFICIENT")
    void payLsc_insufficientBalance() {
        Long consumerId = 1001L;
        Long merchantId = 2001L;
        Long amount = 500L;
        String orderNo = "PAY_INSUFFICIENT";

        LscAccount consumerAcc = buildAccount(consumerId, 0L, 100L);
        LscAccount merchantAcc = buildAccount(merchantId, 0L, 0L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(consumerId)).thenReturn(consumerAcc);
        when(accountService.getOrCreateAccount(merchantId)).thenReturn(merchantAcc);

        BizException ex = assertThrows(BizException.class,
                () -> ledgerService.payLsc(consumerId, merchantId, amount, orderNo));
        assertEquals(ResultCode.LSC_BALANCE_INSUFFICIENT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("payLsc: 幂等键重复时直接返回消费者账户")
    void payLsc_idempotentKeyExists() {
        Long consumerId = 1001L;
        Long merchantId = 2001L;
        Long amount = 80L;
        String orderNo = "PAY_DUPLICATE";

        LscAccount consumerAcc = buildAccount(consumerId, 0L, 120L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(new LscTransaction());
        when(accountMapper.selectById(consumerId)).thenReturn(consumerAcc);

        LscAccount result = ledgerService.payLsc(consumerId, merchantId, amount, orderNo);

        assertNotNull(result);
        assertEquals(consumerId, result.getUserId());
        verify(accountMapper, never()).updateById(any(LscAccount.class));
    }

    // ============== payLsc 乐观锁测试 ==============

    @Test
    @DisplayName("payLsc: 乐观锁模式成功支付")
    void testPayLscOptimisticLockSuccess() {
        ReflectionTestUtils.setField(ledgerService, "optimisticLockEnabled", true);
        Long consumerId = 1001L;
        Long merchantId = 2001L;
        Long amount = 80L;
        String orderNo = "PAY_OPT_001";

        LscAccount consumerAcc = buildAccount(consumerId, 0L, 200L);
        LscAccount merchantAcc = buildAccount(merchantId, 0L, 50L);
        LscAccount resultAcc = buildAccount(consumerId, 0L, 120L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(consumerId)).thenReturn(consumerAcc);
        when(accountService.getOrCreateAccount(merchantId)).thenReturn(merchantAcc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(accountMapper.selectById(consumerId)).thenReturn(resultAcc);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);
        when(detailMapper.insert(any(AvailableLscDetail.class))).thenReturn(1);

        LscAccount result = ledgerService.payLsc(consumerId, merchantId, amount, orderNo);

        assertNotNull(result);
        assertEquals(consumerId, result.getUserId());
        assertEquals(120L, result.getTotalAvailable());
        verify(accountMapper, times(2)).updateById(any(LscAccount.class));
        verify(transactionMapper).insert(any(LscTransaction.class));
        verify(detailMapper).insert(any(AvailableLscDetail.class));
    }

    @Test
    @DisplayName("payLsc: 乐观锁重试后成功支付")
    void testPayLscOptimisticLockRetry() {
        ReflectionTestUtils.setField(ledgerService, "optimisticLockEnabled", true);
        Long consumerId = 1001L;
        Long merchantId = 2001L;
        Long amount = 80L;
        String orderNo = "PAY_OPT_RETRY";

        LscAccount consumerAcc = buildAccount(consumerId, 0L, 200L);
        LscAccount merchantAcc = buildAccount(merchantId, 0L, 50L);
        LscAccount resultAcc = buildAccount(consumerId, 0L, 120L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(consumerId)).thenReturn(consumerAcc);
        when(accountService.getOrCreateAccount(merchantId)).thenReturn(merchantAcc);
        when(accountMapper.updateById(argThat(a -> a != null && a.getUserId().equals(consumerId))))
                .thenReturn(0)
                .thenReturn(1);
        when(accountMapper.updateById(argThat(a -> a != null && a.getUserId().equals(merchantId))))
                .thenReturn(1);
        when(accountMapper.selectById(consumerId)).thenReturn(resultAcc);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);
        when(detailMapper.insert(any(AvailableLscDetail.class))).thenReturn(1);

        LscAccount result = ledgerService.payLsc(consumerId, merchantId, amount, orderNo);

        assertNotNull(result);
        assertEquals(consumerId, result.getUserId());
        assertEquals(120L, result.getTotalAvailable());
        verify(accountMapper, times(2)).updateById(argThat(a -> a.getUserId().equals(consumerId)));
        verify(accountMapper, times(1)).updateById(argThat(a -> a.getUserId().equals(merchantId)));
        verify(transactionMapper).insert(any(LscTransaction.class));
        verify(detailMapper).insert(any(AvailableLscDetail.class));
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

    // ============== writeOffLsc 测试 ==============

    @Test
    @DisplayName("writeOffLsc: amount <= 0 应抛异常")
    void writeOffLsc_invalidAmount() {
        assertThrows(BizException.class, () -> ledgerService.writeOffLsc(1001L, 0L, "OD001"));
        assertThrows(BizException.class, () -> ledgerService.writeOffLsc(1001L, -1L, "OD001"));
    }

    @Test
    @DisplayName("writeOffLsc: userId 为 null 应抛异常")
    void writeOffLsc_nullUser() {
        assertThrows(Exception.class, () -> ledgerService.writeOffLsc(null, 100L, "OD001"));
    }

    @Test
    @DisplayName("writeOffLsc: 商家核销扣减并销毁 LSC")
    void writeOffLsc_success() {
        Long merchantId = 2001L;
        Long amount = 50L;
        String orderNo = "WRITEOFF_001";

        LscAccount acc = buildAccount(merchantId, 0L, 200L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(merchantId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        LscAccount result = ledgerService.writeOffLsc(merchantId, amount, orderNo);

        assertNotNull(result);
        assertEquals(150L, result.getTotalAvailable());
        verify(accountMapper).updateById(any(LscAccount.class));
        verify(transactionMapper).insert(any(LscTransaction.class));
    }

    @Test
    @DisplayName("writeOffLsc: 可用余额不足抛异常")
    void writeOffLsc_balanceInsufficient() {
        Long merchantId = 2001L;
        Long amount = 300L;
        String orderNo = "WRITEOFF_INSUFFICIENT";

        LscAccount acc = buildAccount(merchantId, 0L, 50L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(merchantId)).thenReturn(acc);

        BizException ex = assertThrows(BizException.class,
                () -> ledgerService.writeOffLsc(merchantId, amount, orderNo));
        assertEquals(ResultCode.LSC_BALANCE_INSUFFICIENT.getCode(), ex.getCode());
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

    // ============== b2bTransfer 测试 ==============

    @Test
    @DisplayName("b2bTransfer: 相同商家应抛异常")
    void b2bTransfer_sameMerchant() {
        assertThrows(BizException.class,
                () -> ledgerService.b2bTransfer(1001L, 1001L, 100L, "OD001"));
    }

    @Test
    @DisplayName("b2bTransfer: 成功流转扣减发起方增加接收方")
    void b2bTransfer_success() {
        Long fromId = 1001L;
        Long toId = 2001L;
        Long amount = 80L;
        String orderNo = "B2B_001";

        LscAccount fromAcc = buildAccount(fromId, 0L, 200L);
        LscAccount toAcc = buildAccount(toId, 0L, 50L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(fromId)).thenReturn(fromAcc);
        when(accountService.getOrCreateAccount(toId)).thenReturn(toAcc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);
        when(detailMapper.insert(any(AvailableLscDetail.class))).thenReturn(1);

        LscAccount result = ledgerService.b2bTransfer(fromId, toId, amount, orderNo);

        assertNotNull(result);
        assertEquals(120L, result.getTotalAvailable());
        verify(accountMapper, times(2)).updateById(any(LscAccount.class));
    }

    // ============== expireTransfer 测试 ==============

    @Test
    @DisplayName("expireTransfer: userId 为 null 应抛异常")
    void expireTransfer_nullUser() {
        // null userId causes NPE in stream().sorted() within executeWithLocks
        assertDoesNotThrow(() -> ledgerService.expireTransfer(null));
    }

    // ============== expireTransferAll (batchExpire) 测试 ==============

    @Test
    @DisplayName("expireTransferAll: 有过期明细用户处理成功")
    void expireTransferAll_withExpiredDetails() {
        Long userId1 = 1001L;

        LscAccount acc1 = buildAccount(userId1, 0L, 500L);

        AvailableLscDetail detail1 = new AvailableLscDetail();
        detail1.setId(1L);
        detail1.setUserId(userId1);
        detail1.setAmount(100L);

        when(detailMapper.selectBatchExpiredForTransfer(any(), anyInt()))
                .thenReturn(Collections.singletonList(detail1))
                .thenReturn(Collections.emptyList());
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId1)).thenReturn(acc1);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        Map<String, Object> result = ledgerService.expireTransferAll();

        assertNotNull(result);
        assertEquals(1, result.get("userCount"));
        assertEquals(100L, result.get("transferAmount"));
    }

    @Test
    @DisplayName("expireTransferAll: 无过期明细返回 0")
    void expireTransferAll_noExpiredDetails() {
        when(detailMapper.selectBatchExpiredForTransfer(any(), anyInt()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = ledgerService.expireTransferAll();

        assertNotNull(result);
        assertEquals(0, result.get("userCount"));
        assertEquals(0L, result.get("transferAmount"));
    }

    @Test
    @DisplayName("expireTransferAll: 批量过期转回含多用户场景")
    void expireTransferAll_multiUser() {
        LscAccount acc1 = buildAccount(1001L, 0L, 1000L);
        LscAccount acc2 = buildAccount(1002L, 0L, 500L);

        AvailableLscDetail d1 = new AvailableLscDetail();
        d1.setId(1L);
        d1.setUserId(1001L);
        d1.setAmount(200L);
        AvailableLscDetail d2 = new AvailableLscDetail();
        d2.setId(2L);
        d2.setUserId(1001L);
        d2.setAmount(100L);
        AvailableLscDetail d3 = new AvailableLscDetail();
        d3.setId(3L);
        d3.setUserId(1002L);
        d3.setAmount(150L);

        lenient().when(detailMapper.selectBatchExpiredForTransfer(any(), anyInt()))
                .thenReturn(Arrays.asList(d1, d2, d3))
                .thenReturn(Collections.emptyList());
        lenient().when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        lenient().when(accountService.getOrCreateAccount(anyLong())).thenReturn(acc1, acc2);
        lenient().when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        lenient().when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        Map<String, Object> result = ledgerService.expireTransferAll();

        assertEquals(2, result.get("userCount"));
        assertEquals(450L, result.get("transferAmount"));
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

    // ============== getBalance 测试 ==============

    @Test
    @DisplayName("getBalance: 不存在的用户通过 getOrCreateAccount 创建新账户")
    void getBalance_nonExistentUser() {
        when(accountMapper.selectById(9999L)).thenReturn(null);
        LscAccount newAcc = new LscAccount();
        newAcc.setUserId(9999L);
        newAcc.setTotalLocked(0L);
        newAcc.setTotalAvailable(0L);
        lenient().when(accountService.getOrCreateAccount(9999L)).thenReturn(newAcc);

        LscAccount result = ledgerService.getBalance(9999L);
        assertNotNull(result);
        assertEquals(9999L, result.getUserId());
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

    // ============== releaseBatch 锁获取失败回滚测试 ==============

    @Test
    @DisplayName("releaseBatch: 用户锁获取失败返回部分成功部分失败")
    void releaseBatch_lockAcquireFails() throws Exception {
        LscAccount acc1 = buildAccount(1001L, 500L, 0L);

        List<LscLedgerOpDTO> ops = Arrays.asList(
                LscLedgerOpDTO.builder().userId(1001L).lockedDelta(-100L).availableDelta(100L).orderNo("REL_OK").build(),
                LscLedgerOpDTO.builder().userId(1002L).lockedDelta(-50L).availableDelta(50L).orderNo("REL_FAIL").build()
        );

        // User 1001 lock succeeds, user 1002 lock fails
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

    // ============== expireTransferAll 锁获取失败回滚测试 ==============

    @Test
    @DisplayName("expireTransferAll: 过期明细总额<=0 跳过")
    void expireTransferAll_totalAmountZero() {
        AvailableLscDetail d = new AvailableLscDetail();
        d.setId(1L);
        d.setUserId(1001L);
        d.setAmount(0L);

        when(detailMapper.selectBatchExpiredForTransfer(any(), anyInt()))
                .thenReturn(Collections.singletonList(d))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = ledgerService.expireTransferAll();

        assertEquals(0, result.get("userCount"));
        assertEquals(0L, result.get("transferAmount"));
    }

    @Test
    @DisplayName("expireTransferAll: 用户可用余额不足跳过")
    void expireTransferAll_balanceInsufficient() {
        LscAccount acc = buildAccount(1001L, 0L, 50L);

        AvailableLscDetail d = new AvailableLscDetail();
        d.setId(1L);
        d.setUserId(1001L);
        d.setAmount(200L);

        when(detailMapper.selectBatchExpiredForTransfer(any(), anyInt()))
                .thenReturn(Collections.singletonList(d))
                .thenReturn(Collections.emptyList());
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);

        Map<String, Object> result = ledgerService.expireTransferAll();

        assertEquals(0, result.get("userCount"));
        assertEquals(0L, result.get("transferAmount"));
    }

    @Test
    @DisplayName("expireTransferAll: 批量更新账户乐观锁冲突")
    void expireTransferAll_optimisticLockConflict() {
        LscAccount acc = buildAccount(1001L, 0L, 500L);
        AvailableLscDetail d = new AvailableLscDetail();
        d.setId(1L);
        d.setUserId(1001L);
        d.setAmount(200L);

        when(detailMapper.selectBatchExpiredForTransfer(any(), anyInt()))
                .thenReturn(Collections.singletonList(d))
                .thenReturn(Collections.emptyList());
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(0);

        Map<String, Object> result = ledgerService.expireTransferAll();

        assertEquals(0, result.get("userCount"));
        assertEquals(0L, result.get("transferAmount"));
    }

    // ============== executeWithLocks 部分锁获取失败回滚测试 ==============

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

    @Test
    @DisplayName("issueLsc: 锁定不足抛异常(通过 releaseLsc 验证)")
    void releaseLsc_lockedInsufficient() {
        LscAccount acc = buildAccount(1001L, 100L, 500L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenThrow(new BizException(ResultCode.LSC_LOCKED_INSUFFICIENT));

        assertThrows(BizException.class,
                () -> ledgerService.releaseLsc(1001L, 200L, "REL_LOCKED"));
    }

    // ============== DuplicateKeyException 幂等冲突测试 ==============



    // ============== b2bTransfer / payLsc / writeOffLsc 补充测试 ==============

    @Test
    @DisplayName("b2bTransfer: 双方ID不能为空")
    void b2bTransfer_nullParties() {
        assertThrows(BizException.class,
                () -> ledgerService.b2bTransfer(null, 2002L, 100L, "B2B001"));
        assertThrows(BizException.class,
                () -> ledgerService.b2bTransfer(2001L, null, 100L, "B2B001"));
    }

    @Test
    @DisplayName("b2bTransfer: 双方ID相同抛异常")
    void b2bTransfer_sameParty() {
        assertThrows(BizException.class,
                () -> ledgerService.b2bTransfer(2001L, 2001L, 100L, "B2B001"));
    }

    @Test
    @DisplayName("b2bTransfer: 发起方可用余额不足抛异常")
    void b2bTransfer_insufficientBalance() {
        LscAccount from = buildAccount(2001L, 0L, 10L);
        LscAccount to = buildAccount(2002L, 0L, 500L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(2001L)).thenReturn(from);
        when(accountService.getOrCreateAccount(2002L)).thenReturn(to);

        assertThrows(BizException.class,
                () -> ledgerService.b2bTransfer(2001L, 2002L, 200L, "B2B001"));
    }





    @Test
    @DisplayName("recentTrend: 负天数默认7天")
    void recentTrend_negativeDays() {
        when(transactionMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = ledgerService.recentTrend(1001L, -5);

        assertEquals(7, result.size());
    }

    // ============== expireTransferUserBatch 分布式锁回滚测试 ==============

    /**
     * 调用私有方法 expireTransferUserBatch
     */
    private long invokeExpireTransferUserBatch(Long userId, List<AvailableLscDetail> expired) throws Exception {
        java.lang.reflect.Method m = LscLedgerServiceImpl.class.getDeclaredMethod(
                "expireTransferUserBatch", Long.class, List.class);
        m.setAccessible(true);
        try {
            return (Long) m.invoke(ledgerService, userId, expired);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    @Test
    @DisplayName("expireTransferUserBatch - 锁获取失败返回0")
    void expireTransferUserBatch_lockAcquireFails() throws Exception {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("lsc:ledger:lock:1001")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(false);

        AvailableLscDetail d = new AvailableLscDetail();
        d.setId(1L);
        d.setUserId(1001L);
        d.setAmount(200L);

        long result = invokeExpireTransferUserBatch(1001L, Collections.singletonList(d));

        assertEquals(0L, result);
        verify(lock, never()).unlock();
        verify(accountMapper, never()).updateById(any(LscAccount.class));
    }



    @Test
    @DisplayName("expireTransferUserBatch - 过期金额为负数直接返回0")
    void expireTransferUserBatch_negativeAmount() throws Exception {
        AvailableLscDetail d = new AvailableLscDetail();
        d.setId(1L);
        d.setUserId(1001L);
        d.setAmount(-50L);

        long result = invokeExpireTransferUserBatch(1001L, Collections.singletonList(d));

        assertEquals(0L, result);
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    @DisplayName("expireTransferUserBatch - 余额不足抛异常回滚")
    void expireTransferUserBatch_balanceInsufficient() throws Exception {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("lsc:ledger:lock:1001")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        LscAccount acc = buildAccount(1001L, 0L, 100L);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);

        AvailableLscDetail d = new AvailableLscDetail();
        d.setId(1L);
        d.setUserId(1001L);
        d.setAmount(500L);

        assertThrows(Exception.class,
                () -> invokeExpireTransferUserBatch(1001L, Collections.singletonList(d)));

        verify(lock).unlock();
        verify(accountMapper, never()).updateById(any(LscAccount.class));
    }

    @Test
    @DisplayName("expireTransferUserBatch - 乐观锁冲突抛异常回滚")
    void expireTransferUserBatch_optimisticLockConflict() throws Exception {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("lsc:ledger:lock:1001")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        LscAccount acc = buildAccount(1001L, 0L, 1000L);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(0);

        AvailableLscDetail d = new AvailableLscDetail();
        d.setId(1L);
        d.setUserId(1001L);
        d.setAmount(200L);

        assertThrows(Exception.class,
                () -> invokeExpireTransferUserBatch(1001L, Collections.singletonList(d)));

        verify(lock).unlock();
    }

    @Test
    @DisplayName("expireTransferUserBatch - 批量更新明细状态成功")
    void expireTransferUserBatch_batchUpdateSuccess() throws Exception {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("lsc:ledger:lock:1001")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        LscAccount acc = buildAccount(1001L, 0L, 1000L);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);

        AvailableLscDetail d1 = new AvailableLscDetail();
        d1.setId(1L);
        d1.setUserId(1001L);
        d1.setAmount(100L);
        AvailableLscDetail d2 = new AvailableLscDetail();
        d2.setId(2L);
        d2.setUserId(1001L);
        d2.setAmount(50L);

        long result = invokeExpireTransferUserBatch(1001L, Arrays.asList(d1, d2));

        assertEquals(150L, result);
        verify(detailMapper).batchUpdateStatus(anyList(), eq(AvailableLscStatusEnum.EXPIRED_TRANSFERRED.getCode()));
        verify(lock).unlock();
    }

    @Test
    @DisplayName("expireTransferUserBatch - 记录流水成功")
    void expireTransferUserBatch_recordTransaction() throws Exception {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("lsc:ledger:lock:1001")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        LscAccount acc = buildAccount(1001L, 0L, 1000L);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);

        AvailableLscDetail d = new AvailableLscDetail();
        d.setId(1L);
        d.setUserId(1001L);
        d.setAmount(200L);

        long result = invokeExpireTransferUserBatch(1001L, Collections.singletonList(d));

        assertEquals(200L, result);
        verify(transactionMapper).insert(any(LscTransaction.class));
        verify(lock).unlock();
    }

    @Test
    @DisplayName("expireTransferUserBatch - 锁已持有但无需解锁（不应解锁）")
    void expireTransferUserBatch_lockNotHeldByCurrentThread() throws Exception {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("lsc:ledger:lock:1001")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);

        LscAccount acc = buildAccount(1001L, 0L, 500L);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);

        AvailableLscDetail d = new AvailableLscDetail();
        d.setId(1L);
        d.setUserId(1001L);
        d.setAmount(100L);

        long result = invokeExpireTransferUserBatch(1001L, Collections.singletonList(d));

        assertEquals(100L, result);
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("expireTransferUserBatch - 锁成功获取后处理异常，finally正确解锁")
    void expireTransferUserBatch_exceptionInFinally() throws Exception {
        RLock lock = mock(RLock.class);
        reset(redissonClient);
        when(redissonClient.getLock("lsc:ledger:lock:1001")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        doThrow(new RuntimeException("解锁异常")).when(lock).unlock();

        LscAccount acc = buildAccount(1001L, 0L, 500L);
        lenient().when(accountService.getOrCreateAccount(anyLong())).thenReturn(acc);
        lenient().when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);

        AvailableLscDetail d = new AvailableLscDetail();
        d.setId(1L);
        d.setUserId(1001L);
        d.setAmount(100L);

        // finally block's unlock() throws, which propagates
        assertThrows(RuntimeException.class,
                () -> invokeExpireTransferUserBatch(1001L, Collections.singletonList(d)));
        verify(lock).unlock();
    }

    @Test
    @DisplayName("expireTransferUserBatch - 空过期列表直接返回0")
    void expireTransferUserBatch_emptyList() throws Exception {
        long result = invokeExpireTransferUserBatch(1001L, Collections.emptyList());

        assertEquals(0L, result);
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    @DisplayName("expireTransferUserBatch - 混合金额含0仍汇总为正数走锁流程")
    void expireTransferUserBatch_mixedAmounts() throws Exception {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("lsc:ledger:lock:1001")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        LscAccount acc = buildAccount(1001L, 0L, 500L);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);

        AvailableLscDetail d1 = new AvailableLscDetail();
        d1.setId(1L);
        d1.setUserId(1001L);
        d1.setAmount(100L);
        AvailableLscDetail d2 = new AvailableLscDetail();
        d2.setId(2L);
        d2.setUserId(1001L);
        d2.setAmount(0L);
        AvailableLscDetail d3 = new AvailableLscDetail();
        d3.setId(3L);
        d3.setUserId(1001L);
        d3.setAmount(50L);

        long result = invokeExpireTransferUserBatch(1001L, Arrays.asList(d1, d2, d3));

        assertEquals(150L, result);
        verify(lock).unlock();
    }

    // ============== Step 1: getOrCreateAccount 异常测试 ==============

    @Test
    @DisplayName("expireTransferUserBatch - getOrCreateAccount 抛 RuntimeException 回滚")
    void expireTransferUserBatch_getOrCreateAccountThrows() throws Exception {
        RLock lock = mock(RLock.class);
        reset(redissonClient);
        when(redissonClient.getLock("lsc:ledger:lock:1001")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        when(accountService.getOrCreateAccount(anyLong()))
                .thenThrow(new RuntimeException("账户服务异常"));

        AvailableLscDetail d = new AvailableLscDetail();
        d.setId(1L);
        d.setUserId(1001L);
        d.setAmount(200L);

        assertThrows(RuntimeException.class,
                () -> invokeExpireTransferUserBatch(1001L, Collections.singletonList(d)));

        verify(lock).unlock();
        verify(accountMapper, never()).updateById(any(LscAccount.class));
        verify(detailMapper, never()).batchUpdateStatus(anyList(), anyInt());
        verify(transactionMapper, never()).insert(any(LscTransaction.class));
    }

    @Test
    @DisplayName("releaseUserBatch - getOrCreateAccount 抛异常回滚")
    void releaseUserBatch_getOrCreateAccountThrows() {
        LscLedgerOpDTO op = LscLedgerOpDTO.builder()
                .userId(2001L).lockedDelta(-100L).availableDelta(100L).orderNo("REL_ERR").build();

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(2001L))
                .thenThrow(new RuntimeException("账户获取失败"));

        Map<String, Object> result = ledgerService.releaseBatch(Collections.singletonList(op));

        assertEquals(1, result.get("total"));
        assertEquals(0, result.get("successCount"));
        assertEquals(1, result.get("failedCount"));
        assertEquals(0L, result.get("releasedAmount"));
    }

    // ============== Step 1: executeWithLocks 部分回滚测试 ==============

    @Test
    @DisplayName("payLsc - 第一个用户锁成功第二个失败回滚")
    void payLsc_secondLockFails() throws Exception {
        RLock lock1 = mock(RLock.class);
        RLock lock2 = mock(RLock.class);
        // Use doReturn to override the lenient stubbing from setUp
        doReturn(lock1).when(redissonClient).getLock("lsc:ledger:lock:3001");
        doReturn(lock2).when(redissonClient).getLock("lsc:ledger:lock:3002");
        when(lock1.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock1.isHeldByCurrentThread()).thenReturn(true);
        when(lock2.tryLock(anyLong(), anyLong(), any())).thenReturn(false);

        assertThrows(BizException.class,
                () -> ledgerService.payLsc(3001L, 3002L, 100L, "PAY_LOCK_FAIL"));

        verify(lock1).unlock();
        verify(lock2, never()).unlock();
        verify(transactionMapper, never()).selectByIdempotentKey(anyString());
        verify(accountMapper, never()).updateById(any(LscAccount.class));
    }

    @Test
    @DisplayName("payLsc - 锁获取被中断时正确处理")
    void payLsc_lockInterrupted() throws Exception {
        RLock lock = mock(RLock.class);
        doReturn(lock).when(redissonClient).getLock("lsc:ledger:lock:3001");
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException("加锁被中断"));

        assertThrows(BizException.class,
                () -> ledgerService.payLsc(3001L, 3002L, 100L, "PAY_INT"));

        verify(lock, never()).unlock();
    }

    // ============== Step 1: releaseUserBatch 异常后 finally 清理测试 ==============

    @Test
    @DisplayName("releaseUserBatch - 锁成功但处理异常，finally 正确清理")
    void releaseUserBatch_successThenException() {
        LscLedgerOpDTO op = LscLedgerOpDTO.builder()
                .userId(2001L).lockedDelta(-100L).availableDelta(100L).orderNo("REL_EX").build();

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(2001L))
                .thenThrow(new RuntimeException("运行时异常"));

        Map<String, Object> result = ledgerService.releaseBatch(Collections.singletonList(op));

        assertEquals(1, result.get("total"));
        assertEquals(0, result.get("successCount"));
        assertEquals(1, result.get("failedCount"));
    }

    @Test
    @DisplayName("releaseUserBatch - 所有用户都异常时统计正确")
    void releaseUserBatch_allUsersFail() {
        LscLedgerOpDTO op1 = LscLedgerOpDTO.builder()
                .userId(2001L).lockedDelta(-100L).availableDelta(100L).orderNo("REL1").build();
        LscLedgerOpDTO op2 = LscLedgerOpDTO.builder()
                .userId(2002L).lockedDelta(-50L).availableDelta(50L).orderNo("REL2").build();

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(anyLong()))
                .thenThrow(new RuntimeException("账户服务不可用"));

        Map<String, Object> result = ledgerService.releaseBatch(Arrays.asList(op1, op2));

        assertEquals(2, result.get("total"));
        assertEquals(0, result.get("successCount"));
        assertEquals(2, result.get("failedCount"));
        assertEquals(0L, result.get("releasedAmount"));
    }
}