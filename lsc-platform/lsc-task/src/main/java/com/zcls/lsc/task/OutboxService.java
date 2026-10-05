package com.zcls.lsc.task;

import com.zcls.lsc.task.enums.TaskEnums.OutboxStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 第11.2章 事务消息 Outbox 服务。
 *
 *  - 业务侧在同一事务内写入 outbox_event（与业务数据一同提交）
 *  - 投递器（dispatch）定期拉取 PENDING 记录，投递到消息系统
 *  - 投递语义：至少一次（at-least-once）；消费端通过 inbox 唯一约束去重
 *  - 重试指数退避：next_attempt_at = now + 2^attempts 分钟
 *  - 超过 MAX_ATTEMPTS 置 DEAD，由 backfill_task 人工介入
 */
@Service
public class OutboxService {

    /** 最大投递尝试次数。 */
    public static final int MAX_ATTEMPTS = 10;

    /** 初始重试间隔（分钟）。 */
    public static final long INITIAL_BACKOFF_MINUTES = 1L;

    private final JdbcTemplate jdbc;

    public OutboxService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 业务侧写入 outbox 事件（应在业务事务内调用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public long write(long eventId, String topic, String aggregateId, long aggregateVersion,
                      String payloadJson, int schemaVersion) {
        jdbc.update(
                "INSERT INTO outbox_event(event_id, topic, aggregate_id, aggregate_version, "
                        + "payload_json, schema_version, status, attempts, next_attempt_at, "
                        + "published_at) VALUES(?,?,?,?,?,?,'PENDING',0,NULL,NULL)",
                eventId, topic, aggregateId, aggregateVersion, payloadJson, schemaVersion);
        return eventId;
    }

    /**
     * 拉取待投递的 outbox 事件（投递器调用）。
     */
    public List<OutboxRow> fetchPending(int limit) {
        return jdbc.query(
                "SELECT event_id, topic, aggregate_id, aggregate_version, payload_json, "
                        + "schema_version, attempts FROM outbox_event "
                        + "WHERE status='PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= ?) "
                        + "ORDER BY created_at ASC LIMIT ?",
                (rs, rowNum) -> new OutboxRow(
                        rs.getLong("event_id"),
                        rs.getString("topic"),
                        rs.getString("aggregate_id"),
                        rs.getLong("aggregate_version"),
                        rs.getString("payload_json"),
                        rs.getInt("schema_version"),
                        rs.getInt("attempts")),
                Timestamp.valueOf(LocalDateTime.now()), limit);
    }

    /**
     * 标记投递成功。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markPublished(long eventId) {
        jdbc.update(
                "UPDATE outbox_event SET status='PUBLISHED', published_at=?, attempts=attempts+1 "
                        + "WHERE event_id=? AND status='PENDING'",
                Timestamp.valueOf(LocalDateTime.now()), eventId);
    }

    /**
     * 标记投递失败（指数退避；超过 MAX_ATTEMPTS 置 DEAD）。
     *
     * @return 新状态（FAILED / DEAD）
     */
    @Transactional(rollbackFor = Exception.class)
    public String markFailed(long eventId, String error) {
        // 行锁读取 attempts
        List<Integer> attempts = jdbc.query(
                "SELECT attempts FROM outbox_event WHERE event_id=? FOR UPDATE",
                (rs, rowNum) -> rs.getInt(1), eventId);
        if (attempts.isEmpty()) {
            return OutboxStatus.DEAD.name();
        }
        int newAttempts = attempts.get(0) + 1;
        OutboxStatus newStatus = newAttempts >= MAX_ATTEMPTS
                ? OutboxStatus.DEAD : OutboxStatus.FAILED;
        LocalDateTime nextAttempt = newStatus == OutboxStatus.DEAD ? null
                : LocalDateTime.now().plus(calcBackoffMinutes(newAttempts), ChronoUnit.MINUTES);
        jdbc.update(
                "UPDATE outbox_event SET status=?, attempts=?, next_attempt_at=? WHERE event_id=?",
                newStatus.name(), newAttempts,
                nextAttempt == null ? null : Timestamp.valueOf(nextAttempt), eventId);
        return newStatus.name();
    }

    /**
     * 将 FAILED 状态重置为 PENDING（由 backfill 或人工触发重试）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void requeue(long eventId) {
        jdbc.update(
                "UPDATE outbox_event SET status='PENDING', next_attempt_at=? "
                        + "WHERE event_id=? AND status IN ('FAILED','DEAD')",
                Timestamp.valueOf(LocalDateTime.now()), eventId);
    }

    /**
     * 批量重投所有 FAILED/DEAD 记录（由 backfill 任务调用）。
     *
     * @return 重投的记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int requeueAllFailed() {
        return jdbc.update(
                "UPDATE outbox_event SET status='PENDING', next_attempt_at=? "
                        + "WHERE status IN ('FAILED','DEAD')",
                Timestamp.valueOf(LocalDateTime.now()));
    }

    /**
     * 指数退避：2^(attempts-1) 分钟，封顶 60 分钟。
     */
    private long calcBackoffMinutes(int attempts) {
        long backoff = 1L << Math.min(attempts - 1, 6); // 1, 2, 4, 8, 16, 32, 64
        return Math.min(backoff, 60L);
    }

    public record OutboxRow(long eventId, String topic, String aggregateId, long aggregateVersion,
                            String payloadJson, int schemaVersion, int attempts) {}
}
