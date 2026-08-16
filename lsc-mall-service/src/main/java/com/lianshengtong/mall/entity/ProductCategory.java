package com.lianshengtong.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品类目实体 (product_categories 表)
 */
@Data
@TableName("product_categories")
public class ProductCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 类目ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 类目名称 */
    private String name;

    /** 父类目ID(0为一级类目) */
    private Long parentId;

    /** 排序 */
    private Integer sort;

    /** 状态 0禁用 1启用 */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
