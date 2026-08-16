package com.lianshengtong.mall.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 混合支付计算请求
 * <p>LSC 占用 0~总价，人民币补足，1:1。</p>
 */
@Data
public class HybridPayCalcDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单总价(元) */
    @NotNull(message = "总价不能为空")
    @DecimalMin(value = "0.01", message = "总价必须大于0")
    private BigDecimal totalPrice;

    /** 期望使用的LSC数量(0~totalPrice) */
    @NotNull(message = "LSC数量不能为空")
    @Min(value = 0, message = "LSC数量不能为负")
    private Long lscAmount;

    /** LSC最大可用数量(用户可用余额，可选上限校验) */
    private Long maxAvailableLsc;
}
