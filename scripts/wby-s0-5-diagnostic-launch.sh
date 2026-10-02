#!/usr/bin/env bash
set -euo pipefail

mode="${1:-client}"
case "$mode" in
  client)
    task=":skyforge-neoforge-1211:runWbyS05BDiagnosticClient"
    ;;
  server)
    task=":skyforge-neoforge-1211:runWbyS05BDiagnosticServer"
    ;;
  *)
    echo "usage: $0 [client|server]" >&2
    exit 2
    ;;
esac

mkdir -p .skyforge-diagnostics
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
log=".skyforge-diagnostics/wby-s05-${mode}-${stamp}.log"

echo "Launching converged WBY S0.5 ${mode} profile."
echo "Full console output: ${log}"
echo "Tune nothing during this pass; reproduce and record cross-mod behavior first."

set +e
./gradlew "$task" --no-configuration-cache 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
set -e

run_dir="skyforge-neoforge-1211/run-wby-s05b"
bundle=".skyforge-diagnostics/wby-s05-${mode}-${stamp}"
mkdir -p "$bundle"
cp "$log" "$bundle/console.log"
test -f "$run_dir/logs/latest.log" && cp "$run_dir/logs/latest.log" "$bundle/latest.log"
test -d "$run_dir/crash-reports" && cp -R "$run_dir/crash-reports" "$bundle/crash-reports"
test -d "$run_dir/config" && cp -R "$run_dir/config" "$bundle/config"
find "$run_dir/mods" -maxdepth 1 -type f -printf '%f\n' 2>/dev/null | sort > "$bundle/mods.txt" || true

echo "Diagnostic bundle: $bundle"
exit "$status"
