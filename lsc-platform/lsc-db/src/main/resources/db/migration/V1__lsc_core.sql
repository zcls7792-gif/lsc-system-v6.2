-- ============================================================
-- 第12.5章 权益核心表（lsc_*）
-- 事实源: lsc_event + lsc_entry（不可直接修改）
-- 投影:   lsc_account / lsc_grant_lot / lsc_available_lot
--         lsc_reservation / lsc_reservation_allocation
--         lsc_consumption_allocation / lsc_return_allocation
--         lsc_freeze_allocation / lsc_recovery / lsc_recovery_allocation
--
-- 第12.1章通用规范:
--   - 主键 id BIGINT，含 created_at/updated_at（datetime(3) UTC）
--   - 可变聚合含 version INT，审计与流水表禁止业务 UPDATE
--   - 数量以 _unit 结尾（BIGINT 非负），人民币以 _cent 结尾
--   - 比例以 _ppm/_ppb 结尾
--   - 业务日 business_date 为 DATE（Asia/Shanghai）
--   - 删除限制 RESTRICT，不级联删除财务或权益事实
-- ============================================================

-- 统一字符集与存储引擎
SET NAMES utf8mb4;

-- -----------------------------------------------------------
-- lsc_account: 账户余额投影（第6.2 / 6.3 章）
-- 五个余额桶: locked / available / reserved / frozen_locked / frozen_available
-- 全部非负；pending_recovery 为待追偿投影，不作为负可用余额
-- -----------------------------------------------------------
CREATE TABLE lsc_account (
    user_id              BIGINT      NOT NULL COMMENT '用户ID',
    locked_unit          BIGINT      NOT NULL DEFAULT 0 COMMENT '未释放锁定量(来自 GrantLot 的 remaining_locked 汇总)',
    available_unit       BIGINT      NOT NULL DEFAULT 0 COMMENT '可用余额',
    reserved_unit        BIGINT      NOT NULL DEFAULT 0 COMMENT '支付占用中(订单未完成核销)',
    frozen_locked_unit   BIGINT      NOT NULL DEFAULT 0 COMMENT '风控冻结的锁定来源部分',
    frozen_available_unit BIGINT     NOT NULL DEFAULT 0 COMMENT '风控冻结的可用来源部分',
    pending_recovery_unit BIGINT     NOT NULL DEFAULT 0 COMMENT '待追偿数量投影(独立台账,非负)',
    last_event_seq       BIGINT      NOT NULL DEFAULT 0 COMMENT '该用户最新事件序号',
    version              INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    CONSTRAINT chk_lsc_account_non_negative CHECK (
        locked_unit >= 0
        AND available_unit >= 0
        AND reserved_unit >= 0
        AND frozen_locked_unit >= 0
        AND frozen_available_unit >= 0
        AND pending_recovery_unit >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSC 账户余额投影(与批次汇总一致)';

-- -----------------------------------------------------------
-- lsc_grant_lot: 赠送批次（第5.1 / 6.3 章）
-- original_grant_unit 发放后不可修改；每日额度以此为基数
-- remainder_nano_unit 取值 0..999999999
-- state: ACTIVE / PAUSED / RELEASED / REVOKED
-- -----------------------------------------------------------
CREATE TABLE lsc_grant_lot (
    grant_lot_id           BIGINT      NOT NULL COMMENT '批次ID',
    user_id                BIGINT      NOT NULL COMMENT '用户ID',
    source_item_id         BIGINT      NOT NULL COMMENT '来源订单行ID(order_item.item_id)',
    original_grant_unit    BIGINT      NOT NULL COMMENT '原始赠送量(发放后不可修改,日释放基数)',
    remaining_locked_unit  BIGINT      NOT NULL COMMENT '剩余未释放未冻结锁定量',
    frozen_locked_unit     BIGINT      NOT NULL DEFAULT 0 COMMENT '冻结锁定量',
    released_total_unit    BIGINT      NOT NULL DEFAULT 0 COMMENT '累计转入可用体系的数量',
    revoked_locked_unit    BIGINT      NOT NULL DEFAULT 0 COMMENT '锁定来源撤回量(退款等)',
    remainder_nano_unit    BIGINT      NOT NULL DEFAULT 0 COMMENT '余数累计(0..999999999)',
    grant_business_date    DATE        NOT NULL COMMENT '赠送业务日期(Asia/Shanghai)',
    first_release_date     DATE        NOT NULL COMMENT '首次释放日期(grant_business_date+1)',
    last_processed_date    DATE        NULL COMMENT '上次处理到的业务日',
    state                  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED/RELEASED/REVOKED',
    refund_hold            TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '退款处理中标记,暂停释放但不提前减余额',
    rule_version           VARCHAR(32) NOT NULL COMMENT '规则版本(释放/撤回规则版本)',
    version                INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (grant_lot_id),
    INDEX idx_lsc_grant_lot_user_state (user_id, state),
    INDEX idx_lsc_grant_lot_first_release (first_release_date, state),
    CONSTRAINT chk_lsc_grant_lot_non_negative CHECK (
        original_grant_unit > 0
        AND remaining_locked_unit >= 0
        AND frozen_locked_unit >= 0
        AND released_total_unit >= 0
        AND revoked_locked_unit >= 0
        AND remainder_nano_unit >= 0
        AND remainder_nano_unit < 1000000000
    ),
    -- 第6.3章恒等式: original = remaining_locked + frozen_locked + released + revoked_locked
    CONSTRAINT chk_lsc_grant_lot_identity CHECK (
        original_grant_unit = remaining_locked_unit + frozen_locked_unit
                             + released_total_unit + revoked_locked_unit
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSC 赠送批次(日释放唯一基数来源)';

-- -----------------------------------------------------------
-- lsc_available_lot: 可用批次（第6.2 / 6.3 / 5.5 章）
-- 每笔实际释放产生一条；origin_type: GRANT / REFUND_RESTORE
-- available_at 为真正可用时刻；expire_at = available_at + 365 天
-- 左闭右开: now < expire_at 可用
-- 六项: available / reserved / frozen / consumed / expired / revoked
-- 恒等式: issued + restored = available + reserved + frozen + consumed + expired + revoked
-- -----------------------------------------------------------
CREATE TABLE lsc_available_lot (
    available_lot_id     BIGINT      NOT NULL COMMENT '可用批次ID',
    user_id              BIGINT      NOT NULL COMMENT '用户ID',
    source_grant_lot_id  BIGINT      NOT NULL COMMENT '来源赠送批次',
    origin_type          VARCHAR(16) NOT NULL COMMENT 'GRANT 正常释放 / REFUND_RESTORE 退款返还',
    source_event_id      BIGINT      NOT NULL COMMENT '产生该批次的事件ID',
    lot_sequence         BIGINT      NOT NULL COMMENT '同源批次内序号',
    issued_unit          BIGINT      NOT NULL COMMENT '初始发放量(释放量或退款返还量)',
    restored_unit        BIGINT      NOT NULL DEFAULT 0 COMMENT '退款返还到本批次的量(原批次未过期时)',
    available_unit       BIGINT      NOT NULL COMMENT '可用量',
    reserved_unit        BIGINT      NOT NULL DEFAULT 0 COMMENT '支付占用量',
    frozen_unit          BIGINT      NOT NULL DEFAULT 0 COMMENT '风控冻结的可用量',
    consumed_unit        BIGINT      NOT NULL DEFAULT 0 COMMENT '累计支付核销量',
    expired_unit         BIGINT      NOT NULL DEFAULT 0 COMMENT '过期作销量',
    revoked_unit         BIGINT      NOT NULL DEFAULT 0 COMMENT '撤回量(退款扣回等)',
    available_at         DATETIME(3) NOT NULL COMMENT '真正可用时刻',
    expire_at            DATETIME(3) NOT NULL COMMENT '到期时刻(左闭右开: now<expire_at 可用)',
    terminal_at          DATETIME(3) NULL COMMENT '终结时刻',
    version              INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (available_lot_id),
    -- FEFO 排序: expire_at, available_at, available_lot_id
    INDEX idx_lsc_avail_lot_fefo (user_id, expire_at, available_at, available_lot_id),
    INDEX idx_lsc_avail_lot_source (source_grant_lot_id),
    UNIQUE KEY uk_lsc_avail_lot_seq (source_event_id, lot_sequence),
    CONSTRAINT chk_lsc_avail_lot_non_negative CHECK (
        issued_unit >= 0
        AND restored_unit >= 0
        AND available_unit >= 0
        AND reserved_unit >= 0
        AND frozen_unit >= 0
        AND consumed_unit >= 0
        AND expired_unit >= 0
        AND revoked_unit >= 0
    ),
    -- 第6.3章恒等式: issued + restored = available + reserved + frozen + consumed + expired + revoked
    CONSTRAINT chk_lsc_avail_lot_identity CHECK (
        issued_unit + restored_unit = available_unit + reserved_unit + frozen_unit
                                      + consumed_unit + expired_unit + revoked_unit
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSC 可用批次(释放/退款返还产生,FEFO 消费)';

-- -----------------------------------------------------------
-- lsc_event: 权益事件（第6.1 / 6.4 章）
-- 不可直接修改的事实源；每个事件含业务幂等键、用户序号、业务时间、规则版本、原因、关联订单/退款单/案件
-- -----------------------------------------------------------
CREATE TABLE lsc_event (
    event_id            BIGINT       NOT NULL COMMENT '事件ID',
    user_id             BIGINT       NOT NULL COMMENT '用户ID',
    user_event_seq      BIGINT       NOT NULL COMMENT '用户事件序号(同用户递增)',
    event_type          VARCHAR(32)  NOT NULL COMMENT '事件类型: GRANT/RELEASE/CONSUME/RETURN/EXPIRE/REVOKE/FREEZE/UNFREEZE/RECOVERY 等',
    business_key        VARCHAR(128) NOT NULL COMMENT '业务幂等键(user_id+biz维度唯一)',
    request_hash        VARCHAR(64)  NULL COMMENT '请求摘要(相同key不同请求可检测)',
    order_id            BIGINT       NULL COMMENT '关联订单ID',
    refund_id           BIGINT       NULL COMMENT '关联退款单ID',
    case_id             BIGINT       NULL COMMENT '关联风控案件ID',
    business_date       DATE         NOT NULL COMMENT '业务日期(Asia/Shanghai)',
    occurred_at         DATETIME(3)  NOT NULL COMMENT '事件发生时间(UTC)',
    rule_version        VARCHAR(32)  NOT NULL COMMENT '规则版本',
    original_event_id   BIGINT       NULL COMMENT '纠错事件引用的原事件ID',
    payload_version     INT          NOT NULL DEFAULT 1,
    payload_json        JSON         NULL COMMENT '事件载荷(余数更新/追偿登记等非金额分录信息)',
    event_hash          VARCHAR(64)  NOT NULL COMMENT '事件内容哈希(防篡改)',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_lsc_event_user_seq (user_id, user_event_seq),
    UNIQUE KEY uk_lsc_event_biz_key (user_id, business_key),
    INDEX idx_lsc_event_order (order_id),
    INDEX idx_lsc_event_refund (refund_id),
    INDEX idx_lsc_event_type_date (event_type, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSC 权益事件事实源(不可直接修改)';

-- -----------------------------------------------------------
-- lsc_entry: 权益分录（第6.1 章）
-- 每条分录含 bucket、delta_unit、grant_lot_id、available_lot_id、处置类别
-- bucket: LOCKED / AVAILABLE / RESERVED / FROZEN_LOCKED / FROZEN_AVAILABLE
-- disposition_type: RELEASE / CONSUME / RETURN / EXPIRE / REVOKE / FREEZE / UNFREEZE / TRANSFER
-- 零余额变化的余数更新不写分录,保存在事件 payload
-- -----------------------------------------------------------
CREATE TABLE lsc_entry (
    entry_id           BIGINT       NOT NULL COMMENT '分录ID',
    event_id           BIGINT       NOT NULL COMMENT '所属事件ID',
    entry_seq          INT          NOT NULL COMMENT '事件内分录序号',
    user_id            BIGINT       NOT NULL COMMENT '用户ID',
    grant_lot_id       BIGINT       NULL COMMENT '关联赠送批次',
    available_lot_id   BIGINT       NULL COMMENT '关联可用批次',
    bucket             VARCHAR(20)  NOT NULL COMMENT '余额桶: LOCKED/AVAILABLE/RESERVED/FROZEN_LOCKED/FROZEN_AVAILABLE',
    delta_unit         BIGINT       NOT NULL COMMENT '数量变化(有符号,负向用负数)',
    disposition_type   VARCHAR(32)  NOT NULL COMMENT '处置类别',
    allocation_ref     VARCHAR(64)  NULL COMMENT '关联分配ID(reservation/consumption/freeze/recovery)',
    before_unit        BIGINT       NOT NULL COMMENT '变更前该桶余额',
    after_unit         BIGINT       NOT NULL COMMENT '变更后该桶余额',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (entry_id),
    UNIQUE KEY uk_lsc_entry_event_seq (event_id, entry_seq),
    INDEX idx_lsc_entry_grant_lot (grant_lot_id),
    INDEX idx_lsc_entry_available_lot (available_lot_id),
    INDEX idx_lsc_entry_user_bucket (user_id, bucket),
    CONSTRAINT chk_lsc_entry_bucket CHECK (
        bucket IN ('LOCKED','AVAILABLE','RESERVED','FROZEN_LOCKED','FROZEN_AVAILABLE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSC 权益分录事实源(逐桶逐批次变化,可重建账本)';

-- -----------------------------------------------------------
-- lsc_reservation: 支付占用（第7.1 章）
-- 一订单同一支付生命周期唯一
-- 状态: ACTIVE / CAPTURED / RELEASED / REFUND_RETURNED / EXCEPTION
-- -----------------------------------------------------------
CREATE TABLE lsc_reservation (
    reservation_id       BIGINT       NOT NULL COMMENT '占用ID',
    user_id              BIGINT       NOT NULL COMMENT '用户ID',
    order_id             BIGINT       NOT NULL COMMENT '订单ID',
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CAPTURED/RELEASED/REFUND_RETURNED/EXCEPTION',
    reserved_total_unit  BIGINT       NOT NULL COMMENT '占用总额度',
    expires_at           DATETIME(3)  NOT NULL COMMENT '占用过期时间(min(支付截止,最早批次到期))',
    channel_close_state  VARCHAR(16)  NULL COMMENT '渠道关闭状态',
    version              INT          NOT NULL DEFAULT 0,
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (reservation_id),
    UNIQUE KEY uk_lsc_reservation_order (order_id),
    INDEX idx_lsc_reservation_user_status (user_id, status),
    CONSTRAINT chk_lsc_reservation_non_negative CHECK (reserved_total_unit >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSC 支付占用(订单支付生命周期)';

-- -----------------------------------------------------------
-- lsc_reservation_allocation: 占用分配明细（第7.1 章）
-- 逐 AvailableLot 占用,记录 reserved/captured/released
-- -----------------------------------------------------------
CREATE TABLE lsc_reservation_allocation (
    allocation_id      BIGINT       NOT NULL COMMENT '分配ID',
    reservation_id     BIGINT       NOT NULL COMMENT '占用ID',
    available_lot_id   BIGINT       NOT NULL COMMENT '可用批次ID',
    reserved_unit      BIGINT       NOT NULL COMMENT '本批次预占量',
    captured_unit      BIGINT       NOT NULL DEFAULT 0 COMMENT '支付成功核销量',
    released_unit      BIGINT       NOT NULL DEFAULT 0 COMMENT '释放量(取消/超时)',
    version            INT          NOT NULL DEFAULT 0,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (allocation_id),
    UNIQUE KEY uk_lsc_res_alloc_res_lot (reservation_id, available_lot_id),
    INDEX idx_lsc_res_alloc_lot (available_lot_id),
    CONSTRAINT chk_lsc_res_alloc_non_negative CHECK (
        reserved_unit >= 0
        AND captured_unit >= 0
        AND released_unit >= 0
        AND captured_unit <= reserved_unit
        AND released_unit <= reserved_unit
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSC 支付占用-可用批次分配明细';

-- -----------------------------------------------------------
-- lsc_consumption_allocation: 支付核销分配（第6.3 / 7.4 章）
-- 消费核销逐单元逐批次记录，returned_unit 累计不得超过 captured_unit
-- -----------------------------------------------------------
CREATE TABLE lsc_consumption_allocation (
    consumption_id          BIGINT       NOT NULL COMMENT '核销分配ID',
    user_id                 BIGINT       NOT NULL COMMENT '用户ID',
    order_unit_allocation_id BIGINT      NOT NULL COMMENT '订单单元分摊ID',
    available_lot_id        BIGINT       NOT NULL COMMENT '核销来源可用批次',
    captured_unit           BIGINT       NOT NULL COMMENT '核销量',
    returned_unit           BIGINT       NOT NULL DEFAULT 0 COMMENT '累计退款返还量(<=captured)',
    capture_event_id        BIGINT       NOT NULL COMMENT '核销事件ID',
    version                 INT          NOT NULL DEFAULT 0,
    created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (consumption_id),
    INDEX idx_lsc_cons_alloc_user_lot (user_id, available_lot_id),
    INDEX idx_lsc_cons_alloc_unit (order_unit_allocation_id),
    CONSTRAINT chk_lsc_cons_alloc_check CHECK (
        captured_unit > 0
        AND returned_unit >= 0
        AND returned_unit <= captured_unit
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSC 支付核销分配(消费来源追溯)';

-- -----------------------------------------------------------
-- lsc_return_allocation: 退款返还分配（第7.4 章）
-- 退款时按消费分配定位原 AvailableLot 返还
-- -----------------------------------------------------------
CREATE TABLE lsc_return_allocation (
    return_alloc_id    BIGINT       NOT NULL COMMENT '返还分配ID',
    refund_id          BIGINT       NOT NULL COMMENT '退款单ID',
    consumption_id     BIGINT       NOT NULL COMMENT '原消费分配ID',
    target_available_lot_id BIGINT  NOT NULL COMMENT '返还目标可用批次(原批次或新建REFUND_RESTORE批次)',
    returned_unit      BIGINT       NOT NULL COMMENT '返还量',
    return_event_id    BIGINT       NOT NULL COMMENT '返还事件ID',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (return_alloc_id),
    UNIQUE KEY uk_lsc_return_alloc (refund_id, consumption_id, target_available_lot_id),
    INDEX idx_lsc_return_alloc_consumption (consumption_id),
    CONSTRAINT chk_lsc_return_alloc_non_negative CHECK (returned_unit > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSC 退款返还分配(原批次或宽限批次)';

-- -----------------------------------------------------------
-- lsc_freeze_allocation: 风控冻结分配（第6.2 / 10.2 章）
-- 逐批次冻结，source_bucket 区分锁定来源冻结与可用来源冻结
-- 状态: ACTIVE / RELEASED / REVOKED / EXPIRED
-- -----------------------------------------------------------
CREATE TABLE lsc_freeze_allocation (
    freeze_id          BIGINT       NOT NULL COMMENT '冻结分配ID',
    user_id            BIGINT       NOT NULL COMMENT '用户ID',
    case_id            BIGINT       NOT NULL COMMENT '风控案件ID',
    source_bucket      VARCHAR(20)  NOT NULL COMMENT '来源桶: LOCKED 或 AVAILABLE',
    grant_lot_id       BIGINT       NULL COMMENT '来源赠送批次(锁定来源冻结)',
    available_lot_id   BIGINT       NULL COMMENT '来源可用批次(可用来源冻结)',
    frozen_unit        BIGINT       NOT NULL COMMENT '冻结量',
    released_unit      BIGINT       NOT NULL DEFAULT 0 COMMENT '解除冻结量',
    revoked_unit       BIGINT       NOT NULL DEFAULT 0 COMMENT '因退款撤回被消耗的冻结量',
    expired_unit       BIGINT       NOT NULL DEFAULT 0 COMMENT '冻结期间批次到期被作废的量',
    status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/RELEASED/REVOKED/EXPIRED',
    version            INT          NOT NULL DEFAULT 0,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (freeze_id),
    INDEX idx_lsc_freeze_user_status (user_id, status),
    INDEX idx_lsc_freeze_case (case_id),
    CONSTRAINT chk_lsc_freeze_source CHECK (
        source_bucket IN ('LOCKED','AVAILABLE')
    ),
    CONSTRAINT chk_lsc_freeze_non_negative CHECK (
        frozen_unit > 0
        AND released_unit >= 0
        AND revoked_unit >= 0
        AND expired_unit >= 0
        AND released_unit + revoked_unit + expired_unit <= frozen_unit
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSC 风控冻结分配(逐批次锁定来源/可用来源)';

-- -----------------------------------------------------------
-- lsc_recovery: 权益待追偿（第6.3 / 7.5 / 7.6 章）
-- 待追偿为独立待抵扣数量，不作为负可用余额，不形成人民币债务
-- required = recovered + satisfied_by_expiry + pending
-- 状态: OPEN / PARTIAL / CLEARED / DISPUTED
-- -----------------------------------------------------------
CREATE TABLE lsc_recovery (
    recovery_id              BIGINT       NOT NULL COMMENT '追偿ID',
    user_id                  BIGINT       NOT NULL COMMENT '用户ID',
    source_item_id           BIGINT       NOT NULL COMMENT '来源订单行ID',
    required_unit            BIGINT       NOT NULL COMMENT '累计应追偿量',
    recovered_unit           BIGINT       NOT NULL DEFAULT 0 COMMENT '已从有效权益扣回量',
    satisfied_by_expiry_unit BIGINT       NOT NULL DEFAULT 0 COMMENT '已由源批次自然到期抵充量',
    pending_unit             BIGINT       NOT NULL COMMENT '待追偿量(=required - recovered - satisfied)',
    status                   VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PARTIAL/CLEARED/DISPUTED',
    opened_at                DATETIME(3)  NOT NULL,
    closed_at                DATETIME(3)  NULL,
    version                  INT          NOT NULL DEFAULT 0,
    created_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (recovery_id),
    UNIQUE KEY uk_lsc_recovery_user_item (user_id, source_item_id),
    INDEX idx_lsc_recovery_user_status (user_id, status),
    CONSTRAINT chk_lsc_recovery_non_negative CHECK (
        required_unit > 0
        AND recovered_unit >= 0
        AND satisfied_by_expiry_unit >= 0
        AND pending_unit >= 0
    ),
    -- required = recovered + satisfied_by_expiry + pending
    CONSTRAINT chk_lsc_recovery_identity CHECK (
        required_unit = recovered_unit + satisfied_by_expiry_unit + pending_unit
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSC 权益待追偿台账(独立于可用余额)';

-- -----------------------------------------------------------
-- lsc_recovery_allocation: 追偿分配明细（第7.6 章）
-- 分录被分配的合计不得超过其可抵充数量，使用行锁维护分配上限
-- satisfaction_type: FROM_AVAILABLE / FROM_FROZEN / FROM_EXPIRY / FROM_REFUND_RETURN
-- -----------------------------------------------------------
CREATE TABLE lsc_recovery_allocation (
    alloc_id              BIGINT       NOT NULL COMMENT '追偿分配ID',
    recovery_id           BIGINT       NOT NULL COMMENT '追偿ID',
    source_entry_id       BIGINT       NOT NULL COMMENT '被抵充分录ID',
    source_available_lot_id BIGINT     NULL COMMENT '被抵充可用批次',
    satisfaction_type     VARCHAR(20)  NOT NULL COMMENT '抵充来源类型',
    amount_unit           BIGINT       NOT NULL COMMENT '抵充量',
    event_id              BIGINT       NOT NULL COMMENT '抵充事件ID',
    allocation_seq        INT          NOT NULL COMMENT '分配序号',
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (alloc_id),
    UNIQUE KEY uk_lsc_rec_alloc_recovery_event_seq (recovery_id, event_id, allocation_seq),
    INDEX idx_lsc_rec_alloc_entry (source_entry_id),
    CONSTRAINT chk_lsc_rec_alloc_satisfaction CHECK (
        satisfaction_type IN ('FROM_AVAILABLE','FROM_FROZEN','FROM_EXPIRY','FROM_REFUND_RETURN')
    ),
    CONSTRAINT chk_lsc_rec_alloc_amount CHECK (amount_unit > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSC 追偿分配明细(逐分录抵充)';
