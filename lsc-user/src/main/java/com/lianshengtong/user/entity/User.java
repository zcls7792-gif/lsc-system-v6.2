package com.lianshengtong.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private String mobileEnc;
    private String mobileLookupHash;
    private String nickname;
    private String userType;       // UNVERIFIED/C/B
    private String accountStatus;  // ACTIVE/DISABLED
    private Long referrerUserId;
    private Long firstQualifiedOrderId;
    private String privacyVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
