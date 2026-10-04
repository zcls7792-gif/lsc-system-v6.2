package com.lianshengtong.common.lsc;

/**
 * LSC 单位常量（V7.7.2 第四章 4.1）
 * <p>
 * 1 LSC = 10000 unit；100 unit 抵扣 1 分人民币；10000 unit 抵扣 1 元。
 * 权益落库为 BIGINT 非负整数，传输为十进制字符串。
 * 比例采用整数 ppm（1 = 1000000 ppm），释放率采用 ppb（1 = 1000000000 ppb）。
 * </p>
 */
public final class LscUnitConstants {

    private LscUnitConstants() {}

    /** 1 LSC = 10000 unit */
    public static final long UNIT_PER_LSC = 10_000L;

    /** 100 unit = 1 分人民币 */
    public static final long UNIT_PER_CENT = 100L;

    /** 10000 unit = 1 元人民币 = 100 分 */
    public static final long UNIT_PER_YUAN = 10_000L;

    /** 1 元 = 100 分 */
    public static final long CENT_PER_YUAN = 100L;

    /** ppm 基数：1 = 1000000 ppm */
    public static final long PPM_BASE = 1_000_000L;

    /** ppb 基数：1 = 1000000000 ppb */
    public static final long PPB_BASE = 1_000_000_000L;

    /** 余数 nano_unit 上限：0 至 999999999 */
    public static final long NANO_UNIT_MAX = 999_999_999L;

    // ===== 硬边界常量（不可后台修改）=====

    /** 赠送系数 K_ppm 上限 1000000（即 1.0） */
    public static final long GRANT_COEFFICIENT_MAX_PPM = 1_000_000L;

    /** 抵扣比例硬顶 50% = 500000 ppm */
    public static final long DEDUCTION_MAX_PPM = 500_000L;

    /** 释放率下限 0.05% = 500000 ppb */
    public static final long RELEASE_MIN_PPB = 500_000L;

    /** 释放率上限 0.10% = 1000000 ppb */
    public static final long RELEASE_MAX_PPB = 1_000_000L;

    /** 周转率 w 硬下限 0.5% = 5000 ppm */
    public static final long W_HARD_MIN_PPM = 5_000L;

    /** 周转率 w 硬上限 2.5% = 25000 ppm */
    public static final long W_HARD_MAX_PPM = 25_000L;

    /** 默认 w_min 1% = 10000 ppm */
    public static final long W_MIN_DEFAULT_PPM = 10_000L;

    /** 默认 w_max 2% = 20000 ppm */
    public static final long W_MAX_DEFAULT_PPM = 20_000L;

    /** 默认赠送系数 0.8 = 800000 ppm */
    public static final long GRANT_COEFFICIENT_DEFAULT_PPM = 800_000L;

    /** 默认抵扣比例 20% = 200000 ppm */
    public static final long DEDUCTION_DEFAULT_PPM = 200_000L;

    /** 权益有效期 365 天 */
    public static final int LSC_VALID_DAYS = 365;

    /** 已过期权益退款返还宽限期 30 天 */
    public static final int EXPIRED_LSC_REFUND_GRACE_DAYS = 30;

    /** 已过期券退款补发宽限期 7 天 */
    public static final int EXPIRED_COUPON_REFUND_GRACE_DAYS = 7;

    /** 报价有效期 5 分钟 */
    public static final int QUOTE_TTL_MINUTES = 5;

    /** 默认支付期限 15 分钟 */
    public static final int PAYMENT_TTL_MINUTES = 15;
}
