#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" != "--confirm-read-only" ]]; then
  echo "Usage: $0 --confirm-read-only" >&2
  echo "Installs only the read-only Platform v2 shadow systemd service/timer." >&2
  exit 2
fi

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"
REPO="ni-da-ba/skyforge"
SERVICE_USER="$(id -un)"
SERVICE_HOME="$HOME"
VENV_PYTHON="$ROOT/.skyforge-orchestrator/venv/bin/python"
SERVICE_FILE="/etc/systemd/system/skyforge-v2-shadow.service"
TIMER_FILE="/etc/systemd/system/skyforge-v2-shadow.timer"

if [[ "$SERVICE_USER" == "root" ]]; then
  echo "Refusing shadow install as root; use the existing dedicated service user." >&2
  exit 1
fi
if [[ ! -x "$VENV_PYTHON" ]]; then
  echo "Existing hosted orchestrator virtualenv is unavailable at $VENV_PYTHON." >&2
  exit 1
fi
if [[ ! -r "$ROOT/.skyforge-orchestrator/state.json" ]]; then
  echo "Legacy controller state is not readable; refusing shadow activation." >&2
  exit 1
fi
if ! gh auth status >/dev/null 2>&1; then
  echo "GitHub CLI is not authenticated for the service user." >&2
  exit 1
fi

origin="$(git remote get-url origin)"
case "$origin" in
  *github.com/ni-da-ba/skyforge.git|*github.com:ni-da-ba/skyforge.git) ;;
  *)
    echo "Refusing shadow install from unexpected origin: $origin" >&2
    exit 1
    ;;
esac

echo "Running one read-only shadow preflight before installing units..."
PYTHONDONTWRITEBYTECODE=1 GH_PROMPT_DISABLED=1   "$VENV_PYTHON" scripts/orchestrator/platform_v2_shadow_cycle.py   --root "$ROOT" --repo "$REPO" >/dev/null

tmp_service="$(mktemp)"
trap 'rm -f "$tmp_service"' EXIT

python3 - "$ROOT" "$SERVICE_USER" "$SERVICE_HOME" "$VENV_PYTHON" >"$tmp_service" <<'PY'
from pathlib import Path
import sys

root, user, home, python = sys.argv[1:]
template = Path("deploy/orchestrator/skyforge-v2-shadow.service.in").read_text()
print(
    template.replace("@@ROOT@@", root)
    .replace("@@USER@@", user)
    .replace("@@HOME@@", home)
    .replace("@@VENV_PYTHON@@", python),
    end="",
)
PY

sudo install -m 0644 "$tmp_service" "$SERVICE_FILE"
sudo install -m 0644 deploy/orchestrator/skyforge-v2-shadow.timer "$TIMER_FILE"
sudo systemctl daemon-reload
sudo systemctl enable --now skyforge-v2-shadow.timer
sudo systemctl start skyforge-v2-shadow.service

echo "Read-only Platform v2 shadow timer installed."
echo "Inspect recent samples with:"
echo "  sudo journalctl -u skyforge-v2-shadow.service --since '-2 hours' --no-pager"
echo "Rollback with:"
echo "  scripts/orchestrator/remove_v2_shadow.sh --confirm"
