package com.zcls.lsc.supplychain;

import com.zcls.lsc.supplychain.enums.SupplychainEnums.MovementType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 第9.3章 库存服务（三桶模型）。
 *
 * 库存分实物 on_hand、销售预占 reserved、质检冻结 blocked；
 * 可售 = on_hand - reserved - blocked，三者满足非负约束。
 * 禁止从 Redis 缓存直接扣最终库存。
 * 付款确认时扣实际库存并消除预占；入库、出库、退货及调整均有 stock_movement。
 */
@Service
public class StockService {

    private final JdbcTemplate jdbc;

    public StockService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 入库：增加 on_hand。
     */
    @Transactional(rollbackFor = Exception.class)
    public void stockIn(long warehouseId, long skuId, String batchNo, int qty, String businessKey) {
        ensureBalance(warehouseId, skuId, batchNo);
        jdbc.update("UPDATE stock_balance SET on_hand_qty=on_hand_qty+?, version=version+1 "
                + "WHERE warehouse_id=? AND sku_id=? AND batch_no=?", qty, warehouseId, skuId, batchNo);
        writeMovement(warehouseId, skuId, batchNo, MovementType.IN, qty, businessKey);
    }

    /**
     * 下单预占：增加 reserved，检查可售 >= qty。
     */
    @Transactional(rollbackFor = Exception.class)
    public long reserve(long orderId, long warehouseId, long skuId, String batchNo, int qty,
                        LocalDateTime expiresAt) {
        // 检查可售
        StockRow row = lockBalance(warehouseId, skuId, batchNo);
        int available = row.onHand() - row.reserved() - row.blocked();
        if (available < qty) {
            throw new IllegalStateException("insufficient stock: available=" + available + " need=" + qty);
        }
        jdbc.update("UPDATE stock_balance SET reserved_qty=reserved_qty+?, version=version+1 "
                + "WHERE warehouse_id=? AND sku_id=? AND batch_no=?", qty, warehouseId, skuId, batchNo);

        long reservationId = nextId();
        jdbc.update(
                "INSERT INTO stock_reservation(reservation_id, order_id, sku_id, warehouse_id, batch_no, "
                        + "reserved_qty, status, expires_at, version) VALUES(?,?,?,?,?,?,?,?,?)",
                reservationId, orderId, skuId, warehouseId, batchNo, qty, "ACTIVE",
                java.sql.Timestamp.valueOf(expiresAt), 0);
        writeMovement(warehouseId, skuId, batchNo, MovementType.RESERVE, qty, "RES:" + orderId);
        return reservationId;
    }

    /**
     * 支付成功：预占转实扣，减少 on_hand 和 reserved。
     */
    @Transactional(rollbackFor = Exception.class)
    public void capture(long reservationId) {
        var r = jdbc.queryForObject(
                "SELECT reservation_id, order_id, sku_id, warehouse_id, batch_no, reserved_qty, status "
                        + "FROM stock_reservation WHERE reservation_id=? FOR UPDATE",
                (rs, rowNum) -> new ResRow(
                        rs.getLong("reservation_id"),
                        rs.getLong("order_id"),
                        rs.getLong("sku_id"),
                        rs.getLong("warehouse_id"),
                        rs.getString("batch_no"),
                        rs.getInt("reserved_qty"),
                        rs.getString("status")),
                reservationId);

        if (!"ACTIVE".equals(r.status())) return;

        jdbc.update("UPDATE stock_balance SET on_hand_qty=on_hand_qty-?, reserved_qty=reserved_qty-?, "
                + "version=version+1 WHERE warehouse_id=? AND sku_id=? AND batch_no=?",
                r.reservedQty(), r.reservedQty(), r.warehouseId(), r.skuId(), r.batchNo());
        jdbc.update("UPDATE stock_reservation SET status='CAPTURED' WHERE reservation_id=?", reservationId);
        writeMovement(r.warehouseId(), r.skuId(), r.batchNo(), MovementType.CAPTURE, r.reservedQty(),
                "CAP:" + r.orderId());
    }

    /**
     * 释放预占（取消/超时）：减少 reserved。
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseReservation(long reservationId) {
        var r = jdbc.queryForObject(
                "SELECT reservation_id, order_id, sku_id, warehouse_id, batch_no, reserved_qty, status "
                        + "FROM stock_reservation WHERE reservation_id=? FOR UPDATE",
                (rs, rowNum) -> new ResRow(
                        rs.getLong("reservation_id"),
                        rs.getLong("order_id"),
                        rs.getLong("sku_id"),
                        rs.getLong("warehouse_id"),
                        rs.getString("batch_no"),
                        rs.getInt("reserved_qty"),
                        rs.getString("status")),
                reservationId);
        if (!"ACTIVE".equals(r.status())) return;

        jdbc.update("UPDATE stock_balance SET reserved_qty=reserved_qty-?, version=version+1 "
                + "WHERE warehouse_id=? AND sku_id=? AND batch_no=?",
                r.reservedQty(), r.warehouseId(), r.skuId(), r.batchNo());
        jdbc.update("UPDATE stock_reservation SET status='RELEASED' WHERE reservation_id=?", reservationId);
        writeMovement(r.warehouseId(), r.skuId(), r.batchNo(), MovementType.RELEASE, r.reservedQty(),
                "REL:" + r.orderId());
    }

    /**
     * 质检冻结：增加 blocked，从 on_hand 锁定（不减少 on_hand，仅标记不可售）。
     * 实际模型：blocked 从 on_hand 中扣减可售，不改变 on_hand 本身。
     */
    @Transactional(rollbackFor = Exception.class)
    public void block(long warehouseId, long skuId, String batchNo, int qty, String businessKey) {
        ensureBalance(warehouseId, skuId, batchNo);
        jdbc.update("UPDATE stock_balance SET blocked_qty=blocked_qty+?, version=version+1 "
                + "WHERE warehouse_id=? AND sku_id=? AND batch_no=?", qty, warehouseId, skuId, batchNo);
        writeMovement(warehouseId, skuId, batchNo, MovementType.BLOCK, qty, businessKey);
    }

    /**
     * 解除质检冻结：减少 blocked。
     */
    @Transactional(rollbackFor = Exception.class)
    public void unblock(long warehouseId, long skuId, String batchNo, int qty, String businessKey) {
        jdbc.update("UPDATE stock_balance SET blocked_qty=blocked_qty-?, version=version+1 "
                + "WHERE warehouse_id=? AND sku_id=? AND batch_no=?", qty, warehouseId, skuId, batchNo);
        writeMovement(warehouseId, skuId, batchNo, MovementType.UNBLOCK, qty, businessKey);
    }

    /**
     * 退货入库：增加 on_hand（质检后按可再销售/报损/返供应商处理）。
     * 退款不必然增加可售库存。
     */
    @Transactional(rollbackFor = Exception.class)
    public void returnIn(long warehouseId, long skuId, String batchNo, int qty, String businessKey) {
        ensureBalance(warehouseId, skuId, batchNo);
        jdbc.update("UPDATE stock_balance SET on_hand_qty=on_hand_qty+?, version=version+1 "
                + "WHERE warehouse_id=? AND sku_id=? AND batch_no=?", qty, warehouseId, skuId, batchNo);
        writeMovement(warehouseId, skuId, batchNo, MovementType.IN, qty, businessKey);
    }

    /**
     * 查询可售库存。
     */
    @Transactional(readOnly = true)
    public int getAvailable(long warehouseId, long skuId, String batchNo) {
        StockRow row = jdbc.queryForObject(
                "SELECT on_hand_qty, reserved_qty, blocked_qty FROM stock_balance "
                        + "WHERE warehouse_id=? AND sku_id=? AND batch_no=?",
                (rs, rowNum) -> new StockRow(rs.getInt(1), rs.getInt(2), rs.getInt(3)),
                warehouseId, skuId, batchNo);
        return row.onHand() - row.reserved() - row.blocked();
    }

    private void ensureBalance(long warehouseId, long skuId, String batchNo) {
        jdbc.update(
                "INSERT IGNORE INTO stock_balance(warehouse_id, sku_id, batch_no, on_hand_qty, "
                        + "reserved_qty, blocked_qty, version) VALUES(?,?,?,0,0,0,0)",
                warehouseId, skuId, batchNo);
    }

    private StockRow lockBalance(long warehouseId, long skuId, String batchNo) {
        ensureBalance(warehouseId, skuId, batchNo);
        return jdbc.queryForObject(
                "SELECT on_hand_qty, reserved_qty, blocked_qty FROM stock_balance "
                        + "WHERE warehouse_id=? AND sku_id=? AND batch_no=? FOR UPDATE",
                (rs, rowNum) -> new StockRow(rs.getInt(1), rs.getInt(2), rs.getInt(3)),
                warehouseId, skuId, batchNo);
    }

    private void writeMovement(long warehouseId, long skuId, String batchNo, MovementType type,
                               int qty, String sourceDocId) {
        jdbc.update(
                "INSERT INTO stock_movement(movement_id, business_key, warehouse_id, sku_id, batch_no, "
                        + "movement_type, quantity_delta, source_doc_id, occurred_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?)",
                nextId(), type.name() + ":" + sourceDocId, warehouseId, skuId, batchNo,
                type.name(), qty, sourceDocId, java.sql.Timestamp.valueOf(LocalDateTime.now()));
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    private record StockRow(int onHand, int reserved, int blocked) {}
    private record ResRow(long reservationId, long orderId, long skuId, long warehouseId,
                          String batchNo, int reservedQty, String status) {}
}
