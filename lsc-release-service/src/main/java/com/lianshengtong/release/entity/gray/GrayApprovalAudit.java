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
 * Phase M：灰度审批审计流水（不可变 append-only）。
 * <p>
 * 对 GrayApprovalFlow / GrayApprovalNode 任何状态变更都会写一条审计记录，
 * 可用于链上存证、合规复核、审批追溯。
 */
@Data
@TableName("gray_approval_audit")
public class GrayApprovalAudit implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long flowId;
    private String flowNo;
    /** 动作：FLOW_CREATED / NODE_APPROVED / NODE_REJECTED / FLOW_APPROVED / FLOW_REJECTED /
     *  FLOW_EXECUTING / FLOW_SUCCEEDED / FLOW_EXECUTE_FAILED / FLOW_CANCELLED */
    private String action;
    private String operator;
    private String detailJson;    // （可选）当时 payload / 响应 / 意见，结构化 JSON。
    private String chainTxHash;   // （可选）链上存证 HASH

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
