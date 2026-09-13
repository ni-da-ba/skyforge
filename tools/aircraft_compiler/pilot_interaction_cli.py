#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from pilot_interaction import PilotInteractionError, lower_pilot_interaction


def main() -> int:
    parser = argparse.ArgumentParser(description="Lower AIRCRAFT-001 v0.19 pilot/client interaction qualification contract")
    parser.add_argument("pilot_station", type=Path)
    parser.add_argument("cockpit_yaw_route", type=Path)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        pilot_station = json.loads(args.pilot_station.read_text(encoding="utf-8"))
        cockpit_route = json.loads(args.cockpit_yaw_route.read_text(encoding="utf-8"))
        profile = json.loads(args.profile.read_text(encoding="utf-8"))
        result = lower_pilot_interaction(pilot_station, cockpit_route, profile)
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "pilot_interaction_v0.19.json").write_text(
            json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
    except (OSError, json.JSONDecodeError, PilotInteractionError, ValueError) as exc:
        raise SystemExit(f"pilot interaction lowering error: {exc}") from exc
    print(json.dumps({
        "assetId": result["assetId"],
        "digestSha256": result["digestSha256"],
        "pilotSeatCoordinate": result["pilotSeatCoordinate"],
        "steeringWheelCoordinate": result["steeringWheelCoordinate"],
        "sourceContract": result["sourceContract"],
        "readiness": result["readiness"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
