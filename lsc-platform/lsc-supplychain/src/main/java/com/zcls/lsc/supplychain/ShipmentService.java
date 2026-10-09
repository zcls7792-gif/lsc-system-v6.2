package com.zcls.lsc.supplychain;

import com.zcls.lsc.supplychain.enums.SupplychainEnums.ShipmentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 第9.3章 发货服务。
 *
 * C 端散单与 B 端整箱批量出库共用库存事务；超卖、短发及补发有异常工单。
 * 供应商代发保存发货单、物流及货权责任，不虚构自有库数量。
 */
@Service
public class ShipmentService {

    private final JdbcTemplate jdbc;
    private final StockService stockService;

    public ShipmentService(JdbcTemplate jdbc, StockService stockService) {
        this.jdbc = jdbc;
        this.stockService = stockService;
    }

    /**
     * 创建发货单并扣减库存（实扣）。
     * 此处假设支付已成功、预占已存在；直接 capture 预占。
     */
    @Transactional(rollbackFor = Exception.class)
    public long createShipment(long orderId, String carrier, List<ShipmentItem> items) {
        long shipmentId = nextId();
        jdbc.update(
                "INSERT INTO shipment(shipment_id, order_id, carrier, tracking_no, status, shipped_at) "
                        + "VALUES(?,?,?,?,?,?)",
                shipmentId, orderId, carrier, null, ShipmentStatus.SHIPPED.name(),
                java.sql.Timestamp.valueOf(LocalDateTime.now()));

        for (ShipmentItem item : items) {
            jdbc.update(
                    "INSERT INTO shipment_item(shipment_item_id, shipment_id, order_item_id, qty, "
                            + "warehouse_id, batch_no) VALUES(?,?,?,?,?,?)",
                    nextId(), shipmentId, item.orderItemId(), item.qty(),
                    item.warehouseId(), item.batchNo());

            // 查找该订单的库存预占并 capture
            Long reservationId = jdbc.queryForObject(
                    "SELECT reservation_id FROM stock_reservation WHERE order_id=? AND sku_id=? "
                            + "AND warehouse_id=? AND batch_no=? AND status='ACTIVE'",
                    Long.class, orderId, item.skuId(), item.warehouseId(), item.batchNo());
            if (reservationId != null) {
                stockService.capture(reservationId);
            }
        }
        return shipmentId;
    }

    /**
     * 确认收货。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deliver(long shipmentId) {
        jdbc.update("UPDATE shipment SET status='DELIVERED', delivered_at=? WHERE shipment_id=?",
                java.sql.Timestamp.valueOf(LocalDateTime.now()), shipmentId);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    public record ShipmentItem(long orderItemId, long skuId, int qty, long warehouseId, String batchNo) {}
}
