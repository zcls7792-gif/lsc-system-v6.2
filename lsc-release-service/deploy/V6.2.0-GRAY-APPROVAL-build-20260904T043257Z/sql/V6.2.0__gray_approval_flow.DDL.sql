-- ============================================================
-- Phase M：灰度审批工作流 DDL（lsc-release-service DB）
-- 版本：6.2.0-gray-approval-m1
-- 适用：MySQL 8.x / MariaDB 10.6+
-- ============================================================

-- -------- 1. 审批主表 --------
CREATE TABLE IF NOT EXISTS gray_approval_flow (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  flow_no             VARCHAR(32)     NOT NULL COMMENT '业务编号 GA+yyyyMMdd+6seq',
  flow_type           VARCHAR(24)     NOT NULL COMMENT 'GRADUATE/WEIGHT_CHANGE/ROLLBACK/LAUNCH',
  policy_id           VARCHAR(128)    NOT NULL COMMENT '对应灰度策略 ID',
  payload_json        JSON            NULL     COMMENT '执行参数：targetWeight / reason 等',
  applicant           VARCHAR(64)     NOT NULL,
  title               VARCHAR(256)    DEFAULT NULL,
  apply_reason        VARCHAR(1024)   DEFAULT NULL,
  status              VARCHAR(32)     NOT NULL DEFAULT 'DRAFT'
                      COMMENT 'DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/CANCELLED/EXECUTING/SUCCEEDED/EXECUTE_FAILED',
  required_approvals  TINYINT         NOT NULL DEFAULT 2,
  approved_count      TINYINT         NOT NULL DEFAULT 0,
  total_nodes         TINYINT         NOT NULL DEFAULT 2,
  execute_response    MEDIUMTEXT      NULL     COMMENT '网关接口响应 JSON',
  execute_cost_ms     BIGINT          NULL,
  approved_at         DATETIME(3)     NULL,
  created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by          VARCHAR(64)     DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_no (flow_no),
  KEY idx_policy_status (policy_id, status),
  KEY idx_applicant (applicant),
  KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='灰度审批主表';

-- -------- 2. 审批节点 --------
CREATE TABLE IF NOT EXISTS gray_approval_node (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  flow_id       BIGINT UNSIGNED NOT NULL,
  node_order    TINYINT         NOT NULL COMMENT '节点顺序 1..requiredApprovals',
  approver_role VARCHAR(64)     DEFAULT NULL,
  approver      VARCHAR(64)     DEFAULT NULL COMMENT '实际审批人',
  node_status   VARCHAR(16)     NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/APPROVED/REJECTED/SKIPPED',
  comment       VARCHAR(1024)   DEFAULT NULL,
  signature     VARCHAR(1024)   DEFAULT NULL,
  decided_at    DATETIME(3)     NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_flow (flow_id, node_order),
  KEY idx_approver_status (approver, node_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批节点';

-- -------- 3. 审计流水（不可变 append-only）--------
CREATE TABLE IF NOT EXISTS gray_approval_audit (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  flow_id      BIGINT UNSIGNED NOT NULL,
  flow_no      VARCHAR(32)     NOT NULL,
  action       VARCHAR(48)     NOT NULL,
  operator     VARCHAR(64)     NOT NULL,
  detail_json  JSON            NULL,
  chain_tx_hash VARCHAR(128)   DEFAULT NULL COMMENT '链上存证（可选）',
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_flow (flow_id, id),
  KEY idx_action_time (action, created_at),
  KEY idx_flow_no (flow_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='灰度审批审计流水';

-- -------- 4. 默认权限：ROLE_RELEASE_ADMIN 可审批灰度动作（样例）--------
-- 如果 lsc-user-service 管理权限，请在 release_service 下配置：
--   role_key = ROLE_RELEASE_ADMIN, menu = /api/release/gray/approvals/action/approve
