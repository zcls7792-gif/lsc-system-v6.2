package com.lianshengtong.writeoff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 核销申请请求 DTO
 */
@Data
public class WriteOffApplyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商家ID */
    @NotNull(message = "商家ID不能为空")
    private Long merchantId;

    /** 核销LSC数量 */
    @NotNull(message = "核销数量不能为空")
    @Positive(message = "核销数量必须大于0")
    private Long lscAmount;
}
