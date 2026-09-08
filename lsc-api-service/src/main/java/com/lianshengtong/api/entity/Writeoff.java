package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 核销记录实体（持久化到 H2/MySQL）
 */
@Data
@Entity
@Table(name = "writeoffs")
public class Writeoff {

    @Id
    private Long id;

    @Column(name = "order_no") private String orderNo;
    @Column(name = "merchant_id") private Long merchantId;
    @Column(name = "merchant_name") private String merchantName;
    @Column(name = "lsc_amount") private Double lscAmount;
    private Integer status;
    @Column(name = "status_desc") private String statusDesc;
    @Column(name = "created_at") private String createdAt;
}
