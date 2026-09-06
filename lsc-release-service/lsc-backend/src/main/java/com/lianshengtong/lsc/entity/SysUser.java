package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String mobile;
    private String password;
    private Integer userType; // 0=消费者, 1=商家
    private String realName;
    private String idCard;
    private Long referrerId;
    private LocalDateTime createdAt;
}
