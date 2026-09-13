#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from assembly import AssemblyPlanError, plan_assembly
from assembly_render import emit_assembly_outputs
from blockspace import BlockspaceError, transcribe
from blockspace_render import emit_blockspace_outputs
from model import SpecError
from render import emit_outputs
from solver import solve


def main() -> int:
    parser = argparse.ArgumentParser(description="Compile the bounded AIRCRAFT-001 analytical specimen")
    parser.add_argument("spec", type=Path)
    parser.add_argument("--blockspace-config", type=Path)
    parser.add_argument("--out", type=Path, default=Path("build/aircraft-compiler"))
    args = parser.parse_args()
    blockspace = None
    assembly = None
    try:
        spec = json.loads(args.spec.read_text(encoding="utf-8"))
        resolved = solve(spec)
        emit_outputs(resolved, args.out)
        if args.blockspace_config is not None:
            config = json.loads(args.blockspace_config.read_text(encoding="utf-8"))
            blockspace = transcribe(resolved, config)
            if not blockspace["validation"]["passed"]:
                raise BlockspaceError(
                    "block-space transcription failed validation: "
                    + json.dumps(blockspace["validation"], sort_keys=True)
                )
            emit_blockspace_outputs(blockspace, resolved, args.out)
            assembly = plan_assembly(blockspace)
            if not assembly["validation"]["passed"]:
                raise AssemblyPlanError(
                    "assembly planning failed validation: "
                    + json.dumps(assembly["validation"], sort_keys=True)
                )
            emit_assembly_outputs(assembly, args.out)
    except (
        OSError,
        json.JSONDecodeError,
        SpecError,
        BlockspaceError,
        AssemblyPlanError,
        ValueError,
    ) as exc:
        raise SystemExit(f"aircraft compiler error: {exc}") from exc

    summary = {
        "assetId": resolved["assetId"],
        "compilerVersion": resolved["compilerVersion"],
        "digestSha256": resolved["digestSha256"],
        "validation": resolved["validation"],
        "output": str(args.out),
    }
    if blockspace is not None:
        summary["blockspace"] = {
            "assetId": blockspace["assetId"],
            "compilerVersion": blockspace["compilerVersion"],
            "digestSha256": blockspace["digestSha256"],
            "validation": blockspace["validation"],
        }
    if assembly is not None:
        summary["assembly"] = {
            "assetId": assembly["assetId"],
            "compilerVersion": assembly["compilerVersion"],
            "digestSha256": assembly["digestSha256"],
            "metrics": assembly["metrics"],
            "validation": assembly["validation"],
        }
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
