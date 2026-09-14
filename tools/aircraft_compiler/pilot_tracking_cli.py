#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from pilot_tracking import PilotTrackingError, lower_pilot_tracking


def main() -> int:
    parser = argparse.ArgumentParser(description="Lower AIRCRAFT-001 v0.21 natural Sable pilot tracking contract")
    parser.add_argument("pilot_client_binding", type=Path)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        source = json.loads(args.pilot_client_binding.read_text(encoding="utf-8"))
        profile = json.loads(args.profile.read_text(encoding="utf-8"))
        result = lower_pilot_tracking(source, profile)
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "pilot_tracking_v0.21.json").write_text(
            json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
    except (OSError, json.JSONDecodeError, PilotTrackingError, ValueError) as exc:
        raise SystemExit(f"pilot tracking lowering error: {exc}") from exc
    print(json.dumps({
        "assetId": result["assetId"],
        "digestSha256": result["digestSha256"],
        "trackingContract": result["trackingContract"],
        "motionProbe": result["motionProbe"],
        "readiness": result["readiness"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
