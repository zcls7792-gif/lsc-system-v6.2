package com.lianshengtong.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体 (orders 表)
 * <p>
 * 记录线上商城/线下消费订单。支持混合支付(LSC + 人民币)，
 * 退款字段(refund_lsc_amount / refund_rmb_amount)累计已退金额。
 * </p>
 */
@Data
@TableName("orders")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 订单类型 0线上商城 1线下消费 */
    private Integer orderType;

    /** 消费者ID */
    private Long consumerId;

    /** 商家ID */
    private Long merchantId;

    /** 商品ID(线下为0) */
    private Long productId;

    /** 商品名称快照 */
    private String productName;

    /** 购买数量 */
    private Integer quantity;

    /** 订单总价(元) */
    private BigDecimal totalPrice;

    /** LSC支付数量 */
    private Long lscAmount;

    /** 人民币支付金额 */
    private BigDecimal rmbAmount;

    /** 订单状态 0待支付 1已支付 2已完成 3已取消 4已退款 5部分退款 */
    private Integer status;

    /** 已退LSC数量 */
    private Long refundLscAmount;

    /** 已退人民币金额 */
    private BigDecimal refundRmbAmount;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 创建时间(由 MetaObjectHandler 自动填充) */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 更新时间(由 MetaObjectHandler 自动填充) */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
