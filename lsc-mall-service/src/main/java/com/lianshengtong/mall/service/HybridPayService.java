package com.lianshengtong.mall.service;

import com.lianshengtong.common.dto.HybridPayDTO;
import com.lianshengtong.mall.dto.HybridPayCalcDTO;

/**
 * 混合支付计算服务
 * <p>LSC 占用 0~总价，人民币补足，1:1。</p>
 */
public interface HybridPayService {

    /**
     * 混合支付计算
     * <p>
     * 规则：
     * <ul>
     *   <li>lscAmount 取 min(请求lsc, maxAvailableLsc, totalPrice) 即不超过总价</li>
     *   <li>rmbAmount = totalPrice - lscAmount (1:1)</li>
     *   <li>lscAmount 不足部分由人民币补足</li>
     * </ul>
     * </p>
     *
     * @param dto 计算请求
     * @return 混合支付结果(lscAmount, rmbAmount, totalPrice)
     */
    HybridPayDTO calc(HybridPayCalcDTO dto);
}
