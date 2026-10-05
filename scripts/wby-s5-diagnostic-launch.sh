#!/usr/bin/env bash
set -euo pipefail

mode="${1:-shaders}"
cloud_interior="${2:-off}"
case "$mode" in
  shaders)
    task=":skyforge-neoforge-1211:runWbyS1DiagnosticClient"
    run_directory="run-wby-s5-shaders"
    shader_enabled=true
    windy_overlay=false
    ;;
  shaders-windy)
    task=":skyforge-neoforge-1211:runWbyS1DiagnosticClient"
    run_directory="run-wby-s5-shaders-windy"
    shader_enabled=true
    windy_overlay=true
    ;;
  server)
    task=":skyforge-neoforge-1211:runWbyS1DiagnosticServer"
    run_directory="run-wby-s5-server"
    shader_enabled=false
    windy_overlay=false
    ;;
  *)
    echo "usage: $0 [shaders|shaders-windy|server] [off|on]" >&2
    exit 2
    ;;
esac
if [[ "$cloud_interior" != "off" && "$cloud_interior" != "on" ]]; then
  echo "cloud interior mode must be off or on" >&2
  exit 2
fi
if [[ "$mode" == "server" && "$cloud_interior" != "off" ]]; then
  echo "cloud interior mode applies only to the shader client profile" >&2
  exit 2
fi

a4mc_mod_dir="$PWD/.skyforge-diagnostics/aerodynamics4mc-0.2.2-mods"
a4mc_core="$a4mc_mod_dir/aerodynamics4mc-0.2.2-neoforge+1.21.1.jar"
a4mc_compat="$a4mc_mod_dir/aerodynamics4mc-compat-create-aeronautics-0.2.2-neoforge+1.21.1.jar"
if [[ ! -f "$a4mc_core" || ! -f "$a4mc_compat" ]]; then
  cat >&2 <<'EOF'
The pinned A4MC 0.2.2 runtime jars are missing.
Do not build them on the workstation. Download the a4mc-0.2.2 artifact from the
WBY S5 GitHub Actions run, then extract both jars into:
  .skyforge-diagnostics/aerodynamics4mc-0.2.2-mods/
EOF
  exit 2
fi

run_dir="skyforge-neoforge-1211/$run_directory"
mkdir -p .skyforge-diagnostics "$run_dir"

if [[ "$mode" == "shaders" || "$mode" == "shaders-windy" ]]; then
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
level-name=s5-acceptance
level-seed=817304
level-type=minecraft:normal
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
      gsub(/[[:space:]"]/, "", value)
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
assert_hearthandharvest_worldgen_policy() {
  local data="$1/kubejs/data"
  local nests="$data/skyforge/neoforge/biome_modifier/disable_hearthandharvest_nests.json"
  local salt="$data/skyforge/neoforge/biome_modifier/disable_hearthandharvest_salt_caves.json"
  local lilliput="$data/hearthandharvest/tags/worldgen/biome/has_structure/lilliput_lane.json"
  local maze="$data/hearthandharvest/tags/worldgen/biome/has_structure/corn_maze.json"
  test -s "$nests" && grep -Fq '"features": "hearthandharvest:nest"' "$nests"
  test -s "$salt" && grep -Fq '"features": "hearthandharvest:salt_cave"' "$salt"
  test -s "$lilliput" && grep -Fq '"replace": true' "$lilliput" && grep -Fq '"values": []' "$lilliput"
  test -s "$maze" && grep -Fq '"replace": true' "$maze" && grep -Fq '"values": []' "$maze"
}

bundle=".skyforge-diagnostics/wby-s5-$mode-$stamp"
mkdir -p "$bundle"
log="$bundle/console.log"

echo "Cumulative gameplay: S1 atmosphere/mobility + S2 computing + S3 Diesel Generators/CBC + S4 life/building."
echo "S4 content: Farmer's Delight, Hearth and Harvest, Create: Central Kitchen, Create: Dragons Plus, Supplementaries, Moonlight."
echo "S5 candidate content: Naturalist, Fowl Play, Critters & Companions, Alex's Mobs Continued, Sky Whales, Biomes O' Plenty, Regions Unexplored, Nature's Spirit."
echo "Create a fresh world with the Skyforge preset and seed 817304; do not reuse an existing save. Review biome transitions, duplicate/dense fauna, Sky Whales near sky islands, and unexpected worldgen."
if [[ "$shader_enabled" == true ]]; then
  echo "Shader overlay: Iris 1.8.14 beta 1 + Iris/Oculus for Simple Clouds 1.1.3 NeoForge beta."
  echo "Selected shader: Atmospheric Shaders 0.2 (DH fixes; Simple Clouds support)."
  if [[ "$cloud_interior" == "off" ]]; then
    echo "Diagnostic A/B state: bridge interior fog/mesh suppression is disabled."
  else
    echo "Diagnostic A/B state: bridge interior fog/mesh suppression is enabled for comparison."
  fi
  echo "Run directory: $run_dir"
  echo "Diagnostics bundle: $bundle"
  if [[ "$windy_overlay" == true ]]; then
    echo "Windy is a source-built, patched client-only review overlay; compare its A4MC-driven ribbons/wisps to physical airflow."
    echo "Check open sky, below island overhangs, cloud intersections, and dusk/storm; note missing effects, visual conflicts, or frame-time drops."
  else
    echo "This is the baseline S5 shader profile without Windy; use the same fresh-world seed and route before comparing the separate Windy overlay."
  fi
  echo "Keep Atmospheric Shaders Cloud Style OFF. Review DH LOD visibility/blending, horizon fog, terrain occlusion, and worldgen leakage."
  echo "A title-screen launch alone is insufficient: open the fresh Skyforge-preset world and inspect it in-game."
else
  echo "This server profile intentionally excludes Iris, the Simple Clouds bridge, and all shaderpack files."
  echo "Run directory: $run_dir"
  echo "Diagnostics bundle: $bundle"
fi

status=0
if [[ "$mode" == "shaders" || "$mode" == "shaders-windy" ]]; then
  python_bin="$(command -v python3 || command -v python || true)"
  if [[ -z "$python_bin" ]]; then
    echo "Install Python 3.10 or newer, then rerun this command." >&2
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

  JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dskyforge.dev.waveC25PetroleumAuthority=suppressed" \
    ./gradlew :skyforge-neoforge-1211:wbyS1ResolvePinnedMods \
      -PwbyS1Glider=combined -PwbyS1Clouds=simple-clouds -PwbyS1ThinAir=false \
      -PwbyS1RunDirectory="$run_directory" -PwbyS2ComputingAvionics=true \
      -PwbyS3DieselGenerators=true -PwbyS3CreateBigCannons=true \
      -PwbyS4OrdinaryLife=true -PwbyS5OverworldEcology=true -PwbyS4Shaders=true \
      -PwbyS1A4mcBuiltModDir="$a4mc_mod_dir" --no-configuration-cache \
      2>&1 | tee "$bundle/profile-resolution.log"
  JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dskyforge.dev.waveC25PetroleumAuthority=suppressed" \
    ./gradlew :skyforge-neoforge-1211:wbyS1StageClientMods \
      -PwbyS1Glider=combined -PwbyS1Clouds=simple-clouds -PwbyS1ThinAir=false \
      -PwbyS1RunDirectory="$run_directory" -PwbyS2ComputingAvionics=true \
      -PwbyS3DieselGenerators=true -PwbyS3CreateBigCannons=true \
      -PwbyS4OrdinaryLife=true -PwbyS5OverworldEcology=true -PwbyS4Shaders=true \
      -PwbyS1A4mcBuiltModDir="$a4mc_mod_dir" --no-configuration-cache \
      2>&1 | tee "$bundle/profile-stage.log"
  if [[ "$windy_overlay" == true ]]; then
    windy_jar="$PWD/.skyforge-diagnostics/windy-1.2.0-skyforge-a4mc.jar"
    if [[ ! -f "$windy_jar" ]]; then
      echo "Missing patched Windy review JAR: $windy_jar" >&2
      echo "Download it from the wby-s5-overworld-ecology-resolution Actions artifact." >&2
      exit 2
    fi
    mkdir -p "$run_dir/mods" "$run_dir/config"
    install -m 0644 "$windy_jar" "$run_dir/mods/windy-1.2.0.jar"
    cat > "$run_dir/config/aerodynamics4mc-particles.json" <<'EOF'
{
  "enabled": true,
  "whitelist": [],
  "blacklist": ["dev.fallingcloud.windy.particle.*"]
}
EOF
    sha256sum "$run_dir/mods/windy-1.2.0.jar" | tee "$bundle/windy-sha256.txt"
  fi
  JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dskyforge.dev.waveC25PetroleumAuthority=suppressed" \
    ./gradlew :skyforge-neoforge-1211:wbyS1StagePolicy \
      -PwbyS1Glider=combined -PwbyS1Clouds=simple-clouds -PwbyS1ThinAir=false \
      -PwbyS1RunDirectory="$run_directory" -PwbyS2ComputingAvionics=true \
      -PwbyS3DieselGenerators=true -PwbyS3CreateBigCannons=true \
      -PwbyS4OrdinaryLife=true -PwbyS5OverworldEcology=true -PwbyS4Shaders=true \
      -PwbyS1A4mcBuiltModDir="$a4mc_mod_dir" --no-configuration-cache \
      2>&1 | tee "$bundle/profile-policy-stage.log"
  assert_supplementaries_policy "$run_dir/config/supplementaries-common.toml"
  assert_hearthandharvest_policy "$run_dir/config/hearthandharvest-common.toml"
  assert_hearthandharvest_worldgen_policy "$run_dir"
  cloud_config="$run_dir/config/oculus_for_simpleclouds-client.toml"
  grep -Fq '[interior_clouds]' "$cloud_config"
  if [[ "$cloud_interior" == "on" ]]; then
    sed -i 's/^enabled = false$/enabled = true/' "$cloud_config"
  fi
  expected_cloud_interior=false
  [[ "$cloud_interior" == "on" ]] && expected_cloud_interior=true
  grep -Fqx "enabled = $expected_cloud_interior" "$cloud_config"
  echo "S4 cloud diagnostic policy staging PASS (interior effect $cloud_interior)."
  "$launcher_python" scripts/wby-s4-launch-production-client.py \
    --minecraft-directory "$PWD/.skyforge-diagnostics/wby-s5-production-client" \
    --game-directory "$PWD/$run_dir" \
    --username WbyS4Review 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  set -e
else
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
      -PwbyS4OrdinaryLife=true -PwbyS5OverworldEcology=true \
      -PwbyS4Shaders="$shader_enabled" \
      -PwbyS1A4mcBuiltModDir="$a4mc_mod_dir" \
      --no-configuration-cache 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  set -e
fi

run_path="skyforge-neoforge-1211/$run_directory"
if [[ "$status" -eq 0 ]]; then
  if ! assert_supplementaries_policy "$run_path/config/supplementaries-common.toml"; then
    echo "S4 Supplementaries worldgen policy was not active after the profile loaded." >&2
    status=1
  fi
  if ! assert_hearthandharvest_policy "$run_path/config/hearthandharvest-common.toml"; then
    echo "S4 Hearth and Harvest Corn Maze config policy was not active after the profile loaded." >&2
    status=1
  fi
  if ! assert_hearthandharvest_worldgen_policy "$run_path"; then
    echo "S4 Hearth and Harvest KubeJS worldgen policy was not staged correctly." >&2
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
