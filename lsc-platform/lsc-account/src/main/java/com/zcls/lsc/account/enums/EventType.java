package com.zcls.lsc.account.enums;

/** 权益事件类型（lsc_event.event_type）。 */
public enum EventType {
    /** 赠送发放 */
    GRANT,
    /** 日释放（锁定 -> 可用） */
    RELEASE,
    /** 支付占用（可用 -> 占用） */
    RESERVE,
    /** 支付核销（占用 -> 已消费） */
    CONSUME,
    /** 取消/超时释放占用（占用 -> 可用） */
    RELEASE_RESERVATION,
    /** 退款返还（按原批次或宽限批次） */
    REFUND_RETURN,
    /** 赠送撤回（退款扣回） */
    GRANT_REVOKE,
    /** 到期作废 */
    EXPIRE,
    /** 风控冻结 */
    FREEZE,
    /** 风控解冻 */
    UNFREEZE,
    /** 待追偿登记 */
    RECOVERY_OPEN,
    /** 待追偿抵充 */
    RECOVERY_SATISFY,
    /** 纠错（引用原事件反向） */
    CORRECTION
}
