package com.lianshengtong.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_coupon")
public class UserCoupon {
    @TableId(type = IdType.INPUT)
    private Long couponId;
    private Long userId;
    private Long templateId;
    private Integer templateVersion;
    private Long faceCentSnapshot;
    private Long minSpendCentSnapshot;
    private String scopeSnapshotJson;
    private LocalDateTime receivedAt;
    private LocalDateTime validUntil;
    private String status;  // AVAILABLE/RESERVED/REDEEMED/EXPIRED/REVOKED
    private Long reservedOrderId;
    private Long usedOrderId;
    private Long sourceRewardId;
    private Long replacementOfCouponId;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
