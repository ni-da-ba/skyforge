#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from functional_mechanism import compile_functional_mechanism
from functional_mechanism_structure import encode_mechanism_structure_nbt
from model import SpecError


def main() -> int:
    parser = argparse.ArgumentParser(description="Compile the bounded MECH-001 functional mechanism")
    parser.add_argument("spec", type=Path)
    parser.add_argument(
        "--capability-ledger",
        type=Path,
        default=Path("docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json"),
    )
    parser.add_argument("--out", type=Path, default=Path("build/mech-001"))
    args = parser.parse_args()

    try:
        spec = json.loads(args.spec.read_text(encoding="utf-8"))
        ledger = json.loads(args.capability_ledger.read_text(encoding="utf-8"))
        plan = compile_functional_mechanism(spec, ledger)
    except (OSError, json.JSONDecodeError, SpecError) as exc:
        raise SystemExit(f"functional mechanism compiler error: {exc}") from exc

    args.out.mkdir(parents=True, exist_ok=True)
    resolved = args.out / "resolved.json"
    resolved.write_text(json.dumps(plan, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    structure = args.out / "mech_001_airflow_bench.nbt"
    structure.write_bytes(encode_mechanism_structure_nbt(plan))
    print(json.dumps({
        "assetId": plan["assetId"],
        "compilerVersion": plan["compilerVersion"],
        "placementCount": len(plan["placements"]),
        "digestSha256": plan["digestSha256"],
        "requiredPlatformCapability": plan["requiredPlatformCapability"],
        "output": str(resolved),
        "minecraftStructure": str(structure),
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
