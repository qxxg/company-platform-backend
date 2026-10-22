#!/usr/bin/env bash
# Apache IoTDB 2.0.11 单机版安装
set -euo pipefail

export SUDO_ASKPASS=/home/wy/.sudo-askpass
SUDO() { /usr/bin/sudo -A "$@"; }
log() { echo "[iotdb] $*"; }
VER="2.0.11"

log "1/6 准备 Apache IoTDB ${VER}"
cd /tmp
ZIP="apache-iotdb-${VER}-all-bin.zip"
if [ -s "/home/wy/setup/${ZIP}" ]; then
  cp "/home/wy/setup/${ZIP}" "$ZIP"
elif [ ! -s "$ZIP" ]; then
  curl -fL --retry 3 -o "$ZIP" \
    "https://mirrors.tuna.tsinghua.edu.cn/apache/iotdb/${VER}/apache-iotdb-${VER}-all-bin.zip"
  if [ ! -s "$ZIP" ]; then
    curl -fL --retry 3 -o "$ZIP" \
      "https://dlcdn.apache.org/iotdb/${VER}/apache-iotdb-${VER}-all-bin.zip"
  fi
fi

log "2/6 解压到 /opt/iotdb"
rm -rf "/tmp/apache-iotdb-${VER}-all-bin"
unzip -q "apache-iotdb-${VER}-all-bin.zip" -d /tmp
SUDO rm -rf /opt/iotdb
SUDO mv "/tmp/apache-iotdb-${VER}-all-bin" /opt/iotdb

log "3/6 创建运行用户与数据目录"
if ! id iotdb >/dev/null 2>&1; then
  SUDO useradd --system --home-dir /opt/iotdb --shell /usr/sbin/nologin iotdb
fi
SUDO mkdir -p /opt/iotdb/data/cn /opt/iotdb/data/dn /opt/iotdb/data/dn/wal /opt/iotdb/data/dn/system
SUDO chown -R iotdb:iotdb /opt/iotdb

log "4/6 写入配置"
CONF=/opt/iotdb/conf/iotdb-system.properties
set_prop() {
  local key="$1" value="$2"
  if SUDO grep -q "^${key}=" "$CONF" 2>/dev/null; then
    SUDO sed -i "s|^${key}=.*|${key}=${value}|" "$CONF"
  else
    SUDO bash -c "echo '${key}=${value}' >> '$CONF'"
  fi
}

set_prop dn_rpc_address 0.0.0.0
set_prop dn_rpc_port 6667
set_prop cn_internal_address 127.0.0.1
set_prop cn_internal_port 10710
set_prop cn_consensus_port 10720
set_prop dn_internal_address 127.0.0.1
set_prop dn_internal_port 10730
set_prop dn_mpp_data_exchange_port 10740
set_prop dn_schema_region_consensus_port 10750
set_prop dn_data_region_consensus_port 10760
set_prop cn_data_dirs /opt/iotdb/data/cn
set_prop cn_wal_dirs /opt/iotdb/data/cn/wal
set_prop cn_system_dir /opt/iotdb/data/cn/system
set_prop dn_data_dirs /opt/iotdb/data/dn
set_prop dn_wal_dirs /opt/iotdb/data/dn/wal
set_prop dn_system_dir /opt/iotdb/data/dn/system
set_prop enable_rest_service true
set_prop rest_service_port 18080

JAVA_HOME_REAL=$(readlink -f "$(command -v java)" | sed 's|/bin/java$||')
cat > /tmp/iotdb.service <<EOF
[Unit]
Description=Apache IoTDB ${VER} Standalone
After=network.target

[Service]
Type=forking
User=iotdb
Group=iotdb
Environment=JAVA_HOME=${JAVA_HOME_REAL}
Environment=HEAP_SIZE=2G
ExecStart=/opt/iotdb/sbin/start-standalone.sh
ExecStop=/opt/iotdb/sbin/stop-standalone.sh
TimeoutStartSec=300
TimeoutStopSec=120
Restart=on-failure
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF
SUDO cp /tmp/iotdb.service /etc/systemd/system/iotdb.service
SUDO systemctl daemon-reload
SUDO systemctl enable --now iotdb
sleep 15

log "5/6 检查服务状态"
if ! systemctl is-active --quiet iotdb; then
  log "iotdb 启动失败，最近日志："
  SUDO find /opt/iotdb/logs -type f -name '*.log' -print 2>/dev/null | head -5
  SUDO journalctl -u iotdb --no-pager -n 50 || true
  exit 1
fi
ss -lntp 2>/dev/null | grep -E ':(6667|10710|10720|10730|10740|10750|10760|18080)\b' || true

log "6/6 使用 CLI 验证并创建示例库"
/opt/iotdb/sbin/start-cli.sh -h 127.0.0.1 -p 6667 -u root -pw root -e "show databases" || true
/opt/iotdb/sbin/start-cli.sh -h 127.0.0.1 -p 6667 -u root -pw root -e "create database root.demo" || true
/opt/iotdb/sbin/start-cli.sh -h 127.0.0.1 -p 6667 -u root -pw root -e "insert into root.demo.device1(timestamp, temperature) values(1760000000000, 25.5)" || true
/opt/iotdb/sbin/start-cli.sh -h 127.0.0.1 -p 6667 -u root -pw root -e "select * from root.demo.device1" || true
