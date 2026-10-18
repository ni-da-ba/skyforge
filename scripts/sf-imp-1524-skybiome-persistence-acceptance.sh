#!/usr/bin/env bash
set -euo pipefail -x

server_dir="skyforge-neoforge-1211/run-sf-imp-1524-skybiome"
result_dir="skyforge-neoforge-1211/build/acceptance/sf-imp-1524-skybiome"
task_log="sf-imp-1524-skybiome-task.log"
write_task=":skyforge-neoforge-1211:runSkyBiomeZonePersistenceWrite"
reload_task=":skyforge-neoforge-1211:runSkyBiomeZonePersistenceReload"
server_pid=""

rm -rf "$server_dir" "$result_dir"
mkdir -p "$server_dir"
printf 'eula=true\n' > "$server_dir/eula.txt"
cat > "$server_dir/server.properties" <<'EOF'
level-name=acceptance
level-seed=592850352
online-mode=false
enforce-secure-profile=false
spawn-protection=0
gamemode=creative
difficulty=peaceful
view-distance=3
simulation-distance=2
max-tick-time=0
max-players=1
server-port=25565
EOF

cleanup() {
  status=$?
  set +e
  if [[ -n "$server_pid" ]]; then
    kill -TERM -- "-$server_pid" 2>/dev/null || true
    sleep 2
    kill -KILL -- "-$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
  if [[ "$status" -ne 0 ]]; then
    if [[ -f "$task_log" ]]; then tail -n 180 "$task_log"; fi
    if [[ -f "$server_dir/logs/latest.log" ]]; then tail -n 180 "$server_dir/logs/latest.log"; fi
  fi
  return "$status"
}
trap cleanup EXIT

launch_server() {
  local task="$1"
  setsid bash -c 'exec timeout 8m ./gradlew "$1" --no-daemon --no-configuration-cache --stacktrace' _ "$task" \
    >"$task_log" 2>&1 &
  server_pid=$!
}

wait_server() {
  if ! wait "$server_pid"; then
    server_pid=""
    return 1
  fi
  server_pid=""
}

verify_result() {
  local mode="$1"
  local result="$result_dir/$mode.properties"
  test -f "$result"
  grep -Eq '^status=PASS$' "$result"
  grep -Eq '^worldSeed=592850352$' "$result"
  grep -Eq '^zoneKey=534b5942494f4d45$' "$result"
  grep -Eq '^skyBiomeId=skyforge:cloud_shelf$' "$result"
  grep -Eq '^assignmentCount=1$' "$result"
}
launch_server "$write_task"
wait_server
verify_result write
if [[ ! -f "$server_dir/saves/acceptance/level.dat" ]]; then
  echo "Missing expected saved level.dat. Files under server directory:"
  find "$server_dir" -maxdepth 5 -type f -print | sort
  exit 1
fi
cp "$server_dir/logs/latest.log" "$result_dir/write-server.log"

rm -f "$server_dir/logs/latest.log"
launch_server "$reload_task"
wait_server
verify_result reload
grep -Eq '^assignmentCreated=true$' "$result_dir/write.properties"
grep -Eq '^assignmentRestored=true$' "$result_dir/reload.properties"
test -f "$server_dir/saves/acceptance/level.dat"
cp "$server_dir/logs/latest.log" "$result_dir/reload-server.log"
printf 'status=PASS\nserverBoots=2\nsameWorldFolder=saves/acceptance\n' > "$result_dir/summary.properties"
echo "SF-IMP-1524 SkyBiome zone SavedData write and same-world server reload acceptance PASS."
