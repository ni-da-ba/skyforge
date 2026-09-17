#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" != "--confirm" ]]; then
  echo "Usage: $0 --confirm" >&2
  echo "Disables and removes only the Platform v2 read-only shadow service/timer." >&2
  exit 2
fi

SERVICE_FILE="/etc/systemd/system/skyforge-v2-shadow.service"
TIMER_FILE="/etc/systemd/system/skyforge-v2-shadow.timer"

sudo systemctl disable --now skyforge-v2-shadow.timer 2>/dev/null || true
sudo systemctl stop skyforge-v2-shadow.service 2>/dev/null || true
sudo rm -f "$SERVICE_FILE" "$TIMER_FILE"
sudo systemctl daemon-reload
sudo systemctl reset-failed skyforge-v2-shadow.service 2>/dev/null || true

echo "Platform v2 shadow units removed. Production orchestrator service was not changed."
