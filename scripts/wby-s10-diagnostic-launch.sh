#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "\${BASH_SOURCE[0]}")/.." && pwd)"
profile="\${1:-}"
s9_profile="\${2:-}"
s9_diagnostics="\${3:-}"

if [[ -z "$profile" || -z "$s9_profile" || -z "$s9_diagnostics" ]]; then
  echo "Usage: bash scripts/wby-s10-diagnostic-launch.sh <profile> <accepted-S9-profile> <accepted-S9-diagnostics>"
  echo "Profiles: none quests quests-book enchantment-industry communications operations-stable operations-beta adaptive-music surface-audio full"
  echo "Example paths should point to the accepted S9 run directory and its matching .skyforge-diagnostics directory."
  exit 2
fi

case "$profile" in
  none|quests|quests-book|enchantment-industry|communications|operations-stable|operations-beta|adaptive-music|surface-audio|full) ;;
  *) echo "Unknown S10 profile: $profile" >&2; exit 2 ;;
esac

if ! s9_profile="$(cd "$s9_profile" 2>/dev/null && pwd)"; then
  echo "Accepted S9 profile folder not found: $2" >&2
  exit 2
fi
if [[ ! -d "$s9_profile/mods" || -z "$(find "$s9_profile/mods" -maxdepth 1 -type f -name '*.jar' -print -quit)" ]]; then
  echo "Accepted S9 profile is missing staged JARs under $s9_profile/mods" >&2
  exit 2
fi
if ! s9_diagnostics="$(cd "$s9_diagnostics" 2>/dev/null && pwd)"; then
  echo "Accepted S9 diagnostics folder not found: $3" >&2
  exit 2
fi
if [[ ! -d "$s9_diagnostics/world-datapacks" ]]; then
  echo "Accepted S9 diagnostics are missing world-datapacks: $s9_diagnostics/world-datapacks" >&2
  exit 2
fi

python_bin="$(command -v python3 || command -v python || true)"
if [[ -z "$python_bin" ]]; then
  echo "Install Python 3.10+ and reopen Git Bash." >&2
  exit 2
fi
if ! command -v cygpath >/dev/null 2>&1; then
  echo "Run this script from Git Bash so cygpath can convert Windows paths." >&2
  exit 2
fi

venv="$repo_root/.skyforge-diagnostics/wby-s10-launcher-venv"
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

echo "Preparing S10 profile: $profile"
echo "Accepted S9 profile: $s9_profile"
echo "Accepted S9 datapacks: $s9_diagnostics/world-datapacks"
echo "Only the selected exact-version candidate overlay is downloaded into ignored local folders."
"$launcher_python" "$repo_root/scripts/wby-s10-launch-production-review.py" \
  "$profile" \
  "$(cygpath -w "$s9_profile")" \
  "$(cygpath -w "$s9_diagnostics")"
