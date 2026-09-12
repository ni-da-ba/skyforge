from __future__ import annotations

from math import sqrt

from model import SourcePatch


def _luma(pixel: tuple[int, int, int, int]) -> float:
    # Rec.709 luminance on normalized sRGB is sufficient for detail localization; final color
    # fidelity remains CIEDE2000 in the compiler's perceptual objective.
    r, g, b, _a = pixel
    return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0


def _blur5(values: list[list[float]]) -> list[list[float]]:
    """Burt/Adelson-style separable binomial low-pass kernel [1,4,6,4,1]/16."""
    h, w = len(values), len(values[0])
    kernel = (1.0, 4.0, 6.0, 4.0, 1.0)
    tmp = [[0.0] * w for _ in range(h)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            s = 0.0
            for k, kv in enumerate(kernel):
                xx = min(w - 1, max(0, x + k - 2))
                s += kv * values[y][xx]
            tmp[y][x] = s / 16.0
    for y in range(h):
        for x in range(w):
            s = 0.0
            for k, kv in enumerate(kernel):
                yy = min(h - 1, max(0, y + k - 2))
                s += kv * tmp[yy][x]
            out[y][x] = s / 16.0
    return out


def laplacian_detail_map(patch: SourcePatch, levels: int = 3) -> tuple[float, ...]:
    """Return normalized multiscale residual energy at source resolution.

    This is a compact fixed-resolution Laplacian-pyramid analogue: each level removes a binomial
    low-pass prediction, accumulates the localized residual, then repeats on the low-pass signal.
    It preserves the key property needed here—salient structure is represented at multiple scales—
    without adding an image-processing dependency to the deterministic compiler.
    """
    current = [[_luma(patch.pixel(x, y)) for x in range(patch.width)] for y in range(patch.height)]
    energy = [[0.0] * patch.width for _ in range(patch.height)]
    scale_weight = 1.0
    for _ in range(max(1, levels)):
        low = _blur5(current)
        for y in range(patch.height):
            for x in range(patch.width):
                energy[y][x] += scale_weight * abs(current[y][x] - low[y][x])
        current = low
        scale_weight *= 0.6
    flat = [v for row in energy for v in row]
    if not flat or max(flat) <= 1e-12:
        return tuple(0.0 for _ in flat)
    ordered = sorted(flat)
    hi = ordered[min(len(ordered) - 1, max(0, int(round(0.95 * (len(ordered) - 1))))]
    denom = max(hi, 1e-9)
    return tuple(min(1.0, v / denom) for v in flat)


def optimal_discrete_budget(
    curves: dict[str, dict[int, float]], total_budget: int
) -> tuple[dict[str, int], float]:
    """Exact separable rate-distortion allocation by dynamic programming.

    Each group supplies distortion D_g(k) for a discrete resource count k. The solver minimizes
    sum_g D_g(k_g) subject to sum_g k_g <= B. This is the classical bounded resource-allocation
    problem underlying optimal bit/palette allocation across independently modeled subbands/regions.
    """
    groups = sorted(curves)
    if not groups:
        return {}, 0.0
    states: dict[int, tuple[float, tuple[int, ...]]] = {0: (0.0, ())}
    for gid in groups:
        next_states: dict[int, tuple[float, tuple[int, ...]]] = {}
        for used, (cost, alloc) in states.items():
            for k, d in sorted(curves[gid].items()):
                nu = used + k
                if nu > total_budget:
                    continue
                cand = (cost + d, alloc + (k,))
                old = next_states.get(nu)
                if old is None or cand < old:
                    next_states[nu] = cand
        states = next_states
    if not states:
        raise ValueError("no feasible detail-budget allocation")
    used, (best_cost, alloc) = min(states.items(), key=lambda item: (item[1][0], -item[0], item[1][1]))
    return {gid: alloc[i] for i, gid in enumerate(groups)}, best_cost


def normalized_edge_density(rows: list[list[tuple[int, int, int, int]]]) -> float:
    """Simple QA statistic: fraction of 4-neighbor pairs that change color."""
    h = len(rows)
    w = len(rows[0]) if h else 0
    changed = total = 0
    for y in range(h):
        for x in range(w):
            if x + 1 < w:
                total += 1
                changed += rows[y][x] != rows[y][x + 1]
            if y + 1 < h:
                total += 1
                changed += rows[y][x] != rows[y + 1][x]
    return changed / max(total, 1)
