#!/usr/bin/env bash
set -euo pipefail

# 清理冒烟测试数据（仅开发环境）。财务/排产数据在 3306，订单可能落在 3307，
# 两个分片实例都需要执行。
BASE_DIR="/home/wy/setup/company-platform"
SQL_DIR="${BASE_DIR}/scripts/remote"
MYSQL_BASE=(mysql -uroot -proot -h127.0.0.1 --protocol=TCP)

"${MYSQL_BASE[@]}" -P3306 < "${SQL_DIR}/clean-smoke-data.sql"
"${MYSQL_BASE[@]}" -P3307 < "${SQL_DIR}/clean-smoke-orders-3307.sql"

echo "smoke data cleanup completed"
