package com.lianshengtong.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 订单支付请求 DTO
 */
@Data
public class OrderPayDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 消费者ID(支付方) */
    @NotNull(message = "消费者ID不能为空")
    private Long consumerId;
}
