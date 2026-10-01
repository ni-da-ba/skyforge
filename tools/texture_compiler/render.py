from __future__ import annotations

import html
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


def _composite_rows(base: list[list[RGBA]], overlay: list[list[RGBA]] | None) -> list[list[RGBA]]:
    rows = [row[:] for row in base]
    if overlay is None:
        return rows
    for y in range(min(len(rows), len(overlay))):
        for x in range(min(len(rows[y]), len(overlay[y]))):
            c = overlay[y][x]
            if c[3] > 0:
                rows[y][x] = c
    return rows


def _skin_part(compiled: CompiledTexture, prefix: str, face: str, include_overlay: bool = True) -> list[list[RGBA]] | None:
    base = compiled.region_pixels.get(f"{prefix}.{face}")
    if base is None:
        return None
    if not include_overlay:
        return [row[:] for row in base]
    overlay = compiled.region_pixels.get(f"{prefix}_overlay.{face}")
    return _composite_rows(base, overlay)


def _rotate_cw(rows: list[list[RGBA]]) -> list[list[RGBA]]:
    return [list(reversed(col)) for col in zip(*rows)]


def _rotate_ccw(rows: list[list[RGBA]]) -> list[list[RGBA]]:
    return [list(col) for col in zip(*rows)][::-1]


def _skin_model_rows(compiled: CompiledTexture, face: str, scale: int = 8, include_overlay: bool = True) -> list[list[RGBA]]:
    mapping = {
        "front": {"head": "head", "torso": "torso", "ra": "right_arm", "la": "left_arm", "rl": "right_leg", "ll": "left_leg"},
        "back": {"head": "head", "torso": "torso", "ra": "right_arm", "la": "left_arm", "rl": "right_leg", "ll": "left_leg"},
        "side": {"head": "head", "torso": "torso", "ra": "right_arm", "la": "left_arm", "rl": "right_leg", "ll": "left_leg"},
    }[face]
    tex_face = "right" if face == "side" else face
    bg = (215, 215, 215, 255)
    logical_w, logical_h = 20, 32
    canvas = [[bg for _ in range(logical_w)] for _ in range(logical_h)]
    placements = ({"head": (6, 0), "torso": (6, 8), "ra": (2, 8), "la": (14, 8), "rl": (6, 20), "ll": (10, 20)}
                  if face in ("front", "back") else
                  {"head": (8, 0), "torso": (8, 8), "ra": (4, 8), "la": (12, 8), "rl": (8, 20), "ll": (12, 20)})
    for part, prefix in mapping.items():
        rows = _skin_part(compiled, prefix, tex_face, include_overlay)
        if rows is None:
            continue
        ox, oy = placements[part]
        for y, row in enumerate(rows):
            for x, c in enumerate(row):
                if 0 <= oy+y < logical_h and 0 <= ox+x < logical_w and c[3] > 0:
                    canvas[oy+y][ox+x] = c
    return _scale_rows(canvas, scale)


def _skin_arms_raised_rows(compiled: CompiledTexture, scale: int = 8) -> list[list[RGBA]]:
    bg = (215, 215, 215, 255)
    logical_w, logical_h = 32, 32
    canvas = [[bg for _ in range(logical_w)] for _ in range(logical_h)]
    parts = {
        "head": (_skin_part(compiled, "head", "front", True), 12, 0),
        "torso": (_skin_part(compiled, "torso", "front", True), 12, 8),
        "rl": (_skin_part(compiled, "right_leg", "front", True), 12, 20),
        "ll": (_skin_part(compiled, "left_leg", "front", True), 16, 20),
    }
    for _name, (rows, ox, oy) in parts.items():
        if rows is None:
            continue
        for y,row in enumerate(rows):
            for x,c in enumerate(row):
                if c[3] > 0:
                    canvas[oy+y][ox+x]=c
    ra = _skin_part(compiled, "right_arm", "front", True)
    la = _skin_part(compiled, "left_arm", "front", True)
    if ra is not None:
        rr = _rotate_ccw(ra)
        for y,row in enumerate(rr):
            for x,c in enumerate(row):
                if c[3] > 0:
                    canvas[10+y][x]=c
    if la is not None:
        lr = _rotate_cw(la)
        ox = 20
        for y,row in enumerate(lr):
            for x,c in enumerate(row):
                if c[3] > 0:
                    canvas[10+y][ox+x]=c
    return _scale_rows(canvas, scale)


def _write_atlas_svg(compiled: CompiledTexture, path: Path, scale: int = 8) -> None:
    h = len(compiled.atlas)
    w = len(compiled.atlas[0])
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w*scale}" height="{h*scale}" viewBox="0 0 {w*scale} {h*scale}">', '<rect width="100%" height="100%" fill="#222"/>']
    parts.extend(_pixel_grid_svg(compiled.atlas, scale, 0, 0))
    for x in range(w + 1):
        parts.append(f'<line x1="{x*scale}" y1="0" x2="{x*scale}" y2="{h*scale}" stroke="#000" stroke-opacity="0.18" stroke-width="1"/>')
    for y in range(h + 1):
        parts.append(f'<line x1="0" y1="{y*scale}" x2="{w*scale}" y2="{y*scale}" stroke="#000" stroke-opacity="0.18" stroke-width="1"/>')
    parts.append('</svg>')
    path.write_text("\n".join(parts), encoding="utf-8")


def _skin_model_svg(compiled: CompiledTexture, path: Path, face: str, scale: int = 10, include_overlay: bool = True) -> None:
    rows = _skin_model_rows(compiled, face, 1, include_overlay)
    h = len(rows); w = len(rows[0])
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w*scale}" height="{h*scale}" viewBox="0 0 {w*scale} {h*scale}">']
    parts.extend(_pixel_grid_svg(rows, scale, 0, 0))
    parts.append('</svg>')
    path.write_text("\n".join(parts), encoding="utf-8")


def _head_svg(compiled: CompiledTexture, path: Path, scale: int = 24) -> None:
    base = compiled.region_pixels.get("head.front")
    if base is None:
        return
    rows = _composite_rows(base, compiled.region_pixels.get("head_overlay.front"))
    w = len(rows[0]) * scale
    h = len(rows) * scale
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">', '<rect width="100%" height="100%" fill="#bbb"/>']
    parts.extend(_pixel_grid_svg(rows, scale, 0, 0))
    parts.append('</svg>')
    path.write_text("\n".join(parts), encoding="utf-8")


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
        _skin_model_svg(compiled, out / "model_front.svg", "front")
        _skin_model_svg(compiled, out / "model_back.svg", "back")
        _skin_model_svg(compiled, out / "model_side.svg", "side")
        _skin_model_svg(compiled, out / "model_front_base.svg", "front", include_overlay=False)
        _head_svg(compiled, out / "head_closeup.svg")
        write_rgba_png(out / "model_front.png", _skin_model_rows(compiled, "front"))
        write_rgba_png(out / "model_back.png", _skin_model_rows(compiled, "back"))
        write_rgba_png(out / "model_side.png", _skin_model_rows(compiled, "side"))
        write_rgba_png(out / "model_front_base.png", _skin_model_rows(compiled, "front", include_overlay=False))
        write_rgba_png(out / "model_arms_raised.png", _skin_arms_raised_rows(compiled))
        head = compiled.region_pixels.get("head.front")
        if head is not None:
            head = _composite_rows(head, compiled.region_pixels.get("head_overlay.front"))
            write_rgba_png(out / "head_closeup.png", _scale_rows(head, 24))
