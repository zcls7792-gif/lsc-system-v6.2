package com.lianshengtong.api.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 释放比例配置表（V6.2 第十四章 14.11）
 * 预置数据：
 *   rate_max = 0.06%  (不可编辑)
 *   rate_min = 0.03%  (不可编辑)
 *   k_min = 0.50%    (可配置)
 *   k_max = 1.0%     (可配置)
 *   alpha = 0.06     (可配置)
 */
@Data
@Entity
@Table(name = "release_config")
public class ReleaseConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "config_key", unique = true) private String configKey;
    @Column(name = "config_value") private String configValue;
    private Integer editable;
    @Column(length = 255) private String description;
    @Column(name = "updated_by") private String updatedBy;
    @Column(name = "updated_at") private String updatedAt;
}
