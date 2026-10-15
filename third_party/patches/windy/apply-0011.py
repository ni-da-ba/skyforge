#!/usr/bin/env python3
"""Apply the asserted Windy entity-pass timing experiment."""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply-0011.py WINDY_SOURCE_ROOT/src/.../WindRibbons.java")

path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")
old = "if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;"
new = "if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;"
count = source.count(old)
if count != 1:
    raise SystemExit(f"Expected exactly one late ribbon render-stage guard; found {count}")

source = source.replace(old, new, 1)
path.write_text(source, encoding="utf-8")
print("Applied asserted Windy entity-pass render-stage experiment.")
