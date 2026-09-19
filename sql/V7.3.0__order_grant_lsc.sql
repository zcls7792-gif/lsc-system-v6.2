-- =====================================================================
-- V7.3.0 订单表新增赠送 LSC 字段
-- 用于记录商品赠送积分快照与本订单实际赠送 LSC 数量
-- =====================================================================

ALTER TABLE `orders`
    ADD COLUMN `grant_points` BIGINT NOT NULL DEFAULT 0 COMMENT '单件商品赠送LSC积分快照(V7.3，下单时从商品快照)' AFTER `rmb_amount`,
    ADD COLUMN `granted_lsc`  BIGINT NOT NULL DEFAULT 0 COMMENT '本订单实际赠送LSC数量(V7.3，=grant_points×quantity×人民币支付比例，入锁定池)' AFTER `grant_points`;

-- 索引：便于按赠送量统计与对账
CREATE INDEX `idx_orders_granted_lsc` ON `orders` (`granted_lsc`);
