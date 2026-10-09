package com.zcls.lsc.account.release;

import com.zcls.lsc.common.constants.LscConstants;
import com.zcls.lsc.common.ratio.RatioScale;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

/**
 * 第5.4章 日释放算法。
 *
 * 每个 GrantLot:
 *   numerator = original_grant_unit × r_ppb + remainder_nano_unit
 *   quota = numerator div 1_000_000_000
 *   new_remainder = numerator mod 1_000_000_000
 *   release_unit = min(quota, remaining_locked_unit)
 *
 * 余数取值 0..999_999_999。
 * 中间乘法用 BigInteger 防溢出。
 */
@Component
public class ReleaseCalculator {

    /**
     * 计算某日释放量与新余数。
     *
     * @param originalGrantUnit 原始赠送量(不可修改)
     * @param ratePpb           当日释放率 ppb
     * @param remainderNanoUnit 当前余数(0..999_999_999)
     * @param remainingLocked   当前未冻结剩余锁定量
     * @return ReleaseResult(releaseUnit, newRemainder)
     */
    public ReleaseResult calc(long originalGrantUnit, long ratePpb,
                              long remainderNanoUnit, long remainingLocked) {
        if (originalGrantUnit <= 0L) {
            throw new IllegalArgumentException("original_grant_unit must be > 0");
        }
        if (ratePpb < LscConstants.RELEASE_RATE_MIN_PPB || ratePpb > LscConstants.RELEASE_RATE_MAX_PPB) {
            throw new IllegalArgumentException("rate_ppb out of range: " + ratePpb);
        }
        if (remainderNanoUnit < 0L || remainderNanoUnit >= RatioScale.PPB_SCALE) {
            throw new IllegalArgumentException("remainder out of range: " + remainderNanoUnit);
        }
        if (remainingLocked < 0L) {
            throw new IllegalArgumentException("remaining_locked must be >= 0");
        }
        if (remainingLocked == 0L) {
            return new ReleaseResult(0L, remainderNanoUnit);
        }

        // numerator = original × rate + remainder
        BigInteger numerator = BigInteger.valueOf(originalGrantUnit)
                .multiply(BigInteger.valueOf(ratePpb))
                .add(BigInteger.valueOf(remainderNanoUnit));

        BigInteger ppb = BigInteger.valueOf(RatioScale.PPB_SCALE);
        BigInteger[] div = numerator.divideAndRemainder(ppb);
        long quota = div[0].longValueExact();
        long newRemainder = div[1].longValueExact();

        // 封顶于剩余锁定量
        long release = Math.min(quota, remainingLocked);

        return new ReleaseResult(release, newRemainder);
    }

    /**
     * 计算周转率映射的释放率 ppb（第5.3章）。
     * w ≤ w_min -> r_min; w ≥ w_max -> r_max; 线性插值。
     * 不使用浮点插值。
     *
     * @param wPpm     周转率 ppm
     * @param wMinPpm  w 下限 ppm
     * @param wMaxPpm  w 上限 ppm
     * @param rMinPpb  释放率下限 ppb
     * @param rMaxPpb  释放率上限 ppb
     * @return 释放率 ppb
     */
    public long mapRate(long wPpm, long wMinPpm, long wMaxPpm, long rMinPpb, long rMaxPpb) {
        if (wPpm <= wMinPpm) return rMinPpb;
        if (wPpm >= wMaxPpm) return rMaxPpb;
        // r = r_min + floor((r_max - r_min) * (w - w_min) / (w_max - w_min))
        BigInteger span = BigInteger.valueOf(wPpm - wMinPpm);
        BigInteger rateSpan = BigInteger.valueOf(rMaxPpb - rMinPpb);
        BigInteger denom = BigInteger.valueOf(wMaxPpm - wMinPpm);
        long delta = rateSpan.multiply(span).divide(denom).longValueExact();
        return rMinPpb + delta;
    }

    public record ReleaseResult(long releaseUnit, long newRemainder) {}
}
