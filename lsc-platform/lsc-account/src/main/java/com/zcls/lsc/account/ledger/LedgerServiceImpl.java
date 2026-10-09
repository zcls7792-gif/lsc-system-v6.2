package com.zcls.lsc.account.ledger;

import com.zcls.lsc.account.enums.Bucket;
import com.zcls.lsc.common.constants.LscConstants;
import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.common.idem.IdempotencyKey;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 第6.1章 权益账本事实源写入实现。
 * 所有操作在调用方事务内完成；本服务不标注 @Transactional。
 */
@Service
public class LedgerServiceImpl implements LedgerService {

    private final JdbcTemplate jdbc;

    public LedgerServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long write(LedgerCommand cmd) {
        if (cmd.entries().isEmpty() && (cmd.payloadJson() == null || cmd.payloadJson().isEmpty())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "empty ledger command");
        }

        // 1. 幂等检查 + 冲突检查
        Long existingEventId = findExistingEvent(cmd.userId(), cmd.businessKey());
        if (existingEventId != null) {
            String existingHash = findEventRequestHash(existingEventId);
            if (cmd.requestHash() != null && !cmd.requestHash().equals(existingHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT,
                        "same business_key with different request_hash");
            }
            return existingEventId;
        }

        // 2. 锁定账户行，获取并递增 user_event_seq
        long seq = lockAndNextSeq(cmd.userId());

        // 3. 读取账户当前各桶余额（作为 before_unit 基准）
        Map<Bucket, Long> bucketBalances = loadAccountBuckets(cmd.userId());

        // 4. 写事件
        long eventId = nextId();
        String eventHash = hashEvent(cmd);
        insertEvent(eventId, cmd, seq, eventHash);

        // 5. 写分录并计算 after_unit
        int entrySeq = 1;
        Map<Bucket, Long> running = new EnumMap<>(Bucket.class);
        running.putAll(bucketBalances);

        for (BucketDelta delta : cmd.entries()) {
            if (delta.isZero()) {
                continue; // 零余额变化不写分录（第6.1章）
            }
            Bucket b = delta.bucket();
            long before = running.getOrDefault(b, 0L);
            long after = before + delta.deltaUnit();
            if (after < 0L) {
                throw new BusinessException(ErrorCode.INVARIANT_VIOLATED,
                        "bucket " + b + " goes negative: " + before + " + " + delta.deltaUnit());
            }
            insertEntry(eventId, entrySeq++, cmd.userId(), delta, before, after);
            running.put(b, after);
        }

        // 6. 更新账户投影（各桶余额 + last_event_seq）
        updateAccountBuckets(cmd.userId(), running, seq);

        return eventId;
    }

    private Long findExistingEvent(long userId, String businessKey) {
        List<Long> ids = jdbc.query(
                "SELECT event_id FROM lsc_event WHERE user_id=? AND business_key=?",
                (rs, rowNum) -> rs.getLong(1),
                userId, businessKey);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String findEventRequestHash(long eventId) {
        List<String> hashes = jdbc.query(
                "SELECT request_hash FROM lsc_event WHERE event_id=?",
                (rs, rowNum) -> rs.getString(1),
                eventId);
        return hashes.isEmpty() ? null : hashes.get(0);
    }

    /**
     * 锁账户行并递增 last_event_seq；账户不存在则创建。
     * SELECT ... FOR UPDATE 行锁保证同一用户事件序号递增且并发安全。
     */
    private long lockAndNextSeq(long userId) {
        List<Long> seqs = jdbc.query(
                "SELECT last_event_seq FROM lsc_account WHERE user_id=? FOR UPDATE",
                (rs, rowNum) -> rs.getLong(1),
                userId);
        if (seqs.isEmpty()) {
            // 账户不存在，创建（幂等）
            try {
                jdbc.update("INSERT INTO lsc_account(user_id, last_event_seq, version) VALUES(?, 0, 0)",
                        userId);
            } catch (DuplicateKeyException ignore) {
                // 并发创建，重新锁
            }
            seqs = jdbc.query(
                    "SELECT last_event_seq FROM lsc_account WHERE user_id=? FOR UPDATE",
                    (rs, rowNum) -> rs.getLong(1),
                    userId);
        }
        long seq = seqs.get(0) + 1L;
        jdbc.update("UPDATE lsc_account SET last_event_seq=? WHERE user_id=?", seq, userId);
        return seq;
    }

    private Map<Bucket, Long> loadAccountBuckets(long userId) {
        Map<Bucket, Long> m = new EnumMap<>(Bucket.class);
        jdbc.query(
                "SELECT locked_unit, available_unit, reserved_unit, frozen_locked_unit, frozen_available_unit "
                        + "FROM lsc_account WHERE user_id=?",
                rs -> {
                    m.put(Bucket.LOCKED, rs.getLong("locked_unit"));
                    m.put(Bucket.AVAILABLE, rs.getLong("available_unit"));
                    m.put(Bucket.RESERVED, rs.getLong("reserved_unit"));
                    m.put(Bucket.FROZEN_LOCKED, rs.getLong("frozen_locked_unit"));
                    m.put(Bucket.FROZEN_AVAILABLE, rs.getLong("frozen_available_unit"));
                },
                userId);
        for (Bucket b : Bucket.values()) {
            m.putIfAbsent(b, 0L);
        }
        return m;
    }

    private void insertEvent(long eventId, LedgerCommand cmd, long seq, String eventHash) {
        jdbc.update(
                "INSERT INTO lsc_event(event_id, user_id, user_event_seq, event_type, business_key, "
                        + "request_hash, order_id, refund_id, case_id, business_date, occurred_at, "
                        + "rule_version, original_event_id, payload_version, payload_json, event_hash) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                eventId, cmd.userId(), seq, cmd.eventType().name(), cmd.businessKey(),
                cmd.requestHash(), cmd.orderId(), cmd.refundId(), cmd.caseId(),
                java.sql.Date.valueOf(cmd.businessDate()),
                java.sql.Timestamp.valueOf(cmd.occurredAt()),
                cmd.ruleVersion(), cmd.originalEventId(), 1, cmd.payloadJson(), eventHash);
    }

    private void insertEntry(long eventId, int entrySeq, long userId, BucketDelta delta,
                             long before, long after) {
        jdbc.update(
                "INSERT INTO lsc_entry(entry_id, event_id, entry_seq, user_id, grant_lot_id, "
                        + "available_lot_id, bucket, delta_unit, disposition_type, allocation_ref, "
                        + "before_unit, after_unit) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                nextId(), eventId, entrySeq, userId, delta.grantLotId(), delta.availableLotId(),
                delta.bucket().name(), delta.deltaUnit(), delta.dispositionType().name(),
                delta.allocationRef(), before, after);
    }

    private void updateAccountBuckets(long userId, Map<Bucket, Long> balances, long seq) {
        jdbc.update(
                "UPDATE lsc_account SET locked_unit=?, available_unit=?, reserved_unit=?, "
                        + "frozen_locked_unit=?, frozen_available_unit=?, last_event_seq=?, "
                        + "version=version+1 WHERE user_id=?",
                balances.get(Bucket.LOCKED),
                balances.get(Bucket.AVAILABLE),
                balances.get(Bucket.RESERVED),
                balances.get(Bucket.FROZEN_LOCKED),
                balances.get(Bucket.FROZEN_AVAILABLE),
                seq, userId);
    }

    private long nextId() {
        // 雪花ID简化版：时间戳 + 随机，生产环境用分布式ID生成器
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    private String hashEvent(LedgerCommand cmd) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String s = cmd.userId() + "|" + cmd.eventType() + "|" + cmd.businessKey()
                    + "|" + cmd.businessDate() + "|" + cmd.ruleVersion()
                    + "|" + (cmd.originalEventId() == null ? "" : cmd.originalEventId());
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
