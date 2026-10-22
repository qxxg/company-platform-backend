-- 财务手工开票与客户对账单（company_platform，MySQL 9.x）
-- 业务基线：V1.4 第 8.4 节。
-- 1. 只做手工开票登记，不对接税控/电子发票平台；发票号码由财务手工录入。
-- 2. 开票金额不得超过合同金额；作废/红冲必须关联原发票并走审批，本期不开放接口。
-- 3. 对账单按客户和期间生成快照，确认后归档；重新生成会产生新版本，旧版本保留。
-- 4. 金额一律 BIGINT 分；税额、税率仅用于展示和核对，不参与应收余额计算。
SET NAMES utf8mb4;

USE company_platform;

-- 手工发票登记：一条记录对应一张实际开出的发票。
CREATE TABLE IF NOT EXISTS biz_invoice (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '发票主键',
    customer_id BIGINT NOT NULL COMMENT '客户 ID',
    contract_id BIGINT DEFAULT NULL COMMENT '关联合同 ID',
    order_no BIGINT DEFAULT NULL COMMENT '关联订单号',
    invoice_number VARCHAR(64) NOT NULL COMMENT '发票号码，手工录入，全局唯一',
    invoice_type VARCHAR(32) NOT NULL COMMENT 'VAT_SPECIAL/VAT_NORMAL/ELECTRONIC/OTHER',
    invoice_title VARCHAR(128) NOT NULL COMMENT '开票抬头',
    tax_no VARCHAR(64) DEFAULT NULL COMMENT '纳税人识别号',
    amount BIGINT NOT NULL COMMENT '价税合计，单位分',
    tax_amount BIGINT NOT NULL DEFAULT 0 COMMENT '税额，单位分',
    tax_rate DECIMAL(7, 4) NOT NULL DEFAULT 0.0000 COMMENT '税率百分数，如 13.0000',
    invoice_date DATE NOT NULL COMMENT '开票日期',
    status VARCHAR(32) NOT NULL DEFAULT 'REGISTERED'
        COMMENT 'REGISTERED/SENT/SIGNED/VOID/REVERSED',
    original_invoice_id BIGINT DEFAULT NULL COMMENT '红冲/作废关联的原发票 ID',
    attachment_url VARCHAR(500) DEFAULT NULL COMMENT '发票影像/附件地址',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '登记人',
    created_at DATETIME(3) NOT NULL COMMENT '登记时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_number (invoice_number),
    KEY idx_invoice_customer_date (customer_id, invoice_date),
    KEY idx_invoice_contract (contract_id),
    KEY idx_invoice_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '手工开票登记，不对接税控/电子发票';

-- 客户对账单：保存生成时的期间快照，客户确认后归档。
CREATE TABLE IF NOT EXISTS biz_statement (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '对账单主键',
    customer_id BIGINT NOT NULL COMMENT '客户 ID',
    period_start DATE NOT NULL COMMENT '对账期间开始日',
    period_end DATE NOT NULL COMMENT '对账期间结束日',
    opening_amount BIGINT NOT NULL DEFAULT 0 COMMENT '期初应收余额，单位分',
    receivable_amount BIGINT NOT NULL DEFAULT 0 COMMENT '本期新增应收，单位分',
    received_amount BIGINT NOT NULL DEFAULT 0 COMMENT '本期核销收款，单位分',
    invoice_amount BIGINT NOT NULL DEFAULT 0 COMMENT '本期开票金额，单位分（仅参考，不冲应收）',
    closing_amount BIGINT NOT NULL DEFAULT 0 COMMENT '期末应收余额，单位分',
    status VARCHAR(32) NOT NULL DEFAULT 'GENERATED' COMMENT 'GENERATED/CONFIRMED/CANCELLED',
    difference_note VARCHAR(500) DEFAULT NULL COMMENT '客户确认时的差异说明',
    confirmed_by BIGINT DEFAULT NULL COMMENT '确认人',
    confirmed_at DATETIME(3) DEFAULT NULL COMMENT '确认时间',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '生成人',
    created_at DATETIME(3) NOT NULL COMMENT '生成时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (id),
    KEY idx_statement_customer_period (customer_id, period_start, period_end),
    KEY idx_statement_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户对账单快照，确认后归档';

-- 对账单明细：应收发生、收款核销、开票三类流水，按业务日期排列。
CREATE TABLE IF NOT EXISTS biz_statement_line (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细主键',
    statement_id BIGINT NOT NULL COMMENT '对账单 ID',
    line_type VARCHAR(32) NOT NULL COMMENT 'RECEIVABLE/RECEIPT/INVOICE',
    ref_id BIGINT DEFAULT NULL COMMENT '来源业务主键',
    ref_no VARCHAR(128) DEFAULT NULL COMMENT '来源业务单号',
    biz_date DATE NOT NULL COMMENT '业务日期',
    amount BIGINT NOT NULL COMMENT '金额，单位分',
    remark VARCHAR(500) DEFAULT NULL COMMENT '明细说明',
    created_at DATETIME(3) NOT NULL COMMENT '写入时间',
    PRIMARY KEY (id),
    KEY idx_statement_line_statement (statement_id, biz_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '对账单明细，生成后不可修改';

-- 演示发票：用于验证开票金额、税额和合同可开票额度。正式环境请清空演示数据。
DELETE FROM biz_invoice WHERE remark LIKE '演示发票：%';

INSERT INTO biz_invoice
    (customer_id, contract_id, order_no, invoice_number, invoice_type, invoice_title, tax_no,
     amount, tax_amount, tax_rate, invoice_date, status, attachment_url, remark,
     created_by, created_at, updated_by, updated_at, version)
VALUES
    (3001, 7002, NULL, 'DEMO-INV-0001', 'VAT_SPECIAL', '华东纺织集团有限公司', '91310000DEMO3001X',
     5000000, 575221, 13.0000, '2026-09-15', 'SIGNED', NULL, '演示发票：2026 年 9 月专用发票',
     1, NOW(3), 1, NOW(3), 0),
    (4001, 7003, NULL, 'DEMO-INV-0002', 'VAT_NORMAL', '星河贸易（上海）有限公司', '91310000DEMO4001X',
     1250000, 143805, 13.0000, '2026-10-08', 'SENT', NULL, '演示发票：2026 年 10 月普通发票',
     1, NOW(3), 1, NOW(3), 0);
