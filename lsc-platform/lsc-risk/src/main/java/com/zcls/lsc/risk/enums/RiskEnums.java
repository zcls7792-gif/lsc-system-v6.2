package com.zcls.lsc.risk.enums;

/**
 * 第10/11章 风控与配置审计枚举。
 */
public final class RiskEnums {

    private RiskEnums() {}

    /** 风控案件复核状态。 */
    public enum CaseReviewStatus {
        /** 待复核（48 小时内必须复核） */
        PENDING,
        /** 已复核-维持原处置 */
        CONFIRMED,
        /** 已复核-撤销处置 */
        REVOKED,
        /** 已复核-降级处置 */
        DOWNGRADED
    }

    /** 风控案件处置动作。 */
    public enum ProposedAction {
        /** 仅提示（不阻断业务，不立案） */
        ALERT,
        /** 人工复核（阻塞业务，立案待审核） */
        REVIEW,
        /** 冻结可用余额 */
        FREEZE_AVAILABLE,
        /** 冻结全部权益 */
        FREEZE_ALL,
        /** 限制下单 */
        BLOCK_ORDER,
        /** 限制提现/退款 */
        BLOCK_REFUND
    }

    /** 申诉状态。 */
    public enum AppealStatus {
        /** 已提交（5 个工作日内答复） */
        SUBMITTED,
        /** 受理中 */
        IN_REVIEW,
        /** 已答复-维持 */
        UPHELD,
        /** 已答复-撤销 */
        OVERTURNED,
        /** 已答复-部分撤销 */
        PARTIALLY_OVERTURNED
    }

    /** 配置变更工单状态。 */
    public enum ConfigChangeStatus {
        /** 待审批 */
        PENDING,
        /** 已批准待生效 */
        APPROVED,
        /** 已生效 */
        APPLIED,
        /** 已驳回 */
        REJECTED,
        /** 已撤回 */
        WITHDRAWN
    }

    /** 配置版本状态。 */
    public enum ConfigVersionStatus {
        /** 当前生效 */
        ACTIVE,
        /** 历史版本（已被新版本取代） */
        SUPERSEDED,
        /** 待生效（effective_at 未到） */
        PENDING
    }

    /** 合规门禁状态。 */
    public enum GateStatus {
        /** 待批准 */
        PENDING,
        /** 已批准（在 expiry_at 前有效） */
        APPROVED,
        /** 已拒绝 */
        REJECTED,
        /** 已过期 */
        EXPIRED,
        /** 已撤销 */
        REVOKED
    }

    /** 通知投递状态。 */
    public enum DeliveryStatus {
        /** 待发送 */
        PENDING,
        /** 已发送 */
        SENT,
        /** 发送失败（可重试） */
        FAILED,
        /** 永久失败（超过最大重试次数） */
        DEAD
    }

    /** 管理员审计日志结果。 */
    public enum AuditResult { SUCCESS, FAILURE }

    /** 合规门禁用途。 */
    public enum GateScope {
        /** AI 风控模型启用 */
        AI_RISK_ENABLE,
        /** AI 风控模型下线 */
        AI_RISK_DISABLE,
        /** 上线发布 */
        RELEASE_PUBLISH,
        /** 紧急回滚 */
        EMERGENCY_ROLLBACK
    }

    /** 规则状态。 */
    public enum RuleStatus {
        /** 草稿 */
        DRAFT,
        /** 生效中 */
        ACTIVE,
        /** 已被新版本取代 */
        SUPERSEDED,
        /** 已停用 */
        DISABLED
    }

    /** 规则匹配模式。 */
    public enum MatchMode {
        /** 所有条件都满足才命中 */
        ALL,
        /** 任一条件满足即命中 */
        ANY
    }

    /** 条件运算符。 */
    public enum ConditionOp {
        EQ, NE, GT, GTE, LT, LTE, IN, NOT_IN, CONTAINS
    }
}
