package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 风控日志实体（V6.2 第十一章 用户行为风控体系）
 * 状态：0待处理 1已处理 2申诉中 3已关闭
 */
@Data
@Entity
@Table(name = "risk_logs")
public class RiskLog {

    @Id
    private Long id;

    @Column(name = "user_id") private Long userId;
    @Column(name = "merchant_id") private Long merchantId;
    @Column(name = "merchant_name") private String merchantName;
    private String level;
    @Column(name = "level_code") private Integer levelCode;
    private String type;
    @Column(length = 1000) private String remark;
    /** 状态：0待处理 1已处理 2申诉中 3已关闭 */
    private Integer status;
    @Column(length = 1000) private String content;
    @Column(name = "created_at") private String createdAt;
}
