-- 费用报销、借款与还款（company_platform，MySQL 9.x）
-- 业务基线：V1.4 8.5 应付、付款与费用报销；9.4 审批矩阵“费用报销”一行。
-- 1. 报销单、借款单、还款记录相互独立：借款不并入报销，借款可通过报销付款冲销。
-- 2. 金额统一以分存储；页面录入的元在网关之前已截断为两位小数，后端不再四舍五入。
-- 3. 单据不物理删除：草稿可取消，提交后只能随审批结论流转；财务错误走后续红冲能力。
-- 4. 审批通过只把单据推进到“待付款/待放款”，真正的付款、放款、还款由财务登记。
SET NAMES utf8mb4;

USE company_platform;

-- 费用报销单。
CREATE TABLE IF NOT EXISTS biz_expense_claim (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    claim_no VARCHAR(40) NOT NULL COMMENT '报销单号：BX-雪花号',
    claimant_user_id BIGINT NOT NULL COMMENT '报销人用户 ID',
    claimant_name VARCHAR(64) DEFAULT NULL COMMENT '报销人姓名快照',
    expense_type VARCHAR(32) NOT NULL COMMENT '费用类型：TRAVEL/ENTERTAINMENT/PURCHASE/RENT_UTILITIES/LOGISTICS/OFFICE/MAINTENANCE/OTHER',
    amount BIGINT NOT NULL COMMENT '报销金额：分',
    expense_date DATE NOT NULL COMMENT '费用发生日期',
    factory_id BIGINT DEFAULT NULL COMMENT '归属工厂，可空',
    department VARCHAR(64) DEFAULT NULL COMMENT '归属部门/职能',
    order_no BIGINT DEFAULT NULL COMMENT '关联订单号，可空，仅用于费用归集',
    customer_id BIGINT DEFAULT NULL COMMENT '关联客户 ID，可空，仅用于费用归集',
    description VARCHAR(500) NOT NULL COMMENT '费用说明',
    attachment_url VARCHAR(500) DEFAULT NULL COMMENT '发票/收据/行程单等凭据地址，当前只存链接',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/PAID/REJECTED/CANCELLED',
    approval_instance_id BIGINT DEFAULT NULL COMMENT '审批实例 ID',
    approval_note VARCHAR(500) DEFAULT NULL COMMENT '最近一次审批意见',
    paid_amount BIGINT DEFAULT NULL COMMENT '现金实付金额：分；冲销借款时小于报销金额',
    offset_loan_amount BIGINT DEFAULT NULL COMMENT '冲销借款金额：分',
    payment_method VARCHAR(16) DEFAULT NULL COMMENT '付款方式：CASH/TRANSFER/OFFSET',
    payment_remark VARCHAR(500) DEFAULT NULL COMMENT '付款备注',
    paid_by BIGINT DEFAULT NULL COMMENT '付款经办人',
    paid_at DATETIME(3) DEFAULT NULL COMMENT '付款时间',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号，预留给乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_expense_claim_no (claim_no),
    KEY idx_expense_claim_claimant (claimant_user_id, status),
    KEY idx_expense_claim_factory (factory_id, status),
    KEY idx_expense_claim_date (expense_date),
    KEY idx_expense_claim_approval (approval_instance_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '费用报销单';

-- 借款单。
CREATE TABLE IF NOT EXISTS biz_loan (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    loan_no VARCHAR(40) NOT NULL COMMENT '借款单号：JK-雪花号',
    borrower_user_id BIGINT NOT NULL COMMENT '借款人用户 ID',
    borrower_name VARCHAR(64) DEFAULT NULL COMMENT '借款人姓名快照',
    amount BIGINT NOT NULL COMMENT '借款金额：分',
    loan_date DATE NOT NULL COMMENT '借款日期',
    purpose VARCHAR(500) NOT NULL COMMENT '借款用途',
    expected_repay_date DATE DEFAULT NULL COMMENT '预计还款日期，可空',
    factory_id BIGINT DEFAULT NULL COMMENT '归属工厂，可空',
    department VARCHAR(64) DEFAULT NULL COMMENT '归属部门/职能',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/DISBURSED/REPAID/REJECTED/CANCELLED',
    approval_instance_id BIGINT DEFAULT NULL COMMENT '审批实例 ID',
    approval_note VARCHAR(500) DEFAULT NULL COMMENT '最近一次审批意见',
    disburse_method VARCHAR(16) DEFAULT NULL COMMENT '放款方式：CASH/TRANSFER',
    disbursed_by BIGINT DEFAULT NULL COMMENT '放款经办人',
    disbursed_at DATETIME(3) DEFAULT NULL COMMENT '放款时间',
    repaid_amount BIGINT NOT NULL DEFAULT 0 COMMENT '累计已还金额：分，含报销冲销',
    outstanding_amount BIGINT NOT NULL DEFAULT 0 COMMENT '未还金额：分',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号，预留给乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_loan_no (loan_no),
    KEY idx_loan_borrower (borrower_user_id, status),
    KEY idx_loan_factory (factory_id, status),
    KEY idx_loan_approval (approval_instance_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '借款单';

-- 借款还款记录：手工还款与报销冲销共用本表，只追加不修改。
CREATE TABLE IF NOT EXISTS biz_loan_repayment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    repayment_no VARCHAR(40) NOT NULL COMMENT '还款单号：HK-雪花号',
    loan_id BIGINT NOT NULL COMMENT '借款单 ID',
    loan_no VARCHAR(40) NOT NULL COMMENT '借款单号快照',
    amount BIGINT NOT NULL COMMENT '本次还款金额：分',
    repayment_date DATE NOT NULL COMMENT '还款日期',
    method VARCHAR(16) NOT NULL COMMENT '还款方式：CASH/TRANSFER/OFFSET（报销冲销）',
    expense_claim_id BIGINT DEFAULT NULL COMMENT '冲销的报销单 ID，仅 OFFSET 有值',
    expense_claim_no VARCHAR(40) DEFAULT NULL COMMENT '冲销的报销单号快照',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '登记人',
    created_at DATETIME(3) NOT NULL COMMENT '登记时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_loan_repayment_no (repayment_no),
    KEY idx_loan_repayment_loan (loan_id, repayment_date),
    KEY idx_loan_repayment_claim (expense_claim_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '借款还款记录';
