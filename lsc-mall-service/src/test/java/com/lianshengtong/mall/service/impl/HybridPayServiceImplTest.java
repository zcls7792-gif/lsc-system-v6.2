package com.lianshengtong.mall.service.impl;

import com.lianshengtong.common.dto.HybridPayDTO;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.mall.dto.HybridPayCalcDTO;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("混合支付计算服务单元测试")
class HybridPayServiceImplTest {

    private final HybridPayServiceImpl hybridPayService = new HybridPayServiceImpl();

    // ============== 正常场景 ==============

    @Test
    @DisplayName("calc: 全额LSC支付")
    void calc_fullLscPayment() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(100L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(100L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: 部分LSC+部分人民币")
    void calc_partialLscAndRmb() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(30L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(30L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("70.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: 纯人民币支付(LSC=0)")
    void calc_pureRmbPayment() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("50.00"));
        dto.setLscAmount(0L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("50.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: LSC数量超过总价时截断到总价")
    void calc_lscExceedsPrice_truncated() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("50.00"));
        dto.setLscAmount(100L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(50L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
    }

    // ============== 可用余额约束 ==============

    @Test
    @DisplayName("calc: 受可用余额上限约束")
    void calc_maxAvailableLsc_constrained() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("200.00"));
        dto.setLscAmount(200L);
        dto.setMaxAvailableLsc(50L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(50L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("150.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: 可用余额比请求少时截断")
    void calc_maxAvailableLessThanRequested() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(80L);
        dto.setMaxAvailableLsc(30L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(30L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("70.00"), result.getRmbAmount());
    }

    // ============== 边界场景 ==============

    @Test
    @DisplayName("calc: LSC负数抛异常")
    void calc_negativeLsc_throws() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(-1L);

        assertThrows(BizException.class, () -> hybridPayService.calc(dto));
    }

    @Test
    @DisplayName("calc: 小数金额正确舍入")
    void calc_decimalAmount_correctRounding() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("99.99"));
        dto.setLscAmount(50L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(50L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("49.99"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: LSC=0且可用余额为null正常计算")
    void calc_zeroLsc_nullAvailable() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("25.50"));
        dto.setLscAmount(0L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("25.50"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: 大额支付精度正确")
    void calc_largeAmount_precisionCorrect() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("999999.99"));
        dto.setLscAmount(500000L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(500000L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("499999.99"), result.getRmbAmount());
    }
}
