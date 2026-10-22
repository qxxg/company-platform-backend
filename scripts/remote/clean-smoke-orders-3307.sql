-- 清理冒烟测试与人工验收订单在第二个分片实例上的残留（仅开发环境使用）。
-- 订单号决定了落在 3306 还是 3307 的 company_order1，因此两个实例都要执行。
-- all-shards test 和 admin user token order 是早期分片/令牌手工验证数据，同样清理。
SET NAMES utf8mb4;

USE company_order1;

DELETE FROM t_order_0 WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%'
    OR remark LIKE 'all-shards test%' OR remark = 'admin user token order';
DELETE FROM t_order_1 WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%'
    OR remark LIKE 'all-shards test%' OR remark = 'admin user token order';
DELETE FROM t_order_2 WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%'
    OR remark LIKE 'all-shards test%' OR remark = 'admin user token order';
DELETE FROM t_order_3 WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%'
    OR remark LIKE 'all-shards test%' OR remark = 'admin user token order';

SELECT 'company_order1 smoke orders' AS table_name, COUNT(*) AS rows_left
FROM (
    SELECT order_no FROM t_order_0 WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%'
    UNION ALL SELECT order_no FROM t_order_1 WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%'
    UNION ALL SELECT order_no FROM t_order_2 WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%'
    UNION ALL SELECT order_no FROM t_order_3 WHERE remark LIKE 'smoke test%' OR remark LIKE '%验收%'
) smoke_orders;
