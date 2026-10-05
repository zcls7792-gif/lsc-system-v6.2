-- V9__lsc_rule_seed.sql
-- 第10.8章 预置默认风控规则
-- 规则口径冻结：
--  - 每条规则 score == threshold，单条命中即触发
--  - ALERT 仅记录执行日志不阻断业务；REVIEW/FREEZE_* 等阻塞类动作命中则立案并阻断
--  - match_mode=ALL 需所有条件同时满足

-- ============================================================
-- 场景1: 下单拦截 (scene=ORDER_CREATE, goods_cent, buyer_type)
-- ============================================================

-- R1: 大额下单告警(>=10000元) -> ALERT(仅记录)
INSERT INTO risk_rule(rule_id, rule_code, version, name, description, condition_json, match_mode,
    score, threshold, action, priority, status, effective_at, created_by, checksum, created_at, updated_at)
VALUES (9001, 'ORDER_LARGE_AMOUNT', 1, '大额下单告警', '单笔订单商品金额>=10000元时记录告警',
    '[{"field":"scene","op":"EQ","value":"ORDER_CREATE"},{"field":"goods_cent","op":"GTE","value":1000000}]',
    'ALL', 10, 10, 'ALERT', 100, 'ACTIVE', NOW(), 0, NULL, NOW(), NOW());

-- R2: B端超大额下单复核(>=50000元) -> REVIEW(阻断+立案)
INSERT INTO risk_rule(rule_id, rule_code, version, name, description, condition_json, match_mode,
    score, threshold, action, priority, status, effective_at, created_by, checksum, created_at, updated_at)
VALUES (9002, 'ORDER_B_SUPER_LARGE', 1, 'B端超大额下单复核', 'B端买家单笔>=50000元需人工复核',
    '[{"field":"scene","op":"EQ","value":"ORDER_CREATE"},{"field":"buyer_type","op":"EQ","value":"B"},{"field":"goods_cent","op":"GTE","value":5000000}]',
    'ALL', 20, 20, 'REVIEW', 90, 'ACTIVE', NOW(), 0, NULL, NOW(), NOW());

-- ============================================================
-- 场景2: 退款拦截 (scene=REFUND, order_id, refund_cent, refund_kind)
-- ============================================================

-- R3: 高额退款复核(>=5000元) -> REVIEW(阻断+立案)
INSERT INTO risk_rule(rule_id, rule_code, version, name, description, condition_json, match_mode,
    score, threshold, action, priority, status, effective_at, created_by, checksum, created_at, updated_at)
VALUES (9003, 'REFUND_LARGE_AMOUNT', 1, '高额退款复核', '单笔退款金额>=5000元需人工复核',
    '[{"field":"scene","op":"EQ","value":"REFUND"},{"field":"refund_cent","op":"GTE","value":500000}]',
    'ALL', 15, 15, 'REVIEW', 100, 'ACTIVE', NOW(), 0, NULL, NOW(), NOW());

-- R4: 价差退款关注 -> ALERT(仅记录)
INSERT INTO risk_rule(rule_id, rule_code, version, name, description, condition_json, match_mode,
    score, threshold, action, priority, status, effective_at, created_by, checksum, created_at, updated_at)
VALUES (9004, 'REFUND_PRICE_DIFF', 1, '价差退款关注', '价差退款类型记录告警供后续分析',
    '[{"field":"scene","op":"EQ","value":"REFUND"},{"field":"refund_kind","op":"EQ","value":"PRICE_DIFF"}]',
    'ALL', 10, 10, 'ALERT', 110, 'ACTIVE', NOW(), 0, NULL, NOW(), NOW());

-- ============================================================
-- 场景3: 权益发放拦截 (scene=GRANT, order_id)
-- ============================================================

-- R5: 权益发放统一记录 -> ALERT(仅记录,用于审计追踪)
INSERT INTO risk_rule(rule_id, rule_code, version, name, description, condition_json, match_mode,
    score, threshold, action, priority, status, effective_at, created_by, checksum, created_at, updated_at)
VALUES (9005, 'GRANT_LOG', 1, '权益发放记录', '每次权益发放记录执行日志用于审计',
    '[{"field":"scene","op":"EQ","value":"GRANT"}]',
    'ALL', 5, 5, 'ALERT', 100, 'ACTIVE', NOW(), 0, NULL, NOW(), NOW());
