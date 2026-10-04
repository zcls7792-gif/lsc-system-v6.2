package com.lianshengtong.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * LSC 账户（V7.7.2 第六章 6.2）
 * 5桶：locked / available / reserved / frozen_locked / frozen_available
 * 同一 unit 任一时刻只能属于一个桶。
 */
@Data
@TableName("lsc_account")
public class LscAccount {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private Long lockedUnit;
    private Long availableUnit;
    private Long reservedUnit;
    private Long frozenLockedUnit;
    private Long frozenAvailableUnit;
    private Long pendingRecoveryUnit;
    private Long lastEventSeq;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LscAccount zero(Long userId) {
        LscAccount a = new LscAccount();
        a.setUserId(userId);
        a.setLockedUnit(0L);
        a.setAvailableUnit(0L);
        a.setReservedUnit(0L);
        a.setFrozenLockedUnit(0L);
        a.setFrozenAvailableUnit(0L);
        a.setPendingRecoveryUnit(0L);
        a.setLastEventSeq(0L);
        a.setVersion(0);
        return a;
    }
}
