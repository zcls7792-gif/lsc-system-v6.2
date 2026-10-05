package com.zcls.lsc.account.reconcile;

import com.zcls.lsc.account.enums.Bucket;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 第6.5章 对账服务。
 *
 * 日终核对：
 *  - 订单赠送、Lot、账户、追偿四方
 *  - 已核销 LSC 必须有成功支付或经批准的纠错事实
 *  - 账户各桶 = 所属批次余额之和
 *  - 单用户不平账隔离该账户权益写入并排查
 *  - 平台快照数据不完整或全局规则错误阻断全平台释放
 */
@Service
public class ReconciliationService {

    private final JdbcTemplate jdbc;

    public ReconciliationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 对账某用户的账户投影与批次汇总是否一致。
     *
     * @param userId 用户ID
     * @return 对账差异列表
     */
    @Transactional(readOnly = true)
    public List<Difference> reconcileUserAccount(long userId) {
        List<Difference> diffs = new ArrayList<>();

        // 账户投影
        long[] account = jdbc.queryForObject(
                "SELECT locked_unit, available_unit, reserved_unit, frozen_locked_unit, frozen_available_unit "
                        + "FROM lsc_account WHERE user_id=?",
                (rs, rowNum) -> new long[]{
                        rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)
                }, userId);

        // GrantLot 汇总: remaining_locked + frozen_locked
        long[] grant = jdbc.queryForObject(
                "SELECT COALESCE(SUM(remaining_locked_unit),0), COALESCE(SUM(frozen_locked_unit),0) "
                        + "FROM lsc_grant_lot WHERE user_id=?",
                (rs, rowNum) -> new long[]{rs.getLong(1), rs.getLong(2)}, userId);

        // AvailableLot 汇总: available + reserved + frozen
        long[] avail = jdbc.queryForObject(
                "SELECT COALESCE(SUM(available_unit),0), COALESCE(SUM(reserved_unit),0), "
                        + "COALESCE(SUM(frozen_unit),0) FROM lsc_available_lot WHERE user_id=?",
                (rs, rowNum) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)}, userId);

        checkDiff(diffs, userId, Bucket.LOCKED, account[0], grant[0]);
        checkDiff(diffs, userId, Bucket.FROZEN_LOCKED, account[3], grant[1]);
        checkDiff(diffs, userId, Bucket.AVAILABLE, account[1], avail[0]);
        checkDiff(diffs, userId, Bucket.RESERVED, account[2], avail[1]);
        checkDiff(diffs, userId, Bucket.FROZEN_AVAILABLE, account[4], avail[2]);

        return diffs;
    }

    /**
     * 对账 GrantLot 恒等式: original = remaining + frozen + released + revoked。
     */
    @Transactional(readOnly = true)
    public List<Difference> reconcileGrantLotIdentities(long userId) {
        List<Difference> diffs = new ArrayList<>();
        jdbc.query(
                "SELECT grant_lot_id, original_grant_unit, remaining_locked_unit, frozen_locked_unit, "
                        + "released_total_unit, revoked_locked_unit FROM lsc_grant_lot WHERE user_id=?",
                rs -> {
                    long original = rs.getLong("original_grant_unit");
                    long sum = rs.getLong("remaining_locked_unit") + rs.getLong("frozen_locked_unit")
                            + rs.getLong("released_total_unit") + rs.getLong("revoked_locked_unit");
                    if (original != sum) {
                        diffs.add(new Difference(userId, "GRANT_LOT", rs.getLong("grant_lot_id"),
                                original, sum, "identity"));
                    }
                }, userId);
        return diffs;
    }

    /**
     * 对账 AvailableLot 恒等式: issued + restored = available + reserved + frozen + consumed + expired + revoked。
     */
    @Transactional(readOnly = true)
    public List<Difference> reconcileAvailableLotIdentities(long userId) {
        List<Difference> diffs = new ArrayList<>();
        jdbc.query(
                "SELECT available_lot_id, issued_unit, restored_unit, available_unit, reserved_unit, "
                        + "frozen_unit, consumed_unit, expired_unit, revoked_unit "
                        + "FROM lsc_available_lot WHERE user_id=?",
                rs -> {
                    long left = rs.getLong("issued_unit") + rs.getLong("restored_unit");
                    long right = rs.getLong("available_unit") + rs.getLong("reserved_unit")
                            + rs.getLong("frozen_unit") + rs.getLong("consumed_unit")
                            + rs.getLong("expired_unit") + rs.getLong("revoked_unit");
                    if (left != right) {
                        diffs.add(new Difference(userId, "AVAILABLE_LOT", rs.getLong("available_lot_id"),
                                left, right, "identity"));
                    }
                }, userId);
        return diffs;
    }

    /**
     * 对账追偿恒等式: required = recovered + satisfied_by_expiry + pending。
     */
    @Transactional(readOnly = true)
    public List<Difference> reconcileRecoveryIdentities(long userId) {
        List<Difference> diffs = new ArrayList<>();
        jdbc.query(
                "SELECT recovery_id, required_unit, recovered_unit, satisfied_by_expiry_unit, pending_unit "
                        + "FROM lsc_recovery WHERE user_id=?",
                rs -> {
                    long required = rs.getLong("required_unit");
                    long sum = rs.getLong("recovered_unit") + rs.getLong("satisfied_by_expiry_unit")
                            + rs.getLong("pending_unit");
                    if (required != sum) {
                        diffs.add(new Difference(userId, "RECOVERY", rs.getLong("recovery_id"),
                                required, sum, "identity"));
                    }
                }, userId);
        return diffs;
    }

    /**
     * 对账订单行撤回恒等式: clawback_required = clawback_completed + clawback_pending。
     */
    @Transactional(readOnly = true)
    public List<Difference> reconcileOrderItemClawback(long orderId) {
        List<Difference> diffs = new ArrayList<>();
        jdbc.query(
                "SELECT item_id, clawback_required_unit, clawback_completed_unit, clawback_pending_unit "
                        + "FROM order_item WHERE order_id=?",
                rs -> {
                    long required = rs.getLong("clawback_required_unit");
                    long sum = rs.getLong("clawback_completed_unit") + rs.getLong("clawback_pending_unit");
                    if (required != sum) {
                        diffs.add(new Difference(0L, "ORDER_ITEM", rs.getLong("item_id"),
                                required, sum, "clawback_identity"));
                    }
                }, orderId);
        return diffs;
    }

    private void checkDiff(List<Difference> diffs, long userId, Bucket bucket,
                           long expected, long actual) {
        if (expected != actual) {
            diffs.add(new Difference(userId, "ACCOUNT_" + bucket.name(), userId, expected, actual, "bucket"));
        }
    }

    public record Difference(long userId, String entityType, long entityId,
                             long expectedValue, long actualValue, String category) {}
}
