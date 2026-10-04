package com.lianshengtong.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lsc_recovery_allocation")
public class LscRecoveryAllocation {
    @TableId(type = IdType.INPUT)
    private Long allocId;
    private Long recoveryId;
    private Long sourceEntryId;
    private Long sourceAvailableLotId;
    private String satisfactionType;
    private Long amountUnit;
    private Long eventId;
    private LocalDateTime createdAt;
}
