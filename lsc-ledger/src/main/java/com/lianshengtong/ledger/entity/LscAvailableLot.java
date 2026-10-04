package com.lianshengtong.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * LSC 可用批次 AvailableLot（V7.7.2 第六章 6.3）
 * 恒等式：issued + restored = available + reserved + frozen + consumed + expired + revoked
 */
@Data
@TableName("lsc_available_lot")
public class LscAvailableLot {
    @TableId(type = IdType.INPUT)
    private Long availableLotId;
    private Long userId;
    private Long sourceGrantLotId;
    private String originType;
    private Long sourceEventId;
    private Long lotSequence;
    private Long issuedUnit;
    private Long restoredUnit;
    private Long availableUnit;
    private Long reservedUnit;
    private Long frozenUnit;
    private Long consumedUnit;
    private Long expiredUnit;
    private Long revokedUnit;
    private LocalDateTime availableAt;
    private LocalDateTime expireAt;
    private LocalDateTime terminalAt;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
