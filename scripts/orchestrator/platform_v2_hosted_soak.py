#!/usr/bin/env python3
"""Run the R5C27 disposable hosted workflow soak and emit machine-readable evidence."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import tempfile

from v2.hosted_soak import run_disposable_hosted_soak, run_disposable_provider_failure


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)

    with tempfile.TemporaryDirectory(prefix="skyforge-r5c27-soak-") as td:
        success_root = Path(td) / "success"
        success_root.mkdir()
        failure_root = Path(td) / "provider-failure"
        failure_root.mkdir()
        report = run_disposable_hosted_soak(success_root)
        failure = run_disposable_provider_failure(failure_root)
        payload = {
            **report.as_dict(),
            "provider_failure": {
                **failure.as_dict(),
                "evidence_digest": failure.digest,
            },
        }
    encoded = json.dumps(payload, sort_keys=True, indent=2) + "\n"
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
