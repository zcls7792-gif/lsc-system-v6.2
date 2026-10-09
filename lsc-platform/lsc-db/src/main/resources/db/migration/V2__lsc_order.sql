-- ============================================================
-- 第12.4章 订单/支付/退款
-- 订单四状态分离: buyer_type / discount_mode / fulfillment_status / payment_status / refund_status
-- discount_mode: NONE / LSC / COUPON(每单最多一张券,券与LSC互斥)
-- 运费必须人民币支付且不产生赠送
-- ============================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------
-- orders: 订单主表
-- CHECK: rmb_cent = goods_cent + shipping_cent - coupon_cent - lsc_unit/100
-- CHECK: NOT (coupon_cent > 0 AND lsc_unit > 0)
-- CHECK: lsc_unit 为 100 的整数倍
-- -----------------------------------------------------------
CREATE TABLE orders (
    order_id                 BIGINT       NOT NULL COMMENT '订单ID',
    order_no                 VARCHAR(64)  NOT NULL COMMENT '订单号',
    user_id                  BIGINT       NOT NULL COMMENT '下单用户ID',
    buyer_type_snapshot      VARCHAR(16)  NOT NULL COMMENT '下单时身份快照: C/B',
    seller_entity_id         BIGINT       NOT NULL COMMENT '销售主体(供应链公司)',
    payee_entity_id          BIGINT       NOT NULL COMMENT '收款主体',
    invoice_entity_id        BIGINT       NOT NULL COMMENT '开票主体',
    benefit_obligor_entity_id BIGINT      NOT NULL COMMENT '权益履约义务主体',
    discount_mode            VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/LSC/COUPON',
    goods_cent               BIGINT       NOT NULL COMMENT '商品价款总额(不含运费)',
    shipping_cent            BIGINT       NOT NULL DEFAULT 0 COMMENT '运费(人民币,不产生赠送)',
    coupon_cent              BIGINT       NOT NULL DEFAULT 0 COMMENT '券优惠分',
    lsc_unit                 BIGINT       NOT NULL DEFAULT 0 COMMENT 'LSC 抵扣 unit',
    rmb_cent                 BIGINT       NOT NULL COMMENT '实付人民币分 = goods+shipping-coupon-lsc/100',
    payment_status           VARCHAR(16)  NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID/PAYING/PAID/REFUNDING/PART_REFUNDED/REFUNDED/EXCEPTION/PAID_EXCEPTION',
    fulfillment_status       VARCHAR(16)  NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/CONFIRMED/SHIPPED/COMPLETED/CANCELED/CLOSED',
    refund_status            VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/REQUESTED/REVIEWING/PROCESSING/PART_REFUNDED/REFUNDED/CLOSED',
    expires_at               DATETIME(3)  NOT NULL COMMENT '支付截止时间',
    completed_at             DATETIME(3)  NULL COMMENT '订单完成时间(COMPLETED)',
    quote_version            BIGINT       NOT NULL COMMENT '报价版本(防变更)',
    version                  INT          NOT NULL DEFAULT 0,
    created_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_orders_order_no (order_no),
    INDEX idx_orders_user_status (user_id, payment_status),
    INDEX idx_orders_seller (seller_entity_id),
    CONSTRAINT chk_orders_amount CHECK (
        goods_cent >= 0
        AND shipping_cent >= 0
        AND coupon_cent >= 0
        AND lsc_unit >= 0
        AND rmb_cent >= 0
    ),
    -- 券与 LSC 互斥(42206)
    CONSTRAINT chk_orders_coupon_lsc_mutex CHECK (
        NOT (coupon_cent > 0 AND lsc_unit > 0)
    ),
    -- unit 必须为 100 的整数倍(42208)
    CONSTRAINT chk_orders_lsc_unit_multiple CHECK (
        MOD(lsc_unit, 100) = 0
    ),
    -- rmb_cent = goods_cent + shipping_cent - coupon_cent - lsc_unit / 100
    CONSTRAINT chk_orders_rmb_identity CHECK (
        rmb_cent = goods_cent + shipping_cent - coupon_cent - (lsc_unit DIV 100)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表(四状态分离,券LSC互斥,unit100整数倍)';

-- -----------------------------------------------------------
-- order_item: 订单行
-- 保存 price_version/unit_price/goods/coupon_share/lsc_share/rmb_share 快照
-- granted_unit / grant_lot_id / clawback_* 为权益相关字段
-- -----------------------------------------------------------
CREATE TABLE order_item (
    item_id                BIGINT       NOT NULL COMMENT '订单行ID',
    order_id               BIGINT       NOT NULL COMMENT '订单ID',
    sku_id                 BIGINT       NOT NULL COMMENT 'SKU ID',
    qty                    INT          NOT NULL COMMENT '数量',
    price_version          BIGINT       NOT NULL COMMENT '价格版本快照',
    unit_price_cent        BIGINT       NOT NULL COMMENT '单元售价分',
    line_goods_cent        BIGINT       NOT NULL COMMENT '行商品金额',
    coupon_share_cent      BIGINT       NOT NULL DEFAULT 0 COMMENT '券优惠分摊',
    lsc_share_unit         BIGINT       NOT NULL DEFAULT 0 COMMENT 'LSC 抵扣分摊 unit',
    rmb_share_cent         BIGINT       NOT NULL COMMENT '人民币分摊分',
    grant_coefficient_ppm  BIGINT       NOT NULL COMMENT '赠送系数 ppm(快照)',
    cost_snapshot_enc      VARBINARY(256) NULL COMMENT '成本密文快照',
    granted_unit           BIGINT       NOT NULL DEFAULT 0 COMMENT '实际赠送 unit',
    grant_lot_id           BIGINT       NULL COMMENT '赠送批次ID(完成后写入)',
    clawback_required_unit BIGINT       NOT NULL DEFAULT 0 COMMENT '累计应撤回 unit',
    clawback_completed_unit BIGINT      NOT NULL DEFAULT 0 COMMENT '已撤回(含到期抵充) unit',
    clawback_pending_unit  BIGINT       NOT NULL DEFAULT 0 COMMENT '待追偿 unit',
    item_seq               INT          NOT NULL COMMENT '行内序号',
    version                INT          NOT NULL DEFAULT 0,
    created_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (item_id),
    UNIQUE KEY uk_order_item_order_seq (order_id, item_seq),
    INDEX idx_order_item_sku (sku_id),
    CONSTRAINT chk_order_item_non_negative CHECK (
        qty > 0
        AND unit_price_cent > 0
        AND line_goods_cent > 0
        AND coupon_share_cent >= 0
        AND lsc_share_unit >= 0
        AND rmb_share_cent >= 0
        AND granted_unit >= 0
        AND clawback_required_unit >= 0
        AND clawback_completed_unit >= 0
        AND clawback_pending_unit >= 0
    ),
    -- required = completed + pending
    CONSTRAINT chk_order_item_clawback_identity CHECK (
        clawback_required_unit = clawback_completed_unit + clawback_pending_unit
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单行(价格/分摊/赠送/撤回快照)';

-- -----------------------------------------------------------
-- order_unit_allocation: 订单单元分摊
-- 每个商品单元(按件记录或可验证区间压缩)保存 sale/coupon/lsc/rmb/grant 分摊
-- refunded_* / clawback_target / return_status 记录退款进度
-- -----------------------------------------------------------
CREATE TABLE order_unit_allocation (
    allocation_id       BIGINT       NOT NULL COMMENT '分摊ID',
    item_id             BIGINT       NOT NULL COMMENT '订单行ID',
    unit_index          INT          NOT NULL COMMENT '单元序号',
    sale_cent           BIGINT       NOT NULL COMMENT '该单元售价分',
    coupon_cent         BIGINT       NOT NULL DEFAULT 0 COMMENT '券优惠分摊',
    lsc_unit            BIGINT       NOT NULL DEFAULT 0 COMMENT 'LSC 抵扣分摊',
    rmb_cent            BIGINT       NOT NULL COMMENT '人民币分摊',
    grant_unit          BIGINT       NOT NULL DEFAULT 0 COMMENT '该单元赠送 unit',
    refunded_rmb_cent   BIGINT       NOT NULL DEFAULT 0 COMMENT '累计退人民币分',
    refunded_lsc_unit   BIGINT       NOT NULL DEFAULT 0 COMMENT '累计退 LSC unit',
    clawback_target_unit BIGINT      NOT NULL DEFAULT 0 COMMENT '累计应撤回目标 unit',
    return_status       VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PARTIAL/RETURNED/REVOKED',
    version             INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (allocation_id),
    UNIQUE KEY uk_order_unit_alloc_item_idx (item_id, unit_index),
    CONSTRAINT chk_order_unit_alloc_non_negative CHECK (
        sale_cent > 0
        AND coupon_cent >= 0
        AND lsc_unit >= 0
        AND rmb_cent >= 0
        AND grant_unit >= 0
        AND refunded_rmb_cent >= 0
        AND refunded_lsc_unit >= 0
        AND clawback_target_unit >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单单元分摊(逐件记录,退款撤回基准)';

-- -----------------------------------------------------------
-- payment_attempt: 支付单
-- 渠道回调签名校验后核销
-- -----------------------------------------------------------
CREATE TABLE payment_attempt (
    payment_id          BIGINT       NOT NULL COMMENT '支付ID',
    order_id            BIGINT       NOT NULL COMMENT '订单ID',
    channel             VARCHAR(32)  NOT NULL COMMENT '支付渠道',
    merchant_id         VARCHAR(64)  NOT NULL COMMENT '商户号(与收款主体一致)',
    channel_trade_no    VARCHAR(64)  NULL COMMENT '渠道交易号',
    merchant_request_no VARCHAR(64)  NOT NULL COMMENT '商户请求号',
    amount_cent         BIGINT       NOT NULL COMMENT '支付金额分',
    currency            VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    status              VARCHAR(16)  NOT NULL DEFAULT 'PAYING' COMMENT 'PAYING/PAID/FAILED/CLOSED/EXCEPTION',
    channel_paid_at     DATETIME(3)  NULL COMMENT '渠道成功时间',
    closed_at           DATETIME(3)  NULL,
    version             INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (payment_id),
    UNIQUE KEY uk_payment_channel_trade (channel, merchant_id, channel_trade_no),
    UNIQUE KEY uk_payment_merchant_request (merchant_request_no),
    INDEX idx_payment_order (order_id),
    CONSTRAINT chk_payment_amount CHECK (amount_cent > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单(渠道签名校验后核销)';

-- -----------------------------------------------------------
-- payment_callback: 支付回调落库(Inbox 模式)
-- 敏感原报文加密存于对象存储,仅保存引用与摘要
-- -----------------------------------------------------------
CREATE TABLE payment_callback (
    callback_id        BIGINT       NOT NULL COMMENT '回调ID',
    channel_event_id   VARCHAR(128) NOT NULL COMMENT '渠道事件ID',
    channel_trade_no   VARCHAR(64)  NOT NULL,
    payload_hash       VARCHAR(64)  NOT NULL COMMENT '报文摘要',
    verified           TINYINT(1)   NOT NULL COMMENT '签名校验结果',
    received_at        DATETIME(3)  NOT NULL,
    processed_at       DATETIME(3)  NULL,
    result             VARCHAR(32)  NULL COMMENT '处理结果: SUCCESS/RETRY/FAILED',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (callback_id),
    UNIQUE KEY uk_payment_callback_event (channel_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调Inbox(幂等去重,原报文对象存储)';

-- -----------------------------------------------------------
-- refund_order: 退款单
-- 状态: REQUESTED/REVIEWING/APPROVED/CHANNEL_PENDING/SUCCEEDED/FAILED/CLOSED
-- benefit_status: PENDING/PROCESSING/DONE/EXCEPTION(权益后处理与人民币退款分别记录)
-- -----------------------------------------------------------
CREATE TABLE refund_order (
    refund_id            BIGINT       NOT NULL COMMENT '退款ID',
    refund_no            VARCHAR(64)  NOT NULL COMMENT '退款单号',
    order_id             BIGINT       NOT NULL COMMENT '原订单ID',
    reason               VARCHAR(256) NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    benefit_status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    rmb_cent             BIGINT       NOT NULL COMMENT '应退人民币分',
    shipping_refund_cent BIGINT       NOT NULL DEFAULT 0 COMMENT '退运费分',
    channel_refund_no    VARCHAR(64)  NULL,
    approved_by          BIGINT       NULL,
    succeeded_at         DATETIME(3)  NULL,
    version              INT          NOT NULL DEFAULT 0,
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (refund_id),
    UNIQUE KEY uk_refund_order_no (refund_no),
    INDEX idx_refund_order_order (order_id),
    CONSTRAINT chk_refund_amount CHECK (
        rmb_cent >= 0
        AND shipping_refund_cent >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单(人民币退款与权益后处理分离)';

-- -----------------------------------------------------------
-- refund_item: 退款行
-- refund_kind: QTY(整件退货) / PRICE_DIFF(价差退款)
-- -----------------------------------------------------------
CREATE TABLE refund_item (
    refund_item_id      BIGINT       NOT NULL COMMENT '退款行ID',
    refund_id           BIGINT       NOT NULL COMMENT '退款单ID',
    item_id             BIGINT       NOT NULL COMMENT '订单行ID',
    refund_kind         VARCHAR(16)  NOT NULL COMMENT 'QTY/PRICE_DIFF',
    qty                 INT          NOT NULL DEFAULT 0 COMMENT '退货数量(QTY时有效)',
    rmb_cent            BIGINT       NOT NULL COMMENT '本行退人民币分',
    lsc_restore_unit    BIGINT       NOT NULL DEFAULT 0 COMMENT '本行应返还 LSC unit',
    grant_clawback_unit BIGINT       NOT NULL DEFAULT 0 COMMENT '本行应撤回赠送 unit',
    version             INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (refund_item_id),
    UNIQUE KEY uk_refund_item_refund_item_kind (refund_id, item_id, refund_kind),
    INDEX idx_refund_item_item (item_id),
    CONSTRAINT chk_refund_item_non_negative CHECK (
        qty >= 0
        AND rmb_cent >= 0
        AND lsc_restore_unit >= 0
        AND grant_clawback_unit >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款行(整件退货/价差退款)';

-- -----------------------------------------------------------
-- refund_allocation: 退款-单元分摊
-- 金额型多次退款按累计目标差额计算(7.5)
-- -----------------------------------------------------------
CREATE TABLE refund_allocation (
    alloc_id              BIGINT       NOT NULL COMMENT '退款分摊ID',
    refund_id             BIGINT       NOT NULL COMMENT '退款单ID',
    order_unit_allocation_id BIGINT    NOT NULL COMMENT '订单单元分摊ID',
    rmb_cent              BIGINT       NOT NULL COMMENT '本单元退人民币分',
    lsc_unit              BIGINT       NOT NULL DEFAULT 0 COMMENT '本单元退 LSC unit',
    grant_target_delta_unit BIGINT     NOT NULL DEFAULT 0 COMMENT '本次应撤回目标增量',
    status                VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (alloc_id),
    UNIQUE KEY uk_refund_alloc_refund_unit (refund_id, order_unit_allocation_id),
    CONSTRAINT chk_refund_alloc_non_negative CHECK (
        rmb_cent >= 0
        AND lsc_unit >= 0
        AND grant_target_delta_unit >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款-单元分摊(累计目标差额)';

-- -----------------------------------------------------------
-- invoice_record: 发票记录
-- 支持红字及重开关联
-- -----------------------------------------------------------
CREATE TABLE invoice_record (
    invoice_id           BIGINT       NOT NULL COMMENT '发票ID',
    order_id             BIGINT       NOT NULL COMMENT '订单ID',
    entity_id            BIGINT       NOT NULL COMMENT '开票主体',
    invoice_type         VARCHAR(16)  NOT NULL COMMENT 'SPECIAL/GENERAL/RED',
    buyer_tax_info_enc   VARBINARY(512) NOT NULL COMMENT '购方税务信息密文',
    invoice_no           VARCHAR(64)  NULL,
    tax_rule_version     VARCHAR(32)  NOT NULL,
    amount_cent          BIGINT       NOT NULL,
    tax_cent             BIGINT       NOT NULL,
    status               VARCHAR(16)  NOT NULL DEFAULT 'ISSUED',
    original_invoice_id  BIGINT       NULL COMMENT '红字/重开关联原发票',
    issued_at            DATETIME(3)  NULL,
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (invoice_id),
    INDEX idx_invoice_order (order_id),
    CONSTRAINT chk_invoice_non_negative CHECK (
        amount_cent >= 0
        AND tax_cent >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票记录(支持红字及重开)';
