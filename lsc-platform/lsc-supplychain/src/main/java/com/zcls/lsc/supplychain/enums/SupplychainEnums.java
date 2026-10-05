package com.zcls.lsc.supplychain.enums;

/** 供应链枚举（第9章）。 */
public final class SupplychainEnums {

    private SupplychainEnums() {}

    /** 供应商状态。 */
    public enum SupplierStatus { ACTIVE, SUSPENDED, BLACKLISTED }

    /** 采购单状态。 */
    public enum PurchaseStatus {
        PENDING, APPROVED, PARTIAL_RECEIVED, RECEIVED, INSPECTED, SETTLED, CANCELED
    }

    /** 收货状态。 */
    public enum ReceiptStatus { RECEIVED, INSPECTED, PUTAWAY, CANCELED }

    /** 质检结果。 */
    public enum InspectionResult { PASS, REJECT, PARTIAL }

    /** 库存流水类型。 */
    public enum MovementType {
        /** 入库 */
        IN,
        /** 出库 */
        OUT,
        /** 预占 */
        RESERVE,
        /** 预占转实扣(支付成功) */
        CAPTURE,
        /** 释放预占 */
        RELEASE,
        /** 质检冻结 */
        BLOCK,
        /** 解除冻结 */
        UNBLOCK
    }

    /** 发货状态。 */
    public enum ShipmentStatus { PENDING, SHIPPED, DELIVERED, CANCELED }

    /** 结算状态。 */
    public enum SettlementStatus { PENDING, APPROVED, PAID, CANCELED }

    /** 供应商付款状态。 */
    public enum PaymentStatus { PENDING, PROCESSING, PAID, FAILED }
}
