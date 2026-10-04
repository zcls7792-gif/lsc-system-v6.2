package com.lianshengtong.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * LSC 赠送批次 GrantLot（V7.7.2 第五章）
 * original_grant_unit 发放后不可修改。
 * 恒等式：original = remaining_locked + frozen_locked + released_total + revoked_locked
 */
@Data
@TableName("lsc_grant_lot")
public class LscGrantLot {
    @TableId(type = IdType.INPUT)
    private Long grantLotId;
    private Long userId;
    private Long sourceItemId;
    private Long originalGrantUnit;
    private Long remainingLockedUnit;
    private Long frozenLockedUnit;
    private Long releasedTotalUnit;
    private Long revokedLockedUnit;
    private Long remainderNanoUnit;
    private LocalDate grantBusinessDate;
    private LocalDate firstReleaseDate;
    private LocalDate lastProcessedDate;
    private String state;
    private Boolean refundHold;
    private String ruleVersion;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
