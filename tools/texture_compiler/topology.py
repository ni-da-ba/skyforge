from __future__ import annotations

from model import Region, Seam, Texel, TextureSpecError


def edge(region: Region, side: str, reverse: bool = False) -> tuple[Texel, ...]:
    if side == "top":
        pts = [(region.region_id, x, 0) for x in range(region.width)]
    elif side == "bottom":
        pts = [(region.region_id, x, region.height - 1) for x in range(region.width)]
    elif side == "left":
        pts = [(region.region_id, 0, y) for y in range(region.height)]
    elif side == "right":
        pts = [(region.region_id, region.width - 1, y) for y in range(region.height)]
    else:
        raise TextureSpecError(f"unknown edge {side}")
    if reverse:
        pts.reverse()
    return tuple(pts)


def seam_between(
    seam_id: str,
    a_region: Region,
    a_side: str,
    b_region: Region,
    b_side: str,
    *,
    reverse_b: bool = False,
    hard: bool = True,
    weight: float = 3.0,
) -> Seam:
    return Seam(seam_id, edge(a_region, a_side), edge(b_region, b_side, reverse_b), hard, weight)


def cuboid_seams(prefix: str, faces: dict[str, Region], hard: bool = True) -> tuple[Seam, ...]:
    required = {"front", "back", "left", "right", "top", "bottom"}
    missing = required - set(faces)
    if missing:
        raise TextureSpecError(f"cuboid {prefix} missing faces: {', '.join(sorted(missing))}")
    f, b, l, r, t, d = (faces[k] for k in ("front", "back", "left", "right", "top", "bottom"))
    return (
        seam_between(prefix + ".front_left", f, "left", r, "right", hard=hard),
        seam_between(prefix + ".front_right", f, "right", l, "left", hard=hard),
        seam_between(prefix + ".back_left", b, "left", l, "right", hard=hard),
        seam_between(prefix + ".back_right", b, "right", r, "left", hard=hard),
        seam_between(prefix + ".front_top", f, "top", t, "bottom", hard=hard),
        seam_between(prefix + ".front_bottom", f, "bottom", d, "top", hard=hard),
        seam_between(prefix + ".back_top", b, "top", t, "top", reverse_b=True, hard=hard),
        seam_between(prefix + ".back_bottom", b, "bottom", d, "bottom", reverse_b=True, hard=hard),
        seam_between(prefix + ".right_top", r, "top", t, "left", hard=hard),
        seam_between(prefix + ".right_bottom", r, "bottom", d, "left", hard=hard),
        seam_between(prefix + ".left_top", l, "top", t, "right", hard=hard),
        seam_between(prefix + ".left_bottom", l, "bottom", d, "right", hard=hard),
    )
