#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from assembly_fixture import AssemblyFixtureError, plan_assembly_fixture
from assembly_fixture_render import emit_assembly_fixture_outputs


def main() -> int:
    parser = argparse.ArgumentParser(description="Plan AIRCRAFT-001 v0.10 Physics Assembler fixture")
    parser.add_argument("manifest", type=Path)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        profile = json.loads(args.profile.read_text(encoding="utf-8"))
        result = plan_assembly_fixture(manifest, profile)
        if not result["validation"]["passed"]:
            raise AssemblyFixtureError("assembly fixture topology failed: " + json.dumps(result["topologyChecks"], sort_keys=True))
        emit_assembly_fixture_outputs(result, args.out)
    except (OSError, json.JSONDecodeError, AssemblyFixtureError, ValueError) as exc:
        raise SystemExit(f"assembly fixture error: {exc}") from exc
    print(json.dumps({
        "assetId": result["assetId"],
        "compilerVersion": result["compilerVersion"],
        "digestSha256": result["digestSha256"],
        "metrics": result["metrics"],
        "readiness": result["readiness"],
        "validation": result["validation"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
