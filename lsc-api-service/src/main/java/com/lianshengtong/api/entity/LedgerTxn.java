package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * LSC 账本流水实体（持久化到 H2/MySQL）
 */
@Data
@Entity
@Table(name = "ledger_txns")
public class LedgerTxn {

    @Id
    private Long id;

    @Column(name = "user_id") private Long userId;
    private String type;
    @Column(name = "type_code") private Integer typeCode;
    private Double amount;
    private Double balance;
    @Column(length = 500) private String remark;
    @Column(name = "created_at") private String createdAt;
}
