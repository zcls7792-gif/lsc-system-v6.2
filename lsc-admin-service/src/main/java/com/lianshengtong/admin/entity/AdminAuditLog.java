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
 * 管理员操作审计日志实体 (admin_audit_logs 表)
 * <p>分库分表：lsc_admin 库，按 admin_id 取模 32。配合 AI 异常操作监控。</p>
 */
@Data
@TableName("admin_audit_logs")
public class AdminAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键(雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 管理员ID */
    private Long adminId;

    /** 操作模块 user/merchant/product/b2b/risk/release/evidence/param等 */
    private String module;

    /** 操作类型 login/audit/punish/config/update等 */
    private String action;

    /** 操作目标ID */
    private String targetId;

    /** 操作详情(JSON) */
    private String detail;

    /** 操作IP */
    private String clientIp;

    /** AI异常标记 0正常 1可疑 2异常 */
    private Integer aiFlag;

    /** AI风险评分 */
    private Integer aiScore;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
