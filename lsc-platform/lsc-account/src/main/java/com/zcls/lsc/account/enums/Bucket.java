package com.zcls.lsc.account.enums;

/**
 * 第6.1/6.2章 余额桶。同一 unit 在任一时刻只能属于一个余额桶。
 * 支付占用从 available 移至 reserved；风控冻结从 locked 或 available 移至同来源 frozen。
 */
public enum Bucket {
    /** 未释放锁定（来自 GrantLot 的 remaining_locked 汇总） */
    LOCKED,
    /** 可用 */
    AVAILABLE,
    /** 支付占用中 */
    RESERVED,
    /** 风控冻结的锁定来源部分 */
    FROZEN_LOCKED,
    /** 风控冻结的可用来源部分 */
    FROZEN_AVAILABLE;

    public static boolean isValid(String v) {
        for (Bucket b : values()) {
            if (b.name().equals(v)) return true;
        }
        return false;
    }
}
