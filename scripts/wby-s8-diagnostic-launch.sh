#!/usr/bin/env bash
set -euo pipefail

variant="${1:-}"
case "$variant" in
  control|control-mnd|jne|jne-mnd|incendium|incendium-full|incendium-full-wither|incendium-full-betterbastions|incendium-betternether) ;;
  *) echo "Usage: bash scripts/wby-s8-diagnostic-launch.sh {control|control-mnd|jne|jne-mnd|incendium|incendium-full|incendium-full-wither|incendium-betternether}" >&2; exit 2 ;;
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

if [[ "$variant" == "incendium-full" || "$variant" == "incendium-full-wither" || "$variant" == "incendium-full-betterbastions" ]]; then
  dependency_variant="$variant"
  if [[ "$variant" == "incendium-full-betterbastions" ]]; then dependency_variant="incendium-full"; fi
  python_bin="$(command -v python3 || command -v python || true)"
  if [[ -z "$python_bin" ]]; then echo "Python 3 is required to stage the official review dependencies." >&2; exit 2; fi
  "$python_bin" scripts/wby-s8-download-nether-review-deps.py "$dependency_variant"
  full_downloads="$downloads/$dependency_variant"
  expected_full_jars=(
    '*netherdepthsupgrade*.jar'
    '*nether*industry*.jar'
    '*thinair*.jar'
    '*curios*.jar'
    '*better*fortress*.jar'
    '*mynethersdelight*.jar'
  )
  if [[ "$variant" == "incendium-full-wither" ]]; then
    expected_full_jars+=('*wither*reincarnated*.jar')
  fi
  for pattern in "${expected_full_jars[@]}"; do
    mapfile -t matches < <(find "$full_downloads" -maxdepth 1 -type f -iname "$pattern" -print)
    if (( ${#matches[@]} != 1 )); then
      echo "Expected exactly one full-stack JAR matching $pattern in $full_downloads; found ${#matches[@]}" >&2
      exit 2
    fi
    cp -f "${matches[0]}" "$run/mods/"
  done
  if [[ "$variant" == "incendium-full-betterbastions" ]]; then
    candidate_downloads="$downloads/$variant"
    candidate="$candidate_downloads/betterbastions-1.0.0+neoforge-1.21.1.jar"
    mkdir -p "$candidate_downloads"
    if [[ ! -s "$candidate" ]]; then
      cat >&2 <<EOF
Download the official Better Bastions 1.0.0 NeoForge 1.21.1 file from:
  https://www.curseforge.com/minecraft/mc-mods/better-bastions/files
Save it as:
  $candidate
Select the exact file named betterbastions-1.0.0+neoforge-1.21.1.jar.
This A/B binary stays local: CurseForge metadata says All Rights Reserved while the author page states MIT modpack permission.
EOF
      exit 2
    fi
    cp -f "$candidate" "$run/mods/"
    "$python_bin" - "$candidate" "$bundle/better-bastions-artifact.txt" <<'PY'
import hashlib
import sys
from pathlib import Path
from zipfile import ZipFile
jar = Path(sys.argv[1])
with ZipFile(jar) as archive:
    metadata = [name for name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml") if name in archive.namelist()]
    if not metadata:
        raise SystemExit("Better Bastions file has no NeoForge/Forge mod metadata")
    text = "\n".join(archive.read(name).decode("utf-8", "replace") for name in metadata)
    if "betterbastions" not in text.lower():
        raise SystemExit("Downloaded jar does not identify itself as Better Bastions")
digest = hashlib.sha256(jar.read_bytes()).hexdigest()
Path(sys.argv[2]).write_text(f"filename={jar.name}\nsha256={digest}\n", encoding="utf-8")
print(f"Verified local Better Bastions artifact sha256={digest}")
PY
  fi
  pack="$full_downloads/Incendium-5.4.4.zip"
  if [[ ! -s "$pack" ]]; then echo "Official Incendium ZIP missing after staging: $pack" >&2; exit 2; fi
  echo "During Create World > Data Packs > Open Pack Folder, copy this ZIP into the opened world datapacks folder:"
  echo "  $pack"
  echo "Select Incendium in the Data Packs screen before creating the world."
fi

if [[ "$variant" == "incendium-betternether" ]]; then
  python_bin="$(command -v python3 || command -v python || true)"
  if [[ -z "$python_bin" ]]; then echo "Python 3 is required to stage the official review dependencies." >&2; exit 2; fi
  "$python_bin" scripts/wby-s8-download-nether-review-deps.py incendium-betternether
  composition_downloads="$downloads/incendium-betternether"
  for pattern in '*betternether*.jar' '*bclib*.jar' '*wunderlib*.jar' '*worldweaver*.jar'; do
    mapfile -t matches < <(find "$composition_downloads" -maxdepth 1 -type f -iname "$pattern" -print)
    if (( ${#matches[@]} != 1 )); then
      echo "Expected exactly one composition JAR matching $pattern in $composition_downloads; found ${#matches[@]}" >&2
      exit 2
    fi
    cp -f "${matches[0]}" "$run/mods/"
  done
  pack="$composition_downloads/Incendium-5.4.4.zip"
  if [[ ! -s "$pack" ]]; then echo "Official Incendium ZIP missing after staging: $pack" >&2; exit 2; fi
  echo "Composition-only arm: Incendium plus BetterNether: New Dawn and its three required New Dawn libraries."
  echo "Create a new normal/default world, enable this pack, and inspect worldgen ownership, biome/feature overlap, structures, stability, and startup behavior."
  echo "Copy this Incendium ZIP through Create World > Data Packs > Open Pack Folder, then enable it before world creation:"
  echo "  $pack"
fi

if [[ "$variant" == "incendium" ]]; then
  pack="$downloads/Incendium-5.4.4.zip"
  if [[ ! -f "$pack" ]]; then
    echo "Download the official whole Incendium 5.4.4 data pack from https://modrinth.com/datapack/incendium/version/pwe1kTJE and save it as $pack" >&2
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
echo "For incendium-betternether, treat this as the isolated biome/feature composition qualification; do not infer compatibility from a successful launch alone."
echo "For incendium-full-wither, also compare the Wither fight against the ordinary Incendium full profile; summon one only in this disposable creative review world."
echo "For incendium-full-betterbastions, compare governed vanilla bastions in incendium-full against the local Better Bastions overlay. Use fresh worlds with seed 817304 so existing bastion chunks cannot bias the comparison."
echo "Better Bastions is local-only pending reconciliation of CurseForge's ARR metadata with the author's MIT modpack permission statement."
echo "Wither: Reincarnated is All Rights Reserved and is staged only in the local review folder; do not package or upload its jar."
echo "For incendium-full, review the cumulative Nether stack: terrain/biomes, Nether Depths Upgrade ecology/resources, My Nether's Delight, Create: Nether Industry inputs and processing, ThinAir breathability and Create backtank interaction, and YUNG fortress/Blaze access. Check how these systems interact with the accepted Eternal Nether and S7 threat/BOMD layers."
echo "Record coordinates, screenshots, concerns, and any progression bypass. The control/JNE/Incendium arms inform substrate choice. The control-mnd and jne-mnd runs form a matched Powdery Cannon interaction check, not additional substrate candidates. Do not tune recipes, drops, spawn rates, loot, or balance."
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
