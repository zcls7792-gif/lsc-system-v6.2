-- ============================================================
-- LSC V7.3 账本服务 DDL 迁移
-- 版本：7.3.0-ledger-frozen-balance
-- 适用：MySQL 8.x / MariaDB 10.6+
-- 说明：
--   1. lsc_accounts 新增 total_frozen 字段(风控冻结池)
--   2. lsc_transactions 新增 before_frozen / after_frozen 快照字段
--   3. 更新流水类型注释为 V7.3 定义
--   4. 更新可用明细状态注释为 V7.3 定义(移除"已核销")
-- ============================================================

-- -------- 1. lsc_accounts 新增冻结余额字段 --------
ALTER TABLE `lsc_accounts`
    ADD COLUMN `total_frozen` BIGINT NOT NULL DEFAULT 0 COMMENT '冻结LSC总量(风控冻结池,V7.3新增)' AFTER `total_available`;

-- -------- 2. lsc_transactions 新增冻结余额快照字段 --------
ALTER TABLE `lsc_transactions`
    ADD COLUMN `before_frozen` BIGINT NOT NULL DEFAULT 0 COMMENT '操作前冻结余额(V7.3新增)' AFTER `after_available`,
    ADD COLUMN `after_frozen`  BIGINT NOT NULL DEFAULT 0 COMMENT '操作后冻结余额(V7.3新增)' AFTER `before_frozen`;

-- -------- 3. 更新流水类型注释为 V7.3 定义 --------
ALTER TABLE `lsc_transactions`
    MODIFY COLUMN `type` TINYINT(2) NOT NULL
    COMMENT '流水类型(V7.3): 1消费赠送入锁定 2订单抵扣 3退款退回 4退款回扣 5到期作废 6推荐奖励入锁定 7每日释放 8风控冻结 9风控解冻';

-- -------- 4. 更新可用明细状态注释为 V7.3 定义 --------
ALTER TABLE `available_lsc_details`
    MODIFY COLUMN `status` TINYINT(1) NOT NULL DEFAULT 1
    COMMENT '状态(V7.3): 1有效 2已作废 3已使用 4退款退回';
