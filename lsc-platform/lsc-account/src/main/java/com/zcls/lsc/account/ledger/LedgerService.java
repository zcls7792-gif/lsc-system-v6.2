package com.zcls.lsc.account.ledger;

/**
 * 第6.1章 权益账本事实源写入服务。
 *
 * 职责：
 *  - 接收 LedgerCommand（含事件类型、业务幂等键、分录列表）
 *  - 幂等检查：相同 user_id + business_key 返回已记录 eventId
 *  - 冲突检查：相同 business_key 不同 request_hash 抛 409
 *  - 锁定 lsc_account 行获取并递增 user_event_seq
 *  - 写 lsc_event（事实源，不可修改）
 *  - 写 lsc_entry（每条分录含 before_unit/after_unit 快照）
 *  - 同步更新 lsc_account 投影（各桶余额 + last_event_seq）
 *
 * 批次投影（GrantLot/AvailableLot/Reservation 等）由调用方在同一事务内更新，
 * 本服务不直接操作批次，以保持事实源与投影的职责分离。
 *
 * 所有写入在调用方的 @Transactional 事务内完成；本服务不自行开启事务，
 * 由业务服务（GrantService/ReleaseService/ReservationService 等）统一控制。
 */
public interface LedgerService {

    /**
     * 写入事件与分录并更新账户投影。
     *
     * @param command 账本命令
     * @return 事件ID（幂等时返回已存在事件ID）
     */
    long write(LedgerCommand command);
}
