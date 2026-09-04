package com.lianshengtong.release.entity.gray;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Phase M：单个审批节点（对应一个审批人对审批单的操作流水）。
 */
@Data
@TableName("gray_approval_node")
public class GrayApprovalNode implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 节点状态：WAITING/APPROVED/REJECTED/SKIPPED */
    public enum NodeStatus { WAITING, APPROVED, REJECTED, SKIPPED }

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long flowId;      // 关联 GrayApprovalFlow.id
    private Integer nodeOrder;
    private String approverRole; // 如 ROLE_RELEASE_ADMIN
    private String approver;     // 实际审批人（用户名/邮箱）
    private String nodeStatus;   // WAITING / APPROVED / REJECTED
    private String comment;      // 审批意见
    private String signature;    // 可选：双重签名 base64 / 操作人密码哈希（仅合规时存）

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime decidedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
