package com.lianshengtong.release.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 参数变更审批实体 (param_change_approval)
 * <p>
 * 可配置参数(editable=1)变更需双重管理员签名审批 + 链上存证。
 * 一条记录代表一次参数变更申请及其审批流转。
 * </p>
 */
@Data
@TableName("param_change_approval")
public class ParamChangeApproval implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键 */
    private String configKey;

    /** 变更前值 */
    private String oldValue;

    /** 变更后值 */
    private String newValue;

    /** 申请人 */
    private String operator;

    /** 双重管理员签名(JSON数组，至少2名) */
    private String approverSignatures;

    /** 链上存证交易哈希 */
    private String evidenceTxHash;

    /** 审批状态 0待审批 1已通过 2已拒绝 */
    private Integer status;

    /** 审批人 */
    private String approver;

    /** 审批意见 */
    private String approveComment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
