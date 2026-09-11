from __future__ import annotations

import hashlib
from typing import Any

from model import SpecError


def stable_index(seed: int, key: str, count: int) -> int:
    """Return a deterministic index without Python's randomized hash()."""
    if count <= 0:
        raise SpecError("stable_index requires count > 0")
    digest = hashlib.sha256(f"{seed}:{key}".encode("utf-8")).digest()
    return int.from_bytes(digest[:8], "big") % count


def ratio(numerator: int | float, denominator: int | float, name: str) -> float:
    if denominator == 0:
        raise SpecError(f"cannot evaluate articulation ratio {name}: denominator is zero")
    return float(numerator) / float(denominator)


def evaluate_ratio_profile(
    metrics: dict[str, float],
    profile: dict[str, Any],
) -> tuple[dict[str, Any], list[str]]:
    """Compare measured proportions to an explicit bounded working-reference profile."""
    rules = profile.get("ratios")
    if not isinstance(rules, dict) or not rules:
        raise SpecError("articulation reference profile requires a non-empty ratios object")

    report: dict[str, Any] = {}
    issues: list[str] = []
    for name, rule in sorted(rules.items()):
        if name not in metrics:
            issues.append(f"missing articulation metric: {name}")
            continue
        value = float(metrics[name])
        minimum = float(rule["min"])
        target = float(rule.get("target", (minimum + float(rule["max"])) / 2.0))
        maximum = float(rule["max"])
        if not minimum <= target <= maximum:
            raise SpecError(f"invalid ratio rule ordering for {name}: min <= target <= max required")
        passed = minimum <= value <= maximum
        report[name] = {
            "value": round(value, 6),
            "min": minimum,
            "target": target,
            "max": maximum,
            "passed": passed,
            "distanceFromTarget": round(abs(value - target), 6),
        }
        if not passed:
            issues.append(
                f"articulation ratio {name}={value:.4f} outside [{minimum:.4f}, {maximum:.4f}]"
            )
    return report, issues


def validate_depth_plane_contract(
    declared: dict[str, int],
    required_order: list[str],
) -> list[str]:
    """Validate an ordered semantic depth vocabulary around the primary facade plane."""
    missing = [name for name in required_order if name not in declared]
    if missing:
        return [f"missing articulation depth planes: {', '.join(missing)}"]
    values = [int(declared[name]) for name in required_order]
    issues: list[str] = []
    if values != sorted(values):
        issues.append(
            "articulation depth planes are not monotonic: "
            + ", ".join(f"{name}={declared[name]}" for name in required_order)
        )
    if len(set(values)) != len(values):
        issues.append("articulation depth planes must use distinct offsets")
    return issues


def contiguous_groups(values: list[int]) -> list[tuple[int, int]]:
    ordered = sorted(set(int(v) for v in values))
    if not ordered:
        return []
    groups: list[tuple[int, int]] = []
    start = previous = ordered[0]
    for value in ordered[1:]:
        if value != previous + 1:
            groups.append((start, previous))
            start = value
        previous = value
    groups.append((start, previous))
    return groups


def choose_contiguous_patch(
    eligible: list[int],
    *,
    seed: int,
    key: str,
    span: int,
) -> list[int]:
    """Choose one deterministic contiguous patch from semantically eligible positions."""
    if span < 1:
        raise SpecError("history patch span must be >= 1")
    candidates: list[list[int]] = []
    for lo, hi in contiguous_groups(eligible):
        for start in range(lo, hi - span + 2):
            candidates.append(list(range(start, start + span)))
    if not candidates:
        raise SpecError(f"no contiguous history patch of span {span} fits eligible positions")
    return candidates[stable_index(seed, key, len(candidates))]
