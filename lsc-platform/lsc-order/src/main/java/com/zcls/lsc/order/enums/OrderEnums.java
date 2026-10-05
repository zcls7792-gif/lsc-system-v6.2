package com.zcls.lsc.order.enums;

/** 订单相关枚举（第3.4章）。 */
public final class OrderEnums {

    private OrderEnums() {}

    /** 下单身份快照。 */
    public enum BuyerType { C, B }

    /** 抵扣模式（每单最多一张券，券与LSC互斥）。 */
    public enum DiscountMode { NONE, LSC, COUPON }

    /** 支付状态。 */
    public enum PaymentStatus {
        UNPAID, PAYING, PAID, REFUNDING, PART_REFUNDED, REFUNDED, EXCEPTION,
        /** 已收到渠道成功付款但订单数据异常（第7.2章） */
        PAID_EXCEPTION
    }

    /** 履约状态。 */
    public enum FulfillmentStatus {
        CREATED, CONFIRMED, SHIPPED, COMPLETED, CANCELED, CLOSED
    }

    /** 退款状态。 */
    public enum RefundStatus {
        NONE, REQUESTED, REVIEWING, PROCESSING, PART_REFUNDED, REFUNDED, CLOSED
    }

    /** 退款类型（第7.5章）。 */
    public enum RefundKind {
        /** 整件退货 */
        QTY,
        /** 价差退款 */
        PRICE_DIFF
    }

    /** 退款单状态。 */
    public enum RefundOrderStatus {
        REQUESTED, REVIEWING, APPROVED, CHANNEL_PENDING, SUCCEEDED, FAILED, CLOSED
    }

    /** 退款权益后处理状态。 */
    public enum BenefitStatus {
        PENDING, PROCESSING, DONE, EXCEPTION
    }
}
