#!/usr/bin/env bash
# Redis 8.10.2 源码编译安装 + systemd 服务
# 适用：Ubuntu 26.04，需 wy 用户 sudo 密码 root
set -euo pipefail

export SUDO_ASKPASS=/home/wy/.sudo-askpass
SUDO() { /usr/bin/sudo -A "$@"; }
log() { echo "[redis] $*"; }
VER="8.10.2"

log "1/5 准备 Redis ${VER} 源码"
cd /tmp
if [ ! -d "redis-${VER}" ]; then
  curl -fL --retry 3 -o "redis-${VER}.tar.gz" "https://download.redis.io/releases/redis-${VER}.tar.gz"
  rm -rf "redis-${VER}"
  tar xzf "redis-${VER}.tar.gz"
fi
cd "redis-${VER}"

log "2/5 编译并安装核心二进制"
make -j"$(nproc)" build redis
SUDO install -m 755 \
  src/redis-server src/redis-cli src/redis-benchmark \
  src/redis-check-aof src/redis-check-rdb src/redis-sentinel \
  /usr/local/bin/

log "3/5 创建运行用户与目录"
if ! id redis >/dev/null 2>&1; then
  SUDO useradd --system --home-dir /var/lib/redis --shell /usr/sbin/nologin redis
fi
SUDO mkdir -p /etc/redis /var/lib/redis /var/log/redis
SUDO chown -R redis:redis /var/lib/redis /var/log/redis

log "4/5 写入配置与 systemd 服务"
cat > /tmp/redis.conf <<'EOF'
bind 0.0.0.0
protected-mode yes
port 6379
tcp-backlog 511
timeout 0
tcp-keepalive 300
daemonize no
supervised no
pidfile /run/redis/redis-server.pid
loglevel notice
logfile /var/log/redis/redis-server.log
databases 16
always-show-logo no
set-proc-title yes
proc-title-template "{title} {listen-addr} {server-mode}"
save 3600 1 300 100 60 10000
stop-writes-on-bgsave-error yes
rdbcompression yes
rdbchecksum yes
dbfilename dump.rdb
dir /var/lib/redis
appendonly yes
appendfilename "appendonly.aof"
appenddirname "appendonlydir"
appendfsync everysec
no-appendfsync-on-rewrite no
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb
requirepass root
EOF
SUDO cp /tmp/redis.conf /etc/redis/redis.conf
SUDO chown redis:redis /etc/redis/redis.conf
SUDO chmod 640 /etc/redis/redis.conf

cat > /tmp/redis-server.service <<'EOF'
[Unit]
Description=Redis In-Memory Data Store (8.10.2)
After=network.target

[Service]
Type=simple
User=redis
Group=redis
RuntimeDirectory=redis
RuntimeDirectoryMode=0755
WorkingDirectory=/var/lib/redis
ExecStart=/usr/local/bin/redis-server /etc/redis/redis.conf
ExecStop=/usr/local/bin/redis-cli --no-auth-warning -a root shutdown
Restart=always
RestartSec=3
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF
SUDO cp /tmp/redis-server.service /etc/systemd/system/redis-server.service
SUDO systemctl daemon-reload
SUDO systemctl enable --now redis-server
sleep 2

log "5/5 验证"
systemctl is-active --quiet redis-server
/usr/local/bin/redis-server --version
/usr/local/bin/redis-cli --no-auth-warning -a root ping
/usr/local/bin/redis-cli --no-auth-warning -a root info server | grep -E 'redis_version|tcp_port|uptime_in_seconds'
