package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 释放参数配置
 */
@Data
@TableName("release_config")
public class ReleaseConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 释放率上限 */
    private BigDecimal rateMax;

    /** 释放率下限 */
    private BigDecimal rateMin;

    /** K 值下限 */
    private BigDecimal kMin;

    /** K 值上限 */
    private BigDecimal kMax;

    /** 平滑系数 alpha */
    private BigDecimal alpha;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
