package com.lianshengtong.mall.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品发布/更新请求
 * <p>人民币价格与 LSC 价格强制一致，统一使用 price 字段(1:1)。</p>
 */
@Data
public class ProductPublishDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商家用户ID */
    @NotNull(message = "商家ID不能为空")
    private Long merchantId;

    /** 类目ID */
    private Long categoryId;

    /** 商品名称 */
    @NotBlank(message = "商品名称不能为空")
    private String name;

    /** 商品描述 */
    private String description;

    /** 主图URL */
    private String mainImage;

    /** 价格(人民币元 = LSC枚，1:1强制一致) */
    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    private BigDecimal price;

    /** 库存 */
    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负")
    private Integer stock;
}
