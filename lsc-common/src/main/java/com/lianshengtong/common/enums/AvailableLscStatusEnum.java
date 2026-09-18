package com.lianshengtong.common.enums;

/**
 * 可用 LSC 明细状态枚举 (V7.3)
 * <p>
 * V6.2: 1有效 2过期转回 3已使用 4已核销 5退款退回
 * V7.3: 1有效 2已作废 3已使用 4退款退回（移除"已核销"，因 V7.3 禁止核销兑现）
 * </p>
 */
public enum AvailableLscStatusEnum {

    VALID(1, "有效"),
    EXPIRED_WRITEOFF(2, "已作废"),
    USED(3, "已使用"),
    REFUND_RETURNED(4, "退款退回");

    private final int code;
    private final String desc;

    AvailableLscStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
