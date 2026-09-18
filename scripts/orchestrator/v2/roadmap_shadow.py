"""Pure roadmap recovery/progression projection for Platform v2 live shadow."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import hashlib
import json
from typing import Any, Mapping

from .identity import canonical_digest


VALID_LANES = frozenset(
    {"Implementation", "Authorship", "Content", "Music", "Presentation", "Audit"}
)


class RoadmapNodeKind(str, Enum):
    TASK = "task"
    GATE = "gate"


class RoadmapIssueTruth(str, Enum):
    OPEN = "OPEN"
    CLOSED = "CLOSED"
    UNKNOWN = "UNKNOWN"


class RoadmapShadowDisposition(str, Enum):
    BLOCK = "BLOCK"
    HUMAN_GATE = "HUMAN_GATE"
    TASK_ELIGIBLE = "TASK_ELIGIBLE"
    EXHAUSTED = "EXHAUSTED"


def _positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer")
    return value


@dataclass(frozen=True)
class ShadowRoadmapNode:
    node_id: str
    kind: RoadmapNodeKind
    lane: str | None
    issue_number: int | None
    priority: int
    max_runs: int
    prerequisites: tuple[str, ...]
    objective_hint: str | None = None
    stop_boundary: str | None = None
    human_message: str | None = None


@dataclass(frozen=True)
class ShadowRoadmapManifest:
    roadmap_id: str
    enabled: bool
    max_auto_claims_per_utc_day: int
    nodes: tuple[ShadowRoadmapNode, ...]
    fingerprint: str

    @classmethod
    def from_mapping(cls, payload: Any) -> "ShadowRoadmapManifest":
        if not isinstance(payload, Mapping):
            raise ValueError("roadmap manifest must be an object")
        if payload.get("schema_version") != 1:
            raise ValueError("unsupported roadmap schema_version")
        roadmap_id = str(payload.get("roadmap_id") or "").strip()
        if not roadmap_id:
            raise ValueError("roadmap_id is required")
        raw_nodes = payload.get("nodes")
        if not isinstance(raw_nodes, list) or not raw_nodes:
            raise ValueError("roadmap nodes must be a non-empty list")

        nodes: list[ShadowRoadmapNode] = []
        seen: set[str] = set()
        for index, raw in enumerate(raw_nodes):
            if not isinstance(raw, Mapping):
                raise ValueError(f"nodes[{index}] must be an object")
            node_id = str(raw.get("id") or "").strip()
            if not node_id or node_id in seen:
                raise ValueError(f"invalid or duplicate roadmap node id: {node_id!r}")
            seen.add(node_id)
            try:
                kind = RoadmapNodeKind(str(raw.get("kind") or "task").lower())
            except ValueError as exc:
                raise ValueError(f"{node_id}: invalid roadmap kind") from exc
            lane_raw = raw.get("lane")
            lane = str(lane_raw).strip() if lane_raw is not None else None
            if kind is RoadmapNodeKind.TASK and lane not in VALID_LANES:
                raise ValueError(f"{node_id}: task lane must be a known lane")
            if kind is RoadmapNodeKind.GATE and lane and lane not in VALID_LANES:
                raise ValueError(f"{node_id}: gate lane must be null or known")
            issue_raw = raw.get("issue_number")
            issue = None if issue_raw is None else _positive_int(issue_raw, f"{node_id}.issue_number")
            if kind is RoadmapNodeKind.TASK and issue is None:
                raise ValueError(f"{node_id}: task must be issue-backed")
            priority = raw.get("priority", 0)
            if isinstance(priority, bool) or not isinstance(priority, int):
                raise ValueError(f"{node_id}.priority must be an integer")
            max_runs = _positive_int(raw.get("max_runs", 1), f"{node_id}.max_runs")
            prereq_raw = raw.get("prerequisites") or []
            if not isinstance(prereq_raw, list) or any(not isinstance(x, str) or not x.strip() for x in prereq_raw):
                raise ValueError(f"{node_id}.prerequisites must be non-empty strings")
            objective = str(raw.get("objective_hint") or "").strip() or None
            stop_boundary = str(raw.get("stop_boundary") or "").strip() or None
            human = str(raw.get("human_message") or "").strip() or None
            if kind is RoadmapNodeKind.TASK and (not objective or not stop_boundary):
                raise ValueError(
                    f"{node_id}: task objective_hint and stop_boundary are required"
                )
            if kind is RoadmapNodeKind.GATE and not human:
                raise ValueError(f"{node_id}: gate human_message is required")
            nodes.append(
                ShadowRoadmapNode(
                    node_id=node_id,
                    kind=kind,
                    lane=lane,
                    issue_number=issue,
                    priority=priority,
                    max_runs=max_runs,
                    prerequisites=tuple(x.strip() for x in prereq_raw),
                    objective_hint=objective,
                    stop_boundary=stop_boundary,
                    human_message=human,
                )
            )

        unknown = {
            prereq
            for node in nodes
            for prereq in node.prerequisites
            if prereq not in seen
        }
        if unknown:
            raise ValueError(f"unknown roadmap prerequisites: {sorted(unknown)}")

        encoded = json.dumps(
            dict(payload), sort_keys=True, separators=(",", ":")
        ).encode("utf-8")
        max_claims = _positive_int(
            payload.get("max_auto_claims_per_utc_day", 1),
            "max_auto_claims_per_utc_day",
        )
        return cls(
            roadmap_id=roadmap_id,
            enabled=bool(payload.get("enabled", True)),
            max_auto_claims_per_utc_day=max_claims,
            nodes=tuple(nodes),
            fingerprint=hashlib.sha256(encoded).hexdigest(),
        )


@dataclass(frozen=True)
class ShadowRoadmapState:
    roadmap_id: str
    manifest_fingerprint: str
    completed_runs: tuple[tuple[str, int], ...]
    blocked_nodes: tuple[str, ...]
    active: Mapping[str, Any] | None

    @classmethod
    def from_legacy(
        cls,
        raw: Any,
        manifest: ShadowRoadmapManifest,
    ) -> "ShadowRoadmapState":
        if not isinstance(raw, Mapping):
            raise ValueError("legacy roadmap state must be an object")
        roadmap_id = str(raw.get("roadmap_id") or "")
        fingerprint = str(raw.get("manifest_fingerprint") or "")
        if roadmap_id != manifest.roadmap_id:
            raise ValueError("legacy roadmap_id does not match accepted manifest")
        if fingerprint != manifest.fingerprint:
            raise ValueError("legacy roadmap manifest fingerprint is stale or mismatched")

        ids = {node.node_id for node in manifest.nodes}
        completed_raw = raw.get("completed_runs") or {}
        blocked_raw = raw.get("blocked_nodes") or {}
        if not isinstance(completed_raw, Mapping) or not isinstance(blocked_raw, Mapping):
            raise ValueError("completed_runs and blocked_nodes must be objects")
        if any(str(key) not in ids for key in completed_raw):
            raise ValueError("completed_runs contains unknown node")
        if any(str(key) not in ids for key in blocked_raw):
            raise ValueError("blocked_nodes contains unknown node")

        completed: list[tuple[str, int]] = []
        for key, value in completed_raw.items():
            if isinstance(value, bool) or not isinstance(value, int) or value < 0:
                raise ValueError("completed run count must be a non-negative integer")
            completed.append((str(key), value))

        active = raw.get("active")
        if active is not None and not isinstance(active, Mapping):
            raise ValueError("roadmap active must be an object or null")

        return cls(
            roadmap_id=roadmap_id,
            manifest_fingerprint=fingerprint,
            completed_runs=tuple(sorted(completed)),
            blocked_nodes=tuple(sorted(str(key) for key in blocked_raw)),
            active=active,
        )

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "roadmap_id": self.roadmap_id,
                "manifest_fingerprint": self.manifest_fingerprint,
                "completed_runs": list(self.completed_runs),
                "blocked_nodes": list(self.blocked_nodes),
                "active": dict(self.active) if self.active is not None else None,
            }
        )


@dataclass(frozen=True)
class RoadmapControlObservation:
    paused: bool = False
    protected_pending_authority: bool = False
    pending_decision: bool = False
    pending_worker: bool = False
    blocked_kind: bool = False
    open_task_owned_prs: tuple[int, ...] = ()
    task_pr_truth_complete: bool = True
    no_change_blocked_issues: tuple[int, ...] = ()

    def __post_init__(self) -> None:
        for name in (
            "paused",
            "protected_pending_authority",
            "pending_decision",
            "pending_worker",
            "blocked_kind",
            "task_pr_truth_complete",
        ):
            if not isinstance(getattr(self, name), bool):
                raise ValueError(f"{name} must be boolean")
        for values, label in (
            (self.open_task_owned_prs, "open_task_owned_prs"),
            (self.no_change_blocked_issues, "no_change_blocked_issues"),
        ):
            if any(isinstance(v, bool) or not isinstance(v, int) or v <= 0 for v in values):
                raise ValueError(f"{label} must contain positive integers")


@dataclass(frozen=True)
class RoadmapShadowDecision:
    disposition: RoadmapShadowDisposition
    reason: str
    selected_node_id: str | None
    selected_lane: str | None
    selected_issue_number: int | None
    retired_closed_blocked: tuple[str, ...]
    skipped_closed_selected: tuple[str, ...]
    projected_completed_runs: tuple[tuple[str, int], ...]
    projected_blocked_nodes: tuple[str, ...]
    state_digest: str

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "selected_node_id": self.selected_node_id,
                "selected_lane": self.selected_lane,
                "selected_issue_number": self.selected_issue_number,
                "retired_closed_blocked": list(self.retired_closed_blocked),
                "skipped_closed_selected": list(self.skipped_closed_selected),
                "projected_completed_runs": list(self.projected_completed_runs),
                "projected_blocked_nodes": list(self.projected_blocked_nodes),
                "state_digest": self.state_digest,
            }
        )


def select_shadow_next_node(
    manifest: ShadowRoadmapManifest,
    *,
    completed_runs: Mapping[str, int],
    blocked_nodes: set[str],
) -> ShadowRoadmapNode | None:
    if not manifest.enabled:
        return None
    by_id = {node.node_id: node for node in manifest.nodes}

    def complete(node_id: str) -> bool:
        node = by_id[node_id]
        return int(completed_runs.get(node_id) or 0) >= node.max_runs

    candidates: list[tuple[int, int, ShadowRoadmapNode]] = []
    for index, node in enumerate(manifest.nodes):
        if node.node_id in blocked_nodes:
            continue
        if int(completed_runs.get(node.node_id) or 0) >= node.max_runs:
            continue
        if any(not complete(prereq) for prereq in node.prerequisites):
            continue
        candidates.append((-node.priority, index, node))
    if not candidates:
        return None
    candidates.sort(key=lambda item: (item[0], item[1]))
    return candidates[0][2]


def evaluate_roadmap_shadow(
    *,
    manifest: ShadowRoadmapManifest,
    state: ShadowRoadmapState,
    issue_truth: Mapping[int, RoadmapIssueTruth],
    control: RoadmapControlObservation,
) -> RoadmapShadowDecision:
    completed = dict(state.completed_runs)
    blocked = set(state.blocked_nodes)
    retired: list[str] = []
    skipped: list[str] = []

    def decision(
        disposition: RoadmapShadowDisposition,
        reason: str,
        selected: ShadowRoadmapNode | None = None,
    ) -> RoadmapShadowDecision:
        return RoadmapShadowDecision(
            disposition=disposition,
            reason=reason,
            selected_node_id=selected.node_id if selected else None,
            selected_lane=selected.lane if selected else None,
            selected_issue_number=selected.issue_number if selected else None,
            retired_closed_blocked=tuple(retired),
            skipped_closed_selected=tuple(skipped),
            projected_completed_runs=tuple(sorted(completed.items())),
            projected_blocked_nodes=tuple(sorted(blocked)),
            state_digest=state.digest,
        )

    if (
        control.paused
        or control.protected_pending_authority
        or control.pending_decision
        or control.pending_worker
        or control.blocked_kind
    ):
        return decision(
            RoadmapShadowDisposition.BLOCK,
            "controller/protected authority guard blocks roadmap progression",
        )
    if not manifest.enabled:
        return decision(RoadmapShadowDisposition.BLOCK, "roadmap manifest is disabled")
    if state.active is not None:
        return decision(
            RoadmapShadowDisposition.BLOCK,
            "active roadmap node requires dedicated active-node reconciliation first",
        )

    by_id = {node.node_id: node for node in manifest.nodes}
    for node in manifest.nodes:
        if node.node_id not in blocked or node.kind is not RoadmapNodeKind.TASK:
            continue
        truth = issue_truth.get(node.issue_number, RoadmapIssueTruth.UNKNOWN)
        if truth is RoadmapIssueTruth.CLOSED:
            completed[node.node_id] = max(
                int(completed.get(node.node_id) or 0),
                node.max_runs,
            )
            blocked.remove(node.node_id)
            retired.append(node.node_id)

    if not control.task_pr_truth_complete:
        return decision(
            RoadmapShadowDisposition.BLOCK,
            "task-owned managed PR terminal/open truth is incomplete",
        )
    if control.open_task_owned_prs:
        return decision(
            RoadmapShadowDisposition.BLOCK,
            "open task-owned managed PR blocks fresh roadmap progression",
        )

    no_change = set(control.no_change_blocked_issues)
    while True:
        node = select_shadow_next_node(
            manifest,
            completed_runs=completed,
            blocked_nodes=blocked,
        )
        if node is None:
            return decision(
                RoadmapShadowDisposition.EXHAUSTED,
                "no eligible unblocked roadmap node remains",
            )
        if node.kind is RoadmapNodeKind.GATE:
            return decision(
                RoadmapShadowDisposition.HUMAN_GATE,
                "highest-priority eligible roadmap node is a human gate",
                node,
            )

        truth = issue_truth.get(node.issue_number, RoadmapIssueTruth.UNKNOWN)
        if truth is RoadmapIssueTruth.UNKNOWN:
            return decision(
                RoadmapShadowDisposition.BLOCK,
                "selected task issue truth is unknown",
                node,
            )
        if truth is RoadmapIssueTruth.CLOSED:
            completed[node.node_id] = max(
                int(completed.get(node.node_id) or 0),
                node.max_runs,
            )
            skipped.append(node.node_id)
            continue
        if node.issue_number in no_change:
            return decision(
                RoadmapShadowDisposition.BLOCK,
                "selected task retains a durable no-change blocker whose authority freshness is not independently cleared",
                node,
            )
        return decision(
            RoadmapShadowDisposition.TASK_ELIGIBLE,
            "highest-priority eligible task issue is open",
            node,
        )
