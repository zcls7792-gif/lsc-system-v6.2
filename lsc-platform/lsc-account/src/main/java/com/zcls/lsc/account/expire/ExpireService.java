package com.zcls.lsc.account.expire;

import com.zcls.lsc.account.enums.Bucket;
import com.zcls.lsc.account.enums.EventType;
import com.zcls.lsc.account.enums.LscEnums.DispositionType;
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
 * 第5.5章 权益到期作废服务。
 *
 * - expire_at 采用左闭右开：now < expire_at 可用
 * - 账务支付校验实时检查有效期，不能依赖过期任务是否及时运行
 * - 到期按批次余额分别处理可用及风险冻结可用部分，冻结不暂停有效期
 * - 历史补跑权益自实际到账起计 365 天
 */
@Service
public class ExpireService {

    private final JdbcTemplate jdbc;
    private final LedgerService ledger;

    public ExpireService(JdbcTemplate jdbc, LedgerService ledger) {
        this.jdbc = jdbc;
        this.ledger = ledger;
    }

    /**
     * 作废已到期批次（available_unit + frozen_unit）。
     * 账务日期按实际作废提交日。
     *
     * @param businessDate 业务日期
     * @return 作废结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ExpireResult expireLots(LocalDate businessDate) {
        LocalDateTime now = LocalDateTime.now();

        // 查已到期且有余额的 AvailableLot
        List<ExpireLotRow> lots = jdbc.query(
                "SELECT available_lot_id, user_id, available_unit, frozen_unit, expire_at "
                        + "FROM lsc_available_lot WHERE expire_at<=? AND (available_unit>0 OR frozen_unit>0) "
                        + "LIMIT 5000",
                (rs, rowNum) -> new ExpireLotRow(
                        rs.getLong("available_lot_id"),
                        rs.getLong("user_id"),
                        rs.getLong("available_unit"),
                        rs.getLong("frozen_unit"),
                        rs.getTimestamp("expire_at").toLocalDateTime()),
                java.sql.Timestamp.valueOf(now));

        long totalExpired = 0L;
        int count = 0;

        for (ExpireLotRow lot : lots) {
            long availExpire = lot.availableUnit();
            long frozenExpire = lot.frozenUnit();
            if (availExpire <= 0 && frozenExpire <= 0) continue;

            jdbc.update("UPDATE lsc_available_lot SET available_unit=0, frozen_unit=0, "
                    + "expired_unit=expired_unit+?, terminal_at=?, version=version+1 "
                    + "WHERE available_lot_id=?",
                    availExpire + frozenExpire, java.sql.Timestamp.valueOf(now), lot.availableLotId());

            // 同时更新冻结分配的 expired_unit
            if (frozenExpire > 0) {
                jdbc.update("UPDATE lsc_freeze_allocation SET expired_unit=expired_unit+?, "
                        + "status='EXPIRED' WHERE available_lot_id=? AND status='ACTIVE'",
                        frozenExpire, lot.availableLotId());
            }

            List<BucketDelta> deltas = new java.util.ArrayList<>();
            if (availExpire > 0) {
                deltas.add(new BucketDelta(Bucket.AVAILABLE, -availExpire, DispositionType.EXPIRE,
                        null, lot.availableLotId(), "EXPIRE"));
            }
            if (frozenExpire > 0) {
                deltas.add(new BucketDelta(Bucket.FROZEN_AVAILABLE, -frozenExpire, DispositionType.EXPIRE,
                        null, lot.availableLotId(), "EXPIRE"));
            }

            String businessKey = "EXPIRE:" + lot.availableLotId() + ":" + businessDate;
            LedgerCommand cmd = LedgerCommand.builder()
                    .userId(lot.userId())
                    .eventType(EventType.EXPIRE)
                    .businessKey(businessKey)
                    .businessDate(businessDate)
                    .occurredAt(now)
                    .entries(deltas)
                    .build();
            ledger.write(cmd);

            totalExpired += availExpire + frozenExpire;
            count++;
        }

        return new ExpireResult(businessDate, count, totalExpired);
    }

    private record ExpireLotRow(long availableLotId, long userId, long availableUnit,
                                long frozenUnit, LocalDateTime expireAt) {}
    public record ExpireResult(LocalDate businessDate, int expiredLots, long totalExpiredUnit) {}
}
