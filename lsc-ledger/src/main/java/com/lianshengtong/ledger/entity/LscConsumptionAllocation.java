package com.lianshengtong.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

@Data
@TableName("lsc_consumption_allocation")
public class LscConsumptionAllocation {
    @TableId(type = IdType.INPUT)
    private Long consumptionId;
    private Long userId;
    private Long orderUnitAllocationId;
    private Long availableLotId;
    private Long capturedUnit;
    private Long returnedUnit;
    private Long captureEventId;
    @Version
    private Integer version;
}
