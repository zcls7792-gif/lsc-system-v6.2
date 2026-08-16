package com.lianshengtong.release.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 释放配置实体 (release_config)
 * <p>
 * 区分硬常量(editable=0, 编译后不可修改)与可配置参数(editable=1, 修改需双重管理员签名审批+链上存证)。
 * 预置配置：
 * <ul>
 *   <li>rate_max = 0.0005 (0.05%) editable=0 硬常量</li>
 *   <li>rate_min = 0.0003 (0.03%) editable=0 硬常量</li>
 *   <li>k_min = 0.005 (0.50%) editable=1</li>
 *   <li>k_max = 0.01 (1.0%) editable=1</li>
 *   <li>alpha = 0.05 editable=1</li>
 * </ul>
 */
@Data
@TableName("release_config")
public class ReleaseConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 0不可编辑(硬常量) 1可编辑 */
    private Integer editable;

    /** 描述 */
    private String description;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
