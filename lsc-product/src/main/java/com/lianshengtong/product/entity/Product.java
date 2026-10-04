package com.lianshengtong.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.INPUT)
    private Long productId;
    private Long sellerEntityId;
    private String name;
    private Long categoryId;
    private String status;  // DRAFT/REVIEWING/ON_SALE/OFF_SALE/SOLD_OUT
    private Integer auditVersion;
    private String descriptionRef;
    private String returnPolicyVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
