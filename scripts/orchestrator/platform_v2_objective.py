#!/usr/bin/env python3
"""Read-only natural-objective compiler for Platform-v2 OPT-1."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from v2.objective_intake import compile_objective


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("objective", help="Plain-language Skyforge objective, e.g. 'Continue DR-70'")
    parser.add_argument("--root", type=Path, default=Path.cwd())
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    result = compile_objective(args.objective, root=args.root)
    print(json.dumps(result.as_dict(), sort_keys=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
