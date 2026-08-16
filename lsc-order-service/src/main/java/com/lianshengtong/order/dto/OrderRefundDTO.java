package com.lianshengtong.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 订单退款请求 DTO
 * <p>全额退款时 refundLscAmount/refundRmbAmount 留空；部分退款时填写本次退款金额。</p>
 */
@Data
public class OrderRefundDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 操作人ID(商家) */
    @NotNull(message = "操作人ID不能为空")
    private Long operatorId;

    /** 本次退款LSC数量(部分退款填写，全额退款留空) */
    private Long refundLscAmount;

    /** 本次退款人民币金额(部分退款填写，全额退款留空) */
    private java.math.BigDecimal refundRmbAmount;
}
