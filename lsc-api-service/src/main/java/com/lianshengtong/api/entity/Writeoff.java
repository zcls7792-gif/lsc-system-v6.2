package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 商家核销记录实体（V6.2 核销规则更新版）
 * 核销资金流向：100 LSC → 87元商家主账户 + 3元平台技术服务费 + 10元留存监管账户
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

    // V6.2 核销资金三笔划拨
    @Column(name = "cash_amount") private Double cashAmount;           // 商家回收 = lscAmount * 0.87
    @Column(name = "platform_fee_amount") private Double platformFeeAmount; // 平台技术服务费 = lscAmount * 0.03
    @Column(name = "retained_amount") private Double retainedAmount;    // 留存监管 = lscAmount * 0.10
    @Column(name = "available_before") private Double availableBefore;  // 核销前可用LSC
    @Column(name = "available_after") private Double availableAfter;    // 核销后可用LSC
    @Column(name = "fund_before") private Double fundBefore;            // 商家主账户前余额
    @Column(name = "fund_after") private Double fundAfter;              // 商家主账户后余额

    // V6.2 幂等控制
    @Column(name = "idempotent_key", unique = true) private String idempotentKey;
    @Column(name = "version") private Integer version;

    // 核销状态：0待处理 1处理中 2成功 3失败
    private Integer status;
    @Column(name = "status_desc") private String statusDesc;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "completed_at") private String completedAt;
}
