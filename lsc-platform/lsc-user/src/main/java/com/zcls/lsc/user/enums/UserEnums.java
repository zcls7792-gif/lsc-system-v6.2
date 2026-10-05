package com.zcls.lsc.user.enums;

/**
 * 第12.2章 用户主体与权限枚举。
 */
public final class UserEnums {

    private UserEnums() {}

    /** 法律主体角色。 */
    public enum EntityRole {
        /** 平台方 */
        PLATFORM,
        /** 销售方/商家 */
        SELLER,
        /** 收款方 */
        PAYEE,
        /** 开票方 */
        INVOICE,
        /** 权益义务承担方 */
        BENEFIT_OBLIGOR
    }

    /** 法律主体状态。 */
    public enum EntityStatus {
        /** 待审核 */
        PENDING,
        /** 已认证 */
        VERIFIED,
        /** 已驳回 */
        REJECTED,
        /** 已停用 */
        DISABLED
    }

    /** 用户类型。 */
    public enum UserType {
        /** 未认证 */
        UNVERIFIED,
        /** C 端消费者 */
        C,
        /** B 端商户 */
        B
    }

    /** 账户状态。 */
    public enum AccountStatus {
        NORMAL, FROZEN, CLOSED
    }

    /** B 端资质状态。 */
    public enum BusinessStatus {
        /** 未申请 */
        NONE,
        /** 审核中 */
        PENDING,
        /** 已通过 */
        APPROVED,
        /** 已驳回 */
        REJECTED,
        /** 已暂停 */
        SUSPENDED,
        /** 已过期 */
        EXPIRED
    }

    /** 资质审核状态。 */
    public enum AuditStatus {
        PENDING, APPROVED, REJECTED
    }

    /** 角色作用域类型。 */
    public enum ScopeType {
        /** 全局 */
        GLOBAL,
        /** 平台 */
        PLATFORM,
        /** 商家 */
        MERCHANT,
        /** 仓库 */
        WAREHOUSE
    }
}
