#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
profile="${1:-}"
if [[ -z "$profile" ]]; then
  echo "Usage: bash scripts/wby-s9-diagnostic-launch.sh /c/path/to/extracted/wby-s9-<variant>-review-profile"
  exit 2
fi
profile="$(cd "$profile" && pwd)"
if [[ ! -s "$profile/manifest.txt" || ! -d "$profile/client/mods" || ! -d "$profile/server/mods" ]]; then
  echo "That folder is not an extracted S9 review profile: $profile" >&2
  echo "It must contain manifest.txt, client/mods, server/mods, and world/datapacks." >&2
  exit 2
fi

python_bin="$(command -v python3 || command -v python || true)"
if [[ -z "$python_bin" ]]; then
  echo "Install Python 3.10+ and reopen Git Bash." >&2
  exit 2
fi
venv="$repo_root/.skyforge-diagnostics/wby-s9-launcher-venv"
if [[ -x "$venv/Scripts/python.exe" ]]; then
  launcher_python="$venv/Scripts/python.exe"
else
  launcher_python="$venv/bin/python"
fi
if [[ ! -x "$launcher_python" ]]; then
  "$python_bin" -m venv "$venv"
  if [[ -x "$venv/Scripts/python.exe" ]]; then
    launcher_python="$venv/Scripts/python.exe"
  else
    launcher_python="$venv/bin/python"
  fi
fi
if ! "$launcher_python" -c 'import minecraft_launcher_lib' >/dev/null 2>&1; then
  "$launcher_python" -m pip install --disable-pip-version-check -r "$repo_root/scripts/wby-s4-launcher-requirements.txt"
fi

echo "S9 review profile: $(sed -n 's/^variant=//p' "$profile/manifest.txt")"
echo "Review a fresh normal world: End scale/negative space, biome and resource variety, stronghold/portal route, structure density, and survival pressure."
echo "For End access after joining as operator, use: /execute in minecraft:the_end run tp @s 0 100 0"
echo "For the return-route check, use the End gateway or dragon portal in-world; do not use a command to return."
echo "For the optional FlyHigher profile, compare pressure/altitude behavior to the accepted ThinAir + Aeronautics baseline and record the FlyHigher config before changing any values."
echo "The launcher starts a local dedicated server and opens a client connected to it. Save and quit normally when done."
"$launcher_python" "$repo_root/scripts/wby-s9-launch-production-review.py" --profile "$profile"
