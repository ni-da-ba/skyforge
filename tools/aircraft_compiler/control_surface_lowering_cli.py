#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from control_surface_lowering import YawControlLoweringError, lower_yaw_control


def main() -> int:
    parser = argparse.ArgumentParser(description="Lower AIRCRAFT-001 v0.13 one-axis Swivel-Bearing rudder topology")
    parser.add_argument("manifest", type=Path)
    parser.add_argument("glue", type=Path)
    parser.add_argument("powertrain", type=Path)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        glue = json.loads(args.glue.read_text(encoding="utf-8"))
        powertrain = json.loads(args.powertrain.read_text(encoding="utf-8"))
        profile = json.loads(args.profile.read_text(encoding="utf-8"))
        result = lower_yaw_control(manifest, glue, powertrain, profile)
        if not result["validation"]["passed"]:
            raise YawControlLoweringError("yaw-control lowering validation failed")
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "yaw_control_v0.13.json").write_text(
            json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
    except (OSError, json.JSONDecodeError, YawControlLoweringError, ValueError) as exc:
        raise SystemExit(f"yaw-control lowering error: {exc}") from exc
    print(json.dumps({
        "assetId": result["assetId"],
        "compilerVersion": result["compilerVersion"],
        "digestSha256": result["digestSha256"],
        "metrics": result["metrics"],
        "readiness": result["readiness"],
        "validation": result["validation"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
