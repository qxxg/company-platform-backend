-- 首页大屏 / BI 只读数据集市（company_bi，MySQL 9.x）
-- 业务基线：V1.4 第 11 章“首页大屏与管理驾驶舱”、11.5 KPI 口径。
-- 1. 本库只保存从业务库抽取的只读事实/维度数据与指标口径，不承接业务写入。
-- 2. 金额一律 BIGINT 分；比例由平台 BI 服务按明确分子/分母实时计算并保留两位小数。
-- 3. 不可计算的指标必须写入 bi_metric 并标记 PENDING_SOURCE，由前端显示“待数据源”，
--    禁止用近似值冒充实测值。
-- 4. 事实表由 platform-bi 定时全量刷新；刷新期间旧快照保持可读，成功后整体替换。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS company_bi
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE company_bi;

CREATE TABLE IF NOT EXISTS bi_dim_factory (
    factory_id BIGINT NOT NULL COMMENT '工厂 ID',
    factory_code VARCHAR(64) NOT NULL COMMENT '工厂编码',
    factory_name VARCHAR(128) NOT NULL COMMENT '工厂名称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    updated_at DATETIME(3) NOT NULL COMMENT '抽取时间',
    PRIMARY KEY (factory_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 工厂维度';

CREATE TABLE IF NOT EXISTS bi_dim_customer (
    customer_id BIGINT NOT NULL COMMENT '客户 ID',
    customer_name VARCHAR(128) NOT NULL COMMENT '客户全称',
    owner_user_id BIGINT DEFAULT NULL COMMENT '归属业务员 ID',
    owner_name VARCHAR(64) DEFAULT NULL COMMENT '归属业务员姓名快照',
    default_factory_id BIGINT DEFAULT NULL COMMENT '常用生产工厂，用于无订单应收的工厂归属回退',
    status VARCHAR(32) NOT NULL COMMENT '客户状态',
    credit_limit BIGINT NOT NULL DEFAULT 0 COMMENT '信用额度：分',
    created_at DATETIME(3) NOT NULL COMMENT '客户创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '抽取时间',
    PRIMARY KEY (customer_id),
    KEY idx_bi_dim_customer_owner (owner_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 客户维度';

CREATE TABLE IF NOT EXISTS bi_fact_order (
    order_no BIGINT NOT NULL COMMENT '订单号',
    customer_id BIGINT NOT NULL COMMENT '客户 ID',
    factory_id BIGINT NOT NULL COMMENT '工厂 ID',
    owner_user_id BIGINT DEFAULT NULL COMMENT '订单创建人 / 数据范围归属',
    order_status VARCHAR(32) NOT NULL COMMENT '订单状态',
    total_amount BIGINT NOT NULL COMMENT '订单金额：分',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    completed_at DATETIME(3) DEFAULT NULL COMMENT '完成时间近似值：状态为 COMPLETED 时取最后更新时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最后更新时间',
    PRIMARY KEY (order_no),
    KEY idx_bi_order_created (created_at),
    KEY idx_bi_order_factory_created (factory_id, created_at),
    KEY idx_bi_order_owner_created (owner_user_id, created_at),
    KEY idx_bi_order_customer_created (customer_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 订单事实';

CREATE TABLE IF NOT EXISTS bi_fact_receivable (
    receivable_id BIGINT NOT NULL COMMENT '应收 ID',
    customer_id BIGINT NOT NULL COMMENT '客户 ID',
    order_no BIGINT DEFAULT NULL COMMENT '关联订单号',
    factory_id BIGINT DEFAULT NULL COMMENT '订单工厂；无订单时取客户常用工厂',
    owner_user_id BIGINT DEFAULT NULL COMMENT '业务员 ID',
    amount BIGINT NOT NULL COMMENT '应收金额：分',
    received_amount BIGINT NOT NULL DEFAULT 0 COMMENT '已收金额：分',
    outstanding_amount BIGINT NOT NULL DEFAULT 0 COMMENT '未收金额：分',
    due_date DATE DEFAULT NULL COMMENT '到期日',
    status VARCHAR(32) NOT NULL COMMENT '应收状态',
    overdue_flag TINYINT NOT NULL DEFAULT 0 COMMENT '0 未逾期 / 1 已逾期',
    overdue_days INT NOT NULL DEFAULT 0 COMMENT '逾期天数，未逾期为 0',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (receivable_id),
    KEY idx_bi_receivable_customer (customer_id, status),
    KEY idx_bi_receivable_factory (factory_id, status),
    KEY idx_bi_receivable_owner (owner_user_id, status),
    KEY idx_bi_receivable_due (due_date, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 应收事实';

CREATE TABLE IF NOT EXISTS bi_fact_receipt (
    receipt_id BIGINT NOT NULL COMMENT '收款单 ID',
    customer_id BIGINT NOT NULL COMMENT '客户 ID',
    factory_id BIGINT DEFAULT NULL COMMENT '客户常用工厂，用于厂长范围回退',
    owner_user_id BIGINT DEFAULT NULL COMMENT '客户归属业务员 ID',
    amount BIGINT NOT NULL COMMENT '收款金额：分',
    allocated_amount BIGINT NOT NULL DEFAULT 0 COMMENT '已核销金额：分',
    received_date DATE NOT NULL COMMENT '收款日期',
    status VARCHAR(32) NOT NULL COMMENT '收款单状态',
    created_at DATETIME(3) NOT NULL COMMENT '登记时间',
    PRIMARY KEY (receipt_id),
    KEY idx_bi_receipt_date (received_date),
    KEY idx_bi_receipt_factory (factory_id, received_date),
    KEY idx_bi_receipt_owner (owner_user_id, received_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 收款事实';

CREATE TABLE IF NOT EXISTS bi_fact_expense (
    claim_id BIGINT NOT NULL COMMENT '报销单 ID',
    claimant_user_id BIGINT NOT NULL COMMENT '报销人 ID',
    factory_id BIGINT DEFAULT NULL COMMENT '归属工厂',
    amount BIGINT NOT NULL COMMENT '报销金额：分',
    expense_date DATE NOT NULL COMMENT '费用发生日期',
    status VARCHAR(32) NOT NULL COMMENT '报销单状态',
    paid_amount BIGINT DEFAULT NULL COMMENT '现金实付：分',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (claim_id),
    KEY idx_bi_expense_date (expense_date),
    KEY idx_bi_expense_factory (factory_id, status),
    KEY idx_bi_expense_claimant (claimant_user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 费用报销事实';

CREATE TABLE IF NOT EXISTS bi_fact_loan (
    loan_id BIGINT NOT NULL COMMENT '借款单 ID',
    borrower_user_id BIGINT NOT NULL COMMENT '借款人 ID',
    factory_id BIGINT DEFAULT NULL COMMENT '归属工厂',
    amount BIGINT NOT NULL COMMENT '借款金额：分',
    loan_date DATE NOT NULL COMMENT '借款日期',
    status VARCHAR(32) NOT NULL COMMENT '借款单状态',
    outstanding_amount BIGINT NOT NULL DEFAULT 0 COMMENT '未还金额：分',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (loan_id),
    KEY idx_bi_loan_date (loan_date),
    KEY idx_bi_loan_factory (factory_id, status),
    KEY idx_bi_loan_borrower (borrower_user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 借款事实';

CREATE TABLE IF NOT EXISTS bi_fact_work_order (
    work_order_id BIGINT NOT NULL COMMENT '工单 ID',
    order_no BIGINT NOT NULL COMMENT '订单号',
    customer_id BIGINT NOT NULL COMMENT '客户 ID',
    factory_id BIGINT NOT NULL COMMENT '工厂 ID',
    owner_user_id BIGINT DEFAULT NULL COMMENT '订单业务员 ID',
    device_id BIGINT DEFAULT NULL COMMENT '设备 ID',
    status VARCHAR(32) NOT NULL COMMENT '工单状态',
    planned_meters DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '计划米数；仅用于排队/计划分析，不代表实际产量',
    actual_start DATETIME(3) DEFAULT NULL COMMENT '实际开始时间',
    actual_end DATETIME(3) DEFAULT NULL COMMENT '实际结束时间',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (work_order_id),
    KEY idx_bi_work_order_factory (factory_id, status),
    KEY idx_bi_work_order_owner (owner_user_id, status),
    KEY idx_bi_work_order_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 生产工单事实';

CREATE TABLE IF NOT EXISTS bi_fact_equipment (
    device_id BIGINT NOT NULL COMMENT '设备 ID',
    factory_id BIGINT NOT NULL COMMENT '工厂 ID',
    device_type VARCHAR(64) NOT NULL COMMENT '设备类型',
    status VARCHAR(32) NOT NULL COMMENT '设备状态',
    capacity_meters_per_hour DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '标称产能：米/小时',
    updated_at DATETIME(3) NOT NULL COMMENT '设备最后更新时间',
    PRIMARY KEY (device_id),
    KEY idx_bi_equipment_factory (factory_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 设备当前状态快照';

CREATE TABLE IF NOT EXISTS bi_fact_queue (
    queue_id BIGINT NOT NULL COMMENT '队列 ID',
    device_id BIGINT NOT NULL COMMENT '设备 ID',
    work_order_id BIGINT NOT NULL COMMENT '工单 ID',
    factory_id BIGINT NOT NULL COMMENT '工厂 ID',
    status VARCHAR(32) NOT NULL COMMENT '队列状态',
    enqueued_at DATETIME(3) NOT NULL COMMENT '入队时间',
    planned_start DATETIME(3) DEFAULT NULL COMMENT '计划开始时间',
    actual_start DATETIME(3) DEFAULT NULL COMMENT '实际开始时间',
    PRIMARY KEY (queue_id),
    KEY idx_bi_queue_factory (factory_id, status),
    KEY idx_bi_queue_enqueued (enqueued_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 设备队列事实';

CREATE TABLE IF NOT EXISTS bi_fact_inventory (
    inventory_id BIGINT NOT NULL COMMENT '库存 ID',
    warehouse_id BIGINT NOT NULL COMMENT '仓库 ID',
    factory_id BIGINT NOT NULL COMMENT '工厂 ID',
    material_id BIGINT NOT NULL COMMENT '物料 ID',
    material_name VARCHAR(128) NOT NULL COMMENT '物料名称',
    quantity DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '当前数量',
    unit_cost DECIMAL(16, 4) NOT NULL DEFAULT 0.0000 COMMENT '最近一次采购入库单价；无入库记录为 0',
    amount BIGINT NOT NULL DEFAULT 0 COMMENT '库存金额：分，数量 × 最新入库单价后截断',
    safety_stock DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '物料安全库存快照',
    updated_at DATETIME(3) NOT NULL COMMENT '库存更新时间',
    PRIMARY KEY (inventory_id),
    KEY idx_bi_inventory_factory (factory_id),
    KEY idx_bi_inventory_material (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 库存快照';

CREATE TABLE IF NOT EXISTS bi_fact_transfer (
    transfer_id BIGINT NOT NULL COMMENT '调拨单 ID',
    from_factory_id BIGINT NOT NULL COMMENT '调出工厂',
    to_factory_id BIGINT NOT NULL COMMENT '调入工厂',
    status VARCHAR(16) NOT NULL COMMENT '调拨状态',
    shipped_at DATETIME(3) DEFAULT NULL COMMENT '出库时间',
    received_at DATETIME(3) DEFAULT NULL COMMENT '入库时间',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (transfer_id),
    KEY idx_bi_transfer_from (from_factory_id, status),
    KEY idx_bi_transfer_to (to_factory_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 跨厂调拨事实';

CREATE TABLE IF NOT EXISTS bi_fact_approval (
    instance_id BIGINT NOT NULL COMMENT '审批实例 ID',
    business_type VARCHAR(32) NOT NULL COMMENT '业务类型',
    business_id VARCHAR(64) NOT NULL COMMENT '业务主键',
    factory_id BIGINT DEFAULT NULL COMMENT '工厂范围',
    applicant_user_id BIGINT NOT NULL COMMENT '申请人 ID',
    amount BIGINT DEFAULT NULL COMMENT '申请金额：分',
    status VARCHAR(16) NOT NULL COMMENT '审批状态',
    created_at DATETIME(3) NOT NULL COMMENT '提交时间',
    finished_at DATETIME(3) DEFAULT NULL COMMENT '结束时间',
    duration_seconds BIGINT DEFAULT NULL COMMENT '审批耗时秒数',
    PRIMARY KEY (instance_id),
    KEY idx_bi_approval_factory (factory_id, status),
    KEY idx_bi_approval_applicant (applicant_user_id, status),
    KEY idx_bi_approval_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 审批事实';

CREATE TABLE IF NOT EXISTS bi_customer_credit (
    customer_id BIGINT NOT NULL COMMENT '客户 ID',
    customer_name VARCHAR(128) NOT NULL COMMENT '客户名称',
    owner_user_id BIGINT DEFAULT NULL COMMENT '归属业务员 ID',
    credit_limit BIGINT NOT NULL DEFAULT 0 COMMENT '信用额度：分',
    receivable_open BIGINT NOT NULL DEFAULT 0 COMMENT '未收应收：分',
    order_open BIGINT NOT NULL DEFAULT 0 COMMENT '未关闭订单金额：分',
    used_amount BIGINT NOT NULL DEFAULT 0 COMMENT '已用信用 = 未收应收 + 未关闭订单',
    over_limit TINYINT NOT NULL DEFAULT 0 COMMENT '0 未超限 / 1 超限',
    updated_at DATETIME(3) NOT NULL COMMENT '计算时间',
    PRIMARY KEY (customer_id),
    KEY idx_bi_credit_owner (owner_user_id, over_limit)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 客户信用占用';

CREATE TABLE IF NOT EXISTS bi_metric (
    metric_code VARCHAR(64) NOT NULL COMMENT '指标编码',
    metric_name VARCHAR(128) NOT NULL COMMENT '指标名称',
    category VARCHAR(32) NOT NULL COMMENT '经营/工厂/设备/仓储/财务/流程',
    unit VARCHAR(16) NOT NULL COMMENT '单位',
    definition VARCHAR(1000) NOT NULL COMMENT 'V1.4 口径说明',
    source_note VARCHAR(500) DEFAULT NULL COMMENT '数据来源或待数据源原因',
    status VARCHAR(16) NOT NULL DEFAULT 'READY' COMMENT 'READY / PENDING_SOURCE',
    permission VARCHAR(128) DEFAULT NULL COMMENT '查看该指标所需权限码',
    sort_no INT NOT NULL DEFAULT 100 COMMENT '展示顺序',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (metric_code),
    KEY idx_bi_metric_status (status, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI 指标语义层与口径';

CREATE TABLE IF NOT EXISTS bi_etl_run (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '运行 ID',
    started_at DATETIME(3) NOT NULL COMMENT '开始时间',
    finished_at DATETIME(3) DEFAULT NULL COMMENT '结束时间',
    status VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
    rows_written BIGINT NOT NULL DEFAULT 0 COMMENT '写入事实行数',
    message VARCHAR(1000) DEFAULT NULL COMMENT '失败原因或摘要',
    PRIMARY KEY (id),
    KEY idx_bi_etl_status (status, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'BI ETL 运行记录';

-- 指标口径。READY 为当前可计算；PENDING_SOURCE 明确标记“待数据源”，禁止用近似值冒充。
INSERT INTO bi_metric
    (metric_code, metric_name, category, unit, definition, source_note, status, permission, sort_no, updated_at)
VALUES
    ('month_order_amount', '本月订单额', '经营', '元',
     '本月创建且未取消的订单金额合计，金额按分存储并直接截断。',
     '来源：订单分片库 8 张物理表。', 'READY', 'order:read', 10, NOW(3)),
    ('month_order_count', '本月订单数', '经营', '笔',
     '本月创建且未取消的订单笔数。',
     '来源：订单分片库 8 张物理表。', 'READY', 'order:read', 20, NOW(3)),
    ('order_completion_rate', '订单完成率', '经营', '%',
     '本月创建订单中状态为已完成的订单数 / 本月创建且未取消的订单数 × 100%。',
     '仅按订单状态统计；发货与交付确认模块未实现。', 'READY', 'order:read', 30, NOW(3)),
    ('month_receipt_amount', '本月收款', '经营', '元',
     '本月收款单金额合计，不按核销进度折算，按收款日期归属期间。',
     '来源：财务收款单。', 'READY', 'finance:receipt:read', 40, NOW(3)),
    ('receivable_balance', '应收余额', '财务', '元',
     '所有未结清应收的未收金额合计，不含已取消应收。',
     '来源：财务应收辅助表。', 'READY', 'finance:receivable:read', 50, NOW(3)),
    ('overdue_receivable', '逾期应收', '财务', '元',
     '到期日早于今天且仍有未收余额的应收金额合计。',
     '来源：财务应收辅助表；逾期 30 天阻断下单已在业务服务执行。', 'READY', 'finance:receivable:read', 60, NOW(3)),
    ('credit_over_limit_customers', '信用超限客户', '财务', '户',
     '已用信用额度超过客户信用额度的客户数；已用 = 未收应收 + 已确认未发货订单。',
     '信用额度为 0 表示未授信，不纳入超限客户。', 'READY', 'finance:credit:read', 70, NOW(3)),
    ('open_order_count', '未关闭订单', '经营', '笔',
     '状态不是已取消、已完成的订单笔数，用于观察在制与待排产压力。',
     '来源：订单分片库。', 'READY', 'order:read', 80, NOW(3)),
    ('equipment_fault_count', '故障设备', '设备', '台',
     '当前状态为故障维修的设备数量。',
     '来源：设备台账当前状态快照。', 'READY', 'equipment:read', 90, NOW(3)),
    ('equipment_queue_wait', '队列平均等待', '设备', '分钟',
     '已开始生产的队列记录从入队到实际开始的平均分钟数。',
     '来源：设备队列与生产工单实际开始时间；未开始的等待不在均值内。', 'READY', 'production:schedule:read', 100, NOW(3)),
    ('inventory_amount', '库存金额', '仓储', '元',
     '各仓库库存数量 × 该物料最近一次采购入库单价，逐行截断到分后汇总。',
     '来源：库存台账、采购入库明细；无采购入库单价的物料按 0 计。', 'READY', 'warehouse:read', 110, NOW(3)),
    ('inventory_shortage_count', '缺料预警项', '仓储', '项',
     '同一工厂、同一物料的库存数量低于物料安全库存的项数。',
     '当前按工厂汇总；未实现按仓库安全库存。', 'READY', 'warehouse:read', 120, NOW(3)),
    ('transfer_in_transit_count', '调拨在途', '仓储', '单',
     '状态为待出库或在途的跨厂调拨单数量。',
     '来源：跨厂调拨单。', 'READY', 'warehouse:read', 130, NOW(3)),
    ('expense_pending_amount', '待付款报销', '财务', '元',
     '状态为审批中或待付款的费用报销金额合计。',
     '来源：费用报销单；不含草稿、已驳回、已付款。', 'READY', 'finance:expense:read', 140, NOW(3)),
    ('loan_outstanding', '借款未还', '财务', '元',
     '已放款且尚未还清的借款未还金额合计。',
     '来源：借款单；报销冲销会减少未还金额。', 'READY', 'finance:loan:read', 150, NOW(3)),
    ('pending_approval_count', '待审批数量', '流程', '条',
     '当前状态为待审批的审批实例数量。',
     '来源：审批中心。', 'READY', 'approval:read', 160, NOW(3)),
    ('average_approval_hours', '平均审批时长', '流程', '小时',
     '已完成审批实例从提交到结束的平均小时数，保留两位小数。',
     '来源：审批实例提交/结束时间。', 'READY', 'approval:read', 170, NOW(3)),
    ('on_time_delivery_rate', '准时交付率', '经营', '%',
     '按期交付订单行 / 期间交付订单行 × 100%。',
     '待数据源：发货、交付确认与订单行交期模块尚未实现。', 'PENDING_SOURCE', 'order:read', 210, NOW(3)),
    ('quote_conversion_rate', '报价转化率', '经营', '%',
     '转订单报价数 / 已确认报价数 × 100%。',
     '待数据源：报价与图案模块尚未实现。', 'PENDING_SOURCE', 'customer:read', 220, NOW(3)),
    ('order_change_rate', '订单变更率', '经营', '%',
     '发生变更订单数 / 已确认订单数 × 100%。',
     '待数据源：订单变更单与变更审批尚未实现。', 'PENDING_SOURCE', 'order:read', 230, NOW(3)),
    ('rework_rate', '返工率', '质量', '%',
     '返工长度 / 生产总长度 × 100%。',
     '待数据源：返工记录与报工产量尚未实现。', 'PENDING_SOURCE', 'order:read', 240, NOW(3)),
    ('production_output', '产量（米）', '工厂', '米',
     '期间实际生产完成并入库的米数。',
     '待数据源：报工/实际产量字段尚未实现；当前工单仅有计划米数，不能替代实际产量。', 'PENDING_SOURCE', 'production:schedule:read', 250, NOW(3)),
    ('equipment_utilization', '设备利用率', '设备', '%',
     '实际运行时间 / 计划可用时间 × 100%。',
     '待数据源：缺少计划可用时间与完整运行时长，仅靠状态日志无法按 V1.4 口径计算。', 'PENDING_SOURCE', 'equipment:read', 260, NOW(3)),
    ('equipment_fault_rate', '设备故障率', '设备', '%',
     '故障停机时间 / 计划运行时间 × 100%。',
     '待数据源：缺少计划运行时间与停机时长汇总。', 'PENDING_SOURCE', 'equipment:read', 270, NOW(3)),
    ('equipment_online_rate', '设备在线率', '设备', '%',
     '在线设备数 / 设备总数 × 100%，按心跳/上报判定。',
     '待数据源：IoTDB 心跳在线判定尚未进入 BI ETL。', 'PENDING_SOURCE', 'production:report:read', 280, NOW(3)),
    ('equipment_report_timely_rate', '设备上报及时率', '设备', '%',
     '按周期正常上报的设备数 / 应上报设备数 × 100%。',
     '待数据源：设备上报周期与应报设备清单尚未进入 BI ETL。', 'PENDING_SOURCE', 'production:report:read', 290, NOW(3)),
    ('ocr_match_success_rate', 'OCR 匹配成功率', '设备', '%',
     '识别并精准匹配订单号数 / OCR 识别总数 × 100%。',
     '待数据源：设备图片/OCR 统计尚未进入 BI ETL。', 'PENDING_SOURCE', 'production:report:read', 300, NOW(3)),
    ('inventory_turnover', '库存周转率', '仓储', '次',
     '期间销货成本 / 平均库存金额。',
     '待数据源：缺少销货成本、出库与期初期末库存口径。', 'PENDING_SOURCE', 'warehouse:read', 310, NOW(3)),
    ('inventory_accuracy', '库存准确率', '仓储', '%',
     '盘点账实相符项数 / 盘点总项数 × 100%。',
     '待数据源：库存盘点模块尚未实现。', 'PENDING_SOURCE', 'warehouse:read', 320, NOW(3)),
    ('purchase_on_time_rate', '采购到货及时率', '采购', '%',
     '按期到货采购行 / 到货采购行 × 100%。',
     '待数据源：采购订单与计划到货日期尚未实现。', 'PENDING_SOURCE', 'warehouse:read', 330, NOW(3)),
    ('order_gross_margin', '订单毛利率', '财务', '%',
     '（订单收入 − 订单成本）/ 订单收入 × 100%。',
     '待数据源：订单收入、完整成本归集与分摊口径尚未实现；成本/毛利仅对有权限角色开放。', 'PENDING_SOURCE', 'bi:cost:read', 340, NOW(3)),
    ('ap_balance', '应付余额', '财务', '元',
     '供应商应付余额与付款计划。',
     '待数据源：应付与付款模块尚未实现。', 'PENDING_SOURCE', 'bi:cost:read', 350, NOW(3)),
    ('maintenance_cost', '维保费用', '设备', '元',
     '期间设备维护工单费用合计，全部由公司承担。',
     '待数据源：维护工单已实现，但费用字段与结算口径尚未进入 BI ETL。', 'PENDING_SOURCE', 'equipment:maintenance:read', 360, NOW(3))
ON DUPLICATE KEY UPDATE
    metric_name = VALUES(metric_name),
    category = VALUES(category),
    unit = VALUES(unit),
    definition = VALUES(definition),
    source_note = VALUES(source_note),
    status = VALUES(status),
    permission = VALUES(permission),
    sort_no = VALUES(sort_no),
    updated_at = VALUES(updated_at);

-- BI 看板权限：所有员工可看工作台；厂长/财务可看大屏；成本毛利只给财务和管理员。
-- 权限表属于平台主库，这里切回 company_platform 再写入。
USE company_platform;

INSERT IGNORE INTO sys_permission (code, name, type, parent_id, status)
VALUES ('bi:dashboard:read', '查看经营看板', 'api', 0, 1),
       ('bi:cockpit:read', '查看经营大屏', 'api', 0, 1),
       ('bi:cost:read', '查看成本毛利指标', 'api', 0, 1),
       ('bi:etl:run', '触发 BI 数据同步', 'api', 0, 1);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.code = 'bi:dashboard:read'
WHERE r.code IN ('ADMIN', 'SALES', 'FACTORY_MANAGER', 'OPERATOR', 'FINANCE');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.code = 'bi:cockpit:read'
WHERE r.code IN ('ADMIN', 'FACTORY_MANAGER', 'FINANCE');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.code = 'bi:cost:read'
WHERE r.code IN ('ADMIN', 'FINANCE');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
         JOIN sys_permission p ON p.code = 'bi:etl:run'
WHERE r.code = 'ADMIN';

-- 开发环境应用账号：BI 服务只需本库读写；生产应拆分为只读/ETL 两个账号。
GRANT ALL PRIVILEGES ON company_bi.* TO 'company_app'@'%';
FLUSH PRIVILEGES;
