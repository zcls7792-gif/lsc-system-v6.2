package com.lianshengtong.b2b.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * B2B 订单创建请求 DTO
 */
@Data
public class B2bOrderCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 发起方商家ID */
    @NotNull(message = "发起方ID不能为空")
    private Long initiatorId;

    /** 接收方商家ID */
    @NotNull(message = "接收方ID不能为空")
    private Long counterpartyId;

    /** 交易描述 */
    @NotNull(message = "交易描述不能为空")
    private String tradeDescription;

    /** 交易总金额(元)，须与 LSC 数量 1:1 */
    @NotNull(message = "交易总金额不能为空")
    @Positive(message = "交易总金额必须大于0")
    private BigDecimal totalAmountRmb;

    /** LSC 流转数量(1:1对应总金额) */
    @NotNull(message = "LSC数量不能为空")
    @Positive(message = "LSC数量必须大于0")
    private Long lscAmount;

    /** 合同编号 */
    private String contractNo;

    /** 贸易凭证图片(JSON数组) */
    private String tradeEvidenceUrls;
}
