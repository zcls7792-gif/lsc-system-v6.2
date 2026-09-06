package com.lianshengtong.lsc.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(0, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或 Token 失效"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    SERVER_ERROR(500, "服务器内部错误"),

    LSC_INSUFFICIENT(1001, "LSC 余额不足"),
    LSC_FLOW_FORBIDDEN(1002, "LSC 流转权限不足"),
    NH_NOT_QUALIFIED(1003, "核销资格未满足"),
    NH_DAILY_LIMIT_EXCEEDED(1004, "今日核销额度已用完"),
    NH_ALREADY_DONE_TODAY(1005, "今日已核销过"),
    B2B_NOT_CONFIRMED(1006, "B2B 订单未确认"),
    B2B_AI_VERIFY_FAILED(1007, "B2B 贸易背景核验未通过"),
    RATE_OUT_OF_RANGE(1008, "释放速率越界"),
    PRICE_NOT_MATCH(1009, "商品价格校验失败：人民币价必须等于 LSC 价"),
    K_OUT_OF_RANGE(1010, "核销率 k 参数越界"),
    RISK_BLOCKED(2001, "操作被风控拦截");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
