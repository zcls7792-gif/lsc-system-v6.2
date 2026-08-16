package com.lianshengtong.common.enums;





public enum UserTypeEnum {

    CONSUMER(0, "消费者会员"),
    MERCHANT(1, "商家会员");

    private final int code;
    private final String desc;

    UserTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static UserTypeEnum of(int code) {
        for (UserTypeEnum e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }



    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
