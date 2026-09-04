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
 * Phase M：灰度策略审批单（gray_approval_flow）。
 * <p>
 * 一条审批单 = 一次"灰度毕业（graduate）"或"放量（weight change）"动作的审批流程。
 * 状态流转（状态机）：
 * <pre>
 * DRAFT -> PENDING_APPROVAL -> (APPROVED) -> EXECUTING -> SUCCEEDED
 *                                     |-> REJECTED  (终止)
 *                                     |-> CANCELLED (申请人主动撤销)
 *                      EXECUTING  -> EXECUTE_FAILED  (网关接口调用失败，可重试)
 * </pre>
 */
@Data
@TableName("gray_approval_flow")
public class GrayApprovalFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 审批状态 */
    public enum Status {
        DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, CANCELLED, EXECUTING, SUCCEEDED, EXECUTE_FAILED
    }

    /** 审批类型：GRADUATE（毕业上线）/ WEIGHT_CHANGE（放量）/ ROLLBACK（回滚）/ LAUNCH（首次创建并灰度） */
    public enum Type { GRADUATE, WEIGHT_CHANGE, ROLLBACK, LAUNCH }

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编号：GA + yyyyMMdd + 6位 自增 */
    private String flowNo;

    /** 审批类型 */
    private String flowType;

    /** 对应的灰度策略 ID（对应 lsc-gateway GrayPolicyStore） */
    private String policyId;

    /** 申请时希望的操作参数（JSON）：GRADUATE 空；WEIGHT_CHANGE {"targetWeight":50}；ROLLBACK {"reason":"xxx"} */
    private String payloadJson;

    /** 申请人 */
    private String applicant;

    /** 审批单标题（摘要） */
    private String title;

    /** 申请原因 */
    private String applyReason;

    /** 当前状态 */
    private String status;

    /** 要求最少审批人（默认 2；生产建议 3） */
    private Integer requiredApprovals;

    /** 已通过数量 */
    private Integer approvedCount;

    /** 总节点数（含串行/并行，这里默认并行=requiredApprovals） */
    private Integer totalNodes;

    /** 执行结果：lsc-gateway HTTP 响应体 */
    private String executeResponse;

    /** 执行耗时（ms） */
    private Long executeCostMs;

    /** 审批通过 / 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    /** 创建 / 更新时间（MP 自动填） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    private String updatedBy;
}
