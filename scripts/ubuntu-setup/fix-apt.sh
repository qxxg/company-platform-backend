#!/usr/bin/env bash
set -uo pipefail
export SUDO_ASKPASS=/home/wy/.sudo-askpass
SUDO() { /usr/bin/sudo -A "$@"; }

echo "== before =="
ps -eo pid,ppid,cmd | grep -E 'apt-get update|install-deps|apt-get install' | grep -v grep || true

# 停止卡住的 apt update
for pid in $(pgrep -f 'apt-get update -y' 2>/dev/null); do
  SUDO kill "$pid" 2>/dev/null || true
done
sleep 2

# 停止可能已启动的旧安装脚本
for pid in $(pgrep -f '/tmp/install-deps.sh' 2>/dev/null); do
  SUDO kill "$pid" 2>/dev/null || true
done
for pid in $(pgrep -f 'apt-get install -y curl wget' 2>/dev/null); do
  SUDO kill "$pid" 2>/dev/null || true
done
sleep 1

echo "== switch mirror =="
SUDO sed -i \
  -e 's|http://mirrors.tuna.tsinghua.edu.cn/ubuntu/|https://mirrors.ustc.edu.cn/ubuntu/|g' \
  -e 's|http://cn.archive.ubuntu.com/ubuntu/|https://mirrors.ustc.edu.cn/ubuntu/|g' \
  -e 's|http://archive.ubuntu.com/ubuntu/|https://mirrors.ustc.edu.cn/ubuntu/|g' \
  -e 's|http://security.ubuntu.com/ubuntu/|https://mirrors.ustc.edu.cn/ubuntu/|g' \
  /etc/apt/sources.list.d/ubuntu.sources
grep -E '^URIs:' /etc/apt/sources.list.d/ubuntu.sources

echo "== apt update =="
SUDO apt-get update -y > /tmp/apt-update3.log 2>&1
rc=$?
echo "update_exit=$rc"
tail -n 6 /tmp/apt-update3.log
exit $rc
