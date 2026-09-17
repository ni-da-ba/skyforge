from __future__ import annotations

from collections import defaultdict, deque
from dataclasses import dataclass
from math import exp, inf
from typing import Iterable

from color_math import delta_e_2000
from model import RGBA, Seam, Texel


def perceptual_rgba_distance(a: RGBA, b: RGBA) -> float:
    """CIEDE2000 color distance plus an explicit alpha-channel penalty."""
    return delta_e_2000(a, b) + abs(a[3] - b[3]) / 255.0 * 100.0


@dataclass
class _Edge:
    to: int
    rev: int
    cap: float


class _Dinic:
    def __init__(self, n: int):
        self.g: list[list[_Edge]] = [[] for _ in range(n)]

    def add_edge(self, u: int, v: int, cap: float) -> None:
        if cap < -1e-9:
            raise ValueError("negative capacity")
        cap = max(0.0, cap)
        f = _Edge(v, len(self.g[v]), cap)
        r = _Edge(u, len(self.g[u]), 0.0)
        self.g[u].append(f)
        self.g[v].append(r)

    def add_undirected(self, u: int, v: int, cap: float) -> None:
        self.add_edge(u, v, cap)
        self.add_edge(v, u, cap)

    def maxflow(self, s: int, t: int) -> float:
        total = 0.0
        while True:
            level = [-1] * len(self.g)
            level[s] = 0
            q = deque([s])
            while q:
                u = q.popleft()
                for e in self.g[u]:
                    if e.cap > 1e-12 and level[e.to] < 0:
                        level[e.to] = level[u] + 1
                        q.append(e.to)
            if level[t] < 0:
                break
            it = [0] * len(self.g)

            def dfs(u: int, pushed: float) -> float:
                if u == t:
                    return pushed
                while it[u] < len(self.g[u]):
                    i = it[u]
                    e = self.g[u][i]
                    if e.cap > 1e-12 and level[e.to] == level[u] + 1:
                        got = dfs(e.to, min(pushed, e.cap))
                        if got > 1e-12:
                            e.cap -= got
                            self.g[e.to][e.rev].cap += got
                            return got
                    it[u] += 1
                return 0.0

            while True:
                pushed = dfs(s, inf)
                if pushed <= 1e-12:
                    break
                total += pushed
        return total

    def source_reachable(self, s: int) -> set[int]:
        seen = {s}
        q = deque([s])
        while q:
            u = q.popleft()
            for e in self.g[u]:
                if e.cap > 1e-12 and e.to not in seen:
                    seen.add(e.to)
                    q.append(e.to)
        return seen


class _UnionFind:
    def __init__(self, items: Iterable[Texel]):
        self.parent = {x: x for x in items}

    def find(self, x: Texel) -> Texel:
        p = self.parent[x]
        if p != x:
            self.parent[x] = self.find(p)
        return self.parent[x]

    def union(self, a: Texel, b: Texel) -> None:
        ra, rb = self.find(a), self.find(b)
        if ra != rb:
            if ra > rb:
                ra, rb = rb, ra
            self.parent[rb] = ra


def weighted_k_medoids(samples: dict[Texel, tuple[RGBA, float]], max_colors: int, locked: tuple[RGBA, ...]) -> tuple[RGBA, ...]:
    weighted: dict[RGBA, float] = defaultdict(float)
    for color, weight in samples.values():
        weighted[color] += max(1e-6, weight)
    colors = sorted(weighted)
    if not colors:
        return ((0, 0, 0, 0),)
    k = min(max_colors, len(set(colors).union(locked)))
    medoids: list[RGBA] = []
    transparent = (0, 0, 0, 0)
    if any(c[3] == 0 for c in colors):
        medoids.append(transparent)
    for color in locked:
        if color not in medoids:
            medoids.append(color)
    medoids = medoids[:k]
    if not medoids:
        medoids.append(max(colors, key=lambda c: (weighted[c], tuple(-v for v in c))))
    while len(medoids) < k:
        candidates = [c for c in colors if c not in medoids]
        if not candidates:
            break
        next_color = max(
            candidates,
            key=lambda c: (weighted[c] * min(perceptual_rgba_distance(c, m) for m in medoids), tuple(-v for v in c)),
        )
        medoids.append(next_color)
    for _ in range(12):
        clusters: dict[int, list[RGBA]] = defaultdict(list)
        for color in colors:
            idx = min(range(len(medoids)), key=lambda i: (perceptual_rgba_distance(color, medoids[i]), i))
            clusters[idx].append(color)
        changed = False
        locked_set = set(locked)
        if transparent in medoids:
            locked_set.add(transparent)
        for i in range(len(medoids)):
            if medoids[i] in locked_set:
                continue
            members = clusters.get(i, [])
            if not members:
                continue
            best = min(
                members,
                key=lambda cand: (
                    sum(weighted[c] * perceptual_rgba_distance(c, cand) for c in members),
                    cand,
                ),
            )
            if best != medoids[i]:
                medoids[i] = best
                changed = True
        if not changed:
            break
    return tuple(medoids)


def optimize_labels(
    samples: dict[Texel, tuple[RGBA, float]],
    palette: tuple[RGBA, ...],
    region_shapes: dict[str, tuple[int, int]],
    seams: tuple[Seam, ...],
    smoothness: float,
    contrast_beta: float,
    region_smoothness: dict[str, float] | None = None,
) -> tuple[dict[Texel, int], dict]:
    texels = sorted(samples)
    uf = _UnionFind(texels)
    hard_pairs = 0
    for seam in seams:
        if seam.hard:
            for a, b in zip(seam.a, seam.b):
                uf.union(a, b)
                hard_pairs += 1

    groups: dict[Texel, list[Texel]] = defaultdict(list)
    for t in texels:
        groups[uf.find(t)].append(t)
    roots = sorted(groups)
    root_index = {r: i for i, r in enumerate(roots)}

    unary: list[list[float]] = [[0.0 for _ in palette] for _ in roots]
    for ri, root in enumerate(roots):
        for t in groups[root]:
            color, importance = samples[t]
            for li, p in enumerate(palette):
                d = perceptual_rgba_distance(color, p)
                unary[ri][li] += max(1e-4, importance) * d * d

    pair_weights: dict[tuple[int, int], float] = defaultdict(float)

    def add_pair(a: Texel, b: Texel, base_weight: float) -> None:
        ra, rb = root_index[uf.find(a)], root_index[uf.find(b)]
        if ra == rb:
            return
        ca, _wa = samples[a]
        cb, _wb = samples[b]
        contrast = delta_e_2000(ca, cb)
        w = max(0.0, base_weight) * exp(-max(0.0, contrast_beta) * contrast * contrast)
        key = (ra, rb) if ra < rb else (rb, ra)
        pair_weights[key] += w

    for region_id, (w, h) in region_shapes.items():
        for y in range(h):
            for x in range(w):
                a = (region_id, x, y)
                if x + 1 < w:
                    add_pair(a, (region_id, x + 1, y), smoothness * (region_smoothness or {}).get(region_id, 1.0))
                if y + 1 < h:
                    add_pair(a, (region_id, x, y + 1), smoothness * (region_smoothness or {}).get(region_id, 1.0))
    soft_pairs = 0
    for seam in seams:
        if not seam.hard:
            for a, b in zip(seam.a, seam.b):
                add_pair(a, b, seam.weight * smoothness)
                soft_pairs += 1

    labels = [min(range(len(palette)), key=lambda li: (unary[i][li], li)) for i in range(len(roots))]

    def energy(state: list[int]) -> float:
        e = sum(unary[i][state[i]] for i in range(len(roots)))
        for (i, j), w in pair_weights.items():
            if state[i] != state[j]:
                e += w
        return e

    start_energy = energy(labels)
    sweeps = 0
    while sweeps < 20:
        improved = False
        sweeps += 1
        for alpha in range(len(palette)):
            n = len(roots)
            d0 = [unary[i][labels[i]] for i in range(n)]
            d1 = [unary[i][alpha] for i in range(n)]
            undirected: list[tuple[int, int, float]] = []
            constant = 0.0
            for (i, j), w in pair_weights.items():
                li, lj = labels[i], labels[j]
                e00 = w if li != lj else 0.0
                e01 = w if li != alpha else 0.0
                e10 = w if alpha != lj else 0.0
                e11 = 0.0
                cut_w = max(0.0, (e01 + e10 - e00 - e11) / 2.0)
                ai = e10 - e00 - cut_w
                bj = e01 - e00 - cut_w
                d1[i] += ai
                d1[j] += bj
                constant += e00
                if cut_w > 1e-12:
                    undirected.append((i, j, cut_w))
            source, sink = n, n + 1
            graph = _Dinic(n + 2)
            for i in range(n):
                shift = min(d0[i], d1[i])
                constant += shift
                u0 = d0[i] - shift
                u1 = d1[i] - shift
                graph.add_edge(source, i, u1)
                graph.add_edge(i, sink, u0)
            for i, j, w in undirected:
                graph.add_undirected(i, j, w)
            graph.maxflow(source, sink)
            source_side = graph.source_reachable(source)
            candidate = labels[:]
            for i in range(n):
                if i not in source_side:
                    candidate[i] = alpha
            if energy(candidate) + 1e-8 < energy(labels):
                labels = candidate
                improved = True
        if not improved:
            break

    out: dict[Texel, int] = {}
    for root, members in groups.items():
        li = labels[root_index[root]]
        for t in members:
            out[t] = li
    seam_mismatches = 0
    for seam in seams:
        for a, b in zip(seam.a, seam.b):
            if out[a] != out[b]:
                seam_mismatches += 1
    return out, {
        "variableCount": len(roots),
        "texelCount": len(texels),
        "hardSeamPairsCollapsed": hard_pairs,
        "softSeamPairs": soft_pairs,
        "pairwiseEdgeCount": len(pair_weights),
        "alphaExpansionSweeps": sweeps,
        "startEnergy": start_energy,
        "finalEnergy": energy(labels),
        "seamMismatches": seam_mismatches,
        "algorithm": "contrast_sensitive_potts_alpha_expansion_graph_cut",
        "guarantee": "metric-Potts alpha-expansion move optimization; hard seams are exact equivalence constraints",
    }
