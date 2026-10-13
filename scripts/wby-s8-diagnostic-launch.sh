#!/usr/bin/env bash
set -euo pipefail

variant="${1:-}"
case "$variant" in
  control|control-mnd|jne|jne-mnd|incendium) ;;
  *) echo "Usage: bash scripts/wby-s8-diagnostic-launch.sh {control|control-mnd|jne|jne-mnd|incendium}" >&2; exit 2 ;;
esac

base="skyforge-neoforge-1211/run-wby-s8-control"
if [[ ! -d "$base/mods" ]]; then
  cat >&2 <<EOF
Missing the S8 control client profile:
  $base/mods
Download the artifact named wby-s8-control-client-profile from the S8 GitHub Actions run,
then extract it into skyforge-neoforge-1211/ so that run-wby-s8-control/mods exists.
This launcher copies already-staged files only; it does not run Gradle.
EOF
  exit 2
fi

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
run="skyforge-neoforge-1211/run-wby-s8-$variant-manual-$stamp"
bundle=".skyforge-diagnostics/wby-s8-$variant-$stamp"
mkdir -p "$run" "$bundle"
for folder in mods config serverconfig defaultconfigs kubejs datapacks resourcepacks options.txt; do
  [[ ! -e "$base/$folder" ]] || cp -R "$base/$folder" "$run/"
done

downloads=".skyforge-diagnostics/s8-nether-downloads"
if [[ "$variant" == "jne" || "$variant" == "jne-mnd" ]]; then
  patterns=('*jadens-nether-expansion*.jar' '*elysium*api*.jar' '*lodestone*.jar')
  for pattern in "${patterns[@]}"; do
    mapfile -t matches < <(find "$downloads" -maxdepth 1 -type f -iname "$pattern" -print)
    if (( ${#matches[@]} != 1 )); then
      cat >&2 <<EOF
Expected exactly one JAR matching $pattern in:
  $downloads
Download official JNE 2.4.1, Elysium API 2.0.1, and Lodestone 1.8.2 from:
  https://www.curseforge.com/minecraft/mc-mods/jadens-nether-expansion/files/8707156
  https://www.curseforge.com/minecraft/mc-mods/elysium-api/files/8707151
  https://www.curseforge.com/minecraft/mc-mods/lodestone/files/7264731
  https://www.curseforge.com/minecraft/mc-mods/my-nethers-delight/files/8854319 (control-mnd and jne-mnd arms)
Save the original JARs in $downloads. JNE's license metadata conflicts with its upstream source license; keep it local to this review pending resolution. MND is All Rights Reserved.
EOF
      exit 2
    fi
    cp -f "${matches[0]}" "$run/mods/"
  done
fi

if [[ "$variant" == "control-mnd" || "$variant" == "jne-mnd" ]]; then
  mapfile -t matches < <(find "$downloads" -maxdepth 1 -type f -iname '*mynethersdelight*.jar' -print)
  if (( ${#matches[@]} != 1 )); then
    echo "Expected exactly one My Nether's Delight 1.10.4.1 JAR in $downloads (download link: https://www.curseforge.com/minecraft/mc-mods/my-nethers-delight/files/8854319)" >&2
    exit 2
  fi
  cp -f "${matches[0]}" "$run/mods/"
fi

if [[ "$variant" == "incendium" ]]; then
  pack="$downloads/Incendium-5.4.4.zip"
  if [[ ! -f "$pack" ]]; then
    echo "Download the official whole Incendium 5.4.4 data pack from https://modrinth.com/datapack/incendium/version/7mVvV9Th and save it as $pack" >&2
    exit 2
  fi
  echo "During Create World > Data Packs > Open Pack Folder, copy this ZIP into the opened world datapacks folder:"
  echo "  $pack"
  echo "Select Incendium in the Data Packs screen before creating the world."
fi

if [[ -f "$downloads/Sky-Whale-Retexture.zip" ]]; then
  mkdir -p "$run/resourcepacks"
  cp -f "$downloads/Sky-Whale-Retexture.zip" "$run/resourcepacks/Sky-Whale-Retexture.zip"
fi

echo "S8 arm: $variant. Cumulative S0-S7, including selected TaCZ, with exactly one Nether substrate arm."
echo "Create a NEW normal/default world, not the Skyforge preset, with seed 817304 in each arm."
echo "Use the same game mode/difficulty and settings. Do not reuse or copy generated Nether chunks between arms."
echo "Enter the Nether through a portal and compare biome geography, scale/connectivity, lava, routes, flight and DH/Simple Clouds rendering."
echo "Check for fortresses and Blaze access, bastion/candidate structures, duplicate or suppressed structures, and obvious progression bypasses."
echo "The jne arm excludes My Nether's Delight, so assess its substrate/rendering independently. Compare control-mnd with jne-mnd on the same seed to see whether JNE suppresses Powdery Cannon generation. The historical JNE darkness report involved No Man's Land, which is not part of this S0-S7 stack; note unexpected darkness without attributing that report to MND."
echo "Record coordinates, screenshots, and concerns. The control/JNE/Incendium arms inform substrate choice. The control-mnd and jne-mnd runs form a matched Powdery Cannon interaction check, not additional substrate candidates. Do not tune recipes, drops, spawn rates, loot, or balance."
echo "Run directory: $run"
echo "Diagnostics: $bundle"
echo "Launching the copied profile now. Save and quit normally when finished."

python_bin="$(command -v python3 || command -v python || true)"
if [[ -z "$python_bin" ]]; then echo "Python 3 is required for the production launcher." >&2; exit 2; fi
venv="$PWD/.skyforge-diagnostics/wby-s5-launcher-venv"
if [[ -x "$venv/Scripts/python.exe" ]]; then launcher="$venv/Scripts/python.exe"; else launcher="$venv/bin/python"; fi
if [[ ! -x "$launcher" ]]; then
  "$python_bin" -m venv "$venv"
  if [[ -x "$venv/Scripts/python.exe" ]]; then launcher="$venv/Scripts/python.exe"; else launcher="$venv/bin/python"; fi
fi
if ! "$launcher" -c 'import minecraft_launcher_lib' >/dev/null 2>&1; then
  "$launcher" -m pip install --disable-pip-version-check -r scripts/wby-s4-launcher-requirements.txt
fi

set +e
"$launcher" scripts/wby-s4-launch-production-client.py \
  --minecraft-directory "$PWD/.skyforge-diagnostics/wby-s5-production-client" \
  --game-directory "$PWD/$run" --username WbyS8Review 2>&1 | tee "$bundle/client.log"
status=${PIPESTATUS[0]}
set -e
test ! -f "$run/logs/latest.log" || cp "$run/logs/latest.log" "$bundle/latest.log"
if [[ -d "$run/crash-reports" ]]; then cp -R "$run/crash-reports" "$bundle/"; fi
find "$run/mods" -maxdepth 1 -type f -name '*.jar' -printf '%f\n' | sort > "$bundle/staged-mods.txt"
echo "Send the diagnostics folder if anything failed: $bundle"
exit "$status"
