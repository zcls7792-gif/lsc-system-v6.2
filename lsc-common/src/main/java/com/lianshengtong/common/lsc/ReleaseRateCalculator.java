package com.lianshengtong.common.lsc;

import java.math.BigInteger;

/**
 * 日释放率计算器（V7.7.2 第五章 5.3）
 * <p>
 * 基于平台周转率 w（ppm）在 [w_min, w_max] 区间线性插值到释放率 r（ppb）。
 * 不使用浮点插值，全部整数运算。
 * </p>
 * <pre>
 * w <= w_min  -> r = r_min
 * w >= w_max  -> r = r_max
 * 否则        -> r = r_min + floor((r_max - r_min) * (w - w_min) / (w_max - w_min))
 * </pre>
 */
public final class ReleaseRateCalculator {

    private ReleaseRateCalculator() {}

    /**
     * 计算当日释放率（ppb）。
     *
     * @param wPpm       当日周转率（ppm），w = floor(max(N,0) * 1e6 / B)
     * @param wMinPpm    周转率下限（ppm），硬边界 5000
     * @param wMaxPpm    周转率上限（ppm），硬边界 25000
     * @param rMinPpb    释放率下限（ppb），常量 500000
     * @param rMaxPpb    释放率上限（ppb），常量 1000000
     * @return 释放率 ppb
     */
    public static long calcRatePpb(long wPpm, long wMinPpm, long wMaxPpm,
                                    long rMinPpb, long rMaxPpb) {
        if (wPpm <= wMinPpm) {
            return rMinPpb;
        }
        if (wPpm >= wMaxPpm) {
            return rMaxPpb;
        }
        // r = r_min + floor((r_max - r_min) * (w - w_min) / (w_max - w_min))
        long rateRange = rMaxPpb - rMinPpb;
        long wDelta = wPpm - wMinPpm;
        long wRange = wMaxPpm - wMinPpm;
        long interpolated = LscMathUtil.mulDivFloor(rateRange, wDelta, wRange);
        return rMinPpb + interpolated;
    }

    /**
     * 校验 w_min 和 w_max 配置合法性。
     * 必须满足：5000 <= w_min < w_max <= 25000
     */
    public static boolean isValidWConfig(long wMinPpm, long wMaxPpm) {
        return wMinPpm >= LscUnitConstants.W_HARD_MIN_PPM
                && wMaxPpm <= LscUnitConstants.W_HARD_MAX_PPM
                && wMinPpm < wMaxPpm;
    }

    /**
     * 计算单 Lot 当日释放额度与新余数（V7.7.2 第五章 5.4）。
     * <p>
     * numerator = original_grant_unit * r_ppb + remainder_nano_unit
     * quota = numerator div 1_000_000_000
     * new_remainder = numerator mod 1_000_000_000
     * release_unit = min(quota, remaining_locked_unit)
     * </p>
     *
     * @param originalGrantUnit   原始赠送量（不可修改）
     * @param ratePpb             当日释放率 ppb
     * @param remainderNanoUnit   上一日余数（0 至 999999999）
     * @param remainingLockedUnit 当前未冻结剩余锁定量
     * @return [release_unit, new_remainder_nano_unit]
     */
    public static long[] calcDailyRelease(long originalGrantUnit, long ratePpb,
                                           long remainderNanoUnit, long remainingLockedUnit) {
        if (remainingLockedUnit <= 0) {
            return new long[]{0L, remainderNanoUnit};
        }
        // numerator = original_grant_unit * r_ppb + remainder
        BigInteger o = BigInteger.valueOf(originalGrantUnit);
        BigInteger r = BigInteger.valueOf(ratePpb);
        BigInteger rem = BigInteger.valueOf(remainderNanoUnit);
        BigInteger base = BigInteger.valueOf(LscUnitConstants.PPB_BASE);
        BigInteger numerator = o.multiply(r).add(rem);

        BigInteger[] dr = numerator.divideAndRemainder(base);
        long quota = dr[0].longValueExact();
        long newRemainder = dr[1].longValueExact();

        long releaseUnit = Math.min(quota, remainingLockedUnit);
        return new long[]{releaseUnit, newRemainder};
    }
}
