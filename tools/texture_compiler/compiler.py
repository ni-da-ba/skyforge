from __future__ import annotations

import hashlib
import json
from math import ceil, floor

from color_math import average_linear_rgba, delta_e_2000
from detail_math import laplacian_detail_map, normalized_edge_density, optimal_discrete_budget
from model import CompiledTexture, DetailGroup, RGBA, TextureProblem, Texel
from optimization import optimize_labels, perceptual_rgba_distance, weighted_k_medoids


def _resample(problem: TextureProblem) -> dict[Texel, tuple[RGBA, float]]:
    samples: dict[Texel, tuple[RGBA, float]] = {}
    detail_maps = {
        rid: laplacian_detail_map(src) if problem.multiscale_detail_weight > 0 else tuple(0.0 for _ in src.pixels)
        for rid, src in problem.sources.items()
    }
    for region in problem.regions:
        src = problem.sources[region.region_id]
        detail = detail_maps[region.region_id]
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
                        base_imp = max(1e-4, src.weight(sx, sy))
                        saliency = detail[sy * src.width + sx]
                        imp = base_imp * (1.0 + problem.multiscale_detail_weight * saliency)
                        bucket.append((src.pixel(sx, sy), area * imp))
                        importance_sum += area * imp
                        area_sum += area
                color = average_linear_rgba(bucket)
                importance = importance_sum / max(area_sum, 1e-9)
                samples[(region.region_id, tx, ty)] = (color, importance)
    return samples


def _palette_distortion(samples: dict[Texel, tuple[RGBA, float]], palette: tuple[RGBA, ...]) -> float:
    if not samples or not palette:
        return 0.0
    return sum(
        max(1e-6, weight) * min(perceptual_rgba_distance(color, p) ** 2 for p in palette)
        for color, weight in samples.values()
    )


def _group_palette(
    samples: dict[Texel, tuple[RGBA, float]],
    problem: TextureProblem,
) -> tuple[tuple[RGBA, ...], dict, dict[str, float]]:
    """Allocate finite palette capacity to semantic groups by exact rate-distortion DP."""
    if not problem.detail_groups:
        palette = weighted_k_medoids(samples, problem.max_colors, problem.locked_colors)
        return palette, {"mode": "global_weighted_k_medoids"}, {}

    reserved: list[RGBA] = []
    if any(color[3] == 0 for color, _w in samples.values()):
        reserved.append((0, 0, 0, 0))
    for color in problem.locked_colors:
        if color not in reserved:
            reserved.append(color)
    if len(reserved) >= problem.max_colors:
        return tuple(reserved[: problem.max_colors]), {
            "mode": "semantic_rate_distortion",
            "reserved": [list(c) for c in reserved[: problem.max_colors]],
            "allocation": {},
            "warning": "reserved colors consumed full palette budget",
        }, {}

    region_to_group: dict[str, DetailGroup] = {}
    for group in problem.detail_groups:
        for rid in group.region_ids:
            region_to_group[rid] = group
    ungrouped = sorted({t[0] for t in samples} - set(region_to_group))
    groups = list(problem.detail_groups)
    if ungrouped:
        groups.append(DetailGroup("__ungrouped__", tuple(ungrouped), 1, max(1, min(4, problem.max_colors)), 1.0, 1.0))

    available = problem.max_colors - len(reserved)
    curves: dict[str, dict[int, float]] = {}
    palettes_by_group: dict[str, dict[int, tuple[RGBA, ...]]] = {}
    region_smoothness: dict[str, float] = {}
    reserved_tuple = tuple(reserved)
    for group in groups:
        subset = {t: v for t, v in samples.items() if t[0] in set(group.region_ids)}
        max_extra = min(group.max_colors, available)
        min_extra = min(group.min_colors, max_extra)
        if not subset:
            continue
        curves[group.group_id] = {}
        palettes_by_group[group.group_id] = {}
        for rid in group.region_ids:
            region_smoothness[rid] = group.smoothness_scale
        for extra in range(min_extra, max_extra + 1):
            full = weighted_k_medoids(subset, len(reserved_tuple) + extra, reserved_tuple)
            additions = tuple(c for c in full if c not in reserved_tuple)
            palette = tuple(reserved) + additions
            curves[group.group_id][extra] = group.weight * _palette_distortion(subset, palette)
            palettes_by_group[group.group_id][extra] = additions

    allocation, objective = optimal_discrete_budget(curves, available)
    palette_list = list(reserved)
    for gid in sorted(allocation):
        for color in palettes_by_group[gid][allocation[gid]]:
            if color not in palette_list:
                palette_list.append(color)
    if len(palette_list) < problem.max_colors:
        global_fill = weighted_k_medoids(samples, problem.max_colors, tuple(palette_list))
        for color in global_fill:
            if color not in palette_list and len(palette_list) < problem.max_colors:
                palette_list.append(color)
    palette = tuple(palette_list[: problem.max_colors])
    report = {
        "mode": "semantic_rate_distortion_dynamic_programming",
        "totalBudget": problem.max_colors,
        "reservedColors": [list(c) for c in reserved],
        "allocation": allocation,
        "allocationObjective": objective,
        "groupCurves": {gid: {str(k): v for k, v in sorted(curve.items())} for gid, curve in sorted(curves.items())},
        "finalPaletteSize": len(palette),
    }
    return palette, report, region_smoothness


def compile_texture(problem: TextureProblem) -> CompiledTexture:
    samples = _resample(problem)
    palette, palette_allocation, region_smoothness = _group_palette(samples, problem)
    shapes = {r.region_id: (r.width, r.height) for r in problem.regions}
    labels, opt = optimize_labels(
        samples,
        palette,
        shapes,
        problem.seams,
        problem.smoothness,
        problem.contrast_beta,
        region_smoothness,
    )
    region_pixels: dict[str, list[list[RGBA]]] = {}
    atlas = [[(0, 0, 0, 0) for _x in range(problem.atlas_width)] for _y in range(problem.atlas_height)]
    perceptual_error = 0.0
    weighted_total = 0.0
    region_metrics: dict[str, dict] = {}
    for region in problem.regions:
        rows: list[list[RGBA]] = []
        region_error = 0.0
        region_weight = 0.0
        for y in range(region.height):
            row: list[RGBA] = []
            for x in range(region.width):
                t = (region.region_id, x, y)
                out = palette[labels[t]]
                row.append(out)
                atlas[region.atlas_y + y][region.atlas_x + x] = out
                src_color, importance = samples[t]
                d = delta_e_2000(src_color, out) + abs(src_color[3] - out[3]) / 255.0 * 100.0
                perceptual_error += importance * d
                weighted_total += importance
                region_error += importance * d
                region_weight += importance
            rows.append(row)
        region_pixels[region.region_id] = rows
        region_metrics[region.region_id] = {
            "meanWeightedPerceptualError": region_error / max(region_weight, 1e-9),
            "uniqueColors": len({c for row in rows for c in row}),
            "edgeDensity": normalized_edge_density(rows),
        }
    canonical = json.dumps(
        {
            "assetId": problem.asset_id,
            "atlas": atlas,
            "palette": palette,
            "optimizer": opt,
            "paletteAllocation": palette_allocation,
            "metadata": problem.metadata,
        },
        sort_keys=True,
        separators=(",", ":"),
    )
    issues: list[str] = []
    if opt["seamMismatches"] != 0:
        issues.append(f"seam mismatches: {opt['seamMismatches']}")
    if len(palette) > problem.max_colors:
        issues.append("palette exceeded global budget")
    summary = {
        "schemaVersion": "0.2",
        "compilerVersion": "texture-compiler-0.2",
        "assetId": problem.asset_id,
        "atlas": {"width": problem.atlas_width, "height": problem.atlas_height},
        "regionCount": len(problem.regions),
        "texelCount": sum(r.width * r.height for r in problem.regions),
        "palette": [list(c) for c in palette],
        "paletteSize": len(palette),
        "meanWeightedDeltaE00": perceptual_error / max(weighted_total, 1e-9),
        "optimizer": opt,
        "paletteAllocation": palette_allocation,
        "regionMetrics": region_metrics,
        "multiscaleDetailWeight": problem.multiscale_detail_weight,
        "metadata": problem.metadata,
        "validation": {"passed": not issues, "issues": issues},
        "digestSha256": hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
    }
    return CompiledTexture(summary=summary, atlas=atlas, region_pixels=region_pixels)
