package com.lianshengtong.common.enums;

/**
 * LSC 流水类型枚举 (V7.3)
 * <p>
 * 按 V7.3 spec 8.3 重定义：
 * 1 消费赠送入锁定 / 2 订单抵扣 / 3 退款退回 / 4 退款回扣 /
 * 5 到期作废 / 6 推荐奖励入锁定 / 7 每日释放 / 8 风控冻结 / 9 风控解冻
 * </p>
 * <p>
 * V6.2 历史 type 值 2/3/4/5/6/7/8/9 已通过迁移脚本一次性映射到 V7.3 值；
 * V6.2 的 5(线下消费)/7(商家核销)/8(B2B流转) 在 V7.3 已废弃，仅作历史数据保留，
 * 新流水不再使用这些值。
 * </p>
 */
public enum LscTransactionTypeEnum {

    GRANT_LOCKED(1, "消费赠送入锁定"),
    ORDER_DEDUCT(2, "订单抵扣"),
    REFUND_RETURN(3, "退款退回"),
    REFUND_DEDUCT(4, "退款回扣"),
    EXPIRE_WRITEOFF(5, "到期作废"),
    PROMOTION_REWARD_LOCKED(6, "推荐奖励入锁定"),
    DAILY_RELEASE(7, "每日释放"),
    RISK_FREEZE(8, "风控冻结"),
    RISK_UNFREEZE(9, "风控解冻");

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
