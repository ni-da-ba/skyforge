#!/usr/bin/env python3
"""SF-IMP-1180 machine analysis for glider effect magnitude and hawk A/B."""

from __future__ import annotations

import argparse
import json
import math
import re
from pathlib import Path


def coupling_constants(source_path: Path) -> tuple[float, float, float]:
    text = source_path.read_text(encoding="utf-8")
    def value(name: str) -> float:
        match = re.search(rf"{name}\s*=\s*([0-9]+(?:\.[0-9]+)?);", text)
        if not match:
            raise SystemExit(f"could not read {name} from {source_path}")
        return float(match.group(1))
    return (
        value("TICKS_PER_SECOND"),
        value("RELIABLE_GLIDER_BASELINE_SINK_BLOCKS_PER_TICK"),
        value("SMOOTHING"),
    )


def apply_lift(current_y: float, trusted: bool, updraft: float, tps: float, sink: float, smoothing: float) -> float:
    if not trusted or not math.isfinite(current_y) or not math.isfinite(updraft) or updraft <= 0.0:
        return current_y
    target = updraft / tps - sink
    if target <= current_y:
        return current_y
    return current_y + smoothing * (target - current_y)


def analyze_glider(args: argparse.Namespace) -> None:
    probe = json.loads(args.probe.read_text(encoding="utf-8"))
    tps, sink, smoothing = coupling_constants(args.coupling_source)
    temporal = probe["temporal"]
    period = int(temporal["period_ticks"])
    frames = temporal["frames"]
    if len(frames) < 2:
        raise SystemExit("temporal probe needs at least two frames")
    point_count = len(frames[0]["samples"])
    if any(len(frame["samples"]) != point_count for frame in frames):
        raise SystemExit("temporal point count changed between frames")

    points = []
    for point_index in range(point_count):
        control_y = 0.0
        treatment_y = 0.0
        max_velocity_delta = 0.0
        max_updraft = -math.inf
        positive_intervals = 0
        sampled_updrafts = []
        # N frames span N-1 observed periods. Hold each measured sample over the following interval.
        for frame in frames[:-1]:
            sample = frame["samples"][point_index]
            updraft = float(sample["signed_vertical_air"])
            sampled_updrafts.append(updraft)
            max_updraft = max(max_updraft, updraft)
            trusted = bool(sample["trusted_for_gameplay"])
            control_v = -sink
            treatment_v = apply_lift(control_v, trusted, updraft, tps, sink, smoothing)
            if treatment_v > control_v:
                positive_intervals += 1
            max_velocity_delta = max(max_velocity_delta, treatment_v - control_v)
            control_y += control_v * period
            treatment_y += treatment_v * period

        first = frames[0]["samples"][point_index]
        points.append({
            "point_index": point_index,
            "position": first["position"],
            "ticks": (len(frames) - 1) * period,
            "control_relative_y_blocks": control_y,
            "treatment_relative_y_blocks": treatment_y,
            "accumulated_altitude_advantage_blocks": treatment_y - control_y,
            "max_per_tick_vertical_velocity_delta_blocks": max_velocity_delta,
            "positive_lift_intervals": positive_intervals,
            "mean_sampled_updraft_mps": sum(sampled_updrafts) / len(sampled_updrafts),
            "max_sampled_updraft_mps": max_updraft,
        })

    best = max(points, key=lambda row: row["accumulated_altitude_advantage_blocks"])
    result = {
        "schema_version": 1,
        "artifact_kind": "SKYFORGE_SF_IMP_1180_GLIDER_REAL_FIELD_AB",
        "source_probe_artifact_kind": probe.get("artifact_kind"),
        "terrain_provider_enabled": probe["terrain_provider_bridge"]["enabled"],
        "terrain_provider_registered": probe["terrain_provider_bridge"]["registered"],
        "contract": {
            "ticks_per_second": tps,
            "baseline_sink_blocks_per_tick": sink,
            "smoothing": smoothing,
            "method": "replay each authoritative temporal sample through exact SkyforgeGliderLiftCoupling contract; hold sample for its following 20-tick observed interval",
        },
        "points": points,
        "best_point_index": best["point_index"],
        "best_accumulated_altitude_advantage_blocks": best["accumulated_altitude_advantage_blocks"],
        "max_per_tick_vertical_velocity_delta_blocks": max(
            row["max_per_tick_vertical_velocity_delta_blocks"] for row in points
        ),
        "machine_measurable": any(row["accumulated_altitude_advantage_blocks"] > 1e-12 for row in points),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))


def analyze_hawks(args: argparse.Namespace) -> None:
    control = json.loads(args.control.read_text(encoding="utf-8"))
    treatment = json.loads(args.treatment.read_text(encoding="utf-8"))
    if control["arm"] != "control" or treatment["arm"] != "treatment":
        raise SystemExit("wrong hawk A/B arm labels")

    def keyed(doc: dict) -> dict[tuple[str, int], dict]:
        return {(row["region"], int(row["index"])): row for row in doc["hawks"]}

    crows = keyed(control)
    trows = keyed(treatment)
    if crows.keys() != trows.keys():
        raise SystemExit("hawk A/B populations differ")

    paired = []
    for key in sorted(crows):
        c = crows[key]
        t = trows[key]
        paired.append({
            "region": key[0],
            "index": key[1],
            "control_min_y": c["min_y"],
            "treatment_min_y": t["min_y"],
            "min_y_delta": t["min_y"] - c["min_y"],
            "control_final_y": c["final_y"],
            "treatment_final_y": t["final_y"],
            "final_y_delta": t["final_y"] - c["final_y"],
            "control_edge_departed": c["edge_departed"],
            "treatment_edge_departed": t["edge_departed"],
            "control_below_island_envelope": c["below_island_envelope"],
            "treatment_below_island_envelope": t["below_island_envelope"],
            "control_near_terrain_after_departure": c["near_terrain_after_departure"],
            "treatment_near_terrain_after_departure": t["near_terrain_after_departure"],
            "treatment_c6_adapted": t["c6_adapted"],
            "treatment_c6_transitions": t["c6_transitions"],
            "treatment_c6_steering_commands": t["c6_steering_commands"],
        })

    treatment_transitions = sum(row["c6_transitions"] for row in treatment["hawks"])
    treatment_steering = sum(row["c6_steering_commands"] for row in treatment["hawks"])
    treatment_adapted = sum(1 for row in treatment["hawks"] if row["c6_adapted"])
    qualitative_matches = sum(
        1
        for row in paired
        if row["control_edge_departed"] == row["treatment_edge_departed"]
        and row["control_below_island_envelope"] == row["treatment_below_island_envelope"]
        and row["control_near_terrain_after_departure"] == row["treatment_near_terrain_after_departure"]
    )
    result = {
        "schema_version": 1,
        "artifact_kind": "SKYFORGE_SF_IMP_1180_HAWK_BEHAVIOR_AB",
        "hawk_count_per_arm": len(control["hawks"]),
        "treatment_adapted_hawks": treatment_adapted,
        "treatment_c6_transitions": treatment_transitions,
        "treatment_c6_steering_commands": treatment_steering,
        "c6_navigation_inactive": treatment_transitions == 0 and treatment_steering == 0,
        "qualitative_outcome_matches": qualitative_matches,
        "qualitative_outcome_total": len(paired),
        "paired": paired,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)

    g = sub.add_parser("glider")
    g.add_argument("probe", type=Path)
    g.add_argument("coupling_source", type=Path)
    g.add_argument("output", type=Path)
    g.set_defaults(func=analyze_glider)

    h = sub.add_parser("hawks")
    h.add_argument("control", type=Path)
    h.add_argument("treatment", type=Path)
    h.add_argument("output", type=Path)
    h.set_defaults(func=analyze_hawks)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
