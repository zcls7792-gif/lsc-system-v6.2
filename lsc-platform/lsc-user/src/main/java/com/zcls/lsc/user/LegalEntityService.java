package com.zcls.lsc.user;

import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.user.enums.UserEnums.EntityRole;
import com.zcls.lsc.user.enums.UserEnums.EntityStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 第12.2章 法律主体服务。
 *
 *  - legal_entity 是平台/商家/收款/开票/权益义务等法律身份的统一抽象
 *  - registration_no 唯一（营业执照号），DB uk_legal_entity_reg 兜底
 *  - 状态机：PENDING → VERIFIED / REJECTED → DISABLED
 *  - verified_at 在认证通过时写入，不可篡改
 */
@Service
public class LegalEntityService {

    private final JdbcTemplate jdbc;

    public LegalEntityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 创建法律主体（初始状态 PENDING）。
     */
    @Transactional(rollbackFor = Exception.class)
    public long createEntity(String legalName, String registrationNo, EntityRole role,
                             String payMerchantId) {
        if (legalName == null || legalName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "legal_name is required");
        }
        if (registrationNo == null || registrationNo.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "registration_no is required");
        }
        long entityId = nextId();
        jdbc.update(
                "INSERT INTO legal_entity(entity_id, legal_name, registration_no, role, "
                        + "pay_merchant_id, status, created_at) "
                        + "VALUES(?,?,?,?,?,?,?)",
                entityId, legalName, registrationNo, role.name(),
                payMerchantId, EntityStatus.PENDING.name(),
                Timestamp.valueOf(LocalDateTime.now()));
        return entityId;
    }

    /**
     * 审核通过法律主体。
     */
    @Transactional(rollbackFor = Exception.class)
    public void verifyEntity(long entityId) {
        int updated = jdbc.update(
                "UPDATE legal_entity SET status='VERIFIED', verified_at=? "
                        + "WHERE entity_id=? AND status='PENDING'",
                Timestamp.valueOf(LocalDateTime.now()), entityId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "pending entity not found: " + entityId);
        }
    }

    /**
     * 驳回法律主体。
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejectEntity(long entityId, String reason) {
        int updated = jdbc.update(
                "UPDATE legal_entity SET status='REJECTED' WHERE entity_id=? AND status='PENDING'",
                entityId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "pending entity not found: " + entityId);
        }
    }

    /**
     * 停用法律主体（已认证主体可停用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableEntity(long entityId) {
        int updated = jdbc.update(
                "UPDATE legal_entity SET status='DISABLED' WHERE entity_id=? AND status='VERIFIED'",
                entityId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "verified entity not found: " + entityId);
        }
    }

    /** 查询法律主体详情。 */
    public Map<String, Object> getEntity(long entityId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM legal_entity WHERE entity_id=?", entityId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "entity not found: " + entityId);
        }
        return rows.get(0);
    }

    /** 按角色查询法律主体列表。 */
    public List<Map<String, Object>> listByRole(EntityRole role) {
        return jdbc.queryForList(
                "SELECT * FROM legal_entity WHERE role=? ORDER BY created_at DESC", role.name());
    }

    /** 按状态查询。 */
    public List<Map<String, Object>> listByStatus(EntityStatus status) {
        return jdbc.queryForList(
                "SELECT * FROM legal_entity WHERE status=? ORDER BY created_at DESC", status.name());
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }
}
