package com.lianshengtong.reconciliation.entity;

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
 * 对账报告实体 (reconcile_reports 表)
 * <p>分库分表：lsc_reconciliation 库，按 date 分表。</p>
 */
@Data
@TableName("reconcile_reports")
public class ReconcileReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 对账日期 */
    private LocalDate reconcileDate;

    /** 支付机构流水总额 */
    private BigDecimal paymentTotalAmount;

    /** 支付机构流水笔数 */
    private Long paymentCount;

    /** LSC账本流水总额 */
    private BigDecimal ledgerTotalAmount;

    /** LSC账本流水笔数 */
    private Long ledgerCount;

    /** 差异金额 */
    private BigDecimal diffAmount;

    /** 差异笔数 */
    private Long diffCount;

    /** 对账状态 0进行中 1一致 2有差异 3失败 */
    private Integer status;

    /** 差异明细(JSON) */
    private String diffDetail;

    /** 结果哈希(上链存证) */
    private String resultHash;

    /** 上链交易哈希 */
    private String chainTxHash;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
