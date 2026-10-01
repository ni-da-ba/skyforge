"""Source-fingerprint-validated bounded program progression projection for OPT-6."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import hashlib
import json
from pathlib import Path
from typing import Any, Mapping

from .identity import canonical_digest
from .objective_intake import ObjectiveCandidateTask

PROGRAM_PROGRESSION_RELATIVE_PATH = Path("docs/agent-state/PROGRAM_PROGRESSION.json")
DR_MANIFEST_RELATIVE_PATH = Path("docs/agent-state/ORCHESTRATOR_ROADMAP.json")


def _required(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


def _sha64(value: Any, label: str) -> str:
    text = _required(value, label).lower()
    if len(text) != 64 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase SHA-256 hex")
    return text


class ProgramNodeKind(str, Enum):
    TASK = "task"
    HUMAN_GATE = "human_gate"
    STRATEGIC_GATE = "strategic_gate"


@dataclass(frozen=True)
class ProgramSemanticSource:
    path: str
    sha256: str

    def __post_init__(self) -> None:
        path = _required(self.path, "semantic source path")
        if path.startswith("/") or ".." in Path(path).parts:
            raise ValueError("semantic source path must be repository-relative")
        object.__setattr__(self, "path", path)
        object.__setattr__(self, "sha256", _sha64(self.sha256, "semantic source sha256"))

    def as_dict(self) -> dict[str, str]:
        return {"path": self.path, "sha256": self.sha256}


@dataclass(frozen=True)
class ProgramNode:
    node_id: str
    kind: ProgramNodeKind
    prerequisites: tuple[str, ...] = ()
    issue_number: int | None = None
    lane: str = ""
    objective: str = ""
    stop_boundary: str = ""
    review_gate_id: str = ""
    message: str = ""
    dr_manifest_node_id: str = ""

    def __post_init__(self) -> None:
        object.__setattr__(self, "node_id", _required(self.node_id, "program node id"))
        if not isinstance(self.kind, ProgramNodeKind):
            object.__setattr__(self, "kind", ProgramNodeKind(str(self.kind)))
        object.__setattr__(
            self,
            "prerequisites",
            tuple(_required(value, "program prerequisite") for value in self.prerequisites),
        )
        if self.kind is ProgramNodeKind.TASK:
            if (
                isinstance(self.issue_number, bool)
                or not isinstance(self.issue_number, int)
                or self.issue_number <= 0
            ):
                raise ValueError("program task issue_number must be positive")
            for name in ("lane", "objective", "stop_boundary", "dr_manifest_node_id"):
                object.__setattr__(
                    self, name, _required(getattr(self, name), f"program task {name}")
                )
            if self.review_gate_id or self.message:
                raise ValueError("program task may not carry gate metadata")
        else:
            if self.issue_number is not None or self.lane or self.objective or self.stop_boundary:
                raise ValueError("program gate may not carry task metadata")
            object.__setattr__(self, "message", _required(self.message, "program gate message"))
            if self.kind is ProgramNodeKind.HUMAN_GATE:
                object.__setattr__(
                    self,
                    "review_gate_id",
                    _required(self.review_gate_id, "program review gate id"),
                )
            elif self.review_gate_id:
                raise ValueError("strategic gate may not carry review_gate_id")

    def candidate_task(self, program_id: str) -> ObjectiveCandidateTask:
        if self.kind is not ProgramNodeKind.TASK or self.issue_number is None:
            raise ValueError("only program task nodes have candidate task metadata")
        return ObjectiveCandidateTask(
            roadmap_id=program_id,
            node_id=self.node_id,
            lane=self.lane,
            issue_number=self.issue_number,
            objective=self.objective,
            stop_boundary=self.stop_boundary,
        )

    def as_dict(self) -> dict[str, Any]:
        value: dict[str, Any] = {
            "id": self.node_id,
            "kind": self.kind.value,
            "prerequisites": list(self.prerequisites),
            "message": self.message,
        }
        if self.kind is ProgramNodeKind.TASK:
            value.update(
                {
                    "issue_number": self.issue_number,
                    "lane": self.lane,
                    "objective": self.objective,
                    "stop_boundary": self.stop_boundary,
                    "dr_manifest_node_id": self.dr_manifest_node_id,
                }
            )
        elif self.kind is ProgramNodeKind.HUMAN_GATE:
            value["review_gate_id"] = self.review_gate_id
        return value


@dataclass(frozen=True)
class ProgramProjection:
    program_id: str
    semantic_sources: tuple[ProgramSemanticSource, ...]
    nodes: tuple[ProgramNode, ...]
    supersedes_projection_digest: str = ""

    def __post_init__(self) -> None:
        object.__setattr__(self, "program_id", _required(self.program_id, "program_id"))
        if self.supersedes_projection_digest:
            object.__setattr__(
                self,
                "supersedes_projection_digest",
                _sha64(
                    self.supersedes_projection_digest,
                    "supersedes_projection_digest",
                ),
            )
        if not self.semantic_sources:
            raise ValueError("program projection requires semantic sources")
        paths = [value.path for value in self.semantic_sources]
        if len(paths) != len(set(paths)):
            raise ValueError("duplicate program semantic source")
        ids = [value.node_id for value in self.nodes]
        if not ids or len(ids) != len(set(ids)):
            raise ValueError("program projection node ids must be unique and non-empty")
        seen: set[str] = set()
        for node in self.nodes:
            if any(prereq not in seen for prereq in node.prerequisites):
                raise ValueError("program projection prerequisites must reference earlier nodes")
            seen.add(node.node_id)

    def get(self, node_id: str) -> ProgramNode | None:
        key = _required(node_id, "program node id")
        return next((value for value in self.nodes if value.node_id == key), None)

    def successor(self, node_id: str) -> ProgramNode | None:
        for index, value in enumerate(self.nodes):
            if value.node_id == node_id:
                return self.nodes[index + 1] if index + 1 < len(self.nodes) else None
        raise ValueError("program node is not in projection")

    def as_dict(self) -> dict[str, Any]:
        value = {
            "schema_version": 1,
            "program_id": self.program_id,
            "semantic_sources": [value.as_dict() for value in self.semantic_sources],
            "nodes": [value.as_dict() for value in self.nodes],
        }
        if self.supersedes_projection_digest:
            value["supersedes_projection_digest"] = self.supersedes_projection_digest
        return value

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


def _parse_node(raw: Any) -> ProgramNode:
    if not isinstance(raw, Mapping):
        raise ValueError("program projection node must be object")
    issue = raw.get("issue_number")
    return ProgramNode(
        node_id=raw.get("id"),
        kind=ProgramNodeKind(str(raw.get("kind") or "")),
        prerequisites=tuple(str(value) for value in (raw.get("prerequisites") or [])),
        issue_number=issue,
        lane=str(raw.get("lane") or ""),
        objective=str(raw.get("objective") or ""),
        stop_boundary=str(raw.get("stop_boundary") or ""),
        review_gate_id=str(raw.get("review_gate_id") or ""),
        message=str(raw.get("message") or ""),
        dr_manifest_node_id=str(raw.get("dr_manifest_node_id") or ""),
    )


def _validate_source_digests(root: Path, sources: tuple[ProgramSemanticSource, ...]) -> None:
    for source in sources:
        path = root / source.path
        if not path.is_file():
            raise ValueError(f"program semantic source is missing: {source.path}")
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest != source.sha256:
            raise ValueError(f"program semantic source drifted: {source.path}")


def _validate_dr_task_bindings(root: Path, projection: ProgramProjection) -> None:
    raw = json.loads((root / DR_MANIFEST_RELATIVE_PATH).read_text(encoding="utf-8"))
    values = raw.get("nodes") if isinstance(raw, Mapping) else None
    if not isinstance(values, list):
        raise ValueError("DR manifest nodes are unavailable")
    by_id = {
        str(value.get("id") or ""): value
        for value in values
        if isinstance(value, Mapping)
    }
    for node in projection.nodes:
        if node.kind is not ProgramNodeKind.TASK:
            continue
        source = by_id.get(node.dr_manifest_node_id)
        if source is None:
            raise ValueError("program task DR manifest source is unavailable")
        expected = {
            "issue_number": node.issue_number,
            "lane": node.lane,
            "objective_hint": node.objective,
            "stop_boundary": node.stop_boundary,
        }
        actual = {key: source.get(key) for key in expected}
        if actual != expected:
            raise ValueError(
                f"program task differs from accepted DR manifest node {node.dr_manifest_node_id}"
            )


def load_program_projection(root: Path) -> ProgramProjection:
    root = Path(root).resolve()
    raw = json.loads((root / PROGRAM_PROGRESSION_RELATIVE_PATH).read_text(encoding="utf-8"))
    if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
        raise ValueError("invalid program progression projection")
    sources_raw = raw.get("semantic_sources")
    nodes_raw = raw.get("nodes")
    if not isinstance(sources_raw, list) or not isinstance(nodes_raw, list):
        raise ValueError("program projection sources/nodes must be lists")
    projection = ProgramProjection(
        program_id=raw.get("program_id"),
        semantic_sources=tuple(
            ProgramSemanticSource(
                path=value.get("path") if isinstance(value, Mapping) else None,
                sha256=value.get("sha256") if isinstance(value, Mapping) else None,
            )
            for value in sources_raw
        ),
        nodes=tuple(_parse_node(value) for value in nodes_raw),
        supersedes_projection_digest=str(
            raw.get("supersedes_projection_digest") or ""
        ),
    )
    _validate_source_digests(root, projection.semantic_sources)
    _validate_dr_task_bindings(root, projection)
    return projection


def program_candidate_for_identity(
    *,
    root: Path,
    program_id: str,
    node_id: str,
    projection_digest: str,
) -> ObjectiveCandidateTask:
    projection = load_program_projection(root)
    if projection.program_id != str(program_id or "").strip():
        raise ValueError("program child program_id differs from current projection")
    if projection.digest != str(projection_digest or "").strip():
        raise ValueError("program child projection digest differs from current projection")
    node = projection.get(node_id)
    if node is None or node.kind is not ProgramNodeKind.TASK:
        raise ValueError("program child node is not a current task")
    return node.candidate_task(projection.program_id)
