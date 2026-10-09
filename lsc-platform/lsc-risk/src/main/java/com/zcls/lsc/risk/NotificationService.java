package com.zcls.lsc.risk;

import com.zcls.lsc.risk.enums.RiskEnums.DeliveryStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 第15.2章 通知投递服务。
 *
 *  - 防重投递：相同 (user_id, template_code, business_key, channel) 仅入库一次
 *    （DB 唯一键 uk_notification_delivery 兜底）
 *  - 投递重试上限 MAX_ATTEMPTS：超过则置 DEAD 防止无限重试
 *  - PENDING 超过 STUCK_THRESHOLD_MINUTES 的记录由巡检任务重投
 *  - 投递渠道 SMS / PUSH / IM / EMAIL；模板由 notification_template 定义（外部维护）
 */
@Service
public class NotificationService {

    /** 最大投递尝试次数（含首次）。 */
    public static final int MAX_ATTEMPTS = 5;

    /** 卡住阈值（分钟）：PENDING 超过此时间视为发送失败可重投。 */
    public static final long STUCK_THRESHOLD_MINUTES = 10L;

    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 第15.2.1 入队投递任务（幂等）。
     *
     * @return delivery_id（重复入队返回既有 delivery_id，不抛异常）
     */
    @Transactional(rollbackFor = Exception.class)
    public long enqueue(long userId, String templateCode, String businessKey, String channel) {
        long deliveryId = nextId();
        try {
            jdbc.update(
                    "INSERT INTO notification_delivery(delivery_id, user_id, template_code, "
                            + "business_key, channel, status, attempts, sent_at) "
                            + "VALUES(?,?,?,?,'PENDING',0,NULL)",
                    deliveryId, userId, templateCode, businessKey, channel);
            return deliveryId;
        } catch (DuplicateKeyException e) {
            // 已存在相同 (user, template, business_key, channel) 的投递记录，幂等返回
            Long existing = jdbc.queryForObject(
                    "SELECT delivery_id FROM notification_delivery "
                            + "WHERE user_id=? AND template_code=? AND business_key=? AND channel=?",
                    Long.class, userId, templateCode, businessKey, channel);
            return existing == null ? deliveryId : existing;
        }
    }

    /**
     * 第15.2.2 拉取待发送投递任务（投递消费者调用）。
     */
    public List<PendingDelivery> findPending(int limit) {
        return jdbc.query(
                "SELECT delivery_id, user_id, template_code, business_key, channel, attempts "
                        + "FROM notification_delivery WHERE status='PENDING' "
                        + "ORDER BY created_at ASC LIMIT ?",
                (rs, rowNum) -> new PendingDelivery(
                        rs.getLong("delivery_id"),
                        rs.getLong("user_id"),
                        rs.getString("template_code"),
                        rs.getString("business_key"),
                        rs.getString("channel"),
                        rs.getInt("attempts")),
                limit);
    }

    /**
     * 第15.2.3 标记投递成功。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markSent(long deliveryId) {
        int updated = jdbc.update(
                "UPDATE notification_delivery SET status='SENT', sent_at=?, attempts=attempts+1 "
                        + "WHERE delivery_id=? AND status='PENDING'",
                Timestamp.valueOf(LocalDateTime.now()), deliveryId);
        if (updated == 0) {
            throw new IllegalStateException(
                    "cannot mark sent: delivery not found or not PENDING: " + deliveryId);
        }
    }

    /**
     * 第15.2.4 标记投递失败（attempts+1；超过 MAX_ATTEMPTS 则置 DEAD）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(long deliveryId) {
        // 行锁读取当前 attempts
        List<Integer> attempts = jdbc.query(
                "SELECT attempts FROM notification_delivery WHERE delivery_id=? FOR UPDATE",
                (rs, rowNum) -> rs.getInt(1),
                deliveryId);
        if (attempts.isEmpty()) {
            throw new IllegalStateException("delivery not found: " + deliveryId);
        }
        int newAttempts = attempts.get(0) + 1;
        DeliveryStatus newStatus = newAttempts >= MAX_ATTEMPTS
                ? DeliveryStatus.DEAD : DeliveryStatus.PENDING;
        jdbc.update(
                "UPDATE notification_delivery SET status=?, attempts=? WHERE delivery_id=?",
                newStatus.name(), newAttempts, deliveryId);
    }

    /**
     * 第15.2.5 巡检卡住的 PENDING 投递（超过 STUCK_THRESHOLD_MINUTES）。
     * 通常由定时任务每分钟调用，将这些记录的 attempts 计数 +1；
     * 若超过 MAX_ATTEMPTS 则置 DEAD。
     *
     * @return 重投的投递数
     */
    @Transactional(rollbackFor = Exception.class)
    public int sweepStuckDeliveries() {
        LocalDateTime threshold = LocalDateTime.now().minus(STUCK_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        List<Long> stuck = jdbc.query(
                "SELECT delivery_id FROM notification_delivery "
                        + "WHERE status='PENDING' AND created_at < ?",
                (rs, rowNum) -> rs.getLong(1),
                Timestamp.valueOf(threshold));
        int swept = 0;
        for (Long id : stuck) {
            try {
                markFailed(id);
                swept++;
            } catch (IllegalStateException ignore) {
                // 单条记录异常不影响整体巡检
            }
        }
        return swept;
    }

    /**
     * 查询 DEAD 状态投递（用于人工介入与告警）。
     */
    public List<PendingDelivery> findDeadDeliveries(int limit) {
        return jdbc.query(
                "SELECT delivery_id, user_id, template_code, business_key, channel, attempts "
                        + "FROM notification_delivery WHERE status='DEAD' "
                        + "ORDER BY created_at ASC LIMIT ?",
                (rs, rowNum) -> new PendingDelivery(
                        rs.getLong("delivery_id"),
                        rs.getLong("user_id"),
                        rs.getString("template_code"),
                        rs.getString("business_key"),
                        rs.getString("channel"),
                        rs.getInt("attempts")),
                limit);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    public record PendingDelivery(long deliveryId, long userId, String templateCode,
                                  String businessKey, String channel, int attempts) {}
}
