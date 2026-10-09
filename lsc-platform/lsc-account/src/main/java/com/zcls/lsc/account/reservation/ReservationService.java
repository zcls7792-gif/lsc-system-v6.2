package com.zcls.lsc.account.reservation;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 第7.1章 支付占用服务。
 *
 * - reserve: 下单时按 FEFO 占用 AvailableLot，创建 reservation
 * - capture: 支付成功核销占用 -> 消费
 * - release: 取消/超时释放占用 -> 可用
 *
 * FEFO 排序: expire_at, available_at, available_lot_id。
 * 订单有效支付截止 = min(默认支付截止, 所选批次最早 expire_at)。
 * 支付占用不延长原权益有效期。
 */
public interface ReservationService {

    /**
     * 占用权益。
     *
     * @param orderId   订单ID
     * @param userId    用户ID
     * @param lscUnit   占用 unit(必须为100整数倍)
     * @param expiresAt 订单支付截止时间
     * @return 占用结果
     */
    ReservationResult reserve(long orderId, long userId, long lscUnit, LocalDateTime expiresAt);

    /**
     * 核销占用（支付成功）。
     *
     * @param reservationId 占用ID
     * @param paidAt        渠道成功时间
     */
    void capture(long reservationId, LocalDateTime paidAt);

    /**
     * 释放占用（取消/超时）。
     *
     * @param reservationId 占用ID
     */
    void release(long reservationId);

    record ReservationResult(long reservationId, long reservedUnit, List<Long> allocatedLotIds) {}
}
