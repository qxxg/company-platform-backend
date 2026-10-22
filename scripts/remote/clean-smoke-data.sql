-- 清理冒烟测试与人工验收写入的测试数据（仅开发环境使用）。
-- 财务：冒烟测试会写入 1 分应收、收款单、核销明细、手工发票和客户对账单，备注均为 smoke test 开头。
-- 排产：冒烟测试与人工验收会写入工单、设备队列、插队申请、站内消息和临时设备，
--       工单备注以 smoke test work order 或 验收工单 开头，设备编号以 SMOKE-EQ- 开头。
-- 演示数据（备注以“演示”开头）不受影响。
SET NAMES utf8mb4;

USE company_platform;

-- AI 助手对话审计属于冒烟运行痕迹，测试前清空；正式环境不要执行本脚本。
DELETE FROM ai_chat_log;

DELETE FROM biz_receipt_allocation
    WHERE receipt_id IN (SELECT id FROM biz_receipt WHERE remark LIKE 'smoke test%');

DELETE FROM biz_receipt WHERE remark LIKE 'smoke test%';

DELETE FROM biz_receivable WHERE remark LIKE 'smoke test%';

DELETE FROM biz_statement_line
    WHERE statement_id IN (SELECT id FROM biz_statement WHERE remark LIKE 'smoke test%');

DELETE FROM biz_statement WHERE remark LIKE 'smoke test%';

DELETE FROM biz_invoice WHERE remark LIKE 'smoke test%';

-- 费用报销/借款的冒烟数据：审批实例以业务主键关联，先按业务表定位删除审批轨迹与任务，
-- 再删实例和业务单据；顺序不能颠倒，否则业务单据删除后无法再反查审批实例。
DELETE FROM approval_record
    WHERE instance_id IN (SELECT id FROM approval_instance
        WHERE (business_type = 'EXPENSE' AND business_id IN (
                  SELECT CAST(id AS CHAR) FROM biz_expense_claim
                  WHERE description LIKE 'smoke test%' OR description LIKE '%验收%'))
           OR (business_type = 'LOAN' AND business_id IN (
                  SELECT CAST(id AS CHAR) FROM biz_loan
                  WHERE purpose LIKE 'smoke test%' OR purpose LIKE '%验收%')));

DELETE FROM approval_task
    WHERE instance_id IN (SELECT id FROM approval_instance
        WHERE (business_type = 'EXPENSE' AND business_id IN (
                  SELECT CAST(id AS CHAR) FROM biz_expense_claim
                  WHERE description LIKE 'smoke test%' OR description LIKE '%验收%'))
           OR (business_type = 'LOAN' AND business_id IN (
                  SELECT CAST(id AS CHAR) FROM biz_loan
                  WHERE purpose LIKE 'smoke test%' OR purpose LIKE '%验收%')));

DELETE FROM approval_instance
    WHERE (business_type = 'EXPENSE' AND business_id IN (
              SELECT CAST(id AS CHAR) FROM biz_expense_claim
              WHERE description LIKE 'smoke test%' OR description LIKE '%验收%'))
       OR (business_type = 'LOAN' AND business_id IN (
              SELECT CAST(id AS CHAR) FROM biz_loan
              WHERE purpose LIKE 'smoke test%' OR purpose LIKE '%验收%'));

-- 还款记录引用报销单（冲销）和借款单，先删记录，再删借款单/报销单。
DELETE FROM biz_loan_repayment
    WHERE remark LIKE 'smoke test%'
       OR expense_claim_id IN (SELECT id FROM biz_expense_claim
           WHERE description LIKE 'smoke test%' OR description LIKE '%验收%')
       OR loan_id IN (SELECT id FROM biz_loan
           WHERE purpose LIKE 'smoke test%' OR purpose LIKE '%验收%');

DELETE FROM biz_loan WHERE purpose LIKE 'smoke test%' OR purpose LIKE '%验收%';

DELETE FROM biz_expense_claim WHERE description LIKE 'smoke test%' OR description LIKE '%验收%';

-- 审批中心的冒烟/验收数据：按申请标题定位，先删轨迹与任务再删实例。
DELETE FROM approval_record
    WHERE instance_id IN (SELECT id FROM approval_instance WHERE title LIKE 'smoke test%'
        OR title LIKE 'debug%' OR title LIKE '%验收%' OR title LIKE '客户 #% 信用额度调整%'
        OR title LIKE '设备队列插队申请%');

DELETE FROM approval_task
    WHERE instance_id IN (SELECT id FROM approval_instance WHERE title LIKE 'smoke test%'
        OR title LIKE 'debug%' OR title LIKE '%验收%' OR title LIKE '客户 #% 信用额度调整%'
        OR title LIKE '设备队列插队申请%');

DELETE FROM approval_instance WHERE title LIKE 'smoke test%' OR title LIKE 'debug%'
    OR title LIKE '%验收%' OR title LIKE '客户 #% 信用额度调整%'
    OR title LIKE '设备队列插队申请%';

-- 恢复演示客户的信用额度基线（审批测试会临时改额度，这里还原为种子值）。
UPDATE biz_customer SET credit_limit = 100000000 WHERE id = 9001;

-- 生产排产测试数据：工单/队列/申请/消息/设备。先删子记录再删工单和设备。
USE company_order0;

-- 1) 设备队列：按工单备注定位；已删除工单留下的孤儿队列一并清理。
DELETE FROM t_device_queue
    WHERE work_order_id IN (
        SELECT id FROM t_work_order
        WHERE remark LIKE 'smoke test work order%' OR remark LIKE '%验收工单%' OR remark LIKE 'smoke test%'
    )
       OR work_order_id NOT IN (SELECT id FROM t_work_order);

-- 2) 站内消息：按关联工单号或插队申请 ID 定位；关联对象已删除的孤儿消息一并清理。
DELETE FROM t_station_message
    WHERE content LIKE '%smoke test%'
       OR biz_id IN (
           SELECT CAST(id AS CHAR) FROM t_work_order
           WHERE remark LIKE 'smoke test work order%' OR remark LIKE '%验收工单%' OR remark LIKE 'smoke test%'
       )
       OR (biz_type = 'QUEUE_JUMP' AND biz_id IN (
           SELECT CAST(id AS CHAR) FROM t_queue_jump_request
           WHERE reason LIKE 'smoke test%' OR reason LIKE '%加急%'
       ))
       OR (biz_type = 'WORK_ORDER' AND biz_id NOT IN (SELECT CAST(id AS CHAR) FROM t_work_order));

-- 3) 插队申请：按原因或关联工单定位。
DELETE FROM t_queue_jump_request
    WHERE reason LIKE 'smoke test%'
       OR reason LIKE '%加急%'
       OR work_order_id IN (
           SELECT id FROM t_work_order
           WHERE remark LIKE 'smoke test work order%' OR remark LIKE '%验收工单%'
       );

-- 4) 工单本体。
DELETE FROM t_work_order
    WHERE remark LIKE 'smoke test work order%' OR remark LIKE '%验收工单%'
       OR remark LIKE 'smoke test%';

-- 5) 维保数据：维护工单与点检记录。工单可能由异常点检生成，因此先删工单再删点检。
DELETE FROM t_maintenance_order
    WHERE title LIKE 'smoke test%' OR description LIKE 'smoke test%' OR remark LIKE 'smoke test%'
       OR inspection_id IN (SELECT id FROM t_inspection_record WHERE content LIKE 'smoke test%');

DELETE FROM t_inspection_record WHERE content LIKE 'smoke test%';

-- 6) 维保产生的停机/恢复消息（关联工单已删除时属于孤儿消息）同样清理。
DELETE FROM t_station_message WHERE content LIKE '%维护工单 MT-%';

-- 7) 仓储测试数据：采购入库单、调拨单和随之产生的库存变动。
--    先删单据头（带明细），再把库存数量回退到演示入库单产生的基线。
DELETE FROM t_purchase_receipt_line
    WHERE receipt_id IN (SELECT id FROM t_purchase_receipt
        WHERE remark LIKE 'smoke test%' OR supplier_name LIKE 'smoke test%'
           OR remark LIKE '%验收%' OR supplier_name LIKE '%验收%');

DELETE FROM t_purchase_receipt
    WHERE remark LIKE 'smoke test%' OR supplier_name LIKE 'smoke test%'
       OR remark LIKE '%验收%' OR supplier_name LIKE '%验收%';

DELETE FROM t_stock_transfer_line
    WHERE transfer_id IN (SELECT id FROM t_stock_transfer
        WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%');

DELETE FROM t_stock_transfer
    WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%';

-- 8) 工单领料测试数据：先删明细再删单头，库存数量随下面的回退一起还原。
DELETE FROM t_material_issue_line
    WHERE issue_id IN (SELECT id FROM t_material_issue
        WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%');

DELETE FROM t_material_issue
    WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%';

-- 库存回退：只保留演示入库单 PIN-DEMO-0001 产生的数量，其余归零。
UPDATE t_inventory i
SET i.quantity = IFNULL((
        SELECT SUM(l.quantity) FROM t_purchase_receipt_line l
        JOIN t_purchase_receipt r ON r.id = l.receipt_id
        WHERE r.receipt_no = 'PIN-DEMO-0001'
          AND l.material_id = i.material_id
          AND r.warehouse_id = i.warehouse_id
    ), 0),
    i.updated_at = NOW(3);

DELETE FROM t_inventory WHERE quantity = 0;

-- 清理历史版本遗留的当前工单指针（只清空没有 RUNNING 工单的设备）。
UPDATE t_device d
SET d.current_work_order_id = NULL
WHERE d.current_work_order_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM t_work_order w
      WHERE w.id = d.current_work_order_id AND w.status = 'RUNNING'
  );

-- 维护工单产生的设备状态日志（停机与恢复）属于测试痕迹，一并清理。
DELETE FROM t_device_status_log WHERE reason LIKE '维护工单 MT-%';

DELETE FROM t_device_status_log
    WHERE device_id IN (SELECT id FROM t_device WHERE device_no LIKE 'SMOKE-EQ-%');

DELETE FROM t_device WHERE device_no LIKE 'SMOKE-EQ-%';

-- 订单分片按订单号散落在 company_order0 / company_order1；这里清理本实例的 4 张表，
-- 另一实例由 clean-smoke-orders-3307.sql 处理。
-- 说明：all-shards test 和 admin user token order 是早期分片/令牌手工验证留下的数据，
-- 同样属于测试数据，一并清理；演示订单（备注以“演示”开头）保留。
DELETE FROM t_order_0 WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%'
    OR remark LIKE 'all-shards test%' OR remark = 'admin user token order';
DELETE FROM t_order_1 WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%'
    OR remark LIKE 'all-shards test%' OR remark = 'admin user token order';
DELETE FROM t_order_2 WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%'
    OR remark LIKE 'all-shards test%' OR remark = 'admin user token order';
DELETE FROM t_order_3 WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%'
    OR remark LIKE 'all-shards test%' OR remark = 'admin user token order';

-- 8.5) 设备上报测试数据：订单作业数据、订单图片、未匹配暂存与接入审计。
--      冒烟测试固定使用演示设备 EQ-F001-01 与未知订单号 999999999999999999，
--      按设备编号即可安全清理；IoTDB 中的时序数据由保留策略归档。
DELETE FROM t_device_order_report WHERE device_no = 'EQ-F001-01';

DELETE FROM t_device_image WHERE device_no = 'EQ-F001-01';

DELETE FROM t_device_report_staging WHERE device_no = 'EQ-F001-01'
    OR raw_order_no = '999999999999999999';

DELETE FROM t_device_access_log WHERE device_no = 'EQ-F001-01';

SELECT 'biz_receivable' AS table_name, COUNT(*) AS rows_left FROM company_platform.biz_receivable
UNION ALL
SELECT 'biz_receipt', COUNT(*) FROM company_platform.biz_receipt
UNION ALL
SELECT 'biz_receipt_allocation', COUNT(*) FROM company_platform.biz_receipt_allocation
UNION ALL
SELECT 'biz_invoice', COUNT(*) FROM company_platform.biz_invoice
UNION ALL
SELECT 'biz_statement', COUNT(*) FROM company_platform.biz_statement
UNION ALL
SELECT 'biz_statement_line', COUNT(*) FROM company_platform.biz_statement_line
UNION ALL
SELECT 't_device', COUNT(*) FROM company_order0.t_device
UNION ALL
SELECT 't_work_order', COUNT(*) FROM company_order0.t_work_order
UNION ALL
SELECT 't_device_queue', COUNT(*) FROM company_order0.t_device_queue
UNION ALL
SELECT 't_queue_jump_request', COUNT(*) FROM company_order0.t_queue_jump_request
UNION ALL
SELECT 't_station_message', COUNT(*) FROM company_order0.t_station_message
UNION ALL
SELECT 't_maintenance_order', COUNT(*) FROM company_order0.t_maintenance_order
UNION ALL
SELECT 't_inspection_record', COUNT(*) FROM company_order0.t_inspection_record
UNION ALL
SELECT 't_warehouse', COUNT(*) FROM company_order0.t_warehouse
UNION ALL
SELECT 't_material', COUNT(*) FROM company_order0.t_material
UNION ALL
SELECT 't_inventory', COUNT(*) FROM company_order0.t_inventory
UNION ALL
SELECT 't_purchase_receipt', COUNT(*) FROM company_order0.t_purchase_receipt
UNION ALL
SELECT 't_stock_transfer', COUNT(*) FROM company_order0.t_stock_transfer
UNION ALL
SELECT 't_material_issue', COUNT(*) FROM company_order0.t_material_issue
UNION ALL
SELECT 'approval_instance', COUNT(*) FROM company_platform.approval_instance
UNION ALL
SELECT 'approval_task', COUNT(*) FROM company_platform.approval_task
UNION ALL
SELECT 'ai_chat_log', COUNT(*) FROM company_platform.ai_chat_log;
