package com.lianshengtong.mall.service.impl;

import com.lianshengtong.common.dto.HybridPayDTO;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.mall.dto.HybridPayCalcDTO;
import com.lianshengtong.mall.service.HybridPayService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 混合支付计算实现
 * <p>LSC 占用 0~总价，人民币补足，1:1。</p>
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
        // LSC 不超过总价(1:1，1 LSC = 1 元)
        long maxLscByPrice = totalPrice.setScale(0, RoundingMode.DOWN).longValue();
        long lscAmount = Math.min(reqLsc, maxLscByPrice);
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
            rmbAmount = BigDecimal.ZERO;
        }
        return HybridPayDTO.builder()
                .lscAmount(lscAmount)
                .rmbAmount(rmbAmount)
                .totalPrice(totalPrice)
                .build();
    }
}
