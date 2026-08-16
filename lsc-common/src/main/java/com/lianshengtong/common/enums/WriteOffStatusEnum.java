package com.lianshengtong.common.enums;





public enum WriteOffStatusEnum {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    SUCCESS(2, "成功"),
    FAILED(3, "失败");

    private final int code;
    private final String desc;

    WriteOffStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }



    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
