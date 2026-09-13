#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from model import SpecError
from render import emit_outputs
from solver import solve


def main() -> int:
    parser = argparse.ArgumentParser(description="Compile the bounded AIRCRAFT-001 analytical specimen")
    parser.add_argument("spec", type=Path)
    parser.add_argument("--out", type=Path, default=Path("build/aircraft-compiler"))
    args = parser.parse_args()
    try:
        spec = json.loads(args.spec.read_text(encoding="utf-8"))
        resolved = solve(spec)
        emit_outputs(resolved, args.out)
    except (OSError, json.JSONDecodeError, SpecError, ValueError) as exc:
        raise SystemExit(f"aircraft compiler error: {exc}") from exc
    print(json.dumps({
        "assetId": resolved["assetId"],
        "compilerVersion": resolved["compilerVersion"],
        "digestSha256": resolved["digestSha256"],
        "validation": resolved["validation"],
        "output": str(args.out),
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
