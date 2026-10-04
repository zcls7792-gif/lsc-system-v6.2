package com.lianshengtong.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("referral_counter")
public class ReferralCounter {
    @TableId(type = IdType.INPUT)
    private Long referrerUserId;
    private Integer lastSuccessSequence;
    private Integer version;
    private LocalDateTime updatedAt;
}
