-- 设备台账、工单排产与设备 FIFO 队列（company_order0，MySQL 9.x）
-- 业务基线：V1.4 第 6.2、6.4、6.6、7.7 节。
-- 1. 设备只归属一个工厂，设备不绑定客户；订单只能在同一工厂的设备池内排产。
-- 2. 一台设备同一时间只允许一个工单处于生产状态；其余工单按 queue_seq 从小到大 FIFO。
-- 3. 插队必须由销售申请、厂长审批；审批通过后重排队列并给被顺延工单的业务员发站内消息。
-- 4. 工单、队列、插队申请、设备状态日志均保留历史，不做物理删除。
-- 5. 本脚本只操作 company_order0（3306），与分片订单库同实例，便于排产读取订单。
SET NAMES utf8mb4;

USE company_order0;

-- 设备台账。device_no 全公司唯一；能力字段用逗号分隔，留空表示“不限”。
CREATE TABLE IF NOT EXISTS t_device (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '设备主键',
    device_no VARCHAR(64) NOT NULL COMMENT '设备编号，全公司唯一',
    device_name VARCHAR(128) NOT NULL COMMENT '设备名称',
    device_type VARCHAR(64) NOT NULL COMMENT '设备类型，如印花机/定型机/染色机',
    model VARCHAR(128) DEFAULT NULL COMMENT '型号',
    factory_id BIGINT NOT NULL COMMENT '所属工厂，不允许为空',
    workshop VARCHAR(128) DEFAULT NULL COMMENT '车间/产线',
    location VARCHAR(255) DEFAULT NULL COMMENT '详细位置',
    status VARCHAR(32) NOT NULL DEFAULT 'IDLE'
        COMMENT 'IDLE 空闲 / RUNNING 生产中 / MAINTENANCE 保养 / FAULT 故障 / DISABLED 停用',
    material_types VARCHAR(500) DEFAULT NULL COMMENT '可处理材料，逗号分隔；空=不限',
    min_width_mm INT DEFAULT NULL COMMENT '最小门幅（毫米）',
    max_width_mm INT DEFAULT NULL COMMENT '最大门幅（毫米）',
    process_types VARCHAR(500) DEFAULT NULL COMMENT '可处理工艺，逗号分隔；空=不限',
    capacity_meters_per_hour DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '产能：米/小时',
    changeover_minutes INT NOT NULL DEFAULT 0 COMMENT '换型时间：分钟',
    yield_rate DECIMAL(5, 2) DEFAULT NULL COMMENT '良率百分比 0-100',
    operator_requirement VARCHAR(255) DEFAULT NULL COMMENT '操作员资质要求',
    current_work_order_id BIGINT DEFAULT NULL COMMENT '当前生产工单 ID，最多一个',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号，预留乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_no (device_no),
    KEY idx_device_factory_status (factory_id, status),
    KEY idx_device_current_work_order (current_work_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备台账，设备只归属工厂不绑定客户';

-- 设备状态变更日志：设备状态变化、订单绑定/解绑全程留痕。
CREATE TABLE IF NOT EXISTS t_device_status_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志主键',
    device_id BIGINT NOT NULL COMMENT '设备 ID',
    from_status VARCHAR(32) DEFAULT NULL COMMENT '变更前状态',
    to_status VARCHAR(32) NOT NULL COMMENT '变更后状态',
    reason VARCHAR(500) DEFAULT NULL COMMENT '变更原因',
    work_order_id BIGINT DEFAULT NULL COMMENT '关联工单，可为空',
    changed_by BIGINT DEFAULT NULL COMMENT '操作人',
    changed_at DATETIME(3) NOT NULL COMMENT '变更时间',
    PRIMARY KEY (id),
    KEY idx_device_status_log_device (device_id, changed_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备状态变更日志';

-- 生产工单：一个订单可拆成多个工单，分别分配到同工厂的不同设备。
CREATE TABLE IF NOT EXISTS t_work_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工单主键',
    work_order_no VARCHAR(40) NOT NULL COMMENT '工单号 WO-...',
    order_no BIGINT NOT NULL COMMENT '订单号，订单与工单必须属于同一工厂',
    customer_id BIGINT NOT NULL COMMENT '客户 ID，下单时冗余便于展示',
    sales_user_id BIGINT DEFAULT NULL COMMENT '订单业务员用户 ID，用于插队消息通知',
    factory_id BIGINT NOT NULL COMMENT '生产工厂',
    device_id BIGINT DEFAULT NULL COMMENT '分配设备；待排产时为空',
    material_type VARCHAR(64) NOT NULL COMMENT '材料/布料',
    process_type VARCHAR(64) NOT NULL COMMENT '工艺/染料',
    fabric_width_mm INT NOT NULL COMMENT '门幅（毫米）',
    planned_meters DECIMAL(12, 2) NOT NULL COMMENT '计划生产长度（米）',
    planned_start DATETIME(3) DEFAULT NULL COMMENT '计划开始时间',
    planned_end DATETIME(3) DEFAULT NULL COMMENT '计划完成时间',
    actual_start DATETIME(3) DEFAULT NULL COMMENT '实际开始时间',
    actual_end DATETIME(3) DEFAULT NULL COMMENT '实际完成时间',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING 待排产 / QUEUED 排队中 / RUNNING 生产中 / COMPLETED 已完成 / CANCELLED 已取消',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_by BIGINT DEFAULT NULL COMMENT '最后修改人',
    updated_at DATETIME(3) NOT NULL COMMENT '最后修改时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号，预留乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_work_order_no (work_order_no),
    KEY idx_work_order_order (order_no),
    KEY idx_work_order_factory_status (factory_id, status),
    KEY idx_work_order_device (device_id),
    KEY idx_work_order_sales (sales_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '生产工单，可在同工厂拆分工单';

-- 设备队列：每个工单最多一条队列记录；queue_seq 越小越先生产。
CREATE TABLE IF NOT EXISTS t_device_queue (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '队列主键',
    device_id BIGINT NOT NULL COMMENT '设备 ID',
    work_order_id BIGINT NOT NULL COMMENT '工单 ID',
    queue_seq BIGINT NOT NULL COMMENT '队列序号，越小越靠前',
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING'
        COMMENT 'WAITING 等待 / RUNNING 生产中 / DONE 已完成 / CANCELLED 已取消',
    enqueued_at DATETIME(3) NOT NULL COMMENT '进入队列时间，FIFO 依据',
    planned_start DATETIME(3) DEFAULT NULL COMMENT '预计开始时间',
    planned_end DATETIME(3) DEFAULT NULL COMMENT '预计完成时间',
    original_seq BIGINT DEFAULT NULL COMMENT '插队前原队列序号，审计用',
    jump_request_id BIGINT DEFAULT NULL COMMENT '生效的插队申请 ID',
    jump_reason VARCHAR(500) DEFAULT NULL COMMENT '插队原因快照',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_queue_work_order (work_order_id),
    KEY idx_device_queue_device_status (device_id, status, queue_seq)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备队列，默认按入队时间 FIFO';

-- 插队申请：销售发起，厂长审批；审批中心上线前使用独立审批接口。
CREATE TABLE IF NOT EXISTS t_queue_jump_request (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '申请主键',
    request_no VARCHAR(40) NOT NULL COMMENT '申请单号 QJ-...',
    work_order_id BIGINT NOT NULL COMMENT '申请插队的工单',
    device_id BIGINT NOT NULL COMMENT '目标设备',
    factory_id BIGINT NOT NULL COMMENT '工厂，审批数据范围',
    requested_by BIGINT NOT NULL COMMENT '申请人（销售）',
    target_position INT NOT NULL COMMENT '希望插入的队列位置，1 表示队首',
    reason VARCHAR(500) NOT NULL COMMENT '插队原因，必填',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已撤销',
    approved_by BIGINT DEFAULT NULL COMMENT '审批人',
    approved_at DATETIME(3) DEFAULT NULL COMMENT '审批时间',
    approval_remark VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
    displaced_count INT NOT NULL DEFAULT 0 COMMENT '被顺延工单数量',
    created_at DATETIME(3) NOT NULL COMMENT '申请时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_queue_jump_request_no (request_no),
    KEY idx_queue_jump_factory_status (factory_id, status),
    KEY idx_queue_jump_work_order (work_order_id),
    KEY idx_queue_jump_requester (requested_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备队列插队申请与审批记录';

-- 站内消息：插队通过后通知被顺延工单的业务员；审批结果通知申请人。
CREATE TABLE IF NOT EXISTS t_station_message (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息主键',
    user_id BIGINT NOT NULL COMMENT '接收人用户 ID',
    message_type VARCHAR(32) NOT NULL
        COMMENT 'QUEUE_DISPLACED 队列顺延 / QUEUE_JUMP_RESULT 插队结果 / SYSTEM 系统消息',
    title VARCHAR(200) NOT NULL COMMENT '消息标题',
    content VARCHAR(1000) NOT NULL COMMENT '消息内容',
    biz_type VARCHAR(32) DEFAULT NULL COMMENT '业务类型：WORK_ORDER/ORDER/QUEUE_JUMP',
    biz_id VARCHAR(64) DEFAULT NULL COMMENT '业务主键，字符串兼容雪花号',
    link_path VARCHAR(255) DEFAULT NULL COMMENT '前端跳转路径',
    read_flag TINYINT NOT NULL DEFAULT 0 COMMENT '0 未读 / 1 已读',
    created_at DATETIME(3) NOT NULL COMMENT '发送时间',
    read_at DATETIME(3) DEFAULT NULL COMMENT '阅读时间',
    PRIMARY KEY (id),
    KEY idx_station_message_user_read (user_id, read_flag, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内消息，当前用于插队通知';

-- 演示设备：按工厂编码查找主库中的工厂 ID；重复执行不会产生重复设备。
-- 能力字段留空表示不限，方便在开发环境快速排产；正式设备应补全能力参数。
INSERT IGNORE INTO t_device
    (device_no, device_name, device_type, model, factory_id, workshop, location, status,
     material_types, min_width_mm, max_width_mm, process_types, capacity_meters_per_hour,
     changeover_minutes, yield_rate, operator_requirement, remark, created_by, created_at,
     updated_by, updated_at, version)
SELECT 'EQ-F001-01', '数码印花一号机', '数码印花机', 'DEMO-1800', f.id, '印花车间', 'A 区 01 工位',
       'IDLE', 'COTTON,POLYESTER', 800, 1800, 'PRINT,DIGITAL', 120.00, 20, 96.50,
       '印花操作证', '演示设备：一厂数码印花', 1, NOW(3), 1, NOW(3), 0
FROM company_platform.sys_factory f
WHERE f.code = 'F001';

INSERT IGNORE INTO t_device
    (device_no, device_name, device_type, model, factory_id, workshop, location, status,
     material_types, min_width_mm, max_width_mm, process_types, capacity_meters_per_hour,
     changeover_minutes, yield_rate, operator_requirement, remark, created_by, created_at,
     updated_by, updated_at, version)
SELECT 'EQ-F001-02', '定型二号机', '定型机', 'DEMO-2200', f.id, '后整理车间', 'B 区 02 工位',
       'IDLE', 'COTTON,POLYESTER,BLEND', 1000, 2200, 'FINISH,HEAT_SET', 200.00, 30, 97.00,
       '定型操作证', '演示设备：一厂定型', 1, NOW(3), 1, NOW(3), 0
FROM company_platform.sys_factory f
WHERE f.code = 'F001';

INSERT IGNORE INTO t_device
    (device_no, device_name, device_type, model, factory_id, workshop, location, status,
     material_types, min_width_mm, max_width_mm, process_types, capacity_meters_per_hour,
     changeover_minutes, yield_rate, operator_requirement, remark, created_by, created_at,
     updated_by, updated_at, version)
SELECT 'EQ-F002-01', '二厂染色一号机', '染色机', 'DEMO-1400', f.id, '染色车间', 'C 区 01 工位',
       'IDLE', 'COTTON,BLEND', 600, 1400, 'DYE', 80.00, 40, 94.00,
       '染色操作证', '演示设备：二厂染色', 1, NOW(3), 1, NOW(3), 0
FROM company_platform.sys_factory f
WHERE f.code = 'F002';
