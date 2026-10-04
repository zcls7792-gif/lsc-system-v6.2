package com.lianshengtong.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("business_profile")
public class BusinessProfile {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private String entityName;
    private String licenseNo;
    private LocalDate licenseExpiry;
    private String businessStatus;  // NONE/PENDING/APPROVED/REJECTED/SUSPENDED/EXPIRED
    private Integer approvedVersion;
    private LocalDateTime approvedAt;
    private LocalDateTime qualificationExpireAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
