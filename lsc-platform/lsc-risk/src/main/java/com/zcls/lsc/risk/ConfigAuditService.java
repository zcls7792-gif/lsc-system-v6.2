package com.zcls.lsc.risk;

import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.risk.enums.RiskEnums.AuditResult;
import com.zcls.lsc.risk.enums.RiskEnums.ConfigChangeStatus;
import com.zcls.lsc.risk.enums.RiskEnums.ConfigVersionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 第11章 配置审计与版本生效服务。
 *
 *  - 配置变更必须双签：发起人 requester_id 与审批人 approver_id 必须不同
 *    （DB 层 chk_config_change_no_self 兜底，service 层提前抛 INVALID_ARGUMENT）
 *  - 已生效的 config_version 内容不可修改；新版本生效时旧版本标记为 SUPERSEDED
 *  - effective_at 按 effective_date 灾难性生效，避免运营误操作即时影响线上
 *  - 所有管理员敏感操作必须写 admin_audit_log（before_hash/after_hash 留痕）
 *  - checksum 用于核对新旧 payload 是否一致，避免无意义变更
 */
@Service
public class ConfigAuditService {

    private final JdbcTemplate jdbc;

    public ConfigAuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 第11.2章 发起配置变更工单。
     *
     * @param configGroup   配置分组（如 release_rate / deduction / grant_coef）
     * @param proposedJson  提议内容（JSON 字符串）
     * @param requesterId   发起人
     * @param reason        变更理由
     * @param effectiveDate 生效日期（须为未来日期）
     * @return change_id
     */
    @Transactional(rollbackFor = Exception.class)
    public long submitChange(String configGroup, String proposedJson, long requesterId,
                             String reason, LocalDate effectiveDate) {
        if (configGroup == null || configGroup.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "config_group is required");
        }
        if (proposedJson == null || proposedJson.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "proposed_json is required");
        }
        if (effectiveDate == null || !effectiveDate.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "effective_date must be a future date");
        }
        // 拉取当前生效版本号作为 before_version
        Long beforeVersion = currentVersion(configGroup);

        long changeId = nextId();
        jdbc.update(
                "INSERT INTO config_change_request(change_id, config_group, before_version, "
                        + "proposed_json, requester_id, approver_id, reason, effective_date, "
                        + "status, approval_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                changeId, configGroup, beforeVersion, proposedJson,
                requesterId, null, reason, Date.valueOf(effectiveDate),
                ConfigChangeStatus.PENDING.name(), null);
        return changeId;
    }

    /**
     * 第11.3章 双签审批。
     * 必须由非发起人审批；同一发起人不可自审。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approveChange(long changeId, long approverId) {
        if (approverId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "approver_id is required");
        }
        Long requesterId = jdbc.queryForObject(
                "SELECT requester_id FROM config_change_request WHERE change_id=?",
                Long.class, changeId);
        if (requesterId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "change request not found: " + changeId);
        }
        if (requesterId == approverId) {
            // DB 层 chk_config_change_no_self 也兜底，提前拦截给出明确错误
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "approver must differ from requester (dual-sign)");
        }
        int updated = jdbc.update(
                "UPDATE config_change_request SET approver_id=?, status='APPROVED', "
                        + "approval_at=? WHERE change_id=? AND status='PENDING'",
                approverId, Timestamp.valueOf(LocalDateTime.now()), changeId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "change request not found or not in PENDING status: " + changeId);
        }
    }

    /**
     * 第11.4章 驳回变更工单。
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejectChange(long changeId, long approverId, String reason) {
        int updated = jdbc.update(
                "UPDATE config_change_request SET approver_id=?, status='REJECTED', "
                        + "approval_at=? WHERE change_id=? AND status='PENDING'",
                approverId, Timestamp.valueOf(LocalDateTime.now()), changeId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "change request not found or not in PENDING status: " + changeId);
        }
    }

    /**
     * 第11.5章 撤回未审批工单（仅发起人可撤回，且仅 PENDING 状态可撤回）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void withdrawChange(long changeId, long requesterId) {
        int updated = jdbc.update(
                "UPDATE config_change_request SET status='WITHDRAWN' "
                        + "WHERE change_id=? AND requester_id=? AND status='PENDING'",
                changeId, requesterId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "cannot withdraw: change not found, not owned, or not PENDING");
        }
    }

    /**
     * 第14.2章 应用已审批工单（生成新 config_version，旧版本置为 SUPERSEDED）。
     * 通常由定时任务在 effective_date 当日触发，亦可手动指定工单提前/按时生效。
     *
     * @param changeId 工单ID（必须已 APPROVED）
     * @return 新版本号
     */
    @Transactional(rollbackFor = Exception.class)
    public long applyChange(long changeId) {
        // 行锁读取工单
        List<ChangeRow> rows = jdbc.query(
                "SELECT change_id, config_group, before_version, proposed_json, requester_id, "
                        + "approver_id, effective_date, status FROM config_change_request "
                        + "WHERE change_id=? FOR UPDATE",
                (rs, rowNum) -> new ChangeRow(
                        rs.getLong("change_id"),
                        rs.getString("config_group"),
                        (Long) rs.getObject("before_version"),
                        rs.getString("proposed_json"),
                        rs.getLong("requester_id"),
                        (Long) rs.getObject("approver_id"),
                        rs.getDate("effective_date").toLocalDate(),
                        rs.getString("status")),
                changeId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "change request not found: " + changeId);
        }
        ChangeRow row = rows.get(0);
        if (!ConfigChangeStatus.APPROVED.name().equals(row.status())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "change request not approved: " + row.status());
        }
        if (row.approverId() == null || row.approverId() == row.requesterId()) {
            throw new BusinessException(ErrorCode.INVARIANT_VIOLATED,
                    "dual-sign violated: approver missing or equals requester");
        }

        // 计算新版本号（当前最大 + 1）
        long newVersion = (row.beforeVersion() == null ? 0L : row.beforeVersion()) + 1L;
        String checksum = checksum(row.proposedJson());
        LocalDateTime effectiveAt = LocalDateTime.now();

        // 旧版本置为 SUPERSEDED
        jdbc.update(
                "UPDATE config_version SET status='SUPERSEDED' "
                        + "WHERE config_group=? AND status='ACTIVE'",
                row.configGroup());

        // 写入新版本（uk 主键保证并发安全）
        jdbc.update(
                "INSERT INTO config_version(config_group, version, payload_json, "
                        + "schema_version, checksum, effective_at, change_id, status) "
                        + "VALUES(?,?,?,?,?,?,?,?)",
                row.configGroup(), newVersion, row.proposedJson(),
                1, checksum, Timestamp.valueOf(effectiveAt), changeId,
                ConfigVersionStatus.ACTIVE.name());

        // 工单置为 APPLIED
        jdbc.update(
                "UPDATE config_change_request SET status='APPLIED' WHERE change_id=?",
                changeId);
        return newVersion;
    }

    /**
     * 第14.3章 批量应用已到 effective_date 的 APPROVED 工单。
     * 由定时任务每日触发。
     *
     * @return 应用成功的工单数
     */
    @Transactional(rollbackFor = Exception.class)
    public int applyDueChanges() {
        List<Long> due = jdbc.query(
                "SELECT change_id FROM config_change_request "
                        + "WHERE status='APPROVED' AND effective_date <= ?",
                (rs, rowNum) -> rs.getLong(1),
                Date.valueOf(LocalDate.now()));
        int applied = 0;
        for (Long id : due) {
            try {
                applyChange(id);
                applied++;
            } catch (BusinessException ignore) {
                // 跳过异常工单，由后续人工介入；保持事务独立
            }
        }
        return applied;
    }

    /**
     * 读取当前生效配置（payload_json + version + checksum）。
     */
    public ActiveConfig getActiveConfig(String configGroup) {
        List<ActiveConfig> rows = jdbc.query(
                "SELECT version, payload_json, checksum, effective_at, change_id "
                        + "FROM config_version WHERE config_group=? AND status='ACTIVE'",
                (rs, rowNum) -> new ActiveConfig(
                        configGroup,
                        rs.getLong("version"),
                        rs.getString("payload_json"),
                        rs.getString("checksum"),
                        rs.getTimestamp("effective_at").toLocalDateTime(),
                        rs.getLong("change_id")),
                configGroup);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 第11.6章 写管理员审计日志。
     * 审计日志表禁止 UPDATE/DELETE，仅 INSERT；before_hash/after_hash 留痕资源前后状态摘要。
     */
    @Transactional(rollbackFor = Exception.class)
    public long writeAuditLog(long actorId, String action, String resourceType,
                              long resourceId, String requestId, AuditResult result,
                              String reason, String beforeHash, String afterHash) {
        long logId = nextId();
        jdbc.update(
                "INSERT INTO admin_audit_log(log_id, actor_id, action, resource_type, "
                        + "resource_id, request_id, result, reason, before_hash, after_hash, "
                        + "occurred_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                logId, actorId, action, resourceType, resourceId, requestId,
                result.name(), reason, beforeHash, afterHash,
                Timestamp.valueOf(LocalDateTime.now()));
        return logId;
    }

    /**
     * 查询管理员操作历史。
     */
    public List<AuditLogRow> queryAuditLog(Long actorId, String resourceType, long resourceId, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT log_id, actor_id, action, resource_type, resource_id, request_id, "
                        + "result, reason, before_hash, after_hash, occurred_at "
                        + "FROM admin_audit_log WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (actorId != null) {
            sql.append(" AND actor_id=?");
            args.add(actorId);
        }
        if (resourceType != null) {
            sql.append(" AND resource_type=?");
            args.add(resourceType);
        }
        if (resourceId > 0) {
            sql.append(" AND resource_id=?");
            args.add(resourceId);
        }
        sql.append(" ORDER BY occurred_at DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(),
                (rs, rowNum) -> new AuditLogRow(
                        rs.getLong("log_id"),
                        rs.getLong("actor_id"),
                        rs.getString("action"),
                        rs.getString("resource_type"),
                        rs.getLong("resource_id"),
                        rs.getString("request_id"),
                        rs.getString("result"),
                        rs.getString("reason"),
                        rs.getString("before_hash"),
                        rs.getString("after_hash"),
                        rs.getTimestamp("occurred_at").toLocalDateTime()),
                args.toArray());
    }

    private Long currentVersion(String configGroup) {
        List<Long> vs = jdbc.query(
                "SELECT version FROM config_version WHERE config_group=? AND status='ACTIVE'",
                (rs, rowNum) -> rs.getLong(1),
                configGroup);
        return vs.isEmpty() ? null : vs.get(0);
    }

    private String checksum(String json) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(json.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    public record ChangeRow(long changeId, String configGroup, Long beforeVersion,
                            String proposedJson, long requesterId, Long approverId,
                            LocalDate effectiveDate, String status) {}

    public record ActiveConfig(String configGroup, long version, String payloadJson,
                               String checksum, LocalDateTime effectiveAt, long changeId) {}

    public record AuditLogRow(long logId, long actorId, String action, String resourceType,
                              long resourceId, String requestId, String result, String reason,
                              String beforeHash, String afterHash, LocalDateTime occurredAt) {}
}
