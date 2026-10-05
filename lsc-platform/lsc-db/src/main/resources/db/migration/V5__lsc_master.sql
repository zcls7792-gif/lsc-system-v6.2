-- ============================================================
-- 第12.2 / 12.3 / 12.8 / 12.9章
-- 用户主体、商品价格、供应链库存、风控配置
-- ============================================================

SET NAMES utf8mb4;

-- -------------------- 12.2 用户主体与权限 --------------------

CREATE TABLE user (
    user_id             BIGINT       NOT NULL,
    mobile_enc          VARBINARY(128) NOT NULL COMMENT '手机号密文(用于展示)',
    mobile_lookup_hash  VARCHAR(64)  NOT NULL COMMENT '手机号摘要(用于去重,非裸号)',
    nickname            VARCHAR(64)  NULL,
    user_type           VARCHAR(16)  NOT NULL DEFAULT 'UNVERIFIED' COMMENT 'UNVERIFIED/C/B',
    account_status      VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    referrer_user_id    BIGINT       NULL COMMENT '推荐人(注册时绑定,不可改)',
    first_qualified_order_id BIGINT  NULL COMMENT '首单(固化)',
    privacy_version     VARCHAR(32)  NOT NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user_mobile_hash (mobile_lookup_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

CREATE TABLE legal_entity (
    entity_id           BIGINT       NOT NULL,
    legal_name          VARCHAR(128) NOT NULL,
    registration_no     VARCHAR(64)  NOT NULL,
    role                VARCHAR(32)  NOT NULL COMMENT 'PLATFORM/SELLER/PAYEE/INVOICE/BENEFIT_OBLIGOR',
    pay_merchant_id     VARCHAR(64)  NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    verified_at         DATETIME(3)  NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (entity_id),
    UNIQUE KEY uk_legal_entity_reg (registration_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='法律主体';

CREATE TABLE business_profile (
    user_id             BIGINT       NOT NULL,
    entity_name         VARCHAR(128) NOT NULL,
    license_no          VARCHAR(64)  NOT NULL,
    license_expiry      DATE         NOT NULL,
    business_status     VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/APPROVED/REJECTED/SUSPENDED/EXPIRED',
    approved_version    BIGINT       NULL,
    approved_at         DATETIME(3)  NULL,
    qualification_expire_at DATE    NULL,
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B端商户资质';

CREATE TABLE business_audit_record (
    audit_id            BIGINT       NOT NULL,
    user_id             BIGINT       NOT NULL,
    application_version BIGINT       NOT NULL,
    license_object_key  VARCHAR(256) NOT NULL,
    ocr_result_enc      VARBINARY(1024) NULL,
    status              VARCHAR(16)  NOT NULL,
    auditor_id          BIGINT       NULL,
    remark              VARCHAR(512) NULL,
    audited_at          DATETIME(3)  NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (audit_id),
    INDEX idx_biz_audit_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B端资质审核记录';

CREATE TABLE admin_role_binding (
    binding_id          BIGINT       NOT NULL,
    admin_id            BIGINT       NOT NULL,
    role_code           VARCHAR(32)  NOT NULL,
    scope_type          VARCHAR(16)  NOT NULL,
    scope_id            BIGINT       NOT NULL,
    valid_until         DATETIME(3)  NULL,
    PRIMARY KEY (binding_id),
    UNIQUE KEY uk_admin_role (admin_id, role_code, scope_type, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员角色绑定';

CREATE TABLE user_agreement_acceptance (
    acceptance_id       BIGINT       NOT NULL,
    user_id             BIGINT       NOT NULL,
    agreement_type      VARCHAR(32)  NOT NULL,
    agreement_version   VARCHAR(32)  NOT NULL,
    accepted_at         DATETIME(3)  NOT NULL,
    client_evidence_ref VARCHAR(256) NULL,
    PRIMARY KEY (acceptance_id),
    UNIQUE KEY uk_user_agreement (user_id, agreement_type, agreement_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户协议确认';

-- -------------------- 12.3 商品与价格 --------------------

CREATE TABLE product (
    product_id          BIGINT       NOT NULL,
    seller_entity_id    BIGINT       NOT NULL,
    name                VARCHAR(256) NOT NULL,
    category_id         BIGINT       NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/REVIEWING/ON_SALE/OFF_SALE/SOLD_OUT',
    audit_version       BIGINT       NOT NULL DEFAULT 0,
    description_ref     VARCHAR(256) NULL,
    return_policy_version VARCHAR(32) NOT NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品';

CREATE TABLE product_sku (
    sku_id              BIGINT       NOT NULL,
    product_id          BIGINT       NOT NULL,
    sku_code            VARCHAR(64)  NOT NULL,
    spec_json           JSON         NULL,
    sale_unit           VARCHAR(16)  NOT NULL DEFAULT 'PIECE',
    pack_qty            INT          NOT NULL DEFAULT 1 COMMENT '箱规(B端)',
    b_min_qty           INT          NOT NULL DEFAULT 1 COMMENT 'B端最小采购量',
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (sku_id),
    UNIQUE KEY uk_product_sku_code (sku_code),
    INDEX idx_product_sku_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU';

CREATE TABLE product_price_version (
    price_version       BIGINT       NOT NULL,
    sku_id              BIGINT       NOT NULL,
    retail_price_cent   BIGINT       NOT NULL COMMENT 'C端零售价分',
    b_price_cent        BIGINT       NOT NULL COMMENT 'B端采购价分',
    cost_price_enc      VARBINARY(256) NOT NULL COMMENT '成本密文(KMS)',
    cost_key_version    VARCHAR(32)  NOT NULL,
    cost_basis_code     VARCHAR(16)  NOT NULL DEFAULT 'TAX_INCLUDED',
    grant_coefficient_ppm BIGINT     NOT NULL DEFAULT 800000 COMMENT '赠送系数 ppm',
    grant_c_unit        BIGINT       NOT NULL DEFAULT 0 COMMENT 'C端赠送基准(展示用,实际按公式)',
    grant_b_unit        BIGINT       NOT NULL DEFAULT 0 COMMENT 'B端赠送基准(展示用)',
    effective_at        DATETIME(3)  NOT NULL,
    approved_by         BIGINT       NOT NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (price_version),
    UNIQUE KEY uk_product_price_sku_ver (sku_id, price_version),
    CONSTRAINT chk_product_price CHECK (
        retail_price_cent > 0
        AND b_price_cent > 0
        AND grant_coefficient_ppm >= 0
        AND grant_coefficient_ppm <= 1000000
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品价格版本(价格+成本+赠送基准)';

CREATE TABLE product_audit_record (
    audit_id            BIGINT       NOT NULL,
    product_id          BIGINT       NOT NULL,
    version             BIGINT       NOT NULL,
    reviewer_id         BIGINT       NULL,
    decision            VARCHAR(16)  NOT NULL,
    reason              VARCHAR(512) NULL,
    evidence_ref        VARCHAR(256) NULL,
    reviewed_at         DATETIME(3)  NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (audit_id),
    INDEX idx_product_audit_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品审核记录';

-- -------------------- 12.8 供应链库存 --------------------

CREATE TABLE supplier (
    supplier_id         BIGINT       NOT NULL,
    legal_entity_info   JSON         NOT NULL,
    license_ref         VARCHAR(256) NOT NULL,
    contact_enc         VARBINARY(256) NOT NULL,
    bank_account_enc    VARBINARY(256) NOT NULL,
    bank_account_version INT         NOT NULL DEFAULT 1,
    payment_term_days   INT          NOT NULL DEFAULT 45,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商';

CREATE TABLE purchase_order (
    po_id               BIGINT       NOT NULL,
    po_no               VARCHAR(64)  NOT NULL,
    supplier_id         BIGINT       NOT NULL,
    buyer_entity_id     BIGINT       NOT NULL,
    currency            VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    total_cent          BIGINT       NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    approved_by         BIGINT       NULL,
    payment_due_date    DATE         NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (po_id),
    UNIQUE KEY uk_po_no (po_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单';

CREATE TABLE purchase_order_item (
    po_item_id          BIGINT       NOT NULL,
    po_id               BIGINT       NOT NULL,
    sku_id              BIGINT       NOT NULL,
    qty                 INT          NOT NULL,
    unit_price_cent     BIGINT       NOT NULL,
    line_cent           BIGINT       NOT NULL,
    PRIMARY KEY (po_item_id),
    INDEX idx_poi_po (po_id),
    CONSTRAINT chk_poi CHECK (qty > 0 AND unit_price_cent > 0 AND line_cent > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单明细';

CREATE TABLE goods_receipt (
    receipt_id          BIGINT       NOT NULL,
    po_id               BIGINT       NOT NULL,
    warehouse_id        BIGINT       NOT NULL,
    supplier_delivery_no VARCHAR(64) NULL,
    received_at         DATETIME(3)  NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'RECEIVED',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (receipt_id),
    INDEX idx_gr_po (po_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货单';

CREATE TABLE goods_receipt_item (
    receipt_item_id     BIGINT       NOT NULL,
    receipt_id          BIGINT       NOT NULL,
    po_item_id          BIGINT       NOT NULL,
    received_qty        INT          NOT NULL,
    batch_no            VARCHAR(64)  NULL,
    PRIMARY KEY (receipt_item_id),
    INDEX idx_gri_receipt (receipt_id),
    CONSTRAINT chk_gri_qty CHECK (received_qty > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货单明细';

CREATE TABLE quality_inspection (
    inspection_id       BIGINT       NOT NULL,
    receipt_id          BIGINT       NOT NULL,
    inspector_id        BIGINT       NULL,
    result              VARCHAR(16)  NOT NULL COMMENT 'PASS/REJECT/PARTIAL',
    evidence_ref        VARCHAR(256) NULL,
    inspected_at        DATETIME(3)  NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (inspection_id),
    INDEX idx_qi_receipt (receipt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质检单';

CREATE TABLE quality_inspection_item (
    qi_item_id          BIGINT       NOT NULL,
    inspection_id       BIGINT       NOT NULL,
    receipt_item_id     BIGINT       NOT NULL,
    accepted_qty        INT          NOT NULL,
    rejected_qty        INT          NOT NULL DEFAULT 0,
    reason              VARCHAR(256) NULL,
    PRIMARY KEY (qi_item_id),
    INDEX idx_qii_inspection (inspection_id),
    CONSTRAINT chk_qii CHECK (accepted_qty >= 0 AND rejected_qty >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质检单明细';

CREATE TABLE stock_balance (
    warehouse_id        BIGINT       NOT NULL,
    sku_id              BIGINT       NOT NULL,
    batch_no            VARCHAR(64)  NOT NULL,
    on_hand_qty         INT          NOT NULL DEFAULT 0,
    reserved_qty        INT          NOT NULL DEFAULT 0,
    blocked_qty         INT          NOT NULL DEFAULT 0,
    version             INT          NOT NULL DEFAULT 0,
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (warehouse_id, sku_id, batch_no),
    -- 可售 = on_hand - reserved - blocked >= 0
    CONSTRAINT chk_stock_balance CHECK (
        on_hand_qty >= 0
        AND reserved_qty >= 0
        AND blocked_qty >= 0
        AND on_hand_qty >= reserved_qty + blocked_qty
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存余额(三桶:实物/预占/质检冻结)';

CREATE TABLE stock_movement (
    movement_id         BIGINT       NOT NULL,
    business_key        VARCHAR(128) NOT NULL,
    warehouse_id        BIGINT       NOT NULL,
    sku_id              BIGINT       NOT NULL,
    batch_no            VARCHAR(64)  NOT NULL,
    movement_type       VARCHAR(16)  NOT NULL COMMENT 'IN/OUT/RESERVE/CAPTURE/RELEASE/BLOCK/UNBLOCK',
    quantity_delta      INT          NOT NULL,
    source_doc_id       BIGINT       NULL,
    occurred_at         DATETIME(3)  NOT NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (movement_id),
    UNIQUE KEY uk_stock_movement_biz (business_key),
    INDEX idx_stock_movement_sku (warehouse_id, sku_id, batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水';

CREATE TABLE stock_reservation (
    reservation_id      BIGINT       NOT NULL,
    order_id            BIGINT       NOT NULL,
    sku_id              BIGINT       NOT NULL,
    warehouse_id        BIGINT       NOT NULL,
    batch_no            VARCHAR(64)  NOT NULL,
    reserved_qty        INT          NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    expires_at          DATETIME(3)  NOT NULL,
    version             INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (reservation_id),
    INDEX idx_stock_res_order (order_id),
    CONSTRAINT chk_stock_res CHECK (reserved_qty > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预占';

CREATE TABLE shipment (
    shipment_id         BIGINT       NOT NULL,
    order_id            BIGINT       NOT NULL,
    carrier             VARCHAR(64)  NULL,
    tracking_no         VARCHAR(64)  NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    shipped_at          DATETIME(3)  NULL,
    delivered_at        DATETIME(3)  NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (shipment_id),
    INDEX idx_shipment_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货单';

CREATE TABLE shipment_item (
    shipment_item_id    BIGINT       NOT NULL,
    shipment_id         BIGINT       NOT NULL,
    order_item_id       BIGINT       NOT NULL,
    qty                 INT          NOT NULL,
    warehouse_id        BIGINT       NOT NULL,
    batch_no            VARCHAR(64)  NULL,
    PRIMARY KEY (shipment_item_id),
    INDEX idx_si_shipment (shipment_id),
    CONSTRAINT chk_si_qty CHECK (qty > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货单明细';

CREATE TABLE settlement (
    settlement_id       BIGINT       NOT NULL,
    settle_no           VARCHAR(64)  NOT NULL,
    supplier_id         BIGINT       NOT NULL,
    entity_id           BIGINT       NOT NULL,
    amount_cent         BIGINT       NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    approved_by         BIGINT       NULL,
    paid_at             DATETIME(3)  NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (settlement_id),
    UNIQUE KEY uk_settle_no (settle_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商结算单';

CREATE TABLE settlement_allocation (
    alloc_id            BIGINT       NOT NULL,
    settlement_id       BIGINT       NOT NULL,
    po_item_id          BIGINT       NOT NULL,
    receipt_item_id     BIGINT       NOT NULL,
    quantity            INT          NOT NULL,
    amount_cent         BIGINT       NOT NULL,
    PRIMARY KEY (alloc_id),
    INDEX idx_settle_alloc_settlement (settlement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算分配(四单匹配)';

CREATE TABLE supplier_payment (
    payment_id          BIGINT       NOT NULL,
    payment_no          VARCHAR(64)  NOT NULL,
    settlement_id       BIGINT       NOT NULL,
    amount_cent         BIGINT       NOT NULL,
    bank_trade_no       VARCHAR(64)  NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    paid_at             DATETIME(3)  NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (payment_id),
    UNIQUE KEY uk_supplier_payment_no (payment_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商付款';

-- -------------------- 12.9 风控配置与可靠性 --------------------

CREATE TABLE risk_case (
    case_id             BIGINT       NOT NULL,
    user_id             BIGINT       NOT NULL,
    rule_id             VARCHAR(64)  NULL,
    ai_flag             TINYINT(1)   NOT NULL DEFAULT 0,
    evidence_ref        VARCHAR(256) NULL,
    proposed_action     VARCHAR(32)  NOT NULL,
    review_status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    review_deadline     DATETIME(3)  NOT NULL COMMENT '48小时内复核',
    reviewer_id         BIGINT       NULL,
    decision            VARCHAR(32)  NULL,
    decided_at          DATETIME(3)  NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (case_id),
    INDEX idx_risk_case_user (user_id, review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控案件';

CREATE TABLE appeal (
    appeal_id           BIGINT       NOT NULL,
    case_id             BIGINT       NOT NULL,
    user_id             BIGINT       NOT NULL,
    submitted_at        DATETIME(3)  NOT NULL,
    reply_deadline      DATETIME(3)  NOT NULL COMMENT '5个工作日内答复',
    status              VARCHAR(16)  NOT NULL DEFAULT 'SUBMITTED',
    reviewer_id         BIGINT       NULL,
    decision_ref        VARCHAR(256) NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (appeal_id),
    INDEX idx_appeal_case (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控申诉';

CREATE TABLE config_change_request (
    change_id           BIGINT       NOT NULL,
    config_group        VARCHAR(64)  NOT NULL,
    before_version      BIGINT       NULL,
    proposed_json       JSON         NOT NULL,
    requester_id        BIGINT       NOT NULL,
    approver_id         BIGINT       NULL,
    reason              VARCHAR(512) NULL,
    effective_date      DATE         NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    approval_at         DATETIME(3)  NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (change_id),
    -- 发起人与审批人必须不同
    CONSTRAINT chk_config_change_no_self CHECK (
        approver_id IS NULL OR requester_id <> approver_id
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置变更工单(双签)';

CREATE TABLE config_version (
    config_group        VARCHAR(64)  NOT NULL,
    version             BIGINT       NOT NULL,
    payload_json        JSON         NOT NULL,
    schema_version      INT          NOT NULL DEFAULT 1,
    checksum            VARCHAR(64)  NOT NULL,
    effective_at        DATETIME(3)  NOT NULL,
    change_id           BIGINT       NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (config_group, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配置版本(已生效内容不可修改)';

CREATE TABLE admin_audit_log (
    log_id              BIGINT       NOT NULL,
    actor_id            BIGINT       NOT NULL,
    action              VARCHAR(64)  NOT NULL,
    resource_type       VARCHAR(32)  NOT NULL,
    resource_id         BIGINT       NOT NULL,
    request_id          VARCHAR(64)  NULL,
    result              VARCHAR(16)  NOT NULL,
    reason              VARCHAR(512) NULL,
    before_hash         VARCHAR(64)  NULL,
    after_hash          VARCHAR(64)  NULL,
    occurred_at         DATETIME(3)  NOT NULL,
    PRIMARY KEY (log_id),
    INDEX idx_admin_audit_actor (actor_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员审计日志';

CREATE TABLE notification_delivery (
    delivery_id         BIGINT       NOT NULL,
    user_id             BIGINT       NOT NULL,
    template_code       VARCHAR(64)  NOT NULL,
    business_key        VARCHAR(128) NOT NULL,
    channel             VARCHAR(16)  NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts            INT          NOT NULL DEFAULT 0,
    sent_at             DATETIME(3)  NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (delivery_id),
    UNIQUE KEY uk_notification_delivery (user_id, template_code, business_key, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知投递(防重复到期提醒)';

CREATE TABLE compliance_gate (
    gate_code           VARCHAR(64)  NOT NULL,
    scope               VARCHAR(32)  NOT NULL,
    evidence_ref        VARCHAR(256) NULL,
    owner_id            BIGINT       NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    approved_at         DATETIME(3)  NULL,
    expiry_at           DATETIME(3)  NULL,
    PRIMARY KEY (gate_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合规门禁(AI启用/上线发布)';
