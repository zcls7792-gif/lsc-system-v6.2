package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 释放比例配置表（方案文档14.11）
 * 采用 config_key / config_value 模式
 * rate_max / rate_min 为硬常量，editable=0，不可通过此表修改
 */
@Data
@TableName("release_config")
public class ReleaseConfig {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String configKey;
    private String configValue;
    private Integer editable;
    private String description;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
