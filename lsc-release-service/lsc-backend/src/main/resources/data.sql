-- 释放参数初始值
INSERT INTO release_config (rate_max, rate_min, k_min, k_max, alpha) VALUES (0.0006, 0.0003, 0.005, 0.010, 0.06);

-- 核销档位 A-Z（示例：A, F, K, Z）
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('A', 100000, 275);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('B', 200000, 550);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('C', 300000, 825);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('D', 500000, 1375);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('E', 800000, 2200);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('F', 1000000, 2750);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('G', 1200000, 3300);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('H', 1500000, 4125);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('I', 1800000, 4950);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('J', 2000000, 5500);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('K', 2500000, 6875);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('L', 3000000, 8250);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('M', 4000000, 11000);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('N', 5000000, 13750);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('O', 6000000, 16500);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('P', 8000000, 22000);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('Q', 10000000, 27500);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('R', 12000000, 33000);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('S', 15000000, 41250);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('T', 18000000, 49500);
INSERT INTO nh_level (level, min_revenue, daily_limit) VALUES ('U', 20000000, 55000);

-- 测试用户（密码 123456 的 BCrypt 哈希）
INSERT INTO sys_user (mobile, password, user_type, real_name) VALUES ('13800138000', '$2a$10$Dpgqqd3E8ZoP1wWu2SqUxeI2jT3Y/U9iGA6ti2jUrQLCZ9dedaLsO', 0, '测试消费者');
INSERT INTO sys_user (mobile, password, user_type, real_name) VALUES ('13900139000', '$2a$10$Dpgqqd3E8ZoP1wWu2SqUxeI2jT3Y/U9iGA6ti2jUrQLCZ9dedaLsO', 1, '测试商家');

-- 测试商家
INSERT INTO merchant (user_id, store_name, business_license_url, corporate_account_no, regulatory_agreement_signed, audit_status, credit_score, level, monthly_revenue, province, city, district, address_detail, longitude, latitude)
VALUES (2, '优选生鲜', 'https://cdn.lsc.com/license1.jpg', '6222021234567890', 1, 1, 100, 'F', 1280000, '上海市', '上海市', '浦东新区', '世纪大道100号', 121.506377, 31.245105);

-- LSC 账户
INSERT INTO lsc_account (user_id, total_locked, total_available) VALUES (1, 1256800, 86420);
INSERT INTO lsc_account (user_id, total_locked, total_available) VALUES (2, 580000, 128600);

-- 测试商品
INSERT INTO product (merchant_id, product_name, product_desc, price, lsc_price, stock, sales, product_images, ai_review_result, status)
VALUES (2, '智利车厘子 JJ级 2斤装', '智利进口 JJ级 2斤装 顺丰冷链包邮', 98.00, 98, 580, 23000, 'https://cdn.lsc.com/p1.jpg', 0, 1);
INSERT INTO product (merchant_id, product_name, product_desc, price, lsc_price, stock, sales, product_images, ai_review_result, status)
VALUES (2, '有机蔬菜礼盒 8种时令蔬菜', '8种时令有机蔬菜 净重5kg', 128.00, 128, 320, 8600, 'https://cdn.lsc.com/p2.jpg', 0, 1);
INSERT INTO product (merchant_id, product_name, product_desc, price, lsc_price, stock, sales, product_images, ai_review_result, status)
VALUES (2, '阳澄湖大闸蟹 4两公 10只装', '阳澄湖直发 4两公蟹 10只', 388.00, 388, 150, 5200, 'https://cdn.lsc.com/p3.jpg', 0, 1);

-- 释放汇总示例
INSERT INTO release_summary (release_date, m_total, n_total, k_value, rate, l_locked, t_release, status)
VALUES (CURRENT_DATE, 1800000.00, 7560.00, 0.0042, 0.00045, 1836800000, 826560, 1);
