package com.zcls.lsc.account.release;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 第15章 日释放算法测试（T09, T10, T12, T13）。
 *
 * 公式:
 *   numerator = original_grant_unit × r_ppb + remainder_nano_unit
 *   quota = numerator div 1_000_000_000
 *   new_remainder = numerator mod 1_000_000_000
 *   release_unit = min(quota, remaining_locked_unit)
 *
 * 释放率:
 *   r_min = 500_000 ppb (0.05%)
 *   r_max = 1_000_000 ppb (0.10%)
 *   w=1.5%(15000ppm) → r=750000 ppb (0.075%)
 */
class ReleaseCalculatorTest {

    private final ReleaseCalculator calc = new ReleaseCalculator();

    /**
     * T09: w 为 1% / 1.5% / 2% → 率分别 0.05% / 0.075% / 0.10%
     * w_min=10000(1%), w_max=20000(2%)
     */
    @Test
    void t09_turnoverRateMapping() {
        long wMin = 10_000L;
        long wMax = 20_000L;
        long rMin = 500_000L;
        long rMax = 1_000_000L;

        // w=1% → r_min
        assertEquals(500_000L, calc.mapRate(10_000L, wMin, wMax, rMin, rMax));
        // w=1.5% → 0.075% = 750000 ppb
        assertEquals(750_000L, calc.mapRate(15_000L, wMin, wMax, rMin, rMax));
        // w=2% → r_max
        assertEquals(1_000_000L, calc.mapRate(20_000L, wMin, wMax, rMin, rMax));
        // w<w_min → r_min
        assertEquals(500_000L, calc.mapRate(5_000L, wMin, wMax, rMin, rMax));
        // w>w_max → r_max
        assertEquals(1_000_000L, calc.mapRate(30_000L, wMin, wMax, rMin, rMax));
    }

    /**
     * T10: w 分母为零或净消耗为负 → 最低率(在 ReleaseService 层处理)，
     * 这里验证 mapRate 在 w_min 边界返回 r_min。
     */
    @Test
    void t10_zeroDenominator_returnsMinRate() {
        // 分母为0时 w=0, mapRate(0) = r_min
        assertEquals(500_000L, calc.mapRate(0L, 10_000L, 20_000L, 500_000L, 1_000_000L));
    }

    /**
     * T12: 原始 1 unit 最低率运行 2000 日 → 前 1999 日未超发，第 2000 日清零
     * 最低率 0.05% = 500000 ppb
     * 每日: numerator = 1 × 500000 + remainder = 500000 + remainder
     * 因为 500000 < 1_000_000_000，所以 quota=0，remainder = 500000 + remainder
     * 余数逐日累加 500000，直到 ≥ 1_000_000_000 时释放 1 unit。
     *
     * 第1日: rem=0, num=500000, quota=0, rem=500000
     * 第2日: rem=500000, num=1000000, quota=0, rem=1000000
     * ...
     * 第1999日: rem=500000×1998=999000000, num=999000000+500000=999500000, quota=0, rem=999500000
     * 第2000日: rem=999500000, num=999500000+500000=1000000000, quota=1, rem=0
     *
     * 所以前1999日 quota=0, 第2000日 quota=1。
     * 验证连续 1999 次 quota=0, 第 2000 次 quota=1。
     */
    @Test
    void t12_original1Unit_minRate_2000DaysToClear() {
        long original = 1L;
        long rate = 500_000L;
        long remainder = 0L;
        long remaining = 1L;

        for (int day = 1; day <= 1999; day++) {
            ReleaseCalculator.ReleaseResult r = calc.calc(original, rate, remainder, remaining);
            assertEquals(0L, r.releaseUnit(), "day " + day + " should release 0");
            remainder = r.newRemainder();
        }

        // 第2000日应释放1
        ReleaseCalculator.ReleaseResult r = calc.calc(original, rate, remainder, remaining);
        assertEquals(1L, r.releaseUnit(), "day 2000 should release 1");
        assertEquals(0L, r.newRemainder());
    }

    /**
     * T13: 原始 160000 unit 最低率 → 每有效日额度 80 unit
     * 160000 × 500000 / 1_000_000_000 = 80
     */
    @Test
    void t13_original160000Unit_minRate_daily80Unit() {
        ReleaseCalculator.ReleaseResult r = calc.calc(160_000L, 500_000L, 0L, 160_000L);
        assertEquals(80L, r.releaseUnit());
        // 余数 = 160000 × 500000 mod 1_000_000_000 = 0
        assertEquals(0L, r.newRemainder());
    }

    /**
     * 验证封顶于 remaining_locked：quota=80 但 remaining=50 → 释放 50，余数正常。
     */
    @Test
    void releaseCappedByRemainingLocked() {
        // original=160000, rate=500000 → quota=80, 但 remaining=50
        ReleaseCalculator.ReleaseResult r = calc.calc(160_000L, 500_000L, 0L, 50L);
        assertEquals(50L, r.releaseUnit());
    }

    /**
     * 验证余数累计：original=3, rate=500000
     * 每日 quota = floor(3×500000/1e9) = floor(1500000/1e9) = 0
     * 余数逐日加 1500000
     * 第667日: rem = 1500000×666 = 999000000, num=999000000+1500000=1000500000
     * quota = 1, rem = 500000
     */
    @Test
    void remainderAccumulates() {
        long original = 3L;
        long rate = 500_000L;
        long remainder = 0L;

        for (int day = 1; day <= 666; day++) {
            ReleaseCalculator.ReleaseResult r = calc.calc(original, rate, remainder, 100L);
            assertEquals(0L, r.releaseUnit());
            remainder = r.newRemainder();
        }
        assertEquals(999_000_000L, remainder);

        ReleaseCalculator.ReleaseResult r = calc.calc(original, rate, remainder, 100L);
        assertEquals(1L, r.releaseUnit());
        assertEquals(500_000L, r.newRemainder());
    }

    @Test
    void invalidRate_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> calc.calc(100L, 499_999L, 0L, 100L));
        assertThrows(IllegalArgumentException.class,
                () -> calc.calc(100L, 1_000_001L, 0L, 100L));
    }

    @Test
    void remainderOutOfRange_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> calc.calc(100L, 500_000L, 1_000_000_000L, 100L));
    }
}
