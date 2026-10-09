package com.zcls.lsc.common.constants;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * 第1.3章 不可突破的边界 与 附录B 核心参数字典。
 * 代码硬边界高于后台可编辑参数；任何修改需双签并按 5.3 / 14.2 走版本生效规则。
 */
public final class LscConstants {

    private LscConstants() {}

    // ===== 1.3 不可突破的边界 =====
    /** LSC 不可购买、充值、转让、提现、兑现或跨账户流转；本注释仅为提示，业务由代码路径实现。 */

    /** 抵扣比例硬顶 50%。 */
    public static final long DEDUCTION_MAX_PPM = 500_000L;

    /** 日释放率下限 0.05%（ppb）。 */
    public static final long RELEASE_RATE_MIN_PPB = 500_000L;

    /** 日释放率上限 0.10%（ppb）。 */
    public static final long RELEASE_RATE_MAX_PPB = 1_000_000L;

    /** 周转率硬下限 0.5%（ppm）。 */
    public static final long W_HARD_MIN_PPM = 5_000L;

    /** 周转率硬上限 2.5%（ppm）。 */
    public static final long W_HARD_MAX_PPM = 25_000L;

    /** 经营空间回馈系数 K 范围 0 至 1（ppm）。 */
    public static final long GRANT_COEF_MAX_PPM = 1_000_000L;

    // ===== 附录B 参数字典（默认初始值，运营可调部分以运营配置为准） =====
    public static final long UNIT_SCALE = 10_000L;
    public static final long UNITS_PER_CENT = 100L;
    public static final long GRANT_COEF_DEFAULT_PPM = 800_000L;
    public static final long DEDUCTION_DEFAULT_PPM = 200_000L;

    public static final long W_MIN_DEFAULT_PPM = 10_000L;  // 1%
    public static final long W_MAX_DEFAULT_PPM = 20_000L;  // 2%

    /** 权益有效期 365 天，本版固定。 */
    public static final long LSC_VALID_DAYS = 365L;

    /** 已过期权益退款返还宽限 30 天（7.4 售后政策版本）。 */
    public static final long EXPIRED_LSC_REFUND_GRACE_DAYS = 30L;

    /** 已核销券退款补券宽限 7 天（8.4 售后政策版本）。 */
    public static final long EXPIRED_COUPON_REFUND_GRACE_DAYS = 7L;

    /** 报价默认 TTL 5 分钟（13.1）。 */
    public static final Duration QUOTE_TTL = Duration.of(5, ChronoUnit.MINUTES);

    /** 订单默认支付期限 15 分钟（7.1）。 */
    public static final Duration PAYMENT_TTL = Duration.of(15, ChronoUnit.MINUTES);

    /** 推荐券四档：序号 -> 面额（分）、门槛（分）、领取后有效期天数（8.1）。 */
    public static final long[][] REFERRAL_TIERS = {
            // {min_success_seq, face_cent, min_spend_cent, valid_days}
            {1L,  20_00L, 200_00L, 30L},
            {2L,  30_00L, 250_00L, 40L},
            {3L,  40_00L, 300_00L, 50L},
            {4L,  40_00L, 300_00L, 60L}, // 第 4 位及以后
    };

    // ===== 13.2 错误码（业务码） =====
    public static final int E_COUPON_LSC_MUTEX        = 42206;
    public static final int E_DEDUCTION_OVER_LIMIT    = 42207;
    public static final int E_UNIT_NOT_MULTIPLE_100   = 42208;
    public static final int E_COUPON_NOT_APPLICABLE   = 42209;
    public static final int E_QUOTE_EXPIRED           = 40901;
    public static final int E_IDEMPOTENCY_CONFLICT    = 40902;
    public static final int E_RESOURCE_INSUFFICIENT   = 40903;
    public static final int E_BENEFIT_BLOCKED         = 42301;
    public static final int E_ACCOUNT_SERVICE_DOWN    = 50301;

    // ===== 6.2 桶枚举字符串 =====
    public static final String BUCKET_LOCKED              = "LOCKED";
    public static final String BUCKET_AVAILABLE           = "AVAILABLE";
    public static final String BUCKET_RESERVED            = "RESERVED";
    public static final String BUCKET_FROZEN_LOCKED       = "FROZEN_LOCKED";
    public static final String BUCKET_FROZEN_AVAILABLE    = "FROZEN_AVAILABLE";

    // ===== 来源类型 =====
    public static final String ORIGIN_GRANT              = "GRANT";
    public static final String ORIGIN_REFUND_RESTORE     = "REFUND_RESTORE";
}
