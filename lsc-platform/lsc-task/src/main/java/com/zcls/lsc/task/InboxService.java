package com.zcls.lsc.task;

import com.zcls.lsc.task.enums.TaskEnums.InboxStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 第11.2章 事务消息 Inbox 服务（消费端）。
 *
 *  - 消费端先写入 inbox_event（主键 consumer_name + event_id），DB 唯一约束去重
 *  - 写入成功表示首次消费；写入冲突表示已消费过，直接跳过
 *  - 处理逻辑在 inbox 写入之后执行（处理成功置 PROCESSED，失败置 FAILED）
 *  - FAILED 的 inbox 记录由 backfill 任务重试
 */
@Service
public class InboxService {

    private final JdbcTemplate jdbc;

    public InboxService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 尝试登记消费（幂等）。
     *
     * @return true 表示首次消费（应执行业务处理）；false 表示已消费过（跳过）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean tryConsume(String consumerName, long eventId, String requestHash) {
        try {
            jdbc.update(
                    "INSERT INTO inbox_event(consumer_name, event_id, request_hash, status, "
                            + "processed_at) VALUES(?,?,?,'PENDING',NULL)",
                    consumerName, eventId, requestHash);
            return true;
        } catch (DuplicateKeyException e) {
            return false; // 已消费过
        }
    }

    /**
     * 标记消费成功。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markProcessed(String consumerName, long eventId) {
        int updated = jdbc.update(
                "UPDATE inbox_event SET status='PROCESSED', processed_at=? "
                        + "WHERE consumer_name=? AND event_id=?",
                Timestamp.valueOf(LocalDateTime.now()), consumerName, eventId);
        if (updated == 0) {
            throw new IllegalStateException("inbox record not found: " + consumerName + "/" + eventId);
        }
    }

    /**
     * 标记消费失败（由 backfill 重试）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String consumerName, long eventId) {
        jdbc.update(
                "UPDATE inbox_event SET status='FAILED' "
                        + "WHERE consumer_name=? AND event_id=?",
                consumerName, eventId);
    }

    /**
     * 查询 FAILED 的 inbox 记录（backfill 任务调用）。
     */
    public List<InboxRow> findFailed(int limit) {
        return jdbc.query(
                "SELECT consumer_name, event_id, request_hash FROM inbox_event "
                        + "WHERE status='FAILED' ORDER BY created_at ASC LIMIT ?",
                (rs, rowNum) -> new InboxRow(
                        rs.getString("consumer_name"),
                        rs.getLong("event_id"),
                        rs.getString("request_hash")),
                limit);
    }

    /**
     * 重置 FAILED 为 PENDING（backfill 重试）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void requeue(String consumerName, long eventId) {
        jdbc.update(
                "UPDATE inbox_event SET status='PENDING', processed_at=NULL "
                        + "WHERE consumer_name=? AND event_id=? AND status='FAILED'",
                consumerName, eventId);
    }

    public record InboxRow(String consumerName, long eventId, String requestHash) {}
}
