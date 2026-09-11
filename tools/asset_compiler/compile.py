#!/usr/bin/env python3
"""CLI for the bounded Skyforge asset-compiler proof (issue #488)."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from guild_branch import compile_guild_branch
from guild_branch_detail import compile_guild_branch_v03
from model import SpecError
from render import emit_outputs


def main() -> int:
    parser = argparse.ArgumentParser(description="Compile one bounded Skyforge asset specimen")
    parser.add_argument("spec", type=Path)
    parser.add_argument("--out", type=Path, default=Path("build/asset-compiler"))
    args = parser.parse_args()
    try:
        spec = json.loads(args.spec.read_text(encoding="utf-8"))
        if spec.get("schemaVersion") == "0.3":
            compiled = compile_guild_branch_v03(spec)
        else:
            compiled = compile_guild_branch(spec)
    except (OSError, json.JSONDecodeError, SpecError) as exc:
        raise SystemExit(f"asset compiler error: {exc}") from exc
    emit_outputs(compiled, args.out)
    s = compiled.summary
    print(json.dumps({
        "assetId": s["assetId"],
        "compilerVersion": s["compilerVersion"],
        "blockCount": s["blockCount"],
        "bounds": s["layout"]["bounds"]["size"],
        "validation": s["validation"],
        "digestSha256": s["digestSha256"],
        "output": str(args.out),
    }, indent=2))
    return 0 if s["validation"]["passed"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
