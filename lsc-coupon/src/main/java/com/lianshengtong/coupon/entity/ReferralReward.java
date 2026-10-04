package com.lianshengtong.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("referral_reward")
public class ReferralReward {
    @TableId(type = IdType.INPUT)
    private Long rewardId;
    private Long referralId;
    private Long referrerUserId;
    private Integer successSequence;
    private Integer rewardTier;        // 1-4
    private Long firstOrderId;
    private Long couponId;
    private String status;
    private String sourceRefundStatus;
    private LocalDateTime issuedAt;
}
