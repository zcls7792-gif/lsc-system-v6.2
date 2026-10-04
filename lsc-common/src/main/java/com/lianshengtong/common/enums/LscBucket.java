package com.lianshengtong.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * LSC 余额桶（V7.7.2 第六章 6.2）
 * 同一 unit 任一时刻只能属于一个桶。
 */
@Getter
@AllArgsConstructor
public enum LscBucket {
    LOCKED("LOCKED", "锁定"),
    AVAILABLE("AVAILABLE", "可用"),
    RESERVED("RESERVED", "支付占用"),
    FROZEN_LOCKED("FROZEN_LOCKED", "冻结锁定"),
    FROZEN_AVAILABLE("FROZEN_AVAILABLE", "冻结可用"),
    CONSUMED("CONSUMED", "已消费"),
    EXPIRED("EXPIRED", "已过期"),
    REVOKED("REVOKED", "已撤回"),
    PENDING_RECOVERY("PENDING_RECOVERY", "待追偿");

    private final String code;
    private final String desc;
}
