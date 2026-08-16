package com.lianshengtong.common.enums;





public enum AvailableLscStatusEnum {

    VALID(1, "有效"),
    EXPIRED_TRANSFERRED(2, "已过期转回"),
    USED(3, "已使用"),
    WRITTEN_OFF(4, "已核销"),
    REFUND_RETURNED(5, "退款退回");

    private final int code;
    private final String desc;

    AvailableLscStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }



    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
