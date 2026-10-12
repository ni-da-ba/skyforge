#!/usr/bin/env bash
set -euo pipefail

variant="${1:-}"
case "$variant" in
  engineering|tacz|firepower) ;;
  *)
    echo "Usage: bash scripts/wby-s7-combat-diagnostic-launch.sh {engineering|tacz|firepower}" >&2
    exit 2
    ;;
esac

source_run_path="skyforge-neoforge-1211/run-wby-s7-combat-$variant"
if [[ ! -d "$source_run_path/mods" ]]; then
  cat >&2 <<EOF
Missing the already-staged local profile:
  $source_run_path/mods
This manual launcher copies existing staged files only. It does not run Gradle or create a profile.
EOF
  exit 2
fi

review_stamp="$(date -u +%Y%m%dT%H%M%SZ)"
run_directory="run-wby-s7-combat-$variant-manual-$review_stamp"
run_path="skyforge-neoforge-1211/$run_directory"
mkdir -p "$run_path"
for folder in mods config serverconfig defaultconfigs kubejs datapacks resourcepacks tacz; do
  [[ ! -d "$source_run_path/$folder" ]] || cp -R "$source_run_path/$folder" "$run_path/"
done

# The owner excluded these mods. Remove only their JARs from this fresh manual copy;
# the pre-existing staged profile and its worlds remain untouched.
find "$run_path/mods" -maxdepth 1 -type f \( \
  -iname 'create-fire-control-*.jar' -o -iname 'mianbaos-newmodernwarfare-*.jar' \
\) -delete

a4mc_mod_dir="$PWD/.skyforge-diagnostics/aerodynamics4mc-0.2.2-mods"
for jar in \
  "$a4mc_mod_dir/aerodynamics4mc-0.2.2-neoforge+1.21.1.jar" \
  "$a4mc_mod_dir/aerodynamics4mc-compat-create-aeronautics-0.2.2-neoforge+1.21.1.jar"; do
  if [[ ! -f "$jar" ]]; then
    cat >&2 <<EOF
Missing pinned Aerodynamics4MC review dependency:
  $jar
Download the wby-s7-combat-$variant-resolution Actions artifact and extract both A4MC 0.2.2 JARs into:
  .skyforge-diagnostics/aerodynamics4mc-0.2.2-mods/
EOF
    exit 2
  fi
  cp -f "$jar" "$run_path/mods/"
done

whale_retexture="$run_path/resourcepacks/Sky-Whale-Retexture.zip"
if [[ ! -f "$whale_retexture" ]]; then
  cat >&2 <<EOF
Sky Whale Retexture is required for the cumulative S6/S7 client profile:
  $whale_retexture
Copy Sky-Whale-Retexture.zip into $run_path/resourcepacks/
The resource pack is a local review dependency; do not redistribute it.
EOF
  exit 2
fi

mkdir -p .skyforge-diagnostics
bundle=".skyforge-diagnostics/wby-s7-combat-$variant-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$bundle"

launcher_username="WbyS7Review"
if (( ${#launcher_username} > 16 )); then
  echo "Minecraft usernames cannot exceed 16 characters: $launcher_username" >&2
  exit 2
fi

printf '%s\n' "Manual profile copied from $source_run_path; GitHub Actions owns machine validation." | tee "$bundle/profile-stage.log"
printf '%s\n' "Deferred mods removed from the new copy: Create: Fire Control and Mianbao's NewModernWarfare." | tee "$bundle/policy-stage.log"

if [[ "$variant" == "firepower" ]]; then
  echo "S7 candidate: cumulative S0-S7 BOMD plus combat engineering and CBC: Firepower Components; no TaCZ or Scorched Guns."
else
  echo "S7 candidate: cumulative S0-S7 BOMD plus combat engineering and exactly the '$variant' personal-firearm arm."
fi
echo "Atmospheric Shaders are excluded; the accepted Simple Clouds + Distant Horizons setup remains."
echo "Start a fresh normal Overworld (leave preset at Default/Normal) with seed 817306 in each arm so the worlds are comparable."
echo "Keep difficulty Normal. Begin in Survival; use Creative briefly only if needed to inspect item catalogs or place test targets."
echo "Review the radar/IFF target picture, CBC 5.11.7 projectile behavior, and weapon use while an aircraft/vehicle is moving. Create: Fire Control and its Mianbao dependency are deferred and absent."
echo "Test projectiles against static blocks and moving Sable contraptions; note shielding, penetration, damage, recoil, reload/ammo supply, and whether weapons work after save/reopen."
echo "For Radar 4.9 evidence where applicable, run /radar debug gen_debug_file to capture radar links, filters, and weapon endpoints."
if [[ "$variant" == "tacz" ]]; then
  echo "Inspect aircraft-mounted TaCZ guns, the existing Create: TaCZ manufacturing flow, firearm handling, and TaCZ NPC behavior and default appearances at villages/outposts/mansions."
fi
if [[ "$variant" == "firepower" ]]; then
  echo "Inspect compact cannon/autocannon mounts, vertical mounting, pitch/yaw limits, ammunition loading/feeds/racks, and manual aiming/firing on static CBC mounts and moving Sable craft."
  echo "Create: Radars is pinned to 0.4.9.4; observe the Firepower Components legacy Radar compatibility path. Automatic Fire Control handoff and fail-closed behavior are deferred with Create: Fire Control."
  echo "Check CBC Neo Warfare and Terminal Ballistics coexist with the mounts and ammo system. Radar 5.0 EA API behavior is outside this review."
fi
echo "Use JEI to record candidate ammo/material recipes and loot/spawn observations. Do not change recipes, drops, balance, or progression."
echo "This is a compatibility/content-fit gate only; do not promote a firearm arm from this one run."
echo "Review run directory: $run_path"
echo "Diagnostics bundle: $bundle"
echo "The Sky Whale Retexture is needed if whales appear."
echo "Launch starts now; save and quit normally after review."

python_bin="$(command -v python3 || command -v python || true)"
if [[ -z "$python_bin" ]]; then
  echo "Install Python 3.10 or newer and rerun this command." >&2
  exit 2
fi
launcher_venv="$PWD/.skyforge-diagnostics/wby-s5-launcher-venv"
if [[ -x "$launcher_venv/Scripts/python.exe" ]]; then
  launcher_python="$launcher_venv/Scripts/python.exe"
else
  launcher_python="$launcher_venv/bin/python"
fi
if [[ ! -x "$launcher_python" ]]; then
  "$python_bin" -m venv "$launcher_venv"
  if [[ -x "$launcher_venv/Scripts/python.exe" ]]; then
    launcher_python="$launcher_venv/Scripts/python.exe"
  else
    launcher_python="$launcher_venv/bin/python"
  fi
fi
if ! "$launcher_python" -c 'import minecraft_launcher_lib' >/dev/null 2>&1; then
  "$launcher_python" -m pip install --disable-pip-version-check -r scripts/wby-s4-launcher-requirements.txt
fi

crash_report_manifest="$bundle/crash-reports-before.txt"
if [[ -d "$run_path/crash-reports" ]]; then
  find "$run_path/crash-reports" -maxdepth 1 -type f -printf '%f\n' | sort > "$crash_report_manifest"
else
  : > "$crash_report_manifest"
fi

set +e
"$launcher_python" scripts/wby-s4-launch-production-client.py \
  --minecraft-directory "$PWD/.skyforge-diagnostics/wby-s5-production-client" \
  --game-directory "$PWD/$run_path" \
  --username "$launcher_username" 2>&1 | tee "$bundle/client.log"
status=${PIPESTATUS[0]}
set -e

test ! -f "$run_path/logs/latest.log" || cp "$run_path/logs/latest.log" "$bundle/latest.log"
if [[ -d "$run_path/crash-reports" ]]; then
  while IFS= read -r report; do
    [[ -n "$report" ]] || continue
    if ! grep -Fxq -- "$report" "$crash_report_manifest"; then
      mkdir -p "$bundle/crash-reports"
      cp "$run_path/crash-reports/$report" "$bundle/crash-reports/$report"
    fi
  done < <(find "$run_path/crash-reports" -maxdepth 1 -type f -printf '%f\n' | sort)
fi
test ! -d "$run_path/create_radar_debug" || cp -R "$run_path/create_radar_debug" "$bundle/create_radar_debug"
if [[ -d "$run_path/mods" ]]; then
  find "$run_path/mods" -maxdepth 1 -type f -name '*.jar' -printf '%f\n' | sort > "$bundle/staged-mods.txt"
fi
echo "If a radar debug dump was generated, it is included under $bundle/create_radar_debug."
echo "Send this diagnostics folder if anything failed: $bundle"
exit "$status"
