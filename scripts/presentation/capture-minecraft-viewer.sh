#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 6 ]]; then
  echo "usage: $0 <game-dir> <gradle-client-task> <command-root> <capture-prefix> <output-dir> <stop> [<stop> ...]" >&2
  exit 2
fi

game_dir="$1"
client_task="$2"
command_root="$3"
capture_prefix="$4"
output_dir="$5"
shift 5
stops=("$@")

export DISPLAY="${DISPLAY:-:99}"
export LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-1}"

mkdir -p "$output_dir"
rm -rf "$game_dir/screenshots"
mkdir -p "$game_dir/screenshots"

client_log="$output_dir/client.log"
latest_log="$game_dir/logs/latest.log"
: > "$client_log"

gradle_pid=""
cleanup() {
  if [[ -n "$gradle_pid" ]] && kill -0 "$gradle_pid" 2>/dev/null; then
    pkill -TERM -P "$gradle_pid" 2>/dev/null || true
    kill "$gradle_pid" 2>/dev/null || true
    sleep 2
    pkill -KILL -P "$gradle_pid" 2>/dev/null || true
    kill -9 "$gradle_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

./gradlew ":skyforge-neoforge-1211:$client_task"   --stacktrace --no-configuration-cache >"$client_log" 2>&1 &
gradle_pid=$!

window_id=""
for _ in $(seq 1 180); do
  if ! kill -0 "$gradle_pid" 2>/dev/null; then
    echo "Minecraft client exited before a window became available" >&2
    tail -n 200 "$client_log" >&2 || true
    exit 1
  fi
  window_id="$(xdotool search --onlyvisible --name 'Minecraft' 2>/dev/null | tail -n 1 || true)"
  if [[ -n "$window_id" ]]; then
    break
  fi
  sleep 1
done

if [[ -z "$window_id" ]]; then
  echo "Timed out waiting for Minecraft window" >&2
  tail -n 200 "$client_log" >&2 || true
  exit 1
fi

xdotool windowfocus "$window_id" || true
xdotool windowmove "$window_id" 0 0 || true
xdotool windowsize "$window_id" 1600 900 || true

# Wait for quick-play to finish joining the integrated server. Existing accepted
# viewer logs consistently report this line after the deterministic initial teleport.
joined=false
for _ in $(seq 1 180); do
  if ! kill -0 "$gradle_pid" 2>/dev/null; then
    echo "Minecraft client exited before joining the saved world" >&2
    tail -n 200 "$client_log" >&2 || true
    [[ -f "$latest_log" ]] && tail -n 200 "$latest_log" >&2 || true
    exit 1
  fi
  if [[ -f "$latest_log" ]] && grep -q "joined the game" "$latest_log"; then
    joined=true
    break
  fi
  sleep 1
done

if [[ "$joined" != true ]]; then
  echo "Timed out waiting for integrated-server join" >&2
  tail -n 200 "$client_log" >&2 || true
  [[ -f "$latest_log" ]] && tail -n 200 "$latest_log" >&2 || true
  exit 1
fi

# Let chunks, lighting, vegetation, and the guided initial teleport settle.
sleep 15

# Hide HUD/chat before capture.
xdotool key --window "$window_id" F1
sleep 2

capture_stop() {
  local stop="$1"
  local before newest copied count

  xdotool windowfocus "$window_id" || true
  xdotool key --window "$window_id" t
  sleep 0.5
  xdotool type --window "$window_id" --clearmodifiers --delay 5 "/$command_root $stop"
  xdotool key --window "$window_id" Return

  # Software-rendered CI clients need time after long teleports for chunks, lighting,
  # vegetation and meshing to converge before a presentation capture is meaningful.
  sleep 24

  before="$(find "$game_dir/screenshots" -maxdepth 1 -type f -name '*.png' | wc -l)"
  xdotool key --window "$window_id" F2

  newest=""
  stable_size=0
  stable_ticks=0
  for _ in $(seq 1 45); do
    count="$(find "$game_dir/screenshots" -maxdepth 1 -type f -name '*.png' | wc -l)"
    if (( count > before )); then
      newest="$(find "$game_dir/screenshots" -maxdepth 1 -type f -name '*.png' -printf '%T@ %p\n' | sort -nr | head -n 1 | cut -d' ' -f2-)"
      if [[ -n "$newest" && -f "$newest" ]]; then
        size="$(stat -c '%s' "$newest")"
        if (( size > 4096 && size == stable_size )); then
          stable_ticks=$((stable_ticks + 1))
        else
          stable_ticks=0
        fi
        stable_size="$size"
        if (( stable_ticks >= 2 )); then
          break
        fi
      fi
    fi
    sleep 1
  done

  if [[ -z "$newest" || ! -f "$newest" || "$(stat -c '%s' "$newest")" -le 4096 ]]; then
    echo "No screenshot appeared for stop: $stop" >&2
    tail -n 200 "$client_log" >&2 || true
    [[ -f "$latest_log" ]] && tail -n 200 "$latest_log" >&2 || true
    exit 1
  fi

  copied="$output_dir/$capture_prefix-$stop.png"
  cp "$newest" "$copied"
  echo "captured $stop -> $copied"
}

for stop in "${stops[@]}"; do
  capture_stop "$stop"
done

xdotool key --window "$window_id" F1 || true
cp /tmp/skyforge-xvfb.log "$output_dir/xvfb.log" 2>/dev/null || true
cp "$latest_log" "$output_dir/minecraft-latest.log" 2>/dev/null || true

# Ask the actual client to close first so the integrated server flushes cleanly.
xdotool key --window "$window_id" alt+F4 || true
for _ in $(seq 1 20); do
  if ! kill -0 "$gradle_pid" 2>/dev/null; then
    break
  fi
  sleep 1
done
cleanup
gradle_pid=""
trap - EXIT

png_count="$(find "$output_dir" -maxdepth 1 -type f -name '*.png' | wc -l)"
if (( png_count != ${#stops[@]} )); then
  echo "Expected ${#stops[@]} PNGs but found $png_count" >&2
  exit 1
fi

echo "presentation capture complete: $png_count images"
