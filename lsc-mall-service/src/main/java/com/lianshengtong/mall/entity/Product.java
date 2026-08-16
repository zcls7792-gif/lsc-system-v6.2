package com.lianshengtong.mall.entity;

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
 * 商品实体 (products 表)
 * <p>
 * 人民币价格与 LSC 价格强制一致，共用 {@code price} 字段(单位:元/枚，1:1)。
 * 分库分表：lsc_mall 库，products 表按 merchant_id 取模 32。
 * </p>
 * <ul>
 *   <li>status: 0下架 1上架 2审核中(对应 ProductStatusEnum)</li>
 *   <li>ai_review: 0未审核 1AI通过 2AI可疑 3人工通过 4人工拒绝(对应 AiReviewResultEnum)</li>
 * </ul>
 */
@Data
@TableName("products")
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品ID(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商家用户ID */
    private Long merchantId;

    /** 类目ID */
    private Long categoryId;

    /** 商品名称 */
    private String name;

    /** 商品描述 */
    private String description;

    /** 主图URL */
    private String mainImage;

    /** 价格(人民币元 = LSC枚，1:1 强制一致) */
    private BigDecimal price;

    /** 库存 */
    private Integer stock;

    /** 状态 0下架 1上架 2审核中 */
    private Integer status;

    /** AI审核结果 0未审核 1AI通过 2AI可疑 3人工通过 4人工拒绝 */
    private Integer aiReview;

    /** AI审核备注 */
    private String aiReviewRemark;

    /** 销量 */
    private Long salesCount;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
