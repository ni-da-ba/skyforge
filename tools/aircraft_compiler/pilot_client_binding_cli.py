#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from pilot_client_binding import PilotClientBindingError, lower_pilot_client_binding


def main() -> int:
    parser = argparse.ArgumentParser(description="Lower AIRCRAFT-001 v0.20 genuine client/player binding qualification contract")
    parser.add_argument("pilot_interaction", type=Path)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        pilot_interaction = json.loads(args.pilot_interaction.read_text(encoding="utf-8"))
        profile = json.loads(args.profile.read_text(encoding="utf-8"))
        result = lower_pilot_client_binding(pilot_interaction, profile)
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "pilot_client_binding_v0.20.json").write_text(
            json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
    except (OSError, json.JSONDecodeError, PilotClientBindingError, ValueError) as exc:
        raise SystemExit(f"pilot client binding lowering error: {exc}") from exc
    print(json.dumps({
        "assetId": result["assetId"],
        "digestSha256": result["digestSha256"],
        "pilotSeatCoordinate": result["pilotSeatCoordinate"],
        "steeringWheelCoordinate": result["steeringWheelCoordinate"],
        "clientEntryContract": result["clientEntryContract"],
        "commandProbe": result["commandProbe"],
        "readiness": result["readiness"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
