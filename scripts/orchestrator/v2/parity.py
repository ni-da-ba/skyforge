"""Versioned legacy replay corpus loader and pure Platform v2 parity runner."""

from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any, Callable, Iterable

from .domain import CIState, MechanicalSnapshot, PRClass, TransitionKind, TransitionPlan, decide_mechanical
from .identity import canonical_digest


CORPUS_SCHEMA_VERSION = 1


@dataclass(frozen=True)
class LegacyParityCase:
    case_id: str
    source: str
    snapshot: MechanicalSnapshot
    expected_kind: TransitionKind

    def canonical_record(self) -> dict[str, Any]:
        return {
            "case_id": self.case_id,
            "source": self.source,
            "snapshot": {
                "pr_class": self.snapshot.pr_class.value,
                "active_pr": self.snapshot.active_pr,
                "current_head_sha": self.snapshot.current_head_sha,
                "evidence_sha": self.snapshot.evidence_sha,
                "reviewed_sha": self.snapshot.reviewed_sha,
                "task_spec_hash": self.snapshot.task_spec_hash,
                "accepted_task_spec_hash": self.snapshot.accepted_task_spec_hash,
                "ci_state": self.snapshot.ci_state.value,
                "human_gate_pending": self.snapshot.human_gate_pending,
                "pending_worker": self.snapshot.pending_worker,
                "review_required": self.snapshot.review_required,
            },
            "expected_kind": self.expected_kind.value,
        }


@dataclass(frozen=True)
class ParityResult:
    case_id: str
    expected_kind: TransitionKind
    actual_kind: TransitionKind
    transition_digest: str

    @property
    def matches(self) -> bool:
        return self.expected_kind is self.actual_kind


def _require_mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"{label} must be an object")
    return value


def _require_bool(mapping: dict[str, Any], key: str) -> bool:
    value = mapping.get(key)
    if not isinstance(value, bool):
        raise ValueError(f"snapshot.{key} must be a boolean")
    return value


def _require_text(mapping: dict[str, Any], key: str) -> str:
    value = mapping.get(key)
    if not isinstance(value, str):
        raise ValueError(f"snapshot.{key} must be a string")
    return value


def _parse_case(raw: Any) -> LegacyParityCase:
    record = _require_mapping(raw, "case")
    case_id = str(record.get("case_id") or "").strip()
    source = str(record.get("source") or "").strip()
    if not case_id:
        raise ValueError("case_id is required")
    if not source:
        raise ValueError(f"{case_id}: source provenance is required")

    raw_snapshot = _require_mapping(record.get("snapshot"), f"{case_id}.snapshot")
    try:
        pr_class = PRClass(_require_text(raw_snapshot, "pr_class"))
        ci_state = CIState(_require_text(raw_snapshot, "ci_state"))
        expected_kind = TransitionKind(str(record.get("expected_kind") or ""))
    except ValueError as exc:
        raise ValueError(f"{case_id}: unknown enum value") from exc

    snapshot = MechanicalSnapshot(
        pr_class=pr_class,
        active_pr=_require_bool(raw_snapshot, "active_pr"),
        current_head_sha=_require_text(raw_snapshot, "current_head_sha"),
        evidence_sha=_require_text(raw_snapshot, "evidence_sha"),
        reviewed_sha=_require_text(raw_snapshot, "reviewed_sha"),
        task_spec_hash=_require_text(raw_snapshot, "task_spec_hash"),
        accepted_task_spec_hash=_require_text(raw_snapshot, "accepted_task_spec_hash"),
        ci_state=ci_state,
        human_gate_pending=_require_bool(raw_snapshot, "human_gate_pending"),
        pending_worker=_require_bool(raw_snapshot, "pending_worker"),
        review_required=_require_bool(raw_snapshot, "review_required"),
    )
    return LegacyParityCase(
        case_id=case_id,
        source=source,
        snapshot=snapshot,
        expected_kind=expected_kind,
    )


def load_legacy_corpus(path: Path) -> tuple[LegacyParityCase, ...]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    root = _require_mapping(payload, "corpus")
    if root.get("schema_version") != CORPUS_SCHEMA_VERSION:
        raise ValueError("unsupported legacy replay corpus schema_version")
    raw_cases = root.get("cases")
    if not isinstance(raw_cases, list) or not raw_cases:
        raise ValueError("legacy replay corpus must contain at least one case")
    cases = tuple(_parse_case(raw) for raw in raw_cases)
    identifiers = [case.case_id for case in cases]
    if len(identifiers) != len(set(identifiers)):
        raise ValueError("legacy replay corpus contains duplicate case_id values")
    return cases


def corpus_digest(cases: Iterable[LegacyParityCase]) -> str:
    ordered = sorted(
        (case.canonical_record() for case in cases),
        key=lambda item: item["case_id"],
    )
    return canonical_digest(
        {"schema_version": CORPUS_SCHEMA_VERSION, "cases": ordered}
    )


def run_parity(
    cases: Iterable[LegacyParityCase],
    decider: Callable[[MechanicalSnapshot], TransitionPlan] = decide_mechanical,
) -> tuple[ParityResult, ...]:
    results: list[ParityResult] = []
    for case in cases:
        plan = decider(case.snapshot)
        results.append(
            ParityResult(
                case_id=case.case_id,
                expected_kind=case.expected_kind,
                actual_kind=plan.kind,
                transition_digest=plan.digest,
            )
        )
    return tuple(results)


def require_parity(results: Iterable[ParityResult]) -> None:
    failures = [result for result in results if not result.matches]
    if failures:
        detail = ", ".join(
            f"{result.case_id}: expected {result.expected_kind.value}, got {result.actual_kind.value}"
            for result in failures
        )
        raise AssertionError(f"Platform v2 replay parity failed: {detail}")
