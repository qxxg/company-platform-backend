-- 流程审批中心：审批定义、审批实例、审批任务与审批记录（company_platform，MySQL 9.x）
-- 业务基线：V1.4 流程审批模块。
-- 1. 审批定义按业务类型 + 金额档位匹配审批人（指定人或角色），金额以分存储。
-- 2. 审批实例是一次申请；审批任务是实例下的待办步骤，记录审批人与结论。
-- 3. 审批记录只追加不修改，保留提交、同意、驳回、撤销的完整轨迹。
-- 4. 审批通过后由业务动作执行器回调对应业务服务落地实际变更。
SET NAMES utf8mb4;

USE company_platform;

-- 审批定义：业务类型 + 金额档位 -> 审批人。
CREATE TABLE IF NOT EXISTS approval_definition (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '定义主键',
    business_type VARCHAR(32) NOT NULL COMMENT '业务类型：CREDIT_LIMIT/INVOICE_VOID/EXPENSE/LOAN/QUEUE_JUMP/WAREHOUSE',
    name VARCHAR(128) NOT NULL COMMENT '流程名称',
    amount_min BIGINT NOT NULL DEFAULT 0 COMMENT '金额下限：分，含',
    amount_max BIGINT DEFAULT NULL COMMENT '金额上限：分，不含；NULL 表示不封顶',
    approver_role VARCHAR(32) DEFAULT NULL COMMENT '审批角色编码',
    approver_user_id BIGINT DEFAULT NULL COMMENT '指定审批人用户 ID',
    priority INT NOT NULL DEFAULT 100 COMMENT '匹配优先级，数字小的优先',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 启用 / DISABLED 停用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号，预留乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_definition_name (business_type, name),
    KEY idx_approval_definition_match (business_type, status, priority)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批定义，含金额档位配置';

-- 审批实例：一次申请的整体状态。
CREATE TABLE IF NOT EXISTS approval_instance (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '实例主键',
    instance_no VARCHAR(40) NOT NULL COMMENT '审批单号 AP-...',
    business_type VARCHAR(32) NOT NULL COMMENT '业务类型',
    business_id VARCHAR(64) NOT NULL COMMENT '业务主键，例如客户 ID',
    title VARCHAR(200) NOT NULL COMMENT '申请标题',
    amount BIGINT DEFAULT NULL COMMENT '申请金额：分',
    factory_id BIGINT DEFAULT NULL COMMENT '工厂，数据范围',
    applicant_user_id BIGINT NOT NULL COMMENT '申请人用户 ID',
    applicant_name VARCHAR(64) DEFAULT NULL COMMENT '申请人姓名快照',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已撤销',
    definition_id BIGINT DEFAULT NULL COMMENT '匹配的审批定义 ID',
    current_step INT NOT NULL DEFAULT 1 COMMENT '当前步骤',
    payload VARCHAR(1000) DEFAULT NULL COMMENT '业务参数快照（JSON 文本）',
    result_note VARCHAR(500) DEFAULT NULL COMMENT '审批结论说明',
    created_at DATETIME(3) NOT NULL COMMENT '提交时间',
    finished_at DATETIME(3) DEFAULT NULL COMMENT '结束时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号，预留乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_instance_no (instance_no),
    KEY idx_approval_instance_applicant (applicant_user_id, status),
    KEY idx_approval_instance_business (business_type, business_id),
    KEY idx_approval_instance_status (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批实例';

-- 审批任务：实例下的待办步骤，审批人可以是指定用户或角色。
CREATE TABLE IF NOT EXISTS approval_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务主键',
    instance_id BIGINT NOT NULL COMMENT '审批实例 ID',
    step_no INT NOT NULL DEFAULT 1 COMMENT '步骤序号',
    approver_role VARCHAR(32) DEFAULT NULL COMMENT '审批角色编码',
    approver_user_id BIGINT DEFAULT NULL COMMENT '指定审批人用户 ID',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING 待审批 / APPROVED 已同意 / REJECTED 已驳回 / SKIPPED 已跳过',
    comment VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
    acted_by BIGINT DEFAULT NULL COMMENT '处理人用户 ID',
    acted_at DATETIME(3) DEFAULT NULL COMMENT '处理时间',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_approval_task_instance (instance_id, step_no),
    KEY idx_approval_task_user (approver_user_id, status),
    KEY idx_approval_task_role (approver_role, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批待办任务';

-- 审批记录：只追加的审批轨迹。
CREATE TABLE IF NOT EXISTS approval_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录主键',
    instance_id BIGINT NOT NULL COMMENT '审批实例 ID',
    action VARCHAR(32) NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/CANCEL/COMMENT',
    operator_user_id BIGINT DEFAULT NULL COMMENT '操作人用户 ID',
    operator_name VARCHAR(64) DEFAULT NULL COMMENT '操作人姓名快照',
    comment VARCHAR(500) DEFAULT NULL COMMENT '说明',
    created_at DATETIME(3) NOT NULL COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_approval_record_instance (instance_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审批轨迹记录';

-- 演示审批定义：金额档位由财务在上线前配置，这里给出常见档位示例。
INSERT INTO approval_definition
    (business_type, name, amount_min, amount_max, approver_role, approver_user_id, priority, status,
     remark, created_by, created_at, updated_by, updated_at, version)
SELECT demo.bt, demo.n, demo.amin, demo.amax, demo.role, demo.uid, demo.pri, demo.st, demo.r,
       1, NOW(3), 1, NOW(3), 0
FROM (
    SELECT 'CREDIT_LIMIT' AS bt, '信用额度调整审批' AS n, 0 AS amin, NULL AS amax,
           'FINANCE' AS role, NULL AS uid, 10 AS pri, 'ACTIVE' AS st,
           '演示定义：客户信用额度调整由财务审批' AS r
    UNION ALL
    SELECT 'INVOICE_VOID', '发票作废与红冲审批', 0, NULL, 'FINANCE', NULL, 10, 'ACTIVE',
           '演示定义：发票作废/红冲由财务审批'
    UNION ALL
    SELECT 'EXPENSE', '费用报销审批（1000 元以内）', 0, 100000, 'FACTORY_MANAGER', NULL, 10, 'ACTIVE',
           '演示定义：厂级审批档位'
    UNION ALL
    SELECT 'EXPENSE', '费用报销审批（1000 元以上）', 100000, NULL, 'FINANCE', NULL, 20, 'ACTIVE',
           '演示定义：财务审批档位'
    UNION ALL
    SELECT 'LOAN', '借款审批（5000 元以内）', 0, 500000, 'FACTORY_MANAGER', NULL, 10, 'ACTIVE',
           '演示定义：厂级审批档位'
    UNION ALL
    SELECT 'LOAN', '借款审批（5000 元以上）', 500000, NULL, 'FINANCE', NULL, 20, 'ACTIVE',
           '演示定义：财务审批档位'
    UNION ALL
    SELECT 'QUEUE_JUMP', '设备队列插队审批', 0, NULL, 'FACTORY_MANAGER', NULL, 10, 'ACTIVE',
           '演示定义：插队由厂长审批，后续迁移到本审批中心'
) AS demo
WHERE NOT EXISTS (
    SELECT 1 FROM approval_definition d WHERE d.business_type = demo.bt AND d.name = demo.n
);
