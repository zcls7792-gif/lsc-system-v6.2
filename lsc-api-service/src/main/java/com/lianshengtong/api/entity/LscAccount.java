package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * LSC 账户实体（V6.2 第十四章 14.3）
 * 记录用户/商家的 LSC 锁定池和可用池余额
 */
@Data
@Entity
@Table(name = "lsc_accounts")
public class LscAccount {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "total_locked") private Long totalLocked;
    @Column(name = "total_available") private Long totalAvailable;
    @Version
    private Integer version;
    @Column(name = "updated_at") private String updatedAt;
}
