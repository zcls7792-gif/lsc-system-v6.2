package com.zcls.lsc.api.controller;

import com.zcls.lsc.api.common.ApiResponse;
import com.zcls.lsc.supplychain.*;
import com.zcls.lsc.supplychain.enums.SupplychainEnums.SupplierStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 第13章 供应链管理接口（供应商端 + 管理端）。
 */
@RestController
@RequestMapping("/v1")
public class SupplychainController {

    private final SupplierService supplierService;
    private final PurchaseService purchaseService;
    private final StockService stockService;
    private final ShipmentService shipmentService;
    private final SettlementService settlementService;

    public SupplychainController(SupplierService supplierService, PurchaseService purchaseService,
                                 StockService stockService, ShipmentService shipmentService,
                                 SettlementService settlementService) {
        this.supplierService = supplierService;
        this.purchaseService = purchaseService;
        this.stockService = stockService;
        this.shipmentService = shipmentService;
        this.settlementService = settlementService;
    }

    // ===== 供应商 =====
    @PostMapping("/admin/suppliers")
    public ApiResponse<Long> createSupplier(
            @RequestParam String legalName,
            @RequestParam String licenseNo,
            @RequestParam String contactEnc,
            @RequestParam String bankAccountEnc,
            @RequestParam(defaultValue = "45") int paymentTermDays) {
        return ApiResponse.ok(supplierService.createSupplier(legalName, licenseNo, contactEnc,
                bankAccountEnc, paymentTermDays));
    }

    @PostMapping("/admin/suppliers/{id}/bank-account")
    public ApiResponse<Void> changeBankAccount(
            @PathVariable long id,
            @RequestParam String newBankAccountEnc,
            @RequestParam long reviewerId,
            @RequestParam long createdBy) {
        supplierService.changeBankAccount(id, newBankAccountEnc, reviewerId, createdBy);
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/suppliers/{id}/status")
    public ApiResponse<Void> updateSupplierStatus(@PathVariable long id, @RequestParam String status) {
        supplierService.updateStatus(id, SupplierStatus.valueOf(status));
        return ApiResponse.ok(null);
    }

    // ===== 采购 =====
    @PostMapping("/admin/purchase-orders")
    public ApiResponse<String> createPurchaseOrder(
            @RequestParam long supplierId,
            @RequestParam long buyerEntityId,
            @RequestParam(defaultValue = "CNY") String currency,
            @RequestBody List<PurchaseService.PurchaseItem> items) {
        return ApiResponse.ok(purchaseService.createPurchaseOrder(supplierId, buyerEntityId, currency, items));
    }

    @PostMapping("/admin/purchase-orders/{id}/approve")
    public ApiResponse<Void> approvePurchaseOrder(
            @PathVariable long id,
            @RequestParam long approverId,
            @RequestParam(defaultValue = "45") int paymentTermDays) {
        purchaseService.approvePurchaseOrder(id, approverId, paymentTermDays);
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/goods-receipts")
    public ApiResponse<Long> createGoodsReceipt(
            @RequestParam long poId,
            @RequestParam long warehouseId,
            @RequestParam(required = false) String supplierDeliveryNo,
            @RequestBody List<PurchaseService.ReceiptItem> items) {
        return ApiResponse.ok(purchaseService.createGoodsReceipt(poId, warehouseId, supplierDeliveryNo, items));
    }

    @PostMapping("/admin/inspections")
    public ApiResponse<Long> createInspection(
            @RequestParam long receiptId,
            @RequestParam String result,
            @RequestBody List<PurchaseService.InspectionItem> items,
            @RequestParam(required = false) Long inspectorId,
            @RequestParam(required = false) String evidenceRef) {
        return ApiResponse.ok(purchaseService.createInspection(receiptId, result, items, inspectorId, evidenceRef));
    }

    // ===== 库存 =====
    @GetMapping("/admin/stock/available")
    public ApiResponse<Integer> getAvailableStock(
            @RequestParam long warehouseId,
            @RequestParam long skuId,
            @RequestParam String batchNo) {
        return ApiResponse.ok(stockService.getAvailable(warehouseId, skuId, batchNo));
    }

    @PostMapping("/admin/stock/block")
    public ApiResponse<Void> blockStock(
            @RequestParam long warehouseId,
            @RequestParam long skuId,
            @RequestParam String batchNo,
            @RequestParam int qty,
            @RequestParam String businessKey) {
        stockService.block(warehouseId, skuId, batchNo, qty, businessKey);
        return ApiResponse.ok(null);
    }

    // ===== 发货 =====
    @PostMapping("/admin/shipments")
    public ApiResponse<Long> createShipment(
            @RequestParam long orderId,
            @RequestParam String carrier,
            @RequestBody List<ShipmentService.ShipmentItem> items) {
        return ApiResponse.ok(shipmentService.createShipment(orderId, carrier, items));
    }

    // ===== 结算 =====
    @PostMapping("/admin/settlements")
    public ApiResponse<String> createSettlement(
            @RequestParam long supplierId,
            @RequestParam long entityId,
            @RequestBody List<SettlementService.SettlementAlloc> allocs) {
        return ApiResponse.ok(settlementService.createSettlement(supplierId, entityId, allocs));
    }

    @PostMapping("/admin/settlements/{id}/pay")
    public ApiResponse<Void> paySettlement(
            @PathVariable long id,
            @RequestParam String paymentNo,
            @RequestParam long amountCent,
            @RequestParam(required = false) String bankTradeNo) {
        settlementService.paySettlement(id, paymentNo, amountCent, bankTradeNo);
        return ApiResponse.ok(null);
    }
}
