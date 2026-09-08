#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" != "--confirm" ]]; then
  echo "Usage: $0 --confirm" >&2
  echo "This removes the Skyforge GitHub webhook and disables hosted services." >&2
  echo "It does NOT delete the DigitalOcean Droplet; provider-side destruction is the final explicit step." >&2
  exit 2
fi

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

REPO="ni-da-ba/skyforge"
VALUE_ENV_FILE="/etc/skyforge-orchestrator/value.env"
CADDY_FILE="/etc/caddy/Caddyfile"
VENV_PYTHON="$ROOT/.skyforge-orchestrator/venv/bin/python"

if [[ ! -x "$VENV_PYTHON" ]]; then
  echo "Hosted virtualenv not found at $VENV_PYTHON" >&2
  exit 1
fi

hostname="${SKYFORGE_PUBLIC_HOSTNAME:-}"
if [[ -z "$hostname" && -r "$CADDY_FILE" ]]; then
  hostname="$(awk '/^[[:space:]]*[^#[:space:]].*\{[[:space:]]*$/ {gsub(/[[:space:]]*\{[[:space:]]*$/, ""); gsub(/^[[:space:]]+|[[:space:]]+$/, ""); print; exit}' "$CADDY_FILE")"
fi
if [[ -z "$hostname" ]]; then
  hostname="$(sudo awk '/^[[:space:]]*[^#[:space:]].*\{[[:space:]]*$/ {gsub(/[[:space:]]*\{[[:space:]]*$/, ""); gsub(/^[[:space:]]+|[[:space:]]+$/, ""); print; exit}' "$CADDY_FILE" 2>/dev/null || true)"
fi
if [[ -z "$hostname" ]]; then
  echo "Could not determine the hosted public hostname." >&2
  exit 1
fi

hourly="${SKYFORGE_DROPLET_HOURLY_USD:-}"
issue="${SKYFORGE_VALUE_REPORT_ISSUE:-378}"
if [[ -f "$VALUE_ENV_FILE" ]]; then
  if [[ -z "$hourly" ]]; then
    hourly="$(sed -n 's/^SKYFORGE_DROPLET_HOURLY_USD=//p' "$VALUE_ENV_FILE" | head -n 1)"
  fi
  stored_issue="$(sed -n 's/^SKYFORGE_VALUE_REPORT_ISSUE=//p' "$VALUE_ENV_FILE" | head -n 1)"
  if [[ -n "$stored_issue" ]]; then
    issue="$stored_issue"
  fi
fi

echo "Writing final hosted value report before teardown..."
SKYFORGE_DROPLET_HOURLY_USD="$hourly" SKYFORGE_VALUE_REPORT_ISSUE="$issue" "$VENV_PYTHON" scripts/orchestrator/daily_value_report.py   --root "$ROOT" --repo "$REPO" --issue "$issue" || {
    echo "Final report failed; refusing teardown so the accounting boundary is not silently lost." >&2
    exit 1
  }

webhook_url="https://$hostname/webhook"
mapfile -t hook_ids < <(
  gh api "repos/$REPO/hooks" --paginate     --jq ".[] | select(.config.url == \"$webhook_url\") | .id"
)

for hook_id in "${hook_ids[@]}"; do
  gh api --method DELETE "repos/$REPO/hooks/$hook_id" >/dev/null
  echo "Deleted GitHub webhook #$hook_id."
done

if [[ "${#hook_ids[@]}" -eq 0 ]]; then
  echo "No matching GitHub webhook remained at $webhook_url."
fi

sudo systemctl disable --now skyforge-value-report.timer 2>/dev/null || true
sudo systemctl stop skyforge-value-report.service 2>/dev/null || true
sudo systemctl disable --now skyforge-orchestrator.service 2>/dev/null || true
sudo systemctl disable --now caddy.service 2>/dev/null || true

echo
echo "Hosted Skyforge processes and repository webhook are disabled."
echo "Final reports remain under: $ROOT/.skyforge-orchestrator/reports/"
echo
echo "FINAL PROVIDER STEP REQUIRED:"
echo "  Destroy the DigitalOcean Droplet from the provider control plane."
echo "  Then verify no pilot snapshot, backup, volume, reserved IP, load balancer,"
echo "  database, or other separately billable resource remains."
echo
echo "Do not treat a powered-off Droplet as cancelled; provider destruction is required."
