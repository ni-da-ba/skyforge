#!/usr/bin/env python3
"""Route Windy's ribbon geometry through Minecraft's shared entity buffer batch."""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply-0012.py WINDY_SOURCE_ROOT/src/.../WindRibbons.java")

path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")
replacements = [
    (
        "BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);",
        """com.mojang.blaze3d.vertex.VertexConsumer bb =
                mc.renderBuffers().bufferSource().getBuffer(RIBBON);""",
        "shared entity buffer acquisition",
    ),
    (
        """        MeshData mesh = bb.build();
        if (mesh != null) {
            RIBBON.draw(mesh); // sets up + clears all render state around the draw
        }""",
        """        mc.renderBuffers().bufferSource().endBatch(RIBBON);""",
        "shared entity buffer flush",
    ),
    (
        "private static void vtx(BufferBuilder bb, Matrix4f pose, Vec3 cam, Vec3 p, float u, float v, int a)",
        "private static void vtx(com.mojang.blaze3d.vertex.VertexConsumer bb, Matrix4f pose, Vec3 cam, Vec3 p, float u, float v, int a)",
        "vertex consumer signature",
    ),
]
for old, new, label in replacements:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one {label} anchor; found {count}")
    source = source.replace(old, new, 1)
path.write_text(source, encoding="utf-8")
print("Applied asserted shared entity-buffer submission experiment.")
