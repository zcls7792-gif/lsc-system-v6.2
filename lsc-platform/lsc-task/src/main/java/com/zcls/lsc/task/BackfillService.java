package com.zcls.lsc.task;

import com.zcls.lsc.task.enums.TaskEnums.BackfillSourceType;
import com.zcls.lsc.task.enums.TaskEnums.BackfillStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 第12.7章 补单/重试服务。
 *
 *  - 对失败的 task_run / outbox_event / inbox_event 创建补单任务
 *  - 幂等：uk_backfill_source(source_type, source_id) 保证同一来源仅一个补单任务
 *  - 指数退避重试；超过 max_attempts 置 FAILED 并告警
 *  - 补单任务本身也需要租约保护，避免多节点同时重试同一来源
 */
@Service
public class BackfillService {

    /** 默认最大重试次数。 */
    public static final int DEFAULT_MAX_ATTEMPTS = 10;

    /** 初始重试间隔（分钟）。 */
    public static final long INITIAL_BACKOFF_MINUTES = 5L;

    private final JdbcTemplate jdbc;
    private final LeaseService leaseService;

    public BackfillService(JdbcTemplate jdbc, LeaseService leaseService) {
        this.jdbc = jdbc;
        this.leaseService = leaseService;
    }

    /**
     * 创建补单任务（幂等）。
     *
     * @return backfill_id（已存在返回既有 ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public long createBackfill(BackfillSourceType sourceType, String sourceId,
                               LocalDate businessDate, int maxAttempts) {
        if (maxAttempts <= 0) maxAttempts = DEFAULT_MAX_ATTEMPTS;
        // 幂等检查
        Long existing = jdbc.queryForObject(
                "SELECT backfill_id FROM backfill_task WHERE source_type=? AND source_id=?",
                Long.class, sourceType.name(), sourceId);
        if (existing != null) return existing;

        long backfillId = nextId();
        jdbc.update(
                "INSERT INTO backfill_task(backfill_id, source_type, source_id, business_date, "
                        + "status, attempts, max_attempts, next_attempt_at) "
                        + "VALUES(?,?,?,?,'PENDING',0,?,?)",
                backfillId, sourceType.name(), sourceId,
                businessDate == null ? null : Date.valueOf(businessDate),
                maxAttempts, Timestamp.valueOf(LocalDateTime.now()));
        return backfillId;
    }

    /**
     * 拉取待重试的补单任务。
     */
    public List<BackfillRow> fetchPending(int limit) {
        return jdbc.query(
                "SELECT backfill_id, source_type, source_id, business_date, attempts, max_attempts "
                        + "FROM backfill_task WHERE status='PENDING' "
                        + "AND (next_attempt_at IS NULL OR next_attempt_at <= ?) "
                        + "ORDER BY created_at ASC LIMIT ?",
                (rs, rowNum) -> new BackfillRow(
                        rs.getLong("backfill_id"),
                        rs.getString("source_type"),
                        rs.getString("source_id"),
                        rs.getDate("business_date") == null ? null
                                : rs.getDate("business_date").toLocalDate(),
                        rs.getInt("attempts"),
                        rs.getInt("max_attempts")),
                Timestamp.valueOf(LocalDateTime.now()), limit);
    }

    /**
     * 标记补单成功。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markSucceeded(long backfillId) {
        jdbc.update(
                "UPDATE backfill_task SET status='SUCCEEDED', attempts=attempts+1 "
                        + "WHERE backfill_id=?",
                backfillId);
    }

    /**
     * 标记补单失败（指数退避；超过 max_attempts 置 FAILED）。
     *
     * @return 新状态
     */
    @Transactional(rollbackFor = Exception.class)
    public String markFailed(long backfillId, String error) {
        List<int[]> rows = jdbc.query(
                "SELECT attempts, max_attempts FROM backfill_task WHERE backfill_id=? FOR UPDATE",
                (rs, rowNum) -> new int[]{rs.getInt(1), rs.getInt(2)},
                backfillId);
        if (rows.isEmpty()) return BackfillStatus.FAILED.name();
        int attempts = rows.get(0)[0] + 1;
        int maxAttempts = rows.get(0)[1];
        BackfillStatus status = attempts >= maxAttempts
                ? BackfillStatus.FAILED : BackfillStatus.PENDING;
        LocalDateTime nextAttempt = status == BackfillStatus.FAILED ? null
                : LocalDateTime.now().plus(calcBackoffMinutes(attempts), ChronoUnit.MINUTES);
        jdbc.update(
                "UPDATE backfill_task SET status=?, attempts=?, next_attempt_at=?, last_error=? "
                        + "WHERE backfill_id=?",
                status.name(), attempts,
                nextAttempt == null ? null : Timestamp.valueOf(nextAttempt),
                truncate(error, 1024), backfillId);
        return status.name();
    }

    /**
     * 从失败的 outbox_event 自动创建补单任务。
     *
     * @return 创建的补单任务数
     */
    @Transactional(rollbackFor = Exception.class)
    public int createBackfillsFromFailedOutbox() {
        List<Long> failedIds = jdbc.query(
                "SELECT event_id FROM outbox_event WHERE status='DEAD'",
                (rs, rowNum) -> rs.getLong(1));
        int created = 0;
        for (Long id : failedIds) {
            try {
                createBackfill(BackfillSourceType.OUTBOX, String.valueOf(id), null, DEFAULT_MAX_ATTEMPTS);
                created++;
            } catch (Exception ignore) {
                // 幂等冲突忽略
            }
        }
        return created;
    }

    /**
     * 从失败的 task_run 自动创建补单任务。
     *
     * @return 创建的补单任务数
     */
    @Transactional(rollbackFor = Exception.class)
    public int createBackfillsFromFailedTasks() {
        List<TaskRunRow> failed = jdbc.query(
                "SELECT task_id, business_date FROM task_run WHERE status='FAILED'",
                (rs, rowNum) -> new TaskRunRow(
                        rs.getLong("task_id"),
                        rs.getDate("business_date") == null ? null
                                : rs.getDate("business_date").toLocalDate()));
        int created = 0;
        for (TaskRunRow row : failed) {
            try {
                createBackfill(BackfillSourceType.TASK_RUN, String.valueOf(row.taskId()),
                        row.businessDate(), DEFAULT_MAX_ATTEMPTS);
                created++;
            } catch (Exception ignore) {
                // 幂等冲突忽略
            }
        }
        return created;
    }

    /**
     * 强制将 FAILED 的补单任务重置为 PENDING（人工干预）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reactivate(long backfillId) {
        jdbc.update(
                "UPDATE backfill_task SET status='PENDING', next_attempt_at=? WHERE backfill_id=? "
                        + "AND status='FAILED'",
                Timestamp.valueOf(LocalDateTime.now()), backfillId);
    }

    private long calcBackoffMinutes(int attempts) {
        long backoff = INITIAL_BACKOFF_MINUTES * (1L << Math.min(attempts - 1, 5));
        return Math.min(backoff, 1440L); // 封顶 24 小时
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    public record BackfillRow(long backfillId, String sourceType, String sourceId,
                              LocalDate businessDate, int attempts, int maxAttempts) {}

    public record TaskRunRow(long taskId, LocalDate businessDate) {}
}
