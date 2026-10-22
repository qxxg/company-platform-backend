-- 设备维护工单与点检保养记录（company_order0，MySQL 9.x）
-- 业务基线：V1.4 设备管理模块（设备维护、保养/点检、维保单据）。
-- 1. 维护工单是设备停机保养的唯一业务凭据：开始维护设备转 MAINTENANCE/FAULT，
--    等待队列保留并通知受影响业务员；完成后设备恢复 IDLE 并重算队列计划时间。
-- 2. 维护费用全部由公司承担，只做成本记录，不向客户收取，也不计算设备使用费；
--    金额以分存储，与财务模块口径一致。
-- 3. 点检记录用于登记日常/周/月点检结果；结果异常时可直接生成维护工单。
-- 4. 工单与点检记录只保留历史，不做物理删除；取消只标记状态。
-- 5. 本脚本只操作 company_order0（3306），与设备台账、排产表同库。
SET NAMES utf8mb4;

USE company_order0;

-- 设备维护工单：一次停机保养或故障维修的业务凭据。
CREATE TABLE IF NOT EXISTS t_maintenance_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '维护工单主键',
    maintenance_no VARCHAR(40) NOT NULL COMMENT '维护工单号 MT-...',
    device_id BIGINT NOT NULL COMMENT '设备 ID',
    device_no VARCHAR(64) NOT NULL COMMENT '设备编号快照',
    factory_id BIGINT NOT NULL COMMENT '设备所属工厂，维护数据范围',
    maintenance_type VARCHAR(32) NOT NULL DEFAULT 'PREVENTIVE'
        COMMENT 'PREVENTIVE 预防性保养 / REPAIR 故障维修 / INSPECTION 点检整改',
    title VARCHAR(200) NOT NULL COMMENT '维护主题',
    description VARCHAR(1000) DEFAULT NULL COMMENT '问题描述与处理方案',
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL'
        COMMENT 'LOW 低 / NORMAL 普通 / HIGH 高 / URGENT 紧急',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING 待处理 / IN_PROGRESS 执行中 / COMPLETED 已完成 / CANCELLED 已取消',
    planned_start DATETIME(3) DEFAULT NULL COMMENT '计划开始时间',
    planned_end DATETIME(3) DEFAULT NULL COMMENT '计划结束时间',
    actual_start DATETIME(3) DEFAULT NULL COMMENT '实际开始时间',
    actual_end DATETIME(3) DEFAULT NULL COMMENT '实际结束时间',
    downtime_minutes INT NOT NULL DEFAULT 0 COMMENT '停机时长：分钟',
    cost_amount BIGINT NOT NULL DEFAULT 0 COMMENT '维护成本：分，公司承担',
    cost_note VARCHAR(500) DEFAULT NULL COMMENT '成本说明（外协、备件、人工等）',
    handler_user_id BIGINT DEFAULT NULL COMMENT '执行人用户 ID',
    handler_name VARCHAR(64) DEFAULT NULL COMMENT '执行人姓名快照',
    inspection_id BIGINT DEFAULT NULL COMMENT '来源点检记录 ID，可为空',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号，预留乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_maintenance_no (maintenance_no),
    KEY idx_maintenance_device (device_id, status),
    KEY idx_maintenance_factory_status (factory_id, status),
    KEY idx_maintenance_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备维护工单，费用由公司承担';

-- 点检保养记录：操作员按日/周/月登记点检结果，异常可生成维护工单。
CREATE TABLE IF NOT EXISTS t_inspection_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '点检记录主键',
    device_id BIGINT NOT NULL COMMENT '设备 ID',
    device_no VARCHAR(64) NOT NULL COMMENT '设备编号快照',
    factory_id BIGINT NOT NULL COMMENT '设备所属工厂',
    plan_type VARCHAR(16) NOT NULL DEFAULT 'DAILY'
        COMMENT 'DAILY 日常点检 / WEEKLY 周点检 / MONTHLY 月度保养检查',
    inspection_date DATE NOT NULL COMMENT '点检日期',
    result VARCHAR(16) NOT NULL DEFAULT 'NORMAL'
        COMMENT 'NORMAL 正常 / ABNORMAL 异常',
    item_count INT NOT NULL DEFAULT 0 COMMENT '点检项数',
    abnormal_count INT NOT NULL DEFAULT 0 COMMENT '异常项数',
    content VARCHAR(1000) DEFAULT NULL COMMENT '点检内容与异常描述',
    handler_user_id BIGINT DEFAULT NULL COMMENT '点检人用户 ID',
    handler_name VARCHAR(64) DEFAULT NULL COMMENT '点检人姓名快照',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号，预留乐观锁',
    PRIMARY KEY (id),
    KEY idx_inspection_device_date (device_id, inspection_date),
    KEY idx_inspection_factory_date (factory_id, inspection_date),
    KEY idx_inspection_result (result)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备点检保养记录';

-- 演示点检记录：两台一厂设备各一条，便于直接体验点检→生成维护工单的流程。
INSERT INTO t_inspection_record
    (device_id, device_no, factory_id, plan_type, inspection_date, result,
     item_count, abnormal_count, content, handler_user_id, handler_name,
     created_by, created_at, updated_by, updated_at, version)
SELECT d.id, d.device_no, d.factory_id, 'DAILY', CURDATE(), 'NORMAL', 6, 0,
       '演示点检：油路、传感器、急停与皮带均正常', 3, '厂长一号', 3, NOW(3), 3, NOW(3), 0
FROM t_device d
WHERE d.device_no = 'EQ-F001-01'
  AND NOT EXISTS (SELECT 1 FROM t_inspection_record r WHERE r.device_id = d.id);

INSERT INTO t_inspection_record
    (device_id, device_no, factory_id, plan_type, inspection_date, result,
     item_count, abnormal_count, content, handler_user_id, handler_name,
     created_by, created_at, updated_by, updated_at, version)
SELECT d.id, d.device_no, d.factory_id, 'WEEKLY', CURDATE(), 'ABNORMAL', 6, 1,
       '演示点检：定型温度传感器存在漂移，需安排检修', 3, '厂长一号', 3, NOW(3), 3, NOW(3), 0
FROM t_device d
WHERE d.device_no = 'EQ-F001-02'
  AND NOT EXISTS (SELECT 1 FROM t_inspection_record r WHERE r.device_id = d.id);
