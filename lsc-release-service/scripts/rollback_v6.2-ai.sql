-- ============================================================================
-- 链盛通LSC平台 V6.2-AI对齐变更 - 数据库回滚脚本
-- ============================================================================
-- 用途：将数据库从 V6.2-AI最终版 回滚至 V6.2.0-gray-approval 基线版本
-- 执行顺序：先删除索引 -> 再删除字段 -> 最后删除表（反向依赖）
-- 注意事项：
--   1. 执行前务必备份数据库！
--   2. 建议在低峰期执行，执行期间暂停应用写入
--   3. 回滚不可逆，请确认后再执行
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 第一步：删除新增的索引
-- ============================================================================

-- lsc_transaction 表新增索引
DROP INDEX IF EXISTS idx_lsc_tx_user_created ON lsc_transaction;
DROP INDEX IF EXISTS idx_lsc_tx_order_no ON lsc_transaction;
DROP INDEX IF EXISTS uk_lsc_tx_idempotent ON lsc_transaction;

-- available_lsc_details 表（整表删除，无需单独删索引）

-- product 表新增索引
DROP INDEX IF EXISTS idx_product_merchant_status ON product;
DROP INDEX IF EXISTS idx_product_category_status ON product;

-- orders 表新增索引
DROP INDEX IF EXISTS idx_orders_consumer_status ON orders;
DROP INDEX IF EXISTS idx_orders_merchant_status ON orders;

-- nh_record 表新增索引
DROP INDEX IF EXISTS uk_nh_idempotent ON nh_record;
DROP INDEX IF EXISTS idx_nh_merchant_status ON nh_record;

-- b2b_order 表新增索引
DROP INDEX IF EXISTS uk_b2b_idempotent ON b2b_order;
DROP INDEX IF EXISTS idx_b2b_initiator_status ON b2b_order;
DROP INDEX IF EXISTS idx_b2b_counterparty_status ON b2b_order;

-- merchant_violations 表（整表删除）

-- risk_logs 表（整表删除）

-- blockchain_records 表（整表删除）

-- admin_audit_logs 表（整表删除）

-- tx_exception_log 表（整表删除）

-- ============================================================================
-- 第二步：删除现有表中新增的字段
-- ============================================================================

-- sys_user：删除新增字段
ALTER TABLE sys_user DROP COLUMN IF EXISTS is_verified;
ALTER TABLE sys_user DROP COLUMN IF EXISTS first_order_completed;

-- merchant：删除新增字段
ALTER TABLE merchant DROP COLUMN IF EXISTS ai_risk_score;
ALTER TABLE merchant DROP COLUMN IF EXISTS nh_limit_level;
ALTER TABLE merchant DROP COLUMN IF EXISTS daily_nh_limit;
ALTER TABLE merchant DROP COLUMN IF EXISTS regulatory_account_no;
ALTER TABLE merchant DROP COLUMN IF EXISTS last_nh_date;
ALTER TABLE merchant DROP COLUMN IF EXISTS main_account_no;
ALTER TABLE merchant DROP COLUMN IF EXISTS penalty_status;
ALTER TABLE merchant DROP COLUMN IF EXISTS ai_address_verified;
ALTER TABLE merchant DROP COLUMN IF EXISTS business_hours;
ALTER TABLE merchant DROP COLUMN IF EXISTS address_update_count;

-- orders：删除新增字段
ALTER TABLE orders DROP COLUMN IF EXISTS refund_lsc_amount;
ALTER TABLE orders DROP COLUMN IF EXISTS refund_rmb_amount;
ALTER TABLE orders DROP COLUMN IF EXISTS completed_at;

-- nh_record：删除新增字段
ALTER TABLE nh_record DROP COLUMN IF EXISTS available_before;
ALTER TABLE nh_record DROP COLUMN IF EXISTS available_after;
ALTER TABLE nh_record DROP COLUMN IF EXISTS fund_before;
ALTER TABLE nh_record DROP COLUMN IF EXISTS fund_after;
ALTER TABLE nh_record DROP COLUMN IF EXISTS idempotent_key;
ALTER TABLE nh_record DROP COLUMN IF EXISTS version;

-- b2b_order：删除新增字段
ALTER TABLE b2b_order DROP COLUMN IF EXISTS ai_verification_score;
ALTER TABLE b2b_order DROP COLUMN IF EXISTS counterparty_confirmed;
ALTER TABLE b2b_order DROP COLUMN IF EXISTS confirmed_at;
ALTER TABLE b2b_order DROP COLUMN IF EXISTS lsc_transferred;
ALTER TABLE b2b_order DROP COLUMN IF EXISTS expire_at;
ALTER TABLE b2b_order DROP COLUMN IF EXISTS idempotent_key;
ALTER TABLE b2b_order DROP COLUMN IF EXISTS version;
ALTER TABLE b2b_order DROP COLUMN IF EXISTS completed_at;

-- product：删除新增字段
ALTER TABLE product DROP COLUMN IF EXISTS category_id;
ALTER TABLE product DROP COLUMN IF EXISTS video_url;
ALTER TABLE product DROP COLUMN IF EXISTS video_cover_url;
ALTER TABLE product DROP COLUMN IF EXISTS video_duration;
ALTER TABLE product DROP COLUMN IF EXISTS video_status;
ALTER TABLE product DROP COLUMN IF EXISTS video_reject_reason;
ALTER TABLE product DROP COLUMN IF EXISTS updated_at;
-- ai_tags 重命名回滚：ai_review_tags -> ai_tags
ALTER TABLE product CHANGE COLUMN ai_review_tags ai_tags VARCHAR(512);

-- lsc_transaction：删除新增字段（幂等键）
ALTER TABLE lsc_transaction DROP COLUMN IF EXISTS idempotent_key;

-- ============================================================================
-- 第三步：还原 release_config 表结构（key-value -> 列模式）
-- ============================================================================

-- 删除现有的 key-value 模式表，重建为原始列模式
DROP TABLE IF EXISTS release_config;
CREATE TABLE release_config (
    id INT AUTO_INCREMENT PRIMARY KEY,
    rate_max DECIMAL(10,6) NOT NULL DEFAULT 0.000600 COMMENT '释放速率上限',
    rate_min DECIMAL(10,6) NOT NULL DEFAULT 0.000300 COMMENT '释放速率下限',
    k_min DECIMAL(10,6) NOT NULL DEFAULT 0.005000 COMMENT '释放调节起点',
    k_max DECIMAL(10,6) NOT NULL DEFAULT 0.010000 COMMENT '释放调节终点',
    alpha DECIMAL(10,6) NOT NULL DEFAULT 0.060000 COMMENT '线性调节因子',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 还原初始配置数据
INSERT INTO release_config (rate_max, rate_min, k_min, k_max, alpha) VALUES
(0.000600, 0.000300, 0.005000, 0.010000, 0.060000);

-- ============================================================================
-- 第四步：删除新增的7张表（按反向依赖顺序）
-- ============================================================================

DROP TABLE IF EXISTS tx_exception_log;
DROP TABLE IF EXISTS admin_audit_logs;
DROP TABLE IF EXISTS daily_snapshot_records;
DROP TABLE IF EXISTS blockchain_records;
DROP TABLE IF EXISTS risk_logs;
DROP TABLE IF EXISTS merchant_violations;
DROP TABLE IF EXISTS available_lsc_details;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 回滚完成验证
-- ============================================================================
-- 执行以下查询确认回滚结果：
-- 1. 确认新表已删除：
--    SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES 
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN 
--    ('available_lsc_details','merchant_violations','risk_logs',
--     'blockchain_records','daily_snapshot_records','admin_audit_logs','tx_exception_log');
--    预期：返回 0 行
--
-- 2. 确认字段已删除（以 orders 为例）：
--    SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS 
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' 
--    AND COLUMN_NAME IN ('refund_lsc_amount','refund_rmb_amount','completed_at');
--    预期：返回 0 行
-- ============================================================================
