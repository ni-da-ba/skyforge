#!/usr/bin/env python3
"""Root/operator CLI for the R5C26 Platform-v2 production writer handoff."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from v2.operator_cutover import OperatorCutoverController, OperatorDisposition


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("preflight", "cutover", "rollback", "transfer-authority"))
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--activation-template", type=Path)
    parser.add_argument("--event-key")
    parser.add_argument("--issue-number", type=int)
    parser.add_argument("--source-id")
    parser.add_argument(
        "--signal-kind",
        default="task",
        help="Protected authority signal kind to transfer. Defaults to task for backward compatibility.",
    )
    parser.add_argument(
        "--activation-evidence",
        type=Path,
        default=Path("/var/lib/skyforge-orchestrator/platform-v2-activation.json"),
    )
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Perform the privileged service transition. Without this flag all commands are read-only.",
    )
    args = parser.parse_args(argv)
    if args.command == "preflight" and args.execute:
        parser.error("preflight is always read-only")
    if args.command in {"preflight", "cutover"} and args.activation_template is None:
        parser.error("--activation-template is required for preflight/cutover")
    if args.command == "transfer-authority":
        if not args.event_key or args.issue_number is None or not args.source_id:
            parser.error("transfer-authority requires --event-key, --issue-number, and --source-id")
    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    activation_template = args.activation_template or (
        args.root / ".skyforge-platform-v2" / "operator-evidence" / "final-activation-template.json"
    )
    controller = OperatorCutoverController(
        root=args.root,
        activation_template=activation_template,
        activation_evidence=args.activation_evidence,
    )
    if args.command == "rollback":
        report = controller.rollback(execute=args.execute)
    elif args.command == "transfer-authority":
        report = controller.transfer_authority(
            event_key=args.event_key,
            issue_number=args.issue_number,
            source_id=args.source_id,
            signal_kind=args.signal_kind,
            execute=args.execute,
        )
    else:
        report = controller.cutover(execute=args.command == "cutover" and args.execute)
    print(json.dumps(report.as_dict(), sort_keys=True, indent=2))
    return 0 if report.disposition in {
        OperatorDisposition.PREFLIGHT_READY,
        OperatorDisposition.CUTOVER_COMPLETE,
        OperatorDisposition.ROLLBACK_COMPLETE,
        OperatorDisposition.AUTHORITY_TRANSFER_READY,
        OperatorDisposition.AUTHORITY_TRANSFER_COMPLETE,
    } else 1


if __name__ == "__main__":
    raise SystemExit(main())
