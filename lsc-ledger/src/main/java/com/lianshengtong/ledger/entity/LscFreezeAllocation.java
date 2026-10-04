package com.lianshengtong.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lsc_freeze_allocation")
public class LscFreezeAllocation {
    @TableId(type = IdType.INPUT)
    private Long freezeId;
    private Long userId;
    private Long caseId;
    private String sourceBucket;
    private Long grantLotId;
    private Long availableLotId;
    private Long frozenUnit;
    private Long releasedUnit;
    private Long revokedUnit;
    private Long expiredUnit;
    private String status;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
