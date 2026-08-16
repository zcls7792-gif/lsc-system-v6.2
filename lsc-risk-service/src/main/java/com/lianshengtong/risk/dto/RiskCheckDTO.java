package com.lianshengtong.risk.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 风控检测请求
 */
@Data
public class RiskCheckDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 商品ID(套利检测) */
    private Long productId;

    /** 订单号 */
    private String orderNo;

    /** 订单金额 */
    private BigDecimal orderAmount;

    /** LSC支付数量 */
    private Long lscAmount;

    /** 人民币支付金额 */
    private BigDecimal rmbAmount;

    /** 客户端IP */
    private String clientIp;

    /** IP归属城市 */
    private String clientCity;

    /** 是否启用AI动态风控 */
    private Boolean enableAi;
}
