package com.lianshengtong.common.enums;





public enum OrderStatusEnum {

    PENDING_PAY(0, "待支付"),
    PAID(1, "已支付"),
    COMPLETED(2, "已完成"),
    CANCELLED(3, "已取消"),
    REFUNDED(4, "已退款"),
    PARTIAL_REFUNDED(5, "部分退款");

    private final int code;
    private final String desc;

    OrderStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }



    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
