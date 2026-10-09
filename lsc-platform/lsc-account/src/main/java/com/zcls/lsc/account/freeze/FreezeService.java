package com.zcls.lsc.account.freeze;

import com.zcls.lsc.account.enums.Bucket;
import com.zcls.lsc.account.enums.EventType;
import com.zcls.lsc.account.enums.LscEnums.DispositionType;
import com.zcls.lsc.account.enums.LscEnums.FreezeSourceBucket;
import com.zcls.lsc.account.enums.LscEnums.FreezeStatus;
import com.zcls.lsc.account.ledger.BucketDelta;
import com.zcls.lsc.account.ledger.LedgerCommand;
import com.zcls.lsc.account.ledger.LedgerService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 第10.2章 风控冻结服务。
 *
 * 冻结来源留在批次中：
 *  - 锁定来源冻结：GrantLot.remaining_locked -> frozen_locked
 *  - 可用来源冻结：AvailableLot.available -> frozen
 * 可用权益正常到期但争议纠错应有恢复方案。
 */
@Service
public class FreezeService {

    private final JdbcTemplate jdbc;
    private final LedgerService ledger;

    public FreezeService(JdbcTemplate jdbc, LedgerService ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    /**
     * 冻结权益。
     *
     * @param userId       用户ID
     * @param caseId       风控案件ID
     * @param sourceBucket 来源桶：LOCKED 或 AVAILABLE
     * @param targetLotId  目标批次ID（grant_lot_id 或 available_lot_id）
     * @param unit         冻结量
     * @return 冻结分配ID
     */
    @Transactional(rollbackFor = Exception.class)
    public long freeze(long userId, long caseId, FreezeSourceBucket sourceBucket,
                       long targetLotId, long unit) {
        if (unit <= 0) throw new IllegalArgumentException("unit must be > 0");

        jdbc.queryForObject("SELECT user_id FROM lsc_account WHERE user_id=? FOR UPDATE",
                Long.class, userId);

        long freezeId = nextId();
        Bucket fromBucket;
        Bucket toBucket;

        if (sourceBucket == FreezeSourceBucket.LOCKED) {
            // GrantLot 锁定来源冻结
            jdbc.update("UPDATE lsc_grant_lot SET remaining_locked_unit=remaining_locked_unit-?, "
                    + "frozen_locked_unit=frozen_locked_unit+?, version=version+1 WHERE grant_lot_id=?",
                    unit, unit, targetLotId);
            fromBucket = Bucket.LOCKED;
            toBucket = Bucket.FROZEN_LOCKED;
            jdbc.update("INSERT INTO lsc_freeze_allocation(freeze_id, user_id, case_id, source_bucket, "
                    + "grant_lot_id, available_lot_id, frozen_unit, status, version) "
                    + "VALUES(?,?,?,?,?,?,?,?,?)",
                    freezeId, userId, caseId, sourceBucket.name(), targetLotId, null, unit,
                    FreezeStatus.ACTIVE.name(), 0);
        } else {
            // AvailableLot 可用来源冻结
            jdbc.update("UPDATE lsc_available_lot SET available_unit=available_unit-?, "
                    + "frozen_unit=frozen_unit+?, version=version+1 WHERE available_lot_id=?",
                    unit, unit, targetLotId);
            fromBucket = Bucket.AVAILABLE;
            toBucket = Bucket.FROZEN_AVAILABLE;
            jdbc.update("INSERT INTO lsc_freeze_allocation(freeze_id, user_id, case_id, source_bucket, "
                    + "grant_lot_id, available_lot_id, frozen_unit, status, version) "
                    + "VALUES(?,?,?,?,?,?,?,?,?)",
                    freezeId, userId, caseId, sourceBucket.name(), null, targetLotId, unit,
                    FreezeStatus.ACTIVE.name(), 0);
        }

        String businessKey = "FREEZE:" + caseId + ":" + targetLotId + ":" + sourceBucket;
        LedgerCommand cmd = LedgerCommand.builder()
                .userId(userId)
                .eventType(EventType.FREEZE)
                .businessKey(businessKey)
                .caseId(caseId)
                .businessDate(LocalDate.now())
                .occurredAt(LocalDateTime.now())
                .entries(List.of(
                        new BucketDelta(fromBucket, -unit, DispositionType.FREEZE,
                                sourceBucket == FreezeSourceBucket.LOCKED ? targetLotId : null,
                                sourceBucket == FreezeSourceBucket.AVAILABLE ? targetLotId : null,
                                "FREEZE:" + freezeId),
                        new BucketDelta(toBucket, unit, DispositionType.FREEZE,
                                sourceBucket == FreezeSourceBucket.LOCKED ? targetLotId : null,
                                sourceBucket == FreezeSourceBucket.AVAILABLE ? targetLotId : null,
                                "FREEZE:" + freezeId)))
                .build();
        ledger.write(cmd);

        return freezeId;
    }

    /**
     * 解冻权益。
     * 解除冻结先处理已到期部分，再按待追偿规则分配有效余额（第10.2章）。
     *
     * @param freezeId 冻结分配ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void unfreeze(long freezeId) {
        FreezeRow row = jdbc.queryForObject(
                "SELECT freeze_id, user_id, case_id, source_bucket, grant_lot_id, available_lot_id, "
                        + "frozen_unit, released_unit FROM lsc_freeze_allocation WHERE freeze_id=? FOR UPDATE",
                (rs, rowNum) -> new FreezeRow(
                        rs.getLong("freeze_id"),
                        rs.getLong("user_id"),
                        rs.getLong("case_id"),
                        FreezeSourceBucket.valueOf(rs.getString("source_bucket")),
                        (Long) rs.getObject("grant_lot_id"),
                        (Long) rs.getObject("available_lot_id"),
                        rs.getLong("frozen_unit"),
                        rs.getLong("released_unit")),
                freezeId);

        long toRelease = row.frozenUnit() - row.releasedUnit();
        if (toRelease <= 0) {
            jdbc.update("UPDATE lsc_freeze_allocation SET status=? WHERE freeze_id=?",
                    FreezeStatus.RELEASED.name(), freezeId);
            return;
        }

        Bucket fromBucket;
        Bucket toBucket;
        if (row.sourceBucket() == FreezeSourceBucket.LOCKED) {
            jdbc.update("UPDATE lsc_grant_lot SET frozen_locked_unit=frozen_locked_unit-?, "
                    + "remaining_locked_unit=remaining_locked_unit+?, version=version+1 WHERE grant_lot_id=?",
                    toRelease, toRelease, row.grantLotId());
            fromBucket = Bucket.FROZEN_LOCKED;
            toBucket = Bucket.LOCKED;
        } else {
            jdbc.update("UPDATE lsc_available_lot SET frozen_unit=frozen_unit-?, "
                    + "available_unit=available_unit+?, version=version+1 WHERE available_lot_id=?",
                    toRelease, toRelease, row.availableLotId());
            fromBucket = Bucket.FROZEN_AVAILABLE;
            toBucket = Bucket.AVAILABLE;
        }

        jdbc.update("UPDATE lsc_freeze_allocation SET released_unit=released_unit+?, status=? "
                + "WHERE freeze_id=?", toRelease, FreezeStatus.RELEASED.name(), freezeId);

        String businessKey = "UNFREEZE:" + freezeId;
        LedgerCommand cmd = LedgerCommand.builder()
                .userId(row.userId())
                .eventType(EventType.UNFREEZE)
                .businessKey(businessKey)
                .caseId(row.caseId())
                .businessDate(LocalDate.now())
                .occurredAt(LocalDateTime.now())
                .entries(List.of(
                        new BucketDelta(fromBucket, -toRelease, DispositionType.UNFREEZE, null, null,
                                "FREEZE:" + freezeId),
                        new BucketDelta(toBucket, toRelease, DispositionType.UNFREEZE, null, null,
                                "FREEZE:" + freezeId)))
                .build();
        ledger.write(cmd);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    private record FreezeRow(long freezeId, long userId, long caseId, FreezeSourceBucket sourceBucket,
                             Long grantLotId, Long availableLotId, long frozenUnit, long releasedUnit) {}
}
