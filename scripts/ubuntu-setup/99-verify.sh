#!/usr/bin/env bash
# 安装后统一验证
set -uo pipefail

export SUDO_ASKPASS=/home/wy/.sudo-askpass
SUDO() { /usr/bin/sudo -A "$@"; }

echo "===== OS ====="
hostnamectl 2>/dev/null | grep -E 'Operating System|Kernel|Architecture' || true
date '+%F %T %Z'

echo "===== Services ====="
for s in mysql mysql2 redis-server emqx iotdb; do
  printf "%-14s %s / %s\n" "$s" "$(systemctl is-active "$s" 2>/dev/null)" "$(systemctl is-enabled "$s" 2>/dev/null)"
done

echo "===== Versions ====="
mysql --version 2>/dev/null || true
/usr/local/bin/redis-server --version 2>/dev/null || true
grep -m1 '^version' /opt/iotdb/conf/iotdb-system.properties 2>/dev/null || true

echo "===== Ports ====="
ss -lntp 2>/dev/null | grep -E ':(22|3306|3307|6379|6667|10710|10720|10730|10740|10750|10760|18080|1883|8083|8084|8883|18083)\b' || true

echo "===== MySQL 3306 ====="
mysql -h127.0.0.1 -P3306 -uroot -proot -e "SELECT @@port AS port, @@version AS version; SHOW DATABASES;" 2>&1 || true
mysql -h127.0.0.1 -P3306 -uroot -proot -e "SELECT * FROM demo_meta.shard_route; SELECT COUNT(*) AS shard0_rows FROM demo_shard0.orders;" 2>&1 || true

echo "===== MySQL 3307 ====="
mysql -h127.0.0.1 -P3307 -uroot -proot -e "SELECT @@port AS port, @@version AS version; SHOW DATABASES;" 2>&1 || true
mysql -h127.0.0.1 -P3307 -uroot -proot -e "SELECT COUNT(*) AS shard1_rows FROM demo_shard1.orders;" 2>&1 || true

echo "===== Redis ====="
/usr/local/bin/redis-cli --no-auth-warning -a root ping 2>&1 || true
/usr/local/bin/redis-cli --no-auth-warning -a root info server 2>/dev/null | grep -E 'redis_version|tcp_port|uptime_in_seconds' || true

echo "===== EMQX ====="
SUDO emqx ctl status 2>&1 || true
curl -s -o /dev/null -w "dashboard_http=%{http_code}\n" --max-time 5 http://127.0.0.1:18083/ || true
curl -s --max-time 8 -X POST http://127.0.0.1:18083/api/v5/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"root@123"}' \
  | grep -q '"token"' && echo "dashboard_login=ok" || echo "dashboard_login=failed"

echo "===== IoTDB ====="
/opt/iotdb/sbin/start-cli.sh -h 127.0.0.1 -p 6667 -u root -pw root -e "show databases" 2>&1 || true
/opt/iotdb/sbin/start-cli.sh -h 127.0.0.1 -p 6667 -u root -pw root -e "select * from root.demo.device1" 2>&1 || true
