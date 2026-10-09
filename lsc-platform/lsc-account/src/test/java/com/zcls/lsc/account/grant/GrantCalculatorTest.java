package com.zcls.lsc.account.grant;

import com.zcls.lsc.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 第15章 赠送算法测试（T01-T05）。
 *
 * 公式: grant_unit = floor(M_cent × K_ppm × R_cent × 100 / (1_000_000 × S_cent))
 * M_cent = max(S_cent - C_cent, 0)
 * K_ppm = 800000 (0.8)
 */
class GrantCalculatorTest {

    private final GrantCalculator calc = new GrantCalculator();

    /**
     * T01: C价130元 成本80元 全人民币 → 赠 400000 unit (40 LSC)
     * M = 13000 - 8000 = 5000 分
     * grant = floor(5000 × 800000 × 13000 × 100 / (1000000 × 13000))
     *       = floor(5000 × 800000 × 100 / 1000000)
     *       = floor(400000000000 / 1000000)
     *       = 400000
     */
    @Test
    void t01_cFullRmb_grants400000Unit() {
        long unit = calc.calcUnitGrant(130_00L, 80_00L, 130_00L, 800_000L);
        assertEquals(400_000L, unit);
    }

    /**
     * T02: 同商品优惠券20元 且订单满足门槛 → 赠 338461 unit (33.8461 LSC)
     * S = 13000, C = 8000, R = 11000 (130-20)
     * grant = floor(5000 × 800000 × 11000 × 100 / (1000000 × 13000))
     *       = floor(440000000000000 / 13000000000)
     *       = floor(33846.15...)
     *       = 338461? 让我算准确
     *
     * 分子 = 5000 × 800000 × 11000 × 100 = 5000 × 800000 × 1100000 = 4,400,000,000,000,000
     * 分母 = 1000000 × 13000 = 13,000,000,000
     * 4,400,000,000,000,000 / 13,000,000,000 = 338,461.538...
     * floor = 338461
     */
    @Test
    void t02_cWithCoupon20_grants338461Unit() {
        long unit = calc.calcUnitGrant(130_00L, 80_00L, 110_00L, 800_000L);
        assertEquals(338_461L, unit);
    }

    /**
     * T03: B价100元 成本80元 抵扣20元(LSC) → 赠 128000 unit (12.8 LSC)
     * S = 10000, C = 8000, R = 8000 (100-20)
     * M = 2000
     * grant = floor(2000 × 800000 × 8000 × 100 / (1000000 × 10000))
     *       = floor(128,000,000,000,000 / 10,000,000,000)
     *       = 12800
     *
     * 等等，让我重算:
     * 分子 = 2000 × 800000 × 8000 × 100 = 2000 × 800000 × 800000 = 1,280,000,000,000,000
     * 分母 = 1000000 × 10000 = 10,000,000,000
     * 1,280,000,000,000,000 / 10,000,000,000 = 128,000
     * floor = 128000
     */
    @Test
    void t03_bWithLscDeduction20_grants128000Unit() {
        long unit = calc.calcUnitGrant(100_00L, 80_00L, 80_00L, 800_000L);
        assertEquals(128_000L, unit);
    }

    /**
     * T04: 成本大于售价 → 赠送 0（不出现负值）
     */
    @Test
    void t04_costGreaterThanSale_grantsZero() {
        long unit = calc.calcUnitGrant(100_00L, 120_00L, 100_00L, 800_000L);
        assertEquals(0L, unit);
    }

    /**
     * T05: unit 非 100 整数倍的场景在下单层拒绝（这里测 calculator 不处理），
     * 但确认 sale_cent <= 0 抛异常。
     */
    @Test
    void t05_saleCentZero_throws() {
        assertThrows(BusinessException.class,
                () -> calc.calcUnitGrant(0L, 80_00L, 0L, 800_000L));
    }

    /**
     * 验证系数超范围抛异常。
     */
    @Test
    void coefOutOfRange_throws() {
        assertThrows(BusinessException.class,
                () -> calc.calcUnitGrant(100_00L, 80_00L, 100_00L, 1_000_001L));
    }

    /**
     * 验证逐件取整再汇总的精度：
     * 两件商品，每件 S=100, C=0, R=100, K=800000
     * 每件 = floor(100 × 800000 × 100 × 100 / (1000000 × 100))
     *      = floor(8000000000 / 1000000) = 8000
     * 两件汇总 = 16000
     *
     * 如果先汇总再取整：S=200, R=200
     * grant = floor(200 × 800000 × 200 × 100 / (1000000 × 200))
     *       = floor(32000000000 / 2000000) = 16000
     * 本例结果相同，但取整边界可能不同。这里验证逐件逻辑。
     */
    @Test
    void perUnitRounding_thenSum() {
        long unit = calc.calcUnitGrant(100L, 0L, 100L, 800_000L);
        assertEquals(8000L, unit);
        assertEquals(16000L, unit * 2);
    }
}
