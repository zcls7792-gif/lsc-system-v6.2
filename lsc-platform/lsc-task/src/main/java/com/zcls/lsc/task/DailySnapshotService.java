package com.zcls.lsc.task;

import com.zcls.lsc.task.enums.TaskEnums.SnapshotType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * 第12.7章 日终快照服务。
 *
 *  - 每日业务日 D 对各用户账户五桶余额进行封账快照
 *  - 唯一键 (business_date, snapshot_type, user_id) 保证幂等：重复跑不重复写入
 *  - snapshot_hash 对五桶余额 + last_event_seq 做 SHA-256，防篡改
 *  - 快照仅 INSERT，不 UPDATE；历史快照不可修改
 */
@Service
public class DailySnapshotService {

    private final JdbcTemplate jdbc;

    public DailySnapshotService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 为指定业务日生成所有活跃用户的账户快照。
     *
     * @param businessDate 业务日
     * @return 快照记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int snapshotAccountsForDay(LocalDate businessDate) {
        if (businessDate == null) {
            throw new IllegalArgumentException("businessDate is required");
        }
        List<AccountRow> accounts = jdbc.query(
                "SELECT user_id, locked_unit, available_unit, reserved_unit, "
                        + "frozen_locked_unit, frozen_available_unit, last_event_seq "
                        + "FROM lsc_account",
                (rs, rowNum) -> new AccountRow(
                        rs.getLong("user_id"),
                        rs.getLong("locked_unit"),
                        rs.getLong("available_unit"),
                        rs.getLong("reserved_unit"),
                        rs.getLong("frozen_locked_unit"),
                        rs.getLong("frozen_available_unit"),
                        rs.getLong("last_event_seq")));

        int count = 0;
        for (AccountRow acc : accounts) {
            long snapshotId = nextId();
            String hash = hash(acc);
            try {
                jdbc.update(
                        "INSERT INTO daily_snapshot(snapshot_id, business_date, snapshot_type, "
                                + "user_id, locked_unit, available_unit, reserved_unit, "
                                + "frozen_locked_unit, frozen_available_unit, last_event_seq, "
                                + "snapshot_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                        snapshotId, Date.valueOf(businessDate), SnapshotType.ACCOUNT.name(),
                        acc.userId(), acc.lockedUnit(), acc.availableUnit(), acc.reservedUnit(),
                        acc.frozenLockedUnit(), acc.frozenAvailableUnit(), acc.lastEventSeq(), hash);
                count++;
            } catch (DuplicateKeyException e) {
                // 已存在该日该用户的快照，幂等跳过
            }
        }
        return count;
    }

    /**
     * 查询指定业务日某用户的账户快照。
     */
    public SnapshotRow getSnapshot(LocalDate businessDate, long userId) {
        List<SnapshotRow> rows = jdbc.query(
                "SELECT snapshot_id, business_date, user_id, locked_unit, available_unit, "
                        + "reserved_unit, frozen_locked_unit, frozen_available_unit, "
                        + "last_event_seq, snapshot_hash FROM daily_snapshot "
                        + "WHERE business_date=? AND snapshot_type='ACCOUNT' AND user_id=?",
                (rs, rowNum) -> new SnapshotRow(
                        rs.getLong("snapshot_id"),
                        rs.getDate("business_date").toLocalDate(),
                        rs.getLong("user_id"),
                        rs.getLong("locked_unit"),
                        rs.getLong("available_unit"),
                        rs.getLong("reserved_unit"),
                        rs.getLong("frozen_locked_unit"),
                        rs.getLong("frozen_available_unit"),
                        rs.getLong("last_event_seq"),
                        rs.getString("snapshot_hash")),
                Date.valueOf(businessDate), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 校验快照是否被篡改（对比当前账户余额与快照哈希）。
     *
     * @return true 表示快照完整未篡改
     */
    public boolean verifySnapshot(LocalDate businessDate, long userId) {
        SnapshotRow snap = getSnapshot(businessDate, userId);
        if (snap == null) return false;
        // 注意：日终快照是封账时刻的余额，当前余额可能已变化，
        // 此处仅校验快照自身哈希是否与字段一致，不对比当前余额
        String rehash = hash(new AccountRow(
                snap.userId(), snap.lockedUnit(), snap.availableUnit(), snap.reservedUnit(),
                snap.frozenLockedUnit(), snap.frozenAvailableUnit(), snap.lastEventSeq()));
        return rehash.equals(snap.snapshotHash());
    }

    private String hash(AccountRow acc) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String s = acc.userId() + "|" + acc.lockedUnit() + "|" + acc.availableUnit()
                    + "|" + acc.reservedUnit() + "|" + acc.frozenLockedUnit()
                    + "|" + acc.frozenAvailableUnit() + "|" + acc.lastEventSeq();
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    public record AccountRow(long userId, long lockedUnit, long availableUnit, long reservedUnit,
                             long frozenLockedUnit, long frozenAvailableUnit, long lastEventSeq) {}

    public record SnapshotRow(long snapshotId, LocalDate businessDate, long userId,
                              long lockedUnit, long availableUnit, long reservedUnit,
                              long frozenLockedUnit, long frozenAvailableUnit,
                              long lastEventSeq, String snapshotHash) {}
}
