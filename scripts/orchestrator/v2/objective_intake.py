from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
from pathlib import Path
import re
from typing import Any, Mapping

from .identity import canonical_digest
from .roadmap_service import RoadmapAuthorityLedger
from .roadmap_shadow import (
    RoadmapNodeKind,
    ShadowRoadmapManifest,
    ShadowRoadmapNode,
    ShadowRoadmapState,
    select_shadow_next_node,
)


MANIFEST_RELATIVE_PATH = Path("docs/agent-state/ORCHESTRATOR_ROADMAP.json")
V2_ROADMAP_STATE_RELATIVE_PATH = Path(".skyforge-platform-v2/roadmap-authority.json")
LEGACY_STATE_RELATIVE_PATH = Path(".skyforge-orchestrator/state.json")


class ObjectiveIntent(str, Enum):
    INVESTIGATE = "INVESTIGATE"
    FIX = "FIX"
    DEVELOP = "DEVELOP"
    CONTINUE = "CONTINUE"
    SHOW = "SHOW"


class ObjectiveCompileDisposition(str, Enum):
    READ_ONLY = "READ_ONLY"
    NEEDS_SCOPING = "NEEDS_SCOPING"
    CANDIDATE_TASK = "CANDIDATE_TASK"
    HUMAN_GATE = "HUMAN_GATE"
    BLOCKED = "BLOCKED"
    AMBIGUOUS = "AMBIGUOUS"


@dataclass(frozen=True)
class ObjectiveRequest:
    raw_text: str
    intent: ObjectiveIntent
    target: str

    def __post_init__(self) -> None:
        raw = " ".join(str(self.raw_text or "").split())
        target = " ".join(str(self.target or "").split())
        if not raw:
            raise ValueError("objective text is required")
        if not target:
            raise ValueError("objective target is required")
        object.__setattr__(self, "raw_text", raw)
        object.__setattr__(self, "target", target)

    def as_dict(self) -> dict[str, str]:
        return {
            "raw_text": self.raw_text,
            "intent": self.intent.value,
            "target": self.target,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "ObjectiveRequest":
        if not isinstance(raw, Mapping):
            raise ValueError("objective request must be an object")
        return cls(
            raw_text=raw.get("raw_text"),
            intent=ObjectiveIntent(str(raw.get("intent") or "")),
            target=raw.get("target"),
        )


@dataclass(frozen=True)
class ObjectiveCandidateTask:
    roadmap_id: str
    node_id: str
    lane: str
    issue_number: int
    objective: str
    stop_boundary: str

    def as_dict(self) -> dict[str, object]:
        return {
            "roadmap_id": self.roadmap_id,
            "node_id": self.node_id,
            "lane": self.lane,
            "issue_number": self.issue_number,
            "objective": self.objective,
            "stop_boundary": self.stop_boundary,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "ObjectiveCandidateTask":
        if not isinstance(raw, Mapping):
            raise ValueError("objective candidate task must be an object")
        issue = raw.get("issue_number")
        if isinstance(issue, bool) or not isinstance(issue, int) or issue <= 0:
            raise ValueError("objective candidate issue_number must be positive integer")
        return cls(
            roadmap_id=str(raw.get("roadmap_id") or ""),
            node_id=str(raw.get("node_id") or ""),
            lane=str(raw.get("lane") or ""),
            issue_number=issue,
            objective=str(raw.get("objective") or ""),
            stop_boundary=str(raw.get("stop_boundary") or ""),
        )


@dataclass(frozen=True)
class ObjectiveHumanGate:
    roadmap_id: str
    node_id: str
    lane: str | None
    message: str

    def as_dict(self) -> dict[str, object]:
        return {
            "roadmap_id": self.roadmap_id,
            "node_id": self.node_id,
            "lane": self.lane,
            "message": self.message,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "ObjectiveHumanGate":
        if not isinstance(raw, Mapping):
            raise ValueError("objective human gate must be an object")
        lane = raw.get("lane")
        return cls(
            roadmap_id=str(raw.get("roadmap_id") or ""),
            node_id=str(raw.get("node_id") or ""),
            lane=None if lane is None else str(lane),
            message=str(raw.get("message") or ""),
        )


@dataclass(frozen=True)
class ObjectiveCompileResult:
    disposition: ObjectiveCompileDisposition
    request: ObjectiveRequest | None
    reason: str
    candidate_task: ObjectiveCandidateTask | None = None
    human_gate: ObjectiveHumanGate | None = None

    def __post_init__(self) -> None:
        if self.disposition is ObjectiveCompileDisposition.CANDIDATE_TASK and self.candidate_task is None:
            raise ValueError("CANDIDATE_TASK requires candidate_task")
        if self.disposition is ObjectiveCompileDisposition.HUMAN_GATE and self.human_gate is None:
            raise ValueError("HUMAN_GATE requires human_gate")
        if self.candidate_task is not None and self.human_gate is not None:
            raise ValueError("objective result cannot contain both task and human gate")

    def as_dict(self) -> dict[str, object]:
        return {
            "schema_version": 1,
            "disposition": self.disposition.value,
            "reason": self.reason,
            "request": self.request.as_dict() if self.request else None,
            "candidate_task": self.candidate_task.as_dict() if self.candidate_task else None,
            "human_gate": self.human_gate.as_dict() if self.human_gate else None,
            "executable_task_authority": False,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "ObjectiveCompileResult":
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid objective compile result")
        disposition = ObjectiveCompileDisposition(str(raw.get("disposition") or ""))
        request_raw = raw.get("request")
        task_raw = raw.get("candidate_task")
        gate_raw = raw.get("human_gate")
        if raw.get("executable_task_authority") is not False:
            raise ValueError("objective compile result must remain non-authoritative")
        return cls(
            disposition=disposition,
            request=None if request_raw is None else ObjectiveRequest.from_mapping(request_raw),
            reason=str(raw.get("reason") or ""),
            candidate_task=None if task_raw is None else ObjectiveCandidateTask.from_mapping(task_raw),
            human_gate=None if gate_raw is None else ObjectiveHumanGate.from_mapping(gate_raw),
        )

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


_INTENT_PATTERNS: tuple[tuple[ObjectiveIntent, re.Pattern[str]], ...] = (
    (ObjectiveIntent.INVESTIGATE, re.compile(r"^(?:investigate|diagnose|analy[sz]e)\b\s*(.+)$", re.I)),
    (ObjectiveIntent.FIX, re.compile(r"^(?:fix|repair|correct)\b\s*(.+)$", re.I)),
    (ObjectiveIntent.DEVELOP, re.compile(r"^(?:develop|build|implement|create)\b\s*(.+)$", re.I)),
    (ObjectiveIntent.CONTINUE, re.compile(r"^(?:continue|proceed(?:\s+with)?|resume)\b\s*(.+)$", re.I)),
    (ObjectiveIntent.SHOW, re.compile(r"^(?:show|inspect|explain|status(?:\s+of)?)\b\s*(.+)$", re.I)),
)


def parse_objective(text: str) -> ObjectiveCompileResult:
    normalized = " ".join(str(text or "").strip().split())
    normalized = normalized.rstrip(".!? ")
    if not normalized:
        return ObjectiveCompileResult(
            ObjectiveCompileDisposition.AMBIGUOUS,
            None,
            "objective text is empty",
        )
    matches: list[ObjectiveRequest] = []
    for intent, pattern in _INTENT_PATTERNS:
        match = pattern.match(normalized)
        if match:
            target = match.group(1).strip(" .!?\t")
            if target:
                matches.append(ObjectiveRequest(normalized, intent, target))
    if len(matches) != 1:
        return ObjectiveCompileResult(
            ObjectiveCompileDisposition.AMBIGUOUS,
            None,
            "objective must begin with one supported intent verb: investigate, fix, develop, continue, or show",
        )
    request = matches[0]
    if request.intent in {ObjectiveIntent.INVESTIGATE, ObjectiveIntent.SHOW}:
        return ObjectiveCompileResult(
            ObjectiveCompileDisposition.READ_ONLY,
            request,
            "read-only objective recognized; repository/context acquisition is handled by a later intake tranche",
        )
    if request.intent in {ObjectiveIntent.FIX, ObjectiveIntent.DEVELOP}:
        return ObjectiveCompileResult(
            ObjectiveCompileDisposition.NEEDS_SCOPING,
            request,
            "mutating objective recognized but no repository-owned bounded scope has been derived yet",
        )
    return ObjectiveCompileResult(
        ObjectiveCompileDisposition.NEEDS_SCOPING,
        request,
        "continue objective requires authoritative roadmap compilation",
    )


def load_manifest(root: Path) -> ShadowRoadmapManifest:
    path = Path(root).resolve() / MANIFEST_RELATIVE_PATH
    raw = json.loads(path.read_text(encoding="utf-8"))
    return ShadowRoadmapManifest.from_mapping(raw)


def load_authoritative_roadmap_state(root: Path, manifest: ShadowRoadmapManifest) -> ShadowRoadmapState:
    root = Path(root).resolve()
    v2_path = root / V2_ROADMAP_STATE_RELATIVE_PATH
    if v2_path.is_file():
        raw = json.loads(v2_path.read_text(encoding="utf-8"))
        return RoadmapAuthorityLedger.from_mapping(raw).shadow_state(manifest)

    legacy_path = root / LEGACY_STATE_RELATIVE_PATH
    raw = json.loads(legacy_path.read_text(encoding="utf-8"))
    if not isinstance(raw, Mapping):
        raise ValueError("legacy controller state must be an object")
    roadmap = raw.get("roadmap")
    if not isinstance(roadmap, Mapping):
        raise ValueError("legacy controller state lacks roadmap projection")
    return ShadowRoadmapState.from_legacy(roadmap, manifest)


def _canonical_continue_target(target: str, manifest: ShadowRoadmapManifest) -> str | None:
    compact = re.sub(r"[^a-z0-9]+", "", target.lower())
    if compact in {"dr70", "dressedregion", "dressedregionconvergence", "dressedregionconvergencev3"}:
        return manifest.roadmap_id
    if target.strip().lower() == manifest.roadmap_id.lower():
        return manifest.roadmap_id
    if compact in {"skyforge", "project", "program"}:
        return "PROGRAM"
    return None


def _node_complete(node: ShadowRoadmapNode, state: ShadowRoadmapState) -> bool:
    completed = dict(state.completed_runs)
    return int(completed.get(node.node_id) or 0) >= node.max_runs


def _prerequisites_complete(node: ShadowRoadmapNode, by_id: Mapping[str, ShadowRoadmapNode], state: ShadowRoadmapState) -> bool:
    return all(_node_complete(by_id[prereq], state) for prereq in node.prerequisites)


def _historical_gate_ids(manifest: ShadowRoadmapManifest, state: ShadowRoadmapState) -> set[str]:
    completed = dict(state.completed_runs)
    historical: set[str] = set()
    for index, node in enumerate(manifest.nodes):
        if node.kind is not RoadmapNodeKind.GATE:
            continue
        if any(
            later.kind is RoadmapNodeKind.TASK
            and int(completed.get(later.node_id) or 0) > 0
            for later in manifest.nodes[index + 1 :]
        ):
            historical.add(node.node_id)
    return historical


def _current_blocked_gate(manifest: ShadowRoadmapManifest, state: ShadowRoadmapState) -> ShadowRoadmapNode | None:
    historical = _historical_gate_ids(manifest, state)
    blocked = set(state.blocked_nodes) - historical
    by_id = {node.node_id: node for node in manifest.nodes}
    candidates: list[tuple[int, ShadowRoadmapNode]] = []
    for index, node in enumerate(manifest.nodes):
        if node.kind is not RoadmapNodeKind.GATE or node.node_id not in blocked:
            continue
        if not _prerequisites_complete(node, by_id, state):
            continue
        candidates.append((index, node))
    if not candidates:
        return None
    # Later reached gates supersede older historical gate blocks. This matches the
    # accepted roadmap's forward-only progression without pretending the old human
    # judgment disappeared from durable history.
    return max(candidates, key=lambda item: item[0])[1]


def compile_continue_objective(
    request: ObjectiveRequest,
    *,
    manifest: ShadowRoadmapManifest,
    state: ShadowRoadmapState,
) -> ObjectiveCompileResult:
    if request.intent is not ObjectiveIntent.CONTINUE:
        raise ValueError("compile_continue_objective requires CONTINUE intent")
    target = _canonical_continue_target(request.target, manifest)
    if target is None:
        return ObjectiveCompileResult(
            ObjectiveCompileDisposition.AMBIGUOUS,
            request,
            "continue target does not match an accepted objective-intake scope",
        )
    if target == "PROGRAM":
        return ObjectiveCompileResult(
            ObjectiveCompileDisposition.BLOCKED,
            request,
            "program-wide Continue Skyforge selection is reserved for OPT-6; OPT-1 may not invent program work",
        )
    if state.roadmap_id != manifest.roadmap_id or state.manifest_fingerprint != manifest.fingerprint:
        return ObjectiveCompileResult(
            ObjectiveCompileDisposition.BLOCKED,
            request,
            "authoritative roadmap state does not match the accepted manifest",
        )
    if state.active is not None:
        return ObjectiveCompileResult(
            ObjectiveCompileDisposition.BLOCKED,
            request,
            "roadmap already has active durable authority; continue must reconcile that authority first",
        )

    effective_blocked = set(state.blocked_nodes) | _historical_gate_ids(manifest, state)
    node = select_shadow_next_node(
        manifest,
        completed_runs=dict(state.completed_runs),
        blocked_nodes=effective_blocked,
    )
    if node is None:
        gate = _current_blocked_gate(manifest, state)
        if gate is not None:
            return ObjectiveCompileResult(
                ObjectiveCompileDisposition.HUMAN_GATE,
                request,
                "latest reached roadmap boundary is an explicit human gate; machines may not advance past it",
                human_gate=ObjectiveHumanGate(
                    roadmap_id=manifest.roadmap_id,
                    node_id=gate.node_id,
                    lane=gate.lane,
                    message=str(gate.human_message or ""),
                ),
            )
        return ObjectiveCompileResult(
            ObjectiveCompileDisposition.BLOCKED,
            request,
            "roadmap has no eligible unblocked task or gate",
        )
    if node.kind is RoadmapNodeKind.GATE:
        return ObjectiveCompileResult(
            ObjectiveCompileDisposition.HUMAN_GATE,
            request,
            "next eligible roadmap node is an explicit human gate",
            human_gate=ObjectiveHumanGate(
                roadmap_id=manifest.roadmap_id,
                node_id=node.node_id,
                lane=node.lane,
                message=str(node.human_message or ""),
            ),
        )
    if node.issue_number is None or node.lane is None or not node.objective_hint or not node.stop_boundary:
        return ObjectiveCompileResult(
            ObjectiveCompileDisposition.BLOCKED,
            request,
            "eligible roadmap task lacks bounded repository authority metadata",
        )
    return ObjectiveCompileResult(
        ObjectiveCompileDisposition.CANDIDATE_TASK,
        request,
        "accepted roadmap selected a bounded task candidate; this proposal is not executable task authority",
        candidate_task=ObjectiveCandidateTask(
            roadmap_id=manifest.roadmap_id,
            node_id=node.node_id,
            lane=node.lane,
            issue_number=node.issue_number,
            objective=node.objective_hint,
            stop_boundary=node.stop_boundary,
        ),
    )


def compile_objective(text: str, *, root: Path) -> ObjectiveCompileResult:
    parsed = parse_objective(text)
    if parsed.request is None or parsed.request.intent is not ObjectiveIntent.CONTINUE:
        return parsed
    manifest = load_manifest(root)
    state = load_authoritative_roadmap_state(root, manifest)
    return compile_continue_objective(parsed.request, manifest=manifest, state=state)
