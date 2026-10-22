#!/usr/bin/env bash
# 完成 MySQL 3307 第二实例、示例分片库
set -euo pipefail
export SUDO_ASKPASS=/home/wy/.sudo-askpass
SUDO() { /usr/bin/sudo -A "$@"; }
log() { echo "[mysql2] $*"; }

log "1/6 初始化 3307 数据目录"
if SUDO test -d /var/lib/mysql2/mysql; then
  log "数据目录已初始化，跳过"
else
  SUDO rm -rf /var/lib/mysql2
  SUDO mysqld --initialize-insecure --user=mysql --datadir=/var/lib/mysql2
  SUDO chown -R mysql:mysql /var/lib/mysql2
fi
SUDO mkdir -p /var/log/mysql2 /run/mysqld2 /var/lib/mysql2-files
SUDO chown -R mysql:mysql /var/log/mysql2 /run/mysqld2 /var/lib/mysql2-files

log "2/6 写入 3307 配置"
cat > /tmp/mysql2.cnf <<'EOF'
[mysqld]
user=mysql
server-id=2
port=3307
bind-address=0.0.0.0
socket=/run/mysqld2/mysqld.sock
pid-file=/run/mysqld2/mysqld.pid
datadir=/var/lib/mysql2
log-error=/var/log/mysql2/error.log
secure-file-priv=/var/lib/mysql2-files
mysqlx=0
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
max_connections=300
innodb_buffer_pool_size=256M
EOF
SUDO cp /tmp/mysql2.cnf /etc/mysql/mysql2.cnf
SUDO chown mysql:mysql /etc/mysql/mysql2.cnf

log "3/6 AppArmor 放行第二实例目录"
if [ -f /etc/apparmor.d/usr.sbin.mysqld ] && command -v apparmor_parser >/dev/null 2>&1; then
  cat > /tmp/mysql2-apparmor <<'EOF'
  /var/lib/mysql2/ r,
  /var/lib/mysql2/** rwk,
  /var/lib/mysql2-files/ r,
  /var/lib/mysql2-files/** rwk,
  /var/log/mysql2/ r,
  /var/log/mysql2/** rw,
  /run/mysqld2/ rw,
  /run/mysqld2/** rw,
EOF
  SUDO mkdir -p /etc/apparmor.d/local
  SUDO cp /tmp/mysql2-apparmor /etc/apparmor.d/local/usr.sbin.mysqld
  SUDO apparmor_parser -r /etc/apparmor.d/usr.sbin.mysqld || true
fi

log "4/6 安装并启动 mysql2.service"
cat > /tmp/mysql2.service <<'EOF'
[Unit]
Description=MySQL Second Instance (port 3307)
After=network.target

[Service]
Type=notify
User=mysql
Group=mysql
ExecStartPre=/bin/mkdir -p /run/mysqld2 /var/log/mysql2
ExecStartPre=/bin/chown -R mysql:mysql /run/mysqld2 /var/log/mysql2
ExecStart=/usr/sbin/mysqld --defaults-file=/etc/mysql/mysql2.cnf
ExecReload=/bin/kill -HUP $MAINPID
TimeoutSec=300
Restart=on-failure
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF
SUDO cp /tmp/mysql2.service /etc/systemd/system/mysql2.service
SUDO systemctl daemon-reload
SUDO systemctl enable --now mysql2
sleep 5
if ! systemctl is-active --quiet mysql2; then
  log "mysql2 启动失败，错误日志："
  SUDO tail -n 80 /var/log/mysql2/error.log || true
  exit 1
fi

log "5/6 设置 3307 root 密码与远程用户"
mysql --socket=/run/mysqld2/mysqld.sock -uroot <<'SQL'
ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY 'root';
CREATE USER IF NOT EXISTS 'wy'@'%' IDENTIFIED WITH caching_sha2_password BY 'root';
ALTER USER 'wy'@'%' IDENTIFIED WITH caching_sha2_password BY 'root';
GRANT ALL PRIVILEGES ON *.* TO 'wy'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
SQL
mysql -h127.0.0.1 -P3307 -uroot -proot -e "SELECT @@port AS port, @@version AS version;"

log "6/6 创建分库分表示例库"
mysql -h127.0.0.1 -P3306 -uroot -proot <<'SQL'
CREATE DATABASE IF NOT EXISTS demo_meta DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS demo_shard0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE demo_meta;
CREATE TABLE IF NOT EXISTS shard_route (
  shard_no INT PRIMARY KEY,
  db_host VARCHAR(64) NOT NULL,
  db_port INT NOT NULL,
  db_name VARCHAR(64) NOT NULL,
  range_start BIGINT NOT NULL,
  range_end BIGINT NOT NULL,
  description VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO shard_route VALUES
  (0,'192.168.3.44',3306,'demo_shard0',0,4999999,'分片0：id 0-4999999'),
  (1,'192.168.3.44',3307,'demo_shard1',5000000,9999999,'分片1：id 5000000-9999999')
ON DUPLICATE KEY UPDATE db_host=VALUES(db_host), db_port=VALUES(db_port), db_name=VALUES(db_name), range_start=VALUES(range_start), range_end=VALUES(range_end), description=VALUES(description);
USE demo_shard0;
CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(32) NOT NULL,
  customer_id BIGINT NOT NULL,
  amount_cent BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'created',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO orders (id,order_no,customer_id,amount_cent,status) VALUES
  (1,'SO202610200001',1001,1234500,'created'),
  (2,'SO202610200002',1002,888800,'created'),
  (3,'SO202610200003',1003,5000000,'created');
SQL

mysql -h127.0.0.1 -P3307 -uroot -proot <<'SQL'
CREATE DATABASE IF NOT EXISTS demo_shard1 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE demo_shard1;
CREATE TABLE IF NOT EXISTS orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(32) NOT NULL,
  customer_id BIGINT NOT NULL,
  amount_cent BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'created',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO orders (id,order_no,customer_id,amount_cent,status) VALUES
  (5000001,'SO202610200101',2001,666600,'created'),
  (5000002,'SO202610200102',2002,999900,'created'),
  (5000003,'SO202610200103',2003,1500000,'created');
SQL

log "完成：3307 实例与示例分片库已就绪"
