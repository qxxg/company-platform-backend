-- 财务应收与收款核销表（company_platform，MySQL 9.x）
-- 业务基线：V1.4 第 8 章。
-- 1. 应收由发货/开票正式产生；当前发货模块未上线，先允许财务手工补录并标记来源。
-- 2. 收款登记与核销分离：先登记收款单，再按客户未结应收逐笔分配。
-- 3. 金额一律 BIGINT 分；不录期初应收；所有核销明细永久保留，不做物理删除。
-- 4. 客户信用“未收应收”直接读取 biz_receivable 的 amount - received_amount。
SET NAMES utf8mb4;

USE company_platform;

-- 给已有的应收辅助表补充财务字段；重复执行不会报错。
DROP PROCEDURE IF EXISTS ensure_receivable_finance_columns;
DELIMITER $$
CREATE PROCEDURE ensure_receivable_finance_columns()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'biz_receivable'
                     AND column_name = 'source_type') THEN
        ALTER TABLE biz_receivable
            ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL'
                COMMENT '来源：ORDER/SHIPMENT/MANUAL/ADJUSTMENT' AFTER order_no;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'biz_receivable'
                     AND column_name = 'currency') THEN
        ALTER TABLE biz_receivable
            ADD COLUMN currency VARCHAR(8) NOT NULL DEFAULT 'CNY'
                COMMENT '币种' AFTER amount;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'biz_receivable'
                     AND column_name = 'remark') THEN
        ALTER TABLE biz_receivable
            ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT '备注' AFTER business_owner_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'biz_receivable'
                     AND column_name = 'created_by') THEN
        ALTER TABLE biz_receivable
            ADD COLUMN created_by BIGINT DEFAULT NULL COMMENT '创建人' AFTER remark;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'biz_receivable'
                     AND column_name = 'updated_by') THEN
        ALTER TABLE biz_receivable
            ADD COLUMN updated_by BIGINT DEFAULT NULL COMMENT '最后修改人' AFTER created_by;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'biz_receivable'
                     AND column_name = 'updated_at') THEN
        ALTER TABLE biz_receivable
            ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间' AFTER updated_by;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'biz_receivable'
                     AND column_name = 'version') THEN
        ALTER TABLE biz_receivable
            ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '版本号' AFTER updated_at;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'biz_receivable'
                     AND index_name = 'idx_receivable_due_status') THEN
        ALTER TABLE biz_receivable ADD KEY idx_receivable_due_status (due_date, status);
    END IF;
END$$
DELIMITER ;
CALL ensure_receivable_finance_columns();
DROP PROCEDURE ensure_receivable_finance_columns;

-- 收款单：一张收款单可以分多次核销到多张应收；未核销余额作为预收/待认领。
CREATE TABLE IF NOT EXISTS biz_receipt (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '收款单主键',
    customer_id BIGINT NOT NULL COMMENT '客户 ID',
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    amount BIGINT NOT NULL COMMENT '收款金额，单位分',
    allocated_amount BIGINT NOT NULL DEFAULT 0 COMMENT '已核销金额，单位分',
    received_date DATE NOT NULL COMMENT '收款日期',
    method VARCHAR(32) NOT NULL COMMENT 'BANK_TRANSFER/ACCEPTANCE/CASH/OTHER',
    bank_account VARCHAR(128) DEFAULT NULL COMMENT '收款账户/开户行',
    serial_no VARCHAR(64) DEFAULT NULL COMMENT '银行流水号/票据号',
    attachment_url VARCHAR(500) DEFAULT NULL COMMENT '凭证地址',
    status VARCHAR(32) NOT NULL DEFAULT 'UNALLOCATED'
        COMMENT 'UNALLOCATED/PARTIAL/ALLOCATED/CANCELLED',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '登记人',
    created_at DATETIME(3) NOT NULL COMMENT '登记时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (id),
    KEY idx_receipt_customer_date (customer_id, received_date),
    KEY idx_receipt_status (status),
    KEY idx_receipt_serial (serial_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户收款单，不接银行/第三方支付';

-- 核销明细：收款单与应收的多对多分配；同一收款单对同一应收只允许一条记录。
CREATE TABLE IF NOT EXISTS biz_receipt_allocation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '核销明细主键',
    receipt_id BIGINT NOT NULL COMMENT '收款单 ID',
    receivable_id BIGINT NOT NULL COMMENT '应收 ID',
    amount BIGINT NOT NULL COMMENT '本次核销金额，单位分',
    created_by BIGINT DEFAULT NULL COMMENT '核销人',
    created_at DATETIME(3) NOT NULL COMMENT '核销时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_allocation_receipt_receivable (receipt_id, receivable_id),
    KEY idx_allocation_receivable (receivable_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收款核销明细，永久留痕';

-- 演示应收数据：用于验证账龄、逾期和信用占用，不是期初应收。
-- 先清理同批演示数据再写入，保证脚本重复执行不会产生重复行。
-- 3001 逾期 60 天（演示 30 天阻断口径），4001 逾期 16 天，9001 未到期。
DELETE FROM biz_receivable WHERE remark LIKE '演示应收：%';

INSERT INTO biz_receivable
    (customer_id, contract_id, order_no, source_type, amount, currency, received_amount, due_date, status,
     business_owner_id, remark, created_by, created_at, updated_by, updated_at, version)
VALUES
    (3001, 7002, NULL, 'MANUAL', 6800000, 'CNY', 0, '2026-08-20', 'UNPAID',
     2, '演示应收：2026 年 8 月发货未结清', 1, NOW(3), 1, NOW(3), 0),
    (4001, 7003, NULL, 'MANUAL', 1250000, 'CNY', 0, '2026-10-05', 'UNPAID',
     2, '演示应收：2026 年 10 月发货未结清', 1, NOW(3), 1, NOW(3), 0),
    (9001, 7001, NULL, 'MANUAL', 980000, 'CNY', 0, '2026-11-30', 'UNPAID',
     2, '演示应收：2026 年 11 月发货未结清', 1, NOW(3), 1, NOW(3), 0);

-- 演示收款数据：一张待核销预收、一张已部分核销。正式环境请清空演示数据。
DELETE FROM biz_receipt_allocation
    WHERE receipt_id IN (SELECT id FROM biz_receipt WHERE remark LIKE '演示收款：%');
DELETE FROM biz_receipt WHERE remark LIKE '演示收款：%';

INSERT INTO biz_receipt
    (customer_id, currency, amount, allocated_amount, received_date, method, bank_account, serial_no,
     status, remark, created_by, created_at, updated_by, updated_at, version)
VALUES
    (9001, 'CNY', 500000, 0, '2026-10-18', 'BANK_TRANSFER', '中国银行 尾号 8821', 'DEMO-SK-0001',
     'UNALLOCATED', '演示收款：待核销预收款', 1, NOW(3), 1, NOW(3), 0),
    (3001, 'CNY', 2000000, 0, '2026-10-10', 'ACCEPTANCE', '银行承兑汇票', 'DEMO-SK-0002',
     'UNALLOCATED', '演示收款：已部分核销', 1, NOW(3), 1, NOW(3), 0);

-- 第二张收款单核销到 3001 的演示应收 20,000.00 元，应收转为部分收款。
INSERT INTO biz_receipt_allocation (receipt_id, receivable_id, amount, created_by, created_at)
SELECT r.id, a.id, 2000000, 1, NOW(3)
FROM biz_receipt r
         JOIN biz_receivable a ON a.customer_id = 3001 AND a.remark LIKE '演示应收：%'
WHERE r.serial_no = 'DEMO-SK-0002';

UPDATE biz_receipt SET allocated_amount = 2000000, status = 'ALLOCATED' WHERE serial_no = 'DEMO-SK-0002';
UPDATE biz_receivable SET received_amount = 2000000, status = 'PARTIAL'
WHERE customer_id = 3001 AND remark LIKE '演示应收：%';
