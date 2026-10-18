#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
profile="${1:-}"
if [[ -z "$profile" ]]; then
  echo "Usage: bash scripts/wby-s10-diagnostic-launch.sh <profile> [accepted-S9-profile accepted-S9-diagnostics] [--restore-backup /c/path/to/archive.zip]"
  echo "Profiles: none quests quests-book enchantment-industry communications operations-stable operations-beta adaptive-music surface-audio full"
  echo "With no S9 paths, the script finds the newest matching S9 Nullscape profile in this checkout or a sibling skyforge-s9* checkout."
  exit 2
fi

case "$profile" in
  none|quests|quests-book|enchantment-industry|communications|operations-stable|operations-beta|adaptive-music|surface-audio|full) ;;
  *) echo "Unknown S10 profile: $profile" >&2; exit 2 ;;
esac
shift

s9_profile=""
s9_diagnostics=""
restore_backup=""

if [[ $# -ge 1 && "$1" != "--restore-backup" ]]; then
  if [[ $# -lt 2 || "$2" == "--restore-backup" ]]; then
    echo "Pass both the S9 profile directory and its matching diagnostics directory, or pass neither." >&2
    exit 2
  fi
  s9_profile="$1"
  s9_diagnostics="$2"
  shift 2
else
  parent_dir="$(dirname "$repo_root")"
  search_roots=("$repo_root")
  for candidate in "$parent_dir"/skyforge-s9*; do
    [[ -d "$candidate/.git" ]] || continue
    search_roots+=("$candidate")
  done

  newest_stamp=""
  for root in "${search_roots[@]}"; do
    for candidate_profile in "$root"/skyforge-neoforge-1211/run-wby-s9-nullscape-manual-*; do
      [[ -d "$candidate_profile" ]] || continue
      stamp="${candidate_profile##*-manual-}"
      candidate_diagnostics="$root/.skyforge-diagnostics/wby-s9-nullscape-$stamp"
      [[ -d "$candidate_diagnostics/world-datapacks" ]] || continue
      [[ -d "$candidate_profile/mods" ]] || continue
      if [[ -z "$newest_stamp" || "$stamp" > "$newest_stamp" ]]; then
        newest_stamp="$stamp"
        s9_profile="$candidate_profile"
        s9_diagnostics="$candidate_diagnostics"
      fi
    done
  done

  if [[ -z "$s9_profile" ]]; then
    echo "Could not find a matching S9 Nullscape profile and diagnostics folder." >&2
    echo "Run the S9 Nullscape review first, or pass both S9 paths explicitly." >&2
    exit 2
  fi
  echo "Auto-selected latest S9 Nullscape profile: $s9_profile"
  echo "Matching S9 diagnostics: $s9_diagnostics"
fi

if [[ $# -gt 0 ]]; then
  if [[ $# -ne 2 || "$1" != "--restore-backup" ]]; then
    echo "Optional restore syntax: --restore-backup /c/path/to/simplebackups-archive.zip" >&2
    exit 2
  fi
  restore_backup="$2"
  shift 2
fi

if [[ $# -ne 0 ]]; then
  echo "Unexpected extra arguments." >&2
  exit 2
fi

s9_profile_input="$s9_profile"
if ! s9_profile="$(cd "$s9_profile_input" 2>/dev/null && pwd)"; then
  echo "Accepted S9 profile folder not found: $s9_profile_input" >&2
  exit 2
fi
if [[ ! -d "$s9_profile/mods" || -z "$(find "$s9_profile/mods" -maxdepth 1 -type f -name '*.jar' -print -quit)" ]]; then
  echo "Accepted S9 profile is missing staged JARs under $s9_profile/mods" >&2
  exit 2
fi

s9_diagnostics_input="$s9_diagnostics"
if ! s9_diagnostics="$(cd "$s9_diagnostics_input" 2>/dev/null && pwd)"; then
  echo "Accepted S9 diagnostics folder not found: $s9_diagnostics_input" >&2
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

extra_args=()
if [[ -n "$restore_backup" ]]; then
  if [[ ! -f "$restore_backup" ]]; then
    echo "Backup archive not found: $restore_backup" >&2
    exit 2
  fi
  extra_args=(--restore-backup "$(cygpath -w "$restore_backup")")
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
  "$(cygpath -w "$s9_diagnostics")" \
  "${extra_args[@]}"
