"""Pure Platform v2 control-plane domain types.

This module is intentionally side-effect free and is not imported by the production
controller.  It defines only the mechanical state needed for future replay/shadow
comparison.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from .identity import AcceptanceIdentity, canonical_digest


class PRClass(str, Enum):
    DELIVERY = "DELIVERY"
    HUMAN_GATE = "HUMAN_GATE"
    PROTOTYPE = "PROTOTYPE"
    ARCHIVE = "ARCHIVE"


class CIState(str, Enum):
    UNKNOWN = "UNKNOWN"
    PENDING = "PENDING"
    PASS = "PASS"
    FAIL = "FAIL"


class TransitionKind(str, Enum):
    NOOP = "NOOP"
    WAIT = "WAIT"
    REPAIR_ELIGIBLE = "REPAIR_ELIGIBLE"
    HUMAN_GATE = "HUMAN_GATE"
    INVALIDATE_EVIDENCE = "INVALIDATE_EVIDENCE"
    RECONCILE = "RECONCILE"
    MERGE_ELIGIBLE = "MERGE_ELIGIBLE"


@dataclass(frozen=True)
class MechanicalSnapshot:
    """Minimal facts used by deterministic, model-free transition rules."""

    pr_class: PRClass = PRClass.DELIVERY
    active_pr: bool = False
    current_head_sha: str = ""
    evidence_sha: str = ""
    reviewed_sha: str = ""
    task_spec_hash: str = ""
    accepted_task_spec_hash: str = ""
    ci_state: CIState = CIState.UNKNOWN
    human_gate_pending: bool = False
    pending_worker: bool = False
    review_required: bool = False

    def acceptance_identity(self) -> AcceptanceIdentity:
        return AcceptanceIdentity(
            current_head_sha=self.current_head_sha,
            required_evidence_sha=self.evidence_sha,
            reviewed_sha=self.reviewed_sha,
            task_spec_hash=self.task_spec_hash,
            accepted_task_spec_hash=self.accepted_task_spec_hash,
        )


@dataclass(frozen=True)
class TransitionPlan:
    """One pure mechanical result; execution belongs to a later adapter layer."""

    kind: TransitionKind
    reason: str

    def as_dict(self) -> dict[str, str]:
        return {"kind": self.kind.value, "reason": self.reason}

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


def decide_mechanical(snapshot: MechanicalSnapshot) -> TransitionPlan:
    """Resolve only decisions that do not require semantic/model judgment.

    The function is conservative by design.  It never invents work or dispatches a
    worker.  Unknown or incomplete states become reconciliation rather than action.
    """
    if not snapshot.active_pr:
        return TransitionPlan(TransitionKind.NOOP, "no active PR to advance")

    if snapshot.pr_class in {PRClass.PROTOTYPE, PRClass.ARCHIVE}:
        return TransitionPlan(
            TransitionKind.NOOP,
            f"{snapshot.pr_class.value.lower()} PR is outside delivery automation",
        )

    head = snapshot.current_head_sha
    for label, candidate in (
        ("evidence", snapshot.evidence_sha),
        ("review", snapshot.reviewed_sha),
    ):
        if head and candidate and candidate != head:
            return TransitionPlan(
                TransitionKind.INVALIDATE_EVIDENCE,
                f"{label} SHA does not match current PR head",
            )

    if snapshot.pending_worker:
        return TransitionPlan(
            TransitionKind.WAIT,
            "worker still owns mutable handoff state",
        )

    if (
        snapshot.human_gate_pending
        or snapshot.review_required
        or snapshot.pr_class is PRClass.HUMAN_GATE
    ):
        return TransitionPlan(
            TransitionKind.HUMAN_GATE,
            "human/review authority remains outstanding",
        )

    if snapshot.ci_state is CIState.PENDING:
        return TransitionPlan(TransitionKind.WAIT, "required CI is still active")

    if snapshot.ci_state is CIState.FAIL:
        return TransitionPlan(
            TransitionKind.REPAIR_ELIGIBLE,
            "required CI failed on the current head",
        )

    if snapshot.ci_state is CIState.PASS:
        if snapshot.acceptance_identity().exact_match():
            return TransitionPlan(
                TransitionKind.MERGE_ELIGIBLE,
                "current head, evidence, review, and frozen task spec agree",
            )
        return TransitionPlan(
            TransitionKind.RECONCILE,
            "CI passed but exact acceptance identity is incomplete",
        )

    return TransitionPlan(
        TransitionKind.RECONCILE,
        "insufficient mechanical evidence for a safe transition",
    )
