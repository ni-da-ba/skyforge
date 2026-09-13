#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from glue_encoding import GlueEncodingError, encode_glue_application
from glue_encoding_render import emit_glue_encoding_outputs


def main() -> int:
    parser = argparse.ArgumentParser(description="Encode AIRCRAFT-001 v0.11 edge-exact Create Super Glue fixture")
    parser.add_argument("fixture", type=Path)
    parser.add_argument("profile", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        fixture = json.loads(args.fixture.read_text(encoding="utf-8"))
        profile = json.loads(args.profile.read_text(encoding="utf-8"))
        result = encode_glue_application(fixture, profile)
        if not result["validation"]["passed"]:
            raise GlueEncodingError("glue encoding validation failed: " + json.dumps(result["checks"], sort_keys=True))
        emit_glue_encoding_outputs(result, args.out)
    except (OSError, json.JSONDecodeError, GlueEncodingError, ValueError) as exc:
        raise SystemExit(f"glue encoding error: {exc}") from exc
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
