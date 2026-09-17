"""Read-only CLI for one Platform v2 live-shadow parity sample."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any

from v2.live_parity import analyze_live_parity


def _read_json(path: str, label: str) -> Any:
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise ValueError(f"{label} file is missing: {path}") from exc
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"{label} file is unreadable: {path}") from exc


def render_sample(
    *,
    state: Any,
    snapshot: Any,
    report: Any,
    current_main: str,
) -> str:
    if not isinstance(state, dict):
        raise ValueError("legacy state must be an object")
    sample = analyze_live_parity(
        legacy_pending_decision=state.get("pending_decision"),
        current_main=current_main,
        snapshot=snapshot,
        report=report,
    )
    payload = {"schema_version": 1, "sample": sample.as_dict(), "digest": sample.digest}
    return json.dumps(payload, sort_keys=True, separators=(",", ":"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Compare one legacy pending decision with one v2 shadow transition.")
    parser.add_argument("--state", required=True, help="Legacy controller state JSON.")
    parser.add_argument("--snapshot", required=True, help="Prepared R3B snapshot JSON.")
    parser.add_argument("--report", required=True, help="R3A shadow report JSON.")
    parser.add_argument("--current-main", required=True, help="Exact current accepted main SHA.")
    args = parser.parse_args(argv)
    try:
        output = render_sample(
            state=_read_json(args.state, "legacy state"),
            snapshot=_read_json(args.snapshot, "shadow snapshot"),
            report=_read_json(args.report, "shadow report"),
            current_main=args.current_main,
        )
    except ValueError as exc:
        print(f"shadow parity rejected: {exc}", file=sys.stderr)
        return 2
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
