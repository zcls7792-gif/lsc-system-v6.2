package com.lianshengtong.b2b.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * B2B 订单取消请求 DTO
 * <p>双方均可发起取消，需校验订单处于可取消状态。</p>
 */
@Data
public class B2bOrderCancelDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 操作人商家ID(发起方或接收方) */
    @NotNull(message = "操作人ID不能为空")
    private Long operatorId;
}
