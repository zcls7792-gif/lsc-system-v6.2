package com.lianshengtong.ledger.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.common.enums.*;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.lsc.*;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.ledger.dto.*;
import com.lianshengtong.ledger.entity.*;
import com.lianshengtong.ledger.mapper.*;
import com.lianshengtong.ledger.service.LscLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * LSC 账本核心服务实现（V7.7.2）
 * <p>
 * 事件溯源：lsc_event + lsc_entry 为事实源，账户与批次为同步投影。
 * 所有写操作在同一事务内完成：账户行锁 -> 事件 -> 分录 -> 投影更新。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LscLedgerServiceImpl implements LscLedgerService {

    private final LscAccountMapper accountMapper;
    private final LscGrantLotMapper grantLotMapper;
    private final LscAvailableLotMapper availableLotMapper;
    private final LscEventMapper eventMapper;
    private final LscEntryMapper entryMapper;
    private final LscReservationMapper reservationMapper;
    private final LscReservationAllocationMapper reservationAllocMapper;
    private final LscConsumptionAllocationMapper consumptionMapper;
    private final LscRecoveryMapper recoveryMapper;
    private final LscRecoveryAllocationMapper recoveryAllocMapper;
    private final LscFreezeAllocationMapper freezeMapper;

    // ============================ 赠送 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LscEvent grantLsc(GrantCommand cmd) {
        // 幂等
        LscEvent existing = eventMapper.selectByUserAndBizKey(cmd.getUserId(), cmd.getBusinessKey());
        if (existing != null) return existing;

        LscAccount acc = lockAccount(cmd.getUserId());
        long seq = acc.getLastEventSeq() + 1;

        // 创建 GrantLot
        LscGrantLot lot = new LscGrantLot();
        lot.setGrantLotId(IdUtil.getSnowflakeNextId());
        lot.setUserId(cmd.getUserId());
        lot.setSourceItemId(cmd.getSourceItemId());
        lot.setOriginalGrantUnit(cmd.getGrantUnit());
        lot.setRemainingLockedUnit(cmd.getGrantUnit());
        lot.setFrozenLockedUnit(0L);
        lot.setReleasedTotalUnit(0L);
        lot.setRevokedLockedUnit(0L);
        lot.setRemainderNanoUnit(0L);
        LocalDate grantDate = LocalDate.now();
        lot.setGrantBusinessDate(grantDate);
        lot.setFirstReleaseDate(grantDate.plusDays(1));  // 赠送次日起释放
        lot.setState(GrantLotState.ACTIVE.getCode());
        lot.setRefundHold(false);
        lot.setRuleVersion(cmd.getRuleVersion());
        lot.setVersion(0);
        grantLotMapper.insert(lot);

        // 事件
        LscEvent event = buildEvent(cmd.getUserId(), seq, LscEventType.GRANT, cmd.getBusinessKey(),
                null, null, grantDate);
        eventMapper.insert(event);

        // 分录：locked +grantUnit
        insertEntry(event.getEventId(), 1, cmd.getUserId(), lot.getGrantLotId(), null,
                LscBucket.LOCKED, cmd.getGrantUnit(), "GRANT", acc.getLockedUnit(),
                acc.getLockedUnit() + cmd.getGrantUnit());

        // 投影：账户锁定增加
        acc.setLockedUnit(acc.getLockedUnit() + cmd.getGrantUnit());
        acc.setLastEventSeq(seq);
        accountMapper.updateById(acc);

        return event;
    }

    // ============================ 每日释放 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dailyRelease(Long userId, LocalDate bizDate, long ratePpb) {
        LscAccount acc = lockAccount(userId);

        List<LscGrantLot> lots = grantLotMapper.selectActiveLotsForRelease(userId, bizDate);
        if (lots.isEmpty()) return;

        long seq = acc.getLastEventSeq();
        long totalReleased = 0;

        for (LscGrantLot lot : lots) {
            long[] result = ReleaseRateCalculator.calcDailyRelease(
                    lot.getOriginalGrantUnit(), ratePpb,
                    lot.getRemainderNanoUnit(), lot.getRemainingLockedUnit());
            long releaseUnit = result[0];
            long newRemainder = result[1];

            if (releaseUnit > 0) {
                seq++;
                // 事件
                String bizKey = "RELEASE_" + lot.getGrantLotId() + "_" + bizDate;
                LscEvent event = buildEvent(userId, seq, LscEventType.DAILY_RELEASE, bizKey,
                        null, null, bizDate);
                eventMapper.insert(event);

                // 创建 AvailableLot
                LscAvailableLot avail = new LscAvailableLot();
                avail.setAvailableLotId(IdUtil.getSnowflakeNextId());
                avail.setUserId(userId);
                avail.setSourceGrantLotId(lot.getGrantLotId());
                avail.setOriginType("DAILY_RELEASE");
                avail.setSourceEventId(event.getEventId());
                avail.setLotSequence(1L);
                avail.setIssuedUnit(releaseUnit);
                avail.setRestoredUnit(0L);
                avail.setAvailableUnit(releaseUnit);
                avail.setReservedUnit(0L);
                avail.setFrozenUnit(0L);
                avail.setConsumedUnit(0L);
                avail.setExpiredUnit(0L);
                avail.setRevokedUnit(0L);
                LocalDateTime now = LocalDateTime.now();
                avail.setAvailableAt(now);
                avail.setExpireAt(now.plusDays(LscUnitConstants.LSC_VALID_DAYS));
                avail.setVersion(0);
                availableLotMapper.insert(avail);

                // 分录：locked -releaseUnit, available +releaseUnit
                long beforeLocked = lot.getRemainingLockedUnit();
                insertEntry(event.getEventId(), 1, userId, lot.getGrantLotId(), null,
                        LscBucket.LOCKED, -releaseUnit, "RELEASE", beforeLocked, beforeLocked - releaseUnit);
                insertEntry(event.getEventId(), 2, userId, null, avail.getAvailableLotId(),
                        LscBucket.AVAILABLE, releaseUnit, "RELEASE", 0L, releaseUnit);

                // 投影：GrantLot
                lot.setRemainingLockedUnit(lot.getRemainingLockedUnit() - releaseUnit);
                lot.setReleasedTotalUnit(lot.getReleasedTotalUnit() + releaseUnit);
                if (lot.getRemainingLockedUnit() == 0) {
                    lot.setState(GrantLotState.RELEASED.getCode());
                }
            }
            // 更新余数与处理日期（即使 quota=0 也记录）
            lot.setRemainderNanoUnit(newRemainder);
            lot.setLastProcessedDate(bizDate);
            grantLotMapper.updateById(lot);

            totalReleased += releaseUnit;
        }

        if (totalReleased > 0) {
            acc.setLockedUnit(acc.getLockedUnit() - totalReleased);
            acc.setAvailableUnit(acc.getAvailableUnit() + totalReleased);
            // 追偿冲抵：新释放的可用先冲抵待追偿
            offsetRecoveryInTransaction(acc, seq);
            acc.setLastEventSeq(seq);
            accountMapper.updateById(acc);
        }
    }

    // ============================ 支付占用 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reserveForPayment(ReserveCommand cmd) {
        if (!LscMathUtil.isUnitMultipleOf100(cmd.getReserveUnit())) {
            throw new BizException(ResultCode.UNIT_NOT_MULTIPLE_OF_100);
        }
        LscAccount acc = lockAccount(cmd.getUserId());
        if (acc.getAvailableUnit() < cmd.getReserveUnit()) {
            throw new BizException(ResultCode.LSC_BALANCE_INSUFFICIENT);
        }

        long seq = acc.getLastEventSeq() + 1;
        LscEvent event = buildEvent(cmd.getUserId(), seq, LscEventType.PAY_RESERVE,
                cmd.getBusinessKey(), cmd.getOrderId(), null, LocalDate.now());
        eventMapper.insert(event);

        // FEFO 选择批次
        List<LscAvailableLot> fefo = availableLotMapper.selectFefoAvailable(cmd.getUserId(), LocalDateTime.now());
        long need = cmd.getReserveUnit();
        int entrySeq = 1;

        LscReservation reservation = new LscReservation();
        reservation.setReservationId(IdUtil.getSnowflakeNextId());
        reservation.setUserId(cmd.getUserId());
        reservation.setOrderId(cmd.getOrderId());
        reservation.setStatus("ACTIVE");
        reservation.setReservedTotalUnit(cmd.getReserveUnit());
        reservation.setExpiresAt(cmd.getExpiresAt());
        reservation.setVersion(0);
        reservationMapper.insert(reservation);

        for (LscAvailableLot lot : fefo) {
            if (need <= 0) break;
            long take = Math.min(lot.getAvailableUnit(), need);
            // 分录
            insertEntry(event.getEventId(), entrySeq++, cmd.getUserId(), null, lot.getAvailableLotId(),
                    LscBucket.AVAILABLE, -take, "RESERVE", lot.getAvailableUnit(), lot.getAvailableUnit() - take);
            insertEntry(event.getEventId(), entrySeq++, cmd.getUserId(), null, lot.getAvailableLotId(),
                    LscBucket.RESERVED, take, "RESERVE", lot.getReservedUnit(), lot.getReservedUnit() + take);

            lot.setAvailableUnit(lot.getAvailableUnit() - take);
            lot.setReservedUnit(lot.getReservedUnit() + take);
            availableLotMapper.updateById(lot);

            LscReservationAllocation alloc = new LscReservationAllocation();
            alloc.setAllocId(IdUtil.getSnowflakeNextId());
            alloc.setReservationId(reservation.getReservationId());
            alloc.setAvailableLotId(lot.getAvailableLotId());
            alloc.setReservedUnit(take);
            alloc.setCapturedUnit(0L);
            alloc.setReleasedUnit(0L);
            alloc.setVersion(0);
            reservationAllocMapper.insert(alloc);

            need -= take;
        }
        if (need > 0) {
            throw new BizException(ResultCode.LSC_BALANCE_INSUFFICIENT, "可用批次余额不足");
        }

        acc.setAvailableUnit(acc.getAvailableUnit() - cmd.getReserveUnit());
        acc.setReservedUnit(acc.getReservedUnit() + cmd.getReserveUnit());
        acc.setLastEventSeq(seq);
        accountMapper.updateById(acc);

        return reservation.getReservationId();
    }

    // ============================ 支付核销 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void capturePayment(Long orderId, String businessKey) {
        LscReservation reservation = reservationMapper.selectOne(
                new LambdaQueryWrapper<LscReservation>().eq(LscReservation::getOrderId, orderId));
        if (reservation == null) return;
        if (!"ACTIVE".equals(reservation.getStatus())) return;

        LscAccount acc = lockAccount(reservation.getUserId());
        long seq = acc.getLastEventSeq() + 1;
        LscEvent event = buildEvent(reservation.getUserId(), seq, LscEventType.PAY_CAPTURE,
                businessKey, orderId, null, LocalDate.now());
        eventMapper.insert(event);

        List<LscReservationAllocation> allocs = reservationAllocMapper.selectList(
                new LambdaQueryWrapper<LscReservationAllocation>()
                        .eq(LscReservationAllocation::getReservationId, reservation.getReservationId()));

        long totalCaptured = 0;
        int entrySeq = 1;
        for (LscReservationAllocation alloc : allocs) {
            long capture = alloc.getReservedUnit() - alloc.getCapturedUnit();
            if (capture <= 0) continue;
            LscAvailableLot lot = availableLotMapper.selectByIdForUpdate(alloc.getAvailableLotId());
            insertEntry(event.getEventId(), entrySeq++, reservation.getUserId(), null, lot.getAvailableLotId(),
                    LscBucket.RESERVED, -capture, "CAPTURE", lot.getReservedUnit(), lot.getReservedUnit() - capture);
            insertEntry(event.getEventId(), entrySeq++, reservation.getUserId(), null, lot.getAvailableLotId(),
                    LscBucket.CONSUMED, capture, "CAPTURE", lot.getConsumedUnit(), lot.getConsumedUnit() + capture);

            lot.setReservedUnit(lot.getReservedUnit() - capture);
            lot.setConsumedUnit(lot.getConsumedUnit() + capture);
            availableLotMapper.updateById(lot);

            alloc.setCapturedUnit(alloc.getCapturedUnit() + capture);
            reservationAllocMapper.updateById(alloc);
            totalCaptured += capture;
        }

        reservation.setStatus("CAPTURED");
        reservationMapper.updateById(reservation);

        acc.setReservedUnit(acc.getReservedUnit() - totalCaptured);
        acc.setLastEventSeq(seq);
        accountMapper.updateById(acc);
    }

    // ============================ 解占用 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseReservation(Long orderId, String businessKey) {
        LscReservation reservation = reservationMapper.selectOne(
                new LambdaQueryWrapper<LscReservation>().eq(LscReservation::getOrderId, orderId));
        if (reservation == null) return;
        if (!"ACTIVE".equals(reservation.getStatus())) return;

        LscAccount acc = lockAccount(reservation.getUserId());
        long seq = acc.getLastEventSeq() + 1;
        LscEvent event = buildEvent(reservation.getUserId(), seq, LscEventType.PAY_RELEASE,
                businessKey, orderId, null, LocalDate.now());
        eventMapper.insert(event);

        List<LscReservationAllocation> allocs = reservationAllocMapper.selectList(
                new LambdaQueryWrapper<LscReservationAllocation>()
                        .eq(LscReservationAllocation::getReservationId, reservation.getReservationId()));

        long totalReleased = 0;
        int entrySeq = 1;
        for (LscReservationAllocation alloc : allocs) {
            long release = alloc.getReservedUnit() - alloc.getCapturedUnit() - alloc.getReleasedUnit();
            if (release <= 0) continue;
            LscAvailableLot lot = availableLotMapper.selectByIdForUpdate(alloc.getAvailableLotId());
            insertEntry(event.getEventId(), entrySeq++, reservation.getUserId(), null, lot.getAvailableLotId(),
                    LscBucket.RESERVED, -release, "RELEASE", lot.getReservedUnit(), lot.getReservedUnit() - release);
            insertEntry(event.getEventId(), entrySeq++, reservation.getUserId(), null, lot.getAvailableLotId(),
                    LscBucket.AVAILABLE, release, "RELEASE", lot.getAvailableUnit(), lot.getAvailableUnit() + release);

            lot.setReservedUnit(lot.getReservedUnit() - release);
            lot.setAvailableUnit(lot.getAvailableUnit() + release);
            availableLotMapper.updateById(lot);

            alloc.setReleasedUnit(alloc.getReleasedUnit() + release);
            reservationAllocMapper.updateById(alloc);
            totalReleased += release;
        }

        reservation.setStatus("RELEASED");
        reservationMapper.updateById(reservation);

        acc.setReservedUnit(acc.getReservedUnit() - totalReleased);
        acc.setAvailableUnit(acc.getAvailableUnit() + totalReleased);
        acc.setLastEventSeq(seq);
        accountMapper.updateById(acc);
    }

    // ============================ 退款返还 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundRestore(RefundRestoreCommand cmd) {
        LscAccount acc = lockAccount(cmd.getUserId());
        long seq = acc.getLastEventSeq() + 1;
        LscEvent event = buildEvent(cmd.getUserId(), seq, LscEventType.REFUND_RESTORE,
                cmd.getBusinessKey(), cmd.getOrderId(), cmd.getRefundId(), LocalDate.now());
        eventMapper.insert(event);

        long totalRestored = 0;
        int entrySeq = 1;
        for (RefundRestoreCommand.RestoreItem item : cmd.getItems()) {
            if (item.getRestoreUnit() <= 0) continue;
            if (!item.isOriginalExpired()) {
                // 原批次未到期：返还原批次，保留原到期日
                LscAvailableLot lot = availableLotMapper.selectByIdForUpdate(item.getAvailableLotId());
                insertEntry(event.getEventId(), entrySeq++, cmd.getUserId(), null, lot.getAvailableLotId(),
                        LscBucket.AVAILABLE, item.getRestoreUnit(), "REFUND_RESTORE",
                        lot.getAvailableUnit(), lot.getAvailableUnit() + item.getRestoreUnit());
                lot.setAvailableUnit(lot.getAvailableUnit() + item.getRestoreUnit());
                lot.setRestoredUnit(lot.getRestoredUnit() + item.getRestoreUnit());
                availableLotMapper.updateById(lot);
            } else {
                // 原批次已过期：新建 REFUND_RESTORE 批次，30天有效
                LscAvailableLot restore = new LscAvailableLot();
                restore.setAvailableLotId(IdUtil.getSnowflakeNextId());
                restore.setUserId(cmd.getUserId());
                restore.setSourceGrantLotId(null);
                restore.setOriginType("REFUND_RESTORE");
                restore.setSourceEventId(event.getEventId());
                restore.setLotSequence(1L);
                restore.setIssuedUnit(0L);
                restore.setRestoredUnit(item.getRestoreUnit());
                restore.setAvailableUnit(item.getRestoreUnit());
                restore.setReservedUnit(0L);
                restore.setFrozenUnit(0L);
                restore.setConsumedUnit(0L);
                restore.setExpiredUnit(0L);
                restore.setRevokedUnit(0L);
                LocalDateTime now = LocalDateTime.now();
                restore.setAvailableAt(now);
                restore.setExpireAt(now.plusDays(LscUnitConstants.EXPIRED_LSC_REFUND_GRACE_DAYS));
                restore.setVersion(0);
                availableLotMapper.insert(restore);

                insertEntry(event.getEventId(), entrySeq++, cmd.getUserId(), null, restore.getAvailableLotId(),
                        LscBucket.AVAILABLE, item.getRestoreUnit(), "REFUND_RESTORE_NEW", 0L, item.getRestoreUnit());
            }
            totalRestored += item.getRestoreUnit();
        }

        acc.setAvailableUnit(acc.getAvailableUnit() + totalRestored);
        // 返还先冲抵追偿
        offsetRecoveryInTransaction(acc, seq);
        acc.setLastEventSeq(seq);
        accountMapper.updateById(acc);
    }

    // ============================ 赠送撤回 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clawbackGrant(ClawbackCommand cmd) {
        LscAccount acc = lockAccount(cmd.getUserId());
        long seq = acc.getLastEventSeq() + 1;
        LscEvent event = buildEvent(cmd.getUserId(), seq, LscEventType.GRANT_CLAWBACK,
                cmd.getBusinessKey(), cmd.getOrderId(), cmd.getRefundId(), LocalDate.now());
        eventMapper.insert(event);

        long remaining = cmd.getClawbackUnit();
        int entrySeq = 1;

        // 1. 同源 GrantLot 未冻结锁定量
        if (cmd.getGrantLotId() != null && remaining > 0) {
            LscGrantLot lot = grantLotMapper.selectByIdForUpdate(cmd.getGrantLotId());
            if (lot != null && lot.getRemainingLockedUnit() > 0) {
                long take = Math.min(lot.getRemainingLockedUnit(), remaining);
                insertEntry(event.getEventId(), entrySeq++, cmd.getUserId(), lot.getGrantLotId(), null,
                        LscBucket.LOCKED, -take, "CLAWBACK_LOCKED",
                        lot.getRemainingLockedUnit(), lot.getRemainingLockedUnit() - take);
                lot.setRemainingLockedUnit(lot.getRemainingLockedUnit() - take);
                lot.setRevokedLockedUnit(lot.getRevokedLockedUnit() + take);
                if (lot.getRemainingLockedUnit() == 0 && lot.getReleasedTotalUnit() == 0) {
                    lot.setState(GrantLotState.REVOKED.getCode());
                }
                grantLotMapper.updateById(lot);
                acc.setLockedUnit(acc.getLockedUnit() - take);
                remaining -= take;
            }
        }

        // 2. 全账户未占用可用余额 FEFO
        if (remaining > 0) {
            List<LscAvailableLot> fefo = availableLotMapper.selectFefoAvailable(cmd.getUserId(), LocalDateTime.now());
            for (LscAvailableLot lot : fefo) {
                if (remaining <= 0) break;
                long take = Math.min(lot.getAvailableUnit(), remaining);
                insertEntry(event.getEventId(), entrySeq++, cmd.getUserId(), null, lot.getAvailableLotId(),
                        LscBucket.AVAILABLE, -take, "CLAWBACK_AVAILABLE",
                        lot.getAvailableUnit(), lot.getAvailableUnit() - take);
                lot.setAvailableUnit(lot.getAvailableUnit() - take);
                lot.setRevokedUnit(lot.getRevokedUnit() + take);
                availableLotMapper.updateById(lot);
                acc.setAvailableUnit(acc.getAvailableUnit() - take);
                remaining -= take;
            }
        }

        // 3. 不足部分挂待追偿
        if (remaining > 0) {
            LscRecovery recovery = recoveryMapper.selectOne(
                    new LambdaQueryWrapper<LscRecovery>()
                            .eq(LscRecovery::getUserId, cmd.getUserId())
                            .eq(LscRecovery::getSourceItemId, cmd.getSourceItemId()));
            if (recovery == null) {
                recovery = new LscRecovery();
                recovery.setRecoveryId(IdUtil.getSnowflakeNextId());
                recovery.setUserId(cmd.getUserId());
                recovery.setSourceItemId(cmd.getSourceItemId());
                recovery.setRequiredUnit(remaining);
                recovery.setRecoveredUnit(0L);
                recovery.setSatisfiedByExpiryUnit(0L);
                recovery.setPendingUnit(remaining);
                recovery.setStatus("OPEN");
                recovery.setVersion(0);
                recoveryMapper.insert(recovery);
            } else {
                recovery.setRequiredUnit(recovery.getRequiredUnit() + remaining);
                recovery.setPendingUnit(recovery.getPendingUnit() + remaining);
                recoveryMapper.updateById(recovery);
            }
            acc.setPendingRecoveryUnit(acc.getPendingRecoveryUnit() + remaining);
            insertEntry(event.getEventId(), entrySeq++, cmd.getUserId(), null, null,
                    LscBucket.PENDING_RECOVERY, remaining, "RECOVERY_OPEN",
                    acc.getPendingRecoveryUnit() - remaining, acc.getPendingRecoveryUnit());
        }

        acc.setLastEventSeq(seq);
        accountMapper.updateById(acc);
    }

    // ============================ 冻结/解冻 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long userId, Long caseId, String sourceBucket, Long grantLotId,
                       Long availableLotId, long freezeUnit, String businessKey) {
        LscAccount acc = lockAccount(userId);
        long seq = acc.getLastEventSeq() + 1;
        LscEvent event = buildEvent(userId, seq, LscEventType.FREEZE, businessKey,
                null, null, LocalDate.now());
        event.setCaseId(caseId);
        eventMapper.insert(event);

        if ("LOCKED".equals(sourceBucket) && grantLotId != null) {
            LscGrantLot lot = grantLotMapper.selectByIdForUpdate(grantLotId);
            long take = Math.min(lot.getRemainingLockedUnit(), freezeUnit);
            insertEntry(event.getEventId(), 1, userId, grantLotId, null,
                    LscBucket.LOCKED, -take, "FREEZE", lot.getRemainingLockedUnit(), lot.getRemainingLockedUnit() - take);
            insertEntry(event.getEventId(), 2, userId, grantLotId, null,
                    LscBucket.FROZEN_LOCKED, take, "FREEZE", lot.getFrozenLockedUnit(), lot.getFrozenLockedUnit() + take);
            lot.setRemainingLockedUnit(lot.getRemainingLockedUnit() - take);
            lot.setFrozenLockedUnit(lot.getFrozenLockedUnit() + take);
            grantLotMapper.updateById(lot);
            acc.setLockedUnit(acc.getLockedUnit() - take);
            acc.setFrozenLockedUnit(acc.getFrozenLockedUnit() + take);
        } else if ("AVAILABLE".equals(sourceBucket) && availableLotId != null) {
            LscAvailableLot lot = availableLotMapper.selectByIdForUpdate(availableLotId);
            long take = Math.min(lot.getAvailableUnit(), freezeUnit);
            insertEntry(event.getEventId(), 1, userId, null, availableLotId,
                    LscBucket.AVAILABLE, -take, "FREEZE", lot.getAvailableUnit(), lot.getAvailableUnit() - take);
            insertEntry(event.getEventId(), 2, userId, null, availableLotId,
                    LscBucket.FROZEN_AVAILABLE, take, "FREEZE", lot.getFrozenUnit(), lot.getFrozenUnit() + take);
            lot.setAvailableUnit(lot.getAvailableUnit() - take);
            lot.setFrozenUnit(lot.getFrozenUnit() + take);
            availableLotMapper.updateById(lot);
            acc.setAvailableUnit(acc.getAvailableUnit() - take);
            acc.setFrozenAvailableUnit(acc.getFrozenAvailableUnit() + take);
        }

        acc.setLastEventSeq(seq);
        accountMapper.updateById(acc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfreeze(Long freezeId, String businessKey) {
        // 简化：按冻结分配记录逆向操作
        LscFreezeAllocation freeze = freezeMapper.selectById(freezeId);
        if (freeze == null) return;
        LscAccount acc = lockAccount(freeze.getUserId());
        long seq = acc.getLastEventSeq() + 1;
        LscEvent event = buildEvent(freeze.getUserId(), seq, LscEventType.UNFREEZE, businessKey,
                null, null, LocalDate.now());
        eventMapper.insert(event);

        long unit = freeze.getFrozenUnit() - freeze.getReleasedUnit() - freeze.getRevokedUnit() - freeze.getExpiredUnit();
        if (unit <= 0) return;

        if ("LOCKED".equals(freeze.getSourceBucket())) {
            LscGrantLot lot = grantLotMapper.selectByIdForUpdate(freeze.getGrantLotId());
            lot.setRemainingLockedUnit(lot.getRemainingLockedUnit() + unit);
            lot.setFrozenLockedUnit(lot.getFrozenLockedUnit() - unit);
            grantLotMapper.updateById(lot);
            acc.setLockedUnit(acc.getLockedUnit() + unit);
            acc.setFrozenLockedUnit(acc.getFrozenLockedUnit() - unit);
        } else {
            LscAvailableLot lot = availableLotMapper.selectByIdForUpdate(freeze.getAvailableLotId());
            lot.setAvailableUnit(lot.getAvailableUnit() + unit);
            lot.setFrozenUnit(lot.getFrozenUnit() - unit);
            availableLotMapper.updateById(lot);
            acc.setAvailableUnit(acc.getAvailableUnit() + unit);
            acc.setFrozenAvailableUnit(acc.getFrozenAvailableUnit() - unit);
        }

        freeze.setReleasedUnit(freeze.getReleasedUnit() + unit);
        freeze.setStatus("RELEASED");
        freezeMapper.updateById(freeze);

        acc.setLastEventSeq(seq);
        accountMapper.updateById(acc);
    }

    // ============================ 过期作废 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireAvailableLots(Long userId, LocalDateTime now) {
        LscAccount acc = lockAccount(userId);
        // 查询已过期且尚有可用的批次
        List<LscAvailableLot> expired = availableLotMapper.selectList(
                new LambdaQueryWrapper<LscAvailableLot>()
                        .eq(LscAvailableLot::getUserId, userId)
                        .lt(LscAvailableLot::getExpireAt, now)
                        .gt(LscAvailableLot::getAvailableUnit, 0)
                        .last("FOR UPDATE"));
        if (expired.isEmpty()) return;

        long seq = acc.getLastEventSeq();
        long totalExpired = 0;
        for (LscAvailableLot lot : expired) {
            seq++;
            String bizKey = "EXPIRE_" + lot.getAvailableLotId();
            LscEvent event = buildEvent(userId, seq, LscEventType.EXPIRE, bizKey,
                    null, null, LocalDate.now());
            eventMapper.insert(event);
            long unit = lot.getAvailableUnit();
            insertEntry(event.getEventId(), 1, userId, null, lot.getAvailableLotId(),
                    LscBucket.AVAILABLE, -unit, "EXPIRE", lot.getAvailableUnit(), 0L);
            insertEntry(event.getEventId(), 2, userId, null, lot.getAvailableLotId(),
                    LscBucket.EXPIRED, unit, "EXPIRE", lot.getExpiredUnit(), lot.getExpiredUnit() + unit);
            lot.setAvailableUnit(0L);
            lot.setExpiredUnit(lot.getExpiredUnit() + unit);
            lot.setTerminalAt(now);
            availableLotMapper.updateById(lot);
            totalExpired += unit;
        }

        acc.setAvailableUnit(acc.getAvailableUnit() - totalExpired);
        acc.setLastEventSeq(seq);
        accountMapper.updateById(acc);
    }

    // ============================ 查询 ============================

    @Override
    public LscAccount getAccount(Long userId) {
        LscAccount acc = accountMapper.selectById(userId);
        return acc != null ? acc : LscAccount.zero(userId);
    }

    // ============================ 追偿冲抵 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyRecoveryOffset(Long userId) {
        LscAccount acc = lockAccount(userId);
        if (acc.getPendingRecoveryUnit() <= 0) return;
        offsetRecoveryInTransaction(acc, acc.getLastEventSeq());
        accountMapper.updateById(acc);
    }

    /**
     * 追偿冲抵（在事务内调用，需已持有账户锁）。
     * 新可用权益先按追偿创建时间顺序冲抵已确认待追偿。
     */
    private void offsetRecoveryInTransaction(LscAccount acc, long baseSeq) {
        if (acc.getPendingRecoveryUnit() <= 0 || acc.getAvailableUnit() <= 0) return;

        List<LscRecovery> recoveries = recoveryMapper.selectList(
                new LambdaQueryWrapper<LscRecovery>()
                        .eq(LscRecovery::getUserId, acc.getUserId())
                        .eq(LscRecovery::getStatus, "OPEN")
                        .orderByAsc(LscRecovery::getOpenedAt));

        long available = acc.getAvailableUnit();
        long seq = baseSeq;
        for (LscRecovery rec : recoveries) {
            if (available <= 0) break;
            long offset = Math.min(rec.getPendingUnit(), available);
            if (offset <= 0) continue;

            seq++;
            LscEvent event = buildEvent(acc.getUserId(), seq, LscEventType.RECOVERY_SATISFIED,
                    "RECOVERY_" + rec.getRecoveryId() + "_" + seq, null, null, LocalDate.now());
            eventMapper.insert(event);

            // FEFO 扣减可用
            List<LscAvailableLot> fefo = availableLotMapper.selectFefoAvailable(acc.getUserId(), LocalDateTime.now());
            long need = offset;
            int es = 1;
            for (LscAvailableLot lot : fefo) {
                if (need <= 0) break;
                long take = Math.min(lot.getAvailableUnit(), need);
                insertEntry(event.getEventId(), es++, acc.getUserId(), null, lot.getAvailableLotId(),
                        LscBucket.AVAILABLE, -take, "RECOVERY", lot.getAvailableUnit(), lot.getAvailableUnit() - take);
                insertEntry(event.getEventId(), es++, acc.getUserId(), null, lot.getAvailableLotId(),
                        LscBucket.REVOKED, take, "RECOVERY", lot.getRevokedUnit(), lot.getRevokedUnit() + take);
                lot.setAvailableUnit(lot.getAvailableUnit() - take);
                lot.setRevokedUnit(lot.getRevokedUnit() + take);
                availableLotMapper.updateById(lot);
                need -= take;
            }

            rec.setRecoveredUnit(rec.getRecoveredUnit() + offset);
            rec.setPendingUnit(rec.getPendingUnit() - offset);
            if (rec.getPendingUnit() == 0) {
                rec.setStatus("CLEARED");
                rec.setClosedAt(LocalDateTime.now());
            }
            recoveryMapper.updateById(rec);

            acc.setAvailableUnit(acc.getAvailableUnit() - offset);
            acc.setPendingRecoveryUnit(acc.getPendingRecoveryUnit() - offset);
            available -= offset;
        }
        acc.setLastEventSeq(seq);
    }

    // ============================ 内部工具 ============================

    private LscAccount lockAccount(Long userId) {
        LscAccount acc = accountMapper.selectByIdForUpdate(userId);
        if (acc == null) {
            acc = LscAccount.zero(userId);
            accountMapper.insert(acc);
            acc = accountMapper.selectByIdForUpdate(userId);
        }
        return acc;
    }

    private LscEvent buildEvent(Long userId, long seq, LscEventType type, String bizKey,
                                 Long orderId, Long refundId, LocalDate bizDate) {
        LscEvent e = new LscEvent();
        e.setEventId(IdUtil.getSnowflakeNextId());
        e.setUserId(userId);
        e.setUserEventSeq(seq);
        e.setEventType(type.getCode());
        e.setBusinessKey(bizKey);
        e.setOrderId(orderId);
        e.setRefundId(refundId);
        e.setBusinessDate(bizDate);
        e.setOccurredAt(LocalDateTime.now());
        e.setPayloadVersion(1);
        return e;
    }

    private void insertEntry(Long eventId, int seq, Long userId, Long grantLotId, Long availLotId,
                              LscBucket bucket, long delta, String disposition, long before, long after) {
        LscEntry en = new LscEntry();
        en.setEntryId(IdUtil.getSnowflakeNextId());
        en.setEventId(eventId);
        en.setEntrySeq(seq);
        en.setUserId(userId);
        en.setGrantLotId(grantLotId);
        en.setAvailableLotId(availLotId);
        en.setBucket(bucket.getCode());
        en.setDeltaUnit(delta);
        en.setDispositionType(disposition);
        en.setBeforeUnit(before);
        en.setAfterUnit(after);
        entryMapper.insert(en);
    }

}
