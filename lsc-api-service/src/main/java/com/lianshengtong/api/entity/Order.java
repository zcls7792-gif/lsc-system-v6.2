package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 订单实体（持久化到 H2/MySQL）
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
    @Column(name = "payment_type") private Integer paymentType;
    private Integer status;
    @Column(name = "status_desc") private String statusDesc;
    @Column(name = "created_at") private String createdAt;
}
