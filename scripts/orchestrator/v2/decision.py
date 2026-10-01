"""Typed cached-classifier decisions and pure freshness policy for Platform v2."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any, Mapping

from .events import normalize_legacy_event_key
from .identity import canonical_digest


class DecisionKind(str, Enum):
    NOOP = "NOOP"
    DISPATCH = "DISPATCH"
    HUMAN_GATE = "HUMAN_GATE"
    MERGE = "MERGE"


class WorkerTier(str, Enum):
    LUNA = "LUNA"
    TERRA = "TERRA"


class SourcePRState(str, Enum):
    OPEN = "OPEN"
    CLOSED = "CLOSED"
    MERGED = "MERGED"
    UNKNOWN = "UNKNOWN"


class CachedDecisionDisposition(str, Enum):
    REUSE = "REUSE"
    RECLASSIFY = "RECLASSIFY"
    INVALID = "INVALID"


def _optional_text(value: Any, label: str) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str):
        raise ValueError(f"{label} must be a string or null")
    return value.strip() or None


def _optional_positive_int(value: Any, label: str) -> int | None:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer or null")
    return value


def _key_list(value: Any, label: str) -> tuple[str, ...]:
    if value is None:
        value = []
    if not isinstance(value, list):
        raise ValueError(f"{label} must be a list")
    normalized: list[str] = []
    seen: set[str] = set()
    for raw in value:
        key = normalize_legacy_event_key(raw)
        if key and key not in seen:
            normalized.append(key)
            seen.add(key)
    return tuple(normalized)


@dataclass(frozen=True)
class ClassifierDecision:
    kind: DecisionKind
    lane: str | None = None
    pr_number: int | None = None
    objective: str | None = None
    stop_boundary: str | None = None
    reusable_evidence: str | None = None
    worker_tier: WorkerTier | None = None
    allowed_paths: tuple[str, ...] | None = None
    reason: str = ""
    human_message: str | None = None

    @classmethod
    def from_legacy_mapping(cls, raw: Any) -> "ClassifierDecision":
        if not isinstance(raw, Mapping):
            raise ValueError("classifier decision must be an object")
        try:
            kind = DecisionKind(str(raw.get("decision") or "NOOP").upper())
        except ValueError as exc:
            raise ValueError("unknown classifier decision kind") from exc

        tier_raw = raw.get("worker_tier")
        if tier_raw is None:
            tier = None
        else:
            try:
                tier = WorkerTier(str(tier_raw).strip().upper())
            except ValueError as exc:
                raise ValueError("unknown classifier worker tier") from exc

        allowed_raw = raw.get("allowed_paths")
        if allowed_raw is None:
            allowed = None
        else:
            if not isinstance(allowed_raw, list):
                raise ValueError("classifier allowed_paths must be a list or null")
            if any(not isinstance(path, str) or not path.strip() for path in allowed_raw):
                raise ValueError("classifier allowed_paths must contain non-empty strings")
            allowed = tuple(path.strip() for path in allowed_raw)

        return cls(
            kind=kind,
            lane=_optional_text(raw.get("lane"), "classifier lane"),
            pr_number=_optional_positive_int(
                raw.get("pr_number"), "classifier pr_number"
            ),
            objective=_optional_text(raw.get("objective"), "classifier objective"),
            stop_boundary=_optional_text(
                raw.get("stop_boundary"), "classifier stop_boundary"
            ),
            reusable_evidence=_optional_text(
                raw.get("reusable_evidence"), "classifier reusable_evidence"
            ),
            worker_tier=tier,
            allowed_paths=allowed,
            reason=str(raw.get("reason") or ""),
            human_message=_optional_text(
                raw.get("human_message"), "classifier human_message"
            ),
        )

    @property
    def effective_worker_tier(self) -> WorkerTier | None:
        if self.kind is not DecisionKind.DISPATCH:
            return self.worker_tier
        return self.worker_tier or WorkerTier.TERRA

    def execution_validation_error(self) -> str | None:
        if self.kind is DecisionKind.MERGE:
            if not self.lane:
                return "MERGE decision missing lane"
            return None

        if self.kind is DecisionKind.DISPATCH:
            if not self.lane:
                return "DISPATCH decision missing lane"
            if not self.objective or not self.stop_boundary:
                return "DISPATCH requires objective and stop_boundary"
            if (
                self.effective_worker_tier is WorkerTier.LUNA
                and not self.allowed_paths
            ):
                return "LUNA DISPATCH requires a non-empty allowed_paths scope"
        return None

    def as_dict(self) -> dict[str, Any]:
        return {
            "decision": self.kind.value,
            "lane": self.lane,
            "pr_number": self.pr_number,
            "objective": self.objective,
            "stop_boundary": self.stop_boundary,
            "reusable_evidence": self.reusable_evidence,
            "worker_tier": self.worker_tier.value if self.worker_tier else None,
            "allowed_paths": (
                list(self.allowed_paths) if self.allowed_paths is not None else None
            ),
            "reason": self.reason,
            "human_message": self.human_message,
        }


@dataclass(frozen=True)
class PendingDecisionRecord:
    decision: ClassifierDecision
    event_keys: tuple[str, ...] = ()
    authority_event_keys: tuple[str, ...] = ()
    ordinary_event_keys: tuple[str, ...] = ()
    task_issue_numbers: tuple[int, ...] = ()
    captured_at: str | None = None
    snapshot_main: str | None = None
    source_pr_head: str | None = None

    @classmethod
    def from_legacy_mapping(cls, raw: Any) -> "PendingDecisionRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("pending decision record must be an object")
        decision_raw = raw.get("decision")
        if not isinstance(decision_raw, Mapping):
            raise ValueError("pending decision record requires decision object")

        issues_raw = raw.get("task_issue_numbers", [])
        if issues_raw is None:
            issues_raw = []
        if not isinstance(issues_raw, list):
            raise ValueError("pending decision task_issue_numbers must be a list")
        issues: set[int] = set()
        for value in issues_raw:
            if isinstance(value, bool):
                raise ValueError("task issue number must be a positive integer")
            try:
                issue = int(value)
            except (TypeError, ValueError) as exc:
                raise ValueError("task issue number must be a positive integer") from exc
            if issue <= 0:
                raise ValueError("task issue number must be a positive integer")
            issues.add(issue)

        return cls(
            decision=ClassifierDecision.from_legacy_mapping(decision_raw),
            event_keys=_key_list(raw.get("event_keys", []), "pending decision event_keys"),
            authority_event_keys=_key_list(
                raw.get("authority_event_keys", []),
                "pending decision authority_event_keys",
            ),
            ordinary_event_keys=_key_list(
                raw.get("ordinary_event_keys", []),
                "pending decision ordinary_event_keys",
            ),
            task_issue_numbers=tuple(sorted(issues)),
            captured_at=_optional_text(
                raw.get("captured_at"), "pending decision captured_at"
            ),
            snapshot_main=_optional_text(
                raw.get("snapshot_main"), "pending decision snapshot_main"
            ),
            source_pr_head=_optional_text(
                raw.get("source_pr_head"), "pending decision source_pr_head"
            ),
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "decision": self.decision.as_dict(),
            "event_keys": list(self.event_keys),
            "authority_event_keys": list(self.authority_event_keys),
            "ordinary_event_keys": list(self.ordinary_event_keys),
            "task_issue_numbers": list(self.task_issue_numbers),
            "captured_at": self.captured_at,
            "snapshot_main": self.snapshot_main,
            "source_pr_head": self.source_pr_head,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class DecisionFreshnessObservation:
    current_main: str
    source_pr_state: SourcePRState = SourcePRState.UNKNOWN
    source_pr_head: str = ""

    def __post_init__(self) -> None:
        if not isinstance(self.current_main, str):
            raise ValueError("current_main must be a string")
        if not isinstance(self.source_pr_state, SourcePRState):
            raise ValueError("source_pr_state must be SourcePRState")
        if not isinstance(self.source_pr_head, str):
            raise ValueError("source_pr_head must be a string")


def cached_decision_is_current(
    record: PendingDecisionRecord,
    observation: DecisionFreshnessObservation,
) -> bool:
    """Mirror the current model-free cached-decision freshness predicate."""

    kind = record.decision.kind
    if kind in {
        DecisionKind.NOOP,
        DecisionKind.HUMAN_GATE,
        DecisionKind.MERGE,
    }:
        return True

    if kind is not DecisionKind.DISPATCH:
        return True

    if record.snapshot_main and record.snapshot_main != observation.current_main:
        return False

    if record.decision.pr_number is None:
        return True

    if not record.source_pr_head:
        return False
    if observation.source_pr_state is not SourcePRState.OPEN:
        return False
    return observation.source_pr_head == record.source_pr_head


def cached_decision_disposition(
    record: PendingDecisionRecord,
    observation: DecisionFreshnessObservation,
) -> CachedDecisionDisposition:
    """Return a pure reuse decision without executing or inventing remote work."""

    if not cached_decision_is_current(record, observation):
        return CachedDecisionDisposition.RECLASSIFY
    if record.decision.execution_validation_error() is not None:
        return CachedDecisionDisposition.INVALID
    return CachedDecisionDisposition.REUSE
