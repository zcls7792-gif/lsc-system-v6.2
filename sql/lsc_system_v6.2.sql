-- ============================================================
-- 链盛通LSC系统 V6.2-AI 数据库建表脚本
-- MySQL 8.0.28+  分库分表8库32表(按user_id取模32)
-- ============================================================
-- 说明:
--   1. 本脚本创建 lsc_system 主库, 存放全部全局表(非分库分表).
--   2. 分库分表的物理库(lsc_db_0 ~ lsc_db_7)及其分片表结构
--      请在执行本脚本后, 顺序执行 lsc_sharding.sql 生成.
--   3. Docker 环境通过 MYSQL_DATABASE=lsc_system 自动创建本库,
--      此处的 CREATE DATABASE IF NOT EXISTS 为幂等保护, 不会重复创建.
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 创建 lsc_system 主库 (全局表所在库)
CREATE DATABASE IF NOT EXISTS `lsc_system`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `lsc_system`;

-- ============================================================
-- 1. 用户表 users
-- ============================================================
CREATE TABLE IF NOT EXISTS `users` (
    `user_id`      BIGINT(20)    NOT NULL                COMMENT '用户ID(雪花算法)',
    `user_type`    TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '0消费者会员 1商家会员',
    `mobile`       VARCHAR(20)   NOT NULL                COMMENT '手机号',
    `password_hash` VARCHAR(128) NOT NULL                COMMENT '密码哈希',
    `nickname`     VARCHAR(64)            DEFAULT NULL   COMMENT '昵称',
    `avatar_url`   VARCHAR(512)           DEFAULT NULL   COMMENT '头像URL',
    `is_verified`  TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '0未认证 1已认证',
    `real_name`    VARCHAR(64)            DEFAULT NULL   COMMENT '实名(加密存储)',
    `id_card_no`   VARCHAR(256)           DEFAULT NULL   COMMENT '身份证号(AES-256加密)',
    `referrer_id`  BIGINT(20)             DEFAULT NULL   COMMENT '推荐人ID(唯一外键约束确保仅一级)',
    `status`       TINYINT(1)    NOT NULL DEFAULT 1      COMMENT '0禁用 1正常',
    `created_at`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '注册时间',
    `updated_at`   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_mobile` (`mobile`),
    KEY `idx_referrer` (`referrer_id`),
    KEY `idx_user_type` (`user_type`),
    CONSTRAINT `fk_referrer` FOREIGN KEY (`referrer_id`) REFERENCES `users`(`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 2. 商家扩展表 merchant_extensions
-- ============================================================
CREATE TABLE IF NOT EXISTS `merchant_extensions` (
    `merchant_id`           BIGINT(20)     NOT NULL                  COMMENT '商家ID(雪花算法)',
    `business_license`      VARCHAR(128)   NOT NULL                  COMMENT '营业执照号',
    `business_license_img`  VARCHAR(512)             DEFAULT NULL    COMMENT '营业执照图片',
    `credit_score`          INT            NOT NULL DEFAULT 100      COMMENT '信用评分',
    `ai_risk_score`         INT                      DEFAULT NULL    COMMENT 'AI风险评分 0-100',
    `monthly_revenue`       DECIMAL(18,2)  NOT NULL DEFAULT 0.00     COMMENT '月营业额',
    `nh_limit_level`        TINYINT(2)     NOT NULL DEFAULT 0        COMMENT '核销限额档位1-16 0初始',
    `daily_nh_limit`        INT            NOT NULL DEFAULT 80       COMMENT '每日核销限额',
    `regulatory_account_no` VARCHAR(64)              DEFAULT NULL    COMMENT '监管账户号',
    `main_account_no`       VARCHAR(64)              DEFAULT NULL    COMMENT '主账户号',
    `last_nh_date`          DATE                     DEFAULT NULL    COMMENT '最近核销日期',
    `penalty_status`        TINYINT(1)     NOT NULL DEFAULT 0        COMMENT '0正常 1一级 2二级 3三级 4清退',
    `store_name`            VARCHAR(128)             DEFAULT NULL    COMMENT '门店名称',
    `province`              VARCHAR(32)              DEFAULT NULL    COMMENT '省份',
    `city`                  VARCHAR(32)              DEFAULT NULL    COMMENT '城市',
    `district`              VARCHAR(32)              DEFAULT NULL    COMMENT '区县',
    `address_detail`        VARCHAR(256)             DEFAULT NULL    COMMENT '详细地址',
    `ai_address_verified`   TINYINT(1)     NOT NULL DEFAULT 0        COMMENT '0未核验 1AI通过 2AI可疑 3人工确认',
    `longitude`             DECIMAL(10,7)            DEFAULT NULL    COMMENT '经度',
    `latitude`              DECIMAL(10,7)            DEFAULT NULL    COMMENT '纬度',
    `contact_phone`         VARCHAR(20)              DEFAULT NULL    COMMENT '联系电话',
    `business_hours`        VARCHAR(128)             DEFAULT NULL    COMMENT '营业时间',
    `address_update_count`  TINYINT        NOT NULL DEFAULT 0        COMMENT '当日地址修改次数(每日0时清零)',
    `is_signed_supervision` TINYINT(1)     NOT NULL DEFAULT 0        COMMENT '是否签署监管协议 0否 1是',
    `audit_status`          TINYINT(1)     NOT NULL DEFAULT 0        COMMENT '商家审核状态 0待审核 1通过 2拒绝',
    `created_at`            DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家扩展表';

-- ============================================================
-- 2.1 商家线下门店地址表 store_address
-- ============================================================
CREATE TABLE IF NOT EXISTS `store_address` (
    `id`             BIGINT(20)    NOT NULL                  COMMENT '主键(雪花算法)',
    `merchant_id`    BIGINT(20)    NOT NULL                  COMMENT '商家ID',
    `label`          VARCHAR(32)             DEFAULT NULL    COMMENT '地址标签(总店/分店)',
    `province`       VARCHAR(32)   NOT NULL                  COMMENT '省份',
    `city`           VARCHAR(32)   NOT NULL                  COMMENT '城市',
    `district`       VARCHAR(32)   NOT NULL                  COMMENT '区县',
    `address_detail` VARCHAR(256)  NOT NULL                  COMMENT '详细地址',
    `longitude`      DECIMAL(10,7)            DEFAULT NULL    COMMENT '经度',
    `latitude`       DECIMAL(10,7)            DEFAULT NULL    COMMENT '纬度',
    `contact_phone`  VARCHAR(20)              DEFAULT NULL    COMMENT '联系电话',
    `is_primary`     TINYINT(1)    NOT NULL DEFAULT 0        COMMENT '是否主地址 0否 1是',
    `created_at`     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_merchant` (`merchant_id`),
    KEY `idx_merchant_primary` (`merchant_id`, `is_primary`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家线下门店地址表';

-- ============================================================
-- 3. LSC账户表 lsc_accounts
-- ============================================================
CREATE TABLE IF NOT EXISTS `lsc_accounts` (
    `user_id`         BIGINT(20)  NOT NULL                  COMMENT '用户ID(雪花算法)',
    `total_locked`    BIGINT      NOT NULL DEFAULT 0        COMMENT '锁定LSC总量',
    `total_available` BIGINT      NOT NULL DEFAULT 0        COMMENT '可用LSC总量',
    `version`         INT         NOT NULL DEFAULT 1       COMMENT '乐观锁版本号',
    `updated_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSC账户表';

-- ============================================================
-- 4. LSC流水表 lsc_transactions
-- ============================================================
CREATE TABLE IF NOT EXISTS `lsc_transactions` (
    `id`                BIGINT(20)   NOT NULL              COMMENT '主键(雪花算法)',
    `user_id`           BIGINT(20)   NOT NULL              COMMENT '用户ID',
    `type`              TINYINT(2)   NOT NULL              COMMENT '流水类型 1消费发行 2每日释放 3推广奖励 4商城消费 5线下消费 6过期转回 7商家核销 8B2B流转 9退款退回',
    `amount`            BIGINT       NOT NULL DEFAULT 0   COMMENT '数量',
    `before_locked`     BIGINT       NOT NULL DEFAULT 0,
    `after_locked`      BIGINT       NOT NULL DEFAULT 0,
    `before_available`  BIGINT       NOT NULL DEFAULT 0,
    `after_available`   BIGINT       NOT NULL DEFAULT 0,
    `counterparty_id`   BIGINT(20)            DEFAULT NULL COMMENT '交易对手方用户ID',
    `order_no`          VARCHAR(64)  NOT NULL             COMMENT '关联订单号',
    `idempotent_key`    VARCHAR(128) NOT NULL             COMMENT '幂等校验唯一键',
    `remark`            VARCHAR(256)         DEFAULT NULL COMMENT '备注',
    `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_idempotent_key` (`idempotent_key`),
    KEY `idx_user_id_created` (`user_id`, `created_at`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LSC流水表';

-- ============================================================
-- 5. 可用LSC明细表 available_lsc_details
-- ============================================================
CREATE TABLE IF NOT EXISTS `available_lsc_details` (
    `id`                   BIGINT(20)   NOT NULL            COMMENT '主键(雪花算法)',
    `user_id`              BIGINT(20)   NOT NULL            COMMENT '用户ID',
    `amount`               BIGINT       NOT NULL DEFAULT 0 COMMENT '数量',
    `source_type`          VARCHAR(32)  NOT NULL            COMMENT '来源类型',
    `source_id`            BIGINT(20)            DEFAULT NULL COMMENT '溯源ID',
    `original_expire_date` DATE         NOT NULL            COMMENT '原始过期日期',
    `expire_date`          DATE         NOT NULL            COMMENT '过期日期',
    `status`               TINYINT(1)   NOT NULL DEFAULT 1  COMMENT '1有效 2过期转回 3已使用 4已核销 5退款退回',
    `created_at`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_user_expire` (`user_id`, `expire_date`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可用LSC明细表';

-- ============================================================
-- 6. 商家核销记录表 merchant_nh_records
-- ============================================================
CREATE TABLE IF NOT EXISTS `merchant_nh_records` (
    `id`               BIGINT(20)    NOT NULL              COMMENT '主键(雪花算法)',
    `merchant_id`      BIGINT(20)    NOT NULL              COMMENT '商家ID',
    `lsc_amount`        BIGINT        NOT NULL DEFAULT 0   COMMENT '核销LSC数量',
    `cash_amount`       DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '划拨现金金额',
    `available_before` BIGINT        NOT NULL DEFAULT 0    COMMENT '核销前可用余额',
    `available_after`  BIGINT        NOT NULL DEFAULT 0    COMMENT '核销后可用余额',
    `fund_before`       DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '核销前监管账户余额',
    `fund_after`       DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '核销后监管账户余额',
    `order_no`         VARCHAR(64)   NOT NULL              COMMENT '核销订单号',
    `idempotent_key`   VARCHAR(128)  NOT NULL              COMMENT '幂等键',
    `version`          INT           NOT NULL DEFAULT 1    COMMENT '乐观锁版本号',
    `status`           TINYINT(1)    NOT NULL DEFAULT 0    COMMENT '0待处理 1处理中 2成功 3失败',
    `fail_reason`      VARCHAR(256)           DEFAULT NULL COMMENT '失败原因',
    `created_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `completed_at`     DATETIME(3)            DEFAULT NULL,
    `updated_at`       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_idempotent_key` (`idempotent_key`),
    KEY `idx_merchant_status` (`merchant_id`, `status`),
    KEY `idx_merchant_date` (`merchant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家核销记录表';

-- ============================================================
-- 7. B2B交易订单表 b2b_orders
-- ============================================================
CREATE TABLE IF NOT EXISTS `b2b_orders` (
    `id`                        BIGINT(20)    NOT NULL          COMMENT '主键(雪花算法)',
    `order_no`                  VARCHAR(64)   NOT NULL          COMMENT 'B2B订单号',
    `initiator_id`              BIGINT(20)    NOT NULL          COMMENT '发起方ID',
    `counterparty_id`           BIGINT(20)    NOT NULL          COMMENT '接收方ID',
    `trade_description`         VARCHAR(512)  NOT NULL          COMMENT '交易描述',
    `total_amount_rmb`          DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '交易总金额(元)',
    `lsc_amount`                BIGINT        NOT NULL DEFAULT 0 COMMENT 'LSC流转数量(1:1)',
    `contract_no`               VARCHAR(128)           DEFAULT NULL COMMENT '合同编号',
    `trade_evidence_urls`       VARCHAR(1024)          DEFAULT NULL COMMENT '贸易凭证图片(JSON数组)',
    `ai_verification_result`   TINYINT(1)    NOT NULL DEFAULT 0  COMMENT '0未核验 1AI真实 2AI可疑 3人工真实 4人工虚假',
    `ai_verification_score`     DECIMAL(5,2)            DEFAULT NULL COMMENT 'AI核验评分0-100',
    `ai_risk_tags`              VARCHAR(512)           DEFAULT NULL COMMENT 'AI风险标签(JSON)',
    `counterparty_confirmed`   TINYINT(1)    NOT NULL DEFAULT 0  COMMENT '对手方是否确认',
    `confirmed_by`              VARCHAR(64)            DEFAULT NULL COMMENT '确认人',
    `confirmed_at`              DATETIME(3)            DEFAULT NULL COMMENT '确认时间',
    `lsc_transferred`           TINYINT(1)    NOT NULL DEFAULT 0  COMMENT 'LSC是否已流转',
    `expire_at`                 DATETIME(3)   NOT NULL             COMMENT '订单过期时间',
    `status`                    TINYINT(1)    NOT NULL DEFAULT 0  COMMENT '0待确认 1已确认 2已流转 3已完成 4已取消 5已作废',
    `idempotent_key`            VARCHAR(128)  NOT NULL             COMMENT '幂等键',
    `version`                   INT           NOT NULL DEFAULT 1   COMMENT '乐观锁版本号',
    `created_at`                DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `completed_at`              DATETIME(3)            DEFAULT NULL,
    `updated_at`                DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_idempotent_key` (`idempotent_key`),
    KEY `idx_initiator_status` (`initiator_id`, `status`),
    KEY `idx_counterparty_status` (`counterparty_id`, `status`),
    KEY `idx_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B2B交易订单表';

-- ============================================================
-- 8. 权益商城商品表 products
-- ============================================================
CREATE TABLE IF NOT EXISTS `products` (
    `id`                  BIGINT(20)    NOT NULL             COMMENT '主键(雪花算法)',
    `merchant_id`         BIGINT(20)    NOT NULL             COMMENT '商家ID',
    `product_name`        VARCHAR(256)  NOT NULL             COMMENT '商品名称',
    `product_desc`        TEXT                       DEFAULT NULL COMMENT '商品描述',
    `product_images`      VARCHAR(1024) NOT NULL             COMMENT '图片JSON数组',
    `price`               DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '商品价格(人民币=LSC价格 1:1)',
    `stock`               INT           NOT NULL DEFAULT 0   COMMENT '库存数量',
    `category_id`         INT           NOT NULL DEFAULT 0   COMMENT '商品类目ID',
    `video_url`           VARCHAR(512)           DEFAULT NULL COMMENT '视频URL',
    `video_cover_url`     VARCHAR(512)            DEFAULT NULL COMMENT '视频封面URL',
    `video_duration`      INT                     DEFAULT NULL COMMENT '视频时长(秒)',
    `video_status`        TINYINT(1)    NOT NULL DEFAULT 0   COMMENT '视频审核状态 0待审核 1通过 2拒绝',
    `ai_review_result`   TINYINT(1)    NOT NULL DEFAULT 0   COMMENT 'AI审核 0未审 1AI通过 2AI可疑 3人工通过 4人工拒绝',
    `ai_review_tags`      VARCHAR(512)            DEFAULT NULL COMMENT 'AI审核标签',
    `video_reject_reason` VARCHAR(256)            DEFAULT NULL COMMENT '视频拒绝原因',
    `sales_count`         INT           NOT NULL DEFAULT 0   COMMENT '销量',
    `status`              TINYINT(1)    NOT NULL DEFAULT 2   COMMENT '0下架 1上架 2审核中',
    `created_at`          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_merchant_status` (`merchant_id`, `status`),
    KEY `idx_category_status` (`category_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权益商城商品表';

-- ============================================================
-- 9. 订单表 orders (含线上商城和线下消费)
-- ============================================================
CREATE TABLE IF NOT EXISTS `orders` (
    `id`                 BIGINT(20)    NOT NULL             COMMENT '主键(雪花算法)',
    `order_no`           VARCHAR(64)   NOT NULL             COMMENT '订单号',
    `order_type`         TINYINT(1)    NOT NULL DEFAULT 0   COMMENT '0线上商城 1线下消费',
    `consumer_id`        BIGINT(20)    NOT NULL             COMMENT '消费者ID',
    `merchant_id`        BIGINT(20)    NOT NULL             COMMENT '商家ID',
    `product_id`         BIGINT(20)    NOT NULL             COMMENT '商品ID(线下为0)',
    `product_name`       VARCHAR(256)           DEFAULT NULL COMMENT '商品名称快照',
    `quantity`           INT           NOT NULL DEFAULT 1   COMMENT '购买数量',
    `total_price`        DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '订单总价',
    `lsc_amount`         BIGINT        NOT NULL DEFAULT 0   COMMENT 'LSC支付数量',
    `rmb_amount`         DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '人民币支付金额',
    `status`             TINYINT(1)    NOT NULL DEFAULT 0   COMMENT '0待支付 1已支付 2已完成 3已取消 4已退款 5部分退款',
    `refund_lsc_amount`  BIGINT        NOT NULL DEFAULT 0   COMMENT '已退LSC数量',
    `refund_rmb_amount`  DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '已退人民币金额',
    `pay_time`           DATETIME(3)            DEFAULT NULL COMMENT '支付时间',
    `created_at`         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `completed_at`       DATETIME(3)            DEFAULT NULL,
    `updated_at`         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_consumer_status_created` (`consumer_id`, `status`, `created_at`),
    KEY `idx_merchant_status_created` (`merchant_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ============================================================
-- 10. 每日释放汇总表 daily_release_summary
-- ============================================================
CREATE TABLE IF NOT EXISTS `daily_release_summary` (
    `id`                 BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `date`               DATE          NOT NULL               COMMENT '日期',
    `m_total`            DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '监管账户总余额',
    `n_total`            DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '全网核销总额',
    `k`                  DECIMAL(10,6) NOT NULL DEFAULT 0.000000 COMMENT '核销率k',
    `rate`               DECIMAL(10,6) NOT NULL DEFAULT 0.000000 COMMENT '释放速率',
    `l_locked`           BIGINT        NOT NULL DEFAULT 0    COMMENT '全网锁定LSC总量',
    `t_release`          BIGINT        NOT NULL DEFAULT 0    COMMENT '当日释放总量',
    `batch_count`        INT           NOT NULL DEFAULT 0    COMMENT '总批次数',
    `failed_batch_count` INT           NOT NULL DEFAULT 0    COMMENT '失败批次数',
    `ai_predicted_k_7d`  DECIMAL(10,6)          DEFAULT NULL COMMENT 'AI预测7天核销率均值',
    `ai_predicted_k_30d` DECIMAL(10,6)          DEFAULT NULL COMMENT 'AI预测30天核销率均值',
    `status`             TINYINT(1)    NOT NULL DEFAULT 0    COMMENT '0待执行 1执行中 2成功 3失败',
    `created_at`         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_date` (`date`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日释放汇总表';

-- ============================================================
-- 11. 释放比例配置表 release_config
-- ============================================================
CREATE TABLE IF NOT EXISTS `release_config` (
    `id`            INT           NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `config_key`    VARCHAR(64)   NOT NULL                 COMMENT '配置键',
    `config_value`  VARCHAR(64)   NOT NULL                 COMMENT '配置值',
    `editable`      TINYINT(1)    NOT NULL DEFAULT 1       COMMENT '0不可编辑(硬常量) 1可编辑',
    `description`   VARCHAR(255)           DEFAULT NULL    COMMENT '描述',
    `updated_by`    VARCHAR(64)            DEFAULT NULL    COMMENT '更新人',
    `updated_at`    DATETIME(3)             DEFAULT NULL    COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='释放比例配置表';

-- 预置数据
INSERT INTO `release_config` (`config_key`, `config_value`, `editable`, `description`) VALUES
('rate_max', '0.0005', 0, '释放速率上限0.05% 硬常量不可修改'),
('rate_min', '0.0003', 0, '释放速率下限0.03% 硬常量不可修改'),
('k_min', '0.005', 1, '调节起点0.50% 可配置需双人审批'),
('k_max', '0.01', 1, '调节终点1.0% 可配置需双人审批'),
('alpha', '0.05', 1, '调节因子alpha 可配置需双人审批');

-- ============================================================
-- 12. 商家违规记录表 merchant_violations
-- ============================================================
CREATE TABLE IF NOT EXISTS `merchant_violations` (
    `id`              BIGINT(20)   NOT NULL              COMMENT '主键(雪花算法)',
    `merchant_id`     BIGINT(20)   NOT NULL              COMMENT '商家ID',
    `violation_type`  VARCHAR(32)  NOT NULL               COMMENT '违规类型',
    `violation_desc`  VARCHAR(512) NOT NULL               COMMENT '违规描述',
    `credit_deduct`   INT          NOT NULL DEFAULT 0     COMMENT '扣分值',
    `penalty_action`  VARCHAR(64)  NOT NULL               COMMENT '处罚动作',
    `ai_detected`     TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '0人工发现 1AI自动发现',
    `penalty_start`   DATETIME(3)           DEFAULT NULL   COMMENT '处罚开始时间',
    `penalty_end`     DATETIME(3)           DEFAULT NULL   COMMENT '处罚结束时间',
    `operator`        VARCHAR(64)  NOT NULL               COMMENT '操作人',
    `created_at`      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_merchant_created` (`merchant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家违规记录表';

-- ============================================================
-- 13. 用户风控日志表 risk_logs
-- ============================================================
CREATE TABLE IF NOT EXISTS `risk_logs` (
    `id`             BIGINT(20)    NOT NULL              COMMENT '主键(雪花算法)',
    `user_id`        BIGINT(20)    NOT NULL              COMMENT '用户ID',
    `risk_type`      VARCHAR(32)   NOT NULL              COMMENT '风控类型',
    `risk_detail`    VARCHAR(512)  NOT NULL              COMMENT '风控详情',
    `ai_risk_level`  TINYINT(1)             DEFAULT NULL COMMENT 'AI风险等级 0低 1中 2高',
    `ai_risk_score`  DECIMAL(5,2)           DEFAULT NULL COMMENT 'AI风险评分',
    `action_taken`   VARCHAR(64)   NOT NULL              COMMENT '执行动作',
    `operator`       VARCHAR(64)            DEFAULT NULL  COMMENT '操作人',
    `created_at`     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_risk_type_created` (`risk_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户风控日志表';

-- ============================================================
-- 14. 操作存证记录表 blockchain_records
-- ============================================================
CREATE TABLE IF NOT EXISTS `blockchain_records` (
    `id`             BIGINT(20)   NOT NULL              COMMENT '主键(雪花算法)',
    `batch_no`       VARCHAR(64)  NOT NULL              COMMENT '批次号',
    `operation_type` VARCHAR(32) NOT NULL               COMMENT '操作类型',
    `business_id`    VARCHAR(64)  NOT NULL               COMMENT '业务ID',
    `data_hash`      VARCHAR(128) NOT NULL              COMMENT 'SHA-256哈希值',
    `tx_id`          VARCHAR(128)          DEFAULT NULL  COMMENT '区块链交易ID',
    `retry_count`    INT          NOT NULL DEFAULT 0    COMMENT '重试次数',
    `status`         TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '0待上链 1成功 2失败',
    `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_batch_no` (`batch_no`),
    KEY `idx_tx_id` (`tx_id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作存证记录表';

-- ============================================================
-- 15. 每日快照存证表 daily_snapshot_records
-- ============================================================
CREATE TABLE IF NOT EXISTS `daily_snapshot_records` (
    `id`              BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `snapshot_date`   DATE         NOT NULL               COMMENT '快照日期',
    `m_total`         DECIMAL(18,2) NOT NULL DEFAULT 0.00  COMMENT '监管总余额',
    `n_total`         DECIMAL(18,2) NOT NULL DEFAULT 0.00  COMMENT '核销总额',
    `l_locked`        BIGINT       NOT NULL DEFAULT 0     COMMENT '全网锁定总量',
    `l_available`     BIGINT       NOT NULL DEFAULT 0     COMMENT '全网可用总量',
    `rate`            DECIMAL(10,6) NOT NULL DEFAULT 0    COMMENT '释放比例',
    `t_release`       BIGINT       NOT NULL DEFAULT 0     COMMENT '释放总量',
    `nh_merkle_root`  VARCHAR(128)          DEFAULT NULL   COMMENT '核销明细Merkle树根',
    `data_hash`       VARCHAR(128) NOT NULL               COMMENT 'SHA-256哈希值',
    `tx_id`           VARCHAR(128)          DEFAULT NULL   COMMENT '区块链交易ID',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_snapshot_date` (`snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日快照存证表';

-- ============================================================
-- 16. 管理员操作审计表 admin_audit_logs
-- ============================================================
CREATE TABLE IF NOT EXISTS `admin_audit_logs` (
    `id`               BIGINT(20)   NOT NULL              COMMENT '主键(雪花算法)',
    `admin_id`         BIGINT(20)   NOT NULL              COMMENT '管理员ID',
    `admin_role`       VARCHAR(32)  NOT NULL              COMMENT '管理员角色',
    `operation`        VARCHAR(128) NOT NULL              COMMENT '操作内容',
    `operation_detail` TEXT                   DEFAULT NULL COMMENT '操作详情',
    `ip_address`       VARCHAR(45)  NOT NULL              COMMENT 'IP地址',
    `ai_anomaly_flag`  TINYINT(1)   NOT NULL DEFAULT 0   COMMENT '0正常 1AI判定异常',
    `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_admin_created` (`admin_id`, `created_at`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作审计表';

-- ============================================================
-- 17. 分布式事务异常日志表 tx_exception_log
-- ============================================================
CREATE TABLE IF NOT EXISTS `tx_exception_log` (
    `id`                 BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `global_tx_id`       VARCHAR(128) NOT NULL               COMMENT '全局事务ID',
    `branch_tx_id`       VARCHAR(128) NOT NULL               COMMENT '分支事务ID',
    `business_type`      VARCHAR(32)  NOT NULL               COMMENT '业务类型',
    `business_no`        VARCHAR(64)  NOT NULL               COMMENT '业务编号',
    `exception_content`  TEXT                  DEFAULT NULL   COMMENT '异常内容',
    `ai_diagnosis`       VARCHAR(512)          DEFAULT NULL   COMMENT 'AI故障诊断结果',
    `deal_status`        TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '0未处理 1已修复',
    `deal_operator`      VARCHAR(64)           DEFAULT NULL   COMMENT '处理人',
    `deal_remark`        VARCHAR(512)          DEFAULT NULL   COMMENT '处理备注',
    `created_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_glo_tx` (`global_tx_id`),
    KEY `idx_business_no` (`business_no`),
    KEY `idx_deal_status` (`deal_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分布式事务异常日志表';

-- ============================================================
-- 18. 管理员表 admins
-- ============================================================
CREATE TABLE IF NOT EXISTS `admins` (
    `id`           BIGINT(20)   NOT NULL              COMMENT '主键(雪花算法)',
    `username`     VARCHAR(64)  NOT NULL               COMMENT '用户名',
    `password_hash` VARCHAR(128) NOT NULL              COMMENT '密码哈希',
    `real_name`    VARCHAR(64)           DEFAULT NULL    COMMENT '真实姓名',
    `role`         VARCHAR(32)  NOT NULL               COMMENT '角色: super_admin/ops_admin/tech_admin/finance_admin',
    `status`       TINYINT(1)   NOT NULL DEFAULT 1     COMMENT '0禁用 1正常',
    `last_login_at` DATETIME(3)           DEFAULT NULL   COMMENT '最后登录时间',
    `last_login_ip` VARCHAR(45)           DEFAULT NULL   COMMENT '最后登录IP',
    `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- =================================================================
-- ⚠️  生产环境安全警告 (per I-09 / SECURITY_POSTINSTALL.md)
-- -----------------------------------------------------------------
-- 以下 INSERT 为**开发/演示用默认管理员账号**，密码「Admin@2026」
-- 的 BCrypt 哈希已硬编码，仅供本地联调与 CI 使用。
--
-- 部署到任何非 dev 环境前，必须执行：
--   1. 删除本 INSERT 语句，或手动把 username/password_hash
--      替换为通过环境变量注入的强密码哈希；
--   2. 首次登录后**立即**在管理后台重置所有 super_admin 密码；
--   3. 如需重新生成 BCrypt，参考 docs/SECURITY_POSTINSTALL.md
--      第 2 节「默认管理员密码轮换步骤」。
--
-- 若忽略以上步骤，可能导致后台被暴力破解或默认凭据入侵。
-- =================================================================
-- 预置超级管理员(开发/演示默认密码: Admin@2026 的BCrypt哈希)
INSERT INTO `admins` (`id`, `username`, `password_hash`, `real_name`, `role`, `status`) VALUES
(1000000000000000001, 'super_admin_01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7pIWlQepXkqNjQgRJgJ5q8e', '超级管理员A', 'super_admin', 1),
(1000000000000000002, 'super_admin_02', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7pIWlQepXkqNjQgRJgJ5q8e', '超级管理员B', 'super_admin', 1);

-- ============================================================
-- 19. 商品类目表 product_categories
-- ============================================================
CREATE TABLE IF NOT EXISTS `product_categories` (
    `id`          INT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `parent_id`  INT          NOT NULL DEFAULT 0       COMMENT '父类目ID',
    `name`        VARCHAR(64)  NOT NULL                 COMMENT '类目名称',
    `icon_url`   VARCHAR(512)          DEFAULT NULL     COMMENT '类目图标',
    `sort_order`  INT          NOT NULL DEFAULT 0       COMMENT '排序',
    `status`      TINYINT(1)   NOT NULL DEFAULT 1       COMMENT '0禁用 1正常',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_parent_status` (`parent_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品类目表';

-- ============================================================
-- 20. 推广奖励挂账表 promotion_pending
-- ============================================================
CREATE TABLE IF NOT EXISTS `promotion_pending` (
    `id`              BIGINT(20)   NOT NULL              COMMENT '主键(雪花算法)',
    `referrer_id`     BIGINT(20)   NOT NULL              COMMENT '推荐人ID',
    `consumer_id`     BIGINT(20)   NOT NULL              COMMENT '消费者ID',
    `order_no`        VARCHAR(64)  NOT NULL              COMMENT '首单订单号',
    `reward_amount`   BIGINT       NOT NULL DEFAULT 0    COMMENT '应发奖励数量',
    `pending_amount`  BIGINT       NOT NULL DEFAULT 0    COMMENT '挂账待补发数量',
    `status`          TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '0待补发 1已补发 2已回滚',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_referrer_status` (`referrer_id`, `status`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推广奖励挂账表';

-- ============================================================
-- 21. 存证故障表 evidence_failover
-- ============================================================
CREATE TABLE IF NOT EXISTS `evidence_failover` (
    `id`             BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `batch_no`       VARCHAR(64)  NOT NULL               COMMENT '批次号',
    `operation_type` VARCHAR(32)  NOT NULL               COMMENT '操作类型',
    `business_id`    VARCHAR(64)   NOT NULL               COMMENT '业务ID',
    `raw_data`       TEXT         NOT NULL               COMMENT '原始数据JSON',
    `data_hash`      VARCHAR(128) NOT NULL               COMMENT 'SHA-256哈希值',
    `retry_count`    INT          NOT NULL DEFAULT 0     COMMENT '重试次数',
    `status`         TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '0待补传 1已补传 2人工重提',
    `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_batch_no` (`batch_no`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存证故障表';

-- ============================================================
-- 22. 参数变更审批表 param_change_approval
-- ============================================================
CREATE TABLE IF NOT EXISTS `param_change_approval` (
    `id`              BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_key`      VARCHAR(64)  NOT NULL               COMMENT '配置键',
    `old_value`       VARCHAR(64)  NOT NULL               COMMENT '旧值',
    `new_value`       VARCHAR(64)  NOT NULL               COMMENT '新值',
    `apply_reason`    VARCHAR(512)          DEFAULT NULL    COMMENT '申请理由',
    `ai_simulation_report` TEXT            DEFAULT NULL    COMMENT 'AI仿真推演报告',
    `applicant_id`    BIGINT(20)   NOT NULL               COMMENT '申请人ID',
    `applicant_name`  VARCHAR(64)  NOT NULL               COMMENT '申请人',
    `approver1_id`    BIGINT(20)            DEFAULT NULL    COMMENT '审批人1 ID',
    `approver1_time`  DATETIME(3)           DEFAULT NULL    COMMENT '审批人1时间',
    `approver2_id`    BIGINT(20)            DEFAULT NULL    COMMENT '审批人2 ID',
    `approver2_time`  DATETIME(3)           DEFAULT NULL    COMMENT '审批人2时间',
    `status`          TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '0待审批 1一人已批 2双人已批 3已驳回 4已执行',
    `executed_at`     DATETIME(3)           DEFAULT NULL    COMMENT '执行时间',
    `tx_id`           VARCHAR(128)          DEFAULT NULL    COMMENT '上链交易ID',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='参数变更审批表';

-- ============================================================
-- 23. 对账报告表 reconcile_reports
-- ============================================================
CREATE TABLE IF NOT EXISTS `reconcile_reports` (
    `id`                    BIGINT(20)    NOT NULL              COMMENT '主键(雪花算法)',
    `reconcile_date`       DATE          NOT NULL               COMMENT '对账日期',
    `payment_total_amount`  DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '支付机构流水总额',
    `payment_count`         BIGINT        NOT NULL DEFAULT 0    COMMENT '支付机构流水笔数',
    `ledger_total_amount`   DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT 'LSC账本流水总额',
    `ledger_count`          BIGINT        NOT NULL DEFAULT 0    COMMENT 'LSC账本流水笔数',
    `diff_amount`           DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '差异金额',
    `diff_count`            BIGINT        NOT NULL DEFAULT 0    COMMENT '差异笔数',
    `status`                TINYINT(1)    NOT NULL DEFAULT 0    COMMENT '0进行中 1一致 2有差异 3失败',
    `diff_detail`           TEXT                   DEFAULT NULL  COMMENT '差异明细(JSON)',
    `result_hash`           VARCHAR(128)           DEFAULT NULL  COMMENT '结果哈希(上链存证)',
    `chain_tx_hash`         VARCHAR(128)           DEFAULT NULL  COMMENT '上链交易哈希',
    `created_at`            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reconcile_date` (`reconcile_date`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对账报告表';

SET FOREIGN_KEY_CHECKS = 1;
