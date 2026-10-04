package com.lianshengtong.supplychain.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.common.result.R;
import com.lianshengtong.supplychain.entity.PurchaseOrder;
import com.lianshengtong.supplychain.entity.StockBalance;
import com.lianshengtong.supplychain.entity.Supplier;
import com.lianshengtong.supplychain.mapper.PurchaseOrderMapper;
import com.lianshengtong.supplychain.mapper.StockBalanceMapper;
import com.lianshengtong.supplychain.mapper.SupplierMapper;
import com.lianshengtong.supplychain.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应链接口（V7.7.2 第九章）
 */
@RestController
@RequestMapping("/v1/supplychain")
@RequiredArgsConstructor
public class SupplyChainController {

    private final SupplierMapper supplierMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final StockBalanceMapper stockBalanceMapper;
    private final StockService stockService;

    /** 供应商列表 */
    @GetMapping("/suppliers")
    public R<List<Supplier>> listSuppliers() {
        return R.ok(supplierMapper.selectList(null));
    }

    /** 采购单列表 */
    @GetMapping("/purchase-orders")
    public R<List<PurchaseOrder>> listPurchaseOrders() {
        return R.ok(purchaseOrderMapper.selectList(null));
    }

    /** 库存查询（含可售计算） */
    @GetMapping("/stock")
    public R<List<Map<String, Object>>> listStock(@RequestParam(required = false) Long skuId) {
        LambdaQueryWrapper<StockBalance> w = new LambdaQueryWrapper<>();
        if (skuId != null) w.eq(StockBalance::getSkuId, skuId);
        List<StockBalance> list = stockBalanceMapper.selectList(w);
        return R.ok(list.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("balanceId", s.getBalanceId());
            m.put("skuId", s.getSkuId());
            m.put("warehouseId", s.getWarehouseId());
            m.put("onHandQty", s.getOnHandQty());
            m.put("reservedQty", s.getReservedQty());
            m.put("blockedQty", s.getBlockedQty());
            m.put("availableQty", s.getOnHandQty() - s.getReservedQty() - s.getBlockedQty());
            return m;
        }).toList());
    }

    /** 库存入库 */
    @PostMapping("/stock/in")
    public R<Void> stockIn(@RequestParam Long skuId,
                           @RequestParam Long warehouseId,
                           @RequestParam String batchNo,
                           @RequestParam Integer qty) {
        stockService.stockIn(warehouseId, skuId, batchNo, qty);
        return R.ok();
    }
}
