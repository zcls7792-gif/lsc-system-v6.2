-- ============================================================
-- 第12.6章 日释放与对账
-- release_day_snapshot: 业务日D使用D-1日封账快照
-- release_lot_result: 每Lot每日唯一结果
-- ============================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------
-- release_day_snapshot: 日释放快照
-- 状态: COLLECTING / SEALED / INVALID
-- w_ppm = floor(max(N,0)*1e6 / B); B=0 时 w=0,记录 ZERO_DENOMINATOR
-- -----------------------------------------------------------
CREATE TABLE release_day_snapshot (
    snapshot_id       BIGINT       NOT NULL COMMENT '快照ID',
    business_date     DATE         NOT NULL COMMENT '业务日D',
    previous_date     DATE         NOT NULL COMMENT 'D-1',
    consumed_unit     BIGINT       NOT NULL DEFAULT 0 COMMENT '当日成功核销 unit',
    expired_unit      BIGINT       NOT NULL DEFAULT 0 COMMENT '当日到期作废 unit',
    returned_unit     BIGINT       NOT NULL DEFAULT 0 COMMENT '退款返还入批次 unit',
    denominator_unit  BIGINT       NOT NULL DEFAULT 0 COMMENT 'D-1 日末全部 Available Lot 未终结余额(B)',
    raw_net_unit      BIGINT       NOT NULL DEFAULT 0 COMMENT '原始净消耗 N(可负,供审计)',
    w_ppm             BIGINT       NOT NULL DEFAULT 0 COMMENT '周转率 ppm',
    rate_ppb          BIGINT       NOT NULL DEFAULT 0 COMMENT '当日释放率 ppb',
    config_version    BIGINT       NOT NULL COMMENT '配置版本',
    snapshot_hash     VARCHAR(64)  NOT NULL COMMENT '快照哈希(防篡改)',
    status            VARCHAR(16)  NOT NULL DEFAULT 'COLLECTING',
    sealed_at         DATETIME(3)  NULL,
    anomaly_flags     VARCHAR(256) NULL COMMENT '异常标记: ZERO_DENOMINATOR 等',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uk_release_snapshot_date (business_date),
    CONSTRAINT chk_release_snapshot CHECK (
        denominator_unit >= 0
        AND w_ppm >= 0
        AND rate_ppb >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日释放快照(D-1封账,w_ppm/rate_ppb)';

-- -----------------------------------------------------------
-- release_lot_result: 每 Lot 每日释放结果
-- 唯一键 grant_lot_id + business_date
-- skip_reason: FROZEN / REFUND_HOLD / FAULT_PENDING / ALREADY_RELEASED / ZERO_QUOTA
-- -----------------------------------------------------------
CREATE TABLE release_lot_result (
    result_id         BIGINT       NOT NULL COMMENT '结果ID',
    grant_lot_id      BIGINT       NOT NULL COMMENT '赠送批次ID',
    business_date     DATE         NOT NULL COMMENT '业务日',
    snapshot_version  BIGINT       NOT NULL COMMENT '快照版本',
    rate_ppb          BIGINT       NOT NULL COMMENT '当日释放率 ppb',
    remainder_before  BIGINT       NOT NULL COMMENT '释放前余数(0..999999999)',
    remainder_after   BIGINT       NOT NULL COMMENT '释放后余数',
    released_unit     BIGINT       NOT NULL DEFAULT 0 COMMENT '实际释放量(封顶于 remaining_locked)',
    skip_reason       VARCHAR(32)  NULL COMMENT '跳过原因(非成功时)',
    event_id          BIGINT       NULL COMMENT '释放事件ID',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (result_id),
    UNIQUE KEY uk_release_lot_result (grant_lot_id, business_date),
    INDEX idx_release_lot_result_date (business_date, skip_reason),
    CONSTRAINT chk_release_lot_result CHECK (
        released_unit >= 0
        AND remainder_before >= 0
        AND remainder_before < 1000000000
        AND remainder_after >= 0
        AND remainder_after < 1000000000
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每Lot每日释放结果(唯一键防重跑)';

-- -----------------------------------------------------------
-- task_run: 任务执行记录
-- 唯一任务类型与业务日期的主运行记录,重试子运行留痕
-- -----------------------------------------------------------
CREATE TABLE task_run (
    task_id          BIGINT       NOT NULL COMMENT '任务ID',
    task_type        VARCHAR(32)  NOT NULL COMMENT '任务类型: RELEASE/RECONCILE/EXPIRE 等',
    business_date    DATE         NOT NULL COMMENT '业务日',
    run_id           VARCHAR(64)  NOT NULL COMMENT '运行实例ID',
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCEEDED/FAILED/LEASE_LOST',
    `cursor`         BIGINT       NULL COMMENT '断点游标',
    lease_owner      VARCHAR(128) NULL COMMENT '租约持有者',
    fencing_token    BIGINT       NOT NULL DEFAULT 0 COMMENT 'fencing token(防止旧执行器提交)',
    started_at       DATETIME(3)  NULL,
    finished_at      DATETIME(3)  NULL,
    error_summary    VARCHAR(1024) NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_task_run_type_date (task_type, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行记录(租约与fencing)';

-- -----------------------------------------------------------
-- reconciliation_run / reconciliation_difference: 对账
-- scope_type: GLOBAL / USER / ORDER / LOT
-- -----------------------------------------------------------
CREATE TABLE reconciliation_run (
    recon_id          BIGINT       NOT NULL COMMENT '对账ID',
    scope_type        VARCHAR(16)  NOT NULL,
    scope_id          BIGINT       NOT NULL,
    business_date     DATE         NOT NULL,
    cutoff_event_seq  BIGINT       NULL COMMENT '截止事件序号',
    status            VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
    difference_count  INT          NOT NULL DEFAULT 0,
    report_ref        VARCHAR(256) NULL,
    resolved_by       BIGINT       NULL,
    resolved_at       DATETIME(3)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (recon_id),
    INDEX idx_recon_scope (scope_type, scope_id),
    INDEX idx_recon_date (business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账运行记录';

CREATE TABLE reconciliation_difference (
    diff_id           BIGINT       NOT NULL COMMENT '差异ID',
    recon_id          BIGINT       NOT NULL,
    entity_type       VARCHAR(32)  NOT NULL,
    entity_id         BIGINT       NOT NULL,
    expected_value    VARCHAR(128) NOT NULL,
    actual_value      VARCHAR(128) NOT NULL,
    category          VARCHAR(32)  NOT NULL,
    blocking_scope    VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER/GLOBAL',
    repair_event_id   BIGINT       NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (diff_id),
    INDEX idx_diff_recon (recon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账差异(区分阻断范围)';

-- -----------------------------------------------------------
-- Outbox / Inbox: 事务消息(第11.2章)
-- outbox 与业务一同提交,投递至少一次;消费端 inbox 唯一约束保证重复安全
-- -----------------------------------------------------------
CREATE TABLE outbox_event (
    event_id          BIGINT       NOT NULL,
    topic             VARCHAR(128) NOT NULL,
    aggregate_id      VARCHAR(64)  NOT NULL,
    aggregate_version BIGINT       NOT NULL,
    payload_json      JSON         NOT NULL,
    schema_version    INT          NOT NULL DEFAULT 1,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts          INT          NOT NULL DEFAULT 0,
    next_attempt_at   DATETIME(3)  NULL,
    published_at      DATETIME(3)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (event_id),
    INDEX idx_outbox_status (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务消息Outbox(业务同事务提交)';

CREATE TABLE inbox_event (
    consumer_name     VARCHAR(64)  NOT NULL,
    event_id          BIGINT       NOT NULL,
    request_hash      VARCHAR(64)  NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    processed_at      DATETIME(3)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (consumer_name, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务消息Inbox(消费端幂等)';
