#!/usr/bin/env bash
set -euo pipefail

mode="${1:-client}"
glider="${2:-none}"
clouds="${3:-none}"
thinair="${4:-false}"

case "$mode" in
  client)
    task=":skyforge-neoforge-1211:runWbyS1DiagnosticClient"
    ;;
  server)
    task=":skyforge-neoforge-1211:runWbyS1DiagnosticServer"
    ;;
  *)
    echo "usage: $0 [client|server] [none|hang-glider|ornithopter] [none|better-clouds] [true|false]" >&2
    exit 2
    ;;
esac

case "$glider" in none|hang-glider|ornithopter) ;; *) echo "invalid glider profile: $glider" >&2; exit 2 ;; esac
case "$clouds" in none|better-clouds) ;; *) echo "invalid cloud profile: $clouds" >&2; exit 2 ;; esac
case "$thinair" in true|false) ;; *) echo "ThinAir must be true or false" >&2; exit 2 ;; esac

run_dir="skyforge-neoforge-1211/run-wby-s1"
mkdir -p .skyforge-diagnostics "$run_dir"
if [[ "$mode" == "server" ]]; then
  printf 'eula=true\n' > "$run_dir/eula.txt"
fi

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
bundle=".skyforge-diagnostics/wby-s1-$mode-$glider-$clouds-thinair-$thinair-$stamp"
mkdir -p "$bundle"
log="$bundle/console.log"

echo "Launching WBY S1 $mode profile."
echo "glider=$glider clouds=$clouds thinAir=$thinair"
echo "The client opens to the title screen. Create or open a fresh world for the human review."
echo "No S0.5 synthetic visibility/structure fixture is enabled."
echo "Full console output: $log"

set +e
./gradlew "$task" \
  -PwbyS1Glider="$glider" \
  -PwbyS1Clouds="$clouds" \
  -PwbyS1ThinAir="$thinair" \
  --no-configuration-cache 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
set -e

test ! -f "$run_dir/logs/latest.log" || cp "$run_dir/logs/latest.log" "$bundle/latest.log"
test ! -d "$run_dir/crash-reports" || cp -R "$run_dir/crash-reports" "$bundle/crash-reports"
test ! -d "$run_dir/config" || cp -R "$run_dir/config" "$bundle/config"
test ! -d "$run_dir/mods" || find "$run_dir/mods" -maxdepth 1 -type f -name '*.jar' -printf '%f\n' | sort > "$bundle/staged-mods.txt"

exit "$status"
