#!/usr/bin/env python3
import argparse
import os
from pathlib import Path


def load_properties(path: Path):
    props = {}
    for raw in path.read_text(errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    return props


def req_int(props, key):
    if key not in props:
        raise AssertionError(f"missing property {key}")
    try:
        return int(props[key])
    except ValueError as exc:
        raise AssertionError(f"non-integer property {key}={props[key]!r}") from exc


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("properties", type=Path)
    parser.add_argument("--active-cpus", type=int, required=True)
    parser.add_argument("--max-preparation-columns", type=int, required=True)
    args = parser.parse_args()
    props = load_properties(args.properties)

    assert props.get("status") == "PASS", f"fixture did not PASS: {props.get('status')}"
    assert props.get("lowerCompleted") == props.get("lowerRequired")
    assert props.get("upperCompleted") == props.get("upperRequired")
    assert props.get("independentLedgers") == "true"
    assert props.get("foreignFluidRejected") == "true"
    assert props.get("cavesCompleteBeforeInterior") == "true"
    assert props.get("noReplay") == "true"

    required = [
        "perf.processElapsedNanos",
        "perf.acceptance.warmOriginFootprint.totalNanos",
        "perf.catchup.composedCavePump.totalNanos",
        "perf.admission.nativeOccupancySurvey.totalNanos",
        "perf.terrain.realize.totalNanos",
        "perf.terrain.noCandidatePrefilter.totalNanos",
        "perf.terrain.plannedDirectProjectionSkipped.totalNanos",
        "perf.terrain.realizeDeferredPacket.totalNanos",
        "perf.terrain.deferred.materialize.totalNanos",
        "perf.terrain.deferred.materializeSlice.totalNanos",
        "perf.terrain.deferred.adaptSurface.totalNanos",
        "perf.terrain.deferred.solidCount.totalNanos",
        "perf.terrain.deferred.writePacket.totalNanos",
        "perf.terrain.deferred.completeCatchup.totalNanos",
        "perf.surfacePopulation.coordinator.totalNanos",
        "perf.surfacePopulation.findSurface.totalNanos",
        "perf.surfacePopulation.phase.VEGETAL_DECORATION.totalNanos",
        "perf.caves.authoredPreflight.totalNanos",
        "perf.caves.nativeCarver.totalNanos",
        "perf.caves.authoredCommit.totalNanos",
        "perf.interior.LAKES.totalNanos",
        "perf.interior.LOCAL_MODIFICATIONS.totalNanos",
        "perf.interior.UNDERGROUND_ORES.totalNanos",
        "perf.interior.UNDERGROUND_DECORATION.totalNanos",
        "perf.interior.FLUID_SPRINGS.totalNanos",
    ]
    for key in required:
        assert req_int(props, key) > 0, f"missing/nonpositive performance metric {key}"

    assert (
        req_int(props, "perf.admission.occupancySurveyVerticalSamples.samples"),
        req_int(props, "perf.admission.occupancySurveyVerticalSamples.total"),
        req_int(props, "perf.admission.occupancySurveyVerticalSamples.max"),
    ) == (392, 39592, 101)

    height_samples = req_int(props, "perf.terrain.firstFreeHeightVerticalSamples.samples")
    height_total = req_int(props, "perf.terrain.firstFreeHeightVerticalSamples.total")
    height_max = req_int(props, "perf.terrain.firstFreeHeightVerticalSamples.max")
    assert height_samples >= 100352 and height_total > 0 and height_max <= 101

    assert (
        req_int(props, "perf.terrain.deferredVerticalSamples.samples"),
        req_int(props, "perf.terrain.deferredVerticalSamples.total"),
        req_int(props, "perf.terrain.deferredVerticalSamples.max"),
    ) == (390, 39390, 101)

    materialize_calls = req_int(props, "perf.terrain.deferred.materialize.calls")
    assert materialize_calls == 390
    for phase in ("adaptSurface", "solidCount", "completeCatchup"):
        assert req_int(props, f"perf.terrain.deferred.{phase}.calls") == materialize_calls

    slice_samples = req_int(props, "perf.terrain.deferred.materializeSliceWallNanos.samples")
    slice_column_samples = req_int(props, "perf.terrain.deferred.materializeSliceColumns.samples")
    slice_column_total = req_int(props, "perf.terrain.deferred.materializeSliceColumns.total")
    slice_column_max = req_int(props, "perf.terrain.deferred.materializeSliceColumns.max")
    assert slice_samples == slice_column_samples and slice_samples > materialize_calls
    assert slice_column_total == materialize_calls * 256
    assert 0 < slice_column_max <= args.max_preparation_columns

    prep_p50 = req_int(props, "perf.terrain.deferred.materializeSliceWallNanos.p50")
    prep_p95 = req_int(props, "perf.terrain.deferred.materializeSliceWallNanos.p95")
    prep_p99 = req_int(props, "perf.terrain.deferred.materializeSliceWallNanos.p99")
    prep_max = req_int(props, "perf.terrain.deferred.materializeSliceWallNanos.max")
    prep_p99_target = int(os.environ.get("SKYFORGE_SF_IMP_0072_PREPARATION_P99_NANOS", "16000000"))
    assert 0 < prep_p50 <= prep_p95 <= prep_p99 <= prep_max
    assert prep_p99 < prep_p99_target, (
        f"preparation p99 gate failed: p50={prep_p50}, p95={prep_p95}, "
        f"p99={prep_p99}, max={prep_max}, target={prep_p99_target}"
    )

    write_calls = req_int(props, "perf.terrain.deferred.writePacket.calls")
    quantum_calls = req_int(props, "perf.terrain.realizeDeferredPacket.calls")
    pure_preparation_quanta = slice_samples - materialize_calls
    expected_quanta = write_calls + pure_preparation_quanta
    assert write_calls > materialize_calls and quantum_calls == expected_quanta, (
        f"packet/preparation quantum accounting changed: writePackets={write_calls}, "
        f"preparationSlices={slice_samples}, materializations={materialize_calls}, "
        f"quanta={quantum_calls}, expected={expected_quanta}"
    )

    packet_samples = req_int(props, "perf.terrain.deferred.packetWallNanos.samples")
    packet_p50 = req_int(props, "perf.terrain.deferred.packetWallNanos.p50")
    packet_p95 = req_int(props, "perf.terrain.deferred.packetWallNanos.p95")
    packet_p99 = req_int(props, "perf.terrain.deferred.packetWallNanos.p99")
    packet_max = req_int(props, "perf.terrain.deferred.packetWallNanos.max")
    packet_p99_target = int(os.environ.get("SKYFORGE_SF_IMP_0071_PACKET_P99_NANOS", "16000000"))
    packet_cpu_samples = req_int(props, "perf.terrain.deferred.packetCpuNanos.samples")
    packet_cpu_max = req_int(props, "perf.terrain.deferred.packetCpuNanos.max")
    packet_cpu_max_target = int(os.environ.get("SKYFORGE_SF_IMP_0071_PACKET_CPU_MAX_NANOS", "16000000"))
    assert packet_samples == write_calls and packet_cpu_samples == write_calls
    assert 0 < packet_p50 <= packet_p95 <= packet_p99 <= packet_max
    assert packet_p99 < packet_p99_target and packet_cpu_max < packet_cpu_max_target, (
        f"packet deterministic gate failed: wallP99={packet_p99}, rawWallMax={packet_max}, "
        f"cpuMax={packet_cpu_max}, p99Target={packet_p99_target}, cpuMaxTarget={packet_cpu_max_target}"
    )

    assigned_samples = req_int(props, "perf.terrain.deferred.packetAssignedSolidWrites.samples")
    assigned_total = req_int(props, "perf.terrain.deferred.packetAssignedSolidWrites.total")
    assigned_max = req_int(props, "perf.terrain.deferred.packetAssignedSolidWrites.max")
    expected_samples = req_int(props, "perf.terrain.deferred.expectedSolidBlocks.samples")
    expected_total = req_int(props, "perf.terrain.deferred.expectedSolidBlocks.total")
    assert assigned_samples == write_calls
    assert assigned_max <= 1024
    assert expected_samples == materialize_calls
    assert assigned_total == expected_total

    assert req_int(props, "perf.terrain.realize.calls") == 1
    assert req_int(props, "perf.terrain.plannedDirectProjectionSkipped.calls") == 195
    cave_preflights = req_int(props, "perf.caves.authoredPreflight.calls")
    cave_pumps = req_int(props, "perf.catchup.composedCavePump.calls")
    assert cave_pumps > 0 and cave_preflights >= cave_pumps * 4

    print(
        "SF-IMP-0072 PERFORMANCE VERIFY PASS: "
        f"activeCpus={args.active_cpus}, preparationSlices={slice_samples}, "
        f"preparationColumns={slice_column_total}, prepP50={prep_p50}, prepP95={prep_p95}, "
        f"prepP99={prep_p99}, prepMax={prep_max}, writePackets={write_calls}, "
        f"purePreparationQuanta={pure_preparation_quanta}, totalDeferredQuanta={quantum_calls}, "
        f"packetWallP99={packet_p99}, rawPacketWallMax={packet_max}, packetCpuMax={packet_cpu_max}"
    )


if __name__ == "__main__":
    main()
