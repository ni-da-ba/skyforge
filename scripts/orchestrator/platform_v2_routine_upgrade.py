#!/usr/bin/env python3
"""Operator CLI for routine accepted-main Platform-v2 upgrades."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess

from v2.operator_cutover import OperatorCutoverController
from v2.routine_upgrade import (
    RoutineUpgradeController,
    RoutineUpgradeDisposition,
)


def _resolve_target(root: Path, supplied: str | None) -> str:
    if supplied:
        return supplied.strip()
    result = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "origin/main"],
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout.strip()


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Plan or execute one fail-closed routine Platform-v2 upgrade to accepted origin/main."
        )
    )
    parser.add_argument("command", choices=("plan", "upgrade"))
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--target-sha")
    parser.add_argument("--activation-template", type=Path)
    parser.add_argument(
        "--activation-evidence",
        type=Path,
        default=Path("/var/lib/skyforge-orchestrator/platform-v2-activation.json"),
    )
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Perform the root-only writer transition. Without this flag upgrade is read-only.",
    )
    args = parser.parse_args(argv)
    if args.command == "plan" and args.execute:
        parser.error("plan is always read-only")
    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    root = args.root.resolve()
    template = (
        args.activation_template.resolve()
        if args.activation_template is not None
        else root
        / ".skyforge-platform-v2"
        / "operator-evidence"
        / "final-activation-template.json"
    )
    target = _resolve_target(root, args.target_sha)
    operator = OperatorCutoverController(
        root=root,
        activation_template=template,
        activation_evidence=args.activation_evidence,
    )
    controller = RoutineUpgradeController(
        root=root,
        activation_template=template,
        activation_evidence=args.activation_evidence,
        operator=operator,
    )
    report = controller.upgrade(
        target,
        execute=args.command == "upgrade" and args.execute,
    )
    print(json.dumps(report.as_dict(), sort_keys=True, indent=2))
    return 0 if report.disposition in {
        RoutineUpgradeDisposition.READY,
        RoutineUpgradeDisposition.COMPLETE,
    } else 1


if __name__ == "__main__":
    raise SystemExit(main())
