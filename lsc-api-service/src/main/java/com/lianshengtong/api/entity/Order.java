package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 订单实体（V6.2 退款规则更新）
 * 退款规则：首单不退，LSC订单不退，仅纯人民币非首单可退
 */
@Data
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private Long id;

    @Column(name = "order_no") private String orderNo;
    @Column(name = "user_id") private Long userId;
    @Column(name = "user_name") private String userName;
    @Column(name = "merchant_id") private Long merchantId;
    @Column(name = "merchant_name") private String merchantName;
    @Column(name = "product_id") private Long productId;
    @Column(name = "product_name") private String productName;
    @Column(name = "product_image", length = 1000) private String productImage;
    private Integer quantity;
    private Double price;
    @Column(name = "total_amount") private Double totalAmount;
    @Column(name = "lsc_amount") private Double lscAmount;
    @Column(name = "rmb_amount") private Double rmbAmount;

    // V6.2 订单类型：0纯人民币 1 LSC全额抵扣 2混合支付
    @Column(name = "order_type") private Integer orderType;
    @Column(name = "payment_type") private Integer paymentType;
    // V6.2 首单标记：0非首单 1首单
    @Column(name = "is_first_order") private Integer isFirstOrder;

    // 订单状态：0待支付 1已支付 2已完成 3已取消 4已退款 5部分退款
    private Integer status;
    @Column(name = "status_desc") private String statusDesc;

    // V6.2 退款金额
    @Column(name = "refund_lsc_amount") private Double refundLscAmount;
    @Column(name = "refund_rmb_amount") private Double refundRmbAmount;

    @Column(name = "created_at") private String createdAt;
    @Column(name = "completed_at") private String completedAt;
}
