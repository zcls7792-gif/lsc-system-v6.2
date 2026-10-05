package com.zcls.lsc.risk;

import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.risk.enums.RiskEnums.AppealStatus;
import com.zcls.lsc.risk.enums.RiskEnums.CaseReviewStatus;
import com.zcls.lsc.risk.enums.RiskEnums.ProposedAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 第10章 风控案件与申诉服务。
 *
 *  - 案件 48 小时内必须人工复核（review_deadline）
 *  - 申诉 5 个工作日内必须答复（reply_deadline，仅工作日）
 *  - AI 标记（ai_flag=1）的案件不得直接执行冻结，须复核确认后生效
 *  - 案件复核结果四态：CONFIRMED / REVOKED / DOWNGRADED + 仍 PENDING 视为未复核
 *  - 所有处置都写 lsc_event.case_id 关联，由账务侧执行实际冻结/解冻
 */
@Service
public class RiskService {

    /** 案件复核期限 48 小时。 */
    public static final int REVIEW_DEADLINE_HOURS = 48;

    /** 申诉答复期限 5 个工作日。 */
    public static final int APPEAL_REPLY_WORKING_DAYS = 5;

    private final JdbcTemplate jdbc;

    public RiskService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 第10.2章 创建风控案件。
     *
     * @param userId         被处置用户
     * @param ruleId         命中规则（可空，表示人工立案）
     * @param aiFlag         是否 AI 标记（AI 标记的案件冻结需复核后才执行）
     * @param evidenceRef    证据引用
     * @param proposedAction 建议处置
     * @return case_id
     */
    @Transactional(rollbackFor = Exception.class)
    public long createCase(long userId, String ruleId, boolean aiFlag,
                           String evidenceRef, ProposedAction proposedAction) {
        if (proposedAction == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "proposed_action is required");
        }
        long caseId = nextId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plus(REVIEW_DEADLINE_HOURS, ChronoUnit.HOURS);

        jdbc.update(
                "INSERT INTO risk_case(case_id, user_id, rule_id, ai_flag, evidence_ref, "
                        + "proposed_action, review_status, review_deadline, reviewer_id, "
                        + "decision, decided_at, created_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                caseId, userId, ruleId, aiFlag ? 1 : 0, evidenceRef,
                proposedAction.name(), CaseReviewStatus.PENDING.name(),
                Timestamp.valueOf(deadline), null, null, null,
                Timestamp.valueOf(now));
        return caseId;
    }

    /**
     * 第10.3章 人工复核案件。
     *
     * @param caseId     案件ID
     * @param reviewerId 复核人（必须非空，与原 AI 系统区分）
     * @param decision   复核决定：CONFIRMED 维持 / REVOKED 撤销 / DOWNGRADED 降级
     * @param reason     复核理由（写入 audit_log 由调用方处理）
     */
    @Transactional(rollbackFor = Exception.class)
    public void reviewCase(long caseId, long reviewerId, CaseReviewStatus decision, String reason) {
        if (decision == CaseReviewStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "decision cannot be PENDING in review");
        }
        // 行锁保证并发复核串行化
        int updated = jdbc.update(
                "UPDATE risk_case SET review_status=?, reviewer_id=?, decision=?, decided_at=? "
                        + "WHERE case_id=? AND review_status='PENDING'",
                decision.name(), reviewerId, decision.name(),
                Timestamp.valueOf(LocalDateTime.now()), caseId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "case not found or already reviewed: " + caseId);
        }
    }

    /**
     * 第10.4章 用户提交申诉。
     * 必须基于已复核案件；reply_deadline = 当前 + 5 个工作日。
     */
    @Transactional(rollbackFor = Exception.class)
    public long submitAppeal(long caseId, long userId) {
        // 校验案件存在且已复核
        String status = jdbc.queryForObject(
                "SELECT review_status FROM risk_case WHERE case_id=?",
                String.class, caseId);
        if (status == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "case not found: " + caseId);
        }
        if (CaseReviewStatus.PENDING.name().equals(status)) {
            throw new BusinessException(ErrorCode.BENEFIT_BLOCKED,
                    "cannot appeal a case still pending review");
        }
        // 校验调用方 userId 与案件 user_id 一致
        Long caseUser = jdbc.queryForObject(
                "SELECT user_id FROM risk_case WHERE case_id=?", Long.class, caseId);
        if (caseUser == null || caseUser != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "appeal must be submitted by case owner");
        }
        // 防重复申诉：同一 case 仅允许一个未结案申诉
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM appeal WHERE case_id=? AND status IN ('SUBMITTED','IN_REVIEW')",
                Integer.class, caseId);
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "appeal already in progress for case " + caseId);
        }

        long appealId = nextId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime replyDeadline = addWorkingDays(now, APPEAL_REPLY_WORKING_DAYS);

        jdbc.update(
                "INSERT INTO appeal(appeal_id, case_id, user_id, submitted_at, "
                        + "reply_deadline, status, reviewer_id, decision_ref, created_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?)",
                appealId, caseId, userId, Timestamp.valueOf(now),
                Timestamp.valueOf(replyDeadline), AppealStatus.SUBMITTED.name(),
                null, null, Timestamp.valueOf(now));
        return appealId;
    }

    /**
     * 第10.5章 受理申诉（标记为复核中）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void acceptAppeal(long appealId, long reviewerId) {
        int updated = jdbc.update(
                "UPDATE appeal SET status='IN_REVIEW', reviewer_id=? "
                        + "WHERE appeal_id=? AND status='SUBMITTED'",
                reviewerId, appealId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "appeal not found or not in SUBMITTED status: " + appealId);
        }
    }

    /**
     * 第10.6章 答复申诉。
     *
     * @param appealId    申诉ID
     * @param reviewerId  答复人
     * @param decision    答复决定：UPHELD 维持 / OVERTURNED 撤销 / PARTIALLY_OVERTURNED 部分撤销
     * @param decisionRef 答复依据引用（文档/证据链接）
     */
    @Transactional(rollbackFor = Exception.class)
    public void replyAppeal(long appealId, long reviewerId, AppealStatus decision, String decisionRef) {
        if (decision == AppealStatus.SUBMITTED || decision == AppealStatus.IN_REVIEW) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "decision must be a terminal status");
        }
        int updated = jdbc.update(
                "UPDATE appeal SET status=?, reviewer_id=?, decision_ref=? "
                        + "WHERE appeal_id=? AND status IN ('SUBMITTED','IN_REVIEW')",
                decision.name(), reviewerId, decisionRef, appealId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "appeal not found or already closed: " + appealId);
        }
    }

    /**
     * 第10.7章 查询用户当前未被复核的处置（用于拦截新权益操作）。
     * 仅返回 PENDING 状态或超期未复核的案件。
     */
    public List<Long> findBlockingCases(long userId) {
        return jdbc.query(
                "SELECT case_id FROM risk_case WHERE user_id=? AND review_status='PENDING' "
                        + "AND proposed_action IN ('FREEZE_AVAILABLE','FREEZE_ALL','BLOCK_ORDER','BLOCK_REFUND')",
                (rs, rowNum) -> rs.getLong(1),
                userId);
    }

    /**
     * 查询超期未复核案件（用于定时巡检）。
     */
    public List<Long> findOverdueCases() {
        return jdbc.query(
                "SELECT case_id FROM risk_case WHERE review_status='PENDING' "
                        + "AND review_deadline < ?",
                (rs, rowNum) -> rs.getLong(1),
                Timestamp.valueOf(LocalDateTime.now()));
    }

    /**
     * 查询超期未答复申诉（用于定时巡检）。
     */
    public List<Long> findOverdueAppeals() {
        return jdbc.query(
                "SELECT appeal_id FROM appeal WHERE status IN ('SUBMITTED','IN_REVIEW') "
                        + "AND reply_deadline < ?",
                (rs, rowNum) -> rs.getLong(1),
                Timestamp.valueOf(LocalDateTime.now()));
    }

    /**
     * 工作日加法：跳过周六、周日。
     */
    private LocalDateTime addWorkingDays(LocalDateTime from, int days) {
        LocalDateTime t = from;
        int added = 0;
        while (added < days) {
            t = t.plus(1, ChronoUnit.DAYS);
            DayOfWeek dow = t.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        // 若答复日落在周末，顺延至下周一
        while (t.getDayOfWeek() == DayOfWeek.SATURDAY || t.getDayOfWeek() == DayOfWeek.SUNDAY) {
            t = t.plus(1, ChronoUnit.DAYS);
        }
        return t;
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }
}
