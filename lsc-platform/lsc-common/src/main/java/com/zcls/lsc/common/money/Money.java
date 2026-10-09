package com.zcls.lsc.common.money;

import java.math.BigInteger;
import java.util.Objects;

/**
 * 人民币金额，以分为单位（4.1）。
 * 落库 BIGINT 分；展示为元保留两位小数；对接会计系统可转 decimal(18,2)，不得混用单位。
 */
public final class Money implements Comparable<Money> {

    public static final Money ZERO = new Money(0L);

    private final long cents;

    private Money(long cents) {
        this.cents = cents;
    }

    public static Money ofCents(long cents) {
        if (cents == 0L) return ZERO;
        return new Money(cents);
    }

    public static Money ofYuan(double yuan) {
        // 仅允许在 API 边界构造，且强制四舍五入到分；内部不允许使用
        long cents = Math.round(yuan * 100.0);
        return ofCents(cents);
    }

    public static Money parseCents(String decimal) {
        Objects.requireNonNull(decimal);
        String s = decimal.trim();
        boolean negative = false;
        if (s.startsWith("-")) {
            negative = true;
            s = s.substring(1);
        }
        if (s.isEmpty()) {
            throw new NumberFormatException("empty money string");
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                throw new NumberFormatException("invalid digit at " + i);
            }
        }
        BigInteger big = new BigInteger(s);
        if (big.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("money overflow Long: " + decimal);
        }
        long cents = big.longValueExact();
        return ofCents(negative ? -cents : cents);
    }

    public long cents() {
        return cents;
    }

    public BigInteger bigCents() {
        return BigInteger.valueOf(cents);
    }

    public Money plus(Money other) {
        return ofCents(Math.addExact(this.cents, other.cents));
    }

    public Money minus(Money other) {
        return ofCents(this.cents - other.cents);
    }

    public Money nonNegativeOrZero() {
        return cents < 0L ? ZERO : this;
    }

    public boolean isPositive() {
        return cents > 0L;
    }

    public boolean isZero() {
        return cents == 0L;
    }

    public String displayYuan() {
        long sign = cents < 0 ? -1 : 1;
        long abs = Math.abs(cents);
        return String.format("%s%d.%02d", sign < 0 ? "-" : "", abs / 100, abs % 100);
    }

    @Override
    public int compareTo(Money o) {
        return Long.compare(this.cents, o.cents);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money)) return false;
        return cents == ((Money) o).cents;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(cents);
    }

    @Override
    public String toString() {
        return "Money{" + cents + " cents (" + displayYuan() + " CNY)}";
    }
}
