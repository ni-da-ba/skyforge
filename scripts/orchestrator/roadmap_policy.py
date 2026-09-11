#!/usr/bin/env python3
"""Pure helpers for the bounded Skyforge orchestrator roadmap.

The human-readable PROGRAM_ROADMAP remains the semantic authority. This module only validates the
small machine-readable execution manifest and selects an explicitly authorized next node.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

VALID_LANES = {
    "Implementation",
    "Authorship",
    "Content",
    "Music",
    "Presentation",
    "Audit",
}
VALID_KINDS = {"task", "gate"}


class RoadmapError(RuntimeError):
    pass


@dataclass(frozen=True)
class RoadmapNode:
    node_id: str
    kind: str
    lane: str | None
    issue_number: int | None
    priority: int
    max_runs: int
    prerequisites: tuple[str, ...]
    objective_hint: str
    stop_boundary: str
    human_message: str | None = None


@dataclass(frozen=True)
class RoadmapManifest:
    roadmap_id: str
    enabled: bool
    max_auto_claims_per_utc_day: int
    nodes: tuple[RoadmapNode, ...]
    fingerprint: str


def _positive_int(value: Any, *, field: str, minimum: int = 1) -> int:
    try:
        result = int(value)
    except (TypeError, ValueError) as exc:
        raise RoadmapError(f"{field} must be an integer") from exc
    if result < minimum:
        raise RoadmapError(f"{field} must be >= {minimum}")
    return result


def parse_manifest(payload: dict[str, Any]) -> RoadmapManifest:
    if not isinstance(payload, dict):
        raise RoadmapError("roadmap manifest must be an object")
    if int(payload.get("schema_version") or 0) != 1:
        raise RoadmapError("unsupported roadmap schema_version")

    roadmap_id = str(payload.get("roadmap_id") or "").strip()
    if not roadmap_id:
        raise RoadmapError("roadmap_id is required")

    raw_nodes = payload.get("nodes")
    if not isinstance(raw_nodes, list) or not raw_nodes:
        raise RoadmapError("roadmap nodes must be a non-empty list")

    seen: set[str] = set()
    nodes: list[RoadmapNode] = []
    for index, raw in enumerate(raw_nodes):
        if not isinstance(raw, dict):
            raise RoadmapError(f"nodes[{index}] must be an object")
        node_id = str(raw.get("id") or "").strip()
        if not node_id:
            raise RoadmapError(f"nodes[{index}].id is required")
        if node_id in seen:
            raise RoadmapError(f"duplicate roadmap node id: {node_id}")
        seen.add(node_id)

        kind = str(raw.get("kind") or "task").strip().lower()
        if kind not in VALID_KINDS:
            raise RoadmapError(f"{node_id}: invalid kind {kind!r}")

        lane_value = raw.get("lane")
        lane = str(lane_value).strip() if lane_value is not None else None
        if kind == "task" and lane not in VALID_LANES:
            raise RoadmapError(f"{node_id}: task lane must be a known Skyforge lane")
        if kind == "gate" and lane and lane not in VALID_LANES:
            raise RoadmapError(f"{node_id}: gate lane must be null or a known Skyforge lane")

        issue_value = raw.get("issue_number")
        issue_number = None
        if issue_value is not None:
            issue_number = _positive_int(issue_value, field=f"{node_id}.issue_number")
        if kind == "task" and issue_number is None:
            raise RoadmapError(
                f"{node_id}: v1 roadmap tasks must be issue-backed; missing-issue synthesis remains fail-closed"
            )

        priority = int(raw.get("priority") or 0)
        max_runs = _positive_int(raw.get("max_runs") or 1, field=f"{node_id}.max_runs")
        prereq_raw = raw.get("prerequisites") or []
        if not isinstance(prereq_raw, list):
            raise RoadmapError(f"{node_id}.prerequisites must be a list")
        prerequisites = tuple(str(value).strip() for value in prereq_raw if str(value).strip())

        objective_hint = str(raw.get("objective_hint") or "").strip()
        stop_boundary = str(raw.get("stop_boundary") or "").strip()
        human_message = str(raw.get("human_message") or "").strip() or None
        if kind == "task" and (not objective_hint or not stop_boundary):
            raise RoadmapError(f"{node_id}: task objective_hint and stop_boundary are required")
        if kind == "gate" and not human_message:
            raise RoadmapError(f"{node_id}: gate human_message is required")

        nodes.append(
            RoadmapNode(
                node_id=node_id,
                kind=kind,
                lane=lane,
                issue_number=issue_number,
                priority=priority,
                max_runs=max_runs,
                prerequisites=prerequisites,
                objective_hint=objective_hint,
                stop_boundary=stop_boundary,
                human_message=human_message,
            )
        )

    unknown = {
        prerequisite
        for node in nodes
        for prerequisite in node.prerequisites
        if prerequisite not in seen
    }
    if unknown:
        raise RoadmapError(f"unknown roadmap prerequisite(s): {sorted(unknown)}")

    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return RoadmapManifest(
        roadmap_id=roadmap_id,
        enabled=bool(payload.get("enabled", True)),
        max_auto_claims_per_utc_day=_positive_int(
            payload.get("max_auto_claims_per_utc_day") or 6,
            field="max_auto_claims_per_utc_day",
        ),
        nodes=tuple(nodes),
        fingerprint=hashlib.sha256(canonical).hexdigest(),
    )


def load_manifest(path: Path) -> RoadmapManifest:
    try:
        payload = json.loads(path.read_text())
    except FileNotFoundError as exc:
        raise RoadmapError(f"roadmap manifest not found: {path}") from exc
    except json.JSONDecodeError as exc:
        raise RoadmapError(f"roadmap manifest is not valid JSON: {exc}") from exc
    return parse_manifest(payload)


def select_next_node(
    manifest: RoadmapManifest,
    *,
    completed_runs: dict[str, int] | None = None,
    blocked_nodes: set[str] | None = None,
) -> RoadmapNode | None:
    """Return the highest-priority explicitly eligible node.

    A node is eligible only when every prerequisite has completed its configured run count, it has
    remaining runs, and it is not blocked. Stable manifest order breaks priority ties.
    """
    if not manifest.enabled:
        return None
    completed_runs = completed_runs or {}
    blocked_nodes = blocked_nodes or set()

    by_id = {node.node_id: node for node in manifest.nodes}

    def complete(node_id: str) -> bool:
        node = by_id[node_id]
        return int(completed_runs.get(node_id) or 0) >= node.max_runs

    candidates: list[tuple[int, int, RoadmapNode]] = []
    for index, node in enumerate(manifest.nodes):
        if node.node_id in blocked_nodes:
            continue
        if int(completed_runs.get(node.node_id) or 0) >= node.max_runs:
            continue
        if any(not complete(prerequisite) for prerequisite in node.prerequisites):
            continue
        candidates.append((-node.priority, index, node))

    if not candidates:
        return None
    candidates.sort(key=lambda item: (item[0], item[1]))
    return candidates[0][2]
