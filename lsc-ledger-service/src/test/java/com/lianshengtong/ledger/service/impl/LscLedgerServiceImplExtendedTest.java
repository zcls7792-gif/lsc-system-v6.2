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
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("账本服务扩展单元测试 (V7.3)")
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
        ReflectionTestUtils.setField(ledgerService, "detailValidityDays", 365);
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
        Integer type = LscTransactionTypeEnum.ORDER_DEDUCT.getCode();
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
        assertEquals(0L, result.get("totalFrozen"));
        assertEquals(0L, result.get("totalWrittenOff"));
        assertEquals(0L, result.get("totalUsed"));
        assertEquals(0L, result.get("monthlyRevenue"));
    }

    @Test
    @DisplayName("overview: 含已使用和月收入")
    void overview_withUsedAndMonthlyRevenue() {
        Long userId = 1001L;
        LscAccount acc = buildAccount(userId, 500L, 200L);

        // V7.3: 已使用 = 订单抵扣(ORDER_DEDUCT), 月收入 = 每日释放(DAILY_RELEASE)
        LscTransaction usedTx = new LscTransaction();
        usedTx.setAmount(100L);
        usedTx.setType(LscTransactionTypeEnum.ORDER_DEDUCT.getCode());
        LscTransaction monthTx = new LscTransaction();
        monthTx.setAmount(200L);
        monthTx.setType(LscTransactionTypeEnum.DAILY_RELEASE.getCode());

        when(accountMapper.selectById(userId)).thenReturn(acc);
        when(transactionMapper.selectList(any()))
                .thenReturn(Collections.singletonList(usedTx))
                .thenReturn(Collections.singletonList(monthTx));

        Map<String, Object> result = ledgerService.overview(userId);

        assertNotNull(result);
        assertEquals(0L, result.get("totalWrittenOff")); // V7.3 禁止核销
        assertEquals(100L, result.get("totalUsed"));
        assertEquals(200L, result.get("monthlyRevenue"));
    }

    // ==================== 4. DuplicateKeyException 幂等冲突 ====================

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

    // ==================== 5. applyAccountChange 负值边界 ====================

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

    // ==================== 6. toLongFromObject 边界 ====================

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

    @Test
    @DisplayName("toLongFromObject: String 类型值走 Long.parseLong 路径")
    void toLongFromObject_stringValue() {
        Map<String, Object> row = new HashMap<>();
        row.put("totalAmount", "5000");
        row.put("totalCount", "10");
        when(transactionMapper.aggregateByTimeRange(any(), any(), any()))
                .thenReturn(Collections.singletonList(row));

        Map<String, Object> result = ledgerService.dailySummary(null, null);

        assertEquals(5000L, result.get("totalAmount"));
        assertEquals(10L, result.get("totalCount"));
    }

    // ==================== 7. buildIdemKey 空订单号 ====================

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

    // ==================== 8. 幂等键重复路径 ====================

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

    // ==================== 9. lockedSummary 过滤锁定<=0 ====================

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

    // ==================== 10. releaseBatch null userId 过滤 ====================

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

    // ==================== 11. releaseUserBatch amount<=0 跳过 ====================

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

    // ==================== 12. dailySummary 指定日期 ====================

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

    // ==================== 13. recentTrend ====================

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
        // V7.3 收入类: 退款退回(REFUND_RETURN) + 每日释放(DAILY_RELEASE)
        LscTransaction tx1 = new LscTransaction();
        tx1.setType(LscTransactionTypeEnum.REFUND_RETURN.getCode());
        tx1.setAmount(200L);
        tx1.setOrderNo("ORD_001");
        LscTransaction tx2 = new LscTransaction();
        tx2.setType(LscTransactionTypeEnum.DAILY_RELEASE.getCode());
        tx2.setAmount(100L);
        tx2.setOrderNo("ORD_002");
        // 消费赠送入锁定(GRANT_LOCKED) 不计入可用收入
        LscTransaction tx3 = new LscTransaction();
        tx3.setType(LscTransactionTypeEnum.GRANT_LOCKED.getCode());
        tx3.setAmount(500L);
        tx3.setOrderNo(null);

        when(transactionMapper.selectList(any())).thenReturn(Arrays.asList(tx1, tx2, tx3));

        List<Map<String, Object>> result = ledgerService.recentTrend(1001L, 1);

        assertEquals(1, result.size());
        assertEquals(300L, result.get(0).get("revenue"));
        assertEquals(300L, result.get(0).get("lscIn"));
        assertEquals(2L, result.get(0).get("orderCount"));
    }

    // ==================== 14. getBalance null 账户 ====================

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

    // ==================== 15. nvl null 分支 ====================

    @Test
    @DisplayName("nvl(Long): 账户 totalLocked 和 totalAvailable 为 null 时返回 0")
    void nvl_long_nullBranch() {
        Long userId = 1001L;
        LscAccount acc = new LscAccount();
        acc.setUserId(userId);
        acc.setTotalLocked(null);
        acc.setTotalAvailable(null);
        acc.setVersion(1);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        LscAccount result = ledgerService.issueLsc(userId, 200L, "NVL_LONG_NULL");

        assertNotNull(result);
        assertEquals(200L, result.getTotalLocked());
        assertEquals(0L, result.getTotalAvailable());
    }

    @Test
    @DisplayName("nvl(Integer): 账户 version 为 null 时返回 0")
    void nvl_integer_nullBranch() {
        Long userId = 1001L;
        LscAccount acc = new LscAccount();
        acc.setUserId(userId);
        acc.setTotalLocked(0L);
        acc.setTotalAvailable(0L);
        acc.setVersion(null);

        when(transactionMapper.selectByIdempotentKey(anyString())).thenReturn(null);
        when(accountService.getOrCreateAccount(userId)).thenReturn(acc);
        when(accountMapper.updateById(any(LscAccount.class))).thenReturn(1);
        when(transactionMapper.insert(any(LscTransaction.class))).thenReturn(1);

        LscAccount result = ledgerService.issueLsc(userId, 200L, "NVL_INT_NULL");

        assertNotNull(result);
        assertEquals(1, result.getVersion());
    }
}
