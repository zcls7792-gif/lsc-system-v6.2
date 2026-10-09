-- V11: 用户表增加微信 openid/unionid 列，支持微信登录
-- 关联: 第14.1章 微信登录回调（code2session / OAuth2）

ALTER TABLE user
    ADD COLUMN openid VARCHAR(64) NULL COMMENT '微信小程序openid' AFTER nickname,
    ADD COLUMN unionid VARCHAR(64) NULL COMMENT '微信开放平台unionid' AFTER openid;

ALTER TABLE user ADD INDEX idx_user_openid (openid);
ALTER TABLE user ADD INDEX idx_user_unionid (unionid);
