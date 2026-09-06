package com.lianshengtong.lsc.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号 */
    private String mobile;

    /** 密码 */
    private String password;

    /** 用户类型：0消费者 1商家 */
    private Integer userType;

    /** 真实姓名 */
    private String realName;

    /** 身份证号 */
    private String idCard;

    /** 推荐人ID */
    private Long referrerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
