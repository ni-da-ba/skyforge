#!/usr/bin/env bash
set -euo pipefail

mode="\${1:-client}"
case "$mode" in
  client) task=":skyforge-neoforge-1211:runWbyS1DiagnosticClient" ;;
  server) task=":skyforge-neoforge-1211:runWbyS1DiagnosticServer" ;;
  *) echo "usage: $0 [client|server]" >&2; exit 2 ;;
esac

run_directory="run-wby-s2-integrated"
run_dir="skyforge-neoforge-1211/$run_directory"
mkdir -p .skyforge-diagnostics "$run_dir"
if [[ "$mode" == "server" ]]; then
  printf 'eula=true\n' > "$run_dir/eula.txt"
fi

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
bundle=".skyforge-diagnostics/wby-s2-$mode-$stamp"
mkdir -p "$bundle"
log="$bundle/console.log"

a4mc_source_dir=".skyforge-diagnostics/aerodynamics4mc-0.2.2-src"
a4mc_mod_dir="$PWD/.skyforge-diagnostics/aerodynamics4mc-0.2.2-mods"
a4mc_commit="171d8dc593651d6b34e3bfecaf6469a11b53b433"
a4mc_core="aerodynamics4mc-0.2.2-neoforge+1.21.1.jar"
a4mc_compat="aerodynamics4mc-compat-create-aeronautics-0.2.2-neoforge+1.21.1.jar"

echo "Preparing the pinned Aerodynamics4MC 0.2.2 source ($a4mc_commit)."
if [[ ! -d "$a4mc_source_dir/.git" ]]; then
  mkdir -p "$(dirname "$a4mc_source_dir")"
  git clone --no-checkout https://github.com/MozillaFiredoge/Aerodynamics4MC-Core.git "$a4mc_source_dir"
fi
git -C "$a4mc_source_dir" fetch --depth 1 origin 0.2.2
git -C "$a4mc_source_dir" checkout --detach "$a4mc_commit"
(
  cd "$a4mc_source_dir"
  MOD_IS_RELEASE=true ./gradlew buildAndCollect --no-daemon
) 2>&1 | tee "$bundle/aerodynamics4mc-build.log"

mkdir -p "$a4mc_mod_dir"
cp "$a4mc_source_dir/build/libs/0.2.2/$a4mc_core" "$a4mc_source_dir/build/libs/0.2.2/$a4mc_compat" "$a4mc_mod_dir/"
sha256sum "$a4mc_mod_dir/"*.jar | tee "$bundle/aerodynamics4mc-sha256.txt"

echo "Launching WBY S2 integrated client/server profile."
echo "Computing overlay: CC:Tweaked + Create: Avionics; AAL remains excluded."
echo "Run directory: $run_dir"
if [[ "$mode" == "client" ]]; then
  echo "Create a new Skyforge preset world in this isolated profile."
  echo "Diagnostics bundle: $bundle"
  echo "When finished, send the diagnostics folder: $bundle"
fi
echo "Staged mod inventory will be saved to $bundle/staged-mods.txt"

set +e
./gradlew "$task" \
  -PwbyS1Glider=combined \
  -PwbyS1Clouds=simple-clouds \
  -PwbyS1ThinAir=false \
  -PwbyS1RunDirectory="$run_directory" \
  -PwbyS2ComputingAvionics=true \
  -PwbyS1A4mcBuiltModDir="$a4mc_mod_dir" \
  --no-configuration-cache 2>&1 | tee "$log"
status=\${PIPESTATUS[0]}
set -e

test ! -f "$run_dir/logs/latest.log" || cp "$run_dir/logs/latest.log" "$bundle/latest.log"
test ! -d "$run_dir/crash-reports" || cp -R "$run_dir/crash-reports" "$bundle/crash-reports"
test ! -d "$run_dir/config" || cp -R "$run_dir/config" "$bundle/config"
test ! -d "$run_dir/mods" || find "$run_dir/mods" -maxdepth 1 -type f -name '*.jar' -printf '%f\n' | sort > "$bundle/staged-mods.txt"

exit "$status"
