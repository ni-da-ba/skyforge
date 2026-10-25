#!/usr/bin/env python3
"""Route Windy's ribbon through Minecraft's opaque entity cutout pipeline."""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply-0013.py WINDY_SOURCE_ROOT/src/.../WindRibbons.java")

path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")
old = "RenderType.entityTranslucent(RIBBON_TEX)"
new = "RenderType.entityCutoutNoCull(RIBBON_TEX)"
count = source.count(old)
if count != 1:
    raise SystemExit(f"Expected exactly one built-in translucent ribbon type; found {count}")

source = source.replace(old, new, 1)
path.write_text(source, encoding="utf-8")
print("Applied asserted Windy entity-cutout rendering experiment.")
