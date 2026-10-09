-- ============================================================
-- V10__lsc_product_seed.sql
-- 商品种子数据：法律主体 + 商品 + SKU + 价格版本 + 优惠券模板
-- ============================================================

-- 1. 卖家法律主体（seller_entity_id 引用）
INSERT INTO legal_entity (entity_id, legal_name, registration_no, role, pay_merchant_id, status, verified_at)
VALUES
  (100001, '杭州链盛食品有限公司', '91330110MA01SEED01', 'SELLER', 'PAY_MERCHANT_001', 'VERIFIED', NOW()),
  (100002, '深圳云上茶业有限公司', '91440300MA01SEED02', 'SELLER', 'PAY_MERCHANT_002', 'VERIFIED', NOW()),
  (100003, '云南原生态农业合作社', '91530100MA01SEED03', 'SELLER', 'PAY_MERCHANT_003', 'VERIFIED', NOW());

-- 2. 商品（status=ON_SALE 表示在售）
INSERT INTO product (product_id, seller_entity_id, name, category_id, status, audit_version, description_ref, return_policy_version)
VALUES
  (200001, 100001, '龙井明前茶 50g 罐装',       301, 'ON_SALE', 1, 'desc/200001.html', 'v1'),
  (200002, 100001, '西湖龙井礼盒 250g',           301, 'ON_SALE', 1, 'desc/200002.html', 'v1'),
  (200003, 100002, '凤凰单丛乌龙茶 100g',         302, 'ON_SALE', 1, 'desc/200003.html', 'v1'),
  (200004, 100002, '武夷大红袍 80g 精选',         302, 'ON_SALE', 1, 'desc/200004.html', 'v1'),
  (200005, 100003, '普洱熟茶饼 357g 古树料',      303, 'ON_SALE', 1, 'desc/200005.html', 'v1'),
  (200006, 100003, '滇红金针红茶 100g',           303, 'ON_SALE', 1, 'desc/200006.html', 'v1'),
  (200007, 100001, '茉莉花茶 50g 袋装',           304, 'ON_SALE', 1, 'desc/200007.html', 'v1'),
  (200008, 100002, '安溪铁观音 100g 清香型',     304, 'ON_SALE', 1, 'desc/200008.html', 'v1');

-- 3. SKU（每个商品 1~2 个 SKU）
INSERT INTO product_sku (sku_id, product_id, sku_code, spec_json, sale_unit, pack_qty, b_min_qty, status)
VALUES
  -- 龙井明前茶 50g：1 个 SKU
  (300001, 200001, 'LJ-MQ-50G',  '{"规格":"50g罐装","等级":"明前一级"}', 'PIECE', 1, 1, 'ACTIVE'),
  -- 西湖龙井礼盒 250g：2 个 SKU（精装/简装）
  (300002, 200002, 'LJ-LH-250G-P', '{"规格":"250g礼盒","包装":"精装","等级":"明前特级"}', 'BOX', 1, 1, 'ACTIVE'),
  (300003, 200002, 'LJ-LH-250G-S', '{"规格":"250g礼盒","包装":"简装","等级":"明前一级"}', 'BOX', 1, 5, 'ACTIVE'),
  -- 凤凰单丛乌龙茶 100g
  (300004, 200003, 'FC-DC-100G', '{"规格":"100g袋装","香型":"蜜兰香"}', 'PIECE', 1, 1, 'ACTIVE'),
  -- 武夷大红袍 80g
  (300005, 200004, 'DHP-80G', '{"规格":"80g罐装","等级":"特级","焙火":"中轻火"}', 'PIECE', 1, 1, 'ACTIVE'),
  -- 普洱熟茶饼 357g：2 个 SKU（不同批次年份）
  (300006, 200005, 'PE-357-2020', '{"规格":"357g饼","年份":"2020年","用料":"古树春料"}', 'CAKE', 1, 1, 'ACTIVE'),
  (300007, 200005, 'PE-357-2018', '{"规格":"357g饼","年份":"2018年","用料":"古树春料"}', 'CAKE', 1, 1, 'ACTIVE'),
  -- 滇红金针红茶 100g
  (300008, 200006, 'DH-JZ-100G', '{"规格":"100g袋装","等级":"金芽特级"}', 'PIECE', 1, 1, 'ACTIVE'),
  -- 茉莉花茶 50g
  (300009, 200007, 'ML-HC-50G', '{"规格":"50g袋装","窨次":"五窨"}', 'PIECE', 1, 1, 'ACTIVE'),
  -- 安溪铁观音 100g
  (300010, 200008, 'TG-100G-QX', '{"规格":"100g袋装","香型":"清香型","等级":"一级"}', 'PIECE', 1, 1, 'ACTIVE');

-- 4. 价格版本（每个 SKU 一条当前生效价格）
--    cost_price_enc: 简单加密（开发期用明文字节占位，生产环境用 AES-GCM）
--    grant_coefficient_ppm: 800000 = 80% 返馈权益比例
INSERT INTO product_price_version (price_version, sku_id, retail_price_cent, b_price_cent, cost_price_enc, cost_key_version, cost_basis_code, grant_coefficient_ppm, grant_c_unit, grant_b_unit, effective_at, approved_by)
VALUES
  -- 龙井明前茶 50g：零售 ¥38.00, B 端 ¥28.00, 成本 ¥18.00, 返比 80%
  (1, 300001, 3800, 2800, UNHEX('00'), 'v1', 'TAX_INCLUDED', 800000, 0, 0, '2025-01-01 00:00:00', 100001),
  -- 西湖龙井礼盒 250g 精装：零售 ¥188.00, B 端 ¥148.00
  (2, 300002, 18800, 14800, UNHEX('00'), 'v1', 'TAX_INCLUDED', 800000, 0, 0, '2025-01-01 00:00:00', 100001),
  -- 西湖龙井礼盒 250g 简装：零售 ¥128.00, B 端 ¥98.00
  (3, 300003, 12800, 9800, UNHEX('00'), 'v1', 'TAX_INCLUDED', 750000, 0, 0, '2025-01-01 00:00:00', 100001),
  -- 凤凰单丛 100g：零售 ¥48.00, B 端 ¥35.00
  (4, 300004, 4800, 3500, UNHEX('00'), 'v1', 'TAX_INCLUDED', 800000, 0, 0, '2025-01-01 00:00:00', 100002),
  -- 大红袍 80g：零售 ¥88.00, B 端 ¥65.00
  (5, 300005, 8800, 6500, UNHEX('00'), 'v1', 'TAX_INCLUDED', 800000, 0, 0, '2025-01-01 00:00:00', 100002),
  -- 普洱熟茶饼 357g 2020年：零售 ¥168.00, B 端 ¥128.00
  (6, 300006, 16800, 12800, UNHEX('00'), 'v1', 'TAX_INCLUDED', 850000, 0, 0, '2025-01-01 00:00:00', 100003),
  -- 普洱熟茶饼 357g 2018年：零售 ¥298.00, B 端 ¥228.00
  (7, 300007, 29800, 22800, UNHEX('00'), 'v1', 'TAX_INCLUDED', 850000, 0, 0, '2025-01-01 00:00:00', 100003),
  -- 滇红金针 100g：零售 ¥36.00, B 端 ¥25.00
  (8, 300008, 3600, 2500, UNHEX('00'), 'v1', 'TAX_INCLUDED', 800000, 0, 0, '2025-01-01 00:00:00', 100003),
  -- 茉莉花茶 50g：零售 ¥22.00, B 端 ¥15.00
  (9, 300009, 2200, 1500, UNHEX('00'), 'v1', 'TAX_INCLUDED', 750000, 0, 0, '2025-01-01 00:00:00', 100001),
  -- 铁观音 100g：零售 ¥58.00, B 端 ¥42.00
  (10, 300010, 5800, 4200, UNHEX('00'), 'v1', 'TAX_INCLUDED', 800000, 0, 0, '2025-01-01 00:00:00', 100002);

-- 5. 优惠券模板
INSERT INTO coupon_template_version (template_id, template_version, name, face_cent, min_spend_cent, valid_days, scope_type, scope_definition_json, source_type, reward_tier, status, effective_at)
VALUES
  -- 满100减10 券
  (1, 1, '满100减10', 1000, 10000, 30, 'ALL', NULL, 'OPERATION', 1, 'ACTIVE', '2025-01-01 00:00:00'),
  -- 满200减30 券
  (2, 1, '满200减30', 3000, 20000, 30, 'ALL', NULL, 'OPERATION', 2, 'ACTIVE', '2025-01-01 00:00:00'),
  -- 新人无门槛5元券
  (3, 1, '新人专享5元券', 500, 0, 15, 'ALL', NULL, 'MARKETING', 1, 'ACTIVE', '2025-01-01 00:00:00');
