package com.zcls.lsc.risk;

import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.risk.enums.RiskEnums.GateScope;
import com.zcls.lsc.risk.enums.RiskEnums.GateStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 第11.7章 合规门禁服务。
 *
 *  - 用于 AI 风控模型启用 / 上线发布 / 紧急回滚等高危操作的前置授权
 *  - 门禁开启有时效（expiry_at），超期自动失效
 *  - 业务侧在执行高危操作前必须调用 requireGate 强制校验
 *  - 门禁为一次性凭证：核销后立即置 REVOKED，避免重复使用
 */
@Service
public class ComplianceGateService {

    /** 默认门禁有效期 4 小时。 */
    public static final long DEFAULT_GATE_TTL_HOURS = 4L;

    private final JdbcTemplate jdbc;

    public ComplianceGateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 第11.7.1 申请开启门禁（创建待批准工单）。
     *
     * @param gateCode    门禁编码（业务唯一，幂等键）
     * @param scope       门禁用途（AI_RISK_ENABLE / RELEASE_PUBLISH / EMERGENCY_ROLLBACK ...）
     * @param evidenceRef 证据引用（如审批会议纪要、变更评审纪要）
     * @param ownerId     申请人ID
     * @return gate_code
     */
    @Transactional(rollbackFor = Exception.class)
    public String requestGate(String gateCode, GateScope scope, String evidenceRef, long ownerId) {
        if (gateCode == null || gateCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "gate_code is required");
        }
        if (scope == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "scope is required");
        }
        // 幂等：已存在工单则不允许重复申请
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM compliance_gate WHERE gate_code=?",
                Integer.class, gateCode);
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "gate already exists: " + gateCode);
        }
        jdbc.update(
                "INSERT INTO compliance_gate(gate_code, scope, evidence_ref, owner_id, "
                        + "status, approved_at, expiry_at) VALUES(?,?,?,?,'PENDING',NULL,NULL)",
                gateCode, scope.name(), evidenceRef, ownerId);
        return gateCode;
    }

    /**
     * 第11.7.2 批准门禁（带有效期）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approveGate(String gateCode, long approverId, long ttlHours) {
        if (ttlHours <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "ttl_hours must be positive");
        }
        // 校验申请人与批准人不同
        Long ownerId = jdbc.queryForObject(
                "SELECT owner_id FROM compliance_gate WHERE gate_code=?",
                Long.class, gateCode);
        if (ownerId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "gate not found: " + gateCode);
        }
        if (ownerId == approverId) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "approver must differ from owner (dual-sign)");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbc.update(
                "UPDATE compliance_gate SET status='APPROVED', approved_at=?, expiry_at=? "
                        + "WHERE gate_code=? AND status='PENDING'",
                Timestamp.valueOf(now),
                Timestamp.valueOf(now.plus(ttlHours, ChronoUnit.HOURS)),
                gateCode);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "gate not found or already processed: " + gateCode);
        }
    }

    /**
     * 第11.7.3 拒绝门禁申请。
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejectGate(String gateCode, long approverId) {
        int updated = jdbc.update(
                "UPDATE compliance_gate SET status='REJECTED' "
                        + "WHERE gate_code=? AND status='PENDING'",
                gateCode);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "gate not found or already processed: " + gateCode);
        }
    }

    /**
     * 第11.7.4 校验门禁是否处于有效 APPROVED 状态。
     * 超期的门禁会在此调用时惰性置为 EXPIRED。
     */
    public boolean verifyGate(String gateCode) {
        List<GateRow> rows = jdbc.query(
                "SELECT status, expiry_at FROM compliance_gate WHERE gate_code=?",
                (rs, rowNum) -> new GateRow(
                        rs.getString("status"),
                        rs.getTimestamp("expiry_at") == null ? null
                                : rs.getTimestamp("expiry_at").toLocalDateTime()),
                gateCode);
        if (rows.isEmpty()) {
            return false;
        }
        GateRow row = rows.get(0);
        if (!GateStatus.APPROVED.name().equals(row.status())) {
            return false;
        }
        // 惰性超期处理：超期则置 EXPIRED 并返回 false
        if (row.expiryAt() != null && row.expiryAt().isBefore(LocalDateTime.now())) {
            jdbc.update(
                    "UPDATE compliance_gate SET status='EXPIRED' WHERE gate_code=?",
                    gateCode);
            return false;
        }
        return true;
    }

    /**
     * 第11.7.5 强制校验：门禁未通过则抛 FORBIDDEN。
     * 业务侧在执行 AI 启用 / 上线发布等高危操作前必须调用。
     */
    public void requireGate(String gateCode) {
        if (!verifyGate(gateCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "compliance gate not approved or expired: " + gateCode);
        }
    }

    /**
     * 第11.7.6 撤销门禁（人工撤销或一次性核销）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void revokeGate(String gateCode) {
        int updated = jdbc.update(
                "UPDATE compliance_gate SET status='REVOKED' WHERE gate_code=?",
                gateCode);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "gate not found: " + gateCode);
        }
    }

    /**
     * 第11.7.7 巡检超期门禁（定时任务调用）。
     *
     * @return 置为 EXPIRED 的门禁数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int sweepExpiredGates() {
        return jdbc.update(
                "UPDATE compliance_gate SET status='EXPIRED' "
                        + "WHERE status='APPROVED' AND expiry_at < ?",
                Timestamp.valueOf(LocalDateTime.now()));
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    public record GateRow(String status, LocalDateTime expiryAt) {}
}
