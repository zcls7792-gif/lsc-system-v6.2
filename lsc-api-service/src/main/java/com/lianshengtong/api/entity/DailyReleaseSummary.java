package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 每日释放汇总表（V6.2 第十四章 14.10）
 * 记录每日释放计算结果：核销率k、释放速率rate、释放总量等
 */
@Data
@Entity
@Table(name = "daily_release_summary")
public class DailyReleaseSummary {

    @Id
    private Long id;

    @Column(name = "date") private String date;
    @Column(name = "m_total") private Double mTotal;     // 全网14%监管账户余额总和
    @Column(name = "n_total") private Double nTotal;    // 前一日全网核销总额
    @Column(name = "k") private Double k;               // 核销比例 k = N_total / M_total
    @Column(name = "rate") private Double rate;          // 释放速率 (0.03% ~ 0.06%)
    @Column(name = "l_locked") private Long lLocked;    // 全网锁定LSC总量
    @Column(name = "t_release") private Long tRelease;  // 当日释放总量
    @Column(name = "batch_count") private Integer batchCount;
    @Column(name = "failed_batch_count") private Integer failedBatchCount;
    @Column(name = "ai_predicted_k_7d") private Double aiPredictedK7d;
    @Column(name = "ai_predicted_k_30d") private Double aiPredictedK30d;
    /** 0待计算 1计算中 2完成 3异常 */
    private Integer status;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "updated_at") private String updatedAt;
}
