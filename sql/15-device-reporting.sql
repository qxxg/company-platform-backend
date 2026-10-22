-- 设备数据上报：接入凭据、订单作业数据、订单图片、未匹配暂存与接入审计（company_order0）
-- 业务基线：V1.4 第 6.8 节与《设备接入配置模板与报文示例-v1.0》。
-- 1. 设备上报分两类：订单作业数据落 MySQL（按 order_no 唯一，后推覆盖前推）；
--    自检/异常/心跳落 Apache IoTDB（时序），MySQL 只留接入审计。
-- 2. 订单图片按 order_no 唯一：一条订单只接受一次图片，重复上传按 4003 忽略。
-- 3. 订单号未匹配（OCR 未完成或识别失败）的数据先落暂存表，匹配成功后归位。
-- 4. 设备一机一密：密钥只保存在服务端，设备侧不保存业务权限。
SET NAMES utf8mb4;

USE company_order0;

-- 设备接入凭据：一机一密，用于报文 HMAC-SHA256 验签。
CREATE TABLE IF NOT EXISTS t_device_credential (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_no VARCHAR(64) NOT NULL COMMENT '设备编号',
    secret VARCHAR(128) NOT NULL COMMENT '设备一机一密密钥',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_credential_no (device_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备接入凭据（一机一密）';

-- 订单作业数据：按订单号唯一，最新一次覆盖历史（每 10 秒一次）。
CREATE TABLE IF NOT EXISTS t_device_order_report (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no BIGINT NOT NULL COMMENT '订单号，精确匹配的唯一键',
    device_no VARCHAR(64) NOT NULL COMMENT '上报设备编号',
    work_order_no VARCHAR(40) DEFAULT NULL COMMENT '工单号',
    report_status VARCHAR(32) DEFAULT NULL COMMENT '设备侧状态：idle/running/paused/finished',
    printed_length_meter DECIMAL(14, 3) DEFAULT NULL COMMENT '累计已打印长度：米，3 位小数',
    progress_meter DECIMAL(14, 3) DEFAULT NULL COMMENT '订单累计完成长度：米，3 位小数',
    progress_percent DECIMAL(6, 2) DEFAULT NULL COMMENT '完成百分比',
    ink_usage_ml DECIMAL(14, 3) DEFAULT NULL COMMENT '累计耗墨量：毫升',
    ink_delta_ml DECIMAL(14, 3) DEFAULT NULL COMMENT '本次增量耗墨量：毫升',
    remaining_minutes INT DEFAULT NULL COMMENT '预估剩余分钟',
    speed_meter_per_hour DECIMAL(14, 3) DEFAULT NULL COMMENT '当前速度：米/小时',
    width_meter DECIMAL(10, 3) DEFAULT NULL COMMENT '当前门幅：米',
    pass_count INT DEFAULT NULL COMMENT '已打印遍数',
    device_state VARCHAR(64) DEFAULT NULL COMMENT '设备运行状态快照',
    error_code VARCHAR(64) DEFAULT NULL COMMENT '当前异常码',
    image_uploaded TINYINT NOT NULL DEFAULT 0 COMMENT '本订单图片是否已上传',
    reported_at DATETIME(3) NOT NULL COMMENT '设备上报时间',
    device_timestamp BIGINT DEFAULT NULL COMMENT '设备时间戳：毫秒',
    device_seq BIGINT DEFAULT NULL COMMENT '设备内单调递增序号',
    created_at DATETIME(3) NOT NULL COMMENT '首次接收时间',
    updated_at DATETIME(3) NOT NULL COMMENT '最近覆盖时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_order_report_order (order_no),
    KEY idx_device_order_report_device (device_no),
    KEY idx_device_order_report_reported (reported_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单作业数据，按订单号覆盖';

-- 订单图片：按订单号唯一，一条订单只接受一次。
CREATE TABLE IF NOT EXISTS t_device_image (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    image_id VARCHAR(64) NOT NULL COMMENT '图片编号',
    order_no BIGINT NOT NULL COMMENT '订单号，OCR 精确匹配后写入',
    device_no VARCHAR(64) NOT NULL COMMENT '上报设备编号',
    file_name VARCHAR(255) DEFAULT NULL COMMENT '文件名',
    format VARCHAR(16) DEFAULT NULL COMMENT '图片格式 jpg/png',
    size_bytes BIGINT DEFAULT NULL COMMENT '文件大小：字节',
    width INT DEFAULT NULL COMMENT '像素宽',
    height INT DEFAULT NULL COMMENT '像素高',
    content_hash VARCHAR(128) DEFAULT NULL COMMENT '内容哈希，用于去重',
    ocr_text VARCHAR(64) DEFAULT NULL COMMENT 'OCR 识别到的订单号',
    ocr_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING 待识别 / MATCHED 已匹配 / UNMATCHED 未匹配 / MANUAL 人工处理',
    matched TINYINT NOT NULL DEFAULT 0 COMMENT '是否已匹配订单',
    uploaded_at DATETIME(3) NOT NULL COMMENT '接收时间',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_image_order (order_no),
    UNIQUE KEY uk_device_image_id (image_id),
    KEY idx_device_image_device (device_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单图片，一条订单仅一次';

-- 未匹配暂存：订单号无法精确匹配时的数据暂存，匹配成功后归位。
CREATE TABLE IF NOT EXISTS t_device_report_staging (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_no VARCHAR(64) NOT NULL COMMENT '上报设备编号',
    raw_order_no VARCHAR(64) NOT NULL COMMENT '原始订单号文本（未精确匹配）',
    msg_type VARCHAR(32) NOT NULL COMMENT '报文类型',
    payload_json TEXT NOT NULL COMMENT '原始报文内容',
    device_timestamp BIGINT DEFAULT NULL COMMENT '设备时间戳：毫秒',
    device_seq BIGINT DEFAULT NULL COMMENT '设备内单调递增序号',
    received_at DATETIME(3) NOT NULL COMMENT '接收时间',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_device_report_staging_order (raw_order_no),
    KEY idx_device_report_staging_device (device_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单号未匹配的暂存数据';

-- 接入审计：记录每条上行报文的处理结果，便于设备联调与排障。
CREATE TABLE IF NOT EXISTS t_device_access_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    device_no VARCHAR(64) DEFAULT NULL COMMENT '设备编号',
    msg_type VARCHAR(32) DEFAULT NULL COMMENT '报文类型',
    transport VARCHAR(16) DEFAULT NULL COMMENT '接入方式：MQTT/COAP/HTTP',
    device_seq BIGINT DEFAULT NULL COMMENT '设备序号',
    result_code INT DEFAULT NULL COMMENT '回执码：0 成功，1001 格式错误等',
    result_message VARCHAR(255) DEFAULT NULL COMMENT '结果说明',
    matched TINYINT NOT NULL DEFAULT 0 COMMENT '订单是否匹配成功',
    created_at DATETIME(3) NOT NULL COMMENT '接收时间',
    PRIMARY KEY (id),
    KEY idx_device_access_log_device (device_no, created_at),
    KEY idx_device_access_log_code (result_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备上行接入审计';

-- 演示设备凭据：便于联调时直接使用（生产环境请通过运维下发一机一密密钥）。
INSERT INTO t_device_credential (device_no, secret, enabled, remark, created_at, updated_at)
SELECT 'EQ-F001-01', 'demo-secret-EQ-F001-01', 1, '演示凭据：一机一密', NOW(3), NOW(3)
WHERE NOT EXISTS (SELECT 1 FROM t_device_credential c WHERE c.device_no = 'EQ-F001-01');
