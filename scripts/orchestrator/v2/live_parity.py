"""Deterministic live-shadow parity analysis for Platform v2 Release 3."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any, Mapping

from .decision import (
    CachedDecisionDisposition,
    DecisionFreshnessObservation,
    DecisionKind,
    PendingDecisionRecord,
    SourcePRState,
    cached_decision_disposition,
)
from .identity import canonical_digest


class LiveParityClassification(str, Enum):
    AGREE = "AGREE"
    STRICTER_V2_EVIDENCE_GAP = "STRICTER_V2_EVIDENCE_GAP"
    DIVERGENCE = "DIVERGENCE"
    NON_COMPARABLE = "NON_COMPARABLE"


@dataclass(frozen=True)
class LiveParitySample:
    lane: str
    pr_number: int
    legacy_kind: str
    legacy_decision_digest: str
    freshness: str
    v2_disposition: str
    v2_report_digest: str
    classification: LiveParityClassification
    reason: str

    def as_dict(self) -> dict[str, Any]:
        return {
            "lane": self.lane,
            "pr_number": self.pr_number,
            "legacy_kind": self.legacy_kind,
            "legacy_decision_digest": self.legacy_decision_digest,
            "freshness": self.freshness,
            "v2_disposition": self.v2_disposition,
            "v2_report_digest": self.v2_report_digest,
            "classification": self.classification.value,
            "reason": self.reason,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise ValueError(f"{label} must be an object")
    return value


def _positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer")
    return value


def _managed_observation(snapshot: Any) -> Mapping[str, Any]:
    root = _mapping(snapshot, "shadow snapshot")
    managed = _mapping(root.get("managed"), "shadow snapshot managed")
    return _mapping(managed.get("observation"), "shadow snapshot managed observation")


def _managed_report(report: Any) -> tuple[str, str]:
    root = _mapping(report, "shadow report")
    digest = str(root.get("digest") or "").strip()
    if not digest:
        raise ValueError("shadow report digest is required")
    report_body = _mapping(root.get("report"), "shadow report body")
    entries = report_body.get("entries")
    if not isinstance(entries, list):
        raise ValueError("shadow report entries must be a list")
    matches = [
        _mapping(entry, "shadow report entry")
        for entry in entries
        if isinstance(entry, Mapping) and entry.get("policy") == "managed_pr"
    ]
    if len(matches) != 1:
        raise ValueError("shadow report must contain exactly one managed_pr entry")
    disposition = str(matches[0].get("disposition") or "").strip()
    if not disposition:
        raise ValueError("managed_pr report disposition is required")
    return disposition, digest


def _acceptance_complete(observation: Mapping[str, Any]) -> bool:
    head = str(observation.get("current_head_sha") or "")
    evidence = str(observation.get("evidence_sha") or "")
    reviewed = str(observation.get("reviewed_sha") or "")
    task_spec = str(observation.get("task_spec_hash") or "")
    accepted_spec = str(observation.get("accepted_task_spec_hash") or "")
    return bool(
        head
        and evidence == head
        and reviewed == head
        and task_spec
        and accepted_spec == task_spec
    )


def _identity_matches(
    decision_kind: DecisionKind,
    *,
    decision_lane: str | None,
    decision_pr: int | None,
    lane: str,
    pr_number: int,
) -> bool:
    if decision_kind is DecisionKind.NOOP:
        if decision_lane is None and decision_pr is None:
            return False
    if decision_pr != pr_number:
        return False
    if decision_lane is None:
        return False
    return decision_lane.strip().lower() == lane.strip().lower()


def _non_comparable(
    *,
    lane: str,
    pr_number: int,
    legacy_kind: str,
    legacy_digest: str,
    freshness: str,
    v2_disposition: str,
    report_digest: str,
    reason: str,
) -> LiveParitySample:
    return LiveParitySample(
        lane=lane,
        pr_number=pr_number,
        legacy_kind=legacy_kind,
        legacy_decision_digest=legacy_digest,
        freshness=freshness,
        v2_disposition=v2_disposition,
        v2_report_digest=report_digest,
        classification=LiveParityClassification.NON_COMPARABLE,
        reason=reason,
    )


def analyze_live_parity(
    *,
    legacy_pending_decision: Any,
    current_main: str,
    snapshot: Any,
    report: Any,
) -> LiveParitySample:
    observation = _managed_observation(snapshot)
    lane = str(observation.get("lane") or "").strip()
    if not lane:
        raise ValueError("managed observation lane is required")
    pr_number = _positive_int(observation.get("pr_number"), "managed observation pr_number")
    active = observation.get("active_pr")
    if not isinstance(active, bool):
        raise ValueError("managed observation active_pr must be a boolean")
    current_head = str(observation.get("current_head_sha") or "")
    v2_disposition, report_digest = _managed_report(report)

    if legacy_pending_decision is None:
        return _non_comparable(
            lane=lane,
            pr_number=pr_number,
            legacy_kind="NONE",
            legacy_digest="",
            freshness="NONE",
            v2_disposition=v2_disposition,
            report_digest=report_digest,
            reason="legacy controller has no durable pending decision to compare",
        )

    try:
        record = PendingDecisionRecord.from_legacy_mapping(legacy_pending_decision)
    except ValueError as exc:
        return _non_comparable(
            lane=lane,
            pr_number=pr_number,
            legacy_kind="INVALID",
            legacy_digest=canonical_digest(legacy_pending_decision),
            freshness=CachedDecisionDisposition.INVALID.value,
            v2_disposition=v2_disposition,
            report_digest=report_digest,
            reason=f"legacy pending decision is malformed: {exc}",
        )

    current_main = str(current_main or "").strip()
    if not current_main:
        raise ValueError("current_main is required")

    freshness_observation = DecisionFreshnessObservation(
        current_main=current_main,
        source_pr_state=SourcePRState.OPEN if active else SourcePRState.UNKNOWN,
        source_pr_head=current_head,
    )
    freshness = cached_decision_disposition(record, freshness_observation)
    decision = record.decision

    if freshness is not CachedDecisionDisposition.REUSE:
        return _non_comparable(
            lane=lane,
            pr_number=pr_number,
            legacy_kind=decision.kind.value,
            legacy_digest=record.digest,
            freshness=freshness.value,
            v2_disposition=v2_disposition,
            report_digest=report_digest,
            reason="legacy pending decision is not currently reusable",
        )

    if not _identity_matches(
        decision.kind,
        decision_lane=decision.lane,
        decision_pr=decision.pr_number,
        lane=lane,
        pr_number=pr_number,
    ):
        return _non_comparable(
            lane=lane,
            pr_number=pr_number,
            legacy_kind=decision.kind.value,
            legacy_digest=record.digest,
            freshness=freshness.value,
            v2_disposition=v2_disposition,
            report_digest=report_digest,
            reason="legacy decision is not scoped to the same managed lane and PR",
        )

    expected = {
        DecisionKind.NOOP: "NOOP",
        DecisionKind.HUMAN_GATE: "HUMAN_GATE",
        DecisionKind.MERGE: "MERGE_ELIGIBLE",
        DecisionKind.DISPATCH: "REPAIR_ELIGIBLE",
    }[decision.kind]

    if v2_disposition == expected:
        return LiveParitySample(
            lane=lane,
            pr_number=pr_number,
            legacy_kind=decision.kind.value,
            legacy_decision_digest=record.digest,
            freshness=freshness.value,
            v2_disposition=v2_disposition,
            v2_report_digest=report_digest,
            classification=LiveParityClassification.AGREE,
            reason=f"legacy {decision.kind.value} and v2 {v2_disposition} are equivalent for this scoped sample",
        )

    if (
        decision.kind is DecisionKind.MERGE
        and v2_disposition == "RECONCILE"
        and not _acceptance_complete(observation)
    ):
        return LiveParitySample(
            lane=lane,
            pr_number=pr_number,
            legacy_kind=decision.kind.value,
            legacy_decision_digest=record.digest,
            freshness=freshness.value,
            v2_disposition=v2_disposition,
            v2_report_digest=report_digest,
            classification=LiveParityClassification.STRICTER_V2_EVIDENCE_GAP,
            reason="legacy MERGE lacks the exact reviewed-SHA/task-spec acceptance identity required by v2",
        )

    return LiveParitySample(
        lane=lane,
        pr_number=pr_number,
        legacy_kind=decision.kind.value,
        legacy_decision_digest=record.digest,
        freshness=freshness.value,
        v2_disposition=v2_disposition,
        v2_report_digest=report_digest,
        classification=LiveParityClassification.DIVERGENCE,
        reason=f"legacy {decision.kind.value} expected v2 {expected}, observed {v2_disposition}",
    )
