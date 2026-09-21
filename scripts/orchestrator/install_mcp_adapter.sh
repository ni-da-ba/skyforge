#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
SERVICE_USER="$(id -un)"
SERVICE_HOME="$HOME"
STATE_DIR="$ROOT/.skyforge-orchestrator"
VENV_PYTHON="$STATE_DIR/venv/bin/python"
ENV_FILE="/etc/skyforge-orchestrator/env"
SERVICE_FILE="/etc/systemd/system/skyforge-mcp.service"

if [[ "$SERVICE_USER" == "root" ]]; then
  echo "Refusing MCP adapter install as root; run from the dedicated service user." >&2
  exit 1
fi
if [[ ! -x "$VENV_PYTHON" ]]; then
  echo "Hosted orchestrator venv is missing: $VENV_PYTHON" >&2
  exit 1
fi
if ! sudo test -r "$ENV_FILE"; then
  echo "Protected hosted environment is missing or unreadable via sudo: $ENV_FILE" >&2
  exit 1
fi
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "Refusing MCP adapter install from a tracked-dirty checkout." >&2
  exit 1
fi

"$VENV_PYTHON" scripts/orchestrator/sync_runtime_dependencies.py --root "$ROOT"

read_token="$(sudo sed -n 's/^SKYFORGE_DEVELOPMENT_API_TOKEN=//p' "$ENV_FILE" | head -n 1)"
write_token="$(sudo sed -n 's/^SKYFORGE_DEVELOPMENT_WRITE_TOKEN=//p' "$ENV_FILE" | head -n 1)"
if [[ "${#read_token}" -lt 32 ]]; then
  echo "MCP adapter requires the configured development read token." >&2
  exit 1
fi
expect_write=0
if [[ -n "$write_token" ]]; then
  if [[ "${#write_token}" -lt 32 ]]; then
    echo "Configured development write token is invalid." >&2
    exit 1
  fi
  expect_write=1
fi
unset read_token write_token

tmp_service="$(mktemp)"
trap 'rm -f "$tmp_service"' EXIT
python3 - "$ROOT" "$SERVICE_USER" "$SERVICE_HOME" "$VENV_PYTHON" >"$tmp_service" <<'PY'
from pathlib import Path
import sys

root, user, home, python = sys.argv[1:]
template = Path("deploy/orchestrator/skyforge-mcp.service.in").read_text()
print(
    template.replace("@@ROOT@@", root)
    .replace("@@USER@@", user)
    .replace("@@HOME@@", home)
    .replace("@@VENV_PYTHON@@", python),
    end="",
)
PY

sudo install -m 0644 "$tmp_service" "$SERVICE_FILE"
sudo systemctl daemon-reload
sudo systemctl enable --now skyforge-mcp.service >/dev/null
sudo systemctl restart skyforge-mcp.service

probe_args=(--url http://127.0.0.1:3001/mcp)
if [[ "$expect_write" == "1" ]]; then
  probe_args+=(--expect-write)
fi
ready=0
for _ in $(seq 1 30); do
  if "$VENV_PYTHON" scripts/orchestrator/platform_v2_mcp_probe.py "${probe_args[@]}" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
if [[ "$ready" != "1" ]]; then
  echo "Skyforge MCP sidecar did not become ready." >&2
  echo "Inspect: sudo systemctl status skyforge-mcp.service" >&2
  echo "         sudo journalctl -u skyforge-mcp.service --since '-10 min'" >&2
  exit 1
fi

echo "Skyforge MCP sidecar installed and protocol-ready on localhost:3001/mcp."
echo "It is intentionally not exposed by Caddy. Use an approved secure MCP tunnel/proxy."

