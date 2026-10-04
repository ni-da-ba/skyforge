set -euo pipefail
server_dir="skyforge-neoforge-1211/run-wby-s4-join-server"
client_dir="skyforge-neoforge-1211/run-wby-s4-join-client"
mkdir -p "$server_dir" "$client_dir"
printf 'eula=true\n' > "$server_dir/eula.txt"
cat > "$server_dir/server.properties" <<'EOF'
level-name=s4-acceptance
level-seed=817304
level-type=minecraft:flat
online-mode=false
enforce-secure-profile=false
spawn-protection=0
gamemode=creative
difficulty=peaceful
view-distance=5
simulation-distance=3
max-tick-time=0
max-players=1
server-port=25565
EOF
printf 'onboardAccessibility:false\n' > "$client_dir/options.txt"
mkdir -p "$client_dir/config"
cat > "$client_dir/config/DistantHorizons.toml" <<'EOF'
[client.advanced.autoUpdater]
enableAutoUpdater = false
EOF

server_log="wby-s4-join-server.log"
client_log="wby-s4-join-client.log"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dskyforge.dev.waveC25PetroleumAuthority=suppressed"
server_pid=""
client_pid=""
cleanup() {
  status=$?
  set +e
  for pid in "$client_pid" "$server_pid"; do
    if [[ -n "$pid" ]]; then kill -TERM -- "-$pid" 2>/dev/null || true; fi
  done
  sleep 2
  for pid in "$client_pid" "$server_pid"; do
    if [[ -n "$pid" ]]; then kill -KILL -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; fi
  done
  if [[ "$status" -ne 0 ]]; then
    for log in "$server_log" "$client_log" "$server_dir/logs/latest.log" "$client_dir/logs/latest.log"; do
      if [[ -f "$log" ]]; then echo "--- $log ---"; tail -n 180 "$log"; fi
    done
    [[ ! -f "$server_dir/world/level.dat" ]] || cp "$server_dir/world/level.dat" wby-s4-joined-world-level.dat
  fi
  return "$status"
}
trap cleanup EXIT

assert_supplementaries_policy() {
  local config="$server_dir/config/supplementaries-common.toml"
  test -f "$config"
  awk '
    function trim(value) {
      sub(/^[[:space:]]+/, "", value)
      sub(/[[:space:]]+$/, "", value)
      return value
    }
    BEGIN {
      expected["building.way_sign.road_signs.enabled"] = "false"
      expected["building.ash.basalt_ash"] = "false"
      expected["functional.urn.cave_urns"] = "false"
      expected["functional.flax.wild_flax"] = "false"
      expected["functional.cannon.plunderer.galleon"] = "false"
      expected["redstone.pulley_block.mineshaft_elevator"] = "0.0"
    }
    /^[[:space:]]*#/ { next }
    /^[[:space:]]*\[/ {
      section = $0
      sub(/^[[:space:]]*\[/, "", section)
      sub(/\][[:space:]]*$/, "", section)
      gsub(/[[:space:]]/, "", section)
      next
    }
    index($0, "=") {
      line = $0
      sub(/[[:space:]]*#.*/, "", line)
      split_at = index(line, "=")
      key = trim(substr(line, 1, split_at - 1))
      value = trim(substr(line, split_at + 1))
      gsub(/[[:space:]]/, "", key)
      gsub(/[[:space:]\"]/, "", value)
      full_key = (index(key, ".") ? key : (section == "" ? key : section "." key))
      if (full_key in expected && value == expected[full_key]) found[full_key] = 1
    }
    END {
      for (key in expected) {
        if (!found[key]) {
          print "Supplementaries policy missing or incorrect: " key " (expected " expected[key] ")" > "/dev/stderr"
          exit 1
        }
      }
    }
  ' "$config"
}

start_server() {
  local log_path="$1"
  setsid ./gradlew --no-daemon :skyforge-neoforge-1211:runWbyS1DiagnosticServer \
    -PwbyS1RunDirectory=run-wby-s4-join-server \
    -PwbyS1A4mcBuiltModDir="$GITHUB_WORKSPACE/a4mc-0.2.2" \
    -PwbyS1Glider=combined -PwbyS1Clouds=simple-clouds \
    -PwbyS2ComputingAvionics=true -PwbyS3DieselGenerators=true \
    -PwbyS3CreateBigCannons=true -PwbyS4OrdinaryLife=true \
    --no-configuration-cache >"$log_path" 2>&1 &
  server_pid=$!
  for _ in $(seq 1 180); do
    if grep -Fq 'Done (' "$log_path" 2>/dev/null; then return 0; fi
    if ! kill -0 "$server_pid" 2>/dev/null; then cat "$log_path"; return 1; fi
    sleep 2
  done
  cat "$log_path"
  echo "S4 server did not reach Done: $log_path" >&2
  return 1
}

stop_server() {
  local log_path="$1"
  local server_jvms
  server_jvms="$(ps -eo pid=,comm=,args= | awk '$2 ~ /^java/ && /net.neoforged.devlaunch.Main/ && /wbyS1DiagnosticServerRunVmArgs.txt/ {print $1}')"
  test -n "$server_jvms"
  while IFS= read -r java_pid; do [[ -n "$java_pid" ]] && kill -TERM "$java_pid"; done <<< "$server_jvms"
  local stopped=false
  for _ in $(seq 1 90); do
    if grep -Fq 'Stopping server' "$log_path" 2>/dev/null; then stopped=true; break; fi
    sleep 1
  done
  if [[ "$stopped" != true ]]; then cat "$log_path"; return 1; fi
  for _ in $(seq 1 60); do
    if ! kill -0 -- "-$server_pid" 2>/dev/null; then break; fi
    sleep 1
  done
  if kill -0 -- "-$server_pid" 2>/dev/null; then cat "$log_path"; return 1; fi
  wait "$server_pid" 2>/dev/null || true
  server_pid=""
}

start_server "$server_log"
test -f "$server_dir/world/level.dat"
assert_supplementaries_policy
grep -Fq 'WBY S4 ORDINARY LIFE RESOLUTION PASS' wby-s4-gameplay-resolution.log

setsid env ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe \
  xvfb-run -a ./gradlew --no-daemon :skyforge-neoforge-1211:runWbyS1ClientJoinAcceptance \
    -PwbyS1RunDirectory=run-wby-s4-join-client \
    -PwbyS1A4mcBuiltModDir="$GITHUB_WORKSPACE/a4mc-0.2.2" \
    -PwbyS1Glider=combined -PwbyS1Clouds=simple-clouds \
    -PwbyS2ComputingAvionics=true -PwbyS3DieselGenerators=true \
    -PwbyS3CreateBigCannons=true -PwbyS4OrdinaryLife=true \
    --no-configuration-cache >"$client_log" 2>&1 &
client_pid=$!
client_args_file="skyforge-neoforge-1211/build/moddev/wbyS1ClientJoinAcceptanceRunProgramArgs.txt"
for _ in $(seq 1 120); do
  [[ ! -s "$client_args_file" ]] || break
  if ! kill -0 "$client_pid" 2>/dev/null; then cat "$client_log"; exit 1; fi
  sleep 1
done
test -s "$client_args_file"
cp "$client_args_file" wby-s4-client-program-args.txt
grep -Fq -- '--quickPlayMultiplayer' "$client_args_file"
grep -Fq -- '127.0.0.1:25565' "$client_args_file"
grep -Fq -- 'WbyS1Acceptance' "$client_args_file"

server_latest_log="$server_dir/logs/latest.log"
joined=false
for _ in $(seq 1 600); do
  if grep -Fq 'WbyS1Acceptance joined the game' "$server_log" || \
    grep -Fq 'WbyS1Acceptance joined the game' "$server_latest_log" 2>/dev/null; then joined=true; break; fi
  if ! kill -0 "$client_pid" 2>/dev/null; then cat "$client_log"; exit 1; fi
  if ! kill -0 "$server_pid" 2>/dev/null; then cat "$server_log"; exit 1; fi
  sleep 1
done
test "$joined" = true
grep -Fq '(farmersdelight)' "$server_latest_log"
grep -Fq '(supplementaries)' "$server_latest_log"
grep -Fq '(create_central_kitchen)' "$server_latest_log" || grep -Fq '(createcentral_kitchen)' "$server_latest_log"
grep -Fq '(hearthandharvest)' "$server_latest_log"
test -f "$server_dir/world/level.dat"
cp "$server_latest_log" wby-s4-joined-server-latest.log
client_latest_log="$client_dir/logs/latest.log"
test -f "$client_latest_log"
cp "$client_latest_log" wby-s4-joined-client-latest.log

stop_server "$server_log"
kill -INT -- "-$client_pid" 2>/dev/null || true
sleep 3
kill -TERM -- "-$client_pid" 2>/dev/null || true
wait "$client_pid" 2>/dev/null || true
client_pid=""
test -f "$server_dir/world/level.dat"

server_log="wby-s4-server-reopen.log"
start_server "$server_log"
test -f "$server_dir/world/level.dat"
assert_supplementaries_policy
stop_server "$server_log"
grep -Fq 'Stopping server' "$server_log"
trap - EXIT
echo "WBY S4 ACTUAL CLIENT JOIN AND SAME-WORLD REOPEN PASS"
