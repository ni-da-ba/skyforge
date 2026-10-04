#!/usr/bin/env bash
set -euo pipefail

mode="${1:-client}"
glider="${2:-none}"
clouds="${3:-none}"
thinair="${4:-false}"
distant_horizons="${5:-with-dh}"

case "$mode" in
  client)
    task=":skyforge-neoforge-1211:runWbyS1DiagnosticClient"
    ;;
  server)
    task=":skyforge-neoforge-1211:runWbyS1DiagnosticServer"
    ;;
  *)
    echo "usage: $0 [client|server] [none|reliable-gliders|ornithopter|combined] [none|better-clouds|simple-clouds] [true|false] [with-dh|without-dh]" >&2
    exit 2
    ;;
esac

case "$glider" in none|reliable-gliders|ornithopter|combined) ;; *) echo "invalid glider profile: $glider" >&2; exit 2 ;; esac
case "$clouds" in none|better-clouds|simple-clouds) ;; *) echo "invalid cloud profile: $clouds" >&2; exit 2 ;; esac
case "$thinair" in true|false) ;; *) echo "ThinAir must be true or false" >&2; exit 2 ;; esac
case "$distant_horizons" in with-dh|without-dh) ;; *) echo "Distant Horizons selector must be with-dh or without-dh" >&2; exit 2 ;; esac
if [[ "$mode" != "client" && "$distant_horizons" == "without-dh" ]]; then
  echo "without-dh is a client-only comparison profile" >&2
  exit 2
fi
without_dh=false
[[ "$distant_horizons" != "without-dh" ]] || without_dh=true

run_directory_name="run-wby-s1"
if [[ "$glider" == "combined" && "$clouds" == "simple-clouds" ]]; then
  run_directory_name="run-wby-s1-integrated"
elif [[ "$clouds" == "simple-clouds" ]]; then
  run_directory_name="run-wby-s1-simple-clouds"
fi
run_dir="skyforge-neoforge-1211/$run_directory_name"
mkdir -p .skyforge-diagnostics "$run_dir"
if [[ "$mode" == "server" ]]; then
  printf 'eula=true\n' > "$run_dir/eula.txt"
fi

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
bundle=".skyforge-diagnostics/wby-s1-$mode-$glider-$clouds-thinair-$thinair-$distant_horizons-$stamp"
mkdir -p "$bundle"
log="$bundle/console.log"

a4mc_source_dir=".skyforge-diagnostics/aerodynamics4mc-0.2.2-src"
a4mc_mod_dir="$PWD/.skyforge-diagnostics/aerodynamics4mc-0.2.2-mods"
a4mc_commit="171d8dc593651d6b34e3bfecaf6469a11b53b433"
a4mc_core="aerodynamics4mc-0.2.2-neoforge+1.21.1.jar"
a4mc_compat="aerodynamics4mc-compat-create-aeronautics-0.2.2-neoforge+1.21.1.jar"

echo "Preparing pinned Aerodynamics4MC 0.2.2 source ($a4mc_commit)."
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

echo "Launching WBY S1 $mode profile."
echo "glider=$glider clouds=$clouds thinAir=$thinair distantHorizons=$distant_horizons"
if [[ "$clouds" == "simple-clouds" ]]; then
  echo "Simple Clouds uses an isolated profile directory: $run_dir"
  echo "Create a fresh Skyforge preset world on the first run; this profile has its own isolated world directory."
  echo "Keep Minecraft's Clouds video setting enabled and Distant Horizons enabled for the selected profile."
else
  echo "The client opens to the title screen. Create or open a fresh world for the human review."
fi
if [[ "$glider" == "combined" ]]; then
  echo "Reliable Gliders and Create: Ornithopter Glider are staged together to review distinct early/late roles and coexistence."
fi
if [[ "$without_dh" == true ]]; then
  echo "Distant Horizons is excluded from this client classpath and staged-mod list."
fi
echo "No S0.5 synthetic visibility/structure fixture is enabled."
echo "Diagnostics bundle: $bundle"
echo "Full console output: $log"

set +e
./gradlew "$task" \
  -PwbyS1Glider="$glider" \
  -PwbyS1Clouds="$clouds" \
  -PwbyS1ThinAir="$thinair" \
  -PwbyS1WithoutDistantHorizons="$without_dh" \
  -PwbyS1RunDirectory="$run_directory_name" \
  -PwbyS1A4mcBuiltModDir="$a4mc_mod_dir" \
  --no-configuration-cache 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
set -e

test ! -f "$run_dir/logs/latest.log" || cp "$run_dir/logs/latest.log" "$bundle/latest.log"
test ! -d "$run_dir/crash-reports" || cp -R "$run_dir/crash-reports" "$bundle/crash-reports"
test ! -d "$run_dir/config" || cp -R "$run_dir/config" "$bundle/config"
test ! -d "$run_dir/mods" || find "$run_dir/mods" -maxdepth 1 -type f -name '*.jar' -printf '%f\n' | sort > "$bundle/staged-mods.txt"

exit "$status"
