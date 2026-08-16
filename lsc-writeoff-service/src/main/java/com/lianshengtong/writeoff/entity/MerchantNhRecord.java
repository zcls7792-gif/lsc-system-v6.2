package com.lianshengtong.writeoff.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家核销记录实体 (merchant_nh_records 表)
 * <p>
 * order_no 与 idempotent_key 上均有唯一索引防重，version 字段使用 {@link Version}
 * 实现乐观锁。记录每次核销前后的可用余额与监管账户余额快照，便于审计对账。
 * </p>
 */
@Data
@TableName("merchant_nh_records")
public class MerchantNhRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商家ID */
    private Long merchantId;

    /** 核销LSC数量 */
    private Long lscAmount;

    /** 划拨现金金额 */
    private BigDecimal cashAmount;

    /** 核销前可用余额 */
    private Long availableBefore;

    /** 核销后可用余额 */
    private Long availableAfter;

    /** 核销前监管账户余额 */
    private BigDecimal fundBefore;

    /** 核销后监管账户余额 */
    private BigDecimal fundAfter;

    /** 核销订单号(唯一索引) */
    private String orderNo;

    /** 幂等键(唯一索引) */
    private String idempotentKey;

    /** 乐观锁版本号，更新失败抛出异常触发重试 */
    @Version
    private Integer version;

    /** 状态 0待处理 1处理中 2成功 3失败 */
    private Integer status;

    /** 失败原因 */
    private String failReason;

    /** 创建时间(由 MetaObjectHandler 自动填充) */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 更新时间(由 MetaObjectHandler 自动填充) */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
