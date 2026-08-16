package com.lianshengtong.b2b.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * B2B 订单作废请求 DTO
 * <p>AI 核验可疑或人工判定虚假贸易后作废订单，冻结后续流转权限。</p>
 */
@Data
public class B2bOrderVoidDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 操作人ID(管理员/风控) */
    @NotNull(message = "操作人ID不能为空")
    private Long operatorId;

    /** 作废原因 */
    private String reason;
}
