package com.lianshengtong.release.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 释放参数变更审批 DTO
 */
public class ParamApprovalDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 参数变更申请请求 */
    @Data
    public static class ApplyRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 配置键(如 k_min/k_max/alpha) */
        private String configKey;

        /** 新值 */
        private String configValue;

        /** 申请人 */
        private String operator;

        /** 链上存证交易哈希 */
        private String evidenceTxHash;
    }

    /** 参数变更审批请求 */
    @Data
    public static class ApproveRequest implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 审批记录ID */
        private Long approvalId;

        /** 审批人 */
        private String approver;

        /** 双重管理员签名(至少2名) */
        private List<String> approverSignatures;

        /** 审批意见 */
        private String approveComment;

        /** true通过 false拒绝 */
        private Boolean approved;
    }
}
