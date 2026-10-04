set -euo pipefail
: "${WBY_S4_PRODUCTION_SERVER_DIR:?Set WBY_S4_PRODUCTION_SERVER_DIR to the installed NeoForge server directory}"
server_dir="$WBY_S4_PRODUCTION_SERVER_DIR"
server_java="${JAVA_HOME:+$JAVA_HOME/bin/java}"
[[ -n "$server_java" ]] || server_java="$(command -v java)"
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
    [[ ! -f "$server_dir/s4-acceptance/level.dat" ]] || cp "$server_dir/s4-acceptance/level.dat" wby-s4-joined-world-level.dat
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


assert_hearthandharvest_policy() {
  local config="$1"
  test -f "$config"
  awk '
    function trim(value) {
      sub(/^[[:space:]]+/, "", value)
      sub(/[[:space:]]+$/, "", value)
      return value
    }
    BEGIN {
      expected["generateCornMazes"] = "false"
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
      gsub(/[[:space:]]/,"", value)
      full_key = (index(key, ".") ? key : (section == "" ? key : section "." key))
      if (full_key in expected && value == expected[full_key]) found[full_key] = 1
    }
    END {
      for (key in expected) {
        if (!found[key]) {
          print "Hearth and Harvest worldgen policy missing or incorrect: " key " (expected " expected[key] ")" > "/dev/stderr"
          exit 1
        }
      }
    }
  ' "$config"
}

start_server() {
  local log_path="$1"
  setsid bash -c 'cd "$1"; exec "$2" @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui' _ "$server_dir" "$server_java" >"$log_path" 2>&1 &
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
  kill -TERM -- "-$server_pid" 2>/dev/null || true
  for _ in $(seq 1 90); do
    if ! kill -0 "$server_pid" 2>/dev/null; then break; fi
    sleep 1
  done
  if kill -0 "$server_pid" 2>/dev/null; then cat "$log_path"; return 1; fi
  wait "$server_pid" 2>/dev/null || true
  server_pid=""
}

assert_hearthandharvest_worldgen_policy() {
  local data="$server_dir/kubejs/data"
  local nests="$data/skyforge/neoforge/biome_modifier/disable_hearthandharvest_nests.json"
  local salt="$data/skyforge/neoforge/biome_modifier/disable_hearthandharvest_salt_caves.json"
  local lilliput="$data/hearthandharvest/tags/worldgen/biome/has_structure/lilliput_lane.json"
  local maze="$data/hearthandharvest/tags/worldgen/biome/has_structure/corn_maze.json"
  test -s "$nests" && grep -Fq '"features": "hearthandharvest:nest"' "$nests"
  test -s "$salt" && grep -Fq '"features": "hearthandharvest:salt_cave"' "$salt"
  test -s "$lilliput" && grep -Fq '"replace": true' "$lilliput" && grep -Fq '"values": []' "$lilliput"
  test -s "$maze" && grep -Fq '"replace": true' "$maze" && grep -Fq '"values": []' "$maze"
}

start_server "$server_log"
test -f "$server_dir/s4-acceptance/level.dat"
assert_supplementaries_policy
assert_hearthandharvest_policy "$server_dir/config/hearthandharvest-common.toml"
assert_hearthandharvest_worldgen_policy
grep -Fq 'WBY S4 ORDINARY LIFE RESOLUTION PASS' wby-s4-gameplay-resolution.log

client_dir_abs="$PWD/$client_dir"
launcher_python="${WBY_S4_LAUNCHER_PYTHON:-python3}"
minecraft_dir="${WBY_S4_MINECRAFT_DIRECTORY:-$GITHUB_WORKSPACE/s4-minecraft}"
setsid env ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe \
  xvfb-run -a "$launcher_python" scripts/wby-s4-launch-production-client.py \
    --minecraft-directory "$minecraft_dir" \
    --game-directory "$client_dir_abs" \
    --username WbyS1Acceptance \
    --server 127.0.0.1:25565 \
    --java "$JAVA_HOME/bin/java" \
    --program-args-file "$GITHUB_WORKSPACE/wby-s4-client-program-args.txt" \
    >"$client_log" 2>&1 &
client_pid=$!
client_args_file="wby-s4-client-program-args.txt"
for _ in $(seq 1 900); do
  [[ ! -s "$client_args_file" ]] || break
  if ! kill -0 "$client_pid" 2>/dev/null; then cat "$client_log"; exit 1; fi
  sleep 1
done
test -s "$client_args_file"
cp "$client_args_file" wby-s4-client-program-args.txt
grep -Fq -- '--quickPlayMultiplayer' "$client_args_file"
grep -Fq -- '127.0.0.1:25565' "$client_args_file"
grep -Fq -- 'WbyS1Acceptance' "$client_args_file"
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
test -f "$server_dir/s4-acceptance/level.dat"
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
test -f "$server_dir/s4-acceptance/level.dat"

server_log="wby-s4-server-reopen.log"
start_server "$server_log"
test -f "$server_dir/s4-acceptance/level.dat"
assert_supplementaries_policy
assert_hearthandharvest_policy "$server_dir/config/hearthandharvest-common.toml"
assert_hearthandharvest_worldgen_policy
stop_server "$server_log"
grep -Fq 'Stopping server' "$server_log"
trap - EXIT
echo "WBY S4 ACTUAL CLIENT JOIN AND SAME-WORLD REOPEN PASS"
