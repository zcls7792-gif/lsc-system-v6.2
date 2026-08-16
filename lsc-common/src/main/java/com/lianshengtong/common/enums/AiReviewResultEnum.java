package com.lianshengtong.common.enums;





public enum AiReviewResultEnum {

    NOT_REVIEWED(0, "未审核"),
    AI_PASS(1, "AI通过"),
    AI_SUSPICIOUS(2, "AI可疑"),
    MANUAL_PASS(3, "人工通过"),
    MANUAL_REJECT(4, "人工拒绝");

    private final int code;
    private final String desc;

    AiReviewResultEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }



    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
