package com.zcls.lsc.common.error;

import com.zcls.lsc.common.constants.LscConstants;

/**
 * 第13.2章 错误码。业务码记入内部处理结果，不直接用 HTTP 422 替代已收款事实处理。
 */
public enum ErrorCode {

    /** 42206 券与 LSC 互斥 */
    COUPON_LSC_MUTEX(LscConstants.E_COUPON_LSC_MUTEX, "coupon and LSC are mutually exclusive"),

    /** 42207 抵扣超过上限 */
    DEDUCTION_OVER_LIMIT(LscConstants.E_DEDUCTION_OVER_LIMIT, "deduction over the limit"),

    /** 42208 unit 非 100 整数倍 */
    UNIT_NOT_MULTIPLE_100(LscConstants.E_UNIT_NOT_MULTIPLE_100, "unit must be a multiple of 100"),

    /** 42209 券不适用 */
    COUPON_NOT_APPLICABLE(LscConstants.E_COUPON_NOT_APPLICABLE, "coupon not applicable"),

    /** 40901 报价过期或版本变化 */
    QUOTE_EXPIRED(LscConstants.E_QUOTE_EXPIRED, "quote expired or version changed"),

    /** 40902 幂等键请求冲突 */
    IDEMPOTENCY_CONFLICT(LscConstants.E_IDEMPOTENCY_CONFLICT, "idempotency key conflict"),

    /** 40903 资源并发不足 */
    RESOURCE_INSUFFICIENT(LscConstants.E_RESOURCE_INSUFFICIENT, "concurrent resource insufficient"),

    /** 42301 权益操作被受控阻断 */
    BENEFIT_BLOCKED(LscConstants.E_BENEFIT_BLOCKED, "benefit operation blocked by risk control"),

    /** 50301 账务服务暂不可用 */
    ACCOUNT_SERVICE_DOWN(LscConstants.E_ACCOUNT_SERVICE_DOWN, "account service temporarily unavailable"),

    // ===== 通用补充 =====
    /** 数据库 CHECK / 唯一约束等被破坏 */
    INVARIANT_VIOLATED(40001, "invariant violated"),

    /** 参数非法 */
    INVALID_ARGUMENT(40000, "invalid argument"),

    /** 未找到资源 */
    NOT_FOUND(40400, "not found"),

    /** 风控冻结/无权限 */
    FORBIDDEN(40300, "forbidden"),

    /** 已支付订单数据异常 */
    PAID_EXCEPTION(50302, "paid but order data exception");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
