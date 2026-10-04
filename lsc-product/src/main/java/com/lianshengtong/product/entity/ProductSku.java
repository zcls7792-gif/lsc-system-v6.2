package com.lianshengtong.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("product_sku")
public class ProductSku {
    @TableId(type = IdType.INPUT)
    private Long skuId;
    private Long productId;
    private String skuCode;
    private String specJson;
    private String saleUnit;
    private Integer packQty;
    private Integer bMinQty;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
