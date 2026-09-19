package com.lianshengtong.mall.service.impl;

import com.lianshengtong.common.dto.HybridPayDTO;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.mall.constant.MallConstants;
import com.lianshengtong.mall.dto.HybridPayCalcDTO;
import com.lianshengtong.mall.service.HybridPayService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 混合支付计算实现 (V7.3)
 * <p>
 * V7.3 合规基线：
 * <ul>
 *   <li>LSC 抵扣上限 = 订单总价的 50%（{@link MallConstants#MAX_LSC_DEDUCT_RATIO}）</li>
 *   <li>混合支付时，仅人民币支付部分按比例赠送 LSC（LSC 抵扣部分不赠送）</li>
 * </ul>
 * </p>
 */
@Service
public class HybridPayServiceImpl implements HybridPayService {

    @Override
    public HybridPayDTO calc(HybridPayCalcDTO dto) {
        BigDecimal totalPrice = dto.getTotalPrice();
        long reqLsc = dto.getLscAmount();
        if (reqLsc < 0) {
            throw new BizException("LSC数量不能为负");
        }
        // 负数或零总价直接返回0
        if (totalPrice.signum() <= 0) {
            return HybridPayDTO.builder()
                    .lscAmount(0L)
                    .rmbAmount(new BigDecimal("0.00"))
                    .totalPrice(totalPrice)
                    .estimatedGrantLsc(0L)
                    .build();
        }

        // V7.3 合规基线：LSC 抵扣上限 = 总价 × 50%
        long maxLscByRatio = totalPrice.multiply(MallConstants.MAX_LSC_DEDUCT_RATIO)
                .setScale(0, RoundingMode.FLOOR).longValue();
        // LSC 不超过总价(1:1，1 LSC = 1 元) —— 双保险
        long maxLscByPrice = totalPrice.setScale(0, RoundingMode.DOWN).longValue();
        long lscAmount = Math.min(reqLsc, Math.min(maxLscByRatio, maxLscByPrice));
        // 受可用余额上限约束
        if (dto.getMaxAvailableLsc() != null) {
            lscAmount = Math.min(lscAmount, dto.getMaxAvailableLsc());
        }
        if (lscAmount < 0) {
            lscAmount = 0;
        }
        // 人民币补足 = 总价 - LSC(1:1)
        BigDecimal rmbAmount = totalPrice.subtract(BigDecimal.valueOf(lscAmount))
                .setScale(2, RoundingMode.HALF_UP);
        if (rmbAmount.signum() < 0) {
            rmbAmount = new BigDecimal("0.00");
        }

        // V7.3: 预计赠送 LSC = 商品赠送积分 × (人民币支付金额 / 总价)
        // 混合支付时仅人民币部分按比例赠送，LSC 抵扣部分不赠送
        long estimatedGrant = 0L;
        if (dto.getGrantPoints() != null && dto.getGrantPoints() > 0 && totalPrice.signum() > 0) {
            estimatedGrant = BigDecimal.valueOf(dto.getGrantPoints())
                    .multiply(rmbAmount)
                    .divide(totalPrice, 0, RoundingMode.FLOOR)
                    .longValue();
            if (estimatedGrant < 0) {
                estimatedGrant = 0L;
            }
        }

        return HybridPayDTO.builder()
                .lscAmount(lscAmount)
                .rmbAmount(rmbAmount)
                .totalPrice(totalPrice)
                .estimatedGrantLsc(estimatedGrant)
                .build();
    }
}
