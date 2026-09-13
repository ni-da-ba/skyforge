#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from yaw_authority import YawAuthorityLoweringError, lower_yaw_authority


def main() -> int:
    parser = argparse.ArgumentParser(description="Lower AIRCRAFT-001 v0.15 exact-stack yaw-authority probe")
    parser.add_argument("yaw_actuation", type=Path)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        actuation = json.loads(args.yaw_actuation.read_text(encoding="utf-8"))
        profile = json.loads(args.profile.read_text(encoding="utf-8"))
        result = lower_yaw_authority(actuation, profile)
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "yaw_authority_v0.15.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    except (OSError, json.JSONDecodeError, YawAuthorityLoweringError, ValueError) as exc:
        raise SystemExit(f"yaw authority lowering error: {exc}") from exc
    print(json.dumps({"assetId": result["assetId"], "digestSha256": result["digestSha256"], "runtimeProbe": result["runtimeProbe"], "readiness": result["readiness"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
