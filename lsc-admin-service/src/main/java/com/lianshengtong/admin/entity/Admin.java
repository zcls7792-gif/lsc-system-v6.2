package com.lianshengtong.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员实体 (admins 表)
 * <p>分库分表：lsc_admin 库。</p>
 * <ul>
 *   <li>role: 0超级管理员 1运营 2风控 3财务 4审核员</li>
 *   <li>status: 0禁用 1正常</li>
 * </ul>
 */
@Data
@TableName("admins")
public class Admin implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 管理员ID(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long adminId;

    /** 登录账号 */
    private String username;

    /** 密码哈希(BCrypt) */
    private String passwordHash;

    /**
     * 明文密码(仅用于接收前端入参, 不入库)
     * <p>新增/修改时由前端传入, 服务端 BCrypt 加密后写入 passwordHash。</p>
     */
    @TableField(exist = false)
    private transient String password;

    /** 真实姓名 */
    private String realName;

    /** 角色 0超管 1运营 2风控 3财务 4审核员 */
    private Integer role;

    /** 状态 0禁用 1正常 */
    private Integer status;

    /** 最后登录IP */
    private String lastLoginIp;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
