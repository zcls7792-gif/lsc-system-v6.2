-- 报价表（第7.1章 quote TTL 5分钟）
SET NAMES utf8mb4;

CREATE TABLE quote (
    quote_id        BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    buyer_type      VARCHAR(16)  NOT NULL,
    goods_cent      BIGINT       NOT NULL,
    coupon_cent     BIGINT       NOT NULL DEFAULT 0,
    lsc_unit        BIGINT       NOT NULL DEFAULT 0,
    rmb_cent        BIGINT       NOT NULL,
    discount_mode   VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    coupon_id       BIGINT       NULL,
    expires_at      DATETIME(3)  NOT NULL,
    payload_json    JSON         NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (quote_id),
    INDEX idx_quote_user_expire (user_id, expires_at),
    CONSTRAINT chk_quote CHECK (
        goods_cent >= 0
        AND coupon_cent >= 0
        AND lsc_unit >= 0
        AND rmb_cent >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价单(TTL 5分钟)';
