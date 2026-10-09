package com.zcls.lsc.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 第12.7章 分布式租约服务（lease fencing）。
 *
 *  - 同一 lease_key 仅允许一个持有者；通过 SELECT ... FOR UPDATE + 版本号实现抢占
 *  - fencing_token 单调递增：旧执行者提交时发现 token 不匹配即拒绝，防止脑裂
 *  - 租约有 TTL（expires_at），持有者需定期续约（renew）；过期后其他节点可抢占
 *  - 所有租约操作在同一事务内完成，依赖 DB 行锁
 */
@Service
public class LeaseService {

    /** 默认租约 TTL：5 分钟。 */
    public static final long DEFAULT_LEASE_TTL_SECONDS = 300L;

    /** 默认续约间隔：租约 TTL 的 1/3。 */
    public static final long DEFAULT_RENEW_INTERVAL_SECONDS = 100L;

    private final JdbcTemplate jdbc;

    public LeaseService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 尝试获取租约。
     *
     * @param leaseKey   租约键
     * @param owner      持有者标识
     * @param ttlSeconds 租约 TTL（秒）
     * @return 租约结果（成功返回 fencing_token，失败返回 null）
     */
    @Transactional(rollbackFor = Exception.class)
    public LeaseResult tryAcquire(String leaseKey, String owner, long ttlSeconds) {
        if (leaseKey == null || leaseKey.isBlank()) {
            throw new IllegalArgumentException("leaseKey is required");
        }
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner is required");
        }
        LocalDateTime now = LocalDateTime.now();

        // 行锁读取（若不存在则插入）
        List<LeaseRow> rows = jdbc.query(
                "SELECT lease_owner, fencing_token, expires_at, version FROM lease "
                        + "WHERE lease_key=? FOR UPDATE",
                (rs, rowNum) -> new LeaseRow(
                        rs.getString("lease_owner"),
                        rs.getLong("fencing_token"),
                        rs.getTimestamp("expires_at").toLocalDateTime(),
                        rs.getInt("version")),
                leaseKey);

        if (rows.isEmpty()) {
            // 不存在则创建
            long fencingToken = 1L;
            jdbc.update(
                    "INSERT INTO lease(lease_key, lease_owner, fencing_token, acquired_at, "
                            + "expires_at, last_renew_at, version) VALUES(?,?,?,?,?,?,?)",
                    leaseKey, owner, fencingToken, Timestamp.valueOf(now),
                    Timestamp.valueOf(now.plus(ttlSeconds, ChronoUnit.SECONDS)),
                    Timestamp.valueOf(now), 0);
            return new LeaseResult(true, fencingToken, owner);
        }

        LeaseRow row = rows.get(0);
        boolean expired = row.expiresAt().isBefore(now);

        if (!expired && !owner.equals(row.leaseOwner())) {
            // 租约被他人持有且未过期，获取失败
            return new LeaseResult(false, row.fencingToken(), row.leaseOwner());
        }

        // 抢占：fencing_token + 1（即使是同一持有者续约也递增，保证单调）
        long newToken = row.fencingToken() + 1L;
        jdbc.update(
                "UPDATE lease SET lease_owner=?, fencing_token=?, acquired_at=?, "
                        + "expires_at=?, last_renew_at=?, version=version+1 "
                        + "WHERE lease_key=?",
                owner, newToken, Timestamp.valueOf(now),
                Timestamp.valueOf(now.plus(ttlSeconds, ChronoUnit.SECONDS)),
                Timestamp.valueOf(now), leaseKey);
        return new LeaseResult(true, newToken, owner);
    }

    /**
     * 续约租约（持有者续期）。
     *
     * @return 续约后的 fencing_token；若租约已被他人持有或不存在，返回 -1
     */
    @Transactional(rollbackFor = Exception.class)
    public long renew(String leaseKey, String owner, long ttlSeconds) {
        LocalDateTime now = LocalDateTime.now();
        List<LeaseRow> rows = jdbc.query(
                "SELECT lease_owner, fencing_token FROM lease WHERE lease_key=? FOR UPDATE",
                (rs, rowNum) -> new LeaseRow(
                        rs.getString("lease_owner"),
                        rs.getLong("fencing_token"),
                        now, rs.getInt("version")),
                leaseKey);
        if (rows.isEmpty()) {
            return -1L;
        }
        LeaseRow row = rows.get(0);
        if (!owner.equals(row.leaseOwner())) {
            return -1L; // 已被他人抢占
        }
        long newToken = row.fencingToken() + 1L;
        jdbc.update(
                "UPDATE lease SET fencing_token=?, expires_at=?, last_renew_at=?, version=version+1 "
                        + "WHERE lease_key=?",
                newToken, Timestamp.valueOf(now.plus(ttlSeconds, ChronoUnit.SECONDS)),
                Timestamp.valueOf(now), leaseKey);
        return newToken;
    }

    /**
     * 释放租约（持有者主动释放）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void release(String leaseKey, String owner) {
        int updated = jdbc.update(
                "DELETE FROM lease WHERE lease_key=? AND lease_owner=?",
                leaseKey, owner);
        if (updated == 0) {
            // 已被他人持有或已过期删除，视为释放成功
        }
    }

    /**
     * 校验当前持有者是否仍持有租约（用于执行前校验）。
     *
     * @param expectedToken 持有者获取时拿到的 fencing_token
     * @return true 表示仍持有且 token 匹配，可继续执行；false 表示租约已丢失
     */
    public boolean isHeld(String leaseKey, String owner, long expectedToken) {
        List<LeaseRow> rows = jdbc.query(
                "SELECT lease_owner, fencing_token, expires_at FROM lease WHERE lease_key=?",
                (rs, rowNum) -> new LeaseRow(
                        rs.getString("lease_owner"),
                        rs.getLong("fencing_token"),
                        rs.getTimestamp("expires_at").toLocalDateTime(),
                        0),
                leaseKey);
        if (rows.isEmpty()) return false;
        LeaseRow row = rows.get(0);
        if (!owner.equals(row.leaseOwner())) return false;
        if (row.expiresAt().isBefore(LocalDateTime.now())) return false;
        // fencing_token 必须精确匹配：若被他人抢占过 token 会更大
        return row.fencingToken() == expectedToken;
    }

    /**
     * 强制释放租约（管理端用，无视持有者）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void forceRelease(String leaseKey) {
        jdbc.update("DELETE FROM lease WHERE lease_key=?", leaseKey);
    }

    /**
     * 查询所有活跃租约（管理端巡检）。
     */
    public List<LeaseInfo> listActiveLeases() {
        return jdbc.query(
                "SELECT lease_key, lease_owner, fencing_token, acquired_at, expires_at, "
                        + "last_renew_at FROM lease ORDER BY expires_at ASC",
                (rs, rowNum) -> new LeaseInfo(
                        rs.getString("lease_key"),
                        rs.getString("lease_owner"),
                        rs.getLong("fencing_token"),
                        rs.getTimestamp("acquired_at").toLocalDateTime(),
                        rs.getTimestamp("expires_at").toLocalDateTime(),
                        rs.getTimestamp("last_renew_at").toLocalDateTime()));
    }

    public record LeaseRow(String leaseOwner, long fencingToken, LocalDateTime expiresAt, int version) {}

    public record LeaseResult(boolean acquired, long fencingToken, String owner) {}

    public record LeaseInfo(String leaseKey, String leaseOwner, long fencingToken,
                            LocalDateTime acquiredAt, LocalDateTime expiresAt, LocalDateTime lastRenewAt) {}
}
