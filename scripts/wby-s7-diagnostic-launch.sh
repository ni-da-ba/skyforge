#!/usr/bin/env bash
set -euo pipefail

run_directory="run-wby-s7-threats"
run_path="skyforge-neoforge-1211/$run_directory"
whale_retexture="$run_path/resourcepacks/Sky-Whale-Retexture.zip"
if [[ ! -f "$whale_retexture" ]]; then
  cat >&2 <<EOF
Sky Whale Retexture is required for the cumulative S6/S7 client profile:
  $whale_retexture
Download it from https://modrinth.com/resourcepack/sky-whale-retexture
and save the ZIP there. Enable it in Options > Resource Packs after launch.
The resource pack is a local review dependency and is not bundled or redistributed.
EOF
  exit 2
fi
a4mc_mod_dir="$PWD/.skyforge-diagnostics/aerodynamics4mc-0.2.2-mods"
for jar in \
  "$a4mc_mod_dir/aerodynamics4mc-0.2.2-neoforge+1.21.1.jar" \
  "$a4mc_mod_dir/aerodynamics4mc-compat-create-aeronautics-0.2.2-neoforge+1.21.1.jar"; do
  if [[ ! -f "$jar" ]]; then
    cat >&2 <<EOF
Missing pinned A4MC review dependency:
  $jar
Download and extract both A4MC 0.2.2 JARs from the S5/S6 GitHub Actions artifact into:
  .skyforge-diagnostics/aerodynamics4mc-0.2.2-mods/
EOF
    exit 2
  fi
done

mkdir -p .skyforge-diagnostics
bundle=".skyforge-diagnostics/wby-s7-threats-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$bundle"

base_args=(
  -PwbyS1Glider=combined
  -PwbyS1Clouds=simple-clouds
  -PwbyS1ThinAir=false
  "-PwbyS1RunDirectory=$run_directory"
  -PwbyS1A4mcBuiltModDir="$a4mc_mod_dir"
  -PwbyS2ComputingAvionics=true
  -PwbyS3DieselGenerators=true
  -PwbyS3CreateBigCannons=true
  -PwbyS4OrdinaryLife=true
  -PwbyS4Shaders=false
  -PwbyS5OverworldEcology=true
  -PwbyS6StructuresCivilization=true
  -PwbyS7IllagerThreats=true
  "-PwbyS6D07Variant=both"
  --no-configuration-cache
)
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dskyforge.dev.waveC25PetroleumAuthority=suppressed"

./gradlew :skyforge-neoforge-1211:wbyS1ResolvePinnedMods "${base_args[@]}" 2>&1 | tee "$bundle/profile-resolution.log"
grep -Fq "WBY S7 ILLAGER THREATS RESOLUTION PASS" "$bundle/profile-resolution.log"

./gradlew :skyforge-neoforge-1211:wbyS1StageClientMods "${base_args[@]}" 2>&1 | tee "$bundle/profile-stage.log"
./gradlew :skyforge-neoforge-1211:wbyS1StagePolicy "${base_args[@]}" 2>&1 | tee "$bundle/policy-stage.log"

echo "S7 threat candidate: cumulative S6 roster with both Structory and Explorify, plus the selected illager stack."
echo "Windy and Atmospheric Shaders are excluded from this profile; accepted Simple Clouds + DH baseline remains."
echo "Create a fresh normal Overworld (not the development-only Skyforge preset) with seed 817304."
echo "Review illager structure placement and variety, hostile density and faction overlap, travel/vehicle pressure, and any progression bypasses. Record structure names/coordinates and screenshots."
echo "Use creative mode only to locate candidate structures; then switch to Survival and Normal difficulty to inspect the encounter. Do not tune loot, recipes, or spawn density."
echo "Leave density, loot, recipe, and progression decisions for the whole-stack policy pass."
echo "Review run directory: skyforge-neoforge-1211/$run_directory"
echo "Diagnostics bundle: $bundle"
echo "Sky Whale Retexture remains required if Sky Whales are encountered; install the local ARR pack manually and enable it."
echo "Launch starts now. Save and quit normally when you have finished reviewing."

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
  --game-directory "$PWD/skyforge-neoforge-1211/$run_directory" \
  --username WbyS7Review 2>&1 | tee "$bundle/client.log"
status=${PIPESTATUS[0]}
set -e

test ! -f "$run_path/logs/latest.log" || cp "$run_path/logs/latest.log" "$bundle/latest.log"
test ! -d "$run_path/crash-reports" || cp -R "$run_path/crash-reports" "$bundle/crash-reports"
if [[ -d "$run_path/mods" ]]; then
  find "$run_path/mods" -maxdepth 1 -type f -name '*.jar' -printf '%f\n' | sort > "$bundle/staged-mods.txt"
fi
echo "Send this diagnostics folder if anything failed: $bundle"
exit "$status"
