package com.lianshengtong.ledger.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.ledger.entity.AvailableLscDetail;
import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.entity.LscTransaction;
import com.lianshengtong.ledger.service.LscLedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 补齐 LSC 账本 Controller 方法覆盖率 (I-06)，见 LSC_V6.2_Reports/LSC_V6.2_Code_Quality_Completeness_Audit_20260822.md
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LscLedgerController 方法覆盖率补齐测试")
class LscLedgerControllerTest {

    @Mock
    private LscLedgerService ledgerService;

    @InjectMocks
    private LscLedgerController controller;

    private LscAccount buildAccount() {
        return LscAccount.builder()
                .userId(1L)
                .totalLocked(100L)
                .totalAvailable(50L)
                .version(1)
                .build();
    }

    private LscLedgerOpDTO buildOpDto() {
        return LscLedgerOpDTO.builder()
                .userId(1L)
                .counterpartyId(2L)
                .lockedDelta(100L)
                .availableDelta(100L)
                .orderNo("ORD001")
                .build();
    }

    // ==================== 经过 resolveAmount 的方法 ====================

    @Test
    @DisplayName("issue: 正常请求(lockedDelta=100)返回发行后的账户快照")
    void issue_validDto_returnsAccount() {
        LscLedgerOpDTO dto = buildOpDto();
        LscAccount expected = buildAccount();
        when(ledgerService.issueLsc(anyLong(), anyLong(), anyString())).thenReturn(expected);

        R<LscAccount> result = controller.issue(dto);

        assertTrue(result.isSuccess());
        assertEquals(expected, result.getData());
    }

    @Test
    @DisplayName("release: 正常请求(availableDelta=100)返回释放后的账户快照")
    void release_validDto_returnsAccount() {
        LscLedgerOpDTO dto = buildOpDto();
        LscAccount expected = buildAccount();
        when(ledgerService.releaseLsc(anyLong(), anyLong(), anyString())).thenReturn(expected);

        R<LscAccount> result = controller.release(dto);

        assertTrue(result.isSuccess());
        assertEquals(expected, result.getData());
    }

    @Test
    @DisplayName("pay: 正常请求返回支付后的账户快照")
    void pay_validDto_returnsAccount() {
        LscLedgerOpDTO dto = buildOpDto();
        LscAccount expected = buildAccount();
        when(ledgerService.payLsc(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(expected);

        R<LscAccount> result = controller.pay(dto);

        assertTrue(result.isSuccess());
        assertEquals(expected, result.getData());
    }

    @Test
    @DisplayName("b2bTransfer: 正常请求返回流转后的账户快照")
    void b2bTransfer_validDto_returnsAccount() {
        LscLedgerOpDTO dto = buildOpDto();
        LscAccount expected = buildAccount();
        when(ledgerService.b2bTransfer(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(expected);

        R<LscAccount> result = controller.b2bTransfer(dto);

        assertTrue(result.isSuccess());
        assertEquals(expected, result.getData());
    }

    @Test
    @DisplayName("writeOff: 正常请求返回核销后的账户快照")
    void writeOff_validDto_returnsAccount() {
        LscLedgerOpDTO dto = buildOpDto();
        LscAccount expected = buildAccount();
        when(ledgerService.writeOffLsc(anyLong(), anyLong(), anyString())).thenReturn(expected);

        R<LscAccount> result = controller.writeOff(dto);

        assertTrue(result.isSuccess());
        assertEquals(expected, result.getData());
    }

    @Test
    @DisplayName("refund: 正常请求返回退款后的账户快照")
    void refund_validDto_returnsAccount() {
        LscLedgerOpDTO dto = buildOpDto();
        LscAccount expected = buildAccount();
        when(ledgerService.refundLsc(anyLong(), anyLong(), anyString())).thenReturn(expected);

        R<LscAccount> result = controller.refund(dto);

        assertTrue(result.isSuccess());
        assertEquals(expected, result.getData());
    }

    @Test
    @DisplayName("issue: lockedDelta 与 availableDelta 均为 null 应抛 BizException")
    void issue_nullAmount_throwsBizException() {
        LscLedgerOpDTO dto = LscLedgerOpDTO.builder()
                .userId(1L)
                .orderNo("ORD002")
                .build();

        assertThrows(BizException.class, () -> controller.issue(dto));
    }

    // ==================== 不经过 resolveAmount 的方法 ====================

    @Test
    @DisplayName("expireTransfer: 委托 service 返回过期转回数量")
    void expireTransfer_delegatesToService() {
        LscLedgerOpDTO dto = LscLedgerOpDTO.builder().userId(1L).build();
        when(ledgerService.expireTransfer(anyLong())).thenReturn(100L);

        R<Long> result = controller.expireTransfer(dto);

        assertTrue(result.isSuccess());
        assertEquals(100L, result.getData());
    }

    @Test
    @DisplayName("expireTransferAll: 委托 service 返回全网过期转回汇总")
    void expireTransferAll_delegatesToService() {
        Map<String, Object> expected = new HashMap<>();
        expected.put("userCount", 5);
        expected.put("transferAmount", 1000L);
        when(ledgerService.expireTransferAll()).thenReturn(expected);

        R<Map<String, Object>> result = controller.expireTransferAll();

        assertTrue(result.isSuccess());
        assertEquals(5, result.getData().get("userCount"));
    }

    @Test
    @DisplayName("account: 委托 service 返回账户余额")
    void account_delegatesToService() {
        LscAccount expected = buildAccount();
        when(ledgerService.getBalance(anyLong())).thenReturn(expected);

        R<LscAccount> result = controller.account(1L);

        assertTrue(result.isSuccess());
        assertEquals(expected, result.getData());
    }

    @Test
    @DisplayName("dailySummary: 带类型列表(1,2,3)解析后委托 service")
    void dailySummary_withTypes_delegatesToService() {
        LocalDate date = LocalDate.of(2026, 8, 22);
        Map<String, Object> expected = new HashMap<>();
        expected.put("totalAmount", 500L);
        expected.put("totalCount", 10);
        when(ledgerService.dailySummary(any(), any())).thenReturn(expected);

        R<Map<String, Object>> result = controller.dailySummary(date, "1,2,3");

        assertTrue(result.isSuccess());
        assertEquals(500L, result.getData().get("totalAmount"));
    }

    @Test
    @DisplayName("dailySummary: types 为 null 时直接委托 service")
    void dailySummary_nullTypes_delegatesToService() {
        LocalDate date = LocalDate.of(2026, 8, 22);
        Map<String, Object> expected = new HashMap<>();
        expected.put("totalAmount", 0L);
        when(ledgerService.dailySummary(any(), any())).thenReturn(expected);

        R<Map<String, Object>> result = controller.dailySummary(date, null);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("lockedSummary: 委托 service 返回全网锁定汇总")
    void lockedSummary_delegatesToService() {
        Map<String, Object> expected = new HashMap<>();
        expected.put("totalLocked", 10000L);
        when(ledgerService.lockedSummary()).thenReturn(expected);

        R<Map<String, Object>> result = controller.lockedSummary();

        assertTrue(result.isSuccess());
        assertEquals(10000L, result.getData().get("totalLocked"));
    }

    @Test
    @DisplayName("releaseBatch: 委托 service 返回批量释放汇总")
    void releaseBatch_delegatesToService() {
        List<LscLedgerOpDTO> opList = Collections.singletonList(buildOpDto());
        Map<String, Object> expected = new HashMap<>();
        expected.put("successCount", 1);
        when(ledgerService.releaseBatch(anyList())).thenReturn(expected);

        R<Map<String, Object>> result = controller.releaseBatch(opList);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().get("successCount"));
    }

    @Test
    @DisplayName("transactions: 委托 service 返回流水分页结果")
    void transactions_delegatesToService() {
        Page<LscTransaction> mockPage = new Page<>();
        when(ledgerService.transactionList(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockPage);

        R<IPage<LscTransaction>> result = controller.transactions(
                1L, 1, 20, 1, "2026-01-01", "2026-01-31", "ORD001");

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("availableDetails: 委托 service 返回可用明细分页结果")
    void availableDetails_delegatesToService() {
        Page<AvailableLscDetail> mockPage = new Page<>();
        when(ledgerService.availableDetails(any(), any(), any(), any())).thenReturn(mockPage);

        R<IPage<AvailableLscDetail>> result = controller.availableDetails(1L, 1, 20, 1);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }

    @Test
    @DisplayName("recentTrend: 委托 service 返回近N天交易趋势")
    void recentTrend_delegatesToService() {
        List<Map<String, Object>> expected = Collections.singletonList(new HashMap<>());
        when(ledgerService.recentTrend(any(), any())).thenReturn(expected);

        R<List<Map<String, Object>>> result = controller.recentTrend(1L, 7);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().size());
    }

    @Test
    @DisplayName("overview: 委托 service 返回用户LSC概览")
    void overview_delegatesToService() {
        Map<String, Object> expected = new HashMap<>();
        expected.put("totalLocked", 100L);
        expected.put("totalAvailable", 50L);
        when(ledgerService.overview(anyLong())).thenReturn(expected);

        R<Map<String, Object>> result = controller.overview(1L);

        assertTrue(result.isSuccess());
        assertEquals(100L, result.getData().get("totalLocked"));
    }
}
