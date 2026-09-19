-- ============================================================
-- LSC V7.3 商城服务 DDL 迁移
-- 版本：7.3.0-mall-grant-points
-- 适用：MySQL 8.x / MariaDB 10.6+
-- 说明：
--   1. products 新增 cost_price 字段(成本价，用于进销差计算赠送LSC)
--   2. products 新增 grant_points 字段(赠送LSC积分数量，上限为售价的100%)
-- ============================================================

-- -------- 1. products 新增成本价字段 --------
ALTER TABLE `products`
    ADD COLUMN `cost_price` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '成本价/进货价(元，V7.3用于计算进销差赠送LSC)' AFTER `price`;

-- -------- 2. products 新增赠送积分字段 --------
ALTER TABLE `products`
    ADD COLUMN `grant_points` BIGINT NOT NULL DEFAULT 0 COMMENT '赠送LSC积分数量(V7.3按进销差自动计算，上限为售价100%)' AFTER `cost_price`;
