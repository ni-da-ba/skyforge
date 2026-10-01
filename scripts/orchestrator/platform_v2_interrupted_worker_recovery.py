#!/usr/bin/env python3
"""Operator CLI for exact clean interrupted hosted-worker recovery."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import sys

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from v2.interrupted_worker_recovery import (  # noqa: E402
    inspect_clean_interrupted_worker,
    recover_clean_interrupted_worker,
)


V2_SERVICE = "skyforge-orchestrator-v2.service"


def _service_active(name: str) -> bool:
    completed = subprocess.run(
        ["systemctl", "is-active", "--quiet", name],
        check=False,
    )
    return completed.returncode == 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("plan", "recover"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--attempt-id", required=True)
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()

    root = args.root.resolve()
    inspection = inspect_clean_interrupted_worker(
        root=root,
        attempt_id=args.attempt_id,
    )
    payload = {
        "action": args.action,
        "attempt_id": args.attempt_id,
        "ready": inspection.ready,
        "blockers": list(inspection.blockers),
        "record": inspection.record.as_dict() if inspection.record else None,
        "v2_active": _service_active(V2_SERVICE),
    }

    if args.action == "plan" or not args.execute:
        print(json.dumps(payload, indent=2, sort_keys=True))
        return 0 if inspection.ready else 2

    if payload["v2_active"]:
        payload["blockers"].append(
            "Platform-v2 service must be inactive during interrupted-worker recovery"
        )
        payload["ready"] = False
        print(json.dumps(payload, indent=2, sort_keys=True))
        return 2
    if not inspection.ready:
        print(json.dumps(payload, indent=2, sort_keys=True))
        return 2

    recovered = recover_clean_interrupted_worker(
        root=root,
        attempt_id=args.attempt_id,
    )
    payload["recovered"] = recovered.as_dict()
    payload["ready"] = True
    print(json.dumps(payload, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
