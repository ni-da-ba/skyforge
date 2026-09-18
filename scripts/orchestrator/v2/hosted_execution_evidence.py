"""Nonproduction hosted-execution evidence evaluator for Platform v2 R5C21.

The evaluator is read-only. It binds the accepted durable state chain from hosted task
planning through the controller-owned bounded local commit and requires that no remote
effect has begun. It does not execute any provider, Git command, GitHub mutation, or
writer-authority transition.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from pathlib import Path

from .classifier_provider import ClassifierRunStatus, ClassifierRunStore
from .dormant_handoff_commit import (
    DormantCommitOutcome,
    DormantHandoffCommitStore,
)
from .hosted_admission import (
    HostedAdmissionOutcome,
    HostedAdmissionStore,
)
from .hosted_task_plan import HostedTaskPlanStatus, HostedTaskPlanStore
from .identity import canonical_digest
from .ordinary_effects import OrdinaryEffectStore
from .worker_provider import WorkerRunStatus, WorkerRunStore


class HostedExecutionEvidenceDisposition(str, Enum):
    BLOCKED = "BLOCKED"
    ACCEPTED = "ACCEPTED"


@dataclass(frozen=True)
class HostedExecutionEvidenceDecision:
    disposition: HostedExecutionEvidenceDisposition
    blockers: tuple[str, ...]
    accepted_main_sha: str = ""
    plan_id: str = ""
    classifier_run_id: str = ""
    admission_record_id: str = ""
    worker_run_id: str = ""
    handoff_record_id: str = ""
    result_head_sha: str = ""
    changed_paths: tuple[str, ...] = ()
    remote_effect_ledger_digest: str = ""

    def __post_init__(self) -> None:
        if not isinstance(self.disposition, HostedExecutionEvidenceDisposition):
            raise ValueError("disposition must be HostedExecutionEvidenceDisposition")
        if any(not isinstance(value, str) or not value.strip() for value in self.blockers):
            raise ValueError("blockers must contain non-empty strings")
        if self.disposition is HostedExecutionEvidenceDisposition.ACCEPTED and self.blockers:
            raise ValueError("accepted hosted execution evidence cannot have blockers")

    def as_dict(self) -> dict[str, object]:
        return {
            "disposition": self.disposition.value,
            "blockers": list(self.blockers),
            "accepted_main_sha": self.accepted_main_sha,
            "plan_id": self.plan_id,
            "classifier_run_id": self.classifier_run_id,
            "admission_record_id": self.admission_record_id,
            "worker_run_id": self.worker_run_id,
            "handoff_record_id": self.handoff_record_id,
            "result_head_sha": self.result_head_sha,
            "changed_paths": list(self.changed_paths),
            "remote_effect_ledger_digest": self.remote_effect_ledger_digest,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


def evaluate_hosted_execution_evidence(
    root: Path,
) -> HostedExecutionEvidenceDecision:
    """Evaluate the exact durable nonproduction execution chain at *root*."""

    root = Path(root).resolve()
    blockers: list[str] = []

    plan = HostedTaskPlanStore.for_root(root).load().active
    if plan is None:
        blockers.append("hosted task plan is absent")
        return HostedExecutionEvidenceDecision(
            HostedExecutionEvidenceDisposition.BLOCKED,
            tuple(blockers),
        )
    if plan.status is not HostedTaskPlanStatus.READY_FOR_CLASSIFIER or plan.seed is None:
        blockers.append("hosted task plan is not READY_FOR_CLASSIFIER with exact seed")

    classifier = None
    if plan.seed is not None:
        classifier = ClassifierRunStore.for_root(root).load().get(
            plan.seed.classifier_request.request_id
        )
        if classifier is None:
            blockers.append("matching durable classifier run is absent")
        else:
            if classifier.status is not ClassifierRunStatus.COMPLETE:
                blockers.append("matching classifier run is not COMPLETE")
            if classifier.request != plan.seed.classifier_request:
                blockers.append("classifier request differs from exact hosted task seed")

    admission = HostedAdmissionStore.for_root(root).load().record
    if admission is None:
        blockers.append("hosted admission record is absent")
    else:
        if admission.outcome is not HostedAdmissionOutcome.ADMITTED:
            blockers.append("hosted admission outcome is not ADMITTED")
        if admission.plan_id != plan.plan_id:
            blockers.append("admission plan identity differs from hosted task plan")
        if admission.event_id != plan.event_id:
            blockers.append("admission event identity differs from hosted task plan")
        if admission.issue_number != plan.issue_number:
            blockers.append("admission issue identity differs from hosted task plan")
        if plan.seed is not None and admission.classifier_request_id != plan.seed.classifier_request.request_id:
            blockers.append("admission classifier request differs from hosted task seed")
        if classifier is not None:
            if admission.classifier_run_id != classifier.run_id:
                blockers.append("admission classifier run identity differs from durable classifier")
            if classifier.decision is None:
                blockers.append("complete classifier run lacks typed decision")
            elif admission.classifier_decision_digest != canonical_digest(
                classifier.decision.as_dict()
            ):
                blockers.append("admission classifier decision digest differs from durable classifier")
        if admission.attempt is None or admission.worker_spec is None or admission.frozen_task is None:
            blockers.append("admitted record lacks frozen task/attempt/worker identity")

    worker = None
    if admission is not None and admission.attempt is not None:
        worker = WorkerRunStore.for_root(root).load().find_attempt(
            admission.attempt.attempt_id
        )
        if worker is None:
            blockers.append("matching durable worker run is absent")
        else:
            if worker.status is not WorkerRunStatus.HANDOFF_READY:
                blockers.append("matching worker run is not HANDOFF_READY")
            if admission.worker_spec is None or worker.spec.digest != admission.worker_spec.digest:
                blockers.append("worker run spec differs from admitted frozen worker identity")

    handoff = DormantHandoffCommitStore.for_root(root).load().record
    if handoff is None:
        blockers.append("bounded local handoff commit record is absent")
    else:
        if handoff.outcome is not DormantCommitOutcome.COMMITTED:
            blockers.append("bounded local handoff outcome is not COMMITTED")
        if admission is None:
            blockers.append("handoff commit cannot be bound without admission record")
        else:
            if handoff.admission_record_id != admission.record_id:
                blockers.append("handoff admission identity differs from durable admission")
            if admission.attempt is not None and handoff.attempt_id != admission.attempt.attempt_id:
                blockers.append("handoff attempt identity differs from admitted attempt")
            if admission.worker_spec is not None:
                if handoff.branch != admission.worker_spec.branch:
                    blockers.append("handoff branch differs from admitted frozen worker")
                if handoff.base_sha != admission.worker_spec.base_sha:
                    blockers.append("handoff base SHA differs from admitted frozen worker")
        if worker is None:
            blockers.append("handoff commit cannot be bound without worker run")
        elif handoff.worker_run_id != worker.run_id:
            blockers.append("handoff worker-run identity differs from durable worker")

    effects = OrdinaryEffectStore.for_root(root).load()
    if effects.records:
        blockers.append("remote-effect ledger is non-empty; rehearsal crossed remote-effect boundary")

    accepted = not blockers
    return HostedExecutionEvidenceDecision(
        (
            HostedExecutionEvidenceDisposition.ACCEPTED
            if accepted
            else HostedExecutionEvidenceDisposition.BLOCKED
        ),
        tuple(blockers),
        accepted_main_sha=(
            admission.current_main if admission is not None else ""
        ),
        plan_id=plan.plan_id,
        classifier_run_id=classifier.run_id if classifier is not None else "",
        admission_record_id=admission.record_id if admission is not None else "",
        worker_run_id=worker.run_id if worker is not None else "",
        handoff_record_id=handoff.record_id if handoff is not None else "",
        result_head_sha=handoff.head_sha if handoff is not None else "",
        changed_paths=handoff.changed_paths if handoff is not None else (),
        remote_effect_ledger_digest=effects.digest,
    )
