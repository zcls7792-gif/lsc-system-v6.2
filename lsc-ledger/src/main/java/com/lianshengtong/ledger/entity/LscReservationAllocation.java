package com.lianshengtong.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

@Data
@TableName("lsc_reservation_allocation")
public class LscReservationAllocation {
    @TableId(type = IdType.INPUT)
    private Long allocId;
    private Long reservationId;
    private Long availableLotId;
    private Long reservedUnit;
    private Long capturedUnit;
    private Long releasedUnit;
    @Version
    private Integer version;
}
