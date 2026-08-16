# ============================================================
# 链盛通LSC系统 - 分库分表SQL脚本
# 8库32表 按user_id取模32
# ============================================================
-- 依赖: 必须先执行 lsc_system_v6.2.sql 创建 lsc_system 主库及其全局表,
--       本脚本通过 CREATE TABLE LIKE lsc_system.<table> 克隆分片表结构.
-- Docker: docker-compose 已按 01-schema.sql -> 02-sharding.sql 顺序挂载.
-- ============================================================

-- 创建8个物理库
CREATE DATABASE IF NOT EXISTS `lsc_db_0` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `lsc_db_1` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `lsc_db_2` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `lsc_db_3` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `lsc_db_4` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `lsc_db_5` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `lsc_db_6` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `lsc_db_7` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建Nacos配置库
CREATE DATABASE IF NOT EXISTS `nacos_config` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建XXL-JOB库
CREATE DATABASE IF NOT EXISTS `xxl_job` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建Seata库
CREATE DATABASE IF NOT EXISTS `seata` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================================
-- 需要分库分表的表(按user_id取模32):
-- users, lsc_accounts, lsc_transactions, available_lsc_details, orders
-- ============================================================

-- 分库分表规则说明:
-- 8库: db_0 ~ db_7
-- 每库4表: table_0 ~ table_3
-- 库序号 = (user_id % 32) / 4
-- 表序号 = (user_id % 32) % 4

-- 以下为存储过程: 在每个库中创建对应的分片表
-- 实际部署时由ShardingSphere自动路由, 此脚本仅创建物理表结构

DELIMITER $$

-- 生成分片表的存储过程
CREATE PROCEDURE IF NOT EXISTS create_sharding_tables()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE db_name VARCHAR(20);
    DECLARE tbl_name VARCHAR(30);
    
    WHILE i < 8 DO
        SET db_name = CONCAT('lsc_db_', i);
        
        -- 在每个库中创建4张分片表
        -- users分片表
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`users_0` LIKE `lsc_system`.`users`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`users_1` LIKE `lsc_system`.`users`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`users_2` LIKE `lsc_system`.`users`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`users_3` LIKE `lsc_system`.`users`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        -- lsc_accounts分片表
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`lsc_accounts_0` LIKE `lsc_system`.`lsc_accounts`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`lsc_accounts_1` LIKE `lsc_system`.`lsc_accounts`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`lsc_accounts_2` LIKE `lsc_system`.`lsc_accounts`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`lsc_accounts_3` LIKE `lsc_system`.`lsc_accounts`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        -- lsc_transactions分片表
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`lsc_transactions_0` LIKE `lsc_system`.`lsc_transactions`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`lsc_transactions_1` LIKE `lsc_system`.`lsc_transactions`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`lsc_transactions_2` LIKE `lsc_system`.`lsc_transactions`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`lsc_transactions_3` LIKE `lsc_system`.`lsc_transactions`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        -- available_lsc_details分片表
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`available_lsc_details_0` LIKE `lsc_system`.`available_lsc_details`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`available_lsc_details_1` LIKE `lsc_system`.`available_lsc_details`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`available_lsc_details_2` LIKE `lsc_system`.`available_lsc_details`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`available_lsc_details_3` LIKE `lsc_system`.`available_lsc_details`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        -- orders分片表
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`orders_0` LIKE `lsc_system`.`orders`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`orders_1` LIKE `lsc_system`.`orders`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`orders_2` LIKE `lsc_system`.`orders`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`orders_3` LIKE `lsc_system`.`orders`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_sharding_tables();

-- ============================================================
-- 不需要分库分表的全局表(存在lsc_system库):
-- merchant_extensions, merchant_nh_records, b2b_orders, products,
-- daily_release_summary, release_config, merchant_violations,
-- risk_logs, blockchain_records, daily_snapshot_records,
-- admin_audit_logs, tx_exception_log, admins, product_categories,
-- promotion_pending, evidence_failover, param_change_approval
-- ============================================================

-- ============================================================
-- Seata undo_log表 (每个分片库都需要)
-- ============================================================
DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS create_seata_undolog()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE db_name VARCHAR(20);
    
    WHILE i < 8 DO
        SET db_name = CONCAT('lsc_db_', i);
        
        SET @sql = CONCAT('CREATE TABLE IF NOT EXISTS `', db_name, '`.`undo_log` (
            `branch_id`     BIGINT(20)   NOT NULL COMMENT ''branch transaction id'',
            `xid`           VARCHAR(100) NOT NULL COMMENT ''global transaction id'',
            `context`       VARCHAR(128) NOT NULL COMMENT ''undo_log context,such as serialization'',
            `rollback_info` LONGBLOB     NOT NULL COMMENT ''rollback info'',
            `log_status`    INT(11)      NOT NULL COMMENT ''0:normal status,1:defense status'',
            `log_created`   DATETIME(6)  NOT NULL COMMENT ''create datetime'',
            `log_modified`  DATETIME(6)  NOT NULL COMMENT ''modify datetime'',
            UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''AT transaction mode undo table''');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL create_seata_undolog();

-- ============================================================
-- Seata server全局表
-- ============================================================
USE `seata`;

CREATE TABLE IF NOT EXISTS `global_table` (
    `xid`                       VARCHAR(128) NOT NULL,
    `transaction_id`            BIGINT,
    `status`                    TINYINT      NOT NULL,
    `application_id`            VARCHAR(32),
    `transaction_service_group` VARCHAR(32),
    `transaction_name`          VARCHAR(128),
    `timeout`                   INT,
    `begin_time`                BIGINT,
    `application_data`          VARCHAR(2000),
    `gmt_create`                 DATETIME,
    `gmt_modified`              DATETIME,
    PRIMARY KEY (`xid`),
    KEY `idx_status_gmt_modified` (`status` , `gmt_modified`),
    KEY `idx_transaction_id` (`transaction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `branch_table` (
    `branch_id`         BIGINT       NOT NULL,
    `xid`               VARCHAR(128) NOT NULL,
    `transaction_id`    BIGINT,
    `resource_group_id`  VARCHAR(32),
    `resource_id`        VARCHAR(256),
    `branch_type`        VARCHAR(8),
    `status`             TINYINT,
    `client_id`          VARCHAR(64),
    `application_data`   VARCHAR(2000),
    `gmt_create`         DATETIME(6),
    `gmt_modified`       DATETIME(6),
    PRIMARY KEY (`branch_id`),
    KEY `idx_xid` (`xid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `lock_table` (
    `row_key`        VARCHAR(128) NOT NULL,
    `xid`            VARCHAR(128),
    `transaction_id` BIGINT,
    `branch_id`      BIGINT       NOT NULL,
    `resource_id`    VARCHAR(256),
    `table_name`     VARCHAR(32),
    `pk`             VARCHAR(36),
    `status`          TINYINT      NOT NULL DEFAULT 0,
    `gmt_create`      DATETIME,
    `gmt_modified`   DATETIME,
    PRIMARY KEY (`row_key`),
    KEY `idx_branch_id` (`branch_id`),
    KEY `idx_xid` (`xid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `distributed_lock` (
    `lock_key`   CHAR(20) NOT NULL,
    `lock_value` VARCHAR(20) NOT NULL,
    `expire`     BIGINT,
    primary key (`lock_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `distributed_lock` (lock_key, lock_value, expire) VALUES ('handleDeadSession', '', 0);
