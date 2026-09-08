package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 风控日志实体（持久化到 H2/MySQL）
 */
@Data
@Entity
@Table(name = "risk_logs")
public class RiskLog {

    @Id
    private Long id;

    @Column(name = "merchant_id") private Long merchantId;
    @Column(name = "merchant_name") private String merchantName;
    private String level;
    @Column(name = "level_code") private Integer levelCode;
    @Column(length = 1000) private String content;
    @Column(name = "created_at") private String createdAt;
}
