#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from compiler import compile_texture
from model import TextureSpecError
from render import emit_outputs
from targets.explicit_atlas import problem_from_spec as explicit_problem
from targets.minecraft_skin import problem_from_spec as minecraft_skin_problem


def main() -> int:
    parser = argparse.ArgumentParser(description="Compile a constrained texture specification")
    parser.add_argument("spec", type=Path)
    parser.add_argument("--out", type=Path, default=Path("build/texture-compiler"))
    args = parser.parse_args()
    try:
        spec = json.loads(args.spec.read_text(encoding="utf-8"))
        kind = spec.get("target", {}).get("kind")
        if kind == "minecraft_skin_classic":
            problem = minecraft_skin_problem(spec)
        elif kind == "explicit_atlas":
            problem = explicit_problem(spec)
        else:
            raise TextureSpecError(f"unsupported texture target kind: {kind}")
        compiled = compile_texture(problem)
        emit_outputs(compiled, args.out)
    except (OSError, json.JSONDecodeError, TextureSpecError, ValueError) as exc:
        raise SystemExit(f"texture compiler error: {exc}") from exc
    print(json.dumps({"assetId": compiled.summary["assetId"], "compilerVersion": compiled.summary["compilerVersion"], "atlas": compiled.summary["atlas"], "paletteSize": compiled.summary["paletteSize"], "meanWeightedDeltaE00": compiled.summary["meanWeightedDeltaE00"], "optimizer": compiled.summary["optimizer"], "validation": compiled.summary["validation"], "digestSha256": compiled.summary["digestSha256"], "output": str(args.out), "texture": str(args.out / "texture.png")}, indent=2))
    return 0 if compiled.summary["validation"]["passed"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
