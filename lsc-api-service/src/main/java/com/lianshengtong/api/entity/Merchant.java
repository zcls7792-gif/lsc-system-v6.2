package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 商家实体（持久化到 H2/MySQL）
 * 字段名与前端 MockData Map 保持一致，确保 JSON 输出兼容。
 */
@Data
@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    private Long id;

    @Column(name = "user_id") private Long userId;
    @Column(name = "merchant_name") private String merchantName;
    private String name;
    private String contact;
    @Column(name = "store_name") private String storeName;
    private String mobile;
    private String phone;
    @Column(name = "audit_status") private Integer auditStatus;
    private Integer status;
    @Column(name = "credit_score") private Integer creditScore;
    @Column(name = "ai_risk_score") private Integer aiRiskScore;
    @Column(name = "monthly_revenue") private Integer monthlyRevenue;
    @Column(name = "nh_limit_level") private String nhLimitLevel;
    @Column(name = "daily_nh_limit") private Integer dailyNhLimit;
    @Column(name = "penalty_status") private Integer penaltyStatus;
    private String province;
    private String city;
    private String district;
    @Column(name = "address_detail") private String addressDetail;
    @Column(name = "ai_address_verified") private Integer aiAddressVerified;
    private Double longitude;
    private Double latitude;
    @Column(name = "business_hours") private String businessHours;
    @Column(name = "created_at") private String createdAt;
}
