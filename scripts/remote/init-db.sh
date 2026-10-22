#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="/home/wy/setup/company-platform"
SQL_DIR="${BASE_DIR}/sql"
MYSQL_BASE=(mysql -uroot -proot -h127.0.0.1 --protocol=TCP)

cd "${SQL_DIR}"
"${MYSQL_BASE[@]}" -P3306 < 01-platform-schema.sql
"${MYSQL_BASE[@]}" -P3306 < 02-oauth2-schema.sql
"${MYSQL_BASE[@]}" -P3306 < 07-customer-contract-schema.sql
"${MYSQL_BASE[@]}" -P3306 < 08-finance-receivable-receipt.sql
"${MYSQL_BASE[@]}" -P3306 < 09-finance-invoice-statement.sql
"${MYSQL_BASE[@]}" -P3306 < 14-approval-center.sql
"${MYSQL_BASE[@]}" -P3306 < 03-sharding-schema-shard0.sql
"${MYSQL_BASE[@]}" -P3307 < 04-sharding-schema-shard1.sql
"${MYSQL_BASE[@]}" -P3306 < 10-equipment-scheduling.sql
"${MYSQL_BASE[@]}" -P3306 < 11-equipment-maintenance.sql
"${MYSQL_BASE[@]}" -P3306 < 12-warehouse.sql
"${MYSQL_BASE[@]}" -P3306 < 13-material-issue.sql
"${MYSQL_BASE[@]}" -P3306 < 15-device-reporting.sql
"${MYSQL_BASE[@]}" -P3306 < 16-expense-loan.sql
"${MYSQL_BASE[@]}" -P3306 < 17-ai-assistant.sql
"${MYSQL_BASE[@]}" -P3306 < 18-bi-dashboard.sql
"${MYSQL_BASE[@]}" -P3306 < 05-grants-shard0.sql
"${MYSQL_BASE[@]}" -P3307 < 06-grants-shard1.sql

echo "database initialization completed"
