package com.lianshengtong.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * LSC 分录（事实源）（V7.7.2 第六章 6.1）
 */
@Data
@TableName("lsc_entry")
public class LscEntry {
    @TableId(type = IdType.INPUT)
    private Long entryId;
    private Long eventId;
    private Integer entrySeq;
    private Long userId;
    private Long grantLotId;
    private Long availableLotId;
    private String bucket;
    private Long deltaUnit;
    private String dispositionType;
    private String allocationRef;
    private Long beforeUnit;
    private Long afterUnit;
}
