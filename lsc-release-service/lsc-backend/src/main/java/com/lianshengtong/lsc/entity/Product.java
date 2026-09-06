package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private String productName;
    private String productDesc;
    private String productImages;
    private BigDecimal price;
    private Long lscPrice;
    private Integer stock;
    private Integer sales;
    private Integer categoryId;
    private String videoUrl;
    private String videoCoverUrl;
    private Integer videoDuration;
    private Integer videoStatus; // 0=待审核,1=审核通过,2=审核拒绝
    private Integer aiReviewResult; // 0=AI通过,1=AI可疑,2=人工通过,3=人工拒绝
    private String aiReviewTags;
    private String videoRejectReason;
    private Integer status; // 0=下架,1=上架,2=审核中
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
