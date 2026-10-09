package com.zcls.lsc.task;

import com.zcls.lsc.task.enums.TaskEnums.TaskStatus;
import com.zcls.lsc.task.enums.TaskEnums.TaskType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 第12.7章 CronJob 编排服务。
 *
 *  - 使用 task_run 表记录每个 (task_type, business_date) 的运行实例
 *  - 唯一键 uk_task_run_type_date 保证每日每任务仅一个主运行记录
 *  - lease_owner + fencing_token 实现租约保护：执行前获取租约，执行中校验 token
 *  - 断点续跑：cursor 记录处理进度，租约丢失后可从断点恢复
 *  - 状态流转：PENDING -> RUNNING -> SUCCEEDED/FAILED；租约丢失 -> LEASE_LOST
 */
@Service
public class CronJobService {

    /** 任务租约 TTL（秒）。 */
    public static final long TASK_LEASE_TTL_SECONDS = 300L;

    private final JdbcTemplate jdbc;
    private final LeaseService leaseService;

    public CronJobService(JdbcTemplate jdbc, LeaseService leaseService) {
        this.jdbc = jdbc;
        this.leaseService = leaseService;
    }

    /**
     * 启动一个定时任务（获取租约 + 标记 RUNNING）。
     *
     * @param taskType     任务类型
     * @param businessDate 业务日
     * @param owner        执行者标识
     * @return 任务运行信息（含 task_id、fencing_token、cursor）
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskRunInfo startTask(TaskType taskType, LocalDate businessDate, String owner) {
        if (taskType == null || businessDate == null || owner == null) {
            throw new IllegalArgumentException("taskType, businessDate, owner are required");
        }
        // 幂等：查询已有 task_run
        List<TaskRunRow> rows = jdbc.query(
                "SELECT task_id, status, lease_owner, fencing_token, cursor FROM task_run "
                        + "WHERE task_type=? AND business_date=? FOR UPDATE",
                (rs, rowNum) -> new TaskRunRow(
                        rs.getLong("task_id"),
                        rs.getString("status"),
                        rs.getString("lease_owner"),
                        rs.getLong("fencing_token"),
                        rs.getObject("cursor") == null ? null : rs.getLong("cursor")),
                taskType.name(), Date.valueOf(businessDate));

        long taskId;
        long fencingToken;
        Long cursor = null;

        if (rows.isEmpty()) {
            // 首次运行：创建 task_run
            taskId = nextId();
            String runId = taskType.name() + ":" + businessDate + ":" + System.currentTimeMillis();
            jdbc.update(
                    "INSERT INTO task_run(task_id, task_type, business_date, run_id, status, "
                            + "cursor, lease_owner, fencing_token, started_at) "
                            + "VALUES(?,?,?,?,'RUNNING',NULL,?,?,?)",
                    taskId, taskType.name(), Date.valueOf(businessDate), runId,
                    owner, 0L, Timestamp.valueOf(LocalDateTime.now()));
            fencingToken = 0L;
        } else {
            TaskRunRow row = rows.get(0);
            taskId = row.taskId();
            cursor = row.cursor();
            TaskStatus status = TaskStatus.valueOf(row.status());

            if (status == TaskStatus.SUCCEEDED) {
                // 已成功，幂等返回
                return new TaskRunInfo(taskId, fencingToken = row.fencingToken(), cursor,
                        TaskStatus.SUCCEEDED.name(), null);
            }
            if (status == TaskStatus.RUNNING) {
                // 检查租约是否仍由当前 owner 持有
                boolean held = leaseService.isHeld(leaseKey(taskType, businessDate), owner,
                        row.fencingToken());
                if (!held) {
                    // 租约已丢失，置 LEASE_LOST
                    jdbc.update(
                            "UPDATE task_run SET status='LEASE_LOST' WHERE task_id=?",
                            taskId);
                    throw new IllegalStateException(
                            "lease lost for task " + taskId + ", cannot continue");
                }
                fencingToken = row.fencingToken();
            } else {
                // PENDING / FAILED / LEASE_LOST：重新获取租约
                LeaseService.LeaseResult lr = leaseService.tryAcquire(
                        leaseKey(taskType, businessDate), owner, TASK_LEASE_TTL_SECONDS);
                if (!lr.acquired()) {
                    throw new IllegalStateException(
                            "cannot acquire lease for " + taskType + "/" + businessDate
                                    + ", held by " + lr.owner());
                }
                fencingToken = lr.fencingToken();
                jdbc.update(
                        "UPDATE task_run SET status='RUNNING', lease_owner=?, fencing_token=?, "
                                + "started_at=? WHERE task_id=?",
                        owner, fencingToken, Timestamp.valueOf(LocalDateTime.now()), taskId);
            }
        }

        return new TaskRunInfo(taskId, fencingToken, cursor, TaskStatus.RUNNING.name(), owner);
    }

    /**
     * 更新任务处理游标（断点续跑）。
     * 执行前必须校验 fencing_token 匹配。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateCursor(long taskId, long cursor, long expectedFencingToken) {
        // 校验 fencing token
        Long token = jdbc.queryForObject(
                "SELECT fencing_token FROM task_run WHERE task_id=?", Long.class, taskId);
        if (token == null || token != expectedFencingToken) {
            throw new IllegalStateException(
                    "fencing token mismatch: expected " + expectedFencingToken
                            + " actual " + token);
        }
        jdbc.update(
                "UPDATE task_run SET cursor=? WHERE task_id=?",
                cursor, taskId);
    }

    /**
     * 标记任务成功完成并释放租约。
     */
    @Transactional(rollbackFor = Exception.class)
    public void finishTask(long taskId, TaskType taskType, LocalDate businessDate, String owner) {
        jdbc.update(
                "UPDATE task_run SET status='SUCCEEDED', finished_at=? WHERE task_id=?",
                Timestamp.valueOf(LocalDateTime.now()), taskId);
        leaseService.release(leaseKey(taskType, businessDate), owner);
    }

    /**
     * 标记任务失败并释放租约。
     */
    @Transactional(rollbackFor = Exception.class)
    public void failTask(long taskId, TaskType taskType, LocalDate businessDate,
                         String owner, String error) {
        jdbc.update(
                "UPDATE task_run SET status='FAILED', finished_at=?, error_summary=? WHERE task_id=?",
                Timestamp.valueOf(LocalDateTime.now()),
                error == null ? null : error.substring(0, Math.min(error.length(), 1024)),
                taskId);
        leaseService.release(leaseKey(taskType, businessDate), owner);
    }

    /**
     * 续租任务租约（长任务定期调用）。
     *
     * @return 新的 fencing_token；若租约丢失返回 -1
     */
    @Transactional(rollbackFor = Exception.class)
    public long renewTaskLease(TaskType taskType, LocalDate businessDate, String owner) {
        return leaseService.renew(leaseKey(taskType, businessDate), owner, TASK_LEASE_TTL_SECONDS);
    }

    /**
     * 查询任务运行状态。
     */
    public TaskRunInfo getTaskRun(TaskType taskType, LocalDate businessDate) {
        List<TaskRunInfo> rows = jdbc.query(
                "SELECT task_id, fencing_token, cursor, status, lease_owner FROM task_run "
                        + "WHERE task_type=? AND business_date=?",
                (rs, rowNum) -> new TaskRunInfo(
                        rs.getLong("task_id"),
                        rs.getLong("fencing_token"),
                        rs.getObject("cursor") == null ? null : rs.getLong("cursor"),
                        rs.getString("status"),
                        rs.getString("lease_owner")),
                taskType.name(), Date.valueOf(businessDate));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 查询所有未完成的任务（巡检用）。
     */
    public List<TaskRunInfo> findRunningTasks() {
        return jdbc.query(
                "SELECT task_id, fencing_token, cursor, status, lease_owner FROM task_run "
                        + "WHERE status IN ('PENDING','RUNNING') ORDER BY started_at ASC",
                (rs, rowNum) -> new TaskRunInfo(
                        rs.getLong("task_id"),
                        rs.getLong("fencing_token"),
                        rs.getObject("cursor") == null ? null : rs.getLong("cursor"),
                        rs.getString("status"),
                        rs.getString("lease_owner")));
    }

    private String leaseKey(TaskType taskType, LocalDate businessDate) {
        return "task:" + taskType.name() + ":" + businessDate;
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    public record TaskRunRow(long taskId, String status, String leaseOwner,
                             long fencingToken, Long cursor) {}

    public record TaskRunInfo(long taskId, long fencingToken, Long cursor,
                              String status, String leaseOwner) {}
}
