package com.lianshengtong.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("product_price_version")
public class ProductPriceVersion {
    @TableId(type = IdType.INPUT)
    private Long versionId;
    private Long skuId;
    private Integer priceVersion;
    private Long retailPriceCent;       // C端零售价(分)
    private Long bPriceCent;            // B端采购价(分)
    private String costPriceEnc;        // 成本密文
    private Integer costKeyVersion;
    private String costBasisCode;
    private Long grantCoefficientPpm;   // 赠送系数ppm
    private Long grantCUnit;            // C端赠送基准
    private Long grantBUnit;            // B端赠送基准
    private LocalDateTime effectiveAt;
    private Long approvedBy;
    private LocalDateTime createdAt;
}
