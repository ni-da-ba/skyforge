from __future__ import annotations

from model import RGBA, Region, Seam, SourcePatch, TextureProblem, TextureSpecError
from topology import edge


def _hex(value: str) -> RGBA:
    raw = value.strip().lstrip("#")
    if len(raw) == 6: raw += "ff"
    if len(raw) != 8: raise TextureSpecError(f"invalid color {value}")
    return tuple(int(raw[i:i+2], 16) for i in range(0, 8, 2))  # type: ignore[return-value]


def problem_from_spec(spec: dict) -> TextureProblem:
    target = spec.get("target", {}); atlas = target.get("atlas", {})
    regions: list[Region] = []; region_map: dict[str, Region] = {}; sources: dict[str, SourcePatch] = {}
    for item in target.get("regions", []):
        region = Region(str(item["id"]), int(item["width"]), int(item["height"]), int(item["atlasX"]), int(item["atlasY"])); regions.append(region); region_map[region.region_id] = region
    for item in spec.get("sources", []):
        rid = str(item["region"]); rows = item["pixels"]; h, w = len(rows), len(rows[0]); pixels = tuple(_hex(c) for row in rows for c in row)
        importance_rows = item.get("importance"); importance = tuple(1.0 for _ in pixels) if importance_rows is None else tuple(float(v) for row in importance_rows for v in row)
        sources[rid] = SourcePatch(w, h, pixels, importance)
    seams: list[Seam] = []
    for item in target.get("seams", []):
        ar = region_map[str(item["a"]["region"])]; br = region_map[str(item["b"]["region"])]
        seams.append(Seam(str(item["id"]), edge(ar, str(item["a"]["edge"]), bool(item["a"].get("reverse", False))), edge(br, str(item["b"]["edge"]), bool(item["b"].get("reverse", False))), bool(item.get("hard", True)), float(item.get("weight", 3.0))))
    opt = spec.get("optimization", {})
    return TextureProblem(asset_id=str(spec["assetId"]), atlas_width=int(atlas["width"]), atlas_height=int(atlas["height"]), regions=tuple(regions), sources=sources, seams=tuple(seams), max_colors=int(opt.get("maxColors", 8)), locked_colors=tuple(_hex(c) for c in opt.get("lockedColors", [])), smoothness=float(opt.get("smoothness", 1.0)), contrast_beta=float(opt.get("contrastBeta", 0.015)), metadata={"targetKind": "explicit_atlas", "sourceMode": "explicit_raster"})
