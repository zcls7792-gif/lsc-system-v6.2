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
 * 用户实体类 (对应 users 表)
 * 分库分表按 user_id 取模 32
 *
 * @author lsc
 */
@Data
@TableName("users")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID (雪花算法生成) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long userId;

    /** 用户类型 0消费者会员 1商家会员 */
    private Integer userType;

    /** 手机号 (唯一) */
    private String mobile;

    /** 密码哈希 (BCrypt) */
    private String passwordHash;

    /** 昵称 */
    private String nickname;

    /** 头像URL */
    private String avatarUrl;

    /** 是否已实名 0未认证 1已认证 */
    private Integer isVerified;

    /** 真实姓名 (加密存储) */
    private String realName;

    /** 身份证号 (AES-256加密) */
    private String idCardNo;

    /** 推荐人ID (唯一外键约束，严格一级) */
    private Long referrerId;

    /** 状态 0禁用 1正常 */
    private Integer status;

    /** 推荐码 (用户成为推荐人后生成, 默认取user_id) */
    private String referralCode;

    /** 注册时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
