package com.lianshengtong.release.dto;

import com.lianshengtong.release.entity.gray.GrayApprovalFlow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Phase M：灰度审批 REST 请求/响应 DTO。
 */
public final class GrayApprovalDTO {
    private GrayApprovalDTO() {}

    // ============== 创建审批单 ==============
    @Data
    public static class CreateRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        @NotNull(message = "flowType 不能空")
        public GrayApprovalFlow.Type flowType;

        @NotBlank(message = "policyId 不能空")
        public String policyId;

        @NotBlank(message = "applicant 不能空")
        public String applicant;

        /** 摘要 */
        public String title;
        /** 原因 */
        public String applyReason;
        /** 审批人数（默认 2） */
        public Integer requiredApprovals;
        /** 指定审批人列表（不指定则走角色池 ROLE_RELEASE_ADMIN） */
        public List<String> approvers;

        /** WEIGHT_CHANGE → {targetWeight: 50} ；ROLLBACK → {reason:"错误率超阈值"} */
        public Map<String, Object> payload;
    }

    // ============== 审批（单节点通过 / 拒绝）==============
    @Data
    public static class ApproveRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull public Long flowId;
        @NotBlank public String approver;
        @NotNull public Boolean approved;
        public String comment;
        public String signature;
    }

    // ============== 撤销（仅 DRAFT / PENDING）==============
    @Data
    public static class CancelRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull public Long flowId;
        @NotBlank public String operator;
        public String reason;
    }

    // ============== 重试执行（EXECUTE_FAILED 时）==============
    @Data
    public static class RetryExecuteRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull public Long flowId;
        @NotBlank public String operator;
    }

    // ============== 查询过滤器 ==============
    @Data
    public static class Query implements Serializable {
        private static final long serialVersionUID = 1L;
        public String keyword;            // flowNo / title / policyId
        public String status;             // GrayApprovalFlow.Status
        public String flowType;           // GRADUATE 等
        public String applicant;
        public Integer pageNo   = 1;
        public Integer pageSize = 20;
    }

    // ============== 响应 DTO ==============
    @Data
    public static class Detail implements Serializable {
        private static final long serialVersionUID = 1L;
        public GrayApprovalFlow flow;
        public List<com.lianshengtong.release.entity.gray.GrayApprovalNode> nodes;
        public List<com.lianshengtong.release.entity.gray.GrayApprovalAudit> audits;
    }
}
