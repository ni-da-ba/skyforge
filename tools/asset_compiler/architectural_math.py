from __future__ import annotations

from collections import deque
from dataclasses import dataclass
from math import inf
from typing import Any, Callable, Iterable, Mapping, Sequence

Point2 = tuple[int, int]
Point3 = tuple[int, int, int]
Candidate = Mapping[str, int]


@dataclass(frozen=True)
class TwoEaveGableField:
    """Discrete gable field produced by two inward-moving eave wavefronts.

    This is the closed-form rectangular/two-eave case used by the current Guild branch. It keeps
    the field abstraction that can later be replaced by a general straight-skeleton roof solver
    without changing downstream roof, rafter, ridge, or validation code.
    """

    z0: int
    z1: int
    rise: int
    base_y: int

    def __post_init__(self) -> None:
        if self.z1 <= self.z0:
            raise ValueError("gable field requires z1 > z0")
        if self.rise < 1:
            raise ValueError("gable field rise must be positive")

    @property
    def run(self) -> float:
        return max((self.z1 - self.z0) / 2.0, 1.0)

    @property
    def pitch_ratio(self) -> float:
        return self.rise / self.run

    def height(self, z: int) -> int:
        sample = min(max(z, self.z0), self.z1)
        half = max((self.z1 - self.z0) // 2, 1)
        edge_distance = min(sample - self.z0, self.z1 - sample)
        return self.base_y + (self.rise * edge_distance) // half

    def profile(self, overhang: int = 0) -> list[tuple[int, int]]:
        return [(z, self.height(z)) for z in range(self.z0 - overhang, self.z1 + overhang + 1)]

    def ridge_stations(self) -> list[int]:
        values = [(z, self.height(z)) for z in range(self.z0, self.z1 + 1)]
        top = max(y for _z, y in values)
        return [z for z, y in values if y == top]


def normalized_square_error(value: float, target: float, minimum: float, maximum: float) -> float:
    scale = max(target - minimum, maximum - target, 1e-12)
    return ((value - target) / scale) ** 2


def ratio_in_bounds(value: float, profile: Mapping[str, float]) -> bool:
    return float(profile["min"]) <= value <= float(profile["max"])


def solve_discrete(
    candidates: Iterable[Candidate],
    hard_rules: Sequence[tuple[str, Callable[[Candidate], bool]]],
    soft_terms: Sequence[tuple[str, float, Callable[[Candidate], float]]],
) -> dict[str, Any]:
    """Deterministic finite-domain optimizer.

    Hard rules are feasibility predicates. Soft terms are non-negative penalties; the optimizer
    minimizes their weighted sum. Ties resolve lexicographically by variable name/value so repeated
    compiles are deterministic and do not depend on Python hash ordering.
    """

    evaluated: list[dict[str, Any]] = []
    feasible: list[tuple[float, tuple[tuple[str, int], ...], dict[str, int], list[dict[str, float]]]] = []
    for raw in candidates:
        candidate = {str(k): int(v) for k, v in raw.items()}
        failed = [name for name, rule in hard_rules if not bool(rule(candidate))]
        terms: list[dict[str, float]] = []
        score = 0.0
        if not failed:
            for name, weight, fn in soft_terms:
                penalty = max(0.0, float(fn(candidate)))
                contribution = float(weight) * penalty
                score += contribution
                terms.append(
                    {
                        "name": name,
                        "weight": float(weight),
                        "penalty": penalty,
                        "contribution": contribution,
                    }
                )
            key = tuple(sorted(candidate.items()))
            feasible.append((score, key, candidate, terms))
        evaluated.append(
            {
                "candidate": dict(sorted(candidate.items())),
                "feasible": not failed,
                "failedHardConstraints": failed,
                "score": score if not failed else None,
                "terms": terms,
            }
        )
    if not feasible:
        raise ValueError("discrete architectural optimization has no feasible candidate")
    feasible.sort(key=lambda item: (item[0], item[1]))
    score, _key, chosen, terms = feasible[0]
    return {
        "chosen": dict(sorted(chosen.items())),
        "score": score,
        "terms": terms,
        "candidateCount": len(evaluated),
        "feasibleCount": len(feasible),
        "evaluated": evaluated,
    }


def centered_opening(bay_start: int, bay_width: int, opening_width: int) -> tuple[int, int]:
    """Centered split-grammar opening between structural bay posts.

    A bay from post x=s to post x=s+w has w-1 interior cells. If an odd opening is centered in an
    even number of interior cells, the lower coordinate wins deterministically.
    """

    interior_start = bay_start + 1
    interior_end = bay_start + bay_width - 1
    available = interior_end - interior_start + 1
    if opening_width < 1 or opening_width > available:
        raise ValueError("opening width does not fit bay interior")
    start = interior_start + (available - opening_width) // 2
    return start, start + opening_width - 1


def facade_bay_grammar(
    bay_count: int,
    bay_width: int,
    opening_width: int,
    excluded_bays: Iterable[int] = (),
) -> tuple[list[tuple[int, int]], list[dict[str, Any]]]:
    excluded = set(int(v) for v in excluded_bays)
    groups: list[tuple[int, int]] = []
    trace: list[dict[str, Any]] = []
    for bay in range(bay_count):
        bay_span = [bay * bay_width, (bay + 1) * bay_width]
        if bay in excluded:
            trace.append(
                {
                    "rule": "facade.bay.semantic_interruption",
                    "bay": bay,
                    "baySpan": bay_span,
                    "exception": True,
                }
            )
            continue
        a, b = centered_opening(bay * bay_width, bay_width, opening_width)
        groups.append((a, b))
        trace.append(
            {
                "rule": "facade.bay.centered_opening",
                "bay": bay,
                "baySpan": bay_span,
                "openingSpan": [a, b],
                "openingWidth": opening_width,
                "exception": False,
            }
        )
    return groups, trace


def grammar_description_length_proxy(trace: Sequence[Mapping[str, Any]]) -> float:
    """Small MDL-style proxy: rule vocabulary + exceptions + repeated production cost.

    It is intentionally a proxy, not a statistical claim. It lets optimization prefer a compact,
    repeated grammar over many one-off exceptions while retaining semantic interruptions explicitly.
    """

    rules = {str(item.get("rule")) for item in trace}
    exceptions = sum(1 for item in trace if item.get("exception"))
    return float(len(rules)) + 2.0 * exceptions + 0.1 * len(trace)


@dataclass(frozen=True)
class LayerClaim:
    pos: Point3
    layer: str
    token: str


INCOMPATIBLE_LAYER_PAIRS = {
    frozenset(("structure", "opening_void")),
    frozenset(("envelope", "opening_void")),
    frozenset(("hardware", "opening_void")),
    frozenset(("furnishing", "opening_void")),
    frozenset(("glazing", "furnishing")),
    frozenset(("glazing", "structure")),
    frozenset(("glazing", "envelope")),
    frozenset(("glazing", "hardware")),
}


def layer_conflicts(claims: Iterable[LayerClaim]) -> list[dict[str, Any]]:
    by_pos: dict[Point3, list[LayerClaim]] = {}
    for claim in claims:
        by_pos.setdefault(claim.pos, []).append(claim)
    conflicts: list[dict[str, Any]] = []
    for pos, items in sorted(by_pos.items()):
        for i, left in enumerate(items):
            for right in items[i + 1 :]:
                if frozenset((left.layer, right.layer)) in INCOMPATIBLE_LAYER_PAIRS:
                    conflicts.append(
                        {
                            "pos": list(pos),
                            "layers": sorted([left.layer, right.layer]),
                            "tokens": sorted([left.token, right.token]),
                        }
                    )
    return conflicts


def digital_connected_path(samples: Sequence[Point2]) -> list[Point2]:
    """Convert ordered (z,y) targets into a 4-connected digital polyline.

    Rising steps advance horizontally before climbing; falling steps descend before advancing. This
    keeps bridge cells beneath the target roof field instead of punching into the weather skin.
    """

    if not samples:
        return []
    out: list[Point2] = [samples[0]]
    for next_z, next_y in samples[1:]:
        z, y = out[-1]
        if next_z < z:
            raise ValueError("digital path samples must have nondecreasing z")
        while z < next_z:
            if next_y < y:
                while y > next_y:
                    y -= 1
                    out.append((z, y))
            z += 1
            out.append((z, y))
            if next_y > y:
                while y < next_y:
                    y += 1
                    out.append((z, y))
        while y != next_y:
            y += 1 if next_y > y else -1
            out.append((z, y))
    deduped: list[Point2] = []
    for point in out:
        if not deduped or point != deduped[-1]:
            deduped.append(point)
    return deduped


def is_four_connected(path: Sequence[Point2]) -> bool:
    return all(abs(a[0] - b[0]) + abs(a[1] - b[1]) == 1 for a, b in zip(path, path[1:]))


def neighbors4(point: Point2) -> tuple[Point2, Point2, Point2, Point2]:
    x, z = point
    return ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))


def shortest_path_length(walkable: set[Point2], start: Point2, goal: Point2) -> int | None:
    if start not in walkable or goal not in walkable:
        return None
    queue = deque([(start, 0)])
    seen = {start}
    while queue:
        node, distance = queue.popleft()
        if node == goal:
            return distance
        for nxt in neighbors4(node):
            if nxt in walkable and nxt not in seen:
                seen.add(nxt)
                queue.append((nxt, distance + 1))
    return None


def reachable_distances(walkable: set[Point2], start: Point2) -> dict[Point2, int]:
    if start not in walkable:
        return {}
    distances = {start: 0}
    queue = deque([start])
    while queue:
        node = queue.popleft()
        for nxt in neighbors4(node):
            if nxt in walkable and nxt not in distances:
                distances[nxt] = distances[node] + 1
                queue.append(nxt)
    return distances


def closeness_centrality(walkable: set[Point2], start: Point2) -> float:
    distances = reachable_distances(walkable, start)
    if len(distances) <= 1:
        return 0.0
    return (len(distances) - 1) / max(sum(distances.values()), 1)


def bresenham_line(start: Point2, end: Point2) -> list[Point2]:
    x0, y0 = start
    x1, y1 = end
    dx = abs(x1 - x0)
    dy = -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    line: list[Point2] = []
    while True:
        line.append((x0, y0))
        if x0 == x1 and y0 == y1:
            break
        twice = 2 * err
        if twice >= dy:
            err += dy
            x0 += sx
        if twice <= dx:
            err += dx
            y0 += sy
    return line


def visible_2d(opaque: set[Point2], start: Point2, end: Point2) -> bool:
    return all(point not in opaque for point in bresenham_line(start, end)[1:-1])


def visibility_count(walkable: set[Point2], opaque: set[Point2], start: Point2) -> int:
    if start not in walkable:
        return 0
    return sum(1 for point in walkable if point != start and visible_2d(opaque, start, point))


def neighbors6(point: Point3) -> tuple[Point3, Point3, Point3, Point3, Point3, Point3]:
    x, y, z = point
    return (
        (x + 1, y, z),
        (x - 1, y, z),
        (x, y + 1, z),
        (x, y - 1, z),
        (x, y, z + 1),
        (x, y, z - 1),
    )


def unsupported_targets(nodes: set[Point3], supports: set[Point3], targets: set[Point3]) -> set[Point3]:
    """Return structural target nodes without a 6-connected path to any support node."""

    seeds = nodes & supports
    if not seeds:
        return set(targets)
    queue = deque(seeds)
    reached = set(seeds)
    while queue:
        node = queue.popleft()
        for nxt in neighbors6(node):
            if nxt in nodes and nxt not in reached:
                reached.add(nxt)
                queue.append(nxt)
    return set(targets) - reached


def path_stretch(distance: int | None, start: Point2, goal: Point2) -> float:
    if distance is None:
        return inf
    direct = abs(start[0] - goal[0]) + abs(start[1] - goal[1])
    return float(distance) / max(direct, 1)
