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
    private Integer userType; // 0=消费者会员, 1=商家会员
    private String realName;
    private String idCard;
    private Integer isVerified; // 实名认证状态 0=未认证,1=已认证
    private Long referrerId;
    private Integer firstOrderCompleted; // 首单是否已完成 0=否,1=是
    private LocalDateTime createdAt;
}
