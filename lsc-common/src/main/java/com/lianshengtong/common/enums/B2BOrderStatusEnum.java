package com.lianshengtong.common.enums;





public enum B2BOrderStatusEnum {

    PENDING_CONFIRM(0, "待确认"),
    CONFIRMED(1, "已确认"),
    TRANSFERRED(2, "已流转"),
    COMPLETED(3, "已完成"),
    CANCELLED(4, "已取消"),
    VOIDED(5, "已作废");

    private final int code;
    private final String desc;

    B2BOrderStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }



    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
