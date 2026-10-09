package com.zcls.lsc.common.ratio;

/**
 * 比例口径单位说明（4.1）：
 *  - ppm：百万分之一，1 = 1_000_000 ppm。grant_coefficient 默认 800000；抵扣比例默认 200000。
 *  - ppb：十亿分之一，1 = 1_000_000_000 ppb。释放率单独使用，单位不可混用。
 *  中间乘法使用 BigInteger 防溢出，禁止 double/float。
 */
public final class RatioScale {
    /** 1 = 1_000_000 ppm。 */
    public static final long PPM_SCALE = 1_000_000L;

    /** 1 = 1_000_000_000 ppb。 */
    public static final long PPB_SCALE = 1_000_000_000L;

    /** 抵扣比例硬顶 50%（4.2 / 1.3）。 */
    public static final long DEDUCTION_MAX_PPM = 500_000L;

    /** 系数 K 范围 0 至 1_000_000 ppm（4.3）。 */
    public static final long GRANT_COEF_MAX_PPM = 1_000_000L;

    /** 释放率下限 0.05% = 500_000 ppb（5.3 常量）。 */
    public static final long RELEASE_MIN_PPB = 500_000L;

    /** 释放率上限 0.10% = 1_000_000 ppb（5.3 常量）。 */
    public static final long RELEASE_MAX_PPB = 1_000_000L;

    /** 周转率硬下限 0.5% = 5_000 ppm（5.3）。 */
    public static final long W_HARD_MIN_PPM = 5_000L;

    /** 周转率硬上限 2.5% = 25_000 ppm（5.3）。 */
    public static final long W_HARD_MAX_PPM = 25_000L;

    private RatioScale() {}
}
