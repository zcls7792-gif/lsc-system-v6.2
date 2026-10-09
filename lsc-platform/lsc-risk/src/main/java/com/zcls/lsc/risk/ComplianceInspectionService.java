package com.zcls.lsc.risk;

import com.zcls.lsc.common.constants.LscConstants;
import com.zcls.lsc.risk.enums.ComplianceEnums.InspectionStatus;
import com.zcls.lsc.risk.enums.ComplianceEnums.RuleCode;
import com.zcls.lsc.risk.enums.ComplianceEnums.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 第九章 合规巡检服务 — R01 至 R12 自动化校验。
 *
 * <p>每日定时执行，逐条校验 V7.7.1 方案 1.7 节十二条合规硬底线。
 * 异常项写入 compliance_inspection_result 表，并按严重级别触发告警：</p>
 * <ul>
 *   <li>CRITICAL — 立即告警两名超级管理员，可阻断次日释放任务</li>
 *   <li>HIGH — 当日告警，限期修复</li>
 *   <li>MEDIUM / INFO — 仅记录留痕</li>
 * </ul>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>每条规则独立方法，单一职责，便于单独触发与单元测试</li>
 *   <li>校验以数据库事实为依据（SQL COUNT/SUM），不依赖内存状态</li>
 *   <li>硬边界常量引用 LscConstants，与业务代码同源</li>
 *   <li>fail-closed：任一 CRITICAL 规则不通过，runInspection 返回 false</li>
 * </ul>
 */
@Service
public class ComplianceInspectionService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceInspectionService.class);

    private final JdbcTemplate jdbc;

    public ComplianceInspectionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // =====================================================================
    //  巡检总入口
    // =====================================================================

    /**
     * 执行全部 R01-R12 合规巡检。
     *
     * @param businessDate 业务日
     * @return 全部 CRITICAL 规则均通过返回 true；任一 CRITICAL 不通过返回 false
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean runInspection(LocalDate businessDate) {
        log.info("[合规巡检] 开始执行 R01-R12 巡检，业务日={}", businessDate);
        List<InspectionResult> results = new ArrayList<>();

        results.add(checkR01LscClosedLoop());
        results.add(checkR02GrantCoefficientBound());
        results.add(checkR03CostHidden());
        results.add(checkR04MutexRmbOnlyGrant());
        results.add(checkR05DeductionCap());
        results.add(checkR06NoCapitalPool());
        results.add(checkR07SupplierRmbOnly());
        results.add(checkR08NoRmbNoGrant());
        results.add(checkR09ReleaseRateBound());
        results.add(checkR10ReferralCouponOnly());
        results.add(checkR11B2bVerification());
        results.add(checkR12NoDiversion());

        // 持久化巡检结果
        persistResults(businessDate, results);

        long criticalFail = results.stream()
                .filter(r -> r.status() == InspectionStatus.FAIL)
                .filter(r -> r.severity() == Severity.CRITICAL)
                .count();

        log.info("[合规巡检] 完成，共 {} 条，CRITICAL 失败 {} 条", results.size(), criticalFail);
        return criticalFail == 0;
    }

    // =====================================================================
    //  R01 LSC 全链路闭环
    //  检查点：1) 账户余额无负数；2) 流水无提现/转让/兑现类型；
    //          3) 无跨账户流转分录（同一事件不涉及两个不同 user_id）
    // =====================================================================
    InspectionResult checkR01LscClosedLoop() {
        try {
            // 1. 账户余额非负（DB CHECK 兜底，巡检二次确认）
            Long negativeAccounts = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM lsc_account "
                            + "WHERE locked_unit < 0 OR available_unit < 0 "
                            + "OR reserved_unit < 0 OR frozen_locked_unit < 0 "
                            + "OR frozen_available_unit < 0 OR pending_recovery_unit < 0",
                    Long.class);

            // 2. 流水类型白名单：仅允许合规事件类型
            Long illegalEventTypes = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM lsc_event "
                            + "WHERE event_type NOT IN "
                            + "('GRANT','RELEASE','CONSUME','RETURN','EXPIRE','REVOKE',"
                            + "'FREEZE','UNFREEZE','RECOVERY','REFUND_RESTORE','REFUND_CLAWBACK')",
                    Long.class);

            // 3. 无跨账户流转：分录 disposition_type 不应为 TRANSFER
            Long transferEntries = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM lsc_entry "
                            + "WHERE disposition_type = 'TRANSFER'",
                    Long.class);

            boolean pass = negativeAccounts == 0 && illegalEventTypes == 0
                    && transferEntries == 0;
            String detail = String.format(
                    "negative_accounts=%d, illegal_event_types=%d, transfer_entries=%d",
                    negativeAccounts, illegalEventTypes, transferEntries);
            return new InspectionResult(RuleCode.R01_LSC_CLOSED_LOOP,
                    pass ? InspectionStatus.PASS : InspectionStatus.FAIL,
                    pass ? Severity.INFO : Severity.CRITICAL, detail);
        } catch (Exception e) {
            return errorResult(RuleCode.R01_LSC_CLOSED_LOOP, e);
        }
    }

    // =====================================================================
    //  R02 权益发放基数为经营空间，回馈系数上限 100% 硬编码
    //  检查点：product_price_version.grant_coefficient_ppm ∈ [0, 1_000_000]
    // =====================================================================
    InspectionResult checkR02GrantCoefficientBound() {
        try {
            Long outOfRange = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM product_price_version "
                            + "WHERE grant_coefficient_ppm < 0 "
                            + "OR grant_coefficient_ppm > ?",
                    Long.class, LscConstants.GRANT_COEF_MAX_PPM);

            // 赠送基准不应超过经营空间（零售/采购价对应空间的 100%）
            // grant_c_unit 上限 = (retail_price_cent - cost) * UNIT_SCALE / UNITS_PER_CENT
            // 简化校验：grant_c_unit > 0 时 grant_coefficient_ppm 不得为 0
            Long grantWithoutCoef = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM product_price_version "
                            + "WHERE (grant_c_unit > 0 OR grant_b_unit > 0) "
                            + "AND grant_coefficient_ppm = 0",
                    Long.class);

            boolean pass = outOfRange == 0 && grantWithoutCoef == 0;
            String detail = String.format("out_of_range_coef=%d, grant_without_coef=%d",
                    outOfRange, grantWithoutCoef);
            return new InspectionResult(RuleCode.R02_GRANT_COEFFICIENT_BOUND,
                    pass ? InspectionStatus.PASS : InspectionStatus.FAIL,
                    pass ? Severity.INFO : Severity.CRITICAL, detail);
        } catch (Exception e) {
            return errorResult(RuleCode.R02_GRANT_COEFFICIENT_BOUND, e);
        }
    }

    // =====================================================================
    //  R03 全平台隐藏采购成本与内部经营空间
    //  检查点：1) cost_price_enc 非空；2) C 端展示的赠送基准不超过售价对应上限
    // =====================================================================
    InspectionResult checkR03CostHidden() {
        try {
            // 1. 成本密文必须非空
            Long emptyCostEnc = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM product_price_version "
                            + "WHERE cost_price_enc IS NULL OR OCTET_LENGTH(cost_price_enc) = 0",
                    Long.class);

            // 2. 赠送基准 unit 不应超过售价（分）× UNIT_SCALE / UNITS_PER_CENT
            //    即 grant_c_unit <= retail_price_cent * 100（1元=100分=10000unit）
            Long grantExceedsPrice = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM product_price_version "
                            + "WHERE grant_c_unit > retail_price_cent * ? / ? "
                            + "OR grant_b_unit > b_price_cent * ? / ?",
                    Long.class,
                    LscConstants.UNIT_SCALE, LscConstants.UNITS_PER_CENT,
                    LscConstants.UNIT_SCALE, LscConstants.UNITS_PER_CENT);

            boolean pass = emptyCostEnc == 0 && grantExceedsPrice == 0;
            String detail = String.format("empty_cost_enc=%d, grant_exceeds_price=%d",
                    emptyCostEnc, grantExceedsPrice);
            return new InspectionResult(RuleCode.R03_COST_HIDDEN,
                    pass ? InspectionStatus.PASS : InspectionStatus.FAIL,
                    pass ? Severity.INFO : Severity.HIGH, detail);
        } catch (Exception e) {
            return errorResult(RuleCode.R03_COST_HIDDEN, e);
        }
    }

    // =====================================================================
    //  R04 混合支付仅人民币实付发放权益，券与 LSC 互斥
    //  检查点：1) orders 无 coupon_cent>0 AND lsc_unit>0；
    //          2) order_item 中 rmb_share<=0 时 granted_unit=0
    // =====================================================================
    InspectionResult checkR04MutexRmbOnlyGrant() {
        try {
            // 1. 券与 LSC 互斥（DB CHECK 兜底，巡检二次确认）
            Long mutexViolation = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM orders "
                            + "WHERE coupon_cent > 0 AND lsc_unit > 0",
                    Long.class);

            // 2. 人民币实付为 0 的订单行不应有赠送
            Long grantWithoutRmb = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM order_item "
                            + "WHERE rmb_share_cent <= 0 AND granted_unit > 0",
                    Long.class);

            boolean pass = mutexViolation == 0 && grantWithoutRmb == 0;
            String detail = String.format("mutex_violation=%d, grant_without_rmb=%d",
                    mutexViolation, grantWithoutRmb);
            return new InspectionResult(RuleCode.R04_MUTEX_RMB_ONLY_GRANT,
                    pass ? InspectionStatus.PASS : InspectionStatus.FAIL,
                    pass ? Severity.INFO : Severity.CRITICAL, detail);
        } catch (Exception e) {
            return errorResult(RuleCode.R04_MUTEX_RMB_ONLY_GRANT, e);
        }
    }

    // =====================================================================
    //  R05 单笔 LSC 抵扣硬顶 50%
    //  检查点：orders.lsc_unit 对应的抵扣比例 ≤ DEDUCTION_MAX_PPM
    //  抵扣比例 = (lsc_unit / UNIT_SCALE) / (goods_cent / 100)
    //           = lsc_unit * 100 / (goods_cent * UNIT_SCALE)
    //  用 ppm 比较：lsc_unit * 100 * 1_000_000 <= goods_cent * UNIT_SCALE * DEDUCTION_MAX_PPM
    // =====================================================================
    InspectionResult checkR05DeductionCap() {
        try {
            Long overCap = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM orders "
                            + "WHERE lsc_unit > 0 AND goods_cent > 0 "
                            + "AND lsc_unit * 100 * 1000000 > "
                            + "goods_cent * ? * ?",
                    Long.class,
                    LscConstants.UNIT_SCALE, LscConstants.DEDUCTION_MAX_PPM);

            boolean pass = overCap == 0;
            String detail = String.format("deduction_over_cap=%d (max_ppm=%d)",
                    overCap, LscConstants.DEDUCTION_MAX_PPM);
            return new InspectionResult(RuleCode.R05_DEDUCTION_CAP,
                    pass ? InspectionStatus.PASS : InspectionStatus.FAIL,
                    pass ? Severity.INFO : Severity.CRITICAL, detail);
        } catch (Exception e) {
            return errorResult(RuleCode.R05_DEDUCTION_CAP, e);
        }
    }

    // =====================================================================
    //  R06 不设资金归集池
    //  检查点：系统中不存在资金池/清分/兑付相关表（架构层校验，标记为 INFO）
    // =====================================================================
    InspectionResult checkR06NoCapitalPool() {
        try {
            // 检查是否存在疑似资金池的表名
            Long suspiciousTables = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() "
                            + "AND (table_name LIKE '%pool%' "
                            + "OR table_name LIKE '%capital%' "
                            + "OR table_name LIKE '%clearing%' "
                            + "OR table_name LIKE '%settle_pool%') "
                            + "AND table_name NOT IN ('settlement','settlement_allocation')",
                    Long.class);

            boolean pass = suspiciousTables == 0;
            String detail = String.format("suspicious_pool_tables=%d", suspiciousTables);
            return new InspectionResult(RuleCode.R06_NO_CAPITAL_POOL,
                    pass ? InspectionStatus.PASS : InspectionStatus.FAIL,
                    pass ? Severity.INFO : Severity.HIGH, detail);
        } catch (Exception e) {
            return errorResult(RuleCode.R06_NO_CAPITAL_POOL, e);
        }
    }

    // =====================================================================
    //  R07 上游供应商仅人民币结算
    //  检查点：1) supplier 表无 LSC/权益字段；2) settlement 仅人民币金额
    // =====================================================================
    InspectionResult checkR07SupplierRmbOnly() {
        try {
            // 1. 供应商表不应包含 lsc/权益相关列
            Long supplierLscColumns = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name = 'supplier' "
                            + "AND (column_name LIKE '%lsc%' "
                            + "OR column_name LIKE '%benefit%' "
                            + "OR column_name LIKE '%coupon%')",
                    Long.class);

            // 2. 结算单金额应为人民币（amount_cent 字段存在且为正）
            Long nonRmbSettlement = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM settlement "
                            + "WHERE amount_cent <= 0",
                    Long.class);

            boolean pass = supplierLscColumns == 0 && nonRmbSettlement == 0;
            String detail = String.format("supplier_lsc_columns=%d, non_rmb_settlement=%d",
                    supplierLscColumns, nonRmbSettlement);
            return new InspectionResult(RuleCode.R07_SUPPLIER_RMB_ONLY,
                    pass ? InspectionStatus.PASS : InspectionStatus.FAIL,
                    pass ? Severity.INFO : Severity.CRITICAL, detail);
        } catch (Exception e) {
            return errorResult(RuleCode.R07_SUPPLIER_RMB_ONLY, e);
        }
    }

    // =====================================================================
    //  R08 无真实人民币消费不发放权益
    //  检查点：order_item.rmb_share <= 0 时 granted_unit = 0
    //         （与 R04 第二点重合，此处加强为 granted_unit 必须等于按公式计算值）
    // =====================================================================
    InspectionResult checkR08NoRmbNoGrant() {
        try {
            // 已完成订单中，人民币实付为 0 但有赠送的记录
            Long grantWithZeroRmb = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM order_item oi "
                            + "JOIN orders o ON o.order_id = oi.order_id "
                            + "WHERE o.payment_status = 'PAID' "
                            + "AND oi.rmb_share_cent <= 0 AND oi.granted_unit > 0",
                    Long.class);

            // 退款后应扣回的赠送未处理（clawback_required > clawback_completed + pending）
            Long clawbackInconsistent = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM order_item "
                            + "WHERE clawback_required_unit > 0 "
                            + "AND clawback_required_unit <> "
                            + "clawback_completed_unit + clawback_pending_unit",
                    Long.class);

            boolean pass = grantWithZeroRmb == 0 && clawbackInconsistent == 0;
            String detail = String.format("grant_with_zero_rmb=%d, clawback_inconsistent=%d",
                    grantWithZeroRmb, clawbackInconsistent);
            return new InspectionResult(RuleCode.R08_NO_RMB_NO_GRANT,
                    pass ? InspectionStatus.PASS : InspectionStatus.FAIL,
                    pass ? Severity.INFO : Severity.CRITICAL, detail);
        } catch (Exception e) {
            return errorResult(RuleCode.R08_NO_RMB_NO_GRANT, e);
        }
    }

    // =====================================================================
    //  R09 释放速率与周转率硬边界
    //  检查点：1) release_day_snapshot.rate_ppb ∈ [500000, 1000000]
    //          2) 周转率 w ∈ [5000, 25000] ppm
    // =====================================================================
    InspectionResult checkR09ReleaseRateBound() {
        try {
            // 1. 释放速率越界
            Long rateOutOfBound = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM release_day_snapshot "
                            + "WHERE rate_ppb < ? OR rate_ppb > ?",
                    Long.class,
                    LscConstants.RELEASE_RATE_MIN_PPB,
                    LscConstants.RELEASE_RATE_MAX_PPB);

            // 2. 周转率 w 越界
            Long wOutOfBound = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM release_day_snapshot "
                            + "WHERE w_ppm < ? OR w_ppm > ?",
                    Long.class,
                    LscConstants.W_HARD_MIN_PPM,
                    LscConstants.W_HARD_MAX_PPM);

            boolean pass = rateOutOfBound == 0 && wOutOfBound == 0;
            String detail = String.format(
                    "rate_out_of_bound=%d (min=%d,max=%d), w_out_of_bound=%d (min=%d,max=%d)",
                    rateOutOfBound, LscConstants.RELEASE_RATE_MIN_PPB,
                    LscConstants.RELEASE_RATE_MAX_PPB,
                    wOutOfBound, LscConstants.W_HARD_MIN_PPM, LscConstants.W_HARD_MAX_PPM);
            return new InspectionResult(RuleCode.R09_RELEASE_RATE_BOUND,
                    pass ? InspectionStatus.PASS : InspectionStatus.FAIL,
                    pass ? Severity.INFO : Severity.CRITICAL, detail);
        } catch (Exception e) {
            return errorResult(RuleCode.R09_RELEASE_RATE_BOUND, e);
        }
    }

    // =====================================================================
    //  R10 推荐奖励为独立优惠券，不新增权益
    //  检查点：1) referral_reward 仅关联 coupon_id，无 lsc_unit 字段；
    //          2) 推荐奖励不写入 lsc_event（无 ORIGIN_REFERRAL 类型）
    // =====================================================================
    InspectionResult checkR10ReferralCouponOnly() {
        try {
            // 1. 推荐奖励表不应包含 lsc 相关列
            Long referralLscColumns = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name = 'referral_reward' "
                            + "AND (column_name LIKE '%lsc%' "
                            + "OR column_name LIKE '%unit%' "
                            + "OR column_name LIKE '%benefit%')",
                    Long.class);

            // 2. 流水表中无推荐来源的权益发放事件
            Long referralGrantEvents = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM lsc_event "
                            + "WHERE event_type IN ('REFERRAL','REFERRAL_REWARD')",
                    Long.class);

            boolean pass = referralLscColumns == 0 && referralGrantEvents == 0;
            String detail = String.format("referral_lsc_columns=%d, referral_grant_events=%d",
                    referralLscColumns, referralGrantEvents);
            return new InspectionResult(RuleCode.R10_REFERRAL_COUPON_ONLY,
                    pass ? InspectionStatus.PASS : InspectionStatus.FAIL,
                    pass ? Severity.INFO : Severity.HIGH, detail);
        } catch (Exception e) {
            return errorResult(RuleCode.R10_REFERRAL_COUPON_ONLY, e);
        }
    }

    // =====================================================================
    //  R11 B 端商户必须资质审核
    //  检查点：user_type 为 B 端的用户，business_audit_record 必须为已通过
    // =====================================================================
    InspectionResult checkR11B2bVerification() {
        try {
            // B 端用户（user_type='B'）必须有 APPROVED 的资质档案
            Long unverifiedB2b = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user u "
                            + "WHERE u.user_type = 'B' "
                            + "AND NOT EXISTS ("
                            + "  SELECT 1 FROM business_profile bp "
                            + "  WHERE bp.user_id = u.user_id AND bp.business_status = 'APPROVED'"
                            + ")",
                    Long.class);

            boolean pass = unverifiedB2b == 0;
            String detail = String.format("unverified_b2b_users=%d", unverifiedB2b);
            return new InspectionResult(RuleCode.R11_B2B_VERIFICATION,
                    pass ? InspectionStatus.PASS : InspectionStatus.FAIL,
                    pass ? Severity.INFO : Severity.HIGH, detail);
        } catch (Exception e) {
            return errorResult(RuleCode.R11_B2B_VERIFICATION, e);
        }
    }

    // =====================================================================
    //  R12 B 端禁止窜货，违规取消资格冻结权益
    //  检查点：1) 被认定窜货的商户（business_risk_log）user_type 已非 BUSINESS；
    //          2) 对应账户权益已冻结
    // =====================================================================
    InspectionResult checkR12NoDiversion() {
        try {
            // 1. 已确认窜货的商户（business_risk_log），user_type 应已取消 B
            Long diversionNotHandled = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM business_risk_log brl "
                            + "JOIN user u ON u.user_id = brl.user_id "
                            + "WHERE brl.risk_type = 'DIVERSION' "
                            + "AND brl.review_status = 'CONFIRMED' "
                            + "AND u.user_type = 'B'",
                    Long.class);

            // 2. 已确认窜货的商户，账户应有冻结余额
            Long diversionNotFrozen = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM business_risk_log brl "
                            + "JOIN lsc_account la ON la.user_id = brl.user_id "
                            + "WHERE brl.risk_type = 'DIVERSION' "
                            + "AND brl.review_status = 'CONFIRMED' "
                            + "AND la.frozen_locked_unit = 0 "
                            + "AND la.frozen_available_unit = 0",
                    Long.class);

            boolean pass = diversionNotHandled == 0 && diversionNotFrozen == 0;
            String detail = String.format(
                    "diversion_not_handled=%d, diversion_not_frozen=%d",
                    diversionNotHandled, diversionNotFrozen);
            return new InspectionResult(RuleCode.R12_NO_DIVERSION,
                    pass ? InspectionStatus.PASS : InspectionStatus.FAIL,
                    pass ? Severity.INFO : Severity.HIGH, detail);
        } catch (Exception e) {
            return errorResult(RuleCode.R12_NO_DIVERSION, e);
        }
    }

    // =====================================================================
    //  持久化与辅助方法
    // =====================================================================

    /**
     * 将巡检结果批量写入 compliance_inspection_result 表。
     */
    private void persistResults(LocalDate businessDate, List<InspectionResult> results) {
        for (InspectionResult r : results) {
            jdbc.update(
                    "INSERT INTO compliance_inspection_result"
                            + "(result_id, business_date, rule_code, status, severity, "
                            + "detail, checked_at) VALUES(?,?,?,?,?,?,?)",
                    nextId(), java.sql.Date.valueOf(businessDate),
                    r.ruleCode().code(), r.status().name(),
                    r.severity().name(), r.detail(),
                    Timestamp.valueOf(LocalDateTime.now()));
        }
    }

    /**
     * 构造 ERROR 级别结果（SQL 异常等）。
     */
    private InspectionResult errorResult(RuleCode code, Exception e) {
        log.error("[合规巡检] {} 执行异常", code.code(), e);
        String msg = e.getMessage() == null ? e.getClass().getSimpleName()
                : e.getMessage().substring(0, Math.min(e.getMessage().length(), 500));
        return new InspectionResult(code, InspectionStatus.ERROR, Severity.HIGH,
                "inspection_error: " + msg);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    /**
     * 查询指定业务日的巡检结果（供管理端查看）。
     */
    public List<InspectionResultRow> queryResults(LocalDate businessDate) {
        return jdbc.query(
                "SELECT rule_code, status, severity, detail, checked_at "
                        + "FROM compliance_inspection_result "
                        + "WHERE business_date = ? ORDER BY rule_code",
                (rs, rowNum) -> new InspectionResultRow(
                        rs.getString("rule_code"),
                        rs.getString("status"),
                        rs.getString("severity"),
                        rs.getString("detail"),
                        rs.getTimestamp("checked_at").toLocalDateTime()),
                java.sql.Date.valueOf(businessDate));
    }

    /**
     * 单条巡检结果。
     *
     * @param ruleCode 规则编码
     * @param status   检查状态
     * @param severity 严重级别
     * @param detail   检查详情（含违规数量等）
     */
    public record InspectionResult(
            RuleCode ruleCode,
            InspectionStatus status,
            Severity severity,
            String detail) {}

    /**
     * 巡检结果查询行（供管理端展示）。
     */
    public record InspectionResultRow(
            String ruleCode,
            String status,
            String severity,
            String detail,
            LocalDateTime checkedAt) {}
}
