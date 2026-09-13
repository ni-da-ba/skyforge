#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from steering_control import SteeringControlLoweringError, lower_steering_control


def main() -> int:
    parser = argparse.ArgumentParser(description="Lower AIRCRAFT-001 v0.17 real Steering Wheel source-substitution probe")
    parser.add_argument("yaw_actuation", type=Path)
    parser.add_argument("yaw_neutral_return", type=Path)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        actuation = json.loads(args.yaw_actuation.read_text(encoding="utf-8"))
        neutral = json.loads(args.yaw_neutral_return.read_text(encoding="utf-8"))
        profile = json.loads(args.profile.read_text(encoding="utf-8"))
        result = lower_steering_control(actuation, neutral, profile)
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "steering_control_v0.17.json").write_text(
            json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
    except (OSError, json.JSONDecodeError, SteeringControlLoweringError, ValueError) as exc:
        raise SystemExit(f"steering-control lowering error: {exc}") from exc
    print(json.dumps({
        "assetId": result["assetId"],
        "digestSha256": result["digestSha256"],
        "steeringWheelCoordinate": result["steeringWheelCoordinate"],
        "runtimeProbe": result["runtimeProbe"],
        "readiness": result["readiness"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
