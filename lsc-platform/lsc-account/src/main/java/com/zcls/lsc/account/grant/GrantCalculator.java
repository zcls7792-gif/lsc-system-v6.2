package com.zcls.lsc.account.grant;

import com.zcls.lsc.common.constants.LscConstants;
import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.common.ratio.RatioScale;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

/**
 * 第4.3章 赠送算法。
 *
 * 每个商品单元:
 *   S_cent = 销售金额(必须>0,免费赠品不赠送)
 *   C_cent = 冻结成本
 *   R_cent = 人民币分摊
 *   K_ppm  = 系数(0..1_000_000)
 *
 *  M_cent = max(S_cent - C_cent, 0)
 *  grant_unit = floor(M_cent × K_ppm × R_cent × 100 / (1_000_000 × S_cent))
 *
 * 实际计算使用未提前取整的分子，不使用展示 base_unit 再按比例计算。
 * 先逐件取整到 unit，再汇总至商品行与订单；不得整单先汇总后取整。
 * 中间乘法用 BigInteger 防溢出。
 */
@Component
public class GrantCalculator {

    /**
     * 计算单个商品单元的赠送 unit。
     *
     * @param saleCent   S_cent 该单元售价分(必须>0)
     * @param costCent   C_cent 该单元成本分
     * @param rmbCent    R_cent 该单元人民币分摊分
     * @param coefPpm    K_ppm 系数 ppm(0..1_000_000)
     * @return 赠送 unit(非负)
     */
    public long calcUnitGrant(long saleCent, long costCent, long rmbCent, long coefPpm) {
        if (saleCent <= 0L) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "sale_cent must be > 0, got " + saleCent);
        }
        if (coefPpm < 0L || coefPpm > RatioScale.GRANT_COEF_MAX_PPM) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "grant_coefficient out of range [0,1000000] ppm: " + coefPpm);
        }
        if (rmbCent < 0L) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "rmb_cent must be >= 0, got " + rmbCent);
        }

        // M_cent = max(S_cent - C_cent, 0)
        long marginCent = Math.max(saleCent - costCent, 0L);
        if (marginCent == 0L || rmbCent == 0L || coefPpm == 0L) {
            return 0L;
        }

        // 分子: M_cent × K_ppm × R_cent × 100
        BigInteger numerator = BigInteger.valueOf(marginCent)
                .multiply(BigInteger.valueOf(coefPpm))
                .multiply(BigInteger.valueOf(rmbCent))
                .multiply(BigInteger.valueOf(100L));

        // 分母: 1_000_000 × S_cent
        BigInteger denominator = BigInteger.valueOf(RatioScale.PPM_SCALE)
                .multiply(BigInteger.valueOf(saleCent));

        // floor(分子 / 分母)
        BigInteger result = numerator.divide(denominator);
        return result.longValueExact();
    }

    /**
     * 计算全额人民币支付时的展示基准 unit（4.3 全人民币展示基准）。
     * base_unit = floor(M_cent × K_ppm × 100 / 1_000_000)
     * 仅展示用途，实际赠送按 calcUnitGrant 按比例计算。
     */
    public long calcBaseUnit(long saleCent, long costCent, long coefPpm) {
        if (saleCent <= 0L || coefPpm <= 0L) return 0L;
        long marginCent = Math.max(saleCent - costCent, 0L);
        if (marginCent == 0L) return 0L;
        BigInteger numerator = BigInteger.valueOf(marginCent)
                .multiply(BigInteger.valueOf(coefPpm))
                .multiply(BigInteger.valueOf(100L));
        return numerator.divide(BigInteger.valueOf(RatioScale.PPM_SCALE)).longValueExact();
    }
}
