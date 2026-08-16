package com.lianshengtong.common.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 释放计算工具类
 * 严格执行：
 * - LSC数值使用bigint（Long）
 * - 中间运算使用decimal(18,6)
 * - 结果统一向下取整
 */
public class ReleaseCalcUtil {

    public static final int SCALE = 6;

    /**
     * 计算释放比例rate
     *
     * @param k        核销率
     * @param rateMax  0.05%硬上限
     * @param rateMin  0.03%硬下限
     * @param kMin     0.50%
     * @param kMax     1.0%
     * @param alpha    调节因子0.05
     */
    public static BigDecimal calcRate(BigDecimal k, BigDecimal rateMax, BigDecimal rateMin,
                                      BigDecimal kMin, BigDecimal kMax, BigDecimal alpha) {
        BigDecimal rate;
        if (k.compareTo(kMin) <= 0) {
            rate = rateMax;
        } else if (k.compareTo(kMax) >= 0) {
            rate = rateMin;
        } else {
            // rate = 0.075% - 0.05 * k
            BigDecimal base = new BigDecimal("0.00075"); // 0.075%
            rate = base.subtract(alpha.multiply(k));
        }
        return rate.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 计算当日释放总量（向下取整）
     */
    public static long calcReleaseAmount(long lLocked, BigDecimal rate) {
        BigDecimal l = BigDecimal.valueOf(lLocked);
        BigDecimal result = l.multiply(rate).setScale(SCALE, RoundingMode.HALF_UP);
        // 向下取整到整数个LSC
        return result.setScale(0, RoundingMode.DOWN).longValue();
    }

    /**
     * 百分比字符串转BigDecimal，如 "0.05%" -> 0.0005
     */
    public static BigDecimal percent(String pct) {
        if (pct == null || pct.isEmpty()) return BigDecimal.ZERO;
        String v = pct.trim();
        if (v.endsWith("%")) {
            return new BigDecimal(v.substring(0, v.length() - 1))
                    .divide(new BigDecimal("100"), SCALE, RoundingMode.HALF_UP);
        }
        return new BigDecimal(v);
    }

    /**
     * 校验rate是否在硬约束范围内
     */
    public static boolean isRateValid(BigDecimal rate, BigDecimal rateMin, BigDecimal rateMax) {
        return rate.compareTo(rateMin) >= 0 && rate.compareTo(rateMax) <= 0;
    }
}
