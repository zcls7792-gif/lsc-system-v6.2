package com.zcls.lsc.account.refund;

import com.zcls.lsc.account.enums.Bucket;
import com.zcls.lsc.account.enums.EventType;
import com.zcls.lsc.account.enums.LscEnums.*;
import com.zcls.lsc.account.ledger.BucketDelta;
import com.zcls.lsc.account.ledger.LedgerCommand;
import com.zcls.lsc.account.ledger.LedgerService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RefundServiceImpl implements RefundService {

    private final JdbcTemplate jdbc;
    private final LedgerService ledger;

    public RefundServiceImpl(JdbcTemplate jdbc, LedgerService ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundBenefitResult processRefundBenefit(long refundId, long orderId, long userId,
                                                    LocalDateTime refundAt, LocalDate businessDate) {
        // 锁账户
        jdbc.queryForObject("SELECT user_id FROM lsc_account WHERE user_id=? FOR UPDATE",
                Long.class, userId);

        // 查退款分配
        List<RefundAllocRow> allocs = jdbc.query(
                "SELECT ra.alloc_id, ra.order_unit_allocation_id, ra.rmb_cent, ra.lsc_unit, "
                        + "ra.grant_target_delta_unit, "
                        + "ua.sale_cent, ua.rmb_cent AS unit_rmb_cent, ua.grant_unit, ua.refunded_rmb_cent, "
                        + "oi.cost_snapshot_enc "
                        + "FROM refund_allocation ra "
                        + "JOIN order_unit_allocation ua ON ra.order_unit_allocation_id=ua.allocation_id "
                        + "JOIN order_item oi ON ua.item_id=oi.item_id "
                        + "WHERE ra.refund_id=?",
                (rs, rowNum) -> new RefundAllocRow(
                        rs.getLong("alloc_id"),
                        rs.getLong("order_unit_allocation_id"),
                        rs.getLong("rmb_cent"),
                        rs.getLong("lsc_unit"),
                        rs.getLong("grant_target_delta_unit"),
                        rs.getLong("sale_cent"),
                        rs.getLong("unit_rmb_cent"),
                        rs.getLong("grant_unit"),
                        rs.getLong("refunded_rmb_cent")),
                refundId);

        long totalRestored = 0L;
        long totalRevoked = 0L;
        long totalPending = 0L;

        for (RefundAllocRow a : allocs) {
            // 1. 返还本单已核销 LSC
            if (a.lscUnit() > 0L) {
                long restored = restoreConsumed(refundId, userId, a, refundAt, businessDate);
                totalRestored += restored;
            }

            // 2. 撤回赠送
            if (a.grantTargetDeltaUnit() > 0L) {
                RevokeResult r = revokeGrant(refundId, userId, a, businessDate);
                totalRevoked += r.revoked();
                totalPending += r.pending();
            }
        }

        // 更新 refund_order.benefit_status
        jdbc.update("UPDATE refund_order SET benefit_status='DONE' WHERE refund_id=?", refundId);

        return new RefundBenefitResult(refundId, totalRestored, totalRevoked, totalPending);
    }

    /**
     * 返还本单已核销 LSC（第7.4章）。
     * 原批次未到期 -> 返还原批次保留原到期日；已到期 -> 新建 REFUND_RESTORE 批次 30 天有效。
     */
    private long restoreConsumed(long refundId, long userId, RefundAllocRow alloc,
                                 LocalDateTime refundAt, LocalDate businessDate) {
        long toRestore = alloc.lscUnit();
        if (toRestore <= 0) return 0L;

        // 查该单元的消费分配
        List<ConsumptionRow> consumptions = jdbc.query(
                "SELECT consumption_id, available_lot_id, captured_unit, returned_unit "
                        + "FROM lsc_consumption_allocation WHERE order_unit_allocation_id=?",
                (rs, rowNum) -> new ConsumptionRow(
                        rs.getLong("consumption_id"),
                        rs.getLong("available_lot_id"),
                        rs.getLong("captured_unit"),
                        rs.getLong("returned_unit")),
                alloc.unitAllocationId());

        long restored = 0L;
        List<BucketDelta> deltas = new ArrayList<>();

        for (ConsumptionRow c : consumptions) {
            long canReturn = c.capturedUnit() - c.returnedUnit();
            long take = Math.min(canReturn, toRestore - restored);
            if (take <= 0) break;

            // 查原批次到期时间
            LocalDateTime expireAt = jdbc.queryForObject(
                    "SELECT expire_at FROM lsc_available_lot WHERE available_lot_id=?",
                    rs -> rs.getTimestamp("expire_at").toLocalDateTime(),
                    c.availableLotId());

            if (expireAt.isAfter(refundAt)) {
                // 原批次未到期：返还原批次，保留原到期日
                jdbc.update(
                        "UPDATE lsc_available_lot SET restored_unit=restored_unit+?, "
                                + "available_unit=available_unit+?, consumed_unit=consumed_unit-?, "
                                + "version=version+1 WHERE available_lot_id=?",
                        take, take, take, c.availableLotId());
                deltas.add(new BucketDelta(Bucket.AVAILABLE, take, DispositionType.RETURN,
                        null, c.availableLotId(), "REFUND:" + refundId));
            } else {
                // 已到期：新建 REFUND_RESTORE 批次，30天有效
                long newLotId = nextId();
                LocalDateTime newExpire = refundAt.plusDays(30L);
                jdbc.update(
                        "INSERT INTO lsc_available_lot(available_lot_id, user_id, source_grant_lot_id, "
                                + "origin_type, source_event_id, lot_sequence, issued_unit, restored_unit, "
                                + "available_unit, reserved_unit, frozen_unit, consumed_unit, expired_unit, "
                                + "revoked_unit, available_at, expire_at, version) "
                                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        newLotId, userId, 0L, OriginType.REFUND_RESTORE.name(), 0L, 1L,
                        0L, take, take, 0L, 0L, 0L, 0L, 0L,
                        java.sql.Timestamp.valueOf(refundAt), java.sql.Timestamp.valueOf(newExpire), 0);
                deltas.add(new BucketDelta(Bucket.AVAILABLE, take, DispositionType.RETURN,
                        null, newLotId, "REFUND:" + refundId));
            }

            // 更新 consumption returned
            jdbc.update("UPDATE lsc_consumption_allocation SET returned_unit=returned_unit+? "
                    + "WHERE consumption_id=?", take, c.consumptionId());

            // 写 return_allocation
            jdbc.update(
                    "INSERT INTO lsc_return_allocation(return_alloc_id, refund_id, consumption_id, "
                            + "target_available_lot_id, returned_unit, return_event_id) "
                            + "VALUES(?,?,?,?,?,?)",
                    nextId(), refundId, c.consumptionId(), c.availableLotId(), take, 0L);

            restored += take;
        }

        if (!deltas.isEmpty()) {
            String businessKey = "REFUND_RETURN:" + refundId + ":" + alloc.unitAllocationId();
            LedgerCommand cmd = LedgerCommand.builder()
                    .userId(userId)
                    .eventType(EventType.REFUND_RETURN)
                    .businessKey(businessKey)
                    .refundId(refundId)
                    .businessDate(businessDate)
                    .occurredAt(refundAt)
                    .entries(deltas)
                    .build();
            ledger.write(cmd);
        }

        return restored;
    }

    /**
     * 撤回赠送（第7.5章）。
     * 撤回顺序：
     *  1. 源 GrantLot 已释放出的 AvailableLot 中已过期的 -> satisfied_by_expiry
     *  2. 源 GrantLot 未冻结锁定 remaining_locked -> 扣减
     *  3. 全账户未占用可用 FEFO -> 扣减
     *  4. 风险冻结(同源冻结锁定 -> 冻结可用 FEFO) -> 扣减
     *  5. 不足挂待追偿
     */
    private RevokeResult revokeGrant(long refundId, long userId, RefundAllocRow alloc,
                                     LocalDate businessDate) {
        long toRevoke = alloc.grantTargetDeltaUnit();
        if (toRevoke <= 0) return new RevokeResult(0L, 0L);

        // 查源 GrantLot
        Long grantLotId = jdbc.queryForObject(
                "SELECT grant_lot_id FROM order_item WHERE item_id=("
                        + "SELECT item_id FROM order_unit_allocation WHERE allocation_id=?)",
                Long.class, alloc.unitAllocationId());
        if (grantLotId == null) return new RevokeResult(0L, toRevoke);

        long remaining = toRevoke;
        long revoked = 0L;
        long satisfiedByExpiry = 0L;
        List<BucketDelta> deltas = new ArrayList<>();

        // 1. 源批次自然到期抵充（已过期的 AvailableLot）
        List<Long> expiredLotIds = jdbc.query(
                "SELECT available_lot_id FROM lsc_available_lot "
                        + "WHERE source_grant_lot_id=? AND expire_at<=NOW() AND available_unit>0 "
                        + "ORDER BY expire_at ASC",
                (rs, rowNum) -> rs.getLong(1), grantLotId);
        for (Long lotId : expiredLotIds) {
            if (remaining <= 0) break;
            Long avail = jdbc.queryForObject(
                    "SELECT available_unit FROM lsc_available_lot WHERE available_lot_id=?",
                    Long.class, lotId);
            if (avail == null || avail <= 0) continue;
            long take = Math.min(avail, remaining);
            jdbc.update("UPDATE lsc_available_lot SET available_unit=available_unit-?, "
                    + "expired_unit=expired_unit+?, version=version+1 WHERE available_lot_id=?",
                    take, take, lotId);
            satisfiedByExpiry += take;
            remaining -= take;
        }

        // 2. 源 GrantLot 未冻结锁定
        if (remaining > 0) {
            Long locked = jdbc.queryForObject(
                    "SELECT remaining_locked_unit FROM lsc_grant_lot WHERE grant_lot_id=?",
                    Long.class, grantLotId);
            if (locked != null && locked > 0) {
                long take = Math.min(locked, remaining);
                jdbc.update("UPDATE lsc_grant_lot SET remaining_locked_unit=remaining_locked_unit-?, "
                        + "revoked_locked_unit=revoked_locked_unit+?, version=version+1 WHERE grant_lot_id=?",
                        take, take, grantLotId);
                deltas.add(new BucketDelta(Bucket.LOCKED, -take, DispositionType.REVOKE,
                        grantLotId, null, "REFUND:" + refundId));
                remaining -= take;
                revoked += take;
            }
        }

        // 3. 全账户未占用可用 FEFO
        if (remaining > 0) {
            List<AvailForRevoke> lots = jdbc.query(
                    "SELECT available_lot_id, available_unit FROM lsc_available_lot "
                            + "WHERE user_id=? AND available_unit>0 AND reserved_unit=0 "
                            + "ORDER BY expire_at ASC, available_at ASC, available_lot_id ASC",
                    (rs, rowNum) -> new AvailForRevoke(rs.getLong(1), rs.getLong(2)),
                    userId);
            for (AvailForRevoke lot : lots) {
                if (remaining <= 0) break;
                long take = Math.min(lot.availableUnit(), remaining);
                jdbc.update("UPDATE lsc_available_lot SET available_unit=available_unit-?, "
                        + "revoked_unit=revoked_unit+?, version=version+1 WHERE available_lot_id=?",
                        take, take, lot.availableLotId());
                deltas.add(new BucketDelta(Bucket.AVAILABLE, -take, DispositionType.REVOKE,
                        null, lot.availableLotId(), "REFUND:" + refundId));
                remaining -= take;
                revoked += take;
            }
        }

        // 4. 风险冻结(简化: 先扣冻结可用)
        if (remaining > 0) {
            List<AvailForRevoke> frozen = jdbc.query(
                    "SELECT available_lot_id, frozen_unit FROM lsc_available_lot "
                            + "WHERE user_id=? AND frozen_unit>0 "
                            + "ORDER BY expire_at ASC, available_at ASC, available_lot_id ASC",
                    (rs, rowNum) -> new AvailForRevoke(rs.getLong(1), rs.getLong(2)),
                    userId);
            for (AvailForRevoke lot : frozen) {
                if (remaining <= 0) break;
                long take = Math.min(lot.availableUnit(), remaining);
                jdbc.update("UPDATE lsc_available_lot SET frozen_unit=frozen_unit-?, "
                        + "revoked_unit=revoked_unit+?, version=version+1 WHERE available_lot_id=?",
                        take, take, lot.availableLotId());
                deltas.add(new BucketDelta(Bucket.FROZEN_AVAILABLE, -take, DispositionType.REVOKE,
                        null, lot.availableLotId(), "REFUND:" + refundId));
                remaining -= take;
                revoked += take;
            }
        }

        // 写撤回事件
        if (!deltas.isEmpty()) {
            String businessKey = "GRANT_REVOKE:" + refundId + ":" + alloc.unitAllocationId();
            LedgerCommand cmd = LedgerCommand.builder()
                    .userId(userId)
                    .eventType(EventType.GRANT_REVOKE)
                    .businessKey(businessKey)
                    .refundId(refundId)
                    .businessDate(businessDate)
                    .occurredAt(LocalDateTime.now())
                    .entries(deltas)
                    .build();
            ledger.write(cmd);
        }

        // 5. 不足挂待追偿
        long pending = remaining;
        if (pending > 0) {
            // 查/建 recovery
            Long itemId = jdbc.queryForObject(
                    "SELECT item_id FROM order_unit_allocation WHERE allocation_id=?",
                    Long.class, alloc.unitAllocationId());
            Long recoveryId = jdbc.query(
                    "SELECT recovery_id FROM lsc_recovery WHERE user_id=? AND source_item_id=?",
                    (rs, rowNum) -> rs.getLong(1), userId, itemId)
                    .stream().findFirst().orElse(null);

            if (recoveryId == null) {
                recoveryId = nextId();
                jdbc.update(
                        "INSERT INTO lsc_recovery(recovery_id, user_id, source_item_id, required_unit, "
                                + "recovered_unit, satisfied_by_expiry_unit, pending_unit, status, opened_at) "
                                + "VALUES(?,?,?,?,?,?,?,?,?)",
                        recoveryId, userId, itemId, toRevoke, revoked, satisfiedByExpiry, pending,
                        RecoveryStatus.OPEN.name(), java.sql.Timestamp.valueOf(LocalDateTime.now()));
            } else {
                jdbc.update(
                        "UPDATE lsc_recovery SET required_unit=required_unit+?, "
                                + "recovered_unit=recovered_unit+?, satisfied_by_expiry_unit=satisfied_by_expiry_unit+?, "
                                + "pending_unit=pending_unit+? WHERE recovery_id=?",
                        toRevoke, revoked, satisfiedByExpiry, pending, recoveryId);
            }
        }

        return new RevokeResult(revoked, pending);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    private record RefundAllocRow(long allocId, long unitAllocationId, long rmbCent, long lscUnit,
                                  long grantTargetDeltaUnit, long saleCent, long unitRmbCent,
                                  long grantUnit, long refundedRmbCent) {}
    private record ConsumptionRow(long consumptionId, long availableLotId, long capturedUnit, long returnedUnit) {}
    private record AvailForRevoke(long availableLotId, long availableUnit) {}
    private record RevokeResult(long revoked, long pending) {}
}
