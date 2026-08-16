package com.lianshengtong.release.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日释放汇总实体 (daily_release_summary)
 * <p>
 * 记录每日释放计算的关键输入/输出与执行状态，作为审计与账务比对的依据。
 */
@Data
@TableName("daily_release_summary")
public class DailyReleaseSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 日期 */
    @TableField("`date`")
    private LocalDate date;

    /** 监管账户总余额 M_total */
    private BigDecimal mTotal;

    /** 全网核销总额 N_total */
    private BigDecimal nTotal;

    /** 核销率 k = N_total / M_total */
    private BigDecimal k;

    /** 释放速率 rate */
    private BigDecimal rate;

    /** 全网锁定LSC总量 L_locked */
    private Long lLocked;

    /** 当日释放总量 T_release */
    private Long tRelease;

    /** 总批次数 */
    private Integer batchCount;

    /** 失败批次数 */
    private Integer failedBatchCount;

    /** 实际已释放量(汇总校验用) */
    private Long actualReleased;

    /** AI预测7天核销率均值 */
    private BigDecimal aiPredictedK7d;

    /** AI预测30天核销率均值 */
    private BigDecimal aiPredictedK30d;

    /** 状态 0待执行 1执行中 2成功 3失败 */
    private Integer status;

    /** 失败/阻断原因 */
    private String failReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
