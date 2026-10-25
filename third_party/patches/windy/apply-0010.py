#!/usr/bin/env python3
"""Apply the asserted Windy 1.2.0 entity-translucent rendering experiment."""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply-0010.py WINDY_SOURCE_ROOT/src/.../WindRibbons.java")

path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")
replacements = [
    (
        """    private static final RenderType RIBBON = RenderType.create(
            "windy:ribbon",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(RIBBON_TEX, true, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));""",
        "    private static final RenderType RIBBON = RenderType.entityTranslucent(RIBBON_TEX);",
        "custom ribbon render type",
    ),
    (
        "BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);",
        "BufferBuilder bb = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);",
        "ribbon vertex format",
    ),
    (
        "                .setUv(u, v).setColor(235, 245, 252, a);",
        """                .setUv(u, v).setColor(235, 245, 252, a)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);""",
        "entity vertex attributes",
    ),
]

for old, new, label in replacements:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one upstream {label} snippet; found {count}")
    source = source.replace(old, new, 1)

path.write_text(source, encoding="utf-8")
print("Applied asserted Windy entity-translucent rendering experiment.")
