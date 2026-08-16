package com.lianshengtong.common.result;

public enum ResultCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    SYSTEM_ERROR(500, "系统错误"),
    IDEMPOTENT_DUPLICATE(1001, "重复请求"),
    LSC_BALANCE_INSUFFICIENT(2001, "LSC可用余额不足"),
    LSC_LOCKED_INSUFFICIENT(2002, "LSC锁定余额不足"),
    MERCHANT_NOT_QUALIFIED(2003, "商家不具备核销资格"),
    WRITE_OFF_LIMIT_EXCEEDED(2004, "超过当日核销限额"),
    WRITE_OFF_DAILY_LIMIT(2005, "今日已核销过"),
    B2B_ORDER_NOT_FOUND(2006, "B2B订单不存在"),
    B2B_AMOUNT_MISMATCH(2007, "B2B订单金额与LSC数量不匹配(1:1)"),
    B2B_NOT_CONFIRMED(2008, "B2B订单未经对手方确认"),
    B2B_AI_VERIFY_FAIL(2009, "B2B订单AI核验可疑，需人工复核"),
    CONSUMER_TRANSFER_FORBIDDEN(2010, "消费者之间禁止LSC流转"),
    MERCHANT_TO_CONSUMER_FORBIDDEN(2011, "商家向消费者反向流转禁止"),
    REFUND_LSC_INSUFFICIENT(2012, "商家LSC余额不足，无法退回"),
    PRODUCT_PRICE_MISMATCH(2013, "人民币价格与LSC价格不一致"),
    ADDRESS_DAILY_LIMIT(2014, "今日地址修改次数超限"),
    CREDIT_SCORE_TOO_LOW(2015, "商家信用分不足"),
    RELEASE_RATE_OUT_OF_RANGE(3001, "释放比例超出[0.03%, 0.05%]硬约束，任务已终止"),
    SEATA_TRANSACTION_EXCEPTION(4001, "分布式事务异常，需人工处理"),
    RISK_CONTROL_BLOCKED(5001, "风控拦截，请联系客服");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
