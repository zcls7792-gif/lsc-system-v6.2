package com.zcls.lsc.account.release;

import com.zcls.lsc.account.enums.Bucket;
import com.zcls.lsc.account.enums.EventType;
import com.zcls.lsc.account.enums.LscEnums.DispositionType;
import com.zcls.lsc.account.enums.LscEnums.GrantLotState;
import com.zcls.lsc.account.enums.LscEnums.OriginType;
import com.zcls.lsc.account.ledger.BucketDelta;
import com.zcls.lsc.account.ledger.LedgerCommand;
import com.zcls.lsc.account.ledger.LedgerService;
import com.zcls.lsc.common.time.BusinessDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 第5.1 / 5.4 章 日释放服务。
 *
 * 唯一释放口：GrantLot 的 original_grant_unit 为基数，最多释放剩余未冻结锁定量。
 * 余数累计 remainder_nano_unit；封顶于 remaining_locked_unit。
 * 赠送次日起参与释放（first_release_date = grant_business_date + 1）。
 */
@Service
public class ReleaseService {

    private final JdbcTemplate jdbc;
    private final ReleaseCalculator calculator;
    private final LedgerService ledger;

    public ReleaseService(JdbcTemplate jdbc, ReleaseCalculator calculator, LedgerService ledger) {
        this.jdbc = jdbc;
        this.calculator = calculator;
        this.ledger = ledger;
    }

    /**
     * 执行某日的全部释放。
     *
     * @param businessDate 业务日 D
     * @param ratePpb      当日释放率 ppb
     * @param snapshotVersion 快照版本
     */
    @Transactional(rollbackFor = Exception.class)
    public ReleaseSummary releaseForDay(LocalDate businessDate, long ratePpb, long snapshotVersion) {
        // 查当日需要释放的 GrantLot: state=ACTIVE, first_release_date<=businessDate,
        // refund_hold=0, remaining_locked_unit>0
        List<GrantLotRow> lots = jdbc.query(
                "SELECT grant_lot_id, user_id, original_grant_unit, remaining_locked_unit, "
                        + "frozen_locked_unit, remainder_nano_unit, last_processed_date "
                        + "FROM lsc_grant_lot WHERE state='ACTIVE' AND refund_hold=0 "
                        + "AND remaining_locked_unit > 0 AND first_release_date <= ?",
                (rs, rowNum) -> new GrantLotRow(
                        rs.getLong("grant_lot_id"),
                        rs.getLong("user_id"),
                        rs.getLong("original_grant_unit"),
                        rs.getLong("remaining_locked_unit"),
                        rs.getLong("frozen_locked_unit"),
                        rs.getLong("remainder_nano_unit"),
                        rs.getDate("last_processed_date") == null ? null
                                : rs.getDate("last_processed_date").toLocalDate()),
                java.sql.Date.valueOf(businessDate));

        long totalReleased = 0L;
        int processed = 0;
        int skipped = 0;

        for (GrantLotRow lot : lots) {
            // 幂等：当日已处理过则跳过（release_lot_result 唯一键保证）
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM release_lot_result WHERE grant_lot_id=? AND business_date=?",
                    Integer.class, lot.grantLotId(), java.sql.Date.valueOf(businessDate));
            if (exists != null && exists > 0) {
                skipped++;
                continue;
            }

            // 计算释放量
            ReleaseCalculator.ReleaseResult r = calculator.calc(
                    lot.originalGrantUnit(), ratePpb, lot.remainderNanoUnit(), lot.remainingLockedUnit());

            long releaseUnit = r.releaseUnit();
            long newRemainder = r.newRemainder();

            // 更新 GrantLot
            long newRemaining = lot.remainingLockedUnit() - releaseUnit;
            long newReleasedTotal = releaseUnit; // 累加在 SQL 中
            GrantLotState newState = newRemaining == 0L ? GrantLotState.RELEASED : GrantLotState.ACTIVE;

            jdbc.update(
                    "UPDATE lsc_grant_lot SET remaining_locked_unit=?, released_total_unit=released_total_unit+?, "
                            + "remainder_nano_unit=?, last_processed_date=?, state=?, version=version+1 "
                            + "WHERE grant_lot_id=?",
                    newRemaining, newReleasedTotal, newRemainder,
                    java.sql.Date.valueOf(businessDate), newState.name(), lot.grantLotId());

            // 创建 AvailableLot（如果 release>0）
            Long availableLotId = null;
            if (releaseUnit > 0L) {
                availableLotId = nextId();
                LocalDateTime availableAt = LocalDateTime.now();
                java.time.LocalDateTime expireAt = availableAt.plusDays(365L);
                jdbc.update(
                        "INSERT INTO lsc_available_lot(available_lot_id, user_id, source_grant_lot_id, "
                                + "origin_type, source_event_id, lot_sequence, issued_unit, restored_unit, "
                                + "available_unit, reserved_unit, frozen_unit, consumed_unit, expired_unit, "
                                + "revoked_unit, available_at, expire_at, version) "
                                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        availableLotId, lot.userId(), lot.grantLotId(), OriginType.GRANT.name(),
                        0L, 1L, releaseUnit, 0L, releaseUnit, 0L, 0L, 0L, 0L, 0L,
                        java.sql.Timestamp.valueOf(availableAt), java.sql.Timestamp.valueOf(expireAt), 0);
            }

            // 写 RELEASE 事件
            String businessKey = "RELEASE:" + lot.grantLotId() + ":" + businessDate;
            List<BucketDelta> deltas = List.of(
                    new BucketDelta(Bucket.LOCKED, -releaseUnit, DispositionType.RELEASE,
                            lot.grantLotId(), null, null),
                    new BucketDelta(Bucket.AVAILABLE, releaseUnit, DispositionType.RELEASE,
                            lot.grantLotId(), availableLotId, null));
            LedgerCommand cmd = LedgerCommand.builder()
                    .userId(lot.userId())
                    .eventType(EventType.RELEASE)
                    .businessKey(businessKey)
                    .businessDate(businessDate)
                    .occurredAt(LocalDateTime.now())
                    .entries(deltas)
                    .build();
            long eventId = ledger.write(cmd);

            // 回写 source_event_id
            if (availableLotId != null) {
                jdbc.update("UPDATE lsc_available_lot SET source_event_id=? WHERE available_lot_id=?",
                        eventId, availableLotId);
            }

            // 写 release_lot_result
            jdbc.update(
                    "INSERT INTO release_lot_result(result_id, grant_lot_id, business_date, snapshot_version, "
                            + "rate_ppb, remainder_before, remainder_after, released_unit, event_id) "
                            + "VALUES(?,?,?,?,?,?,?,?,?)",
                    nextId(), lot.grantLotId(), java.sql.Date.valueOf(businessDate), snapshotVersion,
                    ratePpb, lot.remainderNanoUnit(), newRemainder, releaseUnit, eventId);

            totalReleased += releaseUnit;
            processed++;
        }

        return new ReleaseSummary(businessDate, processed, skipped, totalReleased);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    private record GrantLotRow(long grantLotId, long userId, long originalGrantUnit,
                               long remainingLockedUnit, long frozenLockedUnit,
                               long remainderNanoUnit, LocalDate lastProcessedDate) {}

    public record ReleaseSummary(LocalDate businessDate, int processedLots, int skippedLots,
                                 long totalReleasedUnit) {}
}
