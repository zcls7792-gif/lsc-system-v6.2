package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 推广奖励挂账表（V6.2 第八章 8.4）
 * 推荐人锁定余额不足时，未发放部分记入挂账表
 * 每日定时扫描，推荐人锁定余额新增后自动补发
 */
@Data
@Entity
@Table(name = "promotion_pending")
public class PromotionPending {

    @Id
    private Long id;

    /** 推荐人 userId */
    @Column(name = "referrer_id") private Long referrerId;
    /** 被推荐人 userId */
    @Column(name = "referred_id") private Long referredId;
    /** 关联首单订单号 */
    @Column(name = "first_order_no") private String firstOrderNo;
    /** 应发奖励数量（首单消费金额×10%） */
    @Column(name = "pending_amount") private Long pendingAmount;
    /** 已补发数量 */
    @Column(name = "paid_amount") private Long paidAmount;
    /** 状态：0待补发 1已补发 2部分补发 */
    private Integer status;
    @Column(name = "created_at") private String createdAt;
    @Column(name = "updated_at") private String updatedAt;
}
