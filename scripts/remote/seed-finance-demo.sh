#!/usr/bin/env bash
# 重建财务演示数据：应收、收款、核销明细、手工发票。
# 08/09 脚本自带演示数据清理逻辑，可重复执行。
# 用法：bash scripts/remote/seed-finance-demo.sh
set -uo pipefail

SQL_DIR="${1:-/home/wy/setup/company-platform/sql}"
cd "${SQL_DIR}"

mysql -uroot -proot -h127.0.0.1 --protocol=TCP -P3306 < 08-finance-receivable-receipt.sql 2>&1 | grep -v "Warning" || true
echo "08 applied"

mysql -uroot -proot -h127.0.0.1 --protocol=TCP -P3306 < 09-finance-invoice-statement.sql 2>&1 | grep -v "Warning" || true
echo "09 applied"

echo "== receivables =="
mysql -uroot -proot -h127.0.0.1 --protocol=TCP -N -P3306 \
    -e "select id, customer_id, amount, received_amount, due_date, status from company_platform.biz_receivable order by id;" 2>&1 | grep -v "Warning" || true

echo "== receipts =="
mysql -uroot -proot -h127.0.0.1 --protocol=TCP -N -P3306 \
    -e "select id, customer_id, amount, allocated_amount, status, serial_no from company_platform.biz_receipt order by id;" 2>&1 | grep -v "Warning" || true

echo "== allocations =="
mysql -uroot -proot -h127.0.0.1 --protocol=TCP -N -P3306 \
    -e "select id, receipt_id, receivable_id, amount from company_platform.biz_receipt_allocation order by id;" 2>&1 | grep -v "Warning" || true
