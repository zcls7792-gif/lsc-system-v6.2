package com.lianshengtong.common.enums;





public enum MerchantPenaltyStatusEnum {

    NORMAL(0, "正常"),
    LEVEL1(1, "一级处罚"),
    LEVEL2(2, "二级处罚"),
    LEVEL3(3, "三级处罚"),
    EXPELLED(4, "四级处罚清退");

    private final int code;
    private final String desc;

    MerchantPenaltyStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static MerchantPenaltyStatusEnum fromCreditScore(int creditScore) {
        if (creditScore >= 80) return NORMAL;
        if (creditScore >= 60) return LEVEL1;
        if (creditScore >= 40) return LEVEL2;
        if (creditScore >= 20) return LEVEL3;
        return EXPELLED;
    }



    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
