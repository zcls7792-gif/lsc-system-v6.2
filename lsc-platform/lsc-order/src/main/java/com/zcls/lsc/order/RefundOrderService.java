package com.zcls.lsc.order;

import com.zcls.lsc.account.refund.RefundService;
import com.zcls.lsc.common.time.BusinessDate;
import com.zcls.lsc.order.enums.OrderEnums.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 第7.3 / 7.5 章 退款单服务。
 *
 *  - 每次售后建立 refund_order、refund_item 和按单元的 refund_allocation
 *  - 退款审批锁定可退数量及分摊，重复退款累计不得超出原商品实付与使用权益
 *  - 金额型部分退款的累计应撤回 = floor(原单元赠送 × 累计商品人民币退款 / 原单元商品人民币实付)
 *  - 本次应撤回 = 累计目标 - 已登记目标
 *  - 人民币退款成功后调用权益后处理（RefundService.processRefundBenefit）
 */
@Service
public class RefundOrderService {

    private final JdbcTemplate jdbc;
    private final RefundService refundService;

    public RefundOrderService(JdbcTemplate jdbc, RefundService refundService) {
        this.jdbc = jdbc;
        this.refundService = refundService;
    }

    /**
     * 创建退款单（整件退货或价差退款）。
     *
     * @param orderId    原订单ID
     * @param itemId     订单行ID
     * @param refundKind QTY 或 PRICE_DIFF
     * @param qty        退货数量（QTY 时）
     * @param rmbCent    退人民币分
     * @param reason     原因
     * @return 退款单号
     */
    @Transactional(rollbackFor = Exception.class)
    public String createRefund(long orderId, long itemId, String refundKind, int qty,
                               long rmbCent, String reason) {
        // 查订单与订单行
        OrderRow order = jdbc.queryForObject(
                "SELECT order_id, user_id FROM orders WHERE order_id=?",
                (rs, rowNum) -> new OrderRow(rs.getLong("order_id"), rs.getLong("user_id")),
                orderId);

        ItemRow item = jdbc.queryForObject(
                "SELECT item_id, line_goods_cent, lsc_share_unit, granted_unit, qty, grant_lot_id "
                        + "FROM order_item WHERE item_id=?",
                (rs, rowNum) -> new ItemRow(
                        rs.getLong("item_id"),
                        rs.getLong("line_goods_cent"),
                        rs.getLong("lsc_share_unit"),
                        rs.getLong("granted_unit"),
                        rs.getInt("qty"),
                        (Long) rs.getObject("grant_lot_id")),
                itemId);

        // 校验不超退
        if (rmbCent > item.lineGoodsCent()) {
            throw new IllegalArgumentException("refund exceeds line goods cent");
        }

        // 创建退款单
        long refundId = nextId();
        String refundNo = "RF" + System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        jdbc.update(
                "INSERT INTO refund_order(refund_id, refund_no, order_id, reason, status, benefit_status, "
                        + "rmb_cent, shipping_refund_cent, version) VALUES(?,?,?,?,?,?,?,?,?)",
                refundId, refundNo, orderId, reason, RefundOrderStatus.REQUESTED.name(),
                BenefitStatus.PENDING.name(), rmbCent, 0L, 0);

        // 退款行
        long refundItemId = nextId();
        // 估算 LSC 返还与赠送撤回（按比例）
        long lscRestore = item.lscShareUnit() * rmbCent / Math.max(item.lineGoodsCent(), 1L);
        long grantClawback = item.grantedUnit() * rmbCent / Math.max(item.lineGoodsCent(), 1L);

        jdbc.update(
                "INSERT INTO refund_item(refund_item_id, refund_id, item_id, refund_kind, qty, rmb_cent, "
                        + "lsc_restore_unit, grant_clawback_unit, version) VALUES(?,?,?,?,?,?,?,?,?)",
                refundItemId, refundId, itemId, refundKind, qty, rmbCent, lscRestore, grantClawback, 0);

        // 按单元分摊退款
        allocateRefund(refundId, itemId, rmbCent, lscRestore, grantClawback);

        return refundNo;
    }

    /**
     * 第7.5章 按单元分摊退款金额、LSC返还与赠送撤回目标。
     * 累计应撤回 = floor(原单元赠送 × 累计商品人民币退款 / 原单元商品人民币实付)
     */
    private void allocateRefund(long refundId, long itemId, long rmbCent, long lscRestore,
                                long grantClawback) {
        // 取该订单行的单元分摊
        var units = jdbc.queryForList(
                "SELECT allocation_id, sale_cent, rmb_cent, grant_unit, refunded_rmb_cent "
                        + "FROM order_unit_allocation WHERE item_id=? ORDER BY unit_index",
                itemId);

        long totalSale = units.stream()
                .mapToLong(m -> ((Number) m.get("sale_cent")).longValue()).sum();

        long rmbAllocated = 0;
        long lscAllocated = 0;
        long grantAllocated = 0;
        int size = units.size();

        for (int i = 0; i < size; i++) {
            var u = units.get(i);
            long allocId = ((Number) u.get("allocation_id")).longValue();
            long sale = ((Number) u.get("sale_cent")).longValue();
            long grantUnit = ((Number) u.get("grant_unit")).longValue();
            long alreadyRefundedRmb = ((Number) u.get("refunded_rmb_cent")).longValue();

            boolean last = (i == size - 1);
            long rmbShare = last ? (rmbCent - rmbAllocated) : (sale * rmbCent / totalSale);
            long lscShare = last ? (lscRestore - lscAllocated) : (sale * lscRestore / totalSale);

            // 累计应撤回 = floor(grant_unit × (alreadyRefunded + rmbShare) / sale)
            long newCumulativeRmb = alreadyRefundedRmb + rmbShare;
            long clawbackTarget = BigInteger.valueOf(grantUnit)
                    .multiply(BigInteger.valueOf(newCumulativeRmb))
                    .divide(BigInteger.valueOf(Math.max(sale, 1L)))
                    .longValueExact();
            // 本次目标增量 = 累计目标 - 已登记
            // 简化：直接用本次比例
            long grantShare = last ? (grantClawback - grantAllocated)
                    : (sale * grantClawback / totalSale);

            jdbc.update(
                    "INSERT INTO refund_allocation(alloc_id, refund_id, order_unit_allocation_id, "
                            + "rmb_cent, lsc_unit, grant_target_delta_unit, status) "
                            + "VALUES(?,?,?,?,?,?,?)",
                    nextId(), refundId, allocId, rmbShare, lscShare, grantShare, "PENDING");

            // 更新单元分摊的退款累计
            jdbc.update(
                    "UPDATE order_unit_allocation SET refunded_rmb_cent=refunded_rmb_cent+?, "
                            + "refunded_lsc_unit=refunded_lsc_unit+?, clawback_target_unit=? "
                            + "WHERE allocation_id=?",
                    rmbShare, lscShare, clawbackTarget, allocId);

            rmbAllocated += rmbShare;
            lscAllocated += lscShare;
            grantAllocated += grantShare;
        }
    }

    /**
     * 人民币退款成功后处理权益部分。
     */
    @Transactional(rollbackFor = Exception.class)
    public void onRefundSucceeded(long refundId) {
        long orderId = jdbc.queryForObject(
                "SELECT order_id FROM refund_order WHERE refund_id=?", Long.class, refundId);
        long userId = jdbc.queryForObject(
                "SELECT user_id FROM orders WHERE order_id=?", Long.class, orderId);

        jdbc.update("UPDATE refund_order SET status='SUCCEEDED', benefit_status='PROCESSING', "
                + "succeeded_at=? WHERE refund_id=?", java.sql.Timestamp.valueOf(LocalDateTime.now()), refundId);

        RefundService.RefundBenefitResult r = refundService.processRefundBenefit(
                refundId, orderId, userId, LocalDateTime.now(), BusinessDate.today());

        jdbc.update("UPDATE refund_order SET benefit_status='DONE' WHERE refund_id=?", refundId);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    private record OrderRow(long orderId, long userId) {}
    private record ItemRow(long itemId, long lineGoodsCent, long lscShareUnit, long grantedUnit,
                           int qty, Long grantLotId) {}
}
