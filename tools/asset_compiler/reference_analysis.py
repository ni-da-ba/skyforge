from __future__ import annotations

import argparse
import json
import statistics
from pathlib import Path
from typing import Any

from articulation import ratio
from model import SpecError


METRIC_RULES = {
    "bayWidthToWallHeight": ("bayWidth", "wallHeight"),
    "mainDepthToHallWidth": ("mainDepth", "hallWidth"),
    "workingWingWidthToHallWidth": ("workingWingWidth", "hallWidth"),
    "mainRoofRiseToDepth": ("mainRoofRise", "mainDepth"),
    "roofOverhangToDepth": ("roofOverhang", "mainDepth"),
    "publicCanopyDepthToWallHeight": ("publicCanopyDepth", "wallHeight"),
    "workingCanopyDepthToWallHeight": ("workingCanopyDepth", "wallHeight"),
    "publicEntranceWidthToBayWidth": ("publicEntranceWidth", "bayWidth"),
    "averagePublicWindowWidthToBayWidth": ("averagePublicWindowWidth", "bayWidth"),
}


def ratios_for_measurement(record: dict[str, Any]) -> dict[str, float]:
    measurements = record.get("measurements")
    if not isinstance(measurements, dict):
        raise SpecError("reference measurement requires measurements object")
    out: dict[str, float] = {}
    for metric, (numerator_key, denominator_key) in METRIC_RULES.items():
        if numerator_key not in measurements or denominator_key not in measurements:
            raise SpecError(f"reference measurement missing {numerator_key} or {denominator_key}")
        out[metric] = ratio(measurements[numerator_key], measurements[denominator_key], metric)
    return out


def aggregate_reference_profile(records: list[dict[str, Any]], profile_id: str) -> dict[str, Any]:
    if not records:
        raise SpecError("cannot aggregate empty reference-measurement set")
    ratio_sets = [ratios_for_measurement(record) for record in records]
    ratios: dict[str, Any] = {}
    for metric in sorted(METRIC_RULES):
        values = sorted(item[metric] for item in ratio_sets)
        ratios[metric] = {
            "min": round(values[0], 6),
            "target": round(statistics.median(values), 6),
            "max": round(values[-1], 6),
        }
    return {
        "profileId": profile_id,
        "status": "measured_reference",
        "provenance": {
            "externalMasterBuilderMeasurement": True,
            "sampleCount": len(records),
            "sources": [record.get("source", record.get("id", "unknown")) for record in records],
            "note": "Bounds are direct min/median/max summaries of explicitly measured reference builds; artistic authority still requires human review.",
        },
        "ratios": ratios,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Aggregate measured Minecraft architecture references into a Skyforge articulation profile")
    parser.add_argument("measurements", type=Path, help="JSON array of explicitly measured donor/reference builds")
    parser.add_argument("--profile-id", required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        records = json.loads(args.measurements.read_text(encoding="utf-8"))
        if not isinstance(records, list):
            raise SpecError("reference measurement file must contain a JSON array")
        profile = aggregate_reference_profile(records, args.profile_id)
    except (OSError, json.JSONDecodeError, SpecError) as exc:
        raise SystemExit(f"reference analysis error: {exc}") from exc
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(profile, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"profileId": args.profile_id, "sampleCount": len(records), "output": str(args.out)}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
