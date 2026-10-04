package com.lianshengtong.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("referral")
public class Referral {
    @TableId(type = IdType.INPUT)
    private Long referralId;
    private Long referredUserId;
    private Long referrerUserId;
    private LocalDateTime boundAt;
    private Long firstOrderId;
    private String triggerStatus;  // PENDING/TRIGGERED/CANCELED
    private LocalDateTime createdAt;
}
