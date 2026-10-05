-- ============================================================
-- 第12.7章 券与推荐
-- 券模板版本化,用户券领取时固化关键属性
-- 推荐人串行 counter + success_sequence,解决并发发券档位重复
-- ============================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------
-- coupon_template_version: 券模板版本
-- 修改生成新版本,用户券不受模板后续修改影响
-- -----------------------------------------------------------
CREATE TABLE coupon_template_version (
    template_id        BIGINT       NOT NULL COMMENT '模板ID',
    template_version   INT          NOT NULL COMMENT '版本号',
    name               VARCHAR(128) NOT NULL,
    face_cent          BIGINT       NOT NULL COMMENT '面额分',
    min_spend_cent     BIGINT       NOT NULL DEFAULT 0 COMMENT '使用门槛分(不含运费)',
    valid_days         INT          NOT NULL COMMENT '领取后有效期天数',
    scope_type         VARCHAR(16)  NOT NULL COMMENT 'ALL/CATEGORY/PRODUCT',
    scope_definition_json JSON      NULL COMMENT '适用范围定义',
    source_type        VARCHAR(16)  NOT NULL DEFAULT 'OPERATION' COMMENT 'OPERATION/REFERRAL',
    reward_tier        INT          NULL COMMENT '推荐档位(1-4,source_type=REFERRAL时)',
    status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    effective_at       DATETIME(3)  NOT NULL,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (template_id, template_version),
    CONSTRAINT chk_coupon_template CHECK (
        face_cent > 0
        AND min_spend_cent >= 0
        AND valid_days > 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='券模板版本(修改生成新版本)';

-- -----------------------------------------------------------
-- user_coupon: 用户券
-- 状态: AVAILABLE / RESERVED / REDEEMED / EXPIRED / REVOKED
-- replacement_of_coupon_id 对非 NULL 唯一,确保退款最多补发一次
-- -----------------------------------------------------------
CREATE TABLE user_coupon (
    coupon_id              BIGINT       NOT NULL COMMENT '券ID',
    user_id                BIGINT       NOT NULL COMMENT '用户ID',
    template_id            BIGINT       NOT NULL,
    template_version       INT          NOT NULL,
    face_cent_snapshot     BIGINT       NOT NULL COMMENT '面额快照(固化)',
    min_spend_cent_snapshot BIGINT      NOT NULL COMMENT '门槛快照(固化)',
    scope_snapshot_json    JSON         NULL COMMENT '范围快照',
    received_at            DATETIME(3)  NOT NULL,
    valid_until            DATETIME(3)  NOT NULL COMMENT 'now<valid_until 有效',
    status                 VARCHAR(16)  NOT NULL DEFAULT 'AVAILABLE',
    reserved_order_id      BIGINT       NULL COMMENT '预占订单',
    used_order_id          BIGINT       NULL COMMENT '核销订单',
    source_reward_id       BIGINT       NULL COMMENT '来源推荐奖励ID',
    replacement_of_coupon_id BIGINT     NULL COMMENT '补发的原券ID(非NULL唯一)',
    version                INT          NOT NULL DEFAULT 0,
    created_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (coupon_id),
    INDEX idx_user_coupon_user_status (user_id, status, valid_until),
    INDEX idx_user_coupon_order (used_order_id),
    UNIQUE KEY uk_user_coupon_replacement (replacement_of_coupon_id),
    CONSTRAINT chk_user_coupon_non_negative CHECK (
        face_cent_snapshot > 0
        AND min_spend_cent_snapshot >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户券(属性固化,退款补券一次)';

-- -----------------------------------------------------------
-- coupon_flow: 券流水
-- 事件: ISSUE / RESERVE / REDEEM / RELEASE / EXPIRE / REVOKE / REPLACE
-- -----------------------------------------------------------
CREATE TABLE coupon_flow (
    flow_id          BIGINT       NOT NULL,
    coupon_id        BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL,
    event_type       VARCHAR(16)  NOT NULL,
    business_key     VARCHAR(128) NOT NULL COMMENT '幂等键',
    order_id         BIGINT       NULL,
    refund_id        BIGINT       NULL,
    before_status    VARCHAR(16)  NOT NULL,
    after_status     VARCHAR(16)  NOT NULL,
    face_cent        BIGINT       NOT NULL,
    occurred_at      DATETIME(3)  NOT NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (flow_id),
    UNIQUE KEY uk_coupon_flow_biz (business_key),
    INDEX idx_coupon_flow_coupon (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='券状态流水(幂等去重)';

-- -----------------------------------------------------------
-- referral: 推荐关系
-- 注册时绑定一名直接推荐人,绑定后不允许用户修改
-- 禁止自推荐、推荐环路和批量伪造
-- -----------------------------------------------------------
CREATE TABLE referral (
    referral_id        BIGINT       NOT NULL COMMENT '推荐关系ID',
    referred_user_id   BIGINT       NOT NULL COMMENT '被推荐人用户ID',
    referrer_user_id   BIGINT       NOT NULL COMMENT '推荐人用户ID',
    bound_at           DATETIME(3)  NOT NULL,
    first_order_id     BIGINT       NULL COMMENT '首单(固化,不可漂移)',
    trigger_status     VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/TRIGGERED/SKIPPED',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (referral_id),
    UNIQUE KEY uk_referral_referred (referred_user_id),
    INDEX idx_referral_referrer (referrer_user_id),
    -- 禁止自推荐
    CONSTRAINT chk_referral_no_self CHECK (referred_user_id <> referrer_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐关系(一级直推)';

-- -----------------------------------------------------------
-- referral_counter: 推荐人发券计数(第8.2章)
-- 以 referrer_user_id 唯一,发券事务锁行分配 success_sequence
-- 计数只增不减,退款后序号不复用
-- -----------------------------------------------------------
CREATE TABLE referral_counter (
    referrer_user_id     BIGINT       NOT NULL,
    last_success_sequence BIGINT      NOT NULL DEFAULT 0 COMMENT '上一个成功序号',
    version              INT          NOT NULL DEFAULT 0,
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (referrer_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐人发券串行计数';

-- -----------------------------------------------------------
-- referral_reward: 推荐奖励
-- 唯一键 referrer_user_id + success_sequence
-- 奖励券已核销但源首单退款时:不向推荐人追收现金或增加 LSC 待追偿
-- -----------------------------------------------------------
CREATE TABLE referral_reward (
    reward_id          BIGINT       NOT NULL COMMENT '奖励ID',
    referral_id        BIGINT       NOT NULL COMMENT '推荐关系ID',
    referrer_user_id   BIGINT       NOT NULL COMMENT '推荐人',
    success_sequence   BIGINT       NOT NULL COMMENT '成功序号(决定档位)',
    reward_tier        INT          NOT NULL COMMENT '档位1-4',
    first_order_id     BIGINT       NOT NULL COMMENT '触发首单(固化)',
    coupon_id          BIGINT       NULL COMMENT '发放的券ID',
    status             VARCHAR(16)  NOT NULL DEFAULT 'ISSUED' COMMENT 'ISSUED/USED/REVOKED',
    source_refund_status VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PARTIAL/REFUNDED/DISPUTED',
    issued_at          DATETIME(3)  NOT NULL,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (reward_id),
    UNIQUE KEY uk_referral_reward_seq (referrer_user_id, success_sequence),
    INDEX idx_referral_reward_referral (referral_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐奖励(四档券,序号不复用)';
