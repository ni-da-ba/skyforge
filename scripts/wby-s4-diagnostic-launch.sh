#!/usr/bin/env bash
set -euo pipefail

mode="${1:-shaders}"
case "$mode" in
  shaders)
    task=":skyforge-neoforge-1211:runWbyS1DiagnosticClient"
    run_directory="run-wby-s4-shaders"
    shader_enabled=true
    ;;
  server)
    task=":skyforge-neoforge-1211:runWbyS1DiagnosticServer"
    run_directory="run-wby-s4-server"
    shader_enabled=false
    ;;
  *)
    echo "usage: $0 [shaders|server]" >&2
    exit 2
    ;;
esac

a4mc_mod_dir="$PWD/.skyforge-diagnostics/aerodynamics4mc-0.2.2-mods"
a4mc_core="$a4mc_mod_dir/aerodynamics4mc-0.2.2-neoforge+1.21.1.jar"
a4mc_compat="$a4mc_mod_dir/aerodynamics4mc-compat-create-aeronautics-0.2.2-neoforge+1.21.1.jar"
if [[ ! -f "$a4mc_core" || ! -f "$a4mc_compat" ]]; then
  cat >&2 <<'EOF'
The pinned A4MC 0.2.2 runtime jars are missing.
Do not build them on the workstation. Download the a4mc-0.2.2 artifact from the
WBY S4 GitHub Actions run, then extract both jars into:
  .skyforge-diagnostics/aerodynamics4mc-0.2.2-mods/
EOF
  exit 2
fi

run_dir="skyforge-neoforge-1211/$run_directory"
mkdir -p .skyforge-diagnostics "$run_dir"

if [[ "$mode" == "shaders" ]]; then
  shaderpack="$run_dir/shaderpacks/AtmosphericShaders_0.2.zip"
  mkdir -p "$(dirname "$shaderpack")"
  if [[ ! -f "$shaderpack" ]]; then
    cat >&2 <<EOF
Place the licensed shader ZIP here before launching:
  $shaderpack
Download page: https://www.curseforge.com/minecraft/shaders/atmospheric-shaders/files/8153328
Keep the ZIP compressed. Do not rehost or add it to the modpack archive.
EOF
    exit 2
  fi
else
  printf 'eula=true\n' > "$run_dir/eula.txt"
  cat > "$run_dir/server.properties" <<'EOF'
level-name=s4-acceptance
level-seed=817304
level-type=minecraft:flat
online-mode=false
enforce-secure-profile=false
spawn-protection=0
gamemode=creative
difficulty=peaceful
view-distance=5
simulation-distance=3
server-port=25565
EOF
fi

assert_supplementaries_policy() {
  local config="$1"
  test -f "$config"
  awk '
    function trim(value) {
      sub(/^[[:space:]]+/, "", value)
      sub(/[[:space:]]+$/, "", value)
      return value
    }
    BEGIN {
      expected["building.way_sign.road_signs.enabled"] = "false"
      expected["building.ash.basalt_ash"] = "false"
      expected["functional.urn.cave_urns"] = "false"
      expected["functional.flax.wild_flax"] = "false"
      expected["functional.cannon.plunderer.galleon"] = "false"
      expected["redstone.pulley_block.mineshaft_elevator"] = "0.0"
    }
    /^[[:space:]]*#/ { next }
    /^[[:space:]]*\[/ {
      section = $0
      sub(/^[[:space:]]*\[/, "", section)
      sub(/\][[:space:]]*$/, "", section)
      gsub(/[[:space:]]/, "", section)
      next
    }
    index($0, "=") {
      line = $0
      sub(/[[:space:]]*#.*/, "", line)
      split_at = index(line, "=")
      key = trim(substr(line, 1, split_at - 1))
      value = trim(substr(line, split_at + 1))
      gsub(/[[:space:]]/, "", key)
      gsub(/[[:space:]\"]/, "", value)
      full_key = (index(key, ".") ? key : (section == "" ? key : section "." key))
      if (full_key in expected && value == expected[full_key]) found[full_key] = 1
    }
    END {
      for (key in expected) {
        if (!found[key]) {
          print "Supplementaries policy missing or incorrect: " key " (expected " expected[key] ")" > "/dev/stderr"
          exit 1
        }
      }
    }
  ' "$config"
}


assert_hearthandharvest_policy() {
  local config="$1"
  test -f "$config"
  awk '
    function trim(value) {
      sub(/^[[:space:]]+/, "", value)
      sub(/[[:space:]]+$/, "", value)
      return value
    }
    BEGIN {
      expected["generateCornMazes"] = "false"
      expected["generateLilliputLane"] = "false"
      expected["nests.generateNests"] = "false"
      expected["salt.generateSaltCaves"] = "false"
    }
    /^[[:space:]]*#/ { next }
    /^[[:space:]]*\[/ {
      section = $0
      sub(/^[[:space:]]*\[/, "", section)
      sub(/\][[:space:]]*$/, "", section)
      gsub(/[[:space:]]/, "", section)
      next
    }
    index($0, "=") {
      line = $0
      sub(/[[:space:]]*#.*/, "", line)
      split_at = index(line, "=")
      key = trim(substr(line, 1, split_at - 1))
      value = trim(substr(line, split_at + 1))
      gsub(/[[:space:]]/, "", key)
      gsub(/[[:space:]]/,"", value)
      full_key = (index(key, ".") ? key : (section == "" ? key : section "." key))
      if (full_key in expected && value == expected[full_key]) found[full_key] = 1
    }
    END {
      for (key in expected) {
        if (!found[key]) {
          print "Hearth and Harvest worldgen policy missing or incorrect: " key " (expected " expected[key] ")" > "/dev/stderr"
          exit 1
        }
      }
    }
  ' "$config"
}

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
bundle=".skyforge-diagnostics/wby-s4-$mode-$stamp"
mkdir -p "$bundle"
log="$bundle/console.log"

echo "S4 gameplay stack: Farmer's Delight + Hearth and Harvest, Create: Central Kitchen, Create: Dragons Plus, Supplementaries, Moonlight."
echo "Hearth and Harvest is present for CCK Cask GameTest linkage; its Lilliput Lane, Corn Maze, nest, and salt-cave worldgen are disabled by the staged common config."
echo "Cumulative base: S1 atmosphere/mobility + S2 computing + S3 Diesel Generators/CBC."
if [[ "$shader_enabled" == true ]]; then
  echo "Shader overlay: Iris 1.8.14 beta 1 + Iris/Oculus for Simple Clouds 1.1.3 NeoForge beta."
  echo "Selected shader: Atmospheric Shaders 0.2 (DH fixes; Simple Clouds support)."
  echo "Run directory: $run_dir"
  echo "Diagnostics bundle: $bundle"
  echo "After the launcher opens, review cloud rendering, DH LOD visibility/blending, horizon fog, and terrain occlusion."
  echo "Do not count a successful title-screen launch alone: open a world and confirm Simple Clouds still renders with the shader selected."
else
  echo "This server profile intentionally excludes Iris, the Simple Clouds bridge, and all shaderpack files."
  echo "Run directory: $run_dir"
  echo "Diagnostics bundle: $bundle"
fi

set +e
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dskyforge.dev.waveC25PetroleumAuthority=suppressed" \
./gradlew "$task" \
  -PwbyS1Glider=combined \
  -PwbyS1Clouds=simple-clouds \
  -PwbyS1ThinAir=false \
  -PwbyS1RunDirectory="$run_directory" \
  -PwbyS2ComputingAvionics=true \
  -PwbyS3DieselGenerators=true \
  -PwbyS3CreateBigCannons=true \
  -PwbyS4OrdinaryLife=true \
  -PwbyS4Shaders="$shader_enabled" \
  -PwbyS1A4mcBuiltModDir="$a4mc_mod_dir" \
  --no-configuration-cache 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
set -e

run_path="skyforge-neoforge-1211/$run_directory"
if [[ "$status" -eq 0 ]]; then
  if ! assert_supplementaries_policy "$run_path/config/supplementaries-common.toml"; then
    echo "S4 Supplementaries worldgen policy was not active after the profile loaded." >&2
    status=1
  fi
  if ! assert_hearthandharvest_policy "$run_path/config/hearthandharvest-common.toml"; then
    echo "S4 Hearth and Harvest worldgen policy was not active after the profile loaded." >&2
    status=1
  fi
fi
test ! -f "$run_path/logs/latest.log" || cp "$run_path/logs/latest.log" "$bundle/latest.log"
test ! -d "$run_path/crash-reports" || cp -R "$run_path/crash-reports" "$bundle/crash-reports"
test ! -d "$run_path/config" || cp -R "$run_path/config" "$bundle/config"
test ! -d "$run_path/defaultconfigs" || cp -R "$run_path/defaultconfigs" "$bundle/defaultconfigs"
test ! -d "$run_path/saves" || cp -R "$run_path/saves" "$bundle/saves"
test ! -d "$run_path/mods" || find "$run_path/mods" -maxdepth 1 -type f -name '*.jar' -printf '%f\n' | sort > "$bundle/staged-mods.txt"
echo "Send this diagnostics folder when finished: $bundle"
exit "$status"
