#!/usr/bin/env bash
# EMQX 开源版安装（Ubuntu 26.04 使用 noble 仓库）
set -euo pipefail

export SUDO_ASKPASS=/home/wy/.sudo-askpass
SUDO() { /usr/bin/sudo -A "$@"; }
log() { echo "[emqx] $*"; }

log "1/4 添加 EMQX 官方仓库"
curl -fsSL https://assets.emqx.com/scripts/install-emqx-deb.sh -o /tmp/install-emqx-deb.sh
SUDO env os=ubuntu dist=noble bash /tmp/install-emqx-deb.sh

log "2/4 安装 EMQX"
SUDO env DEBIAN_FRONTEND=noninteractive apt-get install -y emqx

log "3/4 启动并设置开机自启"
SUDO systemctl daemon-reload
SUDO systemctl enable --now emqx
sleep 5
systemctl is-active --quiet emqx || { SUDO systemctl status emqx --no-pager || true; exit 1; }

log "4/4 设置 Dashboard 密码并验证"
# EMQX Dashboard 密码要求 8~64 位，因此不能使用 root
EMQX_ADMIN_PASSWORD='root@123'
SUDO emqx ctl admins passwd admin "$EMQX_ADMIN_PASSWORD" || log "密码修改命令失败，请查看 emqx ctl 帮助"
SUDO emqx ctl status
curl -s --max-time 8 -X POST http://127.0.0.1:18083/api/v5/login \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin\",\"password\":\"${EMQX_ADMIN_PASSWORD}\"}" \
  | grep -q '"token"' && log "Dashboard 登录验证通过（admin / ${EMQX_ADMIN_PASSWORD}）" \
  || log "警告：Dashboard 登录验证失败"
ss -lntp 2>/dev/null | grep -E ':(1883|8083|8084|8883|18083)\b' || true
