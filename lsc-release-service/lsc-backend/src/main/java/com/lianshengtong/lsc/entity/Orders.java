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
 * 订单
 */
@Data
@TableName("orders")
public class Orders {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 商家ID */
    private Long merchantId;

    /** 商品ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 订单类型 */
    private Integer orderType;

    /** 总价（人民币） */
    private BigDecimal totalPrice;

    /** LSC 金额 */
    private Long lscAmount;

    /** 人民币金额 */
    private BigDecimal rmbAmount;

    /** 订单状态 */
    private Integer status;

    /** 收货地址ID */
    private Long addressId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
