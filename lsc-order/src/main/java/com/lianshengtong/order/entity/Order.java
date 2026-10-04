package com.lianshengtong.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {
    @TableId(type = IdType.INPUT)
    private Long orderId;
    private String orderNo;
    private Long userId;
    private String buyerTypeSnapshot;
    private Long sellerEntityId;
    private Long payeeEntityId;
    private Long invoiceEntityId;
    private Long benefitObligorEntityId;
    private String discountMode;  // NONE/LSC/COUPON
    private Long goodsCent;
    private Long shippingCent;
    private Long couponCent;
    private Long lscUnit;
    private Long rmbCent;
    private String paymentStatus;
    private String fulfillmentStatus;
    private String refundStatus;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    private String quoteVersion;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
