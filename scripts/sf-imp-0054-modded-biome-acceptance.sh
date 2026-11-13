#!/usr/bin/env bash
set -euo pipefail

server_dir="skyforge-neoforge-1211/run-sf-imp-0054-modded-biome-server"
acceptance_dir="skyforge-neoforge-1211/build/acceptance/sf-imp-0054-modded-biome"
server_result="$acceptance_dir/server.properties"
reopen_result="$acceptance_dir/reopen.properties"
reopen_summary="$acceptance_dir/reopen-summary.properties"
server_task_log="sf-imp-0054-modded-biome-server-task.log"
reopen_task_log="sf-imp-0054-modded-biome-reopen-task.log"
server_pid=""

mkdir -p "$server_dir" "$acceptance_dir"
rm -f "$server_result" "$reopen_result" "$reopen_summary"
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
view-distance=2
simulation-distance=3
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
    for log in "$server_task_log" "$reopen_task_log" "$server_dir/logs/latest.log"; do
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
  if ! wait "$server_pid"; then
    cat "$task_log"
    return 1
  fi
  server_pid=""
}

verify_server_result() {
  local result="$1"
  test -f "$result"
  grep -Eq '^status=PASS$' "$result"
  grep -Eq '^tectonicLoaded=true$' "$result"
  grep -Eq '^lithostitchedLoaded=true$' "$result"
  grep -Fq 'tectonicConfigVerified=true' "$result"
  grep -Fq 'selectedUpperBiome=biomesoplenty\:rocky_shrubland' "$result"
  grep -Fq 'tectonicMorphologyPair=MASSIF/BASIN' "$result"
  grep -Eq '^lowerIslandVolume=.*/sf-imp-0043-dev-massif/.*$' "$result"
  grep -Eq '^upperIslandVolume=.*/sf-imp-0054-review-basin/.*$' "$result"
  grep -Fq 'islandBiomeField=biomesoplenty\:rocky_shrubland' "$result"
  grep -Eq '^lowerPersistentTreeEvidence=true$' "$result"
  grep -Eq '^upperPersistentTreeEvidence=true$' "$result"
  grep -Eq '^exteriorNonAirBlocks=0$' "$result"
}

verify_full_surface_coverage() {
  local result="$1"
  local forest_surface provider_surface forest_populated provider_populated field_chunks
  grep -Eq '^surfaceEcologyRoute=VEGETAL_DECORATION/SURFACE_ECOLOGY$' "$result"
  forest_surface=$(sed -n 's/^forestSurfaceChunks=//p' "$result")
  provider_surface=$(sed -n 's/^providerSurfaceChunks=//p' "$result")
  forest_populated=$(sed -n 's/^forestPopulatedChunks=//p' "$result")
  provider_populated=$(sed -n 's/^providerPopulatedChunks=//p' "$result")
  field_chunks=$(sed -n 's/^islandBiomeFieldChunks=//p' "$result")
  test "$forest_surface" -gt 0
  test "$provider_surface" -gt 0
  test "$field_chunks" -gt 0
  test "$forest_surface" -eq "$forest_populated"
  test "$provider_surface" -eq "$provider_populated"
}

verify_reopen_footprint() {
  local result="$1"
  local forest_surface provider_surface scanned
  forest_surface=$(sed -n 's/^forestSurfaceChunks=//p' "$result")
  provider_surface=$(sed -n 's/^providerSurfaceChunks=//p' "$result")
  scanned=$(sed -n 's/^scannedChunks=//p' "$result")
  test "$forest_surface" -gt 0
  test "$provider_surface" -gt 0
  test "$scanned" -eq "$((forest_surface + provider_surface))"
}

# Generate the complete paired specimen before any client is launched. First-time
# Tectonic generation can occupy the server long enough to expire the login handshake.
rm -f "$server_dir/logs/latest.log"
launch_server ":skyforge-neoforge-1211:runModdedBiomePopulationServer" "$server_task_log"
cat "$server_result"
verify_server_result "$server_result"
verify_full_surface_coverage "$server_result"
grep -Fq 'islandBiomeField=biomesoplenty\:rocky_shrubland' "$server_result"
grep -Eq '^islandBiomeFieldMatchesPopulation=true$' "$server_result"
cp "$server_result" "$acceptance_dir/server-initial.properties"
cp "$server_dir/logs/latest.log" "$acceptance_dir/server-initial.log"

rm -f "$server_dir/logs/latest.log"
launch_server ":skyforge-neoforge-1211:runModdedBiomePopulationServerReopen" "$reopen_task_log"
verify_server_result "$reopen_result"
verify_reopen_footprint "$reopen_result"
grep -Eq '^generationReapplied=false$' "$reopen_result"
test -f "$server_dir/saves/acceptance/level.dat"

printf 'status=PASS\nsameWorldFolder=saves/acceptance\nserverBoots=2\n' > "$reopen_summary"
echo "SF-IMP-0054 paired Tectonic terrain and modded ecology dedicated-server, full-surface, and same-world reopen acceptance PASS."

