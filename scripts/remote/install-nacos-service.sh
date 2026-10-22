#!/usr/bin/env bash
# Install/refresh the Nacos systemd unit on the middleware host.
# Intended for the current deployment layout:
#   Nacos home: /home/wy/nacos-3.0.3
#   JDK:        /usr/lib/jvm/java-17-openjdk-amd64
# Adjust nacos.service if those paths differ.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NACOS_HOME="${NACOS_HOME:-/home/wy/nacos-3.0.3}"

if [ -n "${SUDO_ASKPASS:-}" ]; then
    SUDO=(sudo -A)
else
    SUDO=(sudo)
fi

"${SUDO[@]}" cp "${SCRIPT_DIR}/nacos.service" /etc/systemd/system/nacos.service
"${SUDO[@]}" systemctl daemon-reload
"${SUDO[@]}" systemctl enable nacos

# If Nacos was started manually before the unit existed, stop it first.
if pgrep -f 'target/nacos-server.jar' >/dev/null 2>&1 && ! systemctl is-active --quiet nacos; then
    echo "stopping existing Nacos process"
    bash "${NACOS_HOME}/bin/shutdown.sh" || true
    sleep 5
    pkill -f 'target/nacos-server.jar' || true
    sleep 2
fi

"${SUDO[@]}" systemctl restart nacos

for _ in $(seq 1 60); do
    if curl -fsS --max-time 2 http://127.0.0.1:8848/nacos/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
        echo "nacos systemd service is ready"
        curl -fsS --max-time 3 http://127.0.0.1:8848/nacos/actuator/health || true
        echo
        "${SUDO[@]}" systemctl --no-pager --full status nacos | head -25
        exit 0
    fi
    sleep 2
done

echo "nacos did not become ready"
"${SUDO[@]}" systemctl --no-pager --full status nacos || true
"${SUDO[@]}" journalctl -u nacos -n 80 --no-pager || true
exit 1
