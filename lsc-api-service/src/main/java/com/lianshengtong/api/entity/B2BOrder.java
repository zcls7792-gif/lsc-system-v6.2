package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * B2B 订单实体（持久化到 H2/MySQL）
 */
@Data
@Entity
@Table(name = "b2b_orders")
public class B2BOrder {

    @Id
    private Long id;

    @Column(name = "order_no") private String orderNo;
    @Column(name = "initiator_id") private Long initiatorId;
    @Column(name = "initiator_name") private String initiatorName;
    @Column(name = "counterparty_id") private Long counterpartyId;
    @Column(name = "counterparty_name") private String counterpartyName;
    @Column(name = "lsc_amount") private Double lscAmount;
    @Column(name = "rmb_amount") private Double rmbAmount;
    private Integer status;
    @Column(name = "status_desc") private String statusDesc;
    @Column(name = "created_at") private String createdAt;
}
