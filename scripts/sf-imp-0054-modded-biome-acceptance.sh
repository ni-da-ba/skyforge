#!/usr/bin/env bash
set -euo pipefail

server_dir="skyforge-neoforge-1211/run-sf-imp-0054-modded-biome-server"
client_dir="skyforge-neoforge-1211/run-sf-imp-0054-modded-biome-join-client"
acceptance_dir="skyforge-neoforge-1211/build/acceptance/sf-imp-0054-modded-biome"
server_result="$acceptance_dir/server.properties"
client_result="$acceptance_dir/client.properties"
reopen_result="$acceptance_dir/reopen.properties"
reopen_summary="$acceptance_dir/reopen-summary.properties"
server_task_log="sf-imp-0054-modded-biome-server-task.log"
reopen_task_log="sf-imp-0054-modded-biome-reopen-task.log"
client_task_log="sf-imp-0054-modded-biome-client-task.log"
server_pid=""
client_pid=""

mkdir -p "$server_dir" "$client_dir" "$acceptance_dir"
rm -f "$server_result" "$client_result" "$reopen_result" "$reopen_summary"
printf 'eula=true\n' > "$server_dir/eula.txt"
cat > "$server_dir/server.properties" <<'EOF'
level-name=sf-imp-0054-modded-biome
level-seed=592850352
level-type=skyforge:development
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
    for log in "$server_task_log" "$reopen_task_log" "$client_task_log" "$server_dir/logs/latest.log" "$client_dir/logs/latest.log"; do
      if [[ -f "$log" ]]; then echo "--- $log ---"; tail -n 180 "$log"; fi
    done
  fi
  return "$status"
}
trap cleanup EXIT

launch_server() {
  local task="$1"
  local task_log="$2"
  setsid bash -c 'exec timeout 28m ./gradlew "$1" --no-daemon --stacktrace' _ "$task" \
    >"$task_log" 2>&1 &
  server_pid=$!
}

wait_server_ready() {
  local task_log="$1"
  for _ in $(seq 1 840); do
    if grep -Fq 'Done (' "$server_dir/logs/latest.log" 2>/dev/null; then
      return 0
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
      cat "$task_log"
      return 1
    fi
    sleep 2
  done
  echo "Dedicated server did not finish startup within 28 minutes." >&2
  return 1
}

verify_server_result() {
  local result="$1"
  test -f "$result"
  grep -Eq '^status=PASS$' "$result"
  grep -Eq '^selectedUpperBiome=biomesoplenty:maple_woods$' "$result"
  grep -Eq '^upperPersistentTreeEvidence=true$' "$result"
  grep -Eq '^exteriorNonAirBlocks=0$' "$result"
}

rm -f "$server_dir/logs/latest.log"
launch_server ":skyforge-neoforge-1211:runModdedBiomePopulationServer" "$server_task_log"
wait_server_ready "$server_task_log"

setsid bash -c 'exec timeout 12m xvfb-run -a ./gradlew :skyforge-neoforge-1211:runModdedBiomePopulationJoinClient --no-daemon --stacktrace' \
  >"$client_task_log" 2>&1 &
client_pid=$!

client_result_ready=false
for _ in $(seq 1 360); do
  if [[ -f "$client_result" ]]; then
    client_result_ready=true
    break
  fi
  if ! kill -0 "$client_pid" 2>/dev/null; then
    cat "$client_task_log"
    exit 1
  fi
  sleep 2
done
if [[ "$client_result_ready" != true ]]; then
  echo "Actual client did not complete the remote-join acceptance within 12 minutes." >&2
  exit 1
fi
wait "$client_pid"
client_pid=""
grep -Eq '^status=PASS$' "$client_result"
grep -Eq '^remoteServerJoin=true$' "$client_result"
grep -Eq '^singleplayerServer=false$' "$client_result"
grep -Eq '^spawnSupported=true$' "$client_result"

wait "$server_pid"
server_pid=""
verify_server_result "$server_result"
test -f "$server_dir/saves/acceptance/level.dat"
cp "$server_result" "$acceptance_dir/server-initial.properties"
cp "$server_dir/logs/latest.log" "$acceptance_dir/server-initial.log"

rm -f "$server_dir/logs/latest.log"
launch_server ":skyforge-neoforge-1211:runModdedBiomePopulationServerReopen" "$reopen_task_log"
wait_server_ready "$reopen_task_log"
wait "$server_pid"
server_pid=""
verify_server_result "$reopen_result"
test -f "$server_dir/saves/acceptance/level.dat"

printf 'status=PASS\nsameWorldFolder=saves/acceptance\nserverBoots=2\nremoteServerJoin=true\n' > "$reopen_summary"
echo "SF-IMP-0054 modded-biome dedicated-server, remote-client join, and same-world reopen acceptance PASS."
