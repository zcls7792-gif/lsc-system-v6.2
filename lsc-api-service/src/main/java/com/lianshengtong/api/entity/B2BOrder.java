package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * B2B 交易订单实体（V6.2 更新）
 * 状态：0待确认 1已确认 2已流转 3已完成 4已取消 5已作废
 * AI核验标记：0 AI判定真实 1 AI判定可疑 2人工确认真实 3人工确认虚假
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

    // V6.2 贸易信息
    @Column(name = "trade_description", length = 500) private String tradeDescription;
    @Column(name = "total_amount_rmb") private Double totalAmountRmb;
    @Column(name = "lsc_amount") private Double lscAmount;
    @Column(name = "rmb_amount") private Double rmbAmount;
    @Column(name = "contract_no") private String contractNo;
    @Column(name = "trade_evidence_urls", length = 1000) private String tradeEvidenceUrls;

    // V6.2 AI核验
    @Column(name = "ai_verification_result") private Integer aiVerificationResult;
    @Column(name = "ai_verification_score") private Double aiVerificationScore;

    // V6.2 确认与流转
    @Column(name = "counterparty_confirmed") private Integer counterpartyConfirmed;
    @Column(name = "confirmed_by") private String confirmedBy;
    @Column(name = "confirmed_at") private String confirmedAt;
    @Column(name = "lsc_transferred") private Integer lscTransferred;

    // V6.2 超时控制：7日未确认自动取消
    @Column(name = "expire_at") private String expireAt;

    // V6.2 幂等与版本
    @Column(name = "idempotent_key", unique = true) private String idempotentKey;
    @Column(name = "version") private Integer version;

    // 状态：0待确认 1已确认 2已流转 3已完成 4已取消 5已作废
    private Integer status;
    @Column(name = "status_desc") private String statusDesc;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "completed_at") private String completedAt;
}
