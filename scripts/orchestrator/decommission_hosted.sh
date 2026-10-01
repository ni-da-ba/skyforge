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
failure_dir="$ROOT/.skyforge-orchestrator/reports"
mkdir -p "$failure_dir"

report_failed=0
webhook_cleanup_failed=0

record_cleanup_failure() {
  local kind="$1"
  local message="$2"
  local failure_file="$failure_dir/${kind}_$(date -u +%Y%m%dT%H%M%SZ).txt"
  {
    echo "$message"
    echo "Teardown continued intentionally so telemetry/control-plane cleanup failure cannot trap continuing infrastructure cost."
    echo "Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  } >"$failure_file"
  echo "WARNING: $message Local evidence: $failure_file" >&2
}

hostname="${SKYFORGE_PUBLIC_HOSTNAME:-}"
if [[ -z "$hostname" && -r "$CADDY_FILE" ]]; then
  hostname="$(awk '/^[[:space:]]*[^#[:space:]].*\{[[:space:]]*$/ {gsub(/[[:space:]]*\{[[:space:]]*$/, ""); gsub(/^[[:space:]]+|[[:space:]]+$/, ""); print; exit}' "$CADDY_FILE")"
fi
if [[ -z "$hostname" ]]; then
  hostname="$(sudo awk '/^[[:space:]]*[^#[:space:]].*\{[[:space:]]*$/ {gsub(/[[:space:]]*\{[[:space:]]*$/, ""); gsub(/^[[:space:]]+|[[:space:]]+$/, ""); print; exit}' "$CADDY_FILE" 2>/dev/null || true)"
fi
if [[ -z "$hostname" ]]; then
  webhook_cleanup_failed=1
  record_cleanup_failure "WEBHOOK_CLEANUP_SKIPPED" "Could not determine the hosted public hostname; repository webhook cleanup must be verified externally."
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
if [[ -x "$VENV_PYTHON" ]]; then
  if ! SKYFORGE_DROPLET_HOURLY_USD="$hourly" SKYFORGE_VALUE_REPORT_ISSUE="$issue" "$VENV_PYTHON" scripts/orchestrator/daily_value_report.py --root "$ROOT" --repo "$REPO" --issue "$issue"; then
    report_failed=1
    record_cleanup_failure "FINAL_REPORT_POST_FAILED" "Final hosted value report could not be completed or posted during decommission."
  fi
else
  report_failed=1
  record_cleanup_failure "FINAL_REPORT_UNAVAILABLE" "Hosted reporting virtualenv was unavailable at $VENV_PYTHON."
fi

if [[ -n "$hostname" ]]; then
  webhook_url="https://$hostname/webhook"
  hook_output=""
  if hook_output="$(gh api "repos/$REPO/hooks" --paginate --jq ".[] | select(.config.url == \"$webhook_url\") | .id" 2>/dev/null)"; then
    hook_ids=()
    while IFS= read -r hook_id; do
      [[ -n "$hook_id" ]] && hook_ids+=("$hook_id")
    done <<<"$hook_output"

    for hook_id in "${hook_ids[@]}"; do
      if gh api --method DELETE "repos/$REPO/hooks/$hook_id" >/dev/null 2>&1; then
        echo "Deleted GitHub webhook #$hook_id."
      else
        webhook_cleanup_failed=1
        record_cleanup_failure "WEBHOOK_DELETE_FAILED" "Could not delete GitHub webhook #$hook_id; verify it externally after provider teardown."
      fi
    done

    if [[ "${#hook_ids[@]}" -eq 0 ]]; then
      echo "No matching GitHub webhook remained at $webhook_url."
    fi
  else
    webhook_cleanup_failed=1
    record_cleanup_failure "WEBHOOK_LIST_FAILED" "Could not list GitHub repository webhooks; verify the hosted webhook is removed externally."
  fi
fi

# Local shutdown is unconditional. Cleanup/reporting failures above must never keep paid compute active.
sudo systemctl disable --now skyforge-value-report.timer 2>/dev/null || true
sudo systemctl stop skyforge-value-report.service 2>/dev/null || true
sudo systemctl disable --now skyforge-orchestrator.service 2>/dev/null || true
sudo systemctl disable --now caddy.service 2>/dev/null || true

echo
echo "Hosted Skyforge local processes are disabled."
echo "Final reports/cleanup evidence remain under: $failure_dir/"
echo
echo "FINAL PROVIDER STEP REQUIRED:"
echo "  Destroy the DigitalOcean Droplet from the provider control plane."
echo "  Then verify no pilot snapshot, backup, volume, reserved IP, load balancer,"
echo "  database, or other separately billable resource remains."
if [[ "$report_failed" == "1" ]]; then
  echo "WARNING: final value-report completion failed; local failure evidence was retained."
fi
if [[ "$webhook_cleanup_failed" == "1" ]]; then
  echo "WARNING: GitHub webhook cleanup was incomplete; verify/delete the hosted webhook externally."
fi
echo "Do not treat a powered-off Droplet as cancelled; provider destruction is required."
