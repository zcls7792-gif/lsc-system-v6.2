package com.lianshengtong.common.enums;





public enum LscTransactionTypeEnum {

    CONSUMPTION_ISSUE(1, "消费发行"),
    DAILY_RELEASE(2, "每日释放"),
    PROMOTION_REWARD(3, "推广奖励释放"),
    MALL_CONSUMPTION(4, "权益商城消费"),
    OFFLINE_CONSUMPTION(5, "线下消费"),
    EXPIRE_TRANSFER(6, "过期转回"),
    MERCHANT_WRITE_OFF(7, "商家核销"),
    B2B_TRANSFER(8, "B2B流转支付"),
    REFUND_RETURN(9, "退款退回");

    private final int code;
    private final String desc;

    LscTransactionTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static LscTransactionTypeEnum of(int code) {
        for (LscTransactionTypeEnum e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }



    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
