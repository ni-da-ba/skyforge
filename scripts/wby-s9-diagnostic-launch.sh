#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
variant="${1:-}"
profile="${2:-}"
if [[ -z "$variant" || -z "$profile" ]]; then
  echo "Usage: bash scripts/wby-s9-diagnostic-launch.sh <variant> /c/path/to/accepted-S8-profile"
  echo "Variants: control nullscape nullscape-betterend nullscape-unusual nullscape-moogs nullscape-yungs-strongholds nullscape-yungs-island nullscape-structures nullscape-phantasm nullscape-beyond-bosses nullscape-beyond-bosses-nostronghold nullscape-savage-dragon nullscape-edr nullscape-flyhigher betterend betterend-food betterend-moogs betterend-yungs-strongholds betterend-yungs-island betterend-structures"
  exit 2
fi
case "$variant" in
  control|nullscape|nullscape-betterend|nullscape-unusual|nullscape-moogs|nullscape-yungs-strongholds|nullscape-yungs-island|nullscape-structures|nullscape-phantasm|nullscape-beyond-bosses|nullscape-beyond-bosses-nostronghold|nullscape-savage-dragon|nullscape-edr|nullscape-flyhigher|betterend|betterend-food|betterend-unusual|betterend-edr|betterend-flyhigher|betterend-moogs|betterend-yungs-strongholds|betterend-yungs-island|betterend-structures) ;;
  *) echo "Unknown S9 variant: $variant" >&2; exit 2 ;;
esac
if ! profile="$(cd "$profile" 2>/dev/null && pwd)"; then
  echo "Accepted S8 profile folder not found: ${2}" >&2
  exit 2
fi
if [[ ! -d "$profile/mods" || -z "$(find "$profile/mods" -maxdepth 1 -type f -name '*.jar' -print -quit)" ]]; then
  echo "Accepted S8 profile is missing staged JARs under: $profile/mods" >&2
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

echo "Preparing S9 variant: $variant"
echo "Accepted S8 profile: $profile"
echo "This launcher downloads only selected candidates from their official provider APIs into ignored local folders."
echo "BOP End biome interaction is still an explicit review item."
"$launcher_python" "$repo_root/scripts/wby-s9-launch-production-review.py" \
  "$variant" "$profile" --repo-root "$repo_root"
