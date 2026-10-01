#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path


def load_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        if "=" in line:
            key, value = line.split("=", 1)
        elif ":" in line:
            key, value = line.split(":", 1)
        else:
            continue
        values[key.strip()] = value.strip()
    return values


def require(values: dict[str, str], key: str, expected: str | None = None) -> str:
    if key not in values:
        raise SystemExit(f"missing benchmark evidence key: {key}")
    value = values[key]
    if expected is not None and value != expected:
        raise SystemExit(f"benchmark evidence {key} expected {expected!r}, got {value!r}")
    return value


def positive_int(values: dict[str, str], key: str, minimum: int = 1) -> int:
    value = int(require(values, key))
    if value < minimum:
        raise SystemExit(f"benchmark evidence {key} must be >= {minimum}, got {value}")
    return value


def positive_float(values: dict[str, str], key: str) -> float:
    value = float(require(values, key))
    if value <= 0.0:
        raise SystemExit(f"benchmark evidence {key} must be > 0, got {value}")
    return value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("properties", type=Path)
    args = parser.parse_args()

    if not args.properties.is_file():
        raise SystemExit(f"benchmark evidence file missing: {args.properties}")
    values = load_properties(args.properties)

    require(values, "status", "PASS")
    require(values, "clientExplorationServerPass", "true")
    require(values, "clientExplorationClientPass", "true")
    require(values, "benchmarkSchema", "perf-0502-v1")
    require(values, "benchmarkCase", "perf-0502-client-exploration")
    require(values, "movementDriver", "CLIENT_KEY_INPUT")
    require(values, "spawnIdleMode", "EXECUTED_REAL")
    require(values, "walkTraversalMode", "EXECUTED_LOAD_PROXY")
    require(values, "glideTraversalMode", "EXECUTED_LOAD_PROXY")
    require(values, "freshTerrainFlightMode", "EXECUTED_LOAD_PROXY")
    require(values, "activeMachineryCargoMode", "UNAVAILABLE_CURRENT_SLICE")
    require(values, "saveReloadMode", "UNAVAILABLE_CURRENT_SLICE")

    visited = positive_int(values, "visitedChunks", minimum=4)
    distance = positive_float(values, "maxHorizontalDistanceBlocks")
    frames = positive_int(values, "clientFrameCount", minimum=20)
    frame_samples = positive_int(values, "perf.clientExploration.clientFrameNanos.samples", minimum=20)
    tick_samples = positive_int(values, "perf.clientExploration.serverTickNanos.samples", minimum=100)
    fps = positive_float(values, "clientAverageFps")
    frame_p99 = positive_int(values, "perf.clientExploration.clientFrameNanos.p99")
    tick_p99 = positive_int(values, "perf.clientExploration.serverTickNanos.p99")

    phase_tick_p99: dict[str, int] = {}
    for phase in ("spawn_idle", "walk_traversal", "glide_traversal", "fresh_terrain_flight", "settle"):
        positive_int(values, f"perf.clientExploration.serverTickNanos.{phase}.samples")
        phase_tick_p99[phase] = positive_int(
            values, f"perf.clientExploration.serverTickNanos.{phase}.p99"
        )

    print("PERF-0502 client exploration benchmark PASS")
    print(f"  movementDriver=CLIENT_KEY_INPUT visitedChunks={visited} maxDistanceBlocks={distance:.2f}")
    print(f"  clientFrames={frames} samples={frame_samples} averageFps={fps:.3f}")
    print(f"  frameP99Ms={frame_p99 / 1_000_000.0:.3f}")
    print(f"  serverTickSamples={tick_samples} tickP99Ms={tick_p99 / 1_000_000.0:.3f}")
    print(
        "  phaseTickP99Ms="
        + ", ".join(
            f"{phase}:{value / 1_000_000.0:.3f}" for phase, value in phase_tick_p99.items()
        )
    )
    print("  GitHub/Xvfb FPS is characterization only; no release threshold is applied.")


if __name__ == "__main__":
    main()
