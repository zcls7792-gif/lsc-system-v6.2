package com.zcls.lsc.risk.enums;

/**
 * 第九章 合规巡检 R01-R12 规则枚举与结果状态。
 *
 * <p>每条规则对应 V7.7.1 方案 1.7 节的一条合规硬底线。
 * 巡检结果写入 compliance_inspection_result 表，异常项触发告警并阻断交易。</p>
 */
public final class ComplianceEnums {

    private ComplianceEnums() {}

    /**
     * 合规规则编码 R01-R12，对应 1.7 节十二条硬底线。
     */
    public enum RuleCode {
        /** R01 LSC 全链路闭环，无权益到人民币通道 */
        R01_LSC_CLOSED_LOOP,
        /** R02 权益发放基数为经营空间，回馈系数上限 100% 硬编码 */
        R02_GRANT_COEFFICIENT_BOUND,
        /** R03 全平台隐藏采购成本与内部经营空间 */
        R03_COST_HIDDEN,
        /** R04 混合支付仅人民币实付发放权益，券与 LSC 互斥 */
        R04_MUTEX_RMB_ONLY_GRANT,
        /** R05 单笔 LSC 抵扣硬顶 50% */
        R05_DEDUCTION_CAP,
        /** R06 不设资金归集池 */
        R06_NO_CAPITAL_POOL,
        /** R07 上游供应商仅人民币结算 */
        R07_SUPPLIER_RMB_ONLY,
        /** R08 无真实人民币消费不发放权益 */
        R08_NO_RMB_NO_GRANT,
        /** R09 释放速率与周转率硬边界 */
        R09_RELEASE_RATE_BOUND,
        /** R10 推荐奖励为独立优惠券，不新增权益 */
        R10_REFERRAL_COUPON_ONLY,
        /** R11 B 端商户必须资质审核 */
        R11_B2B_VERIFICATION,
        /** R12 B 端禁止窜货，违规取消资格冻结权益 */
        R12_NO_DIVERSION;

        public String code() {
            return name();
        }
    }

    /**
     * 巡检项严重级别。
     */
    public enum Severity {
        /** 致命：必须立即阻断交易并告警双管理员 */
        CRITICAL,
        /** 高危：当日内修复并告警 */
        HIGH,
        /** 中危：记录并跟踪 */
        MEDIUM,
        /** 信息：仅记录 */
        INFO
    }

    /**
     * 巡检结果状态。
     */
    public enum InspectionStatus {
        /** 通过 */
        PASS,
        /** 不通过（存在违规） */
        FAIL,
        /** 检查异常（SQL 错误等） */
        ERROR
    }
}
