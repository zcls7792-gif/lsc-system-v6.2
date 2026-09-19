package com.lianshengtong.mall.constant;

import java.math.BigDecimal;

/**
 * 商城服务 V7.3 硬常量
 * <p>
 * 所有 V7.3 合规基线相关的硬上限集中在此处定义，避免魔法数字散落代码中。
 * </p>
 * <ul>
 *   <li>{@link #MAX_GRANT_RATIO}：LSC 赠送比例上限 = 100%（合规基线：赠送不可超过售价）</li>
 *   <li>{@link #MAX_LSC_DEDUCT_RATIO}：混合支付 LSC 抵扣上限 = 50%（合规基线）</li>
 * </ul>
 */
public final class MallConstants {

    private MallConstants() {
    }

    /**
     * LSC 赠送比例上限：100%
     * <p>合规基线：商品赠送 LSC 数量不可超过售价（即进销差 × 比例 ≤ 售价）。</p>
     */
    public static final BigDecimal MAX_GRANT_RATIO = new BigDecimal("1.00");

    /**
     * 混合支付 LSC 抵扣上限：50%
     * <p>合规基线：单笔订单 LSC 抵扣金额不得超过订单总金额的 50%。</p>
     */
    public static final BigDecimal MAX_LSC_DEDUCT_RATIO = new BigDecimal("0.50");

    /**
     * LSC 与人民币兑换比例：1:1
     * <p>V7.3 spec：1 元人民币 = 1 LSC。</p>
     */
    public static final BigDecimal LSC_TO_CNY_RATIO = BigDecimal.ONE;
}
