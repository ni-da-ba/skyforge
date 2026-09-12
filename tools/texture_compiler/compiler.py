from __future__ import annotations

import hashlib
import json
from math import ceil, floor

from color_math import average_linear_rgba, delta_e_2000
from model import CompiledTexture, RGBA, TextureProblem, Texel
from optimization import optimize_labels, weighted_k_medoids


def _resample(problem: TextureProblem) -> dict[Texel, tuple[RGBA, float]]:
    samples: dict[Texel, tuple[RGBA, float]] = {}
    for region in problem.regions:
        src = problem.sources[region.region_id]
        for ty in range(region.height):
            y0 = ty * src.height / region.height
            y1 = (ty + 1) * src.height / region.height
            sy0, sy1 = floor(y0), ceil(y1)
            for tx in range(region.width):
                x0 = tx * src.width / region.width
                x1 = (tx + 1) * src.width / region.width
                sx0, sx1 = floor(x0), ceil(x1)
                bucket: list[tuple[RGBA, float]] = []
                importance_sum = 0.0
                area_sum = 0.0
                for sy in range(max(0, sy0), min(src.height, sy1)):
                    oy = max(0.0, min(y1, sy + 1) - max(y0, sy))
                    for sx in range(max(0, sx0), min(src.width, sx1)):
                        ox = max(0.0, min(x1, sx + 1) - max(x0, sx))
                        area = ox * oy
                        if area <= 0:
                            continue
                        imp = max(1e-4, src.weight(sx, sy))
                        bucket.append((src.pixel(sx, sy), area * imp))
                        importance_sum += area * imp
                        area_sum += area
                color = average_linear_rgba(bucket)
                importance = importance_sum / max(area_sum, 1e-9)
                samples[(region.region_id, tx, ty)] = (color, importance)
    return samples


def compile_texture(problem: TextureProblem) -> CompiledTexture:
    samples = _resample(problem)
    palette = weighted_k_medoids(samples, problem.max_colors, problem.locked_colors)
    shapes = {r.region_id: (r.width, r.height) for r in problem.regions}
    labels, opt = optimize_labels(samples, palette, shapes, problem.seams, problem.smoothness, problem.contrast_beta)
    region_pixels: dict[str, list[list[RGBA]]] = {}
    atlas = [[(0, 0, 0, 0) for _x in range(problem.atlas_width)] for _y in range(problem.atlas_height)]
    perceptual_error = 0.0
    weighted_total = 0.0
    for region in problem.regions:
        rows: list[list[RGBA]] = []
        for y in range(region.height):
            row: list[RGBA] = []
            for x in range(region.width):
                t = (region.region_id, x, y)
                out = palette[labels[t]]
                row.append(out)
                atlas[region.atlas_y + y][region.atlas_x + x] = out
                src_color, importance = samples[t]
                perceptual_error += importance * delta_e_2000(src_color, out)
                weighted_total += importance
            rows.append(row)
        region_pixels[region.region_id] = rows
    canonical = json.dumps({"assetId": problem.asset_id, "atlas": atlas, "palette": palette, "optimizer": opt, "metadata": problem.metadata}, sort_keys=True, separators=(",", ":"))
    summary = {
        "schemaVersion": "0.1",
        "compilerVersion": "texture-compiler-0.1",
        "assetId": problem.asset_id,
        "atlas": {"width": problem.atlas_width, "height": problem.atlas_height},
        "regionCount": len(problem.regions),
        "texelCount": sum(r.width * r.height for r in problem.regions),
        "palette": [list(c) for c in palette],
        "paletteSize": len(palette),
        "meanWeightedDeltaE00": perceptual_error / max(weighted_total, 1e-9),
        "optimizer": opt,
        "metadata": problem.metadata,
        "validation": {"passed": opt["seamMismatches"] == 0 and len(palette) <= problem.max_colors, "issues": [] if opt["seamMismatches"] == 0 else [f"seam mismatches: {opt['seamMismatches']}"]},
        "digestSha256": hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
    }
    return CompiledTexture(summary=summary, atlas=atlas, region_pixels=region_pixels)
