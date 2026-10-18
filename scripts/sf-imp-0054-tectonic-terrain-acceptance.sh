#!/usr/bin/env bash
set -euo pipefail

task_log="sf-imp-0054-tectonic-terrain-task.log"
result_dir="skyforge-neoforge-1211/build/acceptance/sf-imp-0054-tectonic"
rm -f "$task_log"
rm -rf "$result_dir"
mkdir -p "$result_dir"

./gradlew --no-daemon --no-configuration-cache --stacktrace :skyforge-neoforge-1211:sfImp0054TectonicTerrainAcceptance 2>&1 | tee "$task_log"

cache_root="${GRADLE_USER_HOME:-${HOME}/.gradle}/caches/modules-2/files-2.1"
hash_report="${result_dir}/runtime-artifact-sha256.txt"
: > "$hash_report"
for artifact in "maven.modrinth/lWDHr9jE/n4iyW7aB" "maven.modrinth/XaDC71GB/dGZOQXkv"; do
  artifact_dir="${cache_root}/${artifact}"
  if [[ ! -d "$artifact_dir" ]]; then
    printf 'Pinned runtime artifact cache is missing: %s\n' "$artifact_dir" >&2
    exit 1
  fi
  mapfile -t artifact_jars < <(find "$artifact_dir" -type f -name '*.jar' | sort)
  if [[ "${#artifact_jars[@]}" -ne 1 ]]; then
    printf 'Expected one pinned runtime JAR under %s, found %s\n' "$artifact_dir" "${#artifact_jars[@]}" >&2
    exit 1
  fi
  printf '%s\n' "$artifact" >> "$hash_report"
  sha256sum -- "${artifact_jars[0]}" >> "$hash_report"
done
