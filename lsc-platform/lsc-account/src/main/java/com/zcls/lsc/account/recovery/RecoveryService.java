package com.zcls.lsc.account.recovery;

import com.zcls.lsc.account.enums.Bucket;
import com.zcls.lsc.account.enums.EventType;
import com.zcls.lsc.account.enums.LscEnums.DispositionType;
import com.zcls.lsc.account.enums.LscEnums.RecoveryStatus;
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
 * 第7.6章 待追偿服务。
 *
 * 待追偿为独立待抵扣数量，不作为负可用余额，不形成人民币债务。
 * 新释放、退款返还及解占用重新转为可用的权益，先按追偿创建时间、recovery_id 顺序冲抵，
 * 再将剩余转为可用或冻结可用。
 */
@Service
public class RecoveryService {

    private final JdbcTemplate jdbc;
    private final LedgerService ledger;

    public RecoveryService(JdbcTemplate jdbc, LedgerService ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    /**
     * 对用户的可用权益进行追偿冲抵。
     * 按追偿创建时间、recovery_id 顺序，从可用余额 FEFO 扣减。
     *
     * @param userId 用户ID
     * @return 冲抵结果
     */
    @Transactional(rollbackFor = Exception.class)
    public RecoverySatisfyResult satisfyPendingRecoveries(long userId) {
        jdbc.queryForObject("SELECT user_id FROM lsc_account WHERE user_id=? FOR UPDATE",
                Long.class, userId);

        // 查所有 OPEN/PARTIAL 的追偿，按 opened_at, recovery_id 排序
        List<RecoveryRow> recoveries = jdbc.query(
                "SELECT recovery_id, pending_unit FROM lsc_recovery "
                        + "WHERE user_id=? AND status IN ('OPEN','PARTIAL') "
                        + "ORDER BY opened_at ASC, recovery_id ASC",
                (rs, rowNum) -> new RecoveryRow(rs.getLong(1), rs.getLong(2)),
                userId);

        long totalSatisfied = 0L;
        int clearedCount = 0;

        for (RecoveryRow rec : recoveries) {
            if (rec.pendingUnit() <= 0) continue;

            // FEFO 查可用批次
            List<AvailRow> lots = jdbc.query(
                    "SELECT available_lot_id, available_unit FROM lsc_available_lot "
                            + "WHERE user_id=? AND available_unit>0 AND reserved_unit=0 "
                            + "ORDER BY expire_at ASC, available_at ASC, available_lot_id ASC",
                    (rs, rowNum) -> new AvailRow(rs.getLong(1), rs.getLong(2)),
                    userId);

            long need = rec.pendingUnit();
            long satisfied = 0L;
            for (AvailRow lot : lots) {
                if (need <= 0) break;
                long take = Math.min(lot.availableUnit(), need);
                jdbc.update("UPDATE lsc_available_lot SET available_unit=available_unit-?, "
                        + "revoked_unit=revoked_unit+?, version=version+1 WHERE available_lot_id=?",
                        take, take, lot.availableLotId());
                need -= take;
                satisfied += take;
            }

            if (satisfied > 0) {
                jdbc.update("UPDATE lsc_recovery SET recovered_unit=recovered_unit+?, "
                        + "pending_unit=pending_unit-?, status=? WHERE recovery_id=?",
                        satisfied, satisfied,
                        need == 0 ? RecoveryStatus.CLEARED.name() : RecoveryStatus.PARTIAL.name(),
                        rec.recoveryId());

                // 写追偿分配
                jdbc.update("INSERT INTO lsc_recovery_allocation(alloc_id, recovery_id, "
                        + "source_entry_id, source_available_lot_id, satisfaction_type, amount_unit, "
                        + "event_id, allocation_seq) VALUES(?,?,?,?,?,?,?,?)",
                        nextId(), rec.recoveryId(), 0L, 0L, "FROM_AVAILABLE", satisfied, 0L, 1);

                String businessKey = "RECOVERY_SATISFY:" + rec.recoveryId() + ":" + System.nanoTime();
                LedgerCommand cmd = LedgerCommand.builder()
                        .userId(userId)
                        .eventType(EventType.RECOVERY_SATISFY)
                        .businessKey(businessKey)
                        .businessDate(LocalDate.now())
                        .occurredAt(LocalDateTime.now())
                        .entries(List.of(new BucketDelta(Bucket.AVAILABLE, -satisfied,
                                DispositionType.REVOKE, null, null, "REC:" + rec.recoveryId())))
                        .build();
                ledger.write(cmd);

                totalSatisfied += satisfied;
                if (need == 0) clearedCount++;
            }
        }

        return new RecoverySatisfyResult(userId, totalSatisfied, clearedCount);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    private record RecoveryRow(long recoveryId, long pendingUnit) {}
    private record AvailRow(long availableLotId, long availableUnit) {}
    public record RecoverySatisfyResult(long userId, long satisfiedUnit, int clearedCount) {}
}
