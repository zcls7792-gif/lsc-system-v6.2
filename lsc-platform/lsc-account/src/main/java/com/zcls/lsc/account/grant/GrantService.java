package com.zcls.lsc.account.grant;

import java.time.LocalDate;

/**
 * 第4.3 / 6.1 章 赠送发放服务。
 * 订单完成后按商品单元发放，一行建立一个 GrantLot。
 */
public interface GrantService {

    /**
     * 订单完成发放赠送。
     *
     * @param orderId      订单ID
     * @param completedAt  完成时间(UTC)
     * @param businessDate 业务日期(Asia/Shanghai)
     * @return 发放的赠送批次ID列表(order_item -> grant_lot_id)
     */
    GrantResult grantOnOrderComplete(long orderId, java.time.LocalDateTime completedAt, LocalDate businessDate);

    record GrantResult(long orderId, int grantedItemCount, long totalGrantedUnit) {}
}
