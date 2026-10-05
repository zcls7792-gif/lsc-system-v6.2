-- V8__lsc_rule_engine.sql
-- 第10.8章 风控规则引擎:规则版本管理 + 执行日志
-- 规则口径: condition_json 存储条件数组, match_mode 决定 ALL/ANY 匹配,
-- 命中规则累加 score, 总分 >= threshold 触发 action (映射 risk_case.proposed_action)

-- ============================================================
-- 风控规则表（版本化）
-- ============================================================
CREATE TABLE IF NOT EXISTS risk_rule (
    rule_id         BIGINT       NOT NULL,
    rule_code       VARCHAR(64)  NOT NULL COMMENT '规则编码(业务唯一,如 REFUND_RATE_HIGH)',
    version         INT          NOT NULL DEFAULT 1 COMMENT '规则版本号',
    name            VARCHAR(128) NOT NULL COMMENT '规则名称',
    description     VARCHAR(512)          COMMENT '规则描述',
    condition_json  JSON         NOT NULL COMMENT '条件数组: [{"field":"refund_rate","op":">=","value":0.3}]',
    match_mode      VARCHAR(8)   NOT NULL DEFAULT 'ALL' COMMENT '匹配模式: ALL / ANY',
    score           INT          NOT NULL DEFAULT 10 COMMENT '命中贡献分值',
    threshold       INT          NOT NULL DEFAULT 10 COMMENT '触发阈值(累计分)',
    action          VARCHAR(32)  NOT NULL COMMENT '触发动作(映射 ProposedAction)',
    priority        INT          NOT NULL DEFAULT 100 COMMENT '执行优先级(小者先)',
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/ACTIVE/SUPERSEDED/DISABLED',
    effective_at    DATETIME              COMMENT '生效时间',
    created_by      BIGINT       NOT NULL,
    checksum        VARCHAR(64)           COMMENT '条件+动作 SHA-256 防篡改',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (rule_id),
    UNIQUE KEY uk_rule_code_version (rule_code, version),
    KEY idx_rule_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控规则表(版本化)';

-- ============================================================
-- 规则执行日志
-- ============================================================
CREATE TABLE IF NOT EXISTS risk_rule_execution_log (
    log_id          BIGINT       NOT NULL,
    rule_id         BIGINT       NOT NULL,
    rule_code       VARCHAR(64)  NOT NULL,
    user_id         BIGINT       NOT NULL,
    matched         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否命中',
    score           INT          NOT NULL DEFAULT 0 COMMENT '本次命中贡献分',
    total_score     INT          NOT NULL DEFAULT 0 COMMENT '累计总分',
    triggered       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否触发动作',
    evidence_ref    VARCHAR(256)          COMMENT '证据引用',
    ai_flag         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否 AI 辅助判定',
    executed_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    KEY idx_log_rule (rule_id),
    KEY idx_log_user (user_id),
    KEY idx_log_executed (executed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控规则执行日志';
