package com.zcls.lsc.account.refund;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 第7.4 / 7.5 章 退款权益处理服务。
 *
 * 退款分为两部分，独立记录：
 *  1. 返还本单已核销的 LSC（原批次或30天宽限批次）
 *  2. 撤回本单赠送的 LSC（5档来源顺序，不足挂待追偿）
 *
 * 人民币退款与权益后处理状态分别记录，不伪装成跨渠道原子事务。
 */
public interface RefundService {

    /**
     * 处理退款的权益部分。
     *
     * @param refundId     退款单ID
     * @param orderId      原订单ID
     * @param userId       用户ID
     * @param refundAt     退款实际到账时间
     * @param businessDate 业务日期
     */
    RefundBenefitResult processRefundBenefit(long refundId, long orderId, long userId,
                                             LocalDateTime refundAt, LocalDate businessDate);

    record RefundBenefitResult(long refundId, long restoredUnit, long revokedUnit, long pendingUnit) {}
}
