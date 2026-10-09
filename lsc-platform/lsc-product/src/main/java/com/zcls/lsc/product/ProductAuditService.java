package com.zcls.lsc.product;

import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.product.enums.ProductEnums.AuditDecision;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 第12.3章 商品审核记录服务。
 *
 *  - 商品每次提交审核产生一条审核记录（version = audit_version）
 *  - 审核决定：APPROVED / REJECTED / NEEDS_REVISION
 *  - 审核记录仅 INSERT，不修改；用于追溯审核历史
 *  - 与 product.audit_version 联动：提交审核时 audit_version+1，审核记录 version 取该值
 */
@Service
public class ProductAuditService {

    private final JdbcTemplate jdbc;
    private final ProductService productService;

    public ProductAuditService(JdbcTemplate jdbc, ProductService productService) {
        this.jdbc = jdbc;
        this.productService = productService;
    }

    /**
     * 提交商品审核（同时创建审核记录，状态置 REVIEWING）。
     *
     * @param productId   商品ID
     * @param applicantId 申请人ID
     * @return audit_id
     */
    @Transactional(rollbackFor = Exception.class)
    public long submitReview(long productId, long applicantId) {
        // 先提交审核（DRAFT -> REVIEWING, audit_version+1）
        productService.submitForReview(productId);

        // 读取更新后的 audit_version
        Long auditVersion = jdbc.queryForObject(
                "SELECT audit_version FROM product WHERE product_id=?",
                Long.class, productId);

        long auditId = nextId();
        jdbc.update(
                "INSERT INTO product_audit_record(audit_id, product_id, version, reviewer_id, "
                        + "decision, reason, evidence_ref, reviewed_at) "
                        + "VALUES(?,?,?,'PENDING',NULL,NULL,NULL,NULL)",
                auditId, productId, auditVersion);
        return auditId;
    }

    /**
     * 执行审核决定。
     *
     * @param auditId    审核记录ID
     * @param reviewerId 审核人ID
     * @param decision   决定
     * @param reason     理由
     * @param evidenceRef 证据引用
     */
    @Transactional(rollbackFor = Exception.class)
    public void review(long auditId, long reviewerId, AuditDecision decision,
                       String reason, String evidenceRef) {
        // 行锁读取审核记录
        List<AuditRow> rows = jdbc.query(
                "SELECT audit_id, product_id, decision FROM product_audit_record "
                        + "WHERE audit_id=? FOR UPDATE",
                (rs, rowNum) -> new AuditRow(
                        rs.getLong("audit_id"),
                        rs.getLong("product_id"),
                        rs.getString("decision")),
                auditId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "audit record not found: " + auditId);
        }
        AuditRow row = rows.get(0);
        if (!"PENDING".equals(row.decision())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "audit record already decided: " + row.decision());
        }

        // 更新审核记录
        jdbc.update(
                "UPDATE product_audit_record SET reviewer_id=?, decision=?, reason=?, "
                        + "evidence_ref=?, reviewed_at=? WHERE audit_id=?",
                reviewerId, decision.name(), reason, evidenceRef,
                Timestamp.valueOf(LocalDateTime.now()), auditId);

        // 联动商品状态
        long productId = row.productId();
        switch (decision) {
            case APPROVED -> productService.approveAndOnSale(productId);
            case REJECTED, NEEDS_REVISION -> productService.rejectToDraft(productId);
        }
    }

    /**
     * 查询商品的审核历史。
     */
    public List<AuditRecordRow> listAuditHistory(long productId) {
        return jdbc.query(
                "SELECT audit_id, product_id, version, reviewer_id, decision, reason, "
                        + "evidence_ref, reviewed_at FROM product_audit_record "
                        + "WHERE product_id=? ORDER BY created_at DESC",
                (rs, rowNum) -> new AuditRecordRow(
                        rs.getLong("audit_id"),
                        rs.getLong("product_id"),
                        rs.getLong("version"),
                        rs.getObject("reviewer_id") == null ? null : rs.getLong("reviewer_id"),
                        rs.getString("decision"),
                        rs.getString("reason"),
                        rs.getString("evidence_ref"),
                        rs.getTimestamp("reviewed_at") == null ? null
                                : rs.getTimestamp("reviewed_at").toLocalDateTime()),
                productId);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    public record AuditRow(long auditId, long productId, String decision) {}

    public record AuditRecordRow(long auditId, long productId, long version, Long reviewerId,
                                 String decision, String reason, String evidenceRef,
                                 LocalDateTime reviewedAt) {}
}
