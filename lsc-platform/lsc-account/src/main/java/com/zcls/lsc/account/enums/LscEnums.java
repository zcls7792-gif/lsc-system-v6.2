package com.zcls.lsc.account.enums;

/** 权益相关枚举集合。 */
public final class LscEnums {

    private LscEnums() {}

    /** GrantLot 状态（6.2）。 */
    public enum GrantLotState {
        ACTIVE,
        /** 风控暂停（不累计额度） */
        PAUSED,
        /** 释放完毕 */
        RELEASED,
        /** 已撤回 */
        REVOKED
    }

    /** AvailableLot 来源类型（6.2）。 */
    public enum OriginType {
        /** 正常释放 */
        GRANT,
        /** 退款返还 */
        REFUND_RESTORE
    }

    /** 处置类别（lsc_entry.disposition_type）。 */
    public enum DispositionType {
        RELEASE,
        CONSUME,
        RETURN,
        EXPIRE,
        REVOKE,
        FREEZE,
        UNFREEZE,
        /** 桶间转移（如占用） */
        TRANSFER
    }

    /** 占用状态（7.1）。 */
    public enum ReservationStatus {
        ACTIVE,
        CAPTURED,
        RELEASED,
        REFUND_RETURNED,
        EXCEPTION
    }

    /** 待追偿状态（7.6）。 */
    public enum RecoveryStatus {
        OPEN,
        PARTIAL,
        CLEARED,
        DISPUTED
    }

    /** 追偿抵充来源类型（7.6）。 */
    public enum SatisfactionType {
        /** 从可用余额扣回 */
        FROM_AVAILABLE,
        /** 从风控冻结可用扣回 */
        FROM_FROZEN,
        /** 由源批次自然到期抵充 */
        FROM_EXPIRY,
        /** 由退款返还批次抵充 */
        FROM_REFUND_RETURN
    }

    /** 冻结状态（10.2）。 */
    public enum FreezeStatus {
        ACTIVE,
        RELEASED,
        REVOKED,
        EXPIRED
    }

    /** 冻结来源桶（6.2）。 */
    public enum FreezeSourceBucket {
        /** 锁定来源冻结 */
        LOCKED,
        /** 可用来源冻结 */
        AVAILABLE
    }
}
