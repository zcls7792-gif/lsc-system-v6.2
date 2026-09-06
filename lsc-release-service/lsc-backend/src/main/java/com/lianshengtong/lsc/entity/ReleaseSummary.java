package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 释放汇总
 */
@Data
@TableName("release_summary")
public class ReleaseSummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 释放日期 */
    private LocalDate releaseDate;

    /** M 总额 */
    private BigDecimal mTotal;

    /** N 总额 */
    private BigDecimal nTotal;

    /** K 值 */
    private BigDecimal kValue;

    /** 释放率 */
    private BigDecimal rate;

    /** 冻结总额 */
    private Long lLocked;

    /** 本次释放总额 */
    private Long tRelease;

    /** 状态 */
    private Integer status;
}
