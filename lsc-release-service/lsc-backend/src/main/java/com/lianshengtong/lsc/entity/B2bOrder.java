package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * B2B 交易订单
 */
@Data
@TableName("b2b_order")
public class B2bOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 付款商家ID */
    private Long fromMerchantId;

    /** 收款商家ID */
    private Long toMerchantId;

    /** 交易描述 */
    private String tradeDescription;

    /** 人民币总金额 */
    private BigDecimal totalAmountRmb;

    /** LSC 金额 */
    private Long lscAmount;

    /** 合同编号 */
    private String contractNo;

    /** 交易凭证URL（JSON 数组） */
    private String tradeEvidenceUrls;

    /** AI 核验结果 */
    private Integer aiVerificationResult;

    /** 状态 */
    private Integer status;

    /** 确认人 */
    private String confirmedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
