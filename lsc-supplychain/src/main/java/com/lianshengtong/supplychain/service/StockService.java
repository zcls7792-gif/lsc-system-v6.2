package com.lianshengtong.supplychain.service;

import cn.hutool.core.util.IdUtil;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.supplychain.entity.StockBalance;
import com.lianshengtong.supplychain.mapper.StockBalanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存服务（V7.7.2 第九章 9.3）
 * 库存分实物 on_hand、销售预占 reserved、质检冻结 blocked。
 * 可售 = on_hand - reserved - blocked。
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockBalanceMapper balanceMapper;

    /** 销售预占库存 */
    @Transactional(rollbackFor = Exception.class)
    public void reserve(Long warehouseId, Long skuId, String batchNo, int qty) {
        StockBalance bal = balanceMapper.selectForUpdate(warehouseId, skuId, batchNo);
        if (bal == null) throw new BizException("库存不存在");
        int available = bal.getOnHandQty() - bal.getReservedQty() - bal.getBlockedQty();
        if (available < qty) throw new BizException("可售库存不足");
        bal.setReservedQty(bal.getReservedQty() + qty);
        balanceMapper.updateById(bal);
    }

    /** 支付确认扣减实际库存并消除预占 */
    @Transactional(rollbackFor = Exception.class)
    public void capture(Long warehouseId, Long skuId, String batchNo, int qty) {
        StockBalance bal = balanceMapper.selectForUpdate(warehouseId, skuId, batchNo);
        if (bal == null || bal.getReservedQty() < qty) throw new BizException("预占不足");
        bal.setOnHandQty(bal.getOnHandQty() - qty);
        bal.setReservedQty(bal.getReservedQty() - qty);
        balanceMapper.updateById(bal);
    }

    /** 解占用 */
    @Transactional(rollbackFor = Exception.class)
    public void release(Long warehouseId, Long skuId, String batchNo, int qty) {
        StockBalance bal = balanceMapper.selectForUpdate(warehouseId, skuId, batchNo);
        if (bal != null && bal.getReservedQty() >= qty) {
            bal.setReservedQty(bal.getReservedQty() - qty);
            balanceMapper.updateById(bal);
        }
    }

    /** 入库 */
    @Transactional(rollbackFor = Exception.class)
    public void stockIn(Long warehouseId, Long skuId, String batchNo, int qty) {
        StockBalance bal = balanceMapper.selectForUpdate(warehouseId, skuId, batchNo);
        if (bal == null) {
            bal = new StockBalance();
            bal.setBalanceId(IdUtil.getSnowflakeNextId());
            bal.setWarehouseId(warehouseId);
            bal.setSkuId(skuId);
            bal.setBatchNo(batchNo);
            bal.setOnHandQty(qty);
            bal.setReservedQty(0);
            bal.setBlockedQty(0);
            bal.setVersion(0);
            balanceMapper.insert(bal);
        } else {
            bal.setOnHandQty(bal.getOnHandQty() + qty);
            balanceMapper.updateById(bal);
        }
    }
}
