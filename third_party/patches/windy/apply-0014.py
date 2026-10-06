#!/usr/bin/env python3
"""Apply a diagnostic that isolates Windy's standard particle path from ribbons."""
from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit(
        "usage: apply-0014.py WINDY_SOURCE_ROOT/src/.../WindDirector.java "
        "WINDY_SOURCE_ROOT/src/.../WindRibbons.java"
    )

director_path, ribbons_path = map(Path, sys.argv[1:])
director = director_path.read_text(encoding="utf-8")
ribbons = ribbons_path.read_text(encoding="utf-8")
probe = 'Boolean.getBoolean("skyforge.windy.shaderParticleProbe")'

replacements = [
    (
        "if (cfg.wisps() && eff > 0.24f) {",
        f"if (cfg.wisps() && ({probe} ? eff > 0.10f : eff > 0.24f)) {{",
        "wisp threshold",
    ),
    (
        "int count = poisson(4.5 * densityMul * (eff - 0.20f), rng);",
        f"""int count = poisson(4.5 * densityMul * (eff - 0.20f), rng);
            if ({probe} && count == 0) count = 1;""",
        "minimum diagnostic wisp density",
    ),
]
for old, new, label in replacements:
    count = director.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} anchor; found {count}")
    director = director.replace(old, new, 1)

stage = "if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;"
count = ribbons.count(stage)
if count != 1:
    raise SystemExit(f"Expected exactly one entity-stage ribbon guard; found {count}")
ribbons = ribbons.replace(stage, stage + f"\n        if ({probe}) return;", 1)

director_path.write_text(director, encoding="utf-8")
ribbons_path.write_text(ribbons, encoding="utf-8")
print("Applied Windy particle-only shader-path diagnostic.")
