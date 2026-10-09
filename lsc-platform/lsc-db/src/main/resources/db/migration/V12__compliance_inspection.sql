-- ============================================================
-- 第九章 合规巡检 R01-R12 与 B 端风控台账
-- compliance_inspection_result: 每日巡检结果留痕
-- business_risk_log: 商户风控案件台账（窜货/虚假商户等）
-- ============================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------
-- compliance_inspection_result: 合规巡检结果
-- 每条 R01-R12 规则每次巡检产生一条记录
-- status: PASS / FAIL / ERROR
-- severity: CRITICAL / HIGH / MEDIUM / INFO
-- -----------------------------------------------------------
CREATE TABLE compliance_inspection_result (
    result_id        BIGINT       NOT NULL COMMENT '结果ID',
    business_date    DATE         NOT NULL COMMENT '业务日',
    rule_code        VARCHAR(64)  NOT NULL COMMENT '规则编码 R01-R12',
    status           VARCHAR(16)  NOT NULL COMMENT 'PASS/FAIL/ERROR',
    severity         VARCHAR(16)  NOT NULL COMMENT 'CRITICAL/HIGH/MEDIUM/INFO',
    detail           VARCHAR(1024) NULL COMMENT '检查详情',
    checked_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (result_id),
    INDEX idx_inspection_date_rule (business_date, rule_code),
    INDEX idx_inspection_status (status, severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合规巡检结果(R01-R12)';

-- -----------------------------------------------------------
-- business_risk_log: 商户风控案件台账
-- 对应 V7.7.1 方案 8.15 商户风控台账表
-- risk_type: DIVERSION(窜货) / FAKE_MERCHANT(虚假商户) / ARBITRAGE(套利) 等
-- review_status: PENDING(待复核) / CONFIRMED(确认) / REVOKED(撤销)
-- -----------------------------------------------------------
CREATE TABLE business_risk_log (
    id                 BIGINT       NOT NULL COMMENT '台账ID',
    user_id            BIGINT       NOT NULL COMMENT '商户用户ID',
    risk_type          VARCHAR(32)  NOT NULL COMMENT '风险类型:DIVERSION/FAKE_MERCHANT/ARBITRAGE',
    risk_level         TINYINT      NOT NULL DEFAULT 1 COMMENT '风险等级 1-3',
    evidence_json      VARCHAR(2048) NULL COMMENT '证据JSON',
    ai_flag            TINYINT      NOT NULL DEFAULT 0 COMMENT 'AI标记 0人工 1AI',
    review_status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/REVOKED',
    reviewer           VARCHAR(64)  NULL COMMENT '复核人',
    review_deadline    DATETIME(3)  NULL COMMENT '复核截止(AI标记后48h)',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_biz_risk_user (user_id, risk_type),
    INDEX idx_biz_risk_status (review_status, review_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户风控案件台账';
