-- ============================================================
-- 链盛通 LSC 消费回馈权益系统 V7.7.2 数据库建表脚本
-- MySQL 8.0+  单个高可用主库（模块化单体，无分片）
-- 编制依据：V7.7.2 技术开发方案 第十二章
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `lsc_system`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `lsc_system`;

-- ============================================================
-- 12.2 用户主体与权限
-- ============================================================

CREATE TABLE IF NOT EXISTS `user` (
    `user_id`               BIGINT       NOT NULL                COMMENT '用户ID(雪花算法)',
    `mobile_enc`            VARCHAR(512) NOT NULL                COMMENT '手机号密文(用于展示)',
    `mobile_lookup_hash`    VARCHAR(64)  NOT NULL                COMMENT '手机号受控摘要(用于去重)',
    `nickname`              VARCHAR(64)           DEFAULT NULL    COMMENT '昵称',
    `user_type`             VARCHAR(16)  NOT NULL DEFAULT 'UNVERIFIED' COMMENT 'UNVERIFIED/C/B',
    `account_status`        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '账户状态',
    `referrer_user_id`      BIGINT                DEFAULT NULL    COMMENT '直接推荐人ID',
    `first_qualified_order_id` BIGINT            DEFAULT NULL    COMMENT '首笔合格订单ID',
    `privacy_version`       VARCHAR(32)           DEFAULT NULL    COMMENT '隐私协议版本',
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_mobile_hash` (`mobile_lookup_hash`),
    KEY `idx_referrer` (`referrer_user_id`),
    KEY `idx_user_type` (`user_type`)
) ENGINE=InnoDB COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `legal_entity` (
    `entity_id`             BIGINT       NOT NULL                COMMENT '主体ID',
    `legal_name`            VARCHAR(128) NOT NULL                COMMENT '法律主体名称',
    `registration_no`       VARCHAR(64)           DEFAULT NULL    COMMENT '统一社会信用代码',
    `role`                  VARCHAR(32)  NOT NULL                COMMENT 'PLATFORM/SELLER/PAYEE/INVOICE/BENEFIT',
    `pay_merchant_id`       VARCHAR(64)           DEFAULT NULL    COMMENT '支付机构商户号',
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    `verified_at`           DATETIME(3)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`entity_id`)
) ENGINE=InnoDB COMMENT='法律主体表';

CREATE TABLE IF NOT EXISTS `business_profile` (
    `user_id`               BIGINT       NOT NULL                COMMENT 'B端用户ID',
    `entity_name`           VARCHAR(128) NOT NULL                COMMENT '企业名称',
    `license_no`            VARCHAR(64)  NOT NULL                COMMENT '营业执照号',
    `license_expiry`        DATE                  DEFAULT NULL    COMMENT '执照到期日',
    `business_status`       VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/APPROVED/REJECTED/SUSPENDED/EXPIRED',
    `approved_version`      INT                   DEFAULT 0       COMMENT '审批版本',
    `approved_at`           DATETIME(3)           DEFAULT NULL,
    `qualification_expire_at` DATETIME(3)         DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB COMMENT='B端资质表';

CREATE TABLE IF NOT EXISTS `business_audit_record` (
    `audit_id`              BIGINT       NOT NULL                COMMENT '审核记录ID',
    `user_id`               BIGINT       NOT NULL,
    `application_version`   INT          NOT NULL,
    `license_object_key`    VARCHAR(512)          DEFAULT NULL    COMMENT '执照影像对象存储key',
    `ocr_result_enc`        TEXT                  DEFAULT NULL    COMMENT 'OCR结果(加密)',
    `status`                VARCHAR(16)  NOT NULL,
    `auditor_id`            BIGINT                DEFAULT NULL,
    `remark`                VARCHAR(512)          DEFAULT NULL,
    `audited_at`            DATETIME(3)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`audit_id`),
    KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='B端资质审核记录';

CREATE TABLE IF NOT EXISTS `admin_role_binding` (
    `binding_id`            BIGINT       NOT NULL                COMMENT '绑定ID',
    `admin_id`              BIGINT       NOT NULL,
    `role_code`             VARCHAR(32)  NOT NULL                COMMENT '角色编码',
    `scope_type`            VARCHAR(16)  NOT NULL                COMMENT 'GLOBAL/COMMODITY/ORDER/FINANCE/RISK',
    `scope_id`              BIGINT                DEFAULT NULL,
    `valid_until`           DATETIME(3)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`binding_id`),
    UNIQUE KEY `uk_admin_role_scope` (`admin_id`, `role_code`, `scope_type`, `scope_id`)
) ENGINE=InnoDB COMMENT='管理员角色绑定';

CREATE TABLE IF NOT EXISTS `user_agreement_acceptance` (
    `acceptance_id`         BIGINT       NOT NULL,
    `user_id`               BIGINT       NOT NULL,
    `agreement_type`        VARCHAR(32)  NOT NULL                COMMENT 'PRIVACY/TERMS/PROMOTION',
    `agreement_version`     VARCHAR(32)  NOT NULL,
    `accepted_at`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `client_evidence_ref`   VARCHAR(512)          DEFAULT NULL,
    PRIMARY KEY (`acceptance_id`),
    UNIQUE KEY `uk_user_agreement` (`user_id`, `agreement_type`, `agreement_version`)
) ENGINE=InnoDB COMMENT='用户协议确认';

-- ============================================================
-- 12.3 商品与价格
-- ============================================================

CREATE TABLE IF NOT EXISTS `product` (
    `product_id`            BIGINT       NOT NULL                COMMENT '商品ID',
    `seller_entity_id`      BIGINT       NOT NULL                COMMENT '销售主体ID',
    `name`                  VARCHAR(256) NOT NULL,
    `category_id`           BIGINT                DEFAULT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/REVIEWING/ON_SALE/OFF_SALE/SOLD_OUT',
    `audit_version`         INT          NOT NULL DEFAULT 0,
    `description_ref`       VARCHAR(512)          DEFAULT NULL,
    `return_policy_version` VARCHAR(32)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`product_id`),
    KEY `idx_status` (`status`),
    KEY `idx_category` (`category_id`)
) ENGINE=InnoDB COMMENT='商品表';

CREATE TABLE IF NOT EXISTS `product_sku` (
    `sku_id`                BIGINT       NOT NULL                COMMENT 'SKU ID',
    `product_id`            BIGINT       NOT NULL,
    `sku_code`              VARCHAR(64)  NOT NULL                COMMENT 'SKU编码',
    `spec_json`             JSON                  DEFAULT NULL    COMMENT '规格JSON',
    `sale_unit`             VARCHAR(16)           DEFAULT '件',
    `pack_qty`              INT          NOT NULL DEFAULT 1       COMMENT '箱规',
    `b_min_qty`             INT          NOT NULL DEFAULT 1       COMMENT 'B端最小采购量',
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`sku_id`),
    UNIQUE KEY `uk_sku_code` (`sku_code`),
    KEY `idx_product` (`product_id`)
) ENGINE=InnoDB COMMENT='SKU表';

CREATE TABLE IF NOT EXISTS `product_price_version` (
    `version_id`            BIGINT       NOT NULL                COMMENT '价格版本ID',
    `sku_id`                BIGINT       NOT NULL,
    `price_version`         INT          NOT NULL                COMMENT '版本号',
    `retail_price_cent`     BIGINT       NOT NULL                COMMENT 'C端零售价(分)',
    `b_price_cent`          BIGINT       NOT NULL                COMMENT 'B端采购价(分)',
    `cost_price_enc`        VARCHAR(512) NOT NULL                COMMENT '成本密文',
    `cost_key_version`      INT          NOT NULL DEFAULT 1,
    `cost_basis_code`       VARCHAR(32)           DEFAULT NULL,
    `grant_coefficient_ppm` BIGINT       NOT NULL DEFAULT 800000  COMMENT '赠送系数ppm',
    `grant_c_unit`          BIGINT       NOT NULL DEFAULT 0       COMMENT 'C端赠送基准(unit)',
    `grant_b_unit`          BIGINT       NOT NULL DEFAULT 0       COMMENT 'B端赠送基准(unit)',
    `effective_at`          DATETIME(3)  NOT NULL,
    `approved_by`           BIGINT                DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`version_id`),
    UNIQUE KEY `uk_sku_version` (`sku_id`, `price_version`)
) ENGINE=InnoDB COMMENT='商品价格版本表';

CREATE TABLE IF NOT EXISTS `product_audit_record` (
    `audit_id`              BIGINT       NOT NULL,
    `product_id`            BIGINT       NOT NULL,
    `version`               INT          NOT NULL,
    `reviewer_id`           BIGINT                DEFAULT NULL,
    `decision`              VARCHAR(16)  NOT NULL,
    `reason`                VARCHAR(512)          DEFAULT NULL,
    `evidence_ref`          VARCHAR(512)          DEFAULT NULL,
    `reviewed_at`           DATETIME(3)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`audit_id`),
    KEY `idx_product` (`product_id`)
) ENGINE=InnoDB COMMENT='商品审核记录';

-- ============================================================
-- 12.4 订单支付与退款
-- ============================================================

CREATE TABLE IF NOT EXISTS `orders` (
    `order_id`              BIGINT       NOT NULL                COMMENT '订单ID',
    `order_no`              VARCHAR(32)  NOT NULL                COMMENT '订单号',
    `user_id`               BIGINT       NOT NULL                COMMENT '下单用户',
    `buyer_type_snapshot`   VARCHAR(16)  NOT NULL                COMMENT '下单时身份快照 C/B',
    `seller_entity_id`      BIGINT       NOT NULL                COMMENT '销售主体',
    `payee_entity_id`       BIGINT       NOT NULL                COMMENT '收款主体',
    `invoice_entity_id`     BIGINT       NOT NULL                COMMENT '开票主体',
    `benefit_obligor_entity_id` BIGINT   NOT NULL                COMMENT '权益义务主体',
    `discount_mode`         VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE/LSC/COUPON',
    `goods_cent`            BIGINT       NOT NULL DEFAULT 0       COMMENT '商品金额(分)',
    `shipping_cent`         BIGINT       NOT NULL DEFAULT 0       COMMENT '运费(分)',
    `coupon_cent`           BIGINT       NOT NULL DEFAULT 0       COMMENT '券优惠(分)',
    `lsc_unit`              BIGINT       NOT NULL DEFAULT 0       COMMENT 'LSC抵扣(unit)',
    `rmb_cent`              BIGINT       NOT NULL DEFAULT 0       COMMENT '实付人民币(分)',
    `payment_status`        VARCHAR(16)  NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID/PAYING/PAID/REFUNDING/PART_REFUNDED/REFUNDED/EXCEPTION',
    `fulfillment_status`    VARCHAR(16)  NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/CONFIRMED/SHIPPED/COMPLETED/CANCELED/CLOSED',
    `refund_status`         VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    `expires_at`            DATETIME(3)  NOT NULL                COMMENT '支付截止',
    `completed_at`          DATETIME(3)           DEFAULT NULL,
    `quote_version`         VARCHAR(64)           DEFAULT NULL,
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`order_id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user` (`user_id`),
    KEY `idx_payment_status` (`payment_status`),
    KEY `idx_fulfillment` (`fulfillment_status`),
    CONSTRAINT `chk_non_negative` CHECK (`coupon_cent` >= 0 AND `lsc_unit` >= 0),
    CONSTRAINT `chk_mutex` CHECK (NOT (`coupon_cent` > 0 AND `lsc_unit` > 0)),
    CONSTRAINT `chk_unit_100` CHECK (MOD(`lsc_unit`, 100) = 0),
    CONSTRAINT `chk_rmb` CHECK (`rmb_cent` = `goods_cent` + `shipping_cent` - `coupon_cent` - `lsc_unit` / 100)
) ENGINE=InnoDB COMMENT='订单表';

CREATE TABLE IF NOT EXISTS `order_item` (
    `item_id`               BIGINT       NOT NULL                COMMENT '订单行ID',
    `order_id`              BIGINT       NOT NULL,
    `sku_id`                BIGINT       NOT NULL,
    `item_seq`              INT          NOT NULL                COMMENT '行序号',
    `qty`                   INT          NOT NULL,
    `price_version`         INT          NOT NULL,
    `unit_price_cent`       BIGINT       NOT NULL                COMMENT '单价(分)',
    `line_goods_cent`       BIGINT       NOT NULL                COMMENT '行商品金额',
    `coupon_share_cent`     BIGINT       NOT NULL DEFAULT 0       COMMENT '券分摊(分)',
    `lsc_share_unit`        BIGINT       NOT NULL DEFAULT 0       COMMENT 'LSC分摊(unit)',
    `rmb_share_cent`        BIGINT       NOT NULL DEFAULT 0       COMMENT '人民币分摊(分)',
    `grant_coefficient_ppm` BIGINT       NOT NULL DEFAULT 800000,
    `cost_snapshot_enc`     VARCHAR(512)          DEFAULT NULL    COMMENT '成本快照密文',
    `granted_unit`          BIGINT       NOT NULL DEFAULT 0       COMMENT '已赠送unit',
    `grant_lot_id`          BIGINT                DEFAULT NULL,
    `clawback_required_unit` BIGINT      NOT NULL DEFAULT 0       COMMENT '应撤回unit',
    `clawback_completed_unit` BIGINT     NOT NULL DEFAULT 0       COMMENT '已撤回unit',
    `clawback_pending_unit` BIGINT       NOT NULL DEFAULT 0       COMMENT '待追偿unit',
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`item_id`),
    UNIQUE KEY `uk_order_seq` (`order_id`, `item_seq`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB COMMENT='订单行表';

CREATE TABLE IF NOT EXISTS `order_unit_allocation` (
    `allocation_id`         BIGINT       NOT NULL                COMMENT '单元分配ID',
    `item_id`               BIGINT       NOT NULL,
    `unit_index`            INT          NOT NULL                COMMENT '单元序号',
    `sale_cent`             BIGINT       NOT NULL                COMMENT '销售金额(分)',
    `coupon_cent`           BIGINT       NOT NULL DEFAULT 0,
    `lsc_unit`              BIGINT       NOT NULL DEFAULT 0,
    `rmb_cent`              BIGINT       NOT NULL                COMMENT '人民币分摊(分)',
    `grant_unit`            BIGINT       NOT NULL DEFAULT 0       COMMENT '该单元赠送unit',
    `refunded_rmb_cent`     BIGINT       NOT NULL DEFAULT 0,
    `refunded_lsc_unit`     BIGINT       NOT NULL DEFAULT 0,
    `clawback_target_unit`  BIGINT       NOT NULL DEFAULT 0,
    `return_status`         VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`allocation_id`),
    UNIQUE KEY `uk_item_unit` (`item_id`, `unit_index`),
    KEY `idx_item` (`item_id`)
) ENGINE=InnoDB COMMENT='订单单元分配表';

CREATE TABLE IF NOT EXISTS `payment_attempt` (
    `payment_id`            BIGINT       NOT NULL,
    `order_id`              BIGINT       NOT NULL,
    `channel`               VARCHAR(32)  NOT NULL,
    `merchant_id`           VARCHAR(64)  NOT NULL,
    `channel_trade_no`      VARCHAR(64)           DEFAULT NULL,
    `merchant_request_no`   VARCHAR(64)  NOT NULL,
    `amount_cent`           BIGINT       NOT NULL,
    `currency`              VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'INIT',
    `channel_paid_at`       DATETIME(3)           DEFAULT NULL,
    `closed_at`             DATETIME(3)           DEFAULT NULL,
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`payment_id`),
    UNIQUE KEY `uk_channel_trade` (`channel`, `merchant_id`, `channel_trade_no`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB COMMENT='支付尝试表';

CREATE TABLE IF NOT EXISTS `payment_callback` (
    `callback_id`           BIGINT       NOT NULL,
    `channel_event_id`      VARCHAR(128) NOT NULL,
    `channel_trade_no`      VARCHAR(64)           DEFAULT NULL,
    `payload_hash`          VARCHAR(64)  NOT NULL,
    `verified`              TINYINT(1)   NOT NULL DEFAULT 0,
    `received_at`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `processed_at`          DATETIME(3)           DEFAULT NULL,
    `result`                VARCHAR(16)           DEFAULT NULL,
    PRIMARY KEY (`callback_id`),
    UNIQUE KEY `uk_channel_event` (`channel_event_id`)
) ENGINE=InnoDB COMMENT='支付回调表';

CREATE TABLE IF NOT EXISTS `refund_order` (
    `refund_id`             BIGINT       NOT NULL,
    `refund_no`             VARCHAR(32)  NOT NULL,
    `order_id`              BIGINT       NOT NULL,
    `reason`                VARCHAR(512)          DEFAULT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED/REVIEWING/APPROVED/CHANNEL_PENDING/SUCCEEDED/FAILED/CLOSED',
    `benefit_status`        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DONE/EXCEPTION',
    `rmb_cent`              BIGINT       NOT NULL DEFAULT 0       COMMENT '应退人民币(分)',
    `shipping_refund_cent`  BIGINT       NOT NULL DEFAULT 0,
    `channel_refund_no`     VARCHAR(64)           DEFAULT NULL,
    `approved_by`           BIGINT                DEFAULT NULL,
    `succeeded_at`          DATETIME(3)           DEFAULT NULL,
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`refund_id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB COMMENT='退款单表';

CREATE TABLE IF NOT EXISTS `refund_item` (
    `refund_item_id`        BIGINT       NOT NULL,
    `refund_id`             BIGINT       NOT NULL,
    `item_id`               BIGINT       NOT NULL,
    `refund_kind`           VARCHAR(16)  NOT NULL                COMMENT 'FULL/PARTIAL/PRICE_DIFF',
    `qty`                   INT          NOT NULL,
    `rmb_cent`              BIGINT       NOT NULL DEFAULT 0,
    `lsc_restore_unit`      BIGINT       NOT NULL DEFAULT 0       COMMENT '返还LSC(unit)',
    `grant_clawback_unit`   BIGINT       NOT NULL DEFAULT 0       COMMENT '撤回赠送(unit)',
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`refund_item_id`),
    UNIQUE KEY `uk_refund_item_kind` (`refund_id`, `item_id`, `refund_kind`)
) ENGINE=InnoDB COMMENT='退款行表';

CREATE TABLE IF NOT EXISTS `refund_allocation` (
    `allocation_id`         BIGINT       NOT NULL,
    `refund_id`             BIGINT       NOT NULL,
    `order_unit_allocation_id` BIGINT    NOT NULL                COMMENT '原订单单元',
    `rmb_cent`              BIGINT       NOT NULL DEFAULT 0,
    `lsc_unit`              BIGINT       NOT NULL DEFAULT 0,
    `grant_target_delta_unit` BIGINT     NOT NULL DEFAULT 0       COMMENT '本次撤回增量',
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`allocation_id`),
    UNIQUE KEY `uk_refund_unit` (`refund_id`, `order_unit_allocation_id`)
) ENGINE=InnoDB COMMENT='退款单元分配表';

CREATE TABLE IF NOT EXISTS `invoice_record` (
    `invoice_id`            BIGINT       NOT NULL,
    `order_id`              BIGINT       NOT NULL,
    `entity_id`             BIGINT       NOT NULL,
    `invoice_type`          VARCHAR(16)  NOT NULL,
    `buyer_tax_info_enc`    TEXT                  DEFAULT NULL,
    `invoice_no`            VARCHAR(32)           DEFAULT NULL,
    `tax_rule_version`      VARCHAR(32)           DEFAULT NULL,
    `amount_cent`           BIGINT       NOT NULL DEFAULT 0,
    `tax_cent`              BIGINT       NOT NULL DEFAULT 0,
    `status`                VARCHAR(16)  NOT NULL,
    `original_invoice_id`   BIGINT                DEFAULT NULL    COMMENT '红冲/重开关联',
    `issued_at`             DATETIME(3)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`invoice_id`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB COMMENT='发票记录表';

-- ============================================================
-- 12.5 权益核心表
-- ============================================================

CREATE TABLE IF NOT EXISTS `lsc_account` (
    `user_id`               BIGINT       NOT NULL                COMMENT '用户ID(主键)',
    `locked_unit`           BIGINT       NOT NULL DEFAULT 0       COMMENT '锁定余额',
    `available_unit`        BIGINT       NOT NULL DEFAULT 0       COMMENT '可用余额',
    `reserved_unit`         BIGINT       NOT NULL DEFAULT 0       COMMENT '支付占用',
    `frozen_locked_unit`    BIGINT       NOT NULL DEFAULT 0       COMMENT '冻结锁定',
    `frozen_available_unit` BIGINT       NOT NULL DEFAULT 0       COMMENT '冻结可用',
    `pending_recovery_unit` BIGINT       NOT NULL DEFAULT 0       COMMENT '待追偿',
    `last_event_seq`        BIGINT       NOT NULL DEFAULT 0       COMMENT '用户事件序号',
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`),
    CONSTRAINT `chk_non_negative` CHECK (`locked_unit` >= 0 AND `available_unit` >= 0
        AND `reserved_unit` >= 0 AND `frozen_locked_unit` >= 0 AND `frozen_available_unit` >= 0
        AND `pending_recovery_unit` >= 0)
) ENGINE=InnoDB COMMENT='LSC账户表(5桶+追偿投影)';

CREATE TABLE IF NOT EXISTS `lsc_grant_lot` (
    `grant_lot_id`          BIGINT       NOT NULL                COMMENT '赠送批次ID',
    `user_id`               BIGINT       NOT NULL,
    `source_item_id`        BIGINT       NOT NULL                COMMENT '源订单行',
    `original_grant_unit`   BIGINT       NOT NULL                COMMENT '原始赠送量(不可修改)',
    `remaining_locked_unit` BIGINT       NOT NULL                COMMENT '未释放未冻结锁定',
    `frozen_locked_unit`    BIGINT       NOT NULL DEFAULT 0       COMMENT '冻结锁定',
    `released_total_unit`   BIGINT       NOT NULL DEFAULT 0       COMMENT '累计释放',
    `revoked_locked_unit`   BIGINT       NOT NULL DEFAULT 0       COMMENT '锁定来源撤回',
    `remainder_nano_unit`   BIGINT       NOT NULL DEFAULT 0       COMMENT '余数0-999999999',
    `grant_business_date`   DATE         NOT NULL                COMMENT '赠送业务日',
    `first_release_date`    DATE         NOT NULL                COMMENT '首次释放日=赠送日+1',
    `last_processed_date`   DATE                  DEFAULT NULL    COMMENT '上次处理日',
    `state`                 VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED/RELEASED/REVOKED',
    `refund_hold`           TINYINT(1)   NOT NULL DEFAULT 0       COMMENT '退款处理中暂停释放',
    `rule_version`          VARCHAR(32)           DEFAULT NULL,
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`grant_lot_id`),
    KEY `idx_user_state` (`user_id`, `state`),
    KEY `idx_first_release` (`first_release_date`, `state`),
    CONSTRAINT `chk_remainder` CHECK (`remainder_nano_unit` >= 0 AND `remainder_nano_unit` < 1000000000),
    CONSTRAINT `chk_grant_id` CHECK (`original_grant_unit` = `remaining_locked_unit` + `frozen_locked_unit`
        + `released_total_unit` + `revoked_locked_unit`)
) ENGINE=InnoDB COMMENT='LSC赠送批次表(GrantLot)';

CREATE TABLE IF NOT EXISTS `lsc_available_lot` (
    `available_lot_id`      BIGINT       NOT NULL                COMMENT '可用批次ID',
    `user_id`               BIGINT       NOT NULL,
    `source_grant_lot_id`   BIGINT                DEFAULT NULL    COMMENT '源GrantLot',
    `origin_type`           VARCHAR(32)  NOT NULL                COMMENT 'DAILY_RELEASE/REFUND_RESTORE/LEGACY_OPENING',
    `source_event_id`       BIGINT                DEFAULT NULL,
    `lot_sequence`          BIGINT       NOT NULL                COMMENT '批次内序号',
    `issued_unit`           BIGINT       NOT NULL DEFAULT 0       COMMENT '发放量',
    `restored_unit`         BIGINT       NOT NULL DEFAULT 0       COMMENT '退款返还量',
    `available_unit`        BIGINT       NOT NULL DEFAULT 0       COMMENT '可用',
    `reserved_unit`         BIGINT       NOT NULL DEFAULT 0       COMMENT '占用',
    `frozen_unit`           BIGINT       NOT NULL DEFAULT 0       COMMENT '冻结',
    `consumed_unit`         BIGINT       NOT NULL DEFAULT 0       COMMENT '已消费',
    `expired_unit`          BIGINT       NOT NULL DEFAULT 0       COMMENT '已过期',
    `revoked_unit`          BIGINT       NOT NULL DEFAULT 0       COMMENT '已撤回',
    `available_at`          DATETIME(3)  NOT NULL                COMMENT '可用时刻',
    `expire_at`             DATETIME(3)  NOT NULL                COMMENT '到期时刻',
    `terminal_at`           DATETIME(3)           DEFAULT NULL    COMMENT '终结时刻',
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`available_lot_id`),
    UNIQUE KEY `uk_source_seq` (`source_event_id`, `lot_sequence`),
    KEY `idx_user_fefo` (`user_id`, `expire_at`, `available_at`, `available_lot_id`),
    KEY `idx_expire` (`expire_at`),
    CONSTRAINT `chk_available_id` CHECK (`issued_unit` + `restored_unit` = `available_unit` + `reserved_unit`
        + `frozen_unit` + `consumed_unit` + `expired_unit` + `revoked_unit`)
) ENGINE=InnoDB COMMENT='LSC可用批次表(AvailableLot)';

CREATE TABLE IF NOT EXISTS `lsc_event` (
    `event_id`              BIGINT       NOT NULL                COMMENT '事件ID(主键)',
    `user_id`               BIGINT       NOT NULL,
    `user_event_seq`        BIGINT       NOT NULL                COMMENT '用户内事件序号',
    `event_type`            VARCHAR(32)  NOT NULL                COMMENT '事件类型',
    `business_key`          VARCHAR(128) NOT NULL                COMMENT '业务幂等键',
    `request_hash`          VARCHAR(64)           DEFAULT NULL,
    `order_id`              BIGINT                DEFAULT NULL,
    `refund_id`             BIGINT                DEFAULT NULL,
    `case_id`               BIGINT                DEFAULT NULL,
    `business_date`         DATE         NOT NULL,
    `occurred_at`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `rule_version`          VARCHAR(32)           DEFAULT NULL,
    `original_event_id`     BIGINT                DEFAULT NULL    COMMENT '纠错反向引用',
    `payload_version`       INT          NOT NULL DEFAULT 1,
    `payload_json`          JSON                  DEFAULT NULL,
    `event_hash`            VARCHAR(64)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`event_id`),
    UNIQUE KEY `uk_user_bizkey` (`user_id`, `business_key`),
    UNIQUE KEY `uk_user_seq` (`user_id`, `user_event_seq`),
    KEY `idx_user_time` (`user_id`, `occurred_at`)
) ENGINE=InnoDB COMMENT='LSC事件表(事实源-不可修改)';

CREATE TABLE IF NOT EXISTS `lsc_entry` (
    `entry_id`              BIGINT       NOT NULL                COMMENT '分录ID',
    `event_id`              BIGINT       NOT NULL,
    `entry_seq`             INT          NOT NULL                COMMENT '分录序号',
    `user_id`               BIGINT       NOT NULL,
    `grant_lot_id`          BIGINT                DEFAULT NULL,
    `available_lot_id`      BIGINT                DEFAULT NULL,
    `bucket`                VARCHAR(32)  NOT NULL                COMMENT '余额桶',
    `delta_unit`            BIGINT       NOT NULL                COMMENT '变化量(有符号)',
    `disposition_type`      VARCHAR(32)           DEFAULT NULL    COMMENT '处置类别',
    `allocation_ref`        VARCHAR(128)          DEFAULT NULL,
    `before_unit`           BIGINT       NOT NULL DEFAULT 0,
    `after_unit`            BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`entry_id`),
    UNIQUE KEY `uk_event_seq` (`event_id`, `entry_seq`),
    KEY `idx_user_lot` (`user_id`, `available_lot_id`),
    KEY `idx_event` (`event_id`)
) ENGINE=InnoDB COMMENT='LSC分录表(事实源)';

CREATE TABLE IF NOT EXISTS `lsc_reservation` (
    `reservation_id`        BIGINT       NOT NULL                COMMENT '占用ID',
    `user_id`               BIGINT       NOT NULL,
    `order_id`              BIGINT       NOT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CAPTURED/RELEASED/REFUND_RETURNED/EXCEPTION',
    `reserved_total_unit`   BIGINT       NOT NULL                COMMENT '占用总量',
    `expires_at`            DATETIME(3)  NOT NULL,
    `channel_close_state`   VARCHAR(16)           DEFAULT NULL,
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`reservation_id`),
    KEY `idx_order` (`order_id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='LSC支付占用表';

CREATE TABLE IF NOT EXISTS `lsc_reservation_allocation` (
    `alloc_id`              BIGINT       NOT NULL,
    `reservation_id`        BIGINT       NOT NULL,
    `available_lot_id`      BIGINT       NOT NULL,
    `reserved_unit`         BIGINT       NOT NULL,
    `captured_unit`         BIGINT       NOT NULL DEFAULT 0,
    `released_unit`         BIGINT       NOT NULL DEFAULT 0,
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`alloc_id`),
    UNIQUE KEY `uk_reserv_lot` (`reservation_id`, `available_lot_id`)
) ENGINE=InnoDB COMMENT='LSC占用分配表';

CREATE TABLE IF NOT EXISTS `lsc_consumption_allocation` (
    `consumption_id`        BIGINT       NOT NULL                COMMENT '消费分配ID',
    `user_id`               BIGINT       NOT NULL,
    `order_unit_allocation_id` BIGINT    NOT NULL                COMMENT '订单单元',
    `available_lot_id`      BIGINT       NOT NULL,
    `captured_unit`         BIGINT       NOT NULL                COMMENT '核销量',
    `returned_unit`         BIGINT       NOT NULL DEFAULT 0       COMMENT '已返还量',
    `capture_event_id`      BIGINT                DEFAULT NULL,
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`consumption_id`),
    KEY `idx_user_unit` (`user_id`, `order_unit_allocation_id`),
    CONSTRAINT `chk_return` CHECK (`returned_unit` <= `captured_unit`)
) ENGINE=InnoDB COMMENT='LSC消费分配表';

CREATE TABLE IF NOT EXISTS `lsc_return_allocation` (
    `return_alloc_id`       BIGINT       NOT NULL,
    `refund_id`             BIGINT       NOT NULL,
    `consumption_id`        BIGINT       NOT NULL,
    `target_available_lot_id` BIGINT     NOT NULL                COMMENT '返还目标批次',
    `returned_unit`         BIGINT       NOT NULL,
    `return_event_id`       BIGINT                DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`return_alloc_id`),
    UNIQUE KEY `uk_refund_consumption_target` (`refund_id`, `consumption_id`, `target_available_lot_id`)
) ENGINE=InnoDB COMMENT='LSC退款返还分配表';

CREATE TABLE IF NOT EXISTS `lsc_freeze_allocation` (
    `freeze_id`             BIGINT       NOT NULL,
    `user_id`               BIGINT       NOT NULL,
    `case_id`               BIGINT       NOT NULL                COMMENT '风控案件',
    `source_bucket`         VARCHAR(32)  NOT NULL                COMMENT '来源桶',
    `grant_lot_id`          BIGINT                DEFAULT NULL,
    `available_lot_id`      BIGINT                DEFAULT NULL,
    `frozen_unit`           BIGINT       NOT NULL,
    `released_unit`         BIGINT       NOT NULL DEFAULT 0,
    `revoked_unit`          BIGINT       NOT NULL DEFAULT 0,
    `expired_unit`          BIGINT       NOT NULL DEFAULT 0,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`freeze_id`),
    KEY `idx_user_case` (`user_id`, `case_id`)
) ENGINE=InnoDB COMMENT='LSC冻结分配表';

CREATE TABLE IF NOT EXISTS `lsc_recovery` (
    `recovery_id`           BIGINT       NOT NULL                COMMENT '追偿ID',
    `user_id`               BIGINT       NOT NULL,
    `source_item_id`        BIGINT       NOT NULL                COMMENT '源订单行',
    `required_unit`         BIGINT       NOT NULL                COMMENT '应追偿',
    `recovered_unit`        BIGINT       NOT NULL DEFAULT 0       COMMENT '已追偿',
    `satisfied_by_expiry_unit` BIGINT    NOT NULL DEFAULT 0       COMMENT '过期抵充',
    `pending_unit`          BIGINT       NOT NULL DEFAULT 0       COMMENT '待追偿',
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PARTIAL/CLEARED/DISPUTED',
    `opened_at`             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `closed_at`             DATETIME(3)           DEFAULT NULL,
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`recovery_id`),
    UNIQUE KEY `uk_user_item` (`user_id`, `source_item_id`),
    CONSTRAINT `chk_recovery` CHECK (`required_unit` = `recovered_unit` + `satisfied_by_expiry_unit` + `pending_unit`)
) ENGINE=InnoDB COMMENT='LSC待追偿表';

CREATE TABLE IF NOT EXISTS `lsc_recovery_allocation` (
    `alloc_id`              BIGINT       NOT NULL,
    `recovery_id`           BIGINT       NOT NULL,
    `source_entry_id`       BIGINT                DEFAULT NULL,
    `source_available_lot_id` BIGINT              DEFAULT NULL,
    `satisfaction_type`     VARCHAR(32)  NOT NULL                COMMENT 'RECOVERED/EXPIRY',
    `amount_unit`           BIGINT       NOT NULL,
    `event_id`              BIGINT                DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`alloc_id`),
    UNIQUE KEY `uk_recovery_event_alloc` (`recovery_id`, `event_id`, `alloc_id`)
) ENGINE=InnoDB COMMENT='LSC追偿分配表';

-- ============================================================
-- 12.6 日释放与对账
-- ============================================================

CREATE TABLE IF NOT EXISTS `release_day_snapshot` (
    `snapshot_id`           BIGINT       NOT NULL,
    `business_date`         DATE         NOT NULL                COMMENT '业务日',
    `previous_date`         DATE                  DEFAULT NULL,
    `consumed_unit`         BIGINT       NOT NULL DEFAULT 0       COMMENT '成功核销',
    `expired_unit`          BIGINT       NOT NULL DEFAULT 0       COMMENT '到期作废',
    `returned_unit`         BIGINT       NOT NULL DEFAULT 0       COMMENT '退款返还入批次',
    `denominator_unit`      BIGINT       NOT NULL DEFAULT 0       COMMENT '可用存量分母B',
    `raw_net_unit`          BIGINT       NOT NULL DEFAULT 0       COMMENT '原始净消耗N',
    `w_ppm`                 BIGINT       NOT NULL DEFAULT 0       COMMENT '周转率ppm',
    `rate_ppb`              BIGINT       NOT NULL DEFAULT 0       COMMENT '释放率ppb',
    `config_version`        VARCHAR(32)           DEFAULT NULL,
    `snapshot_hash`         VARCHAR(64)           DEFAULT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'COLLECTING' COMMENT 'COLLECTING/SEALED/INVALID',
    `sealed_at`             DATETIME(3)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`snapshot_id`),
    UNIQUE KEY `uk_business_date` (`business_date`)
) ENGINE=InnoDB COMMENT='日释放快照表';

CREATE TABLE IF NOT EXISTS `release_lot_result` (
    `result_id`             BIGINT       NOT NULL,
    `grant_lot_id`          BIGINT       NOT NULL,
    `business_date`         DATE         NOT NULL,
    `snapshot_version`      VARCHAR(32)           DEFAULT NULL,
    `rate_ppb`              BIGINT       NOT NULL DEFAULT 0,
    `remainder_before`      BIGINT       NOT NULL DEFAULT 0,
    `remainder_after`       BIGINT       NOT NULL DEFAULT 0,
    `released_unit`         BIGINT       NOT NULL DEFAULT 0,
    `skip_reason`           VARCHAR(64)           DEFAULT NULL,
    `event_id`              BIGINT                DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`result_id`),
    UNIQUE KEY `uk_lot_date` (`grant_lot_id`, `business_date`)
) ENGINE=InnoDB COMMENT='Lot释放结果表';

CREATE TABLE IF NOT EXISTS `task_run` (
    `run_id`                BIGINT       NOT NULL,
    `task_type`             VARCHAR(32)  NOT NULL,
    `business_date`         DATE         NOT NULL,
    `status`                VARCHAR(16)  NOT NULL,
    `cursor`                BIGINT                DEFAULT NULL,
    `lease_owner`           VARCHAR(64)           DEFAULT NULL,
    `fencing_token`         BIGINT                DEFAULT NULL,
    `started_at`            DATETIME(3)           DEFAULT NULL,
    `finished_at`           DATETIME(3)           DEFAULT NULL,
    `error_summary`         VARCHAR(1024)         DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`run_id`),
    KEY `idx_type_date` (`task_type`, `business_date`)
) ENGINE=InnoDB COMMENT='任务运行记录表';

CREATE TABLE IF NOT EXISTS `reconciliation_run` (
    `recon_id`              BIGINT       NOT NULL,
    `scope_type`            VARCHAR(32)  NOT NULL,
    `scope_id`              BIGINT                DEFAULT NULL,
    `business_date`         DATE         NOT NULL,
    `cutoff_event_seq`      BIGINT                DEFAULT NULL,
    `status`                VARCHAR(16)  NOT NULL,
    `difference_count`      INT          NOT NULL DEFAULT 0,
    `report_ref`            VARCHAR(512)          DEFAULT NULL,
    `resolved_by`           BIGINT                DEFAULT NULL,
    `resolved_at`           DATETIME(3)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`recon_id`),
    KEY `idx_date_scope` (`business_date`, `scope_type`)
) ENGINE=InnoDB COMMENT='对账运行表';

CREATE TABLE IF NOT EXISTS `reconciliation_difference` (
    `diff_id`               BIGINT       NOT NULL,
    `recon_id`              BIGINT       NOT NULL,
    `entity_type`           VARCHAR(32)  NOT NULL,
    `entity_id`             BIGINT       NOT NULL,
    `expected_value`        VARCHAR(256)          DEFAULT NULL,
    `actual_value`          VARCHAR(256)          DEFAULT NULL,
    `category`              VARCHAR(32)           DEFAULT NULL,
    `blocking_scope`        VARCHAR(16)           DEFAULT NULL,
    `repair_event_id`       BIGINT                DEFAULT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`diff_id`),
    KEY `idx_recon` (`recon_id`)
) ENGINE=InnoDB COMMENT='对账差异表';

-- ============================================================
-- 12.7 券与推荐
-- ============================================================

CREATE TABLE IF NOT EXISTS `coupon_template_version` (
    `template_version_id`   BIGINT       NOT NULL,
    `template_id`           BIGINT       NOT NULL,
    `template_version`      INT          NOT NULL,
    `name`                  VARCHAR(128) NOT NULL,
    `face_cent`             BIGINT       NOT NULL                COMMENT '面额(分)',
    `min_spend_cent`        BIGINT       NOT NULL DEFAULT 0       COMMENT '门槛(分)',
    `valid_days`            INT          NOT NULL                COMMENT '领取后有效天数',
    `scope_type`            VARCHAR(16)  NOT NULL                COMMENT 'ALL/CATEGORY/PRODUCT',
    `scope_definition_json` JSON                  DEFAULT NULL,
    `source_type`           VARCHAR(16)  NOT NULL                COMMENT 'REFERRAL/ACTIVITY/MANUAL',
    `reward_tier`           INT                   DEFAULT NULL    COMMENT '推荐档位1-4',
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    `effective_at`          DATETIME(3)  NOT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`template_version_id`),
    UNIQUE KEY `uk_template_version` (`template_id`, `template_version`)
) ENGINE=InnoDB COMMENT='券模板版本表';

CREATE TABLE IF NOT EXISTS `user_coupon` (
    `coupon_id`             BIGINT       NOT NULL                COMMENT '用户券ID',
    `user_id`               BIGINT       NOT NULL,
    `template_id`           BIGINT       NOT NULL,
    `template_version`      INT          NOT NULL,
    `face_cent_snapshot`    BIGINT       NOT NULL                COMMENT '面额快照(分)',
    `min_spend_cent_snapshot` BIGINT     NOT NULL                COMMENT '门槛快照(分)',
    `scope_snapshot_json`   JSON                  DEFAULT NULL,
    `received_at`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `valid_until`           DATETIME(3)  NOT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/RESERVED/REDEEMED/EXPIRED/REVOKED',
    `reserved_order_id`     BIGINT                DEFAULT NULL,
    `used_order_id`         BIGINT                DEFAULT NULL,
    `source_reward_id`      BIGINT                DEFAULT NULL    COMMENT '推荐奖励ID',
    `replacement_of_coupon_id` BIGINT            DEFAULT NULL    COMMENT '补发来源券',
    `version`               INT          NOT NULL DEFAULT 0,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`coupon_id`),
    UNIQUE KEY `uk_replacement` (`replacement_of_coupon_id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_valid_until` (`valid_until`)
) ENGINE=InnoDB COMMENT='用户券表';

CREATE TABLE IF NOT EXISTS `coupon_flow` (
    `flow_id`               BIGINT       NOT NULL,
    `coupon_id`             BIGINT       NOT NULL,
    `user_id`               BIGINT       NOT NULL,
    `event_type`            VARCHAR(32)  NOT NULL,
    `business_key`          VARCHAR(128) NOT NULL,
    `order_id`              BIGINT                DEFAULT NULL,
    `refund_id`             BIGINT                DEFAULT NULL,
    `before_status`         VARCHAR(16)           DEFAULT NULL,
    `after_status`          VARCHAR(16)  NOT NULL,
    `face_cent`             BIGINT       NOT NULL DEFAULT 0,
    `occurred_at`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`flow_id`),
    UNIQUE KEY `uk_business_key` (`business_key`),
    KEY `idx_coupon` (`coupon_id`)
) ENGINE=InnoDB COMMENT='券流水表';

CREATE TABLE IF NOT EXISTS `referral` (
    `referral_id`           BIGINT       NOT NULL,
    `referred_user_id`      BIGINT       NOT NULL                COMMENT '被推荐人',
    `referrer_user_id`      BIGINT       NOT NULL                COMMENT '推荐人',
    `bound_at`              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `first_order_id`        BIGINT                DEFAULT NULL,
    `trigger_status`        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/TRIGGERED/CANCELED',
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`referral_id`),
    UNIQUE KEY `uk_referred` (`referred_user_id`),
    KEY `idx_referrer` (`referrer_user_id`),
    CONSTRAINT `chk_no_self` CHECK (`referred_user_id` <> `referrer_user_id`)
) ENGINE=InnoDB COMMENT='推荐关系表';

CREATE TABLE IF NOT EXISTS `referral_counter` (
    `referrer_user_id`      BIGINT       NOT NULL                COMMENT '推荐人ID(主键)',
    `last_success_sequence` INT          NOT NULL DEFAULT 0       COMMENT '最后成功序号',
    `version`               INT          NOT NULL DEFAULT 0,
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`referrer_user_id`)
) ENGINE=InnoDB COMMENT='推荐计数器表';

CREATE TABLE IF NOT EXISTS `referral_reward` (
    `reward_id`             BIGINT       NOT NULL,
    `referral_id`           BIGINT       NOT NULL,
    `referrer_user_id`      BIGINT       NOT NULL,
    `success_sequence`      INT          NOT NULL                COMMENT '成功序号',
    `reward_tier`           INT          NOT NULL                COMMENT '档位1-4',
    `first_order_id`        BIGINT       NOT NULL,
    `coupon_id`             BIGINT                DEFAULT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'ISSUED',
    `source_refund_status`  VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    `issued_at`             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`reward_id`),
    UNIQUE KEY `uk_referrer_seq` (`referrer_user_id`, `success_sequence`),
    UNIQUE KEY `uk_referral` (`referral_id`)
) ENGINE=InnoDB COMMENT='推荐奖励表';

-- ============================================================
-- 12.8 供应链库存
-- ============================================================

CREATE TABLE IF NOT EXISTS `supplier` (
    `supplier_id`           BIGINT       NOT NULL,
    `legal_entity_info`     JSON         NOT NULL,
    `license_ref`           VARCHAR(512)          DEFAULT NULL,
    `contact_enc`           VARCHAR(512)          DEFAULT NULL,
    `bank_account_enc`      VARCHAR(512) NOT NULL                COMMENT '银行账户密文',
    `bank_account_version`  INT          NOT NULL DEFAULT 1,
    `payment_term_days`     INT          NOT NULL DEFAULT 45,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`supplier_id`)
) ENGINE=InnoDB COMMENT='供应商表';

CREATE TABLE IF NOT EXISTS `purchase_order` (
    `po_id`                 BIGINT       NOT NULL,
    `po_no`                 VARCHAR(32)  NOT NULL,
    `supplier_id`           BIGINT       NOT NULL,
    `buyer_entity_id`       BIGINT       NOT NULL,
    `currency`              VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    `total_cent`            BIGINT       NOT NULL DEFAULT 0,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    `approved_by`           BIGINT                DEFAULT NULL,
    `payment_due_date`      DATE                  DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`po_id`),
    UNIQUE KEY `uk_po_no` (`po_no`)
) ENGINE=InnoDB COMMENT='采购单表';

CREATE TABLE IF NOT EXISTS `purchase_order_item` (
    `po_item_id`            BIGINT       NOT NULL,
    `po_id`                 BIGINT       NOT NULL,
    `sku_id`                BIGINT       NOT NULL,
    `qty`                   INT          NOT NULL,
    `unit_price_cent`       BIGINT       NOT NULL,
    `line_cent`             BIGINT       NOT NULL,
    PRIMARY KEY (`po_item_id`),
    KEY `idx_po` (`po_id`)
) ENGINE=InnoDB COMMENT='采购单明细表';

CREATE TABLE IF NOT EXISTS `goods_receipt` (
    `receipt_id`            BIGINT       NOT NULL,
    `po_id`                 BIGINT       NOT NULL,
    `warehouse_id`          BIGINT       NOT NULL,
    `supplier_delivery_no`  VARCHAR(64)           DEFAULT NULL,
    `received_at`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'RECEIVED',
    PRIMARY KEY (`receipt_id`),
    KEY `idx_po` (`po_id`)
) ENGINE=InnoDB COMMENT='收货单表';

CREATE TABLE IF NOT EXISTS `goods_receipt_item` (
    `receipt_item_id`       BIGINT       NOT NULL,
    `receipt_id`            BIGINT       NOT NULL,
    `po_item_id`            BIGINT       NOT NULL,
    `received_qty`          INT          NOT NULL,
    `batch_no`              VARCHAR(64)           DEFAULT NULL,
    PRIMARY KEY (`receipt_item_id`),
    KEY `idx_receipt` (`receipt_id`)
) ENGINE=InnoDB COMMENT='收货单明细表';

CREATE TABLE IF NOT EXISTS `quality_inspection` (
    `inspection_id`         BIGINT       NOT NULL,
    `receipt_id`            BIGINT       NOT NULL,
    `inspector_id`          BIGINT                DEFAULT NULL,
    `result`                VARCHAR(16)  NOT NULL,
    `evidence_ref`          VARCHAR(512)          DEFAULT NULL,
    `inspected_at`          DATETIME(3)           DEFAULT NULL,
    PRIMARY KEY (`inspection_id`),
    UNIQUE KEY `uk_receipt` (`receipt_id`)
) ENGINE=InnoDB COMMENT='质检单表';

CREATE TABLE IF NOT EXISTS `quality_inspection_item` (
    `inspection_item_id`    BIGINT       NOT NULL,
    `inspection_id`         BIGINT       NOT NULL,
    `receipt_item_id`       BIGINT       NOT NULL,
    `accepted_qty`          INT          NOT NULL DEFAULT 0,
    `rejected_qty`          INT          NOT NULL DEFAULT 0,
    `reason`                VARCHAR(256)          DEFAULT NULL,
    PRIMARY KEY (`inspection_item_id`),
    KEY `idx_inspection` (`inspection_id`)
) ENGINE=InnoDB COMMENT='质检单明细表';

CREATE TABLE IF NOT EXISTS `stock_balance` (
    `balance_id`            BIGINT       NOT NULL,
    `warehouse_id`          BIGINT       NOT NULL,
    `sku_id`                BIGINT       NOT NULL,
    `batch_no`              VARCHAR(64)           DEFAULT NULL,
    `on_hand_qty`           INT          NOT NULL DEFAULT 0       COMMENT '实物库存',
    `reserved_qty`          INT          NOT NULL DEFAULT 0       COMMENT '销售预占',
    `blocked_qty`           INT          NOT NULL DEFAULT 0       COMMENT '质检冻结',
    `version`               INT          NOT NULL DEFAULT 0,
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`balance_id`),
    UNIQUE KEY `uk_warehouse_sku_batch` (`warehouse_id`, `sku_id`, `batch_no`),
    CONSTRAINT `chk_stock_non_negative` CHECK (`on_hand_qty` >= 0 AND `reserved_qty` >= 0 AND `blocked_qty` >= 0)
) ENGINE=InnoDB COMMENT='库存余额表';

CREATE TABLE IF NOT EXISTS `stock_movement` (
    `movement_id`           BIGINT       NOT NULL,
    `business_key`          VARCHAR(128) NOT NULL,
    `warehouse_id`          BIGINT       NOT NULL,
    `sku_id`                BIGINT       NOT NULL,
    `batch_no`              VARCHAR(64)           DEFAULT NULL,
    `movement_type`         VARCHAR(32)  NOT NULL,
    `quantity_delta`        INT          NOT NULL,
    `source_doc_id`         BIGINT                DEFAULT NULL,
    `occurred_at`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`movement_id`),
    UNIQUE KEY `uk_business_key` (`business_key`),
    KEY `idx_sku` (`sku_id`)
) ENGINE=InnoDB COMMENT='库存流水表';

CREATE TABLE IF NOT EXISTS `stock_reservation` (
    `reservation_id`        BIGINT       NOT NULL,
    `order_id`              BIGINT       NOT NULL,
    `sku_id`                BIGINT       NOT NULL,
    `warehouse_id`          BIGINT       NOT NULL,
    `batch_no`              VARCHAR(64)           DEFAULT NULL,
    `reserved_qty`          INT          NOT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    `expires_at`            DATETIME(3)  NOT NULL,
    `version`               INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (`reservation_id`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB COMMENT='库存预占表';

CREATE TABLE IF NOT EXISTS `shipment` (
    `shipment_id`           BIGINT       NOT NULL,
    `order_id`              BIGINT       NOT NULL,
    `carrier`               VARCHAR(64)           DEFAULT NULL,
    `tracking_no`           VARCHAR(64)           DEFAULT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `shipped_at`            DATETIME(3)           DEFAULT NULL,
    `delivered_at`          DATETIME(3)           DEFAULT NULL,
    PRIMARY KEY (`shipment_id`),
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB COMMENT='发货单表';

CREATE TABLE IF NOT EXISTS `shipment_item` (
    `shipment_item_id`      BIGINT       NOT NULL,
    `shipment_id`           BIGINT       NOT NULL,
    `order_item_id`         BIGINT       NOT NULL,
    `qty`                   INT          NOT NULL,
    `warehouse_id`          BIGINT       NOT NULL,
    `batch_no`              VARCHAR(64)           DEFAULT NULL,
    PRIMARY KEY (`shipment_item_id`),
    KEY `idx_shipment` (`shipment_id`)
) ENGINE=InnoDB COMMENT='发货单明细表';

CREATE TABLE IF NOT EXISTS `settlement` (
    `settlement_id`         BIGINT       NOT NULL,
    `settle_no`             VARCHAR(32)  NOT NULL,
    `supplier_id`           BIGINT       NOT NULL,
    `entity_id`             BIGINT       NOT NULL,
    `amount_cent`           BIGINT       NOT NULL DEFAULT 0,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `approved_by`           BIGINT                DEFAULT NULL,
    `paid_at`               DATETIME(3)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`settlement_id`),
    UNIQUE KEY `uk_settle_no` (`settle_no`)
) ENGINE=InnoDB COMMENT='结算单表';

CREATE TABLE IF NOT EXISTS `settlement_allocation` (
    `alloc_id`              BIGINT       NOT NULL,
    `settlement_id`         BIGINT       NOT NULL,
    `po_item_id`            BIGINT       NOT NULL,
    `receipt_item_id`       BIGINT       NOT NULL,
    `quantity`              INT          NOT NULL,
    `amount_cent`           BIGINT       NOT NULL,
    PRIMARY KEY (`alloc_id`),
    KEY `idx_settlement` (`settlement_id`)
) ENGINE=InnoDB COMMENT='结算分配表';

CREATE TABLE IF NOT EXISTS `supplier_payment` (
    `payment_id`            BIGINT       NOT NULL,
    `payment_no`            VARCHAR(32)  NOT NULL,
    `settlement_id`         BIGINT       NOT NULL,
    `amount_cent`           BIGINT       NOT NULL,
    `bank_trade_no`         VARCHAR(64)           DEFAULT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`payment_id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`)
) ENGINE=InnoDB COMMENT='供应商付款表';

-- ============================================================
-- 12.9 风控配置与可靠性
-- ============================================================

CREATE TABLE IF NOT EXISTS `risk_case` (
    `case_id`               BIGINT       NOT NULL,
    `user_id`               BIGINT                DEFAULT NULL,
    `rule_id`               VARCHAR(64)           DEFAULT NULL,
    `ai_flag`               TINYINT(1)   NOT NULL DEFAULT 0,
    `evidence_ref`          VARCHAR(512)          DEFAULT NULL,
    `proposed_action`       VARCHAR(256)          DEFAULT NULL,
    `review_status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `review_deadline`       DATETIME(3)           DEFAULT NULL,
    `reviewer_id`           BIGINT                DEFAULT NULL,
    `decision`              VARCHAR(256)          DEFAULT NULL,
    `decided_at`            DATETIME(3)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`case_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_review_status` (`review_status`)
) ENGINE=InnoDB COMMENT='风控案件表';

CREATE TABLE IF NOT EXISTS `appeal` (
    `appeal_id`             BIGINT       NOT NULL,
    `case_id`               BIGINT       NOT NULL,
    `user_id`               BIGINT       NOT NULL,
    `submitted_at`          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `reply_deadline`        DATETIME(3)           DEFAULT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'SUBMITTED',
    `reviewer_id`           BIGINT                DEFAULT NULL,
    `decision_ref`          VARCHAR(512)          DEFAULT NULL,
    PRIMARY KEY (`appeal_id`),
    KEY `idx_case` (`case_id`)
) ENGINE=InnoDB COMMENT='申诉表';

CREATE TABLE IF NOT EXISTS `config_change_request` (
    `change_id`             BIGINT       NOT NULL,
    `config_group`          VARCHAR(64)  NOT NULL,
    `before_version`        INT          NOT NULL DEFAULT 0,
    `proposed_json`         JSON         NOT NULL,
    `requester_id`          BIGINT       NOT NULL,
    `approver_id`           BIGINT                DEFAULT NULL,
    `reason`                VARCHAR(512)          DEFAULT NULL,
    `effective_date`        DATE         NOT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `approval_at`           DATETIME(3)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`change_id`),
    CONSTRAINT `chk_not_self_approve` CHECK (`requester_id` <> `approver_id` OR `approver_id` IS NULL)
) ENGINE=InnoDB COMMENT='配置变更申请表';

CREATE TABLE IF NOT EXISTS `config_version` (
    `config_id`             BIGINT       NOT NULL,
    `config_group`          VARCHAR(64)  NOT NULL,
    `version`               INT          NOT NULL,
    `payload_json`          JSON         NOT NULL,
    `schema_version`        VARCHAR(32)           DEFAULT NULL,
    `checksum`              VARCHAR(64)           DEFAULT NULL,
    `effective_at`          DATETIME(3)  NOT NULL,
    `change_id`             BIGINT                DEFAULT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`config_id`),
    UNIQUE KEY `uk_group_version` (`config_group`, `version`),
    KEY `idx_group_effective` (`config_group`, `effective_at`)
) ENGINE=InnoDB COMMENT='配置版本表';

CREATE TABLE IF NOT EXISTS `admin_audit_log` (
    `log_id`                BIGINT       NOT NULL,
    `actor_id`              BIGINT       NOT NULL,
    `action`                VARCHAR(64)  NOT NULL,
    `resource_type`         VARCHAR(32)  NOT NULL,
    `resource_id`           BIGINT                DEFAULT NULL,
    `request_id`            VARCHAR(64)           DEFAULT NULL,
    `result`                VARCHAR(16)  NOT NULL,
    `reason`                VARCHAR(512)          DEFAULT NULL,
    `before_hash`           VARCHAR(64)           DEFAULT NULL,
    `after_hash`            VARCHAR(64)           DEFAULT NULL,
    `occurred_at`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`log_id`),
    KEY `idx_actor` (`actor_id`),
    KEY `idx_resource` (`resource_type`, `resource_id`)
) ENGINE=InnoDB COMMENT='管理员审计日志表';

CREATE TABLE IF NOT EXISTS `outbox_event` (
    `event_id`              BIGINT       NOT NULL,
    `topic`                 VARCHAR(128) NOT NULL,
    `aggregate_id`          VARCHAR(64)           DEFAULT NULL,
    `payload_json`          JSON         NOT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `attempts`              INT          NOT NULL DEFAULT 0,
    `next_attempt_at`       DATETIME(3)           DEFAULT NULL,
    `published_at`          DATETIME(3)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`event_id`),
    KEY `idx_status_next` (`status`, `next_attempt_at`)
) ENGINE=InnoDB COMMENT='发件箱表';

CREATE TABLE IF NOT EXISTS `inbox_event` (
    `inbox_id`              BIGINT       NOT NULL,
    `consumer_name`         VARCHAR(64)  NOT NULL,
    `event_id`              VARCHAR(128) NOT NULL,
    `request_hash`          VARCHAR(64)           DEFAULT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'RECEIVED',
    `processed_at`          DATETIME(3)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`inbox_id`),
    UNIQUE KEY `uk_consumer_event` (`consumer_name`, `event_id`)
) ENGINE=InnoDB COMMENT='收件箱表';

CREATE TABLE IF NOT EXISTS `notification_delivery` (
    `delivery_id`           BIGINT       NOT NULL,
    `user_id`               BIGINT       NOT NULL,
    `template_code`         VARCHAR(64)  NOT NULL,
    `business_key`          VARCHAR(128) NOT NULL,
    `channel`               VARCHAR(16)  NOT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `attempts`              INT          NOT NULL DEFAULT 0,
    `sent_at`               DATETIME(3)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`delivery_id`),
    UNIQUE KEY `uk_user_template_biz` (`user_id`, `template_code`, `business_key`)
) ENGINE=InnoDB COMMENT='通知投递表';

CREATE TABLE IF NOT EXISTS `compliance_gate` (
    `gate_id`               BIGINT       NOT NULL,
    `gate_code`             VARCHAR(64)  NOT NULL,
    `scope`                 VARCHAR(64)           DEFAULT NULL,
    `evidence_ref`          VARCHAR(512)          DEFAULT NULL,
    `owner_id`              BIGINT                DEFAULT NULL,
    `status`                VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `approved_at`           DATETIME(3)           DEFAULT NULL,
    `expiry_at`             DATETIME(3)           DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`gate_id`),
    UNIQUE KEY `uk_gate_code` (`gate_code`)
) ENGINE=InnoDB COMMENT='合规门禁表';

SET FOREIGN_KEY_CHECKS = 1;
