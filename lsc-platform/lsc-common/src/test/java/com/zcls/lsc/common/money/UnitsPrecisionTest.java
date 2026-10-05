package com.zcls.lsc.common.money;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 第15章 T41: 字段为超 JS 安全整数的 unit → API 字符串往返无精度损失。
 * JS 安全整数上限 2^53 - 1 ≈ 9.007 × 10^15。
 */
class UnitsPrecisionTest {

    @Test
    void t41_largeUnits_stringRoundTrip() {
        // 接近 Long.MAX_VALUE 的值，远超 JS 安全整数
        long big = Long.MAX_VALUE - 1L;
        Units u = Units.of(big);
        String s = u.toDecimalString();
        Units parsed = Units.parse(s);
        assertEquals(u, parsed);
        assertEquals(big, parsed.longValue());
    }

    @Test
    void unitsOverLongMax_throws() {
        String overMax = new java.math.BigInteger(Long.toString(Long.MAX_VALUE))
                .add(java.math.BigInteger.ONE).toString();
        assertThrows(IllegalArgumentException.class, () -> Units.parse(overMax));
    }

    @Test
    void negativeUnits_throws() {
        assertThrows(IllegalArgumentException.class, () -> Units.of(-1L));
    }

    @Test
    void deductionStepValidation() {
        assertEquals(true, Units.of(100L).isDeductionStep());
        assertEquals(true, Units.of(200L).isDeductionStep());
        assertEquals(false, Units.of(101L).isDeductionStep());
    }

    @Test
    void displayLsc_fourDecimals() {
        assertEquals("40.0000", Units.of(400_000L).displayLsc());
        assertEquals("33.8461", Units.of(338_461L).displayLsc());
        assertEquals("0.0100", Units.of(100L).displayLsc());
    }

    @Test
    void moneyCents_roundTrip() {
        Money m = Money.ofCents(130_00L);
        assertEquals("130.00", m.displayYuan());
        assertEquals(130_00L, m.cents());
    }
}
