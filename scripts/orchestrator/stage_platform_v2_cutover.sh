#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

if [[ "$(id -u)" == "0" ]]; then
  echo "Run this staging helper as the dedicated Skyforge service user; it requests sudo only for unit installation." >&2
  exit 2
fi

: "${SKYFORGE_ACCEPTED_MAIN_SHA:?Set SKYFORGE_ACCEPTED_MAIN_SHA to the accepted 40-character main SHA.}"
if [[ ! "$SKYFORGE_ACCEPTED_MAIN_SHA" =~ ^[0-9a-f]{40}$ ]]; then
  echo "SKYFORGE_ACCEPTED_MAIN_SHA must be a lowercase 40-character Git SHA." >&2
  exit 2
fi

HEAD_SHA="$(git rev-parse HEAD)"
if [[ "$HEAD_SHA" != "$SKYFORGE_ACCEPTED_MAIN_SHA" ]]; then
  echo "Refusing staging: checkout HEAD differs from accepted main." >&2
  exit 1
fi
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "Refusing staging from a tracked-dirty worktree." >&2
  exit 1
fi

SERVICE_USER="$(id -un)"
SERVICE_GROUP="$(id -gn)"
SERVICE_HOME="$HOME"
VENV_PYTHON="$ROOT/.skyforge-orchestrator/venv/bin/python"
ACTIVATION_EVIDENCE="${SKYFORGE_V2_ACTIVATION_EVIDENCE:-/var/lib/skyforge-orchestrator/platform-v2-activation.json}"
ACTIVATION_DIR="$(dirname "$ACTIVATION_EVIDENCE")"
LEGACY_UNIT="/etc/systemd/system/skyforge-orchestrator.service"
V2_UNIT="/etc/systemd/system/skyforge-orchestrator-v2.service"

if [[ ! -x "$VENV_PYTHON" ]]; then
  echo "Hosted virtualenv is absent: $VENV_PYTHON" >&2
  exit 1
fi
if ! systemctl is-active --quiet skyforge-orchestrator.service; then
  echo "Refusing staging unless the legacy production service is active." >&2
  exit 1
fi
if systemctl is-active --quiet skyforge-orchestrator-v2.service 2>/dev/null; then
  echo "Refusing staging while the Platform-v2 service is active." >&2
  exit 1
fi
LEGACY_PID_BEFORE="$(systemctl show skyforge-orchestrator.service -p MainPID --value)"
if [[ -z "$LEGACY_PID_BEFORE" || "$LEGACY_PID_BEFORE" == "0" ]]; then
  echo "Unable to capture the active legacy MainPID before staging." >&2
  exit 1
fi

tmp_legacy="$(mktemp)"
tmp_v2="$(mktemp)"
trap 'rm -f "$tmp_legacy" "$tmp_v2"' EXIT

python3 - "$ROOT" "$SERVICE_USER" "$SERVICE_HOME" "$VENV_PYTHON" >"$tmp_legacy" <<'PY'
from pathlib import Path
import sys
root, user, home, python = sys.argv[1:]
source = Path("deploy/orchestrator/skyforge-orchestrator.service.in").read_text()
print(source.replace("@@ROOT@@", root).replace("@@USER@@", user)
      .replace("@@HOME@@", home).replace("@@VENV_PYTHON@@", python), end="")
PY
python3 - "$ROOT" "$SERVICE_USER" "$SERVICE_HOME" "$VENV_PYTHON" "$ACTIVATION_EVIDENCE" >"$tmp_v2" <<'PY'
from pathlib import Path
import sys
root, user, home, python, evidence = sys.argv[1:]
source = Path("deploy/orchestrator/skyforge-orchestrator-v2.service.in").read_text()
print(source.replace("@@ROOT@@", root).replace("@@USER@@", user)
      .replace("@@HOME@@", home).replace("@@VENV_PYTHON@@", python)
      .replace("@@ACTIVATION_EVIDENCE@@", evidence), end="")
PY

grep -q -- '--require-startup-reconcile-success' "$tmp_legacy"
grep -q -- 'platform_v2_hosted_runtime.py' "$tmp_v2"
grep -q -- '--enable-production-execution' "$tmp_v2"
grep -Fq -- "$ACTIVATION_EVIDENCE" "$tmp_v2"

sudo install -d -o root -g "$SERVICE_GROUP" -m 2750 "$ACTIVATION_DIR"
read_probe="$ACTIVATION_DIR/.skyforge-read-probe.$$"
sudo install -o root -g "$SERVICE_GROUP" -m 0640 /dev/null "$read_probe"
if [[ ! -r "$read_probe" ]]; then
  sudo rm -f "$read_probe"
  echo "Activation evidence directory is not readable by $SERVICE_USER: $ACTIVATION_DIR" >&2
  exit 1
fi
sudo rm -f "$read_probe"

sudo install -m 0644 "$tmp_legacy" "$LEGACY_UNIT"
sudo install -m 0644 "$tmp_v2" "$V2_UNIT"
sudo systemctl daemon-reload
sudo systemctl disable skyforge-orchestrator-v2.service >/dev/null 2>&1 || true

if ! systemctl is-active --quiet skyforge-orchestrator.service; then
  echo "Legacy service stopped during staging; treat as an incident." >&2
  exit 1
fi
LEGACY_PID_AFTER="$(systemctl show skyforge-orchestrator.service -p MainPID --value)"
if [[ "$LEGACY_PID_AFTER" != "$LEGACY_PID_BEFORE" ]]; then
  echo "Legacy service PID changed during staging; treat as an incident." >&2
  exit 1
fi
if systemctl is-active --quiet skyforge-orchestrator-v2.service 2>/dev/null; then
  echo "Platform-v2 became active during staging; treat as an incident." >&2
  exit 1
fi

echo "R5C26 unit staging complete. Writer authority remains LEGACY."
echo "Next: run platform_v2_operator_cutover.py preflight with the reviewed activation template."
