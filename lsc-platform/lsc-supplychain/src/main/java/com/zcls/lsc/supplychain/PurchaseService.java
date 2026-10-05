package com.zcls.lsc.supplychain;

import com.zcls.lsc.supplychain.enums.SupplychainEnums.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 第9.2章 采购与四单匹配服务。
 *
 * 采购流程：选品询价 → 采购审批 → 供应商发货 → 质检 → 入库 → 对账 → 结算。
 * 采购、入库、质检和结算四单匹配，差异由采购及财务审批。
 * 付款账期默认 45 天，允许 30-60 天。
 */
@Service
public class PurchaseService {

    private final JdbcTemplate jdbc;
    private final StockService stockService;

    public PurchaseService(JdbcTemplate jdbc, StockService stockService) {
        this.jdbc = jdbc;
        this.stockService = stockService;
    }

    /**
     * 创建采购单。
     */
    @Transactional(rollbackFor = Exception.class)
    public String createPurchaseOrder(long supplierId, long buyerEntityId, String currency,
                                      List<PurchaseItem> items) {
        long poId = nextId();
        String poNo = "PO" + System.currentTimeMillis();
        long totalCent = items.stream().mapToLong(i -> i.unitPriceCent() * i.qty()).sum();

        jdbc.update(
                "INSERT INTO purchase_order(po_id, po_no, supplier_id, buyer_entity_id, currency, "
                        + "total_cent, status, approved_by, payment_due_date) "
                        + "VALUES(?,?,?,?,?,?,?,?,?)",
                poId, poNo, supplierId, buyerEntityId, currency, totalCent,
                PurchaseStatus.PENDING.name(), null, null);

        for (PurchaseItem item : items) {
            jdbc.update(
                    "INSERT INTO purchase_order_item(po_item_id, po_id, sku_id, qty, unit_price_cent, line_cent) "
                            + "VALUES(?,?,?,?,?,?)",
                    nextId(), poId, item.skuId(), item.qty(), item.unitPriceCent(),
                    item.unitPriceCent() * item.qty());
        }
        return poNo;
    }

    /**
     * 审批采购单。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approvePurchaseOrder(long poId, long approverId, int paymentTermDays) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update(
                "UPDATE purchase_order SET status='APPROVED', approved_by=?, payment_due_date=? "
                        + "WHERE po_id=?",
                approverId, java.sql.Date.valueOf(now.toLocalDate().plusDays(paymentTermDays)), poId);
    }

    /**
     * 供应商发货 → 收货。
     * 收货明细与采购明细关联，不支持 po_ids 字符串代替多对多。
     */
    @Transactional(rollbackFor = Exception.class)
    public long createGoodsReceipt(long poId, long warehouseId, String supplierDeliveryNo,
                                   List<ReceiptItem> items) {
        long receiptId = nextId();
        jdbc.update(
                "INSERT INTO goods_receipt(receipt_id, po_id, warehouse_id, supplier_delivery_no, "
                        + "received_at, status) VALUES(?,?,?,?,?,?)",
                receiptId, poId, warehouseId, supplierDeliveryNo,
                java.sql.Timestamp.valueOf(LocalDateTime.now()), ReceiptStatus.RECEIVED.name());

        for (ReceiptItem item : items) {
            jdbc.update(
                    "INSERT INTO goods_receipt_item(receipt_item_id, receipt_id, po_item_id, "
                            + "received_qty, batch_no) VALUES(?,?,?,?,?)",
                    nextId(), receiptId, item.poItemId(), item.receivedQty(), item.batchNo());
        }

        // 收货后状态更新
        jdbc.update("UPDATE purchase_order SET status='PARTIAL_RECEIVED' WHERE po_id=? AND status<>'RECEIVED'",
                poId);
        return receiptId;
    }

    /**
     * 质检：合格入库，不合格报损或返供应商。
     * 质检通过的数量入实物库存 on_hand。
     */
    @Transactional(rollbackFor = Exception.class)
    public long createInspection(long receiptId, String result, List<InspectionItem> items,
                                 Long inspectorId, String evidenceRef) {
        long inspectionId = nextId();
        jdbc.update(
                "INSERT INTO quality_inspection(inspection_id, receipt_id, inspector_id, result, "
                        + "evidence_ref, inspected_at) VALUES(?,?,?,?,?,?)",
                inspectionId, receiptId, inspectorId, result, evidenceRef,
                java.sql.Timestamp.valueOf(LocalDateTime.now()));

        long warehouseId = jdbc.queryForObject(
                "SELECT warehouse_id FROM goods_receipt WHERE receipt_id=?", Long.class, receiptId);

        for (InspectionItem item : items) {
            jdbc.update(
                    "INSERT INTO quality_inspection_item(qi_item_id, inspection_id, receipt_item_id, "
                            + "accepted_qty, rejected_qty, reason) VALUES(?,?,?,?,?,?)",
                    nextId(), inspectionId, item.receiptItemId(), item.acceptedQty(),
                    item.rejectedQty(), item.reason());

            // 合格数量入库（IN 流水，增加 on_hand）
            if (item.acceptedQty() > 0) {
                String batchNo = jdbc.queryForObject(
                        "SELECT batch_no FROM goods_receipt_item WHERE receipt_item_id=?",
                        String.class, item.receiptItemId());
                long skuId = jdbc.queryForObject(
                        "SELECT sku_id FROM purchase_order_item WHERE po_item_id=("
                                + "SELECT po_item_id FROM goods_receipt_item WHERE receipt_item_id=?)",
                        Long.class, item.receiptItemId());
                stockService.stockIn(warehouseId, skuId, batchNo, item.acceptedQty(),
                        "RECEIPT:" + receiptId);
            }
        }

        jdbc.update("UPDATE goods_receipt SET status='INSPECTED' WHERE receipt_id=?", receiptId);
        jdbc.update("UPDATE purchase_order SET status='INSPECTED' WHERE po_id=("
                + "SELECT po_id FROM goods_receipt WHERE receipt_id=?)", receiptId);
        return inspectionId;
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    public record PurchaseItem(long skuId, int qty, long unitPriceCent) {}
    public record ReceiptItem(long poItemId, int receivedQty, String batchNo) {}
    public record InspectionItem(long receiptItemId, int acceptedQty, int rejectedQty, String reason) {}
}
