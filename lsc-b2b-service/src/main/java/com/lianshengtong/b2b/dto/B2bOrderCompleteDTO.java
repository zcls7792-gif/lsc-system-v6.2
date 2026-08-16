package com.lianshengtong.b2b.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class B2bOrderCompleteDTO {

    @NotBlank(message = "订单号不能为空")
    @Size(max = 64, message = "订单号长度不能超过64位")
    private String orderNo;

    @NotNull(message = "操作人ID不能为空")
    private Long operatorId;
}
