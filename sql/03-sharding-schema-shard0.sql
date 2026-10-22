-- 订单分片库 0：MySQL 192.168.3.44:3306 / company_order0
-- 分库规则：order_no % 2 = 0
-- 分表规则：(order_no >> 1) % 4 = 0..3
-- 金额 total_amount 单位为分，写入前已直接截断，禁止四舍五入。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS company_order0
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE company_order0;

CREATE TABLE IF NOT EXISTS t_order_0 (
    order_no BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL DEFAULT 0 COMMENT '财务已录入合同 ID',
    factory_id BIGINT NOT NULL,
    order_status VARCHAR(32) NOT NULL,
    total_amount BIGINT NOT NULL COMMENT '订单总额，单位：分，直接截断',
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    remark VARCHAR(500) DEFAULT NULL,
    created_by BIGINT DEFAULT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (order_no),
    KEY idx_customer_created (customer_id, created_at),
    KEY idx_contract (contract_id),
    KEY idx_factory (factory_id),
    KEY idx_status (order_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单物理表 0';

CREATE TABLE IF NOT EXISTS t_order_1 LIKE t_order_0;
CREATE TABLE IF NOT EXISTS t_order_2 LIKE t_order_0;
CREATE TABLE IF NOT EXISTS t_order_3 LIKE t_order_0;

-- 已存在旧表时补 contract_id；脚本可重复执行。MySQL 不支持 ADD COLUMN IF NOT EXISTS，
-- 因此通过 information_schema 判断后再执行 ALTER。
DROP PROCEDURE IF EXISTS ensure_order_contract_columns;
DELIMITER $$
CREATE PROCEDURE ensure_order_contract_columns()
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order_0'
          AND COLUMN_NAME = 'contract_id') = 0 THEN
        ALTER TABLE t_order_0 ADD COLUMN contract_id BIGINT NOT NULL DEFAULT 0
            COMMENT '财务已录入合同 ID' AFTER customer_id;
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order_1'
          AND COLUMN_NAME = 'contract_id') = 0 THEN
        ALTER TABLE t_order_1 ADD COLUMN contract_id BIGINT NOT NULL DEFAULT 0
            COMMENT '财务已录入合同 ID' AFTER customer_id;
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order_2'
          AND COLUMN_NAME = 'contract_id') = 0 THEN
        ALTER TABLE t_order_2 ADD COLUMN contract_id BIGINT NOT NULL DEFAULT 0
            COMMENT '财务已录入合同 ID' AFTER customer_id;
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order_3'
          AND COLUMN_NAME = 'contract_id') = 0 THEN
        ALTER TABLE t_order_3 ADD COLUMN contract_id BIGINT NOT NULL DEFAULT 0
            COMMENT '财务已录入合同 ID' AFTER customer_id;
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order_0'
          AND INDEX_NAME = 'idx_contract') = 0 THEN
        ALTER TABLE t_order_0 ADD INDEX idx_contract (contract_id);
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order_1'
          AND INDEX_NAME = 'idx_contract') = 0 THEN
        ALTER TABLE t_order_1 ADD INDEX idx_contract (contract_id);
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order_2'
          AND INDEX_NAME = 'idx_contract') = 0 THEN
        ALTER TABLE t_order_2 ADD INDEX idx_contract (contract_id);
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order_3'
          AND INDEX_NAME = 'idx_contract') = 0 THEN
        ALTER TABLE t_order_3 ADD INDEX idx_contract (contract_id);
    END IF;
END$$
DELIMITER ;
CALL ensure_order_contract_columns();
DROP PROCEDURE ensure_order_contract_columns;
