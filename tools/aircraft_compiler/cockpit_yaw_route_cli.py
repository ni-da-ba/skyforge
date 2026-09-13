#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from cockpit_yaw_route import CockpitYawRouteError, lower_cockpit_yaw_route


def main() -> int:
    parser = argparse.ArgumentParser(description="Lower AIRCRAFT-001 v0.18 production cockpit-to-rudder Create kinetic route")
    parser.add_argument("manifest", type=Path)
    parser.add_argument("powertrain", type=Path)
    parser.add_argument("yaw_control", type=Path)
    parser.add_argument("steering_control", type=Path)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        powertrain = json.loads(args.powertrain.read_text(encoding="utf-8"))
        yaw = json.loads(args.yaw_control.read_text(encoding="utf-8"))
        steering = json.loads(args.steering_control.read_text(encoding="utf-8"))
        profile = json.loads(args.profile.read_text(encoding="utf-8"))
        result = lower_cockpit_yaw_route(manifest, powertrain, yaw, steering, profile)
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "cockpit_yaw_route_v0.18.json").write_text(
            json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
    except (OSError, json.JSONDecodeError, CockpitYawRouteError, ValueError) as exc:
        raise SystemExit(f"cockpit yaw route lowering error: {exc}") from exc
    print(json.dumps({
        "assetId": result["assetId"],
        "digestSha256": result["digestSha256"],
        "selectedCandidate": result["selectedCandidate"],
        "routePath": result["routePath"],
        "metrics": result["metrics"],
        "signContract": result["signContract"],
        "readiness": result["readiness"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
