package com.lianshengtong.mall.service.impl;

import com.lianshengtong.common.dto.HybridPayDTO;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.mall.dto.HybridPayCalcDTO;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("混合支付计算服务单元测试 (V7.3)")
class HybridPayServiceImplTest {

    private final HybridPayServiceImpl hybridPayService = new HybridPayServiceImpl();

    // ============== 正常场景 ==============

    @Test
    @DisplayName("calc: LSC抵扣受50%上限约束(全额LSC请求被截断到50%)")
    void calc_fullLscPayment_cappedAt50Percent() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(100L);

        HybridPayDTO result = hybridPayService.calc(dto);

        // V7.3: LSC抵扣上限 = 总价 × 50% = 50
        assertEquals(50L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("50.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: 部分LSC+部分人民币(未超过50%上限)")
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
    @DisplayName("calc: LSC数量超过50%上限时截断到50%")
    void calc_lscExceeds50Percent_truncated() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("50.00"));
        dto.setLscAmount(100L);

        HybridPayDTO result = hybridPayService.calc(dto);

        // V7.3: max LSC = 50 × 50% = 25
        assertEquals(25L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("25.00"), result.getRmbAmount());
    }

    // ============== 可用余额约束 ==============

    @Test
    @DisplayName("calc: 受可用余额上限约束(低于50%上限)")
    void calc_maxAvailableLsc_constrained() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("200.00"));
        dto.setLscAmount(200L);
        dto.setMaxAvailableLsc(50L);

        HybridPayDTO result = hybridPayService.calc(dto);

        // 50%上限=100，可用余额=50，取较小值
        assertEquals(50L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("150.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: 可用余额比50%上限多时受50%约束")
    void calc_maxAvailableMoreThan50Percent_cappedByRatio() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(80L);
        dto.setMaxAvailableLsc(80L);

        HybridPayDTO result = hybridPayService.calc(dto);

        // 50%上限=50，可用余额=80，取较小值50
        assertEquals(50L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("50.00"), result.getRmbAmount());
    }

    // ============== V7.3 预计赠送 LSC ==============

    @Test
    @DisplayName("calc: 纯人民币支付全额赠送")
    void calc_estimatedGrant_pureRmb_fullGrant() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(0L);
        dto.setGrantPoints(20L);

        HybridPayDTO result = hybridPayService.calc(dto);

        // 纯人民币支付，全额赠送 = 20
        assertEquals(20L, result.getEstimatedGrantLsc().longValue());
    }

    @Test
    @DisplayName("calc: 混合支付按人民币比例赠送")
    void calc_estimatedGrant_hybrid_proportional() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(30L);
        dto.setGrantPoints(20L);

        HybridPayDTO result = hybridPayService.calc(dto);

        // 人民币支付70元，占70%，赠送 = 20 × 70/100 = 14
        assertEquals(30L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("70.00"), result.getRmbAmount());
        assertEquals(14L, result.getEstimatedGrantLsc().longValue());
    }

    @Test
    @DisplayName("calc: LSC抵扣部分不赠送")
    void calc_estimatedGrant_lscDeductNoGrant() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(50L); // 50%上限
        dto.setGrantPoints(20L);

        HybridPayDTO result = hybridPayService.calc(dto);

        // LSC抵扣50元，人民币支付50元，赠送 = 20 × 50/100 = 10
        assertEquals(50L, result.getLscAmount().longValue());
        assertEquals(10L, result.getEstimatedGrantLsc().longValue());
    }

    @Test
    @DisplayName("calc: 未提供grantPoints时预计赠送为0")
    void calc_estimatedGrant_nullGrantPoints() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(0L);
        // grantPoints 未设置

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getEstimatedGrantLsc().longValue());
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
        dto.setLscAmount(49L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(49L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("50.99"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: 零总价返回0")
    void calc_zeroPrice_returnsZero() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("0.00"));
        dto.setLscAmount(10L);
        dto.setGrantPoints(5L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
        assertEquals(0L, result.getEstimatedGrantLsc().longValue());
    }

    @Test
    @DisplayName("calc: 大额支付精度正确")
    void calc_largeAmount_precisionCorrect() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("999999.99"));
        dto.setLscAmount(500000L);

        HybridPayDTO result = hybridPayService.calc(dto);

        // 50%上限 = floor(999999.99 × 0.5) = 499999
        assertEquals(499999L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("500000.99"), result.getRmbAmount());
    }
}
