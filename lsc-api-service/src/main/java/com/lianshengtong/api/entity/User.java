package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 用户表实体（V6.2 第十四章 14.1）
 * 平台注册用户统称为平台用户，分为消费者会员(0)和商家会员(1)
 */
@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** 用户类型：0消费者会员 1商家会员 */
    @Column(name = "user_type") private Integer userType;
    /** 手机号（唯一） */
    @Column(unique = true) private String mobile;
    /** 是否已实名认证 */
    @Column(name = "is_verified") private Integer isVerified;
    /** 推荐人ID（一级直推） */
    @Column(name = "referrer_id") private Long referrerId;
    /** 首单是否已完成 */
    @Column(name = "first_order_completed") private Integer firstOrderCompleted;
    /** 用户名 */
    @Column(name = "user_name") private String userName;
    /** 创建时间 */
    @Column(name = "created_at") private String createdAt;
}
