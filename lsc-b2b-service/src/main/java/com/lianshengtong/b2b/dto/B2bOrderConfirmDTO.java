package com.lianshengtong.b2b.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * B2B 订单确认请求 DTO
 * <p>对手方确认时需校验身份，确认人ID必须等于订单 counterpartyId。</p>
 */
@Data
public class B2bOrderConfirmDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 确认人(对手方)商家ID */
    @NotNull(message = "确认人ID不能为空")
    private Long confirmerId;

    /** 确认人名称(落库 confirmed_by) */
    private String confirmedBy;
}
