-- 客户、合同与信用辅助表（company_platform，MySQL 9.x）
-- 业务基线：V1.4 第 5、8 章。
-- 1. 客户来源、归属业务员必填；客户归属变更、名称/额度/结算/开票变更需审批。
-- 2. 合同由财务正式录入，订单只能引用“已录入/生效”的合同。
-- 3. 金额一律 BIGINT 分；税率 DECIMAL(5,4)；不做期初应收/应付。
SET NAMES utf8mb4;

USE company_platform;

CREATE TABLE IF NOT EXISTS biz_customer (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '客户主键',
    customer_code VARCHAR(32) NOT NULL COMMENT '客户编码',
    name VARCHAR(128) NOT NULL COMMENT '客户全称',
    short_name VARCHAR(64) DEFAULT NULL COMMENT '客户简称',
    customer_type VARCHAR(32) NOT NULL DEFAULT 'ENTERPRISE' COMMENT 'ENTERPRISE/INDIVIDUAL/OTHER',
    credit_code VARCHAR(32) DEFAULT NULL COMMENT '统一社会信用代码',
    industry VARCHAR(64) DEFAULT NULL COMMENT '行业',
    region VARCHAR(64) DEFAULT NULL COMMENT '区域',
    source VARCHAR(64) NOT NULL COMMENT '客户来源字典编码',
    owner_user_id BIGINT NOT NULL COMMENT '归属业务员用户 ID',
    owner_name VARCHAR(64) DEFAULT NULL COMMENT '归属业务员姓名快照',
    department VARCHAR(64) DEFAULT NULL COMMENT '归属部门',
    contact_name VARCHAR(64) DEFAULT NULL COMMENT '主联系人',
    contact_title VARCHAR(64) DEFAULT NULL COMMENT '联系人职务',
    contact_phone VARCHAR(32) DEFAULT NULL COMMENT '联系人手机号',
    contact_email VARCHAR(128) DEFAULT NULL COMMENT '联系人邮箱',
    contact_wechat VARCHAR(64) DEFAULT NULL COMMENT '联系人微信',
    address VARCHAR(255) DEFAULT NULL COMMENT '收货/对账地址',
    billing_title VARCHAR(128) DEFAULT NULL COMMENT '开票抬头',
    tax_no VARCHAR(32) DEFAULT NULL COMMENT '纳税人识别号',
    bank_name VARCHAR(128) DEFAULT NULL COMMENT '开户行',
    bank_account VARCHAR(64) DEFAULT NULL COMMENT '银行账号',
    settlement_method VARCHAR(32) NOT NULL DEFAULT 'MONTHLY' COMMENT 'PREPAID/MONTHLY/ACCEPTANCE/OTHER',
    payment_days INT NOT NULL DEFAULT 0 COMMENT '账期天数',
    credit_limit BIGINT NOT NULL DEFAULT 0 COMMENT '信用额度，单位分；0 表示未授信',
    default_tax_rate DECIMAL(5,4) NOT NULL DEFAULT 0.1300 COMMENT '默认税率，4 位小数',
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    default_factory_id BIGINT DEFAULT NULL COMMENT '常用生产工厂，不限制订单工厂',
    status VARCHAR(32) NOT NULL DEFAULT 'PROSPECT' COMMENT 'PROSPECT/COOPERATING/SUSPENDED/LOST/BLACKLIST',
    risk_level VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/WATCH/HIGH',
    first_cooperation_date DATE DEFAULT NULL COMMENT '首次合作日期',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_code (customer_code),
    KEY idx_customer_owner (owner_user_id),
    KEY idx_customer_name (name),
    KEY idx_customer_credit_code (credit_code),
    KEY idx_customer_phone (contact_phone),
    KEY idx_customer_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户主数据';

CREATE TABLE IF NOT EXISTS biz_contract (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '合同主键',
    contract_no VARCHAR(64) NOT NULL COMMENT '财务正式合同编号',
    customer_id BIGINT NOT NULL COMMENT '客户 ID',
    contract_type VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/FRAMEWORK/ANNUAL_PRICE',
    start_date DATE DEFAULT NULL COMMENT '开始日期',
    end_date DATE DEFAULT NULL COMMENT '结束日期，空表示长期',
    contract_amount BIGINT NOT NULL COMMENT '合同金额，单位分',
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    settlement_method VARCHAR(32) DEFAULT NULL COMMENT '结算方式',
    payment_days INT DEFAULT NULL COMMENT '账期天数',
    tax_rate DECIMAL(5,4) DEFAULT NULL COMMENT '税率，4 位小数',
    price_terms TEXT DEFAULT NULL COMMENT '价格条款',
    quality_terms TEXT DEFAULT NULL COMMENT '质量条款',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_ENTRY'
        COMMENT 'PENDING_ENTRY/ENTERED/ACTIVE/CHANGING/EXPIRED/TERMINATED',
    entry_user_id BIGINT DEFAULT NULL COMMENT '财务录入人 ID',
    entry_user_name VARCHAR(64) DEFAULT NULL COMMENT '财务录入人姓名快照',
    entry_time DATETIME(3) DEFAULT NULL COMMENT '正式录入时间',
    attachment_url VARCHAR(500) DEFAULT NULL COMMENT '合同附件路径',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_no (contract_no),
    KEY idx_contract_customer (customer_id),
    KEY idx_contract_status (status),
    KEY idx_contract_dates (start_date, end_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户合同';

CREATE TABLE IF NOT EXISTS biz_receivable (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '应收主键',
    customer_id BIGINT NOT NULL COMMENT '客户 ID',
    contract_id BIGINT DEFAULT NULL COMMENT '合同 ID，手工其他应收可为空',
    order_no BIGINT DEFAULT NULL COMMENT '关联订单号',
    amount BIGINT NOT NULL COMMENT '应收金额，单位分',
    received_amount BIGINT NOT NULL DEFAULT 0 COMMENT '已收金额，单位分',
    due_date DATE DEFAULT NULL COMMENT '到期日',
    status VARCHAR(32) NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID/PARTIAL/PAID/CANCELLED',
    business_owner_id BIGINT DEFAULT NULL COMMENT '业务员用户 ID',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_receivable_customer_status (customer_id, status, due_date),
    KEY idx_receivable_contract (contract_id),
    KEY idx_receivable_order (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户应收辅助表，正式应收/收款在财务模块扩展';

-- 开发演示客户：保留 3001/4001/9001 三个固定编号，与既有测试订单对应。
-- 信用额度单位分：1,000,000.00 元；信用额度为 0 表示未授信，不能赊销下单。
INSERT IGNORE INTO biz_customer
    (id, customer_code, name, short_name, customer_type, credit_code, industry, region, source,
     owner_user_id, owner_name, department, contact_name, contact_phone, settlement_method,
     payment_days, credit_limit, default_tax_rate, currency, status, risk_level,
     first_cooperation_date, remark, created_by, created_at, updated_by, updated_at, version)
VALUES
    (3001, 'C3001', '华东纺织集团有限公司', '华东纺织', 'ENTERPRISE', '91310000DEMO3001X', '纺织制造', '华东',
     'EXHIBITION', 2, '销售一号', '销售部', '张经理', '13800003001', 'MONTHLY', 30, 100000000, 0.1300, 'CNY',
     'COOPERATING', 'NORMAL', '2026-01-12', '开发环境演示客户', 1, NOW(3), 1, NOW(3), 0),
    (4001, 'C4001', '星河贸易（上海）有限公司', '星河贸易', 'ENTERPRISE', '91310000DEMO4001X', '贸易', '华东',
     'REFERRAL', 2, '销售一号', '销售部', '李经理', '13800004001', 'MONTHLY', 30, 100000000, 0.1300, 'CNY',
     'COOPERATING', 'NORMAL', '2026-02-18', '开发环境演示客户', 1, NOW(3), 1, NOW(3), 0),
    (9001, 'C9001', '演示客户有限公司', '演示客户', 'ENTERPRISE', '91310000DEMO9001X', '综合制造', '华东',
     'EXHIBITION', 2, '销售一号', '销售部', '王经理', '13800009001', 'MONTHLY', 30, 100000000, 0.1300, 'CNY',
     'COOPERATING', 'NORMAL', '2026-03-01', '冒烟测试和前端联调客户', 1, NOW(3), 1, NOW(3), 0);

-- 财务已录入合同：订单必须引用 ENTERED/ACTIVE 合同；合同金额 1,000,000.00 元。
INSERT IGNORE INTO biz_contract
    (id, contract_no, customer_id, contract_type, start_date, end_date, contract_amount, currency,
     settlement_method, payment_days, tax_rate, price_terms, quality_terms, status,
     entry_user_id, entry_user_name, entry_time, remark, created_by, created_at, updated_by, updated_at, version)
VALUES
    (7001, 'HT-2026-9001', 9001, 'FRAMEWORK', '2025-01-01', '2030-12-31', 100000000, 'CNY',
     'MONTHLY', 30, 0.1300, '按材料+染料+工艺分项，以米计价；单价保留 4 位小数，金额截取到分。',
     '按客户确认版生产，质量问题按合同处理。', 'ENTERED', 1, '系统管理员', NOW(3),
     '演示客户框架合同', 1, NOW(3), 1, NOW(3), 0),
    (7002, 'HT-2026-3001', 3001, 'ANNUAL_PRICE', '2025-01-01', '2030-12-31', 100000000, 'CNY',
     'MONTHLY', 30, 0.1300, '年度价格协议，按米计价。', '按客户确认版生产。', 'ENTERED', 1, '系统管理员', NOW(3),
     '华东纺织年度价格协议', 1, NOW(3), 1, NOW(3), 0),
    (7003, 'HT-2026-4001', 4001, 'FRAMEWORK', '2025-01-01', '2030-12-31', 100000000, 'CNY',
     'MONTHLY', 30, 0.1300, '框架合同，按米计价。', '按客户确认版生产。', 'ENTERED', 1, '系统管理员', NOW(3),
     '星河贸易框架合同', 1, NOW(3), 1, NOW(3), 0);
