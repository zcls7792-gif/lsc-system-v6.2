package com.lianshengtong.ledger.service;

import com.lianshengtong.ledger.dto.*;
import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.entity.LscEvent;

import java.time.LocalDate;

/**
 * LSC 账本核心服务（V7.7.2 第六章）
 * 所有写操作保证：事件+分录+投影在同一事务内原子完成。
 */
public interface LscLedgerService {

    /** 消费赠送：创建 GrantLot，增加锁定余额 */
    LscEvent grantLsc(GrantCommand cmd);

    /** 每日释放：按 original_grant_unit 计算额度，余数累计，创建 AvailableLot */
    void dailyRelease(Long userId, LocalDate bizDate, long ratePpb);

    /** 支付占用：FEFO 选择批次，available -> reserved */
    Long reserveForPayment(ReserveCommand cmd);

    /** 支付核销：reserved -> consumed */
    void capturePayment(Long orderId, String businessKey);

    /** 解占用：reserved -> available */
    void releaseReservation(Long orderId, String businessKey);

    /** 退款返还：原抵扣返还，原批次未到期则恢复，已过期则新建返还批次 */
    void refundRestore(RefundRestoreCommand cmd);

    /** 赠送撤回：从锁定/可用/冻结扣减，不足挂待追偿 */
    void clawbackGrant(ClawbackCommand cmd);

    /** 冻结：从 locked/available 移至 frozen */
    void freeze(Long userId, Long caseId, String sourceBucket, Long grantLotId,
                Long availableLotId, long freezeUnit, String businessKey);

    /** 解冻 */
    void unfreeze(Long freezeId, String businessKey);

    /** 过期作废：available -> expired */
    void expireAvailableLots(Long userId, java.time.LocalDateTime now);

    /** 查询账户（5桶+追偿） */
    LscAccount getAccount(Long userId);

    /** 待追偿处理：新可用权益先冲抵追偿 */
    void applyRecoveryOffset(Long userId);
}
