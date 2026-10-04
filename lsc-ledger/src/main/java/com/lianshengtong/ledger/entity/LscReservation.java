package com.lianshengtong.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lsc_reservation")
public class LscReservation {
    @TableId(type = IdType.INPUT)
    private Long reservationId;
    private Long userId;
    private Long orderId;
    private String status;
    private Long reservedTotalUnit;
    private LocalDateTime expiresAt;
    private String channelCloseState;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
