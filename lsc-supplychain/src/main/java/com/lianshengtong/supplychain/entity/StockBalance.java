package com.lianshengtong.supplychain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_balance")
public class StockBalance {
    @TableId(type = IdType.INPUT)
    private Long balanceId;
    private Long warehouseId;
    private Long skuId;
    private String batchNo;
    private Integer onHandQty;
    private Integer reservedQty;
    private Integer blockedQty;
    @Version
    private Integer version;
    private LocalDateTime updatedAt;
}
