package com.zcls.lsc.account.grant;

import com.zcls.lsc.account.enums.Bucket;
import com.zcls.lsc.account.enums.EventType;
import com.zcls.lsc.account.enums.LscEnums.DispositionType;
import com.zcls.lsc.account.enums.LscEnums.GrantLotState;
import com.zcls.lsc.account.ledger.BucketDelta;
import com.zcls.lsc.account.ledger.LedgerCommand;
import com.zcls.lsc.account.ledger.LedgerService;
import com.zcls.lsc.common.time.BusinessDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 第4.3 / 6.1 章 赠送发放实现。
 * 逐件取整到 unit，再汇总至商品行；一行建立一个 GrantLot。
 * original_grant_unit 发放后不可修改。
 */
@Service
public class GrantServiceImpl implements GrantService {

    private final JdbcTemplate jdbc;
    private final GrantCalculator calculator;
    private final LedgerService ledger;
    private final com.zcls.lsc.risk.rule.RiskInterceptionService riskInterception;

    public GrantServiceImpl(JdbcTemplate jdbc, GrantCalculator calculator, LedgerService ledger,
                            com.zcls.lsc.risk.rule.RiskInterceptionService riskInterception) {
        this.jdbc = jdbc;
        this.calculator = calculator;
        this.ledger = ledger;
        this.riskInterception = riskInterception;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrantResult grantOnOrderComplete(long orderId, LocalDateTime completedAt, LocalDate businessDate) {
        // 1. 查订单
        Long userId = jdbc.queryForObject(
                "SELECT user_id FROM orders WHERE order_id=?", Long.class, orderId);
        if (userId == null) {
            throw new IllegalArgumentException("order not found: " + orderId);
        }

        // 风控规则引擎拦截：权益发放前评估用户风险，命中则自动立案并阻断
        riskInterception.checkGrant(userId, orderId);

        // 2. 查订单行
        List<OrderItemRow> items = jdbc.query(
                "SELECT item_id, sku_id, qty, price_version, grant_coefficient_ppm, cost_snapshot_enc "
                        + "FROM order_item WHERE order_id=? ORDER BY item_seq",
                (rs, rowNum) -> new OrderItemRow(
                        rs.getLong("item_id"),
                        rs.getLong("sku_id"),
                        rs.getInt("qty"),
                        rs.getLong("price_version"),
                        rs.getLong("grant_coefficient_ppm")),
                orderId);

        int grantedCount = 0;
        long totalGranted = 0L;

        for (OrderItemRow item : items) {
            // 3. 查该价格版本的成本(C端/B端售价已在 order_unit_allocation.sale_cent)
            long costCent = jdbc.queryForObject(
                    "SELECT cost_price_enc FROM product_price_version WHERE price_version=?",
                    (rs, rowNum) -> {
                        byte[] enc = rs.getBytes(1);
                        return enc == null ? 0L : 0L;
                    },
                    item.priceVersion());

            // 4. 查单元分摊
            List<UnitAllocRow> units = jdbc.query(
                    "SELECT allocation_id, unit_index, sale_cent, rmb_cent, grant_unit "
                            + "FROM order_unit_allocation WHERE item_id=? ORDER BY unit_index",
                    (rs, rowNum) -> new UnitAllocRow(
                            rs.getLong("allocation_id"),
                            rs.getInt("unit_index"),
                            rs.getLong("sale_cent"),
                            rs.getLong("rmb_cent"),
                            rs.getLong("grant_unit")),
                    item.itemId());

            // 5. 逐件计算赠送并汇总
            long itemGranted = 0L;
            for (UnitAllocRow u : units) {
                long unitGrant = calculator.calcUnitGrant(
                        u.saleCent(), costCent, u.rmbCent(), item.grantCoefPpm());
                // 回写单元赠送
                jdbc.update("UPDATE order_unit_allocation SET grant_unit=? WHERE allocation_id=?",
                        unitGrant, u.allocationId());
                itemGranted += unitGrant;
            }

            if (itemGranted <= 0L) {
                continue; // 无赠送(成本>=售价或系数为0)
            }

            // 6. 创建 GrantLot(一行一个)
            long grantLotId = nextId();
            LocalDate grantDate = businessDate;
            LocalDate firstReleaseDate = BusinessDate.next(grantDate);
            jdbc.update(
                    "INSERT INTO lsc_grant_lot(grant_lot_id, user_id, source_item_id, original_grant_unit, "
                            + "remaining_locked_unit, frozen_locked_unit, released_total_unit, revoked_locked_unit, "
                            + "remainder_nano_unit, grant_business_date, first_release_date, state, rule_version, version) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    grantLotId, userId, item.itemId(), itemGranted, itemGranted, 0L, 0L, 0L,
                    0L, java.sql.Date.valueOf(grantDate), java.sql.Date.valueOf(firstReleaseDate),
                    GrantLotState.ACTIVE.name(), "v7.7.2", 0);

            // 7. 写 GRANT 事件 + LOCKED 桶分录
            String businessKey = "GRANT:" + orderId + ":" + item.itemId();
            List<BucketDelta> deltas = List.of(
                    new BucketDelta(Bucket.LOCKED, itemGranted, DispositionType.RELEASE,
                            grantLotId, null, null));
            LedgerCommand cmd = LedgerCommand.builder()
                    .userId(userId)
                    .eventType(EventType.GRANT)
                    .businessKey(businessKey)
                    .orderId(orderId)
                    .businessDate(businessDate)
                    .occurredAt(completedAt)
                    .entries(deltas)
                    .build();
            ledger.write(cmd);

            // 8. 回写 order_item
            jdbc.update(
                    "UPDATE order_item SET granted_unit=?, grant_lot_id=? WHERE item_id=?",
                    itemGranted, grantLotId, item.itemId());

            grantedCount++;
            totalGranted += itemGranted;
        }

        return new GrantResult(orderId, grantedCount, totalGranted);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    private record OrderItemRow(long itemId, long skuId, int qty, long priceVersion, long grantCoefPpm) {}
    private record UnitAllocRow(long allocationId, int unitIndex, long saleCent, long rmbCent, long grantUnit) {}
}
