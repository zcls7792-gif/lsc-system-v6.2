package com.lianshengtong.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lsc_recovery")
public class LscRecovery {
    @TableId(type = IdType.INPUT)
    private Long recoveryId;
    private Long userId;
    private Long sourceItemId;
    private Long requiredUnit;
    private Long recoveredUnit;
    private Long satisfiedByExpiryUnit;
    private Long pendingUnit;
    private String status;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    @Version
    private Integer version;
}
