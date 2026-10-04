package com.zcls.lsc.common.money;

import java.math.BigInteger;
import java.util.Objects;

/**
 * LSC 权益数量（unit）。
 * 第4.1章：1 LSC = 10000 unit；100 unit 抵扣 1 分人民币。
 * 落库为 BIGINT 非负整数，传输为十进制字符串以避免 JavaScript 大整数损失。
 * 严禁 double/float 参与账务，中间乘法使用 BigInteger 防溢出。
 */
public final class Units implements Comparable<Units> {

    /** 1 LSC = 10000 unit（代码常量 unit_scale，1.3 不可突破）。 */
    public static final long UNIT_SCALE = 10_000L;

    /** 100 unit 对应 1 分人民币（units_per_cent，代码常量）。 */
    public static final long UNITS_PER_CENT = 100L;

    /** 抵扣允许的最小单位粒度：100 的整数倍（4.2 + CHECK MOD(lsc_unit,100)=0）。 */
    public static final long DEDUCTION_STEP = 100L;

    public static final Units ZERO = new Units(0L);

    private final long value;

    private Units(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("Units must be non-negative, got " + value);
        }
        this.value = value;
    }

    public static Units of(long value) {
        if (value == 0L) return ZERO;
        return new Units(value);
    }

    /** 从十进制字符串构造（API 边界）。 */
    public static Units parse(String decimal) {
        Objects.requireNonNull(decimal, "decimal");
        String trimmed = decimal.trim();
        if (trimmed.isEmpty()) {
            throw new NumberFormatException("empty units string");
        }
        // 仅允许十进制数字非负
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < '0' || c > '9') {
                throw new NumberFormatException("invalid digit '" + c + "' in \"" + decimal + "\"");
            }
        }
        BigInteger big = new BigInteger(trimmed);
        if (big.signum() < 0) {
            throw new IllegalArgumentException("negative units: " + decimal);
        }
        // 超出 long 范围直接拒绝，强制走字符串通道
        if (big.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("units overflow Long: " + decimal);
        }
        return of(big.longValueExact());
    }

    public long longValue() {
        return value;
    }

    /** 转 BigInteger 用于中间乘法防溢出。 */
    public BigInteger bigValue() {
        return BigInteger.valueOf(value);
    }

    /** 输出十进制字符串（API 传输口径）。 */
    public String toDecimalString() {
        return Long.toString(value);
    }

    /** 展示为 LSC 文本（4 位小数），仅展示用途，禁止用于记账。 */
    public String displayLsc() {
        long whole = value / UNIT_SCALE;
        long frac = value % UNIT_SCALE;
        return String.format("%d.%04d", whole, frac);
    }

    public Units plus(Units other) {
        return of(Math.addExact(this.value, other.value));
    }

    public Units minus(Units other) {
        long r = this.value - other.value;
        if (r < 0L) {
            throw new ArithmeticException("Units underflow: " + this.value + " - " + other.value);
        }
        return of(r);
    }

    public Units min(Units other) {
        return of(Math.min(this.value, other.value));
    }

    public boolean isZero() {
        return value == 0L;
    }

    public boolean isPositive() {
        return value > 0L;
    }

    /** 校验是否抵扣单位（100 整数倍）。 */
    public boolean isDeductionStep() {
        return value % DEDUCTION_STEP == 0L;
    }

    @Override
    public int compareTo(Units o) {
        return Long.compare(this.value, o.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Units)) return false;
        return value == ((Units) o).value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return "Units{" + toDecimalString() + " (" + displayLsc() + " LSC)}";
    }
}
