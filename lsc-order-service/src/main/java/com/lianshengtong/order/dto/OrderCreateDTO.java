package com.lianshengtong.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单创建请求 DTO
 * <p>支持线上商城(0)/线下消费(1)，混合支付(LSC + 人民币)。</p>
 */
@Data
public class OrderCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单类型 0线上商城 1线下消费 */
    @NotNull(message = "订单类型不能为空")
    private Integer orderType;

    /** 消费者ID */
    @NotNull(message = "消费者ID不能为空")
    private Long consumerId;

    /** 商家ID */
    @NotNull(message = "商家ID不能为空")
    private Long merchantId;

    /** 商品ID(线下为0) */
    private Long productId;

    /** 商品名称快照 */
    private String productName;

    /** 购买数量 */
    private Integer quantity;

    /** 订单总价(元) */
    @NotNull(message = "订单总价不能为空")
    @Positive(message = "订单总价必须大于0")
    private BigDecimal totalPrice;

    /** LSC支付数量(1:1对应人民币元，可空表示全部用人民币) */
    private Long lscAmount;
}
