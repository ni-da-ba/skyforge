#!/usr/bin/env python3
"""Root/operator CLI for the R5C26 Platform-v2 production writer handoff."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from v2.operator_cutover import OperatorCutoverController, OperatorDisposition


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("preflight", "cutover", "rollback"))
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--activation-template", type=Path, required=True)
    parser.add_argument(
        "--activation-evidence",
        type=Path,
        default=Path("/etc/skyforge-orchestrator/platform-v2-activation.json"),
    )
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Perform the privileged service transition. Without this flag all commands are read-only.",
    )
    args = parser.parse_args(argv)
    if args.command == "preflight" and args.execute:
        parser.error("preflight is always read-only")
    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    controller = OperatorCutoverController(
        root=args.root,
        activation_template=args.activation_template,
        activation_evidence=args.activation_evidence,
    )
    if args.command == "rollback":
        report = controller.rollback(execute=args.execute)
    else:
        report = controller.cutover(execute=args.command == "cutover" and args.execute)
    print(json.dumps(report.as_dict(), sort_keys=True, indent=2))
    return 0 if report.disposition in {
        OperatorDisposition.PREFLIGHT_READY,
        OperatorDisposition.CUTOVER_COMPLETE,
        OperatorDisposition.ROLLBACK_COMPLETE,
    } else 1


if __name__ == "__main__":
    raise SystemExit(main())
