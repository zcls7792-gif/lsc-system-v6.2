package com.lianshengtong.ledger.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("账本服务扩展单元测试 - 补充覆盖")
class LscLedgerServiceImplExtendedTest {

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
    void setUp() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        ReflectionTestUtils.setField(ledgerService, "transactionTemplate", txTemplate);
        ReflectionTestUtils.setField(ledgerService, "lockWaitMs", 3000L);
        ReflectionTestUtils.setField(ledgerService, "lockLeaseMs", 10000L);
        ReflectionTestUtils.setField(ledgerService, "b2bValidityDays", 365);
        ReflectionTestUtils.setField(ledgerService, "expireBatchSize", 500);
        ReflectionTestUtils.setField(ledgerService, "optimisticLockEnabled", false);

        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            lenient().doReturn(true).when(rLock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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

    // ==================== 1. transactionList 测试 ====================

    @Test
    @DisplayName("transactionList: 无过滤条件分页查询")
    void transactionList_noFilters() {
        Page<LscTransaction> page = new Page<>(1, 20);
        List<LscTransaction> records = Arrays.asList(new LscTransaction(), new LscTransaction());
        Page<LscTransaction> resultPage = new Page<>(1, 20);
        resultPage.setRecords(records);
        resultPage.setTotal(2);

        when(transactionMapper.selectPage(any(Page.class), any())).thenReturn(resultPage);

        IPage<LscTransaction> result = ledgerService.transactionList(null, null, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(2, result.getRecords().size());
        assertEquals(2L, result.getTotal());
    }

    @Test
    @DisplayName("transactionList: 带所有过滤条件查询")
    void transactionList_withAllFilters() {
        Long userId = 1001L;
        Integer type = LscTransactionTypeEnum.MALL_CONSUMPTION.getCode();
        String startDate = "2025-01-01";
        String endDate = "2025-01-31";
        String orderNo = "ORD_001";

        Page<LscTransaction> resultPage = new Page<>(1, 20);
        resultPage.setRecords(Collections.singletonList(new LscTransaction()));
        resultPage.setTotal(1);

        when(transactionMapper.selectPage(any(Page.class), any())).thenReturn(resultPage);

        IPage<LscTransaction> result = ledgerService.transactionList(userId, 1, 20, type, startDate, endDate, orderNo);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("transactionList: 默认页码和大小")
    void transactionList_defaultPageSize() {
        Page<LscTransaction> resultPage = new Page<>(1, 20);
        resultPage.setRecords(Collections.emptyList());
        resultPage.setTotal(0);

        when(transactionMapper.selectPage(any(Page.class), any())).thenReturn(resultPage);

        IPage<LscTransaction> result = ledgerService.transactionList(null, null, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(0, result.getRecords().size());
    }

    // ==================== 2. availableDetails 测试 ====================

    @Test
    @DisplayName("availableDetails: 无过滤条件分页查询")
    void availableDetails_noFilters() {
        Page<AvailableLscDetail> resultPage = new Page<>(1, 20);
        resultPage.setRecords(Collections.emptyList());
        resultPage.setTotal(0);

        when(detailMapper.selectPage(any(Page.class), any())).thenReturn(resultPage);

        IPage<AvailableLscDetail> result = ledgerService.availableDetails(null, null, null, null);

        assertNotNull(result);
        assertEquals(0, result.getRecords().size());
    }

    @Test
    @DisplayName("availableDetails: 带userId和status过滤")
    void availableDetails_withFilters() {
        Long userId = 1001L;
        Integer status = AvailableLscStatusEnum.VALID.getCode();

        AvailableLscDetail d = new AvailableLscDetail();
        d.setId(1L);
        d.setUserId(userId);
        d.setAmount(100L);
        d.setStatus(status);

        Page<AvailableLscDetail> resultPage = new Page<>(1, 20);
        resultPage.setRecords(Collections.singletonList(d));
        resultPage.setTotal(1);

        when(detailMapper.selectPage(any(Page.class), any())).thenReturn(resultPage);

        IPage<AvailableLscDetail> result = ledgerService.availableDetails(userId, 1, 20, status);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals(100L, result.getRecords().get(0).getAmount());
    }

    // ==================== 3. overview 测试 ====================

    @Test
    @DisplayName("overview: 正常返回聚合数据")
    void overview_returnsAggregatedData() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 500L, 200L);

        when(accountMapper.selectById(userId)).thenReturn(acc);
        when(transactionMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = ledgerService.overview(userId);

        assertNotNull(result);
        assertEquals(500L, result.get("totalLocked"));
        assertEquals(200L, result.get("totalAvailable"));
        assertEquals(0L, result.get("totalWrittenOff"));
        assertEquals(0L, result.get("totalUsed"));
        assertEquals(0L, result.get("monthlyRevenue"));
    }

    @Test
    @DisplayName("overview: 含核销和月收入")
    void overview_withWriteOffAndMonthlyRevenue() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 500L, 200L);

        LscTransaction writeOffTx = new LscTransaction();
        writeOffTx.setAmount(100L);
        writeOffTx.setType(LscTransactionTypeEnum.MERCHANT_WRITE_OFF.getCode());
        LscTransaction monthTx = new LscTransaction();
        monthTx.setAmount(200L);
        monthTx.setType(LscTransactionTypeEnum.MALL_CONSUMPTION.getCode());

        when(accountMapper.selectById(userId)).thenReturn(acc);
        when(transactionMapper.selectList(any()))
                .thenReturn(Collections.singletonList(writeOffTx))
                .thenReturn(Collections.singletonList(monthTx));

        Map<String, Object> result = ledgerService.overview(userId);

        assertNotNull(result);
        assertEquals(100L, result.get("totalWrittenOff"));
        assertEquals(100L, result.get("totalUsed"));
        assertEquals(200L, result.get("monthlyRevenue"));
    }

    // ==================== 4. expireTransfer 补充测试 ====================

    @Test
    @DisplayName("expireTransfer: 幂等键重复直接返回0")
    void expireTransfer_idempotentKeyExists() {
        Long userId = 1001L;
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(new LscTransaction());

        long result = ledgerService.expireTransfer(userId);

        assertEquals(0L, result);
    }

    @Test
    @DisplayName("expireTransfer: 无过期明细返回0")
    void expireTransfer_noExpiredDetails() {
        Long userId = 1001L;
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(detailMapper.selectExpiredForTransfer(eq(userId), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        long result = ledgerService.expireTransfer(userId);

        assertEquals(0L, result);
    }

    @Test
    @DisplayName("expireTransfer: 可用余额不足抛异常")
    void expireTransfer_balanceInsufficient() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 0L, 50L);

        AvailableLscDetail d = new AvailableLscDetail();
        d.setId(1L);
        d.setUserId(userId);
        d.setAmount(300L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(detailMapper.selectExpiredForTransfer(eq(userId), any(), anyInt()))
                .thenReturn(Collections.singletonList(d));
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);

        BizException ex = assertThrows(BizException.class,
                () -> ledgerService.expireTransfer(userId));
        assertEquals(ResultCode.LSC_BALANCE_INSUFFICIENT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("expireTransfer: 账户更新乐观锁冲突抛异常")
    void expireTransfer_optimisticLockConflict() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 0L, 500L);

        AvailableLscDetail d = new AvailableLscDetail();
        d.setId(1L);
        d.setUserId(userId);
        d.setAmount(200L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(detailMapper.selectExpiredForTransfer(eq(userId), any(), anyInt()))
                .thenReturn(Collections.singletonList(d));
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(0);

        assertThrows(BizException.class,
                () -> ledgerService.expireTransfer(userId));
    }

    // ==================== 5. DuplicateKeyException 幂等冲突 ====================

    @Test
    @DisplayName("recordTransaction: insert 抛 DuplicateKeyException 时静默忽略")
    void recordTransaction_duplicateKeyException() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 200L, 100L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        doThrow(new DuplicateKeyException("唯一索引冲突"))
                .when(transactionMapper).insert(any(LscTransaction.class));
        when(detailMapper.insert(any(AvailableLscDetail.class))).thenReturn(1);

        assertDoesNotThrow(() -> ledgerService.releaseLsc(userId, 50L, "REL_DUP_KEY"));
    }

    // ==================== 6. applyAccountChange 负值边界 ====================

    @Test
    @DisplayName("applyAccountChange: newLocked < 0 抛 LSC_LOCKED_INSUFFICIENT")
    void applyAccountChange_lockedInsufficient() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 50L, 200L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);

        BizException ex = assertThrows(BizException.class,
                () -> ledgerService.releaseLsc(userId, 100L, "REL_LOCK_NEG"));
        assertEquals(ResultCode.LSC_LOCKED_INSUFFICIENT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("applyAccountChange: newAvailable < 0 抛 LSC_BALANCE_INSUFFICIENT")
    void applyAccountChange_availableInsufficient() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 200L, 10L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);

        BizException ex = assertThrows(BizException.class,
                () -> ledgerService.writeOffLsc(userId, 100L, "WO_AVAIL_NEG"));
        assertEquals(ResultCode.LSC_BALANCE_INSUFFICIENT.getCode(), ex.getCode());
    }

    // ==================== 7. toLongFromObject 边界 ====================

    @Test
    @DisplayName("toLongFromObject: null 返回 0")
    void toLongFromObject_null() {
        Map<String, Object> row = new HashMap<>();
        row.put("totalAmount", null);
        row.put("totalCount", null);
        when(transactionMapper.aggregateByTimeRange(any(), any(), any()))
                .thenReturn(Collections.singletonList(row));

        Map<String, Object> result = ledgerService.dailySummary(null, null);

        assertEquals(0L, result.get("totalAmount"));
        assertEquals(0L, result.get("totalCount"));
    }

    @Test
    @DisplayName("toLongFromObject: BigDecimal 类型转换")
    void toLongFromObject_bigDecimal() {
        Map<String, Object> row = new HashMap<>();
        row.put("totalAmount", new BigDecimal("9999999999"));
        row.put("totalCount", new BigDecimal("12345"));
        when(transactionMapper.aggregateByTimeRange(any(), any(), any()))
                .thenReturn(Collections.singletonList(row));

        Map<String, Object> result = ledgerService.dailySummary(null, null);

        assertEquals(9999999999L, result.get("totalAmount"));
        assertEquals(12345L, result.get("totalCount"));
    }

    @Test
    @DisplayName("toLongFromObject: Integer 类型转换")
    void toLongFromObject_integer() {
        Map<String, Object> row = new HashMap<>();
        row.put("totalAmount", 500);
        row.put("totalCount", 10);
        when(transactionMapper.aggregateByTimeRange(any(), any(), any()))
                .thenReturn(Collections.singletonList(row));

        Map<String, Object> result = ledgerService.dailySummary(null, null);

        assertEquals(500L, result.get("totalAmount"));
        assertEquals(10L, result.get("totalCount"));
    }

    // ==================== 8. buildIdemKey 空订单号 ====================

    @Test
    @DisplayName("buildIdemKey: 空订单号时使用随机生成器")
    void buildIdemKey_blankOrderNo() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 0L, 0L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        LscAccount result = ledgerService.issueLsc(userId, 200L, "");

        assertNotNull(result);
        assertEquals(200L, result.getTotalLocked());
    }

    // ==================== 9. 各种幂等键重复路径 ====================

    @Test
    @DisplayName("releaseLsc: 幂等键重复返回已有账户")
    void releaseLsc_idempotentKeyExists() {
        Long userId = 1001L;
        LscAccount existingAcc = buildAccount(userId, 100L, 100L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(new LscTransaction());
        when(accountMapper.selectById(userId)).thenReturn(existingAcc);

        LscAccount result = ledgerService.releaseLsc(userId, 50L, "REL_DUP");

        assertNotNull(result);
        assertEquals(100L, result.getTotalLocked());
        assertEquals(100L, result.getTotalAvailable());
        verify(accountMapper, never()).updateById(any(LscAccount.class));
    }

    @Test
    @DisplayName("writeOffLsc: 幂等键重复返回已有账户")
    void writeOffLsc_idempotentKeyExists() {
        Long merchantId = 2001L;
        LscAccount existingAcc = buildAccount(merchantId, 0L, 300L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(new LscTransaction());
        when(accountMapper.selectById(merchantId)).thenReturn(existingAcc);

        LscAccount result = ledgerService.writeOffLsc(merchantId, 50L, "WO_DUP");

        assertNotNull(result);
        assertEquals(300L, result.getTotalAvailable());
        verify(accountMapper, never()).updateById(any(LscAccount.class));
    }

    @Test
    @DisplayName("b2bTransfer: 幂等键重复返回发起方账户")
    void b2bTransfer_idempotentKeyExists() {
        Long fromId = 2001L;
        Long toId = 2002L;
        LscAccount existingAcc = buildAccount(fromId, 0L, 500L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(new LscTransaction());
        when(accountMapper.selectById(fromId)).thenReturn(existingAcc);

        LscAccount result = ledgerService.b2bTransfer(fromId, toId, 80L, "B2B_DUP");

        assertNotNull(result);
        assertEquals(500L, result.getTotalAvailable());
        verify(accountMapper, never()).updateById(any(LscAccount.class));
    }

    // ==================== 10. payLsc 单边更新失败 ====================

    @Test
    @DisplayName("payLsc: 消费者账户更新失败抛异常")
    void payLsc_consumerUpdateFails() {
        Long consumerId = 1001L;
        Long merchantId = 2001L;
        Long amount = 80L;

        LscAccount consumerAcc = buildAccount(consumerId, 0L, 200L);
        LscAccount merchantAcc = buildAccount(merchantId, 0L, 50L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(consumerId)).thenReturn(consumerAcc);
        when(accountService.getOrCreateAccount(merchantId)).thenReturn(merchantAcc);
        when(accountMapper.updateById(argThat(a -> a != null && a.getUserId().equals(consumerId))))
                .thenReturn(0);

        assertThrows(BizException.class,
                () -> ledgerService.payLsc(consumerId, merchantId, amount, "PAY_CONSUMER_FAIL"));
    }

    @Test
    @DisplayName("payLsc: 商家账户更新失败抛异常")
    void payLsc_merchantUpdateFails() {
        Long consumerId = 1001L;
        Long merchantId = 2001L;
        Long amount = 80L;

        LscAccount consumerAcc = buildAccount(consumerId, 0L, 200L);
        LscAccount merchantAcc = buildAccount(merchantId, 0L, 50L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(consumerId)).thenReturn(consumerAcc);
        when(accountService.getOrCreateAccount(merchantId)).thenReturn(merchantAcc);
        when(accountMapper.updateById(argThat(a -> a != null && a.getUserId().equals(consumerId))))
                .thenReturn(1);
        when(accountMapper.updateById(argThat(a -> a != null && a.getUserId().equals(merchantId))))
                .thenReturn(0);

        assertThrows(BizException.class,
                () -> ledgerService.payLsc(consumerId, merchantId, amount, "PAY_MERCHANT_FAIL"));
    }

    // ==================== 11. b2bTransfer 单边更新失败 ====================

    @Test
    @DisplayName("b2bTransfer: 发起方账户更新失败抛异常")
    void b2bTransfer_fromUpdateFails() {
        Long fromId = 2001L;
        Long toId = 2002L;
        Long amount = 80L;

        LscAccount fromAcc = buildAccount(fromId, 0L, 200L);
        LscAccount toAcc = buildAccount(toId, 0L, 50L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(fromId)).thenReturn(fromAcc);
        when(accountService.getOrCreateAccount(toId)).thenReturn(toAcc);
        when(accountMapper.updateById(argThat(a -> a != null && a.getUserId().equals(fromId))))
                .thenReturn(0);

        assertThrows(BizException.class,
                () -> ledgerService.b2bTransfer(fromId, toId, amount, "B2B_FROM_FAIL"));
    }

    @Test
    @DisplayName("b2bTransfer: 接收方账户更新失败抛异常")
    void b2bTransfer_toUpdateFails() {
        Long fromId = 2001L;
        Long toId = 2002L;
        Long amount = 80L;

        LscAccount fromAcc = buildAccount(fromId, 0L, 200L);
        LscAccount toAcc = buildAccount(toId, 0L, 50L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(fromId)).thenReturn(fromAcc);
        when(accountService.getOrCreateAccount(toId)).thenReturn(toAcc);
        when(accountMapper.updateById(argThat(a -> a != null && a.getUserId().equals(fromId))))
                .thenReturn(1);
        when(accountMapper.updateById(argThat(a -> a != null && a.getUserId().equals(toId))))
                .thenReturn(0);

        assertThrows(BizException.class,
                () -> ledgerService.b2bTransfer(fromId, toId, amount, "B2B_TO_FAIL"));
    }

    // ==================== 12. lockedSummary 过滤锁定<=0 ====================

    @Test
    @DisplayName("lockedSummary: 过滤锁定余额<=0 的账户")
    void lockedSummary_filterNonPositive() {
        LscAccount acc1 = buildAccount(1001L, 500L, 0L);
        LscAccount acc2 = buildAccount(1002L, 0L, 100L);
        LscAccount acc3 = buildAccount(1003L, -1L, 200L);
        when(accountMapper.selectAllLockedAccounts())
                .thenReturn(Arrays.asList(acc1, acc2, acc3));

        Map<String, Object> result = ledgerService.lockedSummary();

        assertEquals(500L, result.get("totalLocked"));
        assertEquals(3, result.get("userCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) result.get("accounts");
        assertNotNull(accounts);
        assertEquals(1, accounts.size());
        assertEquals(1001L, accounts.get(0).get("userId"));
    }

    // ==================== 13. releaseBatch null userId 过滤 ====================

    @Test
    @DisplayName("releaseBatch: null userId 的操作被过滤掉")
    void releaseBatch_nullUserIdFiltered() {
        LscAccount acc = buildAccount(1001L, 200L, 0L);

        List<LscLedgerOpDTO> ops = Arrays.asList(
                LscLedgerOpDTO.builder().userId(null).lockedDelta(-100L).availableDelta(100L).orderNo("REL_NULL").build(),
                LscLedgerOpDTO.builder().userId(1001L).lockedDelta(-50L).availableDelta(50L).orderNo("REL_OK").build()
        );

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);
        when(detailMapper.insert(any(AvailableLscDetail.class))).thenReturn(1);

        Map<String, Object> result = ledgerService.releaseBatch(ops);

        assertEquals(2, result.get("total"));
        assertEquals(1, result.get("successCount"));
        assertEquals(0, result.get("failedCount"));
        assertEquals(50L, result.get("releasedAmount"));
    }

    // ==================== 14. releaseUserBatch amount<=0 跳过 ====================

    @Test
    @DisplayName("releaseUserBatch: amount<=0 操作被跳过不抛异常")
    void releaseUserBatch_amountZeroOrNegativeSkipped() {
        LscAccount acc = buildAccount(1001L, 200L, 0L);

        List<LscLedgerOpDTO> ops = Arrays.asList(
                LscLedgerOpDTO.builder().userId(1001L).lockedDelta(0L).availableDelta(0L).orderNo("REL_ZERO").build(),
                LscLedgerOpDTO.builder().userId(1001L).lockedDelta(-50L).availableDelta(50L).orderNo("REL_OK").build()
        );

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(1001L)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);
        when(detailMapper.insert(any(AvailableLscDetail.class))).thenReturn(1);

        Map<String, Object> result = ledgerService.releaseBatch(ops);

        assertEquals(2, result.get("total"));
        assertEquals(2, result.get("successCount"));
        assertEquals(0, result.get("failedCount"));
        assertEquals(50L, result.get("releasedAmount"));
    }

    // ==================== 15. payLscOptimistically 幂等键存在路径 ====================

    @Test
    @DisplayName("payLscOptimistically: 幂等键存在直接返回消费者账户")
    void payLscOptimistically_idempotentKeyExists() {
        ReflectionTestUtils.setField(ledgerService, "optimisticLockEnabled", true);
        Long consumerId = 1001L;
        Long merchantId = 2001L;
        LscAccount consumerAcc = buildAccount(consumerId, 0L, 120L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(new LscTransaction());
        when(accountMapper.selectById(consumerId)).thenReturn(consumerAcc);

        LscAccount result = ledgerService.payLsc(consumerId, merchantId, 80L, "PAY_OPT_DUP");

        assertNotNull(result);
        assertEquals(consumerId, result.getUserId());
        assertEquals(120L, result.getTotalAvailable());
        verify(accountMapper, never()).updateById(any(LscAccount.class));
    }

    // ==================== 16. expireTransferAll 单用户异常隔离 ====================

    @Test
    @DisplayName("expireTransferAll: 单个用户异常不影响其他用户处理")
    void expireTransferAll_userExceptionIsolated() {
        LscAccount acc1 = buildAccount(1001L, 0L, 1000L);
        LscAccount acc2 = buildAccount(1002L, 0L, 500L);

        AvailableLscDetail d1 = new AvailableLscDetail();
        d1.setId(1L);
        d1.setUserId(1001L);
        d1.setAmount(200L);
        AvailableLscDetail d2 = new AvailableLscDetail();
        d2.setId(2L);
        d2.setUserId(1002L);
        d2.setAmount(100L);

        lenient().when(detailMapper.selectBatchExpiredForTransfer(any(), anyInt()))
                .thenReturn(Arrays.asList(d1, d2))
                .thenReturn(Collections.emptyList());
        lenient().when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        lenient().when(accountService.getOrCreateAccount(1001L)).thenReturn(acc1);
        lenient().when(accountService.getOrCreateAccount(1002L))
                .thenThrow(new RuntimeException("用户1002账户异常"));
        lenient().when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        lenient().when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        Map<String, Object> result = ledgerService.expireTransferAll();

        assertEquals(1, result.get("userCount"));
        assertEquals(200L, result.get("transferAmount"));
    }

    // ==================== 17. dailySummary 指定日期 ====================

    @Test
    @DisplayName("dailySummary: 指定日期聚合")
    void dailySummary_withDate() {
        LocalDate date = LocalDate.of(2025, 6, 15);
        Map<String, Object> row = new HashMap<>();
        row.put("totalAmount", 3000L);
        row.put("totalCount", 5L);
        List<Map<String, Object>> rows = Collections.singletonList(row);

        when(transactionMapper.aggregateByTimeRange(any(), any(), any())).thenReturn(rows);

        Map<String, Object> result = ledgerService.dailySummary(date, null);

        assertEquals(3000L, result.get("totalAmount"));
        assertEquals(5L, result.get("totalCount"));
    }

    // ==================== 18. recentTrend 正天数 ====================

    @Test
    @DisplayName("recentTrend: 指定正天数查询")
    void recentTrend_positiveDays() {
        when(transactionMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = ledgerService.recentTrend(1001L, 14);

        assertNotNull(result);
        assertEquals(14, result.size());
        for (Map<String, Object> point : result) {
            assertNotNull(point.get("date"));
            assertEquals(0L, point.get("orderCount"));
            assertEquals(0L, point.get("revenue"));
        }
    }

    @Test
    @DisplayName("recentTrend: 默认7天(days为null)")
    void recentTrend_defaultDays() {
        when(transactionMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = ledgerService.recentTrend(1001L, null);

        assertEquals(7, result.size());
    }

    @Test
    @DisplayName("recentTrend: 含收入类流水统计")
    void recentTrend_withRevenue() {
        LscTransaction tx1 = new LscTransaction();
        tx1.setType(LscTransactionTypeEnum.MALL_CONSUMPTION.getCode());
        tx1.setAmount(200L);
        tx1.setOrderNo("ORD_001");
        LscTransaction tx2 = new LscTransaction();
        tx2.setType(LscTransactionTypeEnum.DAILY_RELEASE.getCode());
        tx2.setAmount(100L);
        tx2.setOrderNo("ORD_002");
        LscTransaction tx3 = new LscTransaction();
        tx3.setType(LscTransactionTypeEnum.CONSUMPTION_ISSUE.getCode());
        tx3.setAmount(500L);
        tx3.setOrderNo(null);

        when(transactionMapper.selectList(any())).thenReturn(Arrays.asList(tx1, tx2, tx3));

        List<Map<String, Object>> result = ledgerService.recentTrend(1001L, 1);

        assertEquals(1, result.size());
        assertEquals(300L, result.get(0).get("revenue"));
        assertEquals(300L, result.get(0).get("lscIn"));
        assertEquals(2L, result.get(0).get("orderCount"));
    }

    // ==================== 19. expireTransferAll 循环5次空数据 ====================

    @Test
    @DisplayName("expireTransferAll: 循环5次均为空后退出")
    void expireTransferAll_emptyLoopGuard() {
        when(detailMapper.selectBatchExpiredForTransfer(any(), anyInt()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = ledgerService.expireTransferAll();

        assertEquals(0, result.get("userCount"));
        assertEquals(0L, result.get("transferAmount"));
    }

    // ==================== 20. payLscOptimistically 重试返回0 ====================

    @Test
    @DisplayName("payLscOptimistically: 全部重试失败抛 OptimisticLockingFailure")
    void payLscOptimistically_allRetriesFail() {
        ReflectionTestUtils.setField(ledgerService, "optimisticLockEnabled", true);
        Long consumerId = 1001L;
        Long merchantId = 2001L;

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(eq(consumerId)))
                .thenAnswer(inv -> buildAccount(consumerId, 0L, 200L));
        when(accountService.getOrCreateAccount(eq(merchantId)))
                .thenAnswer(inv -> buildAccount(merchantId, 0L, 50L));
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(0);

        assertThrows(RuntimeException.class,
                () -> ledgerService.payLsc(consumerId, merchantId, 80L, "PAY_ALL_FAIL"));
    }

    // ==================== 21. releaseBatch 全部 amount<=0 ====================

    @Test
    @DisplayName("releaseBatch: 所有操作 amount<=0 释放金额为0")
    void releaseBatch_allAmountsZeroOrNegative() {
        List<LscLedgerOpDTO> ops = Arrays.asList(
                LscLedgerOpDTO.builder().userId(1001L).lockedDelta(0L).availableDelta(0L).orderNo("Z1").build(),
                LscLedgerOpDTO.builder().userId(1001L).lockedDelta(null).availableDelta(null).orderNo("Z2").build()
        );

        Map<String, Object> result = ledgerService.releaseBatch(ops);

        assertEquals(2, result.get("total"));
        assertEquals(0L, result.get("releasedAmount"));
    }

    // ==================== 22. getBalance 账户为null时创建新账户 ====================

    @Test
    @DisplayName("getBalance: 账户为null返回带默认值的账户")
    void getBalance_nullAccountFromMapper() {
        when(accountMapper.selectById(9999L)).thenReturn(null);

        LscAccount result = ledgerService.getBalance(9999L);

        assertNotNull(result);
        assertEquals(9999L, result.getUserId());
        assertEquals(0L, result.getTotalLocked());
        assertEquals(0L, result.getTotalAvailable());
        assertEquals(0, result.getVersion());
    }

    // ==================== 23. expireTransferAll 循环多次用户成功 ====================

    @Test
    @DisplayName("expireTransferAll: 多轮循环处理多批用户")
    void expireTransferAll_multipleBatches() {
        LscAccount acc1 = buildAccount(1001L, 0L, 1000L);
        LscAccount acc2 = buildAccount(1002L, 0L, 500L);

        AvailableLscDetail d1 = new AvailableLscDetail();
        d1.setId(1L);
        d1.setUserId(1001L);
        d1.setAmount(100L);
        AvailableLscDetail d2 = new AvailableLscDetail();
        d2.setId(2L);
        d2.setUserId(1002L);
        d2.setAmount(200L);

        when(detailMapper.selectBatchExpiredForTransfer(any(), anyInt()))
                .thenReturn(Collections.singletonList(d1))
                .thenReturn(Collections.singletonList(d2))
                .thenReturn(Collections.emptyList());
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(anyLong())).thenReturn(acc1, acc2);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        Map<String, Object> result = ledgerService.expireTransferAll();

        assertEquals(2, result.get("userCount"));
        assertEquals(300L, result.get("transferAmount"));
    }

    // ==================== 24. applyAccountChange 负可用核销 ====================

    @Test
    @DisplayName("writeOffLsc: 可用余额刚好等于核销金额成功")
    void writeOffLsc_exactBalanceSuccess() {
        Long merchantId = 2001L;
        Long amount = 50L;

        LscAccount acc = buildAccount(merchantId, 0L, 50L);
        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(merchantId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        LscAccount result = ledgerService.writeOffLsc(merchantId, amount, "WO_EXACT");

        assertNotNull(result);
        assertEquals(0L, result.getTotalAvailable());
    }

    // ==================== 25. issueLsc 空订单号 ====================

    @Test
    @DisplayName("issueLsc: 空订单号发行成功")
    void issueLsc_blankOrderNo() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 0L, 0L);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        LscAccount result = ledgerService.issueLsc(userId, 100L, "");

        assertNotNull(result);
        assertEquals(100L, result.getTotalLocked());
    }
}