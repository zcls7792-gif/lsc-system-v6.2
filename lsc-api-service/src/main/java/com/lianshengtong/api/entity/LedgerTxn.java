package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * LSC 流水表实体（V6.2 第十四章 14.4）
 *
 * LSC流水类型枚举（type）：
 * 1 消费发行
 * 2 每日释放
 * 3 推广奖励释放
 * 4 权益商城消费
 * 5 线下消费
 * 6 过期转回
 * 7 商家核销
 * 8 B2B流转支付
 * 9 退款发行回滚
 */
@Data
@Entity
@Table(name = "lsc_transactions")
public class LedgerTxn {

    @Id
    private Long id;

    @Column(name = "user_id") private Long userId;
    /** 流水类型：1-9 见类注释 */
    @Column(name = "type") private Integer type;
    /** 兼容字段（保留旧版 type 字符串） */
    @Column(name = "type_str") private String typeStr;
    /** 流水金额（bigint） */
    private Long amount;
    /** 操作前锁定池余额 */
    @Column(name = "before_locked") private Long beforeLocked;
    /** 操作后锁定池余额 */
    @Column(name = "after_locked") private Long afterLocked;
    /** 操作前可用池余额 */
    @Column(name = "before_available") private Long beforeAvailable;
    /** 操作后可用池余额 */
    @Column(name = "after_available") private Long afterAvailable;
    /** 交易对手方 userId */
    @Column(name = "counterparty_id") private Long counterpartyId;
    /** 关联订单号 */
    @Column(name = "order_no") private String orderNo;
    /** 幂等键（唯一） */
    @Column(name = "idempotent_key", unique = true) private String idempotentKey;
    /** 兼容字段：旧版余额快照 */
    private Double balance;
    /** 备注 */
    @Column(length = 500) private String remark;
    /** 哈希存证（SHA-256，V6.2 第十五章） */
    @Column(name = "evidence_hash", length = 64) private String evidenceHash;
    @Column(name = "created_at") private String createdAt;
}
