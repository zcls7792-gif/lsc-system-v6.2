-- 释放参数初始值（config_key/config_value 模式，方案文档14.11）
-- rate_max / rate_min 为硬常量，editable=0，不可修改
INSERT INTO release_config (config_key, config_value, editable, description) VALUES
('rate_max', '0.06%', 0, '释放速率上限-硬常量'),
('rate_min', '0.03%', 0, '释放速率下限-硬常量'),
('k_min', '0.50%', 1, '释放调节起点'),
('k_max', '1.0%', 1, '释放调节终点'),
('alpha', '0.06', 1, '线性调节因子');

-- 核销档位 A-Z（26档，按方案文档第十章）
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES
('A', 100000, 275), ('B', 200000, 550), ('C', 400000, 1100), ('D', 600000, 1650),
('E', 800000, 2200), ('F', 1000000, 2750), ('G', 1200000, 3300), ('H', 1400000, 3850),
('I', 1600000, 4400), ('J', 1800000, 4950), ('K', 2000000, 5500), ('L', 2500000, 6900),
('M', 3000000, 8250), ('N', 3500000, 9660), ('O', 4000000, 11000), ('P', 4500000, 12400),
('Q', 5000000, 13800), ('R', 6000000, 16500), ('S', 7000000, 19000), ('T', 8000000, 22000),
('U', 9000000, 24800), ('V', 10000000, 27600), ('W', 12000000, 33000), ('X', 15000000, 41000),
('Y', 17000000, 46900), ('Z', 20000000, 55000);

-- 测试用户（密码 123456 的 BCrypt 哈希）
INSERT INTO sys_user (mobile, password, user_type, real_name, is_verified) VALUES
('13800138000', '$2a$10$Dpgqqd3E8ZoP1wWu2SqUxeI2jT3Y/U9iGA6ti2jUrQLCZ9dedaLsO', 0, '测试消费者', 1),
('13900139000', '$2a$10$Dpgqqd3E8ZoP1wWu2SqUxeI2jT3Y/U9iGA6ti2jUrQLCZ9dedaLsO', 1, '测试商家', 1);

-- 测试商家
INSERT INTO merchant (user_id, store_name, business_license_url, corporate_account_no, regulatory_agreement_signed, audit_status, credit_score, level, monthly_revenue, province, city, district, address_detail, longitude, latitude)
VALUES (2, '优选生鲜', 'https://cdn.lsc.com/license1.jpg', '6222021234567890', 1, 1, 100, 'G', 1280000, '上海市', '上海市', '浦东新区', '世纪大道100号', 121.506377, 31.245105);

-- LSC 账户
INSERT INTO lsc_account (user_id, total_locked, total_available, version) VALUES (1, 1256800, 86420, 1);
INSERT INTO lsc_account (user_id, total_locked, total_available, version) VALUES (2, 580000, 128600, 1);

-- 测试商品
INSERT INTO product (merchant_id, product_name, product_desc, price, lsc_price, stock, sales, product_images, ai_review_result, status)
VALUES (2, '智利车厘子 JJ级 2斤装', '智利进口 JJ级 2斤装 顺丰冷链包邮', 98.00, 98, 580, 23000, 'https://cdn.lsc.com/p1.jpg', 0, 1);
INSERT INTO product (merchant_id, product_name, product_desc, price, lsc_price, stock, sales, product_images, ai_review_result, status)
VALUES (2, '有机蔬菜礼盒 8种时令蔬菜', '8种时令有机蔬菜 净重5kg', 128.00, 128, 320, 8600, 'https://cdn.lsc.com/p2.jpg', 0, 1);
INSERT INTO product (merchant_id, product_name, product_desc, price, lsc_price, stock, sales, product_images, ai_review_result, status)
VALUES (2, '阳澄湖大闸蟹 4两公 10只装', '阳澄湖直发 4两公蟹 10只', 388.00, 388, 150, 5200, 'https://cdn.lsc.com/p3.jpg', 0, 1);

-- 释放汇总示例（k=0.75%处于中间区间，rate=0.09%-0.06×0.75%=0.045%）
INSERT INTO release_summary (release_date, m_total, n_total, k_value, rate, l_locked, t_release, status)
VALUES (CURRENT_DATE, 1800000.00, 7560.00, 0.0075, 0.00045, 1836800000, 826560, 1);
