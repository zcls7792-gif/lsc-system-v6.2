package com.zcls.lsc.task.enums;

/**
 * 第12.7章 任务调度枚举。
 */
public final class TaskEnums {

    private TaskEnums() {}

    /** 任务运行状态。 */
    public enum TaskStatus {
        PENDING, RUNNING, SUCCEEDED, FAILED, LEASE_LOST
    }

    /** Outbox 投递状态。 */
    public enum OutboxStatus {
        /** 待投递 */
        PENDING,
        /** 已投递 */
        PUBLISHED,
        /** 投递失败（可重试） */
        FAILED,
        /** 超过最大重试次数，死信 */
        DEAD
    }

    /** Inbox 消费状态。 */
    public enum InboxStatus {
        PENDING, PROCESSED, FAILED
    }

    /** 租约状态（推导，不在 DB 中持久化）。 */
    public enum LeaseState {
        HELD, EXPIRED, NOT_HELD
    }

    /** 补单任务状态。 */
    public enum BackfillStatus {
        PENDING, RUNNING, SUCCEEDED, FAILED, IGNORED
    }

    /** 补单来源类型。 */
    public enum BackfillSourceType {
        /** 来自失败的 task_run */
        TASK_RUN,
        /** 来自失败的 outbox_event */
        OUTBOX,
        /** 来自失败的 inbox_event */
        INBOX
    }

    /** 快照类型。 */
    public enum SnapshotType {
        /** 账户五桶余额 */
        ACCOUNT,
        /** 订单汇总 */
        ORDER,
        /** 库存汇总 */
        INVENTORY
    }

    /** 定时任务类型。 */
    public enum TaskType {
        /** 日释放 */
        RELEASE,
        /** 对账 */
        RECONCILE,
        /** 过期作废 */
        EXPIRE,
        /** 日终快照 */
        DAILY_SNAPSHOT,
        /** Outbox 投递 */
        OUTBOX_DISPATCH,
        /** 补单重试 */
        BACKFILL,
        /** 通知投递 */
        NOTIFICATION,
        /** 合规门禁清扫 */
        GATE_SWEEP,
        /** 合规巡检 R01-R12 */
        COMPLIANCE_INSPECTION
    }
}
