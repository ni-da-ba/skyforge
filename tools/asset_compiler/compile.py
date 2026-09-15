#!/usr/bin/env python3
"""CLI for the bounded Skyforge asset-compiler proof (issue #488)."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from guild_branch import compile_guild_branch
from guild_branch_detail import compile_guild_branch_v03
from guild_branch_interior import compile_guild_branch_v04
from guild_branch_runtime import compile_guild_branch_v05
from guild_branch_facade import compile_guild_branch_v06
from guild_branch_cohesion import compile_guild_branch_v07
from guild_branch_articulation import compile_guild_branch_v08
from guild_branch_detail_resolution import compile_guild_branch_v09
from guild_branch_finish import compile_guild_branch_v10
from guild_branch_continuity import compile_guild_branch_v11
from guild_branch_math_optimization import compile_guild_branch_v12
from guild_branch_first_principles_final import compile_guild_branch_first_principles_final
from guild_branch_first_principles_detail import compile_guild_branch_first_principles_detail
from minecraft_structure import structure_filename, write_structure_nbt
from model import SpecError
from render import emit_outputs


def main() -> int:
    parser = argparse.ArgumentParser(description="Compile one bounded Skyforge asset specimen")
    parser.add_argument("spec", type=Path)
    parser.add_argument("--out", type=Path, default=Path("build/asset-compiler"))
    parser.add_argument(
        "--minecraft-structure",
        action="store_true",
        help="also emit one vanilla Minecraft 1.21.1 structure-template .nbt file",
    )
    args = parser.parse_args()
    try:
        spec = json.loads(args.spec.read_text(encoding="utf-8"))
        if spec.get("schemaVersion") == "0.14":
            compiled = compile_guild_branch_first_principles_detail(spec)
        elif spec.get("schemaVersion") == "0.13":
            compiled = compile_guild_branch_first_principles_final(spec)
        elif spec.get("schemaVersion") == "0.12":
            compiled = compile_guild_branch_v12(spec)
        elif spec.get("schemaVersion") == "0.11":
            compiled = compile_guild_branch_v11(spec)
        elif spec.get("schemaVersion") == "0.10":
            compiled = compile_guild_branch_v10(spec)
        elif spec.get("schemaVersion") == "0.9":
            compiled = compile_guild_branch_v09(spec)
        elif spec.get("schemaVersion") == "0.8":
            compiled = compile_guild_branch_v08(spec)
        elif spec.get("schemaVersion") == "0.7":
            compiled = compile_guild_branch_v07(spec)
        elif spec.get("schemaVersion") == "0.6":
            compiled = compile_guild_branch_v06(spec)
        elif spec.get("schemaVersion") == "0.5":
            compiled = compile_guild_branch_v05(spec)
        elif spec.get("schemaVersion") == "0.4":
            compiled = compile_guild_branch_v04(spec)
        elif spec.get("schemaVersion") == "0.3":
            compiled = compile_guild_branch_v03(spec)
        else:
            compiled = compile_guild_branch(spec)
    except (OSError, json.JSONDecodeError, SpecError) as exc:
        raise SystemExit(f"asset compiler error: {exc}") from exc

    emit_outputs(compiled, args.out)
    s = compiled.summary
    realization_intent_path = None
    if str(s.get("compilerVersion")) == "0.14-first-principles-detail":
        try:
            from guild_realization_intent import project_guild_v014_realization_ir

            intent_model = project_guild_v014_realization_ir(compiled)
            realization_intent_path = args.out / "realization_intent.json"
            realization_intent_path.write_text(
                json.dumps(intent_model.to_dict(), indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
        except (OSError, SpecError) as exc:
            raise SystemExit(f"asset compiler realization-intent export error: {exc}") from exc

    minecraft_path = None
    minecraft_adapter_report = None
    if args.minecraft_structure:
        try:
            minecraft_path = args.out / structure_filename(s["assetId"])
            # v0.14 is the first architecture path required to cross the explicit Minecraft
            # realization adapter. Historical specimens remain exportable as regression evidence.
            use_adapter = str(s.get("compilerVersion")) == "0.14-first-principles-detail"
            minecraft_adapter_report = write_structure_nbt(
                minecraft_path,
                compiled,
                use_adapter=use_adapter,
            )
            if minecraft_adapter_report is not None:
                (args.out / "minecraft_adapter.json").write_text(
                    json.dumps(minecraft_adapter_report, indent=2, sort_keys=True) + "\n",
                    encoding="utf-8",
                )
        except (OSError, SpecError) as exc:
            raise SystemExit(f"asset compiler Minecraft export error: {exc}") from exc

    print(json.dumps({
        "assetId": s["assetId"],
        "compilerVersion": s["compilerVersion"],
        "blockCount": s["blockCount"],
        "bounds": s["layout"]["bounds"]["size"],
        "validation": s["validation"],
        "digestSha256": s["digestSha256"],
        "output": str(args.out),
        "realizationIntent": str(realization_intent_path) if realization_intent_path else None,
        "minecraftStructure": str(minecraft_path) if minecraft_path else None,
        "minecraftAdapter": minecraft_adapter_report,
    }, indent=2))
    return 0 if s["validation"]["passed"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
