#!/usr/bin/env bash
set -euo pipefail
export SUDO_ASKPASS=/home/wy/.sudo-askpass
SUDO() { /usr/bin/sudo -A "$@"; }
export DEBIAN_FRONTEND=noninteractive

echo "[prereq] installing packages..."
SUDO apt-get install -y \
  curl wget gnupg ca-certificates lsb-release apt-transport-https \
  openjdk-17-jre-headless build-essential libssl-dev pkg-config tcl unzip

echo "[prereq] versions:"
java -version 2>&1 | head -n 3
gcc --version | head -n 1
make --version | head -n 1
unzip -v | head -n 1
