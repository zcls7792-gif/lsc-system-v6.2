package com.lianshengtong.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * LSC 事件类型（V7.7.2 第六章 6.1）
 * 所有事件不可修改，纠错通过新增反向事件实现。
 */
@Getter
@AllArgsConstructor
public enum LscEventType {
    GRANT("GRANT", "消费赠送-锁定"),
    DAILY_RELEASE("DAILY_RELEASE", "每日释放-锁定转可用"),
    PAY_RESERVE("PAY_RESERVE", "支付占用-可用转占用"),
    PAY_CAPTURE("PAY_CAPTURE", "支付核销-占用转已消费"),
    PAY_RELEASE("PAY_RELEASE", "支付解占用-占用转可用"),
    REFUND_RESTORE("REFUND_RESTORE", "退款返还-原抵扣返还"),
    GRANT_CLAWBACK("GRANT_CLAWBACK", "赠送撤回-锁定/可用扣减"),
    RECOVERY_SATISFIED("RECOVERY_SATISFIED", "追偿抵扣-待追偿清偿"),
    RECOVERY_OPEN("RECOVERY_OPEN", "追偿挂账-待追偿登记"),
    EXPIRE("EXPIRE", "过期作废-可用转已过期"),
    FREEZE("FREEZE", "风险冻结"),
    UNFREEZE("UNFREEZE", "解除冻结"),
    CORRECTION("CORRECTION", "人工纠错(反向事件)");

    private final String code;
    private final String desc;
}
