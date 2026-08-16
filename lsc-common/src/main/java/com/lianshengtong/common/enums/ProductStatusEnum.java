package com.lianshengtong.common.enums;





public enum ProductStatusEnum {

    OFF_SHELF(0, "下架"),
    ON_SHELF(1, "上架"),
    UNDER_REVIEW(2, "审核中");

    private final int code;
    private final String desc;

    ProductStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }



    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
