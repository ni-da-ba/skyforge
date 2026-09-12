from __future__ import annotations

import json
from pathlib import Path

from model import CompiledTexture, RGBA
from png import write_rgba_png


def _rgba_css(c: RGBA) -> str:
    return f"rgba({c[0]},{c[1]},{c[2]},{c[3]/255:.4f})"


def _pixel_grid_svg(rows: list[list[RGBA]], scale: int, x0: int, y0: int) -> list[str]:
    out: list[str] = []
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c[3] == 0:
                continue
            out.append(f'<rect x="{x0+x*scale}" y="{y0+y*scale}" width="{scale}" height="{scale}" fill="{_rgba_css(c)}"/>')
    return out


def _scale_rows(rows: list[list[RGBA]], scale: int) -> list[list[RGBA]]:
    out: list[list[RGBA]] = []
    for row in rows:
        expanded = [c for c in row for _ in range(scale)]
        for _ in range(scale):
            out.append(expanded[:])
    return out


def _skin_model_rows(compiled: CompiledTexture, face: str, scale: int = 8) -> list[list[RGBA]]:
    p = compiled.region_pixels
    mapping = {
        "front": {"head": "head.front", "torso": "torso.front", "ra": "right_arm.front", "la": "left_arm.front", "rl": "right_leg.front", "ll": "left_leg.front"},
        "back": {"head": "head.back", "torso": "torso.back", "ra": "right_arm.back", "la": "left_arm.back", "rl": "right_leg.back", "ll": "left_leg.back"},
        "side": {"head": "head.right", "torso": "torso.right", "ra": "right_arm.right", "la": "left_arm.right", "rl": "right_leg.right", "ll": "left_leg.right"},
    }[face]
    bg = (215, 215, 215, 255)
    canvas = [[bg for _ in range(20)] for _ in range(32)]
    placements = ({"head": (6, 0), "torso": (6, 8), "ra": (2, 8), "la": (14, 8), "rl": (6, 20), "ll": (10, 20)} if face in ("front", "back") else {"head": (8, 0), "torso": (8, 8), "ra": (4, 8), "la": (12, 8), "rl": (8, 20), "ll": (12, 20)})
    for part, rid in mapping.items():
        rows = p.get(rid)
        if rows is None:
            continue
        ox, oy = placements[part]
        for y, row in enumerate(rows):
            for x, c in enumerate(row):
                if 0 <= oy+y < 32 and 0 <= ox+x < 20 and c[3] > 0:
                    canvas[oy+y][ox+x] = c
    return _scale_rows(canvas, scale)


def _write_atlas_svg(compiled: CompiledTexture, path: Path, scale: int = 8) -> None:
    h = len(compiled.atlas); w = len(compiled.atlas[0])
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w*scale}" height="{h*scale}" viewBox="0 0 {w*scale} {h*scale}">', '<rect width="100%" height="100%" fill="#222"/>']
    parts.extend(_pixel_grid_svg(compiled.atlas, scale, 0, 0)); parts.append('</svg>')
    path.write_text("\n".join(parts), encoding="utf-8")


def _skin_model_svg(compiled: CompiledTexture, path: Path, face: str, scale: int = 10) -> None:
    p = compiled.region_pixels
    mapping = {"front": {"head": "head.front", "torso": "torso.front", "ra": "right_arm.front", "la": "left_arm.front", "rl": "right_leg.front", "ll": "left_leg.front"}, "back": {"head": "head.back", "torso": "torso.back", "ra": "right_arm.back", "la": "left_arm.back", "rl": "right_leg.back", "ll": "left_leg.back"}, "side": {"head": "head.right", "torso": "torso.right", "ra": "right_arm.right", "la": "left_arm.right", "rl": "right_leg.right", "ll": "left_leg.right"}}[face]
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{20*scale}" height="{32*scale}" viewBox="0 0 {20*scale} {32*scale}">', '<rect width="100%" height="100%" fill="#d7d7d7"/>']
    placements = ({"head": (6, 0), "torso": (6, 8), "ra": (2, 8), "la": (14, 8), "rl": (6, 20), "ll": (10, 20)} if face in ("front", "back") else {"head": (8, 0), "torso": (8, 8), "ra": (4, 8), "la": (12, 8), "rl": (8, 20), "ll": (12, 20)})
    for part, region_id in mapping.items():
        if region_id in p:
            x, y = placements[part]
            parts.extend(_pixel_grid_svg(p[region_id], scale, x * scale, y * scale))
    parts.append('</svg>'); path.write_text("\n".join(parts), encoding="utf-8")


def _head_svg(compiled: CompiledTexture, path: Path, scale: int = 24) -> None:
    rows = compiled.region_pixels.get("head.front")
    if rows is None: return
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{len(rows[0])*scale}" height="{len(rows)*scale}" viewBox="0 0 {len(rows[0])*scale} {len(rows)*scale}">', '<rect width="100%" height="100%" fill="#bbb"/>']
    parts.extend(_pixel_grid_svg(rows, scale, 0, 0)); parts.append('</svg>'); path.write_text("\n".join(parts), encoding="utf-8")


def emit_outputs(compiled: CompiledTexture, out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    write_rgba_png(out / "texture.png", compiled.atlas)
    (out / "resolved.json").write_text(json.dumps(compiled.summary, indent=2, sort_keys=True), encoding="utf-8")
    validation = compiled.summary["validation"]
    lines = ["PASS" if validation["passed"] else "FAIL"] + list(validation["issues"])
    lines += [f"paletteSize={compiled.summary['paletteSize']}", f"meanWeightedDeltaE00={compiled.summary['meanWeightedDeltaE00']:.6f}", f"digestSha256={compiled.summary['digestSha256']}"]
    (out / "validation.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    _write_atlas_svg(compiled, out / "atlas.svg")
    if compiled.summary.get("metadata", {}).get("targetKind") == "minecraft_skin_classic":
        _skin_model_svg(compiled, out / "model_front.svg", "front"); _skin_model_svg(compiled, out / "model_back.svg", "back"); _skin_model_svg(compiled, out / "model_side.svg", "side"); _head_svg(compiled, out / "head_closeup.svg")
        write_rgba_png(out / "model_front.png", _skin_model_rows(compiled, "front")); write_rgba_png(out / "model_back.png", _skin_model_rows(compiled, "back")); write_rgba_png(out / "model_side.png", _skin_model_rows(compiled, "side"))
        head = compiled.region_pixels.get("head.front")
        if head is not None: write_rgba_png(out / "head_closeup.png", _scale_rows(head, 24))
