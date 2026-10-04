package com.lianshengtong.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * V7.7.2 错误码（第十三章 13.2）
 */
@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(0, "success"),

    // 422xx 业务校验
    COUPON_LSC_MUTEX(42206, "券与LSC互斥，每单最多使用一项"),
    DEDUCTION_EXCEED_LIMIT(42207, "抵扣超过上限"),
    UNIT_NOT_MULTIPLE_OF_100(42208, "抵扣unit必须为100的整数倍"),
    COUPON_NOT_APPLICABLE(42209, "券不适用"),

    // 409xx 并发冲突
    QUOTE_EXPIRED(40901, "报价过期或版本变化"),
    IDEMPOTENT_CONFLICT(40902, "幂等键请求冲突"),
    IDEMPOTENT_DUPLICATE(40904, "重复请求"),
    RESOURCE_INSUFFICIENT(40903, "资源并发不足"),

    // 423xx 受控阻断
    LSC_OPERATION_BLOCKED(42301, "权益操作被受控阻断"),

    // 503xx 服务不可用
    LEDGER_SERVICE_UNAVAILABLE(50301, "账务服务暂不可用"),

    // 通用
    PARAM_INVALID(400, "参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    LSC_BALANCE_INSUFFICIENT(4001, "LSC可用余额不足"),
    LSC_LOCKED_INSUFFICIENT(4002, "LSC锁定余额不足"),
    SYSTEM_ERROR(5000, "系统错误"),
    TOO_MANY_REQUESTS(4290, "请求过于频繁");

    private final int code;
    private final String message;
}
