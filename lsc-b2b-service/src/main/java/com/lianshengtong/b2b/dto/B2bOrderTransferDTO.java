package com.lianshengtong.b2b.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * B2B 订单流转执行请求 DTO
 * <p>仅发起方可执行流转，需校验订单已确认且 AI 核验通过。</p>
 */
@Data
public class B2bOrderTransferDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 操作人(发起方)商家ID */
    @NotNull(message = "操作人ID不能为空")
    private Long operatorId;
}
