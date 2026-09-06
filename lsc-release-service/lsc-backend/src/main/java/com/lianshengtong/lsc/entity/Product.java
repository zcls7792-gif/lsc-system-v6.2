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
 * 商品
 */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商家ID */
    private Long merchantId;

    /** 商品名称 */
    private String productName;

    /** 商品描述 */
    private String productDesc;

    /** 人民币价格 */
    private BigDecimal price;

    /** LSC 计价 */
    private Long lscPrice;

    /** 库存 */
    private Integer stock;

    /** 销量 */
    private Integer sales;

    /** 商品图片（JSON 数组） */
    private String productImages;

    /** AI 审核结果 */
    private Integer aiReviewResult;

    /** AI 标签（JSON 数组） */
    private String aiTags;

    /** 商品状态 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
