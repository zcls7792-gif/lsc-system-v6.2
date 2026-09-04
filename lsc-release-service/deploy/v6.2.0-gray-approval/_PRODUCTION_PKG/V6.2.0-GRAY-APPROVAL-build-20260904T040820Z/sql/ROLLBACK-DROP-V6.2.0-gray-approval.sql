-- ================================================================
-- V6.2.0 Gray Approval 回滚 DDL (仅用于紧急回滚到部署前状态)
-- 执行前确认：审批流程中所有单已 cancel / 未执行，业务侧已确认不丢数据
-- 执行顺序：先 drop 子表再 drop 父表
-- ================================================================
USE lsc_release;

-- 1) 审批流水 (FK → flow.id / node.id)
DROP TABLE IF EXISTS gray_approval_audit;

-- 2) 审批节点 (FK → flow.id)
DROP TABLE IF EXISTS gray_approval_node;

-- 3) 审批单主表
DROP TABLE IF EXISTS gray_approval_flow;
