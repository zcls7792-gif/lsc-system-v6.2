package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 存证记录实体（持久化到 H2/MySQL）
 */
@Data
@Entity
@Table(name = "evidence_records")
public class Evidence {

    @Id
    private Long id;

    @Column(name = "merchant_id") private Long merchantId;
    @Column(name = "merchant_name") private String merchantName;
    @Column(name = "evidence_hash", length = 200) private String evidenceHash;
    @Column(name = "block_height") private Long blockHeight;
    @Column(name = "tx_id") private String txId;
    private Integer status;
    @Column(name = "created_at") private String createdAt;
}
