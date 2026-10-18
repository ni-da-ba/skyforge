#!/usr/bin/env bash
set -euo pipefail

task_log="sf-imp-0054-tectonic-terrain-task.log"
result_dir="skyforge-neoforge-1211/build/acceptance/sf-imp-0054-tectonic"
rm -f "$task_log"
rm -rf "$result_dir"
mkdir -p "$result_dir"

./gradlew --no-daemon --stacktrace :skyforge-neoforge-1211:sfImp0054TectonicTerrainAcceptance 2>&1 | tee "$task_log"
