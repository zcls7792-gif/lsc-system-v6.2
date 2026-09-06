-- 清理旧表（保证幂等初始化）
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

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mobile VARCHAR(20) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    user_type TINYINT NOT NULL COMMENT '0=消费者, 1=商家',
    real_name VARCHAR(50),
    id_card VARCHAR(20),
    referrer_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 商家扩展表
CREATE TABLE IF NOT EXISTS merchant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    store_name VARCHAR(100) NOT NULL,
    business_license_url VARCHAR(255),
    corporate_account_no VARCHAR(50),
    regulatory_agreement_signed TINYINT DEFAULT 0,
    audit_status TINYINT DEFAULT 0 COMMENT '0=审核中,1=通过,2=驳回',
    credit_score INT DEFAULT 100,
    level VARCHAR(2) DEFAULT 'A',
    monthly_revenue DECIMAL(12,2) DEFAULT 0,
    province VARCHAR(20), city VARCHAR(20), district VARCHAR(20),
    address_detail VARCHAR(200),
    longitude DECIMAL(10,6), latitude DECIMAL(10,6),
    contact_phone VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- LSC 账户表
CREATE TABLE IF NOT EXISTS lsc_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    total_locked BIGINT DEFAULT 0,
    total_available BIGINT DEFAULT 0,
    version INT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- LSC 流水表
CREATE TABLE IF NOT EXISTS lsc_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type TINYINT NOT NULL COMMENT '1消费发行,2每日释放,3推广奖励,4商城消费,5线下消费,6过期转回,7商家核销,8B2B流转,9退款退回',
    amount BIGINT NOT NULL,
    before_locked BIGINT, after_locked BIGINT,
    before_available BIGINT, after_available BIGINT,
    counterparty_id BIGINT,
    order_no VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_lsc_tx_user ON lsc_transaction(user_id, type);

-- 商品表
CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    product_desc TEXT,
    price DECIMAL(10,2) NOT NULL,
    lsc_price BIGINT NOT NULL,
    stock INT DEFAULT 0,
    sales INT DEFAULT 0,
    product_images TEXT,
    ai_review_result TINYINT DEFAULT 0 COMMENT '0=AI通过,1=AI可疑,2=人工通过,3=人工拒绝',
    ai_tags VARCHAR(200),
    status TINYINT DEFAULT 1 COMMENT '0=下架,1=上架,2=审核中',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    merchant_id BIGINT,
    product_id BIGINT,
    product_name VARCHAR(200),
    order_type TINYINT DEFAULT 0 COMMENT '0=商城,1=线下',
    total_price DECIMAL(10,2) NOT NULL,
    lsc_amount BIGINT DEFAULT 0,
    rmb_amount DECIMAL(10,2) DEFAULT 0,
    status TINYINT DEFAULT 0 COMMENT '0=待支付,1=已支付,2=已完成,3=已取消,4=已退款',
    address_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 核销记录表
CREATE TABLE IF NOT EXISTS nh_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    merchant_id BIGINT NOT NULL,
    lsc_amount BIGINT NOT NULL,
    cash_amount DECIMAL(12,2) NOT NULL,
    status TINYINT DEFAULT 2 COMMENT '0=处理中,1=失败,2=成功',
    nh_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
CREATE UNIQUE INDEX uk_nh_merchant_date ON nh_record(merchant_id, nh_date);

-- B2B 订单表
CREATE TABLE IF NOT EXISTS b2b_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    from_merchant_id BIGINT NOT NULL,
    to_merchant_id BIGINT NOT NULL,
    trade_description VARCHAR(500),
    total_amount_rmb DECIMAL(12,2),
    lsc_amount BIGINT NOT NULL,
    contract_no VARCHAR(50),
    trade_evidence_urls TEXT,
    ai_verification_result TINYINT DEFAULT 0 COMMENT '0=AI真实,1=AI可疑,2=人工真实,3=人工虚假',
    status TINYINT DEFAULT 0 COMMENT '0=待确认,1=已确认,2=已流转,3=已完成,4=已取消',
    confirmed_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 释放汇总表
CREATE TABLE IF NOT EXISTS release_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    release_date DATE NOT NULL UNIQUE,
    m_total DECIMAL(14,2),
    n_total DECIMAL(14,2),
    k_value DECIMAL(10,6),
    rate DECIMAL(10,6),
    l_locked BIGINT,
    t_release BIGINT,
    status TINYINT DEFAULT 1
);

-- 释放参数表（单行配置）
CREATE TABLE IF NOT EXISTS release_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rate_max DECIMAL(10,6) DEFAULT 0.0006,
    rate_min DECIMAL(10,6) DEFAULT 0.0003,
    k_min DECIMAL(10,6) DEFAULT 0.005,
    k_max DECIMAL(10,6) DEFAULT 0.010,
    alpha DECIMAL(10,4) DEFAULT 0.06,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 核销档位表
CREATE TABLE IF NOT EXISTS nh_level (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    level VARCHAR(2) NOT NULL UNIQUE,
    min_revenue DECIMAL(12,2) NOT NULL,
    daily_limit BIGINT NOT NULL
);
