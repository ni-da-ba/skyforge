from __future__ import annotations

import hashlib
from math import inf
from typing import Sequence

Point2 = tuple[int, int]


def hungarian_min_cost(cost: Sequence[Sequence[float]]) -> tuple[list[int], float]:
    """Solve rectangular linear assignment in O(n^2 m), n <= m.

    Returns the selected column for each row and the exact minimum total cost.  This is the classic
    primal-dual Hungarian algorithm; it is useful for assigning semantic fixtures to distinct valid
    architectural sites without greedy collisions.
    """
    n = len(cost)
    if n == 0:
        return [], 0.0
    m = len(cost[0])
    if m == 0 or any(len(row) != m for row in cost):
        raise ValueError("assignment cost matrix must be non-empty and rectangular")
    if n > m:
        raise ValueError("hungarian_min_cost requires rows <= columns")
    u = [0.0] * (n + 1)
    v = [0.0] * (m + 1)
    p = [0] * (m + 1)
    way = [0] * (m + 1)
    for i in range(1, n + 1):
        p[0] = i
        j0 = 0
        minv = [inf] * (m + 1)
        used = [False] * (m + 1)
        while True:
            used[j0] = True
            i0 = p[j0]
            delta = inf
            j1 = 0
            for j in range(1, m + 1):
                if used[j]:
                    continue
                cur = float(cost[i0 - 1][j - 1]) - u[i0] - v[j]
                if cur < minv[j] - 1e-12 or (abs(cur - minv[j]) <= 1e-12 and j0 < way[j]):
                    minv[j] = cur
                    way[j] = j0
                if minv[j] < delta - 1e-12 or (abs(minv[j] - delta) <= 1e-12 and j < j1):
                    delta = minv[j]
                    j1 = j
            for j in range(m + 1):
                if used[j]:
                    u[p[j]] += delta
                    v[j] -= delta
                else:
                    minv[j] -= delta
            j0 = j1
            if p[j0] == 0:
                break
        while True:
            j1 = way[j0]
            p[j0] = p[j1]
            j0 = j1
            if j0 == 0:
                break
    assignment = [-1] * n
    for j in range(1, m + 1):
        if p[j] != 0:
            assignment[p[j] - 1] = j - 1
    total = sum(float(cost[i][assignment[i]]) for i in range(n))
    return assignment, total


def _stable_rank(point: Point2, seed: int) -> int:
    raw = f"{seed}:{point[0]}:{point[1]}".encode("utf-8")
    return int.from_bytes(hashlib.sha256(raw).digest()[:8], "big")


def blue_noise_maximin_select(
    candidates: Sequence[Point2],
    count: int,
    min_distance: float,
    seed: int,
) -> list[Point2]:
    """Deterministic discrete maximin sampling with a Poisson-disk spacing contract.

    The finite candidate domain makes exact rejection simple: each accepted sample must remain at
    least `min_distance` from all accepted samples.  Among feasible candidates, the next point
    maximizes its distance to the current set, giving the sparse, non-clumped behavior sought from
    blue-noise / Poisson-disk placement while remaining deterministic for compiler use.
    """
    pts = sorted(set(candidates))
    if count <= 0 or not pts:
        return []
    first = min(pts, key=lambda p: (_stable_rank(p, seed), p))
    selected = [first]
    remaining = [p for p in pts if p != first]
    min_d2 = float(min_distance) ** 2
    while remaining and len(selected) < count:
        feasible: list[tuple[float, int, Point2]] = []
        for p in remaining:
            d2 = min((p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2 for q in selected)
            if d2 + 1e-12 >= min_d2:
                feasible.append((d2, -_stable_rank(p, seed), p))
        if not feasible:
            break
        _d2, _rank, chosen = max(feasible, key=lambda item: (item[0], item[1], tuple(-v for v in item[2])))
        selected.append(chosen)
        remaining.remove(chosen)
    return selected


def minimum_pair_distance(points: Sequence[Point2]) -> float | None:
    if len(points) < 2:
        return None
    return min(
        ((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2) ** 0.5
        for i, a in enumerate(points)
        for b in points[i + 1 :]
    )
