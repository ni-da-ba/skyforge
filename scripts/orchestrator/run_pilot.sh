#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

export SKYFORGE_ORCHESTRATOR_DEDICATED_CLONE=1

STATE_DIR="$ROOT/.skyforge-orchestrator"
VENV="$STATE_DIR/venv"
PORT="${SKYFORGE_ORCHESTRATOR_PORT:-3000}"

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required." >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "GitHub CLI is not authenticated. Run: gh auth login" >&2
  exit 1
fi

if ! gh webhook --help >/dev/null 2>&1; then
  echo "The gh-webhook extension is required." >&2
  echo "Install it after reviewing the extension source: gh extension install cli/gh-webhook" >&2
  exit 1
fi

if [[ ! -x "$VENV/bin/python" ]]; then
  python3 -m venv "$VENV"
  "$VENV/bin/python" -m pip install --upgrade pip
  "$VENV/bin/python" -m pip install -r scripts/orchestrator/requirements.txt
fi

"$VENV/bin/python" scripts/orchestrator/skyforge_orchestrator.py \
  --root "$ROOT" \
  --repo ni-da-ba/skyforge \
  --port "$PORT" &
SERVER_PID=$!

cleanup() {
  kill "$SERVER_PID" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

sleep 1
if ! kill -0 "$SERVER_PID" >/dev/null 2>&1; then
  echo "Orchestrator server exited during startup." >&2
  wait "$SERVER_PID"
fi

echo "Forwarding selected Skyforge webhooks to localhost:$PORT."
echo "This is a development pilot; do not use gh webhook forward as production infrastructure."

gh webhook forward \
  --repo ni-da-ba/skyforge \
  --events push,pull_request,workflow_run,issue_comment \
  --url "http://127.0.0.1:$PORT/webhook"
