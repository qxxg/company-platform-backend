#!/usr/bin/env bash
# MySQL 9.7 LTS 双实例安装（3306 默认实例 + 3307 第二实例）
# 适用：Ubuntu 26.04 (resolute)，需 wy 用户 sudo 密码 root
set -euo pipefail

export SUDO_ASKPASS=/home/wy/.sudo-askpass
SUDO() { /usr/bin/sudo -A "$@"; }
log() { echo "[mysql] $*"; }

log "1/7 配置 MySQL 9.7 LTS APT 源"
curl -fsSL https://repo.mysql.com/RPM-GPG-KEY-mysql-2025 -o /tmp/mysql-key.asc
SUDO gpg --dearmor --yes -o /usr/share/keyrings/mysql.gpg /tmp/mysql-key.asc
cat > /tmp/mysql.list <<'EOF'
deb [signed-by=/usr/share/keyrings/mysql.gpg] https://repo.mysql.com/apt/ubuntu/ resolute mysql-9.7-lts
EOF
SUDO cp /tmp/mysql.list /etc/apt/sources.list.d/mysql.list
SUDO apt-get update -y

log "2/7 安装 mysql-community-server"
cat > /tmp/mysql-preseed.txt <<'EOF'
mysql-community-server mysql-community-server/root-pass password root
mysql-community-server mysql-community-server/re-root-pass password root
mysql-community-server mysql-community-server/default-auth-override select Use Strong Password Encryption
EOF
SUDO debconf-set-selections < /tmp/mysql-preseed.txt
SUDO env DEBIAN_FRONTEND=noninteractive apt-get install -y mysql-community-server

systemctl is-active --quiet mysql || { log "mysql 服务未启动"; SUDO systemctl status mysql --no-pager || true; exit 1; }
mysql --version

log "3/7 设置 3306 实例 root 密码并创建远程用户"
# 依次尝试：sudo socket 登录、root/root、debian-sys-maint 维护账号
MYSQL1_CMD=""
if SUDO mysql -e "SELECT 1" >/dev/null 2>&1; then
  MYSQL1_CMD="SUDO mysql"
elif mysql -uroot -proot -e "SELECT 1" >/dev/null 2>&1; then
  MYSQL1_CMD="mysql -uroot -proot"
elif [ -f /etc/mysql/debian.cnf ] && mysql --defaults-file=/etc/mysql/debian.cnf -e "SELECT 1" >/dev/null 2>&1; then
  MYSQL1_CMD="mysql --defaults-file=/etc/mysql/debian.cnf"
else
  log "无法登录 3306 实例，请手动检查 /var/log/mysql/error.log"
  exit 1
fi

$MYSQL1_CMD <<'SQL'
ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY 'root';
CREATE USER IF NOT EXISTS 'wy'@'%' IDENTIFIED WITH caching_sha2_password BY 'root';
ALTER USER 'wy'@'%' IDENTIFIED WITH caching_sha2_password BY 'root';
GRANT ALL PRIVILEGES ON *.* TO 'wy'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
SQL

log "4/7 允许 3306 实例远程访问"
cat > /tmp/mysql-bind.cnf <<'EOF'
[mysqld]
bind-address = 0.0.0.0
EOF
SUDO cp /tmp/mysql-bind.cnf /etc/mysql/mysql.conf.d/zz-bind.cnf
SUDO systemctl restart mysql
sleep 2
mysql -h127.0.0.1 -P3306 -uroot -proot -e "SELECT @@port AS port, @@version AS version;"

log "5/7 初始化 3307 第二实例"
SUDO mkdir -p /var/lib/mysql2 /var/log/mysql2 /run/mysqld2 /var/lib/mysql2-files
SUDO chown -R mysql:mysql /var/lib/mysql2 /var/log/mysql2 /run/mysqld2 /var/lib/mysql2-files
if [ ! -d /var/lib/mysql2/mysql ]; then
  SUDO mysqld --initialize-insecure --user=mysql --datadir=/var/lib/mysql2
fi

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

# AppArmor：MySQL 二进制共用同一 profile，放行第二实例目录
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

log "6/7 设置 3307 实例 root 密码并创建远程用户"
mysql --socket=/run/mysqld2/mysqld.sock -uroot <<'SQL'
ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY 'root';
CREATE USER IF NOT EXISTS 'wy'@'%' IDENTIFIED WITH caching_sha2_password BY 'root';
ALTER USER 'wy'@'%' IDENTIFIED WITH caching_sha2_password BY 'root';
GRANT ALL PRIVILEGES ON *.* TO 'wy'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
SQL
mysql -h127.0.0.1 -P3307 -uroot -proot -e "SELECT @@port AS port, @@version AS version;"

log "7/7 创建分库分表示例库 demo_shard0 / demo_shard1 / demo_meta"
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

log "完成：3306/3307 双实例已启动，示例库已创建"
