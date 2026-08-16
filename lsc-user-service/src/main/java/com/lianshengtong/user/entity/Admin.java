package com.lianshengtong.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员实体类 (对应 admins 表)
 *
 * @author lsc
 */
@Data
@TableName("admins")
public class Admin implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 (雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户名 (唯一) */
    private String username;

    /** 密码哈希 (BCrypt) */
    private String passwordHash;

    /** 真实姓名 */
    private String realName;

    /** 角色: super_admin / ops_admin / tech_admin / finance_admin */
    private String role;

    /** 状态 0禁用 1正常 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 最后登录IP */
    private String lastLoginIp;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
