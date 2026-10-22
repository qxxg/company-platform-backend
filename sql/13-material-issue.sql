-- 工单领料与材料成本（company_order0，MySQL 9.x）
-- 业务基线：V1.4 仓储与生产规则——材料成本只能来自仓库，采购入库是库存唯一来源。
-- 1. 领料单针对一个生产工单：仓库必须与工单同厂，工单必须已分配设备且未完成。
-- 2. 领料单先建单（PENDING 待发料），确认发料（COMPLETED）时才扣减库存并计入工单材料成本。
-- 3. 单价与损耗率取自物料档案快照，金额 = 数量 × 单价，按分截断，与采购入库口径一致。
-- 4. 工单材料成本不落库，由已发料领料单明细实时汇总，避免与领料单两处维护。
-- 5. 库存数量不允许为负：发料用条件更新扣减，库存不足时整单失败。
SET NAMES utf8mb4;

USE company_order0;

-- 工单领料单头：一次领料请求，对应一个工单和一个仓库。
CREATE TABLE IF NOT EXISTS t_material_issue (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '领料单主键',
    issue_no VARCHAR(40) NOT NULL COMMENT '领料单号 MI-...',
    work_order_id BIGINT NOT NULL COMMENT '生产工单 ID',
    work_order_no VARCHAR(40) NOT NULL COMMENT '工单号快照',
    order_no BIGINT NOT NULL COMMENT '订单号',
    customer_id BIGINT NOT NULL COMMENT '客户 ID 快照',
    factory_id BIGINT NOT NULL COMMENT '工单所属工厂，仓库必须同厂',
    warehouse_id BIGINT NOT NULL COMMENT '领料仓库',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING 待发料 / COMPLETED 已发料 / CANCELLED 已取消',
    total_amount BIGINT NOT NULL DEFAULT 0 COMMENT '领料金额：分，计入工单材料成本',
    issued_at DATETIME(3) DEFAULT NULL COMMENT '发料时间',
    operator_user_id BIGINT DEFAULT NULL COMMENT '发料人用户 ID',
    operator_name VARCHAR(64) DEFAULT NULL COMMENT '发料人姓名快照',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号，预留乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_material_issue_no (issue_no),
    KEY idx_material_issue_work_order (work_order_id, status),
    KEY idx_material_issue_warehouse (warehouse_id, status),
    KEY idx_material_issue_order (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工单领料单，确认发料后计入工单材料成本';

-- 领料明细：数量、单价、金额和损耗率快照。
CREATE TABLE IF NOT EXISTS t_material_issue_line (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细主键',
    issue_id BIGINT NOT NULL COMMENT '领料单 ID',
    work_order_id BIGINT NOT NULL COMMENT '工单 ID，冗余便于按工单汇总成本',
    material_id BIGINT NOT NULL COMMENT '物料 ID',
    material_code VARCHAR(64) NOT NULL COMMENT '物料编码快照',
    material_name VARCHAR(128) NOT NULL COMMENT '物料名称快照',
    unit VARCHAR(16) NOT NULL COMMENT '单位快照',
    quantity DECIMAL(14, 2) NOT NULL COMMENT '领料数量，2 位小数',
    unit_price DECIMAL(16, 4) NOT NULL COMMENT '单价快照，4 位小数',
    amount BIGINT NOT NULL COMMENT '行金额：分，按分截断',
    loss_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '损耗率快照，2 位小数',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_material_issue_line_issue (issue_id),
    KEY idx_material_issue_line_work_order (work_order_id),
    KEY idx_material_issue_line_material (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工单领料明细';
