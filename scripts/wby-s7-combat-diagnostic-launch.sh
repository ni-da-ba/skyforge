#!/usr/bin/env bash
set -euo pipefail

variant="${1:-}"
case "$variant" in
  engineering|tacz|scorched) ;;
  *)
    echo "Usage: bash scripts/wby-s7-combat-diagnostic-launch.sh {engineering|tacz|scorched}" >&2
    exit 2
    ;;
esac

run_directory="run-wby-s7-combat-$variant"
run_path="skyforge-neoforge-1211/$run_directory"
whale_retexture="$run_path/resourcepacks/Sky-Whale-Retexture.zip"
if [[ ! -f "$whale_retexture" ]]; then
  cat >&2 <<EOF
Sky Whale Retexture is required for the cumulative S6/S7 client profile:
  $whale_retexture
Download it from https://modrinth.com/resourcepack/sky-whale-retexture
and copy it there before running this command. Do not redistribute the pack.
EOF
  exit 2
fi

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
done

mkdir -p .skyforge-diagnostics
bundle=".skyforge-diagnostics/wby-s7-combat-$variant-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$bundle"

base_args=(
  -PwbyS1Glider=combined
  -PwbyS1Clouds=simple-clouds
  -PwbyS1ThinAir=false
  "-PwbyS1RunDirectory=$run_directory"
  "-PwbyS1A4mcBuiltModDir=$a4mc_mod_dir"
  -PwbyS2ComputingAvionics=true
  -PwbyS3DieselGenerators=true
  -PwbyS3CreateBigCannons=true
  -PwbyS4OrdinaryLife=true
  -PwbyS4Shaders=false
  -PwbyS5OverworldEcology=true
  -PwbyS6StructuresCivilization=true
  -PwbyS7IllagerThreats=true
  -PwbyS7IceAndFire=true
  -PwbyS7Bomd=true
  "-PwbyS7CombatVariant=$variant"
  -PwbyS6D07Variant=both
  --no-configuration-cache
)
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dskyforge.dev.waveC25PetroleumAuthority=suppressed"

./gradlew :skyforge-neoforge-1211:wbyS1ResolvePinnedMods "${base_args[@]}" 2>&1 | tee "$bundle/profile-resolution.log"
grep -Fq "WBY S7 COMBAT RESOLUTION PASS variant=$variant" "$bundle/profile-resolution.log"

./gradlew :skyforge-neoforge-1211:wbyS1StageClientMods "${base_args[@]}" 2>&1 | tee "$bundle/profile-stage.log"
./gradlew :skyforge-neoforge-1211:wbyS1StagePolicy "${base_args[@]}" 2>&1 | tee "$bundle/policy-stage.log"

echo "S7 candidate: cumulative S0-S7 BOMD plus combat engineering and exactly the '$variant' personal-firearm arm."
echo "Atmospheric Shaders are excluded; the accepted Simple Clouds + Distant Horizons setup remains."
echo "Start a fresh normal Overworld (leave preset at Default/Normal) with seed 817306 in each arm so the worlds are comparable."
echo "Keep difficulty Normal. Begin in Survival; use Creative briefly only if needed to inspect item catalogs or place test targets."
echo "Review the radar/IFF target picture, track handoff into Fire Control, targeting stability while an aircraft/vehicle is moving, and cannon alignment/fire timing with CBC 5.11.7."
echo "Test projectiles against static blocks and moving Sable contraptions; note shielding, penetration, damage, recoil, reload/ammo supply, and whether weapons work after save/reopen."
echo "After building/linking the radar and weapon network, run /radar debug gen_debug_file in the world to capture radar links, filters, and weapon endpoints."
echo "For TaCZ, inspect aircraft-mounted guns, Create automation, TaCZ NPC behavior, and default NPC appearances at villages/outposts/mansions. For Scorched, inspect the distinct weapon/mob/structure/turret/ExoSuit catalogue and any shield-generator interaction."
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

set +e
"$launcher_python" scripts/wby-s4-launch-production-client.py \
  --minecraft-directory "$PWD/.skyforge-diagnostics/wby-s5-production-client" \
  --game-directory "$PWD/$run_path" \
  --username WbyS7CombatReview 2>&1 | tee "$bundle/client.log"
status=${PIPESTATUS[0]}
set -e

test ! -f "$run_path/logs/latest.log" || cp "$run_path/logs/latest.log" "$bundle/latest.log"
test ! -d "$run_path/crash-reports" || cp -R "$run_path/crash-reports" "$bundle/crash-reports"
test ! -d "$run_path/create_radar_debug" || cp -R "$run_path/create_radar_debug" "$bundle/create_radar_debug"
if [[ -d "$run_path/mods" ]]; then
  find "$run_path/mods" -maxdepth 1 -type f -name '*.jar' -printf '%f\n' | sort > "$bundle/staged-mods.txt"
fi
echo "If a radar debug dump was generated, it is included under $bundle/create_radar_debug."\necho "Send this diagnostics folder if anything failed: $bundle"
exit "$status"
