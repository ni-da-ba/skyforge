#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

REPO="ni-da-ba/skyforge"
SERVICE_USER="$(id -un)"
SERVICE_HOME="$HOME"
STATE_DIR="$ROOT/.skyforge-orchestrator"
VENV="$STATE_DIR/venv"
VENV_PYTHON="$VENV/bin/python"
CONFIG_DIR="/etc/skyforge-orchestrator"
ENV_FILE="$CONFIG_DIR/env"
SERVICE_FILE="/etc/systemd/system/skyforge-orchestrator.service"
CADDY_FILE="/etc/caddy/Caddyfile"

: "${SKYFORGE_PUBLIC_HOSTNAME:?Set SKYFORGE_PUBLIC_HOSTNAME to the public HTTPS hostname.}"
: "${SKYFORGE_WEBHOOK_SECRET:?Set SKYFORGE_WEBHOOK_SECRET to a high-entropy webhook secret.}"

if [[ "${#SKYFORGE_WEBHOOK_SECRET}" -lt 32 ]]; then
  echo "SKYFORGE_WEBHOOK_SECRET must be at least 32 characters." >&2
  exit 1
fi

for command in git gh python3 sudo caddy curl; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Required command is missing: $command" >&2
    exit 1
  fi
done

if ! gh auth status >/dev/null 2>&1; then
  echo "GitHub CLI is not authenticated for the service user. Run: gh auth login" >&2
  exit 1
fi

origin="$(git remote get-url origin)"
case "$origin" in
  *github.com/ni-da-ba/skyforge.git|*github.com:ni-da-ba/skyforge.git) ;;
  *)
    echo "Refusing hosted install from unexpected origin: $origin" >&2
    exit 1
    ;;
esac

mkdir -p "$STATE_DIR"
if [[ ! -x "$VENV_PYTHON" ]]; then
  python3 -m venv "$VENV"
  "$VENV_PYTHON" -m pip install --upgrade pip
  "$VENV_PYTHON" -m pip install -r scripts/orchestrator/requirements.txt
fi

tmp_service="$(mktemp)"
tmp_caddy="$(mktemp)"
tmp_env="$(mktemp)"
trap 'rm -f "$tmp_service" "$tmp_caddy" "$tmp_env"' EXIT

python3 - "$ROOT" "$SERVICE_USER" "$SERVICE_HOME" "$VENV_PYTHON" >"$tmp_service" <<'PY'
from pathlib import Path
import sys

root, user, home, python = sys.argv[1:]
template = Path("deploy/orchestrator/skyforge-orchestrator.service.in").read_text()
print(
    template.replace("@@ROOT@@", root)
    .replace("@@USER@@", user)
    .replace("@@HOME@@", home)
    .replace("@@VENV_PYTHON@@", python),
    end="",
)
PY

python3 - "$SKYFORGE_PUBLIC_HOSTNAME" >"$tmp_caddy" <<'PY'
from pathlib import Path
import sys

hostname = sys.argv[1]
template = Path("deploy/orchestrator/Caddyfile.in").read_text()
print(template.replace("@@HOSTNAME@@", hostname), end="")
PY

umask 077
cat >"$tmp_env" <<EOF
SKYFORGE_ORCHESTRATOR_DEDICATED_CLONE=1
SKYFORGE_REQUIRE_WEBHOOK_SECRET=1
SKYFORGE_STARTUP_RECONCILE=1
SKYFORGE_ORCHESTRATOR_AUTO_MERGE=0
SKYFORGE_WEBHOOK_SECRET=$SKYFORGE_WEBHOOK_SECRET
SKYFORGE_ORCHESTRATOR_MAX_CLASSIFIER_CALLS_PER_DAY=${SKYFORGE_ORCHESTRATOR_MAX_CLASSIFIER_CALLS_PER_DAY:-48}
SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY=${SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY:-8}
EOF

sudo install -d -m 0700 "$CONFIG_DIR"
sudo install -m 0600 "$tmp_env" "$ENV_FILE"
sudo install -m 0644 "$tmp_service" "$SERVICE_FILE"
sudo install -d -m 0755 /etc/caddy
sudo install -m 0644 "$tmp_caddy" "$CADDY_FILE"

sudo systemctl daemon-reload
sudo systemctl enable --now skyforge-orchestrator.service
sudo systemctl enable --now caddy.service
sudo systemctl restart skyforge-orchestrator.service
sudo systemctl reload caddy.service

health_url="https://$SKYFORGE_PUBLIC_HOSTNAME/healthz"
webhook_url="https://$SKYFORGE_PUBLIC_HOSTNAME/webhook"

echo "Waiting for trusted HTTPS health endpoint: $health_url"
healthy=0
for _ in $(seq 1 30); do
  if curl --fail --silent --show-error "$health_url" >/dev/null 2>&1; then
    healthy=1
    break
  fi
  sleep 2
done
if [[ "$healthy" != "1" ]]; then
  echo "Hosted health endpoint did not become ready. Inspect:" >&2
  echo "  sudo systemctl status skyforge-orchestrator caddy" >&2
  echo "  sudo journalctl -u skyforge-orchestrator -u caddy --since '-10 min'" >&2
  exit 1
fi

existing_hook_id="$(
  gh api "repos/$REPO/hooks" --paginate     --jq ".[] | select(.config.url == \"$webhook_url\") | .id"     | head -n 1
)"

export SKYFORGE_HOOK_URL="$webhook_url"
hook_payload="$(
  python3 - <<'PY'
import json
import os

print(json.dumps({
    "name": "web",
    "active": True,
    "events": ["push", "pull_request", "workflow_run", "issue_comment"],
    "config": {
        "url": os.environ["SKYFORGE_HOOK_URL"],
        "content_type": "json",
        "insecure_ssl": "0",
        "secret": os.environ["SKYFORGE_WEBHOOK_SECRET"],
    },
}))
PY
)"

if [[ -n "$existing_hook_id" ]]; then
  printf '%s' "$hook_payload"     | gh api --method PATCH "repos/$REPO/hooks/$existing_hook_id" --input - >/dev/null
  hook_id="$existing_hook_id"
  echo "Updated existing repository webhook #$hook_id."
else
  hook_id="$(
    printf '%s' "$hook_payload"       | gh api --method POST "repos/$REPO/hooks" --input - --jq '.id'
  )"
  echo "Created repository webhook #$hook_id."
fi
unset hook_payload SKYFORGE_HOOK_URL

echo "Hosted Skyforge orchestrator installed."
echo "Health:  $health_url"
echo "Webhook: $webhook_url"
echo "Hook ID:  $hook_id"
echo
echo "Verify the latest GitHub delivery with:"
echo "  gh api repos/$REPO/hooks/$hook_id/deliveries --jq '.[0] | {status_code,event,delivered_at}'"
echo
echo "Auto-merge remains disabled. AUDIT-0009 daily call ceilings remain in force."
