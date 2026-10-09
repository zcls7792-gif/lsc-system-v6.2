package com.zcls.lsc.user;

import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.user.enums.UserEnums.BusinessStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 第12.2章 B 端商户资质服务。
 *
 *  - business_profile 主键为 user_id（一个用户最多一条资质档案）
 *  - 状态机：NONE → PENDING → APPROVED / REJECTED → SUSPENDED / EXPIRED
 *  - approved_version 固化审核通过时的版本号，用于回溯
 *  - license_expiry 过期后由定时任务将状态置为 EXPIRED
 */
@Service
public class BusinessProfileService {

    private final JdbcTemplate jdbc;

    public BusinessProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 提交 B 端资质申请。
     * 若已存在 profile 则更新；仅 NONE/REJECTED 状态可重新提交。
     */
    @Transactional(rollbackFor = Exception.class)
    public long submitApplication(long userId, String entityName, String licenseNo,
                                  LocalDate licenseExpiry, String licenseObjectKey) {
        // 查现有状态
        String currentStatus = jdbc.query(
                "SELECT business_status FROM business_profile WHERE user_id=?",
                (rs, rowNum) -> rs.getString(1), userId)
                .stream().findFirst().orElse(null);

        if (currentStatus != null
                && !BusinessStatus.NONE.name().equals(currentStatus)
                && !BusinessStatus.REJECTED.name().equals(currentStatus)) {
            throw new BusinessException(ErrorCode.BENEFIT_BLOCKED,
                    "cannot submit application in status: " + currentStatus);
        }

        long auditId = nextId();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        if (currentStatus == null) {
            // 新建 profile
            jdbc.update(
                    "INSERT INTO business_profile(user_id, entity_name, license_no, license_expiry, "
                            + "business_status, updated_at) VALUES(?,?,?,?,?,?)",
                    userId, entityName, licenseNo, Date.valueOf(licenseExpiry),
                    BusinessStatus.PENDING.name(), now);
        } else {
            jdbc.update(
                    "UPDATE business_profile SET entity_name=?, license_no=?, license_expiry=?, "
                            + "business_status='PENDING', updated_at=? WHERE user_id=?",
                    entityName, licenseNo, Date.valueOf(licenseExpiry), now, userId);
        }

        // 写审核记录
        jdbc.update(
                "INSERT INTO business_audit_record(audit_id, user_id, application_version, "
                        + "license_object_key, status, created_at) VALUES(?,?,?,?,?,?)",
                auditId, userId, 1L, licenseObjectKey, BusinessStatus.PENDING.name(), now);
        return auditId;
    }

    /**
     * 审核 B 端资质。
     *
     * @param auditId   审核记录ID
     * @param auditorId 审核人
     * @param approved  是否通过
     * @param remark    审核备注
     */
    @Transactional(rollbackFor = Exception.class)
    public void auditApplication(long auditId, long auditorId, boolean approved, String remark) {
        // 查审核记录
        Map<String, Object> audit = jdbc.queryForList(
                "SELECT * FROM business_audit_record WHERE audit_id=?", auditId)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "audit record not found: " + auditId));

        long userId = ((Number) audit.get("user_id")).longValue();
        String status = (String) audit.get("status");
        if (!BusinessStatus.PENDING.name().equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "audit record not in PENDING status: " + status);
        }

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String targetStatus = approved ? BusinessStatus.APPROVED.name() : BusinessStatus.REJECTED.name();

        // 更新审核记录
        jdbc.update(
                "UPDATE business_audit_record SET status=?, auditor_id=?, remark=?, audited_at=? "
                        + "WHERE audit_id=?",
                targetStatus, auditorId, remark, now, auditId);

        // 更新 profile
        if (approved) {
            jdbc.update(
                    "UPDATE business_profile SET business_status='APPROVED', approved_version=?, "
                            + "approved_at=?, updated_at=? WHERE user_id=?",
                    auditId, now, now, userId);
        } else {
            jdbc.update(
                    "UPDATE business_profile SET business_status='REJECTED', updated_at=? WHERE user_id=?",
                    now, userId);
        }

        // 通过后将 user_type 升级为 B
        if (approved) {
            jdbc.update("UPDATE user SET user_type='B' WHERE user_id=? AND user_type!='B'", userId);
        }
    }

    /** 暂停已通过资质（违规处置）。 */
    @Transactional(rollbackFor = Exception.class)
    public void suspendProfile(long userId, String reason) {
        int updated = jdbc.update(
                "UPDATE business_profile SET business_status='SUSPENDED', updated_at=? "
                        + "WHERE user_id=? AND business_status='APPROVED'",
                Timestamp.valueOf(LocalDateTime.now()), userId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "approved profile not found: " + userId);
        }
    }

    /** 恢复已暂停资质。 */
    @Transactional(rollbackFor = Exception.class)
    public void resumeProfile(long userId) {
        int updated = jdbc.update(
                "UPDATE business_profile SET business_status='APPROVED', updated_at=? "
                        + "WHERE user_id=? AND business_status='SUSPENDED'",
                Timestamp.valueOf(LocalDateTime.now()), userId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "suspended profile not found: " + userId);
        }
    }

    /** 查询用户资质档案。 */
    public Map<String, Object> getProfile(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM business_profile WHERE user_id=?", userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 查询审核历史。 */
    public List<Map<String, Object>> listAuditHistory(long userId) {
        return jdbc.queryForList(
                "SELECT * FROM business_audit_record WHERE user_id=? ORDER BY created_at DESC", userId);
    }

    /** 扫描过期资质并置为 EXPIRED（定时任务调用）。 */
    @Transactional(rollbackFor = Exception.class)
    public int expireOverdueProfiles() {
        return jdbc.update(
                "UPDATE business_profile SET business_status='EXPIRED', updated_at=? "
                        + "WHERE business_status='APPROVED' AND license_expiry < ?",
                Timestamp.valueOf(LocalDateTime.now()), Date.valueOf(LocalDate.now()));
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }
}
