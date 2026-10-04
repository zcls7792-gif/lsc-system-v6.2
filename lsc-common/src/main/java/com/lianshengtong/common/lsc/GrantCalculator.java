package com.lianshengtong.common.lsc;

/**
 * 赠送算法计算器（V7.7.2 第四章 4.3）
 * <p>
 * 每个商品单元：销售金额 S_cent，冻结成本 C_cent，人民币分摊 R_cent，系数 K_ppm。
 * M_cent = max(S_cent - C_cent, 0)
 * 全额人民币：base_unit = floor(M_cent × K_ppm × 100 / 1_000_000)
 * 混合支付：grant_unit = floor(M_cent × K_ppm × R_cent × 100 / (1_000_000 × S_cent))
 * </p>
 * <p>
 * 实际计算使用未提前取整的分子，不使用展示 base_unit 再按比例计算。
 * 先逐件取整到 unit，再汇总；不得整单先汇总后取整。
 * </p>
 */
public final class GrantCalculator {

    private GrantCalculator() {}

    /**
     * 计算全额人民币支付时的赠送 unit。
     * base_unit = floor(M_cent × K_ppm × 100 / 1_000_000)
     *
     * @param saleCent  商品单元销售金额（分），必须 > 0
     * @param costCent  冻结成本（分）
     * @param kPpm      赠送系数 ppm（0 至 1000000）
     * @return 赠送 unit
     */
    public static long calcFullRmbGrant(long saleCent, long costCent, long kPpm) {
        if (saleCent <= 0) return 0L;
        if (kPpm <= 0) return 0L;
        long mCent = Math.max(saleCent - costCent, 0L);
        if (mCent <= 0) return 0L;
        // floor(M_cent × K_ppm × 100 / 1_000_000)
        return LscMathUtil.mulDivFloor(mCent, kPpm * 100, LscUnitConstants.PPM_BASE);
    }

    /**
     * 计算混合支付（LSC 抵扣或券优惠）时的赠送 unit。
     * grant_unit = floor(M_cent × K_ppm × R_cent × 100 / (1_000_000 × S_cent))
     *
     * @param saleCent  商品单元销售金额（分），必须 > 0
     * @param costCent  冻结成本（分）
     * @param rCent     人民币分摊（分），即该单元实际人民币支付
     * @param kPpm      赠送系数 ppm
     * @return 赠送 unit
     */
    public static long calcHybridGrant(long saleCent, long costCent, long rCent, long kPpm) {
        if (saleCent <= 0) return 0L;
        if (kPpm <= 0 || rCent <= 0) return 0L;
        long mCent = Math.max(saleCent - costCent, 0L);
        if (mCent <= 0) return 0L;
        // floor(M_cent × K_ppm × R_cent × 100 / (1_000_000 × S_cent))
        // 使用 BigInteger 避免溢出
        java.math.BigInteger m = java.math.BigInteger.valueOf(mCent);
        java.math.BigInteger k = java.math.BigInteger.valueOf(kPpm);
        java.math.BigInteger r = java.math.BigInteger.valueOf(rCent);
        java.math.BigInteger s = java.math.BigInteger.valueOf(saleCent);
        java.math.BigInteger numerator = m.multiply(k).multiply(r)
                .multiply(java.math.BigInteger.valueOf(100));
        java.math.BigInteger denominator = java.math.BigInteger.valueOf(LscUnitConstants.PPM_BASE)
                .multiply(s);
        return numerator.divide(denominator).longValueExact();
    }

    /**
     * 计算 LSC 最大抵扣分数（V7.7.2 第四章 4.2）。
     * max_deduction_cent = floor(P_cent × deduction_ppm / 1_000_000)
     *
     * @param goodsCent     商品价款总额（分，不含运费）
     * @param deductionPpm  抵扣比例 ppm（不得大于 500000）
     * @return 最大抵扣分数
     */
    public static long calcMaxDeductionCent(long goodsCent, long deductionPpm) {
        if (goodsCent <= 0 || deductionPpm <= 0) return 0L;
        long ppm = Math.min(deductionPpm, LscUnitConstants.DEDUCTION_MAX_PPM);
        return LscMathUtil.mulPpmFloor(goodsCent, ppm);
    }
}
