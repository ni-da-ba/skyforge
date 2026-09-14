#!/usr/bin/env python3
"""Compile the current Guild specimen through the bounded Wild Blue Yonder Minecraft profile."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from guild_branch_first_principles_detail import compile_guild_branch_first_principles_detail
from guild_realization_intent import project_guild_v014_realization_ir
from minecraft_structure import structure_filename
from minecraft_wby_structure import write_wby_structure_nbt
from model import SpecError
from render import emit_outputs


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Compile one Guild v0.14 specimen with the opt-in WBY C1 Minecraft realization profile"
    )
    parser.add_argument("spec", type=Path)
    parser.add_argument("--out", type=Path, default=Path("build/asset-compiler-wby"))
    args = parser.parse_args()

    try:
        spec = json.loads(args.spec.read_text(encoding="utf-8"))
        if spec.get("schemaVersion") != "0.14":
            raise SpecError(
                "WBY structure compilation is currently bounded to schemaVersion 0.14; "
                f"got {spec.get('schemaVersion')!r}"
            )
        compiled = compile_guild_branch_first_principles_detail(spec)
        emit_outputs(compiled, args.out)

        intent_model = project_guild_v014_realization_ir(compiled)
        intent_path = args.out / "realization_intent.json"
        intent_path.write_text(
            json.dumps(intent_model.to_dict(), indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        summary = compiled.summary
        structure_path = args.out / structure_filename(summary["assetId"])
        adapter_report = write_wby_structure_nbt(structure_path, compiled)
        adapter_path = args.out / "minecraft_wby_adapter.json"
        adapter_path.write_text(
            json.dumps(adapter_report, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
    except (OSError, json.JSONDecodeError, SpecError) as exc:
        raise SystemExit(f"WBY asset compiler error: {exc}") from exc

    print(
        json.dumps(
            {
                "assetId": summary["assetId"],
                "compilerVersion": summary["compilerVersion"],
                "minecraftProfile": "wby-c1-create",
                "blockCount": summary["blockCount"],
                "digestSha256": summary["digestSha256"],
                "output": str(args.out),
                "realizationIntent": str(intent_path),
                "minecraftStructure": str(structure_path),
                "minecraftAdapter": adapter_report,
            },
            indent=2,
        )
    )
    return 0 if summary["validation"]["passed"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
