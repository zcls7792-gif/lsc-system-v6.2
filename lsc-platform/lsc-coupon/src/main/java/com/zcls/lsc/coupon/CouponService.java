package com.zcls.lsc.coupon;

import com.zcls.lsc.common.constants.LscConstants;
import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 第8.3 / 8.4 章 优惠券服务。
 *
 *  - 模板修改生成新版本，用户券领取时固化面额、门槛、范围、有效期、来源及规则版本
 *  - 状态: AVAILABLE / RESERVED / REDEEMED / EXPIRED / REVOKED
 *  - 已支付使用券的商品订单退款：全部适用商品退回时返还一张同属性券；原券仍有效则沿用原截止，已过期则给 7 天宽限
 *  - replacement_of_coupon_id 唯一确保整单最多补发一次
 */
@Service
public class CouponService {

    private final JdbcTemplate jdbc;

    public CouponService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 发券（从模板版本固化属性）。
     */
    @Transactional(rollbackFor = Exception.class)
    public long issueCoupon(long userId, long templateId, int templateVersion, Long sourceRewardId) {
        var tpl = jdbc.queryForObject(
                "SELECT face_cent, min_spend_cent, valid_days, scope_definition_json "
                        + "FROM coupon_template_version WHERE template_id=? AND template_version=?",
                (rs, rowNum) -> new TemplateRow(
                        rs.getLong("face_cent"),
                        rs.getLong("min_spend_cent"),
                        rs.getInt("valid_days"),
                        rs.getString("scope_definition_json")),
                templateId, templateVersion);

        long couponId = nextId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime validUntil = now.plusDays(tpl.validDays());

        jdbc.update(
                "INSERT INTO user_coupon(coupon_id, user_id, template_id, template_version, "
                        + "face_cent_snapshot, min_spend_cent_snapshot, scope_snapshot_json, received_at, "
                        + "valid_until, status, source_reward_id, version) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                couponId, userId, templateId, templateVersion, tpl.faceCent(), tpl.minSpendCent(),
                tpl.scopeJson(), java.sql.Timestamp.valueOf(now), java.sql.Timestamp.valueOf(validUntil),
                "AVAILABLE", sourceRewardId, 0);

        writeFlow(couponId, userId, "ISSUE", "ISSUE:" + couponId, null, null, "AVAILABLE", tpl.faceCent());
        return couponId;
    }

    /**
     * 核销券（支付成功时）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void redeemCoupon(long couponId, long orderId, long userId) {
        String status = jdbc.queryForObject(
                "SELECT status FROM user_coupon WHERE coupon_id=? FOR UPDATE", String.class, couponId);
        if (!"RESERVED".equals(status)) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, "coupon status=" + status);
        }
        Long faceCent = jdbc.queryForObject(
                "SELECT face_cent_snapshot FROM user_coupon WHERE coupon_id=?", Long.class, couponId);

        jdbc.update("UPDATE user_coupon SET status='REDEEMED', used_order_id=?, version=version+1 "
                + "WHERE coupon_id=?", orderId, couponId);
        writeFlow(couponId, userId, "REDEEM", "REDEEM:" + orderId, orderId, null, "REDEEMED", faceCent);
    }

    /**
     * 释放券（取消未支付订单）。
     * 仍在有效期即恢复可用，已过期则作废。
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseCoupon(long couponId, long orderId) {
        var row = jdbc.queryForObject(
                "SELECT status, valid_until, face_cent_snapshot, user_id FROM user_coupon WHERE coupon_id=? FOR UPDATE",
                (rs, rowNum) -> new CouponStatusRow(
                        rs.getString("status"),
                        rs.getTimestamp("valid_until").toLocalDateTime(),
                        rs.getLong("face_cent_snapshot"),
                        rs.getLong("user_id")),
                couponId);

        if (!"RESERVED".equals(row.status())) return;

        String newStatus = row.validUntil().isAfter(LocalDateTime.now()) ? "AVAILABLE" : "EXPIRED";
        jdbc.update("UPDATE user_coupon SET status=?, reserved_order_id=NULL, version=version+1 "
                + "WHERE coupon_id=?", newStatus, couponId);
        writeFlow(couponId, row.userId(), "RELEASE", "RELEASE:" + orderId, orderId, null, newStatus,
                row.faceCent());
    }

    /**
     * 第8.4章 订单退款补券。
     * 全部适用商品均退回时，对已核销券返还一张同属性券；原券仍有效则沿用原截止，已过期则给 7 天宽限。
     * 整单最多补发一次（replacement_of_coupon_id 唯一）。
     *
     * @return 补发的新券ID，或 null 如果不满足条件
     */
    @Transactional(rollbackFor = Exception.class)
    public Long replaceCouponOnRefund(long originalCouponId, long orderId, long userId) {
        // 检查是否已补发
        Long existing = jdbc.queryForObject(
                "SELECT coupon_id FROM user_coupon WHERE replacement_of_coupon_id=?",
                (rs, rowNum) -> rs.getLong(1), originalCouponId);
        if (existing != null) return null; // 已补发过

        // 查原券属性
        var orig = jdbc.queryForObject(
                "SELECT face_cent_snapshot, min_spend_cent_snapshot, scope_snapshot_json, valid_until, status, template_id, template_version "
                        + "FROM user_coupon WHERE coupon_id=?",
                (rs, rowNum) -> new OrigCouponRow(
                        rs.getLong("face_cent_snapshot"),
                        rs.getLong("min_spend_cent_snapshot"),
                        rs.getString("scope_snapshot_json"),
                        rs.getTimestamp("valid_until").toLocalDateTime(),
                        rs.getString("status"),
                        rs.getLong("template_id"),
                        rs.getInt("template_version")),
                originalCouponId);

        if (!"REDEEMED".equals(orig.status())) return null;

        // 新券有效期：原券仍有效则沿用原截止，已过期则给 7 天宽限
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newValidUntil = orig.validUntil().isAfter(now)
                ? orig.validUntil() : now.plusDays(LscConstants.EXPIRED_COUPON_REFUND_GRACE_DAYS);

        long newCouponId = nextId();
        jdbc.update(
                "INSERT INTO user_coupon(coupon_id, user_id, template_id, template_version, "
                        + "face_cent_snapshot, min_spend_cent_snapshot, scope_snapshot_json, received_at, "
                        + "valid_until, status, replacement_of_coupon_id, version) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                newCouponId, userId, orig.templateId(), orig.templateVersion(), orig.faceCent(),
                orig.minSpendCent(), orig.scopeJson(), java.sql.Timestamp.valueOf(now),
                java.sql.Timestamp.valueOf(newValidUntil), "AVAILABLE", originalCouponId, 0);

        // 原券保持 REDEEMED，记录 replacement
        writeFlow(newCouponId, userId, "REPLACE", "REPLACE:" + originalCouponId, null, orderId,
                "AVAILABLE", orig.faceCent());
        return newCouponId;
    }

    private void writeFlow(long couponId, long userId, String eventType, String businessKey,
                           Long orderId, Long refundId, String afterStatus, long faceCent) {
        jdbc.update(
                "INSERT INTO coupon_flow(flow_id, coupon_id, user_id, event_type, business_key, order_id, "
                        + "refund_id, before_status, after_status, face_cent, occurred_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                nextId(), couponId, userId, eventType, businessKey, orderId, refundId,
                "PREV", afterStatus, faceCent, java.sql.Timestamp.valueOf(LocalDateTime.now()));
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    private record TemplateRow(long faceCent, long minSpendCent, int validDays, String scopeJson) {}
    private record CouponStatusRow(String status, LocalDateTime validUntil, long faceCent, long userId) {}
    private record OrigCouponRow(long faceCent, long minSpendCent, String scopeJson,
                                 LocalDateTime validUntil, String status, long templateId, int templateVersion) {}
}
