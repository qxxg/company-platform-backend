-- 仓储系统：仓库档案、物料档案、库存台账、采购入库与跨厂调拨（company_order0，MySQL 9.x）
-- 业务基线：V1.4 仓储模块。
-- 1. 每个工厂至少有一个主仓库（MAIN），仓库不跨工厂归属；跨厂调拨走调拨单。
-- 2. 物料档案保存单价（4 位小数）与损耗率（2 位小数）；金额一律以分存储并截断。
-- 3. 采购入库是库存增加的**唯一**来源：没有期初库存，也不提供库存调整接口；
--    跨厂调拨入库是唯一的“转移”来源。
-- 4. 库存数量不允许为负；出库（调拨）必须校验可用数量充足。
-- 5. 采购入库单确认收货后不可再修改，也不提供作废/红冲（审批中心上线前无通道）。
-- 6. 本脚本只操作 company_order0（3306），与设备台账、排产表同库，便于后续工单领料联动。
SET NAMES utf8mb4;

USE company_order0;

-- 仓库档案：每个工厂至少一个主仓库，仓库不绑定客户。
CREATE TABLE IF NOT EXISTS t_warehouse (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仓库主键',
    warehouse_no VARCHAR(64) NOT NULL COMMENT '仓库编号，全公司唯一',
    warehouse_name VARCHAR(128) NOT NULL COMMENT '仓库名称',
    factory_id BIGINT NOT NULL COMMENT '所属工厂，仓库不跨厂',
    warehouse_type VARCHAR(32) NOT NULL DEFAULT 'MAIN'
        COMMENT 'MAIN 主仓 / FEEDING 备料仓 / RETURN 退货仓',
    address VARCHAR(255) DEFAULT NULL COMMENT '仓库地址',
    manager_user_id BIGINT DEFAULT NULL COMMENT '仓库负责人用户 ID',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE 启用 / DISABLED 停用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号，预留乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_no (warehouse_no),
    KEY idx_warehouse_factory (factory_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '仓库档案，每厂至少一个主仓';

-- 物料档案：材料成本的基础数据；单价 4 位小数、损耗率 2 位小数。
CREATE TABLE IF NOT EXISTS t_material (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物料主键',
    material_code VARCHAR(64) NOT NULL COMMENT '物料编码，全公司唯一',
    material_name VARCHAR(128) NOT NULL COMMENT '物料名称',
    material_type VARCHAR(32) NOT NULL DEFAULT 'FABRIC'
        COMMENT 'FABRIC 布料 / CHEMICAL 化工 / ACCESSORY 辅料',
    spec VARCHAR(255) DEFAULT NULL COMMENT '规格，例如幅宽/克重/成分',
    unit VARCHAR(16) NOT NULL DEFAULT '米' COMMENT '计量单位：米/公斤/卷/件',
    unit_price DECIMAL(16, 4) NOT NULL DEFAULT 0.0000 COMMENT '采购单价：最多 4 位小数',
    loss_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '损耗率：百分比，2 位小数',
    safety_stock DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '安全库存',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE 启用 / DISABLED 停用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号，预留乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_material_code (material_code),
    KEY idx_material_type_status (material_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '物料档案，单价 4 位小数、损耗率 2 位小数';

-- 库存台账：按仓库 + 物料记录数量，只能由采购入库和调拨入库增加。
CREATE TABLE IF NOT EXISTS t_inventory (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '库存主键',
    warehouse_id BIGINT NOT NULL COMMENT '仓库 ID',
    factory_id BIGINT NOT NULL COMMENT '仓库所属工厂',
    material_id BIGINT NOT NULL COMMENT '物料 ID',
    quantity DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '当前库存数量',
    updated_at DATETIME(3) NOT NULL COMMENT '最后变动时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号，预留乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_warehouse_material (warehouse_id, material_id),
    KEY idx_inventory_factory (factory_id),
    KEY idx_inventory_material (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '库存台账，不允许负库存';

-- 采购入库单：材料成本与库存的唯一来源，确认收货后不可修改。
CREATE TABLE IF NOT EXISTS t_purchase_receipt (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '入库单主键',
    receipt_no VARCHAR(40) NOT NULL COMMENT '入库单号 PIN-...',
    factory_id BIGINT NOT NULL COMMENT '收货工厂',
    warehouse_id BIGINT NOT NULL COMMENT '收货仓库',
    supplier_name VARCHAR(200) NOT NULL COMMENT '供应商名称',
    receipt_date DATE NOT NULL COMMENT '收货日期',
    total_amount BIGINT NOT NULL DEFAULT 0 COMMENT '入库总金额：分',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT 草稿 / RECEIVED 已入库 / CANCELLED 已取消',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号，预留乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_receipt_no (receipt_no),
    KEY idx_purchase_receipt_factory_status (factory_id, status),
    KEY idx_purchase_receipt_warehouse (warehouse_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '采购入库单，库存增加的唯一来源';

-- 采购入库明细：金额 = 单价 × 数量，按分截断。
CREATE TABLE IF NOT EXISTS t_purchase_receipt_line (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细主键',
    receipt_id BIGINT NOT NULL COMMENT '入库单 ID',
    material_id BIGINT NOT NULL COMMENT '物料 ID',
    material_code VARCHAR(64) NOT NULL COMMENT '物料编码快照',
    material_name VARCHAR(128) NOT NULL COMMENT '物料名称快照',
    unit VARCHAR(16) NOT NULL COMMENT '单位快照',
    quantity DECIMAL(14, 2) NOT NULL COMMENT '入库数量，2 位小数',
    unit_price DECIMAL(16, 4) NOT NULL COMMENT '单价快照，4 位小数',
    amount BIGINT NOT NULL COMMENT '行金额：分，按分截断',
    loss_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '损耗率快照，2 位小数',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_purchase_receipt_line_receipt (receipt_id),
    KEY idx_purchase_receipt_line_material (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '采购入库明细';

-- 跨厂调拨单：出库扣减来源库存，入库增加目标库存。
CREATE TABLE IF NOT EXISTS t_stock_transfer (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '调拨单主键',
    transfer_no VARCHAR(40) NOT NULL COMMENT '调拨单号 TR-...',
    from_warehouse_id BIGINT NOT NULL COMMENT '调出仓库',
    from_factory_id BIGINT NOT NULL COMMENT '调出工厂',
    to_warehouse_id BIGINT NOT NULL COMMENT '调入仓库',
    to_factory_id BIGINT NOT NULL COMMENT '调入工厂',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING 待出库 / IN_TRANSIT 在途 / COMPLETED 已入库 / CANCELLED 已取消',
    planned_date DATE DEFAULT NULL COMMENT '计划调拨日期',
    shipped_at DATETIME(3) DEFAULT NULL COMMENT '出库时间',
    received_at DATETIME(3) DEFAULT NULL COMMENT '入库时间',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号，预留乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_transfer_no (transfer_no),
    KEY idx_stock_transfer_from (from_warehouse_id, status),
    KEY idx_stock_transfer_to (to_warehouse_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '跨厂调拨单';

-- 调拨明细：数量在出库时从来源仓库扣减，入库时在目标仓库增加。
CREATE TABLE IF NOT EXISTS t_stock_transfer_line (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细主键',
    transfer_id BIGINT NOT NULL COMMENT '调拨单 ID',
    material_id BIGINT NOT NULL COMMENT '物料 ID',
    material_code VARCHAR(64) NOT NULL COMMENT '物料编码快照',
    material_name VARCHAR(128) NOT NULL COMMENT '物料名称快照',
    unit VARCHAR(16) NOT NULL COMMENT '单位快照',
    quantity DECIMAL(14, 2) NOT NULL COMMENT '调拨数量，2 位小数',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_stock_transfer_line_transfer (transfer_id),
    KEY idx_stock_transfer_line_material (material_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '调拨明细';

-- 演示仓库：每个工厂一个主仓。
INSERT INTO t_warehouse
    (warehouse_no, warehouse_name, factory_id, warehouse_type, address, manager_user_id, status,
     remark, created_by, created_at, updated_by, updated_at, version)
SELECT 'WH-F001-01', '示范工厂主仓', f.id, 'MAIN', '示范工厂 A 区原料库', 3, 'ACTIVE',
       '演示仓库：一厂主仓', 1, NOW(3), 1, NOW(3), 0
FROM company_platform.sys_factory f
WHERE f.code = 'F001'
  AND NOT EXISTS (SELECT 1 FROM t_warehouse w WHERE w.warehouse_no = 'WH-F001-01');

INSERT INTO t_warehouse
    (warehouse_no, warehouse_name, factory_id, warehouse_type, address, manager_user_id, status,
     remark, created_by, created_at, updated_by, updated_at, version)
SELECT 'WH-F002-01', '第二工厂主仓', f.id, 'MAIN', '第二工厂 B 区原料库', 3, 'ACTIVE',
       '演示仓库：二厂主仓', 1, NOW(3), 1, NOW(3), 0
FROM company_platform.sys_factory f
WHERE f.code = 'F002'
  AND NOT EXISTS (SELECT 1 FROM t_warehouse w WHERE w.warehouse_no = 'WH-F002-01');

-- 演示物料：布料单价 4 位小数，辅料带损耗率。
INSERT INTO t_material
    (material_code, material_name, material_type, spec, unit, unit_price, loss_rate, safety_stock,
     status, remark, created_by, created_at, updated_by, updated_at, version)
SELECT demo.c, demo.n, demo.t, demo.s, demo.u, demo.p, demo.l, demo.ss, demo.st, demo.r,
       1, NOW(3), 1, NOW(3), 0
FROM (
    SELECT 'MAT-COTTON-128' AS c, '全棉坯布 128 支' AS n, 'FABRIC' AS t,
           '幅宽 1500mm / 128 支 / 100% 棉' AS s, '米' AS u, 18.5000 AS p, 2.50 AS l, 5000.00 AS ss,
           'ACTIVE' AS st, '演示物料：主用布料' AS r
    UNION ALL
    SELECT 'MAT-POLY-150', '涤纶坯布 150 支', 'FABRIC', '幅宽 1500mm / 150 支 / 涤纶', '米',
           12.8000, 3.00, 3000.00, 'ACTIVE', '演示物料：混纺坯布'
    UNION ALL
    SELECT 'MAT-INK-BLK', '数码印花黑墨', 'CHEMICAL', '1L / 瓶', '公斤',
           45.6000, 1.00, 200.00, 'ACTIVE', '演示物料：耗材'
    UNION ALL
    SELECT 'MAT-BELT-STD', '标准输送带', 'ACCESSORY', '宽度 800mm', '米',
           96.0000, 0.50, 100.00, 'ACTIVE', '演示物料：设备辅料'
) AS demo
WHERE NOT EXISTS (SELECT 1 FROM t_material m WHERE m.material_code = demo.c);

-- 演示采购入库单：已入库并生成初始库存，便于直接体验库存与调拨。
INSERT INTO t_purchase_receipt
    (receipt_no, factory_id, warehouse_id, supplier_name, receipt_date, total_amount, status,
     remark, created_by, created_at, updated_by, updated_at, version)
SELECT 'PIN-DEMO-0001', w.factory_id, w.id, '演示供应商：华纺原料行', CURDATE(), 0, 'RECEIVED',
       '演示采购入库单：材料成本的唯一来源', 1, NOW(3), 1, NOW(3), 0
FROM t_warehouse w
WHERE w.warehouse_no = 'WH-F001-01'
  AND NOT EXISTS (SELECT 1 FROM t_purchase_receipt r WHERE r.receipt_no = 'PIN-DEMO-0001');

INSERT INTO t_purchase_receipt_line
    (receipt_id, material_id, material_code, material_name, unit, quantity, unit_price, amount, loss_rate, remark)
SELECT r.id, m.id, m.material_code, m.material_name, m.unit,
       CASE m.material_code WHEN 'MAT-COTTON-128' THEN 8000.00
                            WHEN 'MAT-POLY-150' THEN 5000.00
                            WHEN 'MAT-INK-BLK' THEN 120.00
                            ELSE 60.00 END,
       m.unit_price,
       FLOOR(CASE m.material_code WHEN 'MAT-COTTON-128' THEN 8000.00
                                  WHEN 'MAT-POLY-150' THEN 5000.00
                                  WHEN 'MAT-INK-BLK' THEN 120.00
                                  ELSE 60.00 END * m.unit_price * 100),
       m.loss_rate, '演示入库明细'
FROM t_purchase_receipt r
JOIN t_warehouse w ON w.id = r.warehouse_id
JOIN t_material m ON m.material_code IN ('MAT-COTTON-128', 'MAT-POLY-150', 'MAT-INK-BLK', 'MAT-BELT-STD')
WHERE r.receipt_no = 'PIN-DEMO-0001'
  AND NOT EXISTS (SELECT 1 FROM t_purchase_receipt_line l WHERE l.receipt_id = r.id);

UPDATE t_purchase_receipt r
SET r.total_amount = (SELECT COALESCE(SUM(l.amount), 0) FROM t_purchase_receipt_line l WHERE l.receipt_id = r.id)
WHERE r.receipt_no = 'PIN-DEMO-0001';

-- 演示库存：由上面的采购入库单生成，与“采购入库是唯一来源”保持一致。
INSERT INTO t_inventory (warehouse_id, factory_id, material_id, quantity, updated_at, version)
SELECT r.warehouse_id, r.factory_id, l.material_id, l.quantity, NOW(3), 0
FROM t_purchase_receipt_line l
JOIN t_purchase_receipt r ON r.id = l.receipt_id
WHERE r.receipt_no = 'PIN-DEMO-0001'
  AND NOT EXISTS (
      SELECT 1 FROM t_inventory i WHERE i.warehouse_id = r.warehouse_id AND i.material_id = l.material_id
  );
