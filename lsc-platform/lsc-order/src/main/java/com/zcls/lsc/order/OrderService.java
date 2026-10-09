package com.zcls.lsc.order;

import com.zcls.lsc.account.reservation.ReservationService;
import com.zcls.lsc.common.constants.LscConstants;
import com.zcls.lsc.common.error.BusinessException;
import com.zcls.lsc.common.error.ErrorCode;
import com.zcls.lsc.common.time.BusinessDate;
import com.zcls.lsc.order.enums.OrderEnums.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 第3.4 / 4.2 / 7.1 章 下单服务。
 *
 *  - 下单时快照 SKU、价格版本、商品金额、运费、抵扣及人民币分摊
 *  - 任何后续商品改价、成本调整、B 身份变更不重算旧单
 *  - 优惠按适用商品金额比例向下分配到分，剩余分按最大余数法分配
 *  - LSC 分摊先按分计算再乘 100 得到 unit
 *  - 创建事务内预占库存及券或 LSC，记录逐商品单元分摊和逐 Lot 占用
 */
@Service
public class OrderService {

    private final JdbcTemplate jdbc;
    private final ReservationService reservationService;
    private final com.zcls.lsc.risk.rule.RiskInterceptionService riskInterception;

    public OrderService(JdbcTemplate jdbc, ReservationService reservationService,
                        com.zcls.lsc.risk.rule.RiskInterceptionService riskInterception) {
        this.jdbc = jdbc;
        this.reservationService = reservationService;
        this.riskInterception = riskInterception;
    }

    /**
     * 从报价创建订单。
     *
     * @param quoteId 报价ID
     * @param userId  用户ID
     * @return 订单号
     */
    @Transactional(rollbackFor = Exception.class)
    public String createOrderFromQuote(long quoteId, long userId) {
        // 取报价（锁）
        QuoteRow q = jdbc.queryForObject(
                "SELECT quote_id, user_id, buyer_type, goods_cent, coupon_cent, lsc_unit, rmb_cent, "
                        + "discount_mode, coupon_id, expires_at, payload_json FROM quote WHERE quote_id=? FOR UPDATE",
                (rs, rowNum) -> new QuoteRow(
                        rs.getLong("quote_id"),
                        rs.getLong("user_id"),
                        rs.getString("buyer_type"),
                        rs.getLong("goods_cent"),
                        rs.getLong("coupon_cent"),
                        rs.getLong("lsc_unit"),
                        rs.getLong("rmb_cent"),
                        rs.getString("discount_mode"),
                        (Long) rs.getObject("coupon_id"),
                        rs.getTimestamp("expires_at").toLocalDateTime(),
                        rs.getString("payload_json")),
                quoteId);

        if (q.userId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "quote not owned by user");
        }
        if (q.expiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.QUOTE_EXPIRED, "quote expired");
        }

        // 风控规则引擎拦截：下单前评估用户风险，命中则自动立案并阻断
        riskInterception.checkOrderCreation(userId, q.goodsCent(), q.buyerType());

        // 解析报价商品项
        List<QuoteItemRow> items = parseItems(q.payloadJson());

        // 创建订单
        long orderId = nextId();
        String orderNo = "LSC" + System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();
        LocalDate businessDate = BusinessDate.today();
        LocalDateTime expiresAt = now.plusMinutes(LscConstants.PAYMENT_TTL.toMinutes());

        // 销售主体（本期统一供应链公司，实际从配置取）
        long sellerEntity = 1L;

        jdbc.update(
                "INSERT INTO orders(order_id, order_no, user_id, buyer_type_snapshot, seller_entity_id, "
                        + "payee_entity_id, invoice_entity_id, benefit_obligor_entity_id, discount_mode, "
                        + "goods_cent, shipping_cent, coupon_cent, lsc_unit, rmb_cent, payment_status, "
                        + "fulfillment_status, refund_status, expires_at, quote_version, version) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                orderId, orderNo, userId, q.buyerType(), sellerEntity, sellerEntity, sellerEntity, sellerEntity,
                q.discountMode(), q.goodsCent(), 0L, q.couponCent(), q.lscUnit(), q.rmbCent(),
                PaymentStatus.UNPAID.name(), FulfillmentStatus.CREATED.name(), RefundStatus.NONE.name(),
                java.sql.Timestamp.valueOf(expiresAt), q.quoteId(), 0);

        // 创建订单行 + 单元分摊
        int itemSeq = 0;
        for (QuoteItemRow item : items) {
            long itemId = nextId();
            jdbc.update(
                    "INSERT INTO order_item(item_id, order_id, sku_id, qty, price_version, unit_price_cent, "
                            + "line_goods_cent, coupon_share_cent, lsc_share_unit, rmb_share_cent, "
                            + "grant_coefficient_ppm, granted_unit, clawback_required_unit, "
                            + "clawback_completed_unit, clawback_pending_unit, item_seq, version) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    itemId, orderId, item.skuId(), item.qty(), 0L, item.unitPriceCent(), item.lineCent(),
                    0L, 0L, item.lineCent(), 800_000L, 0L, 0L, 0L, 0L, itemSeq++, 0);

            // 单元分摊（逐件）
            for (int u = 0; u < item.qty(); u++) {
                jdbc.update(
                        "INSERT INTO order_unit_allocation(allocation_id, item_id, unit_index, sale_cent, "
                                + "coupon_cent, lsc_unit, rmb_cent, grant_unit, refunded_rmb_cent, "
                                + "refunded_lsc_unit, clawback_target_unit, return_status, version) "
                                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        nextId(), itemId, u, item.unitPriceCent(), 0L, 0L, item.unitPriceCent(),
                        0L, 0L, 0L, 0L, "NONE", 0);
            }
        }

        // 分摊优惠（券或LSC）到单元
        if (q.couponCent() > 0L || q.lscUnit() > 0L) {
            allocateDiscount(orderId, q.couponCent(), q.lscUnit());
        }

        // LSC 占用
        if (q.lscUnit() > 0L) {
            reservationService.reserve(orderId, userId, q.lscUnit(), expiresAt);
        }

        // 券预占
        if (q.couponId() != null) {
            jdbc.update("UPDATE user_coupon SET status='RESERVED', reserved_order_id=?, version=version+1 "
                    + "WHERE coupon_id=? AND status='AVAILABLE'", orderId, q.couponId());
        }

        return orderNo;
    }

    /**
     * 第4.2章 优惠分摊。
     * 优惠先按适用商品金额比例向下分配到分，剩余分按最大余数法分配，
     * 余数相同按 order_item_id、unit_index 升序。
     * LSC 分摊先按分计算再乘 100 得到 unit。
     */
    private void allocateDiscount(long orderId, long couponCent, long lscUnit) {
        // 取所有单元分摊
        List<UnitRow> units = jdbc.query(
                "SELECT ua.allocation_id, ua.sale_cent, oi.item_id "
                        + "FROM order_unit_allocation ua JOIN order_item oi ON ua.item_id=oi.item_id "
                        + "WHERE oi.order_id=? ORDER BY oi.item_id ASC, ua.unit_index ASC",
                (rs, rowNum) -> new UnitRow(
                        rs.getLong("allocation_id"),
                        rs.getLong("item_id"),
                        rs.getLong("sale_cent")),
                orderId);

        long totalSale = units.stream().mapToLong(UnitRow::saleCent).sum();
        if (totalSale <= 0) return;

        // 券分摊
        if (couponCent > 0) {
            long[] assigned = proportionalAllocate(units, couponCent, totalSale);
            for (int i = 0; i < units.size(); i++) {
                jdbc.update("UPDATE order_unit_allocation SET coupon_cent=? WHERE allocation_id=?",
                        assigned[i], units.get(i).allocationId());
            }
        }

        // LSC 分摊：先按分计算再乘 100
        if (lscUnit > 0) {
            long lscCent = lscUnit / 100L;
            long[] centsAssigned = proportionalAllocate(units, lscCent, totalSale);
            for (int i = 0; i < units.size(); i++) {
                long unitAssigned = centsAssigned[i] * 100L;
                jdbc.update("UPDATE order_unit_allocation SET lsc_unit=?, rmb_cent=sale_cent-coupon_cent-? "
                        + "WHERE allocation_id=?", unitAssigned, centsAssigned[i], units.get(i).allocationId());
            }
        } else {
            // 无 LSC 时 rmb = sale - coupon
            for (UnitRow u : units) {
                jdbc.update("UPDATE order_unit_allocation SET rmb_cent=sale_cent-coupon_cent WHERE allocation_id=?",
                        u.allocationId());
            }
        }
    }

    /**
     * 按金额比例向下取整分配，剩余按最大余数法分配。
     */
    private long[] proportionalAllocate(List<UnitRow> units, long totalToAllocate, long totalSale) {
        long[] assigned = new long[units.size()];
        long[] remainders = new long[units.size()];
        long allocated = 0L;

        for (int i = 0; i < units.size(); i++) {
            long raw = units.get(i).saleCent() * totalToAllocate;
            assigned[i] = raw / totalSale;
            remainders[i] = raw % totalSale;
            allocated += assigned[i];
        }

        long remaining = totalToAllocate - allocated;
        // 最大余数法，余数相同按 item_id, unit_index 升序（已按此排序）
        while (remaining > 0) {
            int maxIdx = -1;
            long maxRem = -1;
            for (int i = 0; i < remainders.length; i++) {
                if (remainders[i] > maxRem) {
                    maxRem = remainders[i];
                    maxIdx = i;
                }
            }
            if (maxIdx < 0) break;
            assigned[maxIdx]++;
            remainders[maxIdx] = -1; // 不再参与
            remaining--;
        }
        return assigned;
    }

    private List<QuoteItemRow> parseItems(String json) {
        // 简化 JSON 解析（生产用 Jackson）
        List<QuoteItemRow> list = new ArrayList<>();
        if (json == null) return list;
        // 解析 [{"skuId":1,"qty":2,"unitPriceCent":13000,"lineCent":26000},...]
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
        for (String obj : json.split("\\},")) {
            obj = obj.replace("{", "").replace("}", "").trim();
            if (obj.isEmpty()) continue;
            long skuId = 0, unitPrice = 0, lineCent = 0;
            int qty = 0;
            for (String kv : obj.split(",")) {
                String[] p = kv.split(":");
                String k = p[0].trim().replace("\"", "");
                String v = p[1].trim();
                switch (k) {
                    case "skuId": skuId = Long.parseLong(v); break;
                    case "qty": qty = Integer.parseInt(v); break;
                    case "unitPriceCent": unitPrice = Long.parseLong(v); break;
                    case "lineCent": lineCent = Long.parseLong(v); break;
                }
            }
            list.add(new QuoteItemRow(skuId, qty, unitPrice, lineCent));
        }
        return list;
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    private record QuoteRow(long quoteId, long userId, String buyerType, long goodsCent, long couponCent,
                            long lscUnit, long rmbCent, String discountMode, Long couponId,
                            LocalDateTime expiresAt, String payloadJson) {}
    private record QuoteItemRow(long skuId, int qty, long unitPriceCent, long lineCent) {}
    private record UnitRow(long allocationId, long itemId, long saleCent) {}

    // ===== C 端订单查询 =====

    /** C 端订单列表（按用户查询，可选状态过滤）。 */
    public List<Map<String, Object>> listUserOrders(long userId, String status, int limit, long offset) {
        String statusFilter = (status == null || status.isEmpty()) ? "" : " AND payment_status = ?";
        String sql = "SELECT order_id, order_no, user_id, payment_status, fulfillment_status, "
                + "goods_cent, coupon_cent, lsc_unit, rmb_cent, created_at, completed_at "
                + "FROM orders WHERE user_id = ?" + statusFilter + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<Object> args = new java.util.ArrayList<>();
        args.add(userId);
        if (statusFilter.length() > 0) args.add(status);
        args.add(limit);
        args.add(offset);
        return jdbc.queryForList(sql, args.toArray());
    }

    /** C 端订单详情。 */
    public Map<String, Object> getOrderDetail(long orderId, long userId) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT order_id, order_no, user_id, buyer_type_snapshot, payment_status, fulfillment_status, "
                        + "refund_status, goods_cent, shipping_cent, coupon_cent, lsc_unit, rmb_cent, "
                        + "discount_mode, created_at, completed_at FROM orders WHERE order_id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("order_id", rs.getLong("order_id"));
                    m.put("order_no", rs.getString("order_no"));
                    m.put("user_id", rs.getLong("user_id"));
                    m.put("buyer_type_snapshot", rs.getString("buyer_type_snapshot"));
                    m.put("payment_status", rs.getString("payment_status"));
                    m.put("fulfillment_status", rs.getString("fulfillment_status"));
                    m.put("refund_status", rs.getString("refund_status"));
                    m.put("goods_cent", rs.getLong("goods_cent"));
                    m.put("shipping_cent", rs.getLong("shipping_cent"));
                    m.put("coupon_cent", rs.getLong("coupon_cent"));
                    m.put("lsc_unit", rs.getLong("lsc_unit"));
                    m.put("rmb_cent", rs.getLong("rmb_cent"));
                    m.put("discount_mode", rs.getString("discount_mode"));
                    m.put("created_at", rs.getString("created_at"));
                    m.put("completed_at", rs.getString("completed_at"));
                    return m;
                }, orderId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "order not found: " + orderId);
        }
        Map<String, Object> order = rows.get(0);
        if (!Long.valueOf(userId).equals(order.get("user_id"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "order not owned by user");
        }
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT item_id, order_id, sku_id, qty, unit_price_cent, line_goods_cent, "
                        + "coupon_share_cent, lsc_share_unit, rmb_share_cent FROM order_item WHERE order_id = ?", orderId);
        order.put("items", items);
        return order;
    }
}
