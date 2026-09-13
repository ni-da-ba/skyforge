#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from yaw_actuation import YawActuationLoweringError, lower_yaw_actuation


def main() -> int:
    parser = argparse.ArgumentParser(description="Lower AIRCRAFT-001 v0.14 post-assembly Swivel extra-cog actuation probe")
    parser.add_argument("manifest", type=Path)
    parser.add_argument("powertrain", type=Path)
    parser.add_argument("yaw_control", type=Path)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        powertrain = json.loads(args.powertrain.read_text(encoding="utf-8"))
        yaw_control = json.loads(args.yaw_control.read_text(encoding="utf-8"))
        profile = json.loads(args.profile.read_text(encoding="utf-8"))
        result = lower_yaw_actuation(manifest, powertrain, yaw_control, profile)
        if not result["validation"]["passed"]:
            raise YawActuationLoweringError("yaw actuation lowering validation failed")
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "yaw_actuation_v0.14.json").write_text(
            json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
    except (OSError, json.JSONDecodeError, YawActuationLoweringError, ValueError) as exc:
        raise SystemExit(f"yaw actuation lowering error: {exc}") from exc
    print(json.dumps({
        "assetId": result["assetId"],
        "compilerVersion": result["compilerVersion"],
        "digestSha256": result["digestSha256"],
        "runtimeProbe": result["runtimeProbe"],
        "readiness": result["readiness"],
        "validation": result["validation"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
