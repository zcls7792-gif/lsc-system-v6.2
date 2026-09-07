package com.lianshengtong.api.entity;

import com.lianshengtong.api.config.StringListConverter;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.List;

/**
 * 商品实体（持久化到 H2/MySQL）
 * 图片列表字段用 StringListConverter 存为逗号分隔字符串。
 */
@Data
@Entity
@Table(name = "products")
public class Product {

    @Id
    private Long id;

    @Column(name = "merchant_id") private Long merchantId;
    @Column(name = "merchant_name") private String merchantName;
    @Column(name = "product_name") private String productName;
    private String name;
    @Column(name = "product_desc", length = 2000) private String productDesc;
    @Column(length = 2000) private String description;

    @Convert(converter = StringListConverter.class)
    @Column(name = "product_images", length = 4000) private List<String> productImages;

    @Column(length = 1000) private String cover;

    @Convert(converter = StringListConverter.class)
    @Column(length = 4000) private List<String> images;

    private Double price;
    @Column(name = "lsc_price") private Double lscPrice;
    private Integer stock;
    private Integer sales;
    @Column(name = "category_id") private Integer categoryId;
    private Integer status;
    @Column(name = "ai_review_result") private Integer aiReviewResult;
    @Column(name = "created_at") private String createdAt;
}
