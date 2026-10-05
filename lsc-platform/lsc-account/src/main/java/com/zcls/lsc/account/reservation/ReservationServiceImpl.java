package com.zcls.lsc.account.reservation;

import com.zcls.lsc.account.enums.Bucket;
import com.zcls.lsc.account.enums.EventType;
import com.zcls.lsc.account.enums.LscEnums.DispositionType;
import com.zcls.lsc.account.enums.LscEnums.ReservationStatus;
import com.zcls.lsc.account.ledger.BucketDelta;
import com.zcls.lsc.account.ledger.LedgerCommand;
import com.zcls.lsc.account.ledger.LedgerService;
import com.zcls.lsc.common.constants.LscConstants;
import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReservationServiceImpl implements ReservationService {

    private final JdbcTemplate jdbc;
    private final LedgerService ledger;

    public ReservationServiceImpl(JdbcTemplate jdbc, LedgerService ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReservationResult reserve(long orderId, long userId, long lscUnit, LocalDateTime expiresAt) {
        if (lscUnit <= 0L || lscUnit % 100L != 0L) {
            throw new BusinessException(ErrorCode.UNIT_NOT_MULTIPLE_100, "lsc_unit=" + lscUnit);
        }

        // 幂等：相同 order 已有 ACTIVE reservation
        List<Long> existing = jdbc.query(
                "SELECT reservation_id FROM lsc_reservation WHERE order_id=? AND status='ACTIVE'",
                (rs, rowNum) -> rs.getLong(1), orderId);
        if (!existing.isEmpty()) {
            return loadReservationResult(existing.get(0));
        }

        // 锁账户行(保证并发安全)
        jdbc.queryForObject("SELECT user_id FROM lsc_account WHERE user_id=? FOR UPDATE",
                Long.class, userId);

        // FEFO 查可用批次: available_unit>0, expire_at>now, 按 expire_at, available_at, id
        LocalDateTime now = LocalDateTime.now();
        List<AvailLotRow> lots = jdbc.query(
                "SELECT available_lot_id, available_unit, expire_at FROM lsc_available_lot "
                        + "WHERE user_id=? AND available_unit>0 AND expire_at>? "
                        + "ORDER BY expire_at ASC, available_at ASC, available_lot_id ASC",
                (rs, rowNum) -> new AvailLotRow(
                        rs.getLong("available_lot_id"),
                        rs.getLong("available_unit"),
                        rs.getTimestamp("expire_at").toLocalDateTime()),
                userId, java.sql.Timestamp.valueOf(now));

        long need = lscUnit;
        List<AllocInput> allocs = new ArrayList<>();
        LocalDateTime earliestExpire = null;

        for (AvailLotRow lot : lots) {
            if (need <= 0) break;
            long take = Math.min(need, lot.availableUnit());
            if (take <= 0) continue;
            allocs.add(new AllocInput(lot.availableLotId(), take));
            need -= take;
            if (earliestExpire == null || lot.expireAt().isBefore(earliestExpire)) {
                earliestExpire = lot.expireAt();
            }
        }

        if (need > 0L) {
            throw new BusinessException(ErrorCode.RESOURCE_INSUFFICIENT,
                    "insufficient available LSC, need=" + lscUnit + ", got=" + (lscUnit - need));
        }

        // 支付截止 = min(expiresAt, 最早批次 expire_at)
        LocalDateTime effectiveExpire = earliestExpire != null && earliestExpire.isBefore(expiresAt)
                ? earliestExpire : expiresAt;

        // 创建 reservation
        long reservationId = nextId();
        jdbc.update(
                "INSERT INTO lsc_reservation(reservation_id, user_id, order_id, status, "
                        + "reserved_total_unit, expires_at, version) VALUES(?,?,?,?,?,?,?)",
                reservationId, userId, orderId, ReservationStatus.ACTIVE.name(),
                lscUnit, java.sql.Timestamp.valueOf(effectiveExpire), 0);

        // 创建 allocation + 更新 AvailableLot
        List<BucketDelta> deltas = new ArrayList<>();
        deltas.add(new BucketDelta(Bucket.AVAILABLE, -lscUnit, DispositionType.TRANSFER,
                null, null, "RES:" + reservationId));
        deltas.add(new BucketDelta(Bucket.RESERVED, lscUnit, DispositionType.TRANSFER,
                null, null, "RES:" + reservationId));

        for (AllocInput a : allocs) {
            jdbc.update(
                    "INSERT INTO lsc_reservation_allocation(allocation_id, reservation_id, available_lot_id, "
                            + "reserved_unit, captured_unit, released_unit, version) VALUES(?,?,?,?,?,?,?)",
                    nextId(), reservationId, a.lotId(), a.unit(), 0L, 0L, 0);
            // 批次投影: available -> reserved
            jdbc.update(
                    "UPDATE lsc_available_lot SET available_unit=available_unit-?, reserved_unit=reserved_unit+?, "
                            + "version=version+1 WHERE available_lot_id=?",
                    a.unit(), a.unit(), a.lotId());
        }

        // 写 RESERVE 事件
        String businessKey = "RESERVE:" + orderId;
        LedgerCommand cmd = LedgerCommand.builder()
                .userId(userId)
                .eventType(EventType.RESERVE)
                .businessKey(businessKey)
                .orderId(orderId)
                .businessDate(LocalDate.now())
                .occurredAt(now)
                .entries(deltas)
                .build();
        ledger.write(cmd);

        List<Long> lotIds = allocs.stream().map(AllocInput::lotId).toList();
        return new ReservationResult(reservationId, lscUnit, lotIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void capture(long reservationId, LocalDateTime paidAt) {
        // 查 reservation
        ReservationRow res = jdbc.queryForObject(
                "SELECT reservation_id, user_id, order_id, reserved_total_unit FROM lsc_reservation "
                        + "WHERE reservation_id=? FOR UPDATE",
                (rs, rowNum) -> new ReservationRow(
                        rs.getLong("reservation_id"),
                        rs.getLong("user_id"),
                        rs.getLong("order_id"),
                        rs.getLong("reserved_total_unit")),
                reservationId);

        // 查 allocations
        List<ResAllocRow> allocs = jdbc.query(
                "SELECT allocation_id, available_lot_id, reserved_unit, captured_unit, released_unit "
                        + "FROM lsc_reservation_allocation WHERE reservation_id=?",
                (rs, rowNum) -> new ResAllocRow(
                        rs.getLong("allocation_id"),
                        rs.getLong("available_lot_id"),
                        rs.getLong("reserved_unit"),
                        rs.getLong("captured_unit"),
                        rs.getLong("released_unit")),
                reservationId);

        // 幂等: 已 CAPTURED
        String status = jdbc.queryForObject(
                "SELECT status FROM lsc_reservation WHERE reservation_id=?", String.class, reservationId);
        if (ReservationStatus.CAPTURED.name().equals(status)) {
            return;
        }

        List<BucketDelta> deltas = new ArrayList<>();
        long totalCaptured = 0L;

        for (ResAllocRow a : allocs) {
            long toCapture = a.reservedUnit() - a.capturedUnit() - a.releasedUnit();
            if (toCapture <= 0) continue;
            // allocation captured
            jdbc.update(
                    "UPDATE lsc_reservation_allocation SET captured_unit=captured_unit+?, version=version+1 "
                            + "WHERE allocation_id=?",
                    toCapture, a.allocationId());
            // AvailableLot: reserved -> consumed
            jdbc.update(
                    "UPDATE lsc_available_lot SET reserved_unit=reserved_unit-?, consumed_unit=consumed_unit+?, "
                            + "version=version+1 WHERE available_lot_id=?",
                    toCapture, toCapture, a.availableLotId());
            // 创建 consumption_allocation
            jdbc.update(
                    "INSERT INTO lsc_consumption_allocation(consumption_id, user_id, order_unit_allocation_id, "
                            + "available_lot_id, captured_unit, returned_unit, capture_event_id, version) "
                            + "VALUES(?,?,?,?,?,?,?,?)",
                    nextId(), res.userId(), res.orderId(), a.availableLotId(), toCapture, 0L, 0L, 0);

            totalCaptured += toCapture;
        }

        deltas.add(new BucketDelta(Bucket.RESERVED, -totalCaptured, DispositionType.CONSUME,
                null, null, "RES:" + reservationId));

        // 更新 reservation 状态
        jdbc.update(
                "UPDATE lsc_reservation SET status=?, version=version+1 WHERE reservation_id=?",
                ReservationStatus.CAPTURED.name(), reservationId);

        String businessKey = "CONSUME:" + res.orderId();
        LedgerCommand cmd = LedgerCommand.builder()
                .userId(res.userId())
                .eventType(EventType.CONSUME)
                .businessKey(businessKey)
                .orderId(res.orderId())
                .businessDate(LocalDate.now())
                .occurredAt(paidAt)
                .entries(deltas)
                .build();
        ledger.write(cmd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(long reservationId) {
        ReservationRow res = jdbc.queryForObject(
                "SELECT reservation_id, user_id, order_id, reserved_total_unit FROM lsc_reservation "
                        + "WHERE reservation_id=? FOR UPDATE",
                (rs, rowNum) -> new ReservationRow(
                        rs.getLong("reservation_id"),
                        rs.getLong("user_id"),
                        rs.getLong("order_id"),
                        rs.getLong("reserved_total_unit")),
                reservationId);

        String status = jdbc.queryForObject(
                "SELECT status FROM lsc_reservation WHERE reservation_id=?", String.class, reservationId);
        if (ReservationStatus.RELEASED.name().equals(status)
                || ReservationStatus.CAPTURED.name().equals(status)) {
            return;
        }

        List<ResAllocRow> allocs = jdbc.query(
                "SELECT allocation_id, available_lot_id, reserved_unit, captured_unit, released_unit "
                        + "FROM lsc_reservation_allocation WHERE reservation_id=?",
                (rs, rowNum) -> new ResAllocRow(
                        rs.getLong("allocation_id"),
                        rs.getLong("available_lot_id"),
                        rs.getLong("reserved_unit"),
                        rs.getLong("captured_unit"),
                        rs.getLong("released_unit")),
                reservationId);

        List<BucketDelta> deltas = new ArrayList<>();
        long totalReleased = 0L;

        for (ResAllocRow a : allocs) {
            long toRelease = a.reservedUnit() - a.capturedUnit() - a.releasedUnit();
            if (toRelease <= 0) continue;
            jdbc.update(
                    "UPDATE lsc_reservation_allocation SET released_unit=released_unit+?, version=version+1 "
                            + "WHERE allocation_id=?",
                    toRelease, a.allocationId());
            // AvailableLot: reserved -> available
            jdbc.update(
                    "UPDATE lsc_available_lot SET reserved_unit=reserved_unit-?, available_unit=available_unit+?, "
                            + "version=version+1 WHERE available_lot_id=?",
                    toRelease, toRelease, a.availableLotId());
            totalReleased += toRelease;
        }

        if (totalReleased > 0) {
            deltas.add(new BucketDelta(Bucket.RESERVED, -totalReleased, DispositionType.TRANSFER,
                    null, null, "RES:" + reservationId));
            deltas.add(new BucketDelta(Bucket.AVAILABLE, totalReleased, DispositionType.TRANSFER,
                    null, null, "RES:" + reservationId));
        }

        jdbc.update(
                "UPDATE lsc_reservation SET status=?, version=version+1 WHERE reservation_id=?",
                ReservationStatus.RELEASED.name(), reservationId);

        String businessKey = "RELEASE_RES:" + res.orderId();
        LedgerCommand cmd = LedgerCommand.builder()
                .userId(res.userId())
                .eventType(EventType.RELEASE_RESERVATION)
                .businessKey(businessKey)
                .orderId(res.orderId())
                .businessDate(LocalDate.now())
                .occurredAt(LocalDateTime.now())
                .entries(deltas)
                .build();
        ledger.write(cmd);
    }

    private ReservationResult loadReservationResult(long reservationId) {
        long unit = jdbc.queryForObject(
                "SELECT reserved_total_unit FROM lsc_reservation WHERE reservation_id=?",
                Long.class, reservationId);
        List<Long> lotIds = jdbc.query(
                "SELECT available_lot_id FROM lsc_reservation_allocation WHERE reservation_id=?",
                (rs, rowNum) -> rs.getLong(1), reservationId);
        return new ReservationResult(reservationId, unit, lotIds);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    private record AvailLotRow(long availableLotId, long availableUnit, LocalDateTime expireAt) {}
    private record AllocInput(long lotId, long unit) {}
    private record ReservationRow(long reservationId, long userId, long orderId, long reservedUnit) {}
    private record ResAllocRow(long allocationId, long availableLotId, long reservedUnit,
                               long capturedUnit, long releasedUnit) {}
}
