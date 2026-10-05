-- ============================================================
-- 第12.7章 任务调度与可靠性
-- lease: 分布式租约(lease fencing)
-- backfill_task: 补单/重试任务
-- daily_snapshot: 账户日终快照(通用)
-- ============================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------
-- lease: 分布式租约
-- 同一 lease_key 仅一个持有者；fencing_token 单调递增防止旧执行者提交
-- -----------------------------------------------------------
CREATE TABLE lease (
    lease_key         VARCHAR(128) NOT NULL COMMENT '租约键(任务名/资源名)',
    lease_owner       VARCHAR(128) NOT NULL COMMENT '持有者标识(节点ID:线程)',
    fencing_token     BIGINT       NOT NULL DEFAULT 0 COMMENT '单调递增fencing token',
    acquired_at       DATETIME(3)  NOT NULL COMMENT '获取时间',
    expires_at        DATETIME(3)  NOT NULL COMMENT '过期时间',
    last_renew_at     DATETIME(3)  NOT NULL COMMENT '最后续约时间',
    version           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (lease_key),
    CONSTRAINT chk_lease CHECK (fencing_token >= 0 AND version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分布式租约(lease fencing)';

-- -----------------------------------------------------------
-- backfill_task: 补单/重试任务
-- 对失败的 task_run / outbox_event 进行重试，保留幂等键
-- -----------------------------------------------------------
CREATE TABLE backfill_task (
    backfill_id       BIGINT       NOT NULL COMMENT '补单ID',
    source_type       VARCHAR(32)  NOT NULL COMMENT '来源类型: TASK_RUN/OUTBOX/INBOX',
    source_id         VARCHAR(128) NOT NULL COMMENT '来源记录ID',
    business_date     DATE         NULL COMMENT '关联业务日',
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCEEDED/FAILED/IGNORED',
    attempts          INT          NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    max_attempts      INT          NOT NULL DEFAULT 10 COMMENT '最大尝试次数',
    next_attempt_at   DATETIME(3)  NULL COMMENT '下次尝试时间',
    last_error        VARCHAR(1024) NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (backfill_id),
    UNIQUE KEY uk_backfill_source (source_type, source_id),
    INDEX idx_backfill_status (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补单/重试任务';

-- -----------------------------------------------------------
-- daily_snapshot: 账户日终快照(通用)
-- 每日业务日 D 对各用户账户五桶余额进行封账快照，用于审计与对账
-- -----------------------------------------------------------
CREATE TABLE daily_snapshot (
    snapshot_id       BIGINT       NOT NULL COMMENT '快照ID',
    business_date     DATE         NOT NULL COMMENT '业务日',
    snapshot_type     VARCHAR(32)  NOT NULL DEFAULT 'ACCOUNT' COMMENT '快照类型: ACCOUNT/ORDER/INVENTORY',
    user_id           BIGINT       NOT NULL COMMENT '用户ID',
    locked_unit       BIGINT       NOT NULL DEFAULT 0,
    available_unit    BIGINT       NOT NULL DEFAULT 0,
    reserved_unit     BIGINT       NOT NULL DEFAULT 0,
    frozen_locked_unit BIGINT      NOT NULL DEFAULT 0,
    frozen_available_unit BIGINT   NOT NULL DEFAULT 0,
    last_event_seq    BIGINT       NOT NULL DEFAULT 0 COMMENT '封账时 last_event_seq',
    snapshot_hash     VARCHAR(64)  NOT NULL COMMENT '快照哈希(防篡改)',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uk_daily_snapshot (business_date, snapshot_type, user_id),
    CONSTRAINT chk_daily_snapshot CHECK (
        locked_unit >= 0
        AND available_unit >= 0
        AND reserved_unit >= 0
        AND frozen_locked_unit >= 0
        AND frozen_available_unit >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户日终快照(封账,防篡改)';
