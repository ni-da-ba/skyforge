#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from yaw_neutral_return import YawNeutralReturnLoweringError, lower_yaw_neutral_return


def main() -> int:
    parser = argparse.ArgumentParser(description="Lower AIRCRAFT-001 v0.16 commanded neutral-return probe")
    parser.add_argument("yaw_actuation", type=Path)
    parser.add_argument("yaw_authority", type=Path)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        actuation = json.loads(args.yaw_actuation.read_text(encoding="utf-8"))
        authority = json.loads(args.yaw_authority.read_text(encoding="utf-8"))
        profile = json.loads(args.profile.read_text(encoding="utf-8"))
        result = lower_yaw_neutral_return(actuation, authority, profile)
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "yaw_neutral_return_v0.16.json").write_text(
            json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
    except (OSError, json.JSONDecodeError, YawNeutralReturnLoweringError, ValueError) as exc:
        raise SystemExit(f"yaw neutral-return lowering error: {exc}") from exc
    print(json.dumps({
        "assetId": result["assetId"],
        "digestSha256": result["digestSha256"],
        "runtimeProbe": result["runtimeProbe"],
        "readiness": result["readiness"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
