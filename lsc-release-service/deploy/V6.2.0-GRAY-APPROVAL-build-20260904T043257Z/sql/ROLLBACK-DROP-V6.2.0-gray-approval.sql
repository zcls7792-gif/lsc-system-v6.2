-- V6.2.0 Gray Approval Rollback DDL
-- 顺序：子表 → 父表（audit → node → flow）
-- 执行前请确认 lsc_release 当前库的状态！
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS lsc_release.gray_approval_audit;
DROP TABLE IF EXISTS lsc_release.gray_approval_node;
DROP TABLE IF EXISTS lsc_release.gray_approval_flow;
SET FOREIGN_KEY_CHECKS = 1;
