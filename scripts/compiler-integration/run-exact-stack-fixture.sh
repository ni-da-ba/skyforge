#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 5 || $# -gt 7 ]]; then
  echo "usage: $0 <gradle-task> <log> <start-marker> <pass-marker> <fail-marker> [boot-timeout-seconds] [fixture-timeout-seconds]" >&2
  exit 2
fi

task=$1
log=$2
start_marker=$3
pass_marker=$4
fail_marker=$5
boot_timeout=${6:-120}
fixture_timeout=${7:-120}

rm -f "${log}"
setsid ./gradlew "${task}" --no-configuration-cache >"${log}" 2>&1 &
pid=$!

cleanup() {
  if kill -0 "${pid}" 2>/dev/null; then
    kill -TERM -- "-${pid}" 2>/dev/null || true
    sleep 1
    kill -KILL -- "-${pid}" 2>/dev/null || true
  fi
  wait "${pid}" 2>/dev/null || true
}
trap cleanup EXIT

fail_with_log() {
  local code=$1
  local expected=$2
  local started=$3
  local deadline=$4
  echo "${code} expectedCondition=\"${expected}\" startEpoch=${started} deadlineEpoch=${deadline} processId=${pid} serverState=gradle-process clientState=headless finalDiagnosticDump=log:${log}" >&2
  tail -n 300 "${log}" >&2 || true
  exit 1
}

boot_started=$(date +%s)
boot_deadline=$((boot_started + boot_timeout))
while true; do
  if grep -Fq "${fail_marker}" "${log}" 2>/dev/null; then
    grep -F "${fail_marker}" "${log}" >&2 || true
    fail_with_log "FAIL_BOOT" "fixture reaches START without classified runtime failure" "${boot_started}" "${boot_deadline}"
  fi
  if grep -Fq "${start_marker}" "${log}" 2>/dev/null; then
    break
  fi
  if ! kill -0 "${pid}" 2>/dev/null; then
    fail_with_log "FAIL_BOOT" "fixture emits START marker" "${boot_started}" "${boot_deadline}"
  fi
  now=$(date +%s)
  if (( now > boot_deadline )); then
    fail_with_log "TIMEOUT_SERVER_BOOT" "fixture emits START marker" "${boot_started}" "${boot_deadline}"
  fi
  sleep 1
done

fixture_started=$(date +%s)
fixture_deadline=$((fixture_started + fixture_timeout))
pass_seen=0
pass_seen_at=0
post_pass_grace=${SKYFORGE_EXACT_STACK_POST_PASS_GRACE_SECONDS:-5}
post_pass_deadline=0
while true; do
  if grep -Fq "${fail_marker}" "${log}" 2>/dev/null; then
    grep -F "${fail_marker}" "${log}" >&2 || true
    tail -n 120 "${log}" >&2 || true
    exit 1
  fi

  if (( pass_seen == 0 )) && grep -Fq "${pass_marker}" "${log}" 2>/dev/null; then
    pass_seen=1
    pass_seen_at=$(date +%s)
    post_pass_deadline=$((pass_seen_at + post_pass_grace))
  fi

  if ! kill -0 "${pid}" 2>/dev/null; then
    set +e
    wait "${pid}"
    process_status=$?
    set -e
    if (( process_status != 0 )); then
      fail_with_log "FAIL_RUNTIME_EXIT" "fixture process remains healthy through terminal observation" "${fixture_started}" "${fixture_deadline}"
    fi
    if (( pass_seen == 1 )); then
      grep -F "${pass_marker}" "${log}"
      exit 0
    fi
    fail_with_log "FAIL_RUNTIME_EXIT" "fixture emits classified PASS before clean process exit" "${fixture_started}" "${fixture_deadline}"
  fi

  now=$(date +%s)
  if (( pass_seen == 1 )); then
    if (( now > post_pass_deadline )); then
      # Exact-stack server fixtures deliberately stay alive after PASS; the runner owns teardown.
      # Observe one bounded grace window first so an immediate late FAIL/crash cannot be masked.
      if grep -Fq "${fail_marker}" "${log}" 2>/dev/null; then
        grep -F "${fail_marker}" "${log}" >&2 || true
        tail -n 120 "${log}" >&2 || true
        exit 1
      fi
      grep -F "${pass_marker}" "${log}"
      cleanup
      trap - EXIT
      exit 0
    fi
  elif (( now > fixture_deadline )); then
    fail_with_log "TIMEOUT_FIXTURE_TERMINAL_STATE" "fixture emits classified PASS or FAIL marker" "${fixture_started}" "${fixture_deadline}"
  fi
  sleep 1
done
