-- 清理旧表（保证幂等初始化）
DROP TABLE IF EXISTS tx_exception_log;
DROP TABLE IF EXISTS admin_audit_logs;
DROP TABLE IF EXISTS daily_snapshot_records;
DROP TABLE IF EXISTS blockchain_records;
DROP TABLE IF EXISTS risk_logs;
DROP TABLE IF EXISTS merchant_violations;
DROP TABLE IF EXISTS available_lsc_details;
DROP TABLE IF EXISTS nh_level;
DROP TABLE IF EXISTS release_config;
DROP TABLE IF EXISTS release_summary;
DROP TABLE IF EXISTS b2b_order;
DROP TABLE IF EXISTS nh_record;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS lsc_transaction;
DROP TABLE IF EXISTS lsc_account;
DROP TABLE IF EXISTS merchant;
DROP TABLE IF EXISTS sys_user;

-- 14.1 用户表（sys_user）
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mobile VARCHAR(20) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    user_type TINYINT NOT NULL DEFAULT 0 COMMENT '0=消费者会员, 1=商家会员',
    real_name VARCHAR(50),
    id_card VARCHAR(20),
    is_verified TINYINT NOT NULL DEFAULT 0 COMMENT '实名认证状态 0=未认证,1=已认证',
    referrer_id BIGINT,
    first_order_completed TINYINT NOT NULL DEFAULT 0 COMMENT '首单是否已完成 0=否,1=是',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 14.2 商家扩展表（merchant）
CREATE TABLE IF NOT EXISTS merchant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    store_name VARCHAR(128),
    business_license_url VARCHAR(128),
    corporate_account_no VARCHAR(64),
    regulatory_agreement_signed TINYINT NOT NULL DEFAULT 0 COMMENT '监管协议签署状态',
    audit_status TINYINT DEFAULT 0 COMMENT '0=审核中,1=通过,2=驳回',
    credit_score INT NOT NULL DEFAULT 100,
    ai_risk_score INT,
    level VARCHAR(2) DEFAULT 'A' COMMENT '核销限额档位A-Z',
    monthly_revenue DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    nh_limit_level VARCHAR(2) NOT NULL DEFAULT '0' COMMENT '核销限额档位A-Z，0为初始额度',
    daily_nh_limit INT NOT NULL DEFAULT 80,
    regulatory_account_no VARCHAR(64),
    last_nh_date DATE,
    main_account_no VARCHAR(64),
    penalty_status TINYINT NOT NULL DEFAULT 0 COMMENT '0=正常,1=一级处罚,2=二级处罚,3=三级处罚,4=四级处罚清退',
    province VARCHAR(32), city VARCHAR(32), district VARCHAR(32),
    address_detail VARCHAR(256),
    ai_address_verified TINYINT NOT NULL DEFAULT 0,
    longitude DECIMAL(10,7), latitude DECIMAL(10,7),
    contact_phone VARCHAR(20),
    business_hours VARCHAR(128),
    address_update_count TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 14.3 LSC账户表（lsc_account）
CREATE TABLE IF NOT EXISTS lsc_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    total_locked BIGINT NOT NULL DEFAULT 0,
    total_available BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 14.4 LSC流水表（lsc_transaction）
CREATE TABLE IF NOT EXISTS lsc_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type TINYINT NOT NULL COMMENT '1=消费发行,2=每日释放,3=推广奖励释放,4=权益商城消费,5=线下消费,6=过期转回,7=商家核销,8=B2B流转支付,9=退款发行回滚',
    amount BIGINT NOT NULL DEFAULT 0,
    before_locked BIGINT NOT NULL DEFAULT 0,
    after_locked BIGINT NOT NULL DEFAULT 0,
    before_available BIGINT NOT NULL DEFAULT 0,
    after_available BIGINT NOT NULL DEFAULT 0,
    counterparty_id BIGINT,
    order_no VARCHAR(64) NOT NULL,
    idempotent_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_lsc_tx_user_created ON lsc_transaction(user_id, created_at);
CREATE INDEX idx_lsc_tx_order_no ON lsc_transaction(order_no);
CREATE UNIQUE INDEX uk_lsc_tx_idempotent ON lsc_transaction(idempotent_key);

-- 14.5 可用LSC明细表（available_lsc_details）
CREATE TABLE IF NOT EXISTS available_lsc_details (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount BIGINT NOT NULL DEFAULT 0,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT,
    original_expire_date DATE NOT NULL,
    expire_date DATE NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=可用,0=已过期转回',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ald_user_expire ON available_lsc_details(user_id, expire_date);
CREATE INDEX idx_ald_user_status ON available_lsc_details(user_id, status);

-- 14.8 权益商城商品表（product）
CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    product_name VARCHAR(256) NOT NULL,
    product_desc TEXT,
    product_images VARCHAR(1024) NOT NULL,
    price DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    lsc_price BIGINT NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    sales INT DEFAULT 0,
    category_id INT NOT NULL DEFAULT 0,
    video_url VARCHAR(512),
    video_cover_url VARCHAR(512),
    video_duration INT,
    video_status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待审核,1=审核通过,2=审核拒绝',
    ai_review_result TINYINT NOT NULL DEFAULT 0 COMMENT '0=AI通过,1=AI可疑,2=人工通过,3=人工拒绝',
    ai_review_tags VARCHAR(512),
    video_reject_reason VARCHAR(256),
    status TINYINT NOT NULL DEFAULT 2 COMMENT '0=下架,1=上架,2=审核中',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_product_merchant_status ON product(merchant_id, status);
CREATE INDEX idx_product_category_status ON product(category_id, status);

-- 14.9 订单表（orders）
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    order_type TINYINT NOT NULL DEFAULT 0 COMMENT '0=线上商城,1=线下消费',
    payment_type TINYINT NOT NULL DEFAULT 0 COMMENT '0=纯人民币支付,1=LSC全额抵扣,2=混合支付',
    is_first_order TINYINT NOT NULL DEFAULT 0 COMMENT '0=非首单,1=首单',
    user_id BIGINT NOT NULL,
    merchant_id BIGINT,
    product_id BIGINT,
    product_name VARCHAR(200),
    total_price DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    lsc_amount BIGINT NOT NULL DEFAULT 0,
    rmb_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待支付,1=已支付,2=已完成,3=已取消,4=已退款,5=部分退款',
    refund_lsc_amount BIGINT NOT NULL DEFAULT 0,
    refund_rmb_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    address_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
CREATE INDEX idx_orders_consumer_status ON orders(user_id, status, created_at);
CREATE INDEX idx_orders_merchant_status ON orders(merchant_id, status, created_at);

-- 14.6 商家核销记录表（nh_record）
CREATE TABLE IF NOT EXISTS nh_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    merchant_id BIGINT NOT NULL,
    lsc_amount BIGINT NOT NULL DEFAULT 0,
    cash_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    available_before BIGINT NOT NULL DEFAULT 0,
    available_after BIGINT NOT NULL DEFAULT 0,
    fund_before DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    fund_after DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    idempotent_key VARCHAR(128) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待处理,1=处理中,2=成功,3=失败',
    nh_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
CREATE UNIQUE INDEX uk_nh_merchant_date ON nh_record(merchant_id, nh_date);
CREATE UNIQUE INDEX uk_nh_idempotent ON nh_record(idempotent_key);
CREATE INDEX idx_nh_merchant_status ON nh_record(merchant_id, status);

-- 14.7 B2B交易订单表（b2b_order）
CREATE TABLE IF NOT EXISTS b2b_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    from_merchant_id BIGINT NOT NULL,
    to_merchant_id BIGINT NOT NULL,
    trade_description VARCHAR(512) NOT NULL,
    total_amount_rmb DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    lsc_amount BIGINT NOT NULL DEFAULT 0,
    contract_no VARCHAR(128),
    trade_evidence_urls VARCHAR(1024),
    ai_verification_result TINYINT NOT NULL DEFAULT 0 COMMENT '0=AI判定真实,1=AI判定可疑,2=人工确认真实,3=人工确认虚假',
    ai_verification_score DECIMAL(5,2),
    counterparty_confirmed TINYINT NOT NULL DEFAULT 0,
    confirmed_by VARCHAR(64),
    confirmed_at TIMESTAMP,
    lsc_transferred TINYINT NOT NULL DEFAULT 0,
    expire_at TIMESTAMP NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待确认,1=已确认,2=已流转,3=已完成,4=已取消,5=已作废',
    idempotent_key VARCHAR(128) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
CREATE UNIQUE INDEX uk_b2b_idempotent ON b2b_order(idempotent_key);
CREATE INDEX idx_b2b_initiator_status ON b2b_order(from_merchant_id, status);
CREATE INDEX idx_b2b_counterparty_status ON b2b_order(to_merchant_id, status);

-- 14.10 每日释放汇总表（release_summary）
CREATE TABLE IF NOT EXISTS release_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    release_date DATE NOT NULL UNIQUE,
    m_total DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    n_total DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    k_value DECIMAL(10,6) NOT NULL DEFAULT 0.000000,
    rate DECIMAL(10,6) NOT NULL DEFAULT 0.000000 COMMENT '取值必须在0.03%~0.06%之间',
    l_locked BIGINT NOT NULL DEFAULT 0,
    t_release BIGINT NOT NULL DEFAULT 0,
    batch_count INT NOT NULL DEFAULT 0,
    failed_batch_count INT NOT NULL DEFAULT 0,
    ai_predicted_k_7d DECIMAL(10,6),
    ai_predicted_k_30d DECIMAL(10,6),
    status TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 14.11 释放比例配置表（release_config）—— 采用 config_key/config_value 模式
CREATE TABLE IF NOT EXISTS release_config (
    id INT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(64) NOT NULL UNIQUE,
    config_value VARCHAR(64) NOT NULL,
    editable TINYINT NOT NULL DEFAULT 1,
    description VARCHAR(255),
    updated_by VARCHAR(64),
    updated_at TIMESTAMP
);

-- 核销档位表（26档 A-Z）
CREATE TABLE IF NOT EXISTS nh_level (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    level VARCHAR(2) NOT NULL UNIQUE,
    min_revenue DECIMAL(18,2) NOT NULL,
    daily_limit BIGINT NOT NULL
);

-- 14.12 商家违规记录表（merchant_violations）
CREATE TABLE IF NOT EXISTS merchant_violations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    violation_type VARCHAR(32) NOT NULL,
    violation_desc VARCHAR(512) NOT NULL,
    credit_deduct INT NOT NULL DEFAULT 0,
    penalty_action VARCHAR(64) NOT NULL,
    ai_detected TINYINT NOT NULL DEFAULT 0,
    penalty_start TIMESTAMP,
    penalty_end TIMESTAMP,
    operator VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_mv_merchant_created ON merchant_violations(merchant_id, created_at);

-- 14.13 用户风控日志表（risk_logs）
CREATE TABLE IF NOT EXISTS risk_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    risk_type VARCHAR(32) NOT NULL,
    risk_detail VARCHAR(512) NOT NULL,
    ai_risk_level TINYINT,
    ai_risk_score DECIMAL(5,2),
    action_taken VARCHAR(64) NOT NULL,
    operator VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_risk_user_created ON risk_logs(user_id, created_at);
CREATE INDEX idx_risk_type_created ON risk_logs(risk_type, created_at);

-- 14.14 操作存证记录表（blockchain_records）
CREATE TABLE IF NOT EXISTS blockchain_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(64) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    business_id VARCHAR(64) NOT NULL,
    data_hash VARCHAR(128) NOT NULL,
    tx_id VARCHAR(128),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_bc_batch_no ON blockchain_records(batch_no);
CREATE INDEX idx_bc_tx_id ON blockchain_records(tx_id);
CREATE INDEX idx_bc_created_at ON blockchain_records(created_at);

-- 14.15 每日快照存证表（daily_snapshot_records）
CREATE TABLE IF NOT EXISTS daily_snapshot_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_date DATE NOT NULL UNIQUE,
    data_hash VARCHAR(128) NOT NULL,
    tx_id VARCHAR(128),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 14.16 管理员操作审计表（admin_audit_logs）
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    admin_role VARCHAR(32) NOT NULL,
    operation VARCHAR(128) NOT NULL,
    operation_detail TEXT,
    ip_address VARCHAR(45) NOT NULL,
    ai_anomaly_flag TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_admin_created ON admin_audit_logs(admin_id, created_at);
CREATE INDEX idx_audit_created_at ON admin_audit_logs(created_at);

-- 14.17 分布式事务异常日志表（tx_exception_log）
CREATE TABLE IF NOT EXISTS tx_exception_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    global_tx_id VARCHAR(128) NOT NULL,
    branch_tx_id VARCHAR(128) NOT NULL,
    business_type VARCHAR(32) NOT NULL,
    business_no VARCHAR(64) NOT NULL,
    exception_content TEXT,
    ai_diagnosis VARCHAR(512),
    deal_status TINYINT NOT NULL DEFAULT 0,
    deal_operator VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tx_glo_tx ON tx_exception_log(global_tx_id);
CREATE INDEX idx_tx_business_no ON tx_exception_log(business_no);
