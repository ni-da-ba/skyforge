"""One-boundary-at-a-time production coordinator for Platform v2 R5C24."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum
from pathlib import Path
import subprocess
from typing import Callable, Iterable

from .classifier_provider import (
    ClassifierProvider,
    ClassifierRunStatus,
    ClassifierRunStore,
)
from .dormant_handoff_commit import (
    DormantCommitDisposition,
    DormantCommitOutcome,
    DormantHandoffCommitLedger,
    DormantHandoffCommitStore,
    advance_dormant_handoff_commit,
)
from .dormant_worker import (
    DormantWorkerDisposition,
    advance_dormant_admitted_worker,
)
from .external import ExternalProducerClaim
from .external_service import ExternalClaimStore
from .hosted_admission import (
    HostedAdmissionLedger,
    HostedAdmissionOutcome,
    HostedAdmissionStore,
    advance_hosted_task_admission,
)
from .hosted_classifier import (
    HostedClassifierDisposition,
    advance_hosted_classifier_proposal,
)
from .hosted_state import HostedStateStore
from .hosted_task_plan import (
    HostedTaskPlanLedger,
    HostedTaskPlanStatus,
    HostedTaskPlanStore,
    advance_claimed_task_preflight,
    claim_next_protected_task,
)
from .identity import canonical_digest
from .managed_pr_lifecycle import (
    ManagedLifecycleDisposition,
    advance_managed_pr_lifecycle,
)
from .production_execution import (
    HostedManagedHandoffLedger,
    HostedManagedHandoffStore,
    ProductionExecutionPermit,
    RemoteHandoffDisposition,
    advance_committed_remote_handoff,
)
from .provider_usage import (
    ProviderSpendKind,
    ProviderUsageStore,
)
from .quota import LocalBudgetObservation
from .task_event_composition import TaskAuthorityEventStore
from .worker_provider import WorkerProvider, WorkerRunStore


class ProductionCoordinatorDisposition(str, Enum):
    IDLE = "IDLE"
    ADVANCED = "ADVANCED"
    WAIT = "WAIT"
    BLOCKED = "BLOCKED"
    HUMAN_GATE = "HUMAN_GATE"
    TASK_COMPLETE = "TASK_COMPLETE"


@dataclass(frozen=True)
class ProductionCoordinatorResult:
    disposition: ProductionCoordinatorDisposition
    phase: str
    reason: str
    event_id: str = ""

    @property
    def digest(self) -> str:
        return canonical_digest({
            "disposition":self.disposition.value,
            "phase":self.phase,
            "reason":self.reason,
            "event_id":self.event_id,
        })


def _projected_external_claims(root: Path) -> tuple[ExternalProducerClaim,...]:
    durable=ExternalClaimStore.for_root(root).load()
    if durable.claims:
        return durable.claims
    projection=HostedStateStore.for_root(root).load().legacy_projection or {}
    raw=projection.get("external_claims") or []
    if not isinstance(raw,list):
        raise ValueError("projected external_claims must be list")
    return tuple(ExternalProducerClaim.from_legacy_mapping(v) for v in raw)


def _usage_budget(
    root: Path,
    *,
    day: str,
    kind: ProviderSpendKind,
    spend_id: str,
    limit: int,
) -> tuple[LocalBudgetObservation,bool]:
    store=ProviderUsageStore.for_root(root)
    ledger=store.load()
    budget=ledger.budget_for_spend(
        day=day,kind=kind,spend_id=spend_id,daily_limit=limit
    )
    existing=any(
        r.day==day and r.kind is kind and r.spend_id==spend_id
        for r in ledger.reservations
    )
    if existing:
        return budget,True
    updated,created=store.reserve(
        day=day,kind=kind,spend_id=spend_id,daily_limit=limit
    )
    return budget,created


def _today(day_provider: Callable[[],str] | None) -> str:
    if day_provider is not None:
        value=str(day_provider())
    else:
        value=datetime.now(timezone.utc).date().isoformat()
    if len(value)!=10:
        raise ValueError("day provider must return YYYY-MM-DD")
    return value


class ProductionCoordinator:
    def __init__(
        self,
        *,
        root: Path,
        repo: str,
        permit: ProductionExecutionPermit,
        trusted_actors: Iterable[str],
        classifier_provider: ClassifierProvider,
        worker_provider: WorkerProvider,
        classifier_daily_limit: int,
        worker_daily_limit: int,
        runner=subprocess.run,
        day_provider: Callable[[],str] | None = None,
    ) -> None:
        self.root=Path(root).resolve()
        self.repo=str(repo)
        self.permit=permit
        self.trusted_actors=tuple(trusted_actors)
        self.classifier_provider=classifier_provider
        self.worker_provider=worker_provider
        self.classifier_daily_limit=int(classifier_daily_limit)
        self.worker_daily_limit=int(worker_daily_limit)
        if self.classifier_daily_limit < 1 or self.worker_daily_limit < 1:
            raise ValueError("provider daily limits must be positive")
        self.runner=runner
        self.day_provider=day_provider

    def advance_once(self) -> ProductionCoordinatorResult:
        root=self.root
        plan_store=HostedTaskPlanStore.for_root(root)
        plan_ledger=plan_store.load()
        plan=plan_ledger.active
        external=_projected_external_claims(root)

        if plan is None:
            state=HostedStateStore.for_root(root).load()
            authority=TaskAuthorityEventStore.for_root(root).load()
            result=claim_next_protected_task(
                ledger=plan_ledger,
                inbox=state.inbox,
                authority_events=authority,
                external_claims=external,
            )
            if result.ledger != plan_ledger:
                plan_store.save(result.ledger)
            if result.disposition.value == "CLAIMED":
                return ProductionCoordinatorResult(
                    ProductionCoordinatorDisposition.ADVANCED,
                    "TASK_CLAIM",
                    result.reason,
                    result.ledger.active.event_id,
                )
            return ProductionCoordinatorResult(
                ProductionCoordinatorDisposition.IDLE,
                "TASK_CLAIM",
                result.reason,
            )

        event_id=plan.event_id
        authority=TaskAuthorityEventStore.for_root(root).load()

        if plan.status in {HostedTaskPlanStatus.CLAIMED,HostedTaskPlanStatus.WAIT_REMOTE}:
            result=advance_claimed_task_preflight(
                ledger=plan_ledger,
                authority_events=authority,
                trusted_actors=self.trusted_actors,
                repo=self.repo,
                runner=self.runner,
            )
            if result.ledger != plan_ledger:
                plan_store.save(result.ledger)
            if result.disposition.value == "READY_FOR_CLASSIFIER":
                disposition=ProductionCoordinatorDisposition.ADVANCED
            elif result.disposition.value == "WAIT_REMOTE":
                disposition=ProductionCoordinatorDisposition.WAIT
            else:
                disposition=ProductionCoordinatorDisposition.BLOCKED
            return ProductionCoordinatorResult(disposition,"PREFLIGHT",result.reason,event_id)

        if plan.status is HostedTaskPlanStatus.BLOCKED:
            return ProductionCoordinatorResult(
                ProductionCoordinatorDisposition.BLOCKED,
                "PREFLIGHT",
                plan.reason,
                event_id,
            )

        assert plan.seed is not None
        request_id=plan.seed.classifier_request.request_id
        classifier=ClassifierRunStore.for_root(root).load().get(request_id)
        if classifier is None or classifier.status not in {
            ClassifierRunStatus.COMPLETE,
            ClassifierRunStatus.FAILED,
            ClassifierRunStatus.INTERRUPTED,
        }:
            day=_today(self.day_provider)
            budget,reserved=_usage_budget(
                root,
                day=day,
                kind=ProviderSpendKind.CLASSIFIER,
                spend_id=request_id,
                limit=self.classifier_daily_limit,
            )
            if not reserved:
                return ProductionCoordinatorResult(
                    ProductionCoordinatorDisposition.WAIT,
                    "CLASSIFIER",
                    "classifier daily provider budget is exhausted",
                    event_id,
                )
            result=advance_hosted_classifier_proposal(
                plan_ledger=plan_ledger,
                root=root,
                provider=self.classifier_provider,
                provider_quota=None,
                local_budget=budget,
            )
            if result.disposition in {
                HostedClassifierDisposition.COMPLETE,
                HostedClassifierDisposition.ALREADY_COMPLETE,
            }:
                disposition=ProductionCoordinatorDisposition.ADVANCED
            elif result.disposition is HostedClassifierDisposition.QUOTA_BLOCKED:
                disposition=ProductionCoordinatorDisposition.WAIT
            else:
                disposition=ProductionCoordinatorDisposition.BLOCKED
            return ProductionCoordinatorResult(disposition,"CLASSIFIER",result.reason,event_id)

        if classifier.status is not ClassifierRunStatus.COMPLETE:
            return ProductionCoordinatorResult(
                ProductionCoordinatorDisposition.BLOCKED,
                "CLASSIFIER",
                f"classifier run is {classifier.status.value}; explicit recovery is required",
                event_id,
            )

        admission_store=HostedAdmissionStore.for_root(root)
        admission_ledger=admission_store.load()
        admission=admission_ledger.record
        if admission is None:
            day=_today(self.day_provider)
            usage=ProviderUsageStore.for_root(root).load()
            worker_budget=usage.budget(
                day=day,
                kind=ProviderSpendKind.WORKER,
                daily_limit=self.worker_daily_limit,
            )
            result=advance_hosted_task_admission(
                root=root,
                plan_ledger=plan_ledger,
                trusted_actors=self.trusted_actors,
                repo=self.repo,
                active_external_claims=external,
                provider_quota=None,
                local_budget=worker_budget,
                attempt_number=1,
                runner=self.runner,
            )
            if result.ledger.record is None:
                disposition=ProductionCoordinatorDisposition.WAIT
            elif result.ledger.record.outcome is HostedAdmissionOutcome.ADMITTED:
                disposition=ProductionCoordinatorDisposition.ADVANCED
            else:
                disposition=ProductionCoordinatorDisposition.BLOCKED
            return ProductionCoordinatorResult(disposition,"ADMISSION",result.reason,event_id)

        if admission.outcome is not HostedAdmissionOutcome.ADMITTED:
            return ProductionCoordinatorResult(
                ProductionCoordinatorDisposition.BLOCKED,
                "ADMISSION",
                admission.reason,
                event_id,
            )
        assert admission.attempt is not None

        worker=WorkerRunStore.for_root(root).load().find_attempt(admission.attempt.attempt_id)
        if worker is None:
            day=_today(self.day_provider)
            budget,reserved=_usage_budget(
                root,
                day=day,
                kind=ProviderSpendKind.WORKER,
                spend_id=admission.attempt.attempt_id,
                limit=self.worker_daily_limit,
            )
            if not reserved:
                return ProductionCoordinatorResult(
                    ProductionCoordinatorDisposition.WAIT,
                    "WORKER",
                    "worker daily provider budget is exhausted",
                    event_id,
                )
            result=advance_dormant_admitted_worker(
                root=root,
                provider=self.worker_provider,
                provider_quota=None,
                local_budget=budget,
            )
            if result.disposition in {
                DormantWorkerDisposition.HANDOFF_READY,
                DormantWorkerDisposition.ALREADY_READY,
            }:
                disposition=ProductionCoordinatorDisposition.ADVANCED
            elif result.disposition is DormantWorkerDisposition.QUOTA_BLOCKED:
                disposition=ProductionCoordinatorDisposition.WAIT
            else:
                disposition=ProductionCoordinatorDisposition.BLOCKED
            return ProductionCoordinatorResult(disposition,"WORKER",result.reason,event_id)

        if worker.status.value != "HANDOFF_READY":
            return ProductionCoordinatorResult(
                ProductionCoordinatorDisposition.BLOCKED,
                "WORKER",
                f"worker run is {worker.status.value}; explicit recovery is required",
                event_id,
            )

        commit_store=DormantHandoffCommitStore.for_root(root)
        commit=commit_store.load().record
        if commit is None:
            result=advance_dormant_handoff_commit(root=root)
            if result.ledger.record is None:
                disposition=ProductionCoordinatorDisposition.BLOCKED
            elif result.ledger.record.outcome is DormantCommitOutcome.COMMITTED:
                disposition=ProductionCoordinatorDisposition.ADVANCED
            elif result.ledger.record.outcome is DormantCommitOutcome.NO_CHANGE:
                disposition=ProductionCoordinatorDisposition.BLOCKED
            else:
                disposition=ProductionCoordinatorDisposition.BLOCKED
            return ProductionCoordinatorResult(disposition,"LOCAL_COMMIT",result.reason,event_id)

        if commit.outcome is not DormantCommitOutcome.COMMITTED:
            return ProductionCoordinatorResult(
                ProductionCoordinatorDisposition.BLOCKED,
                "LOCAL_COMMIT",
                commit.reason,
                event_id,
            )

        handoff_store=HostedManagedHandoffStore.for_root(root)
        handoff=handoff_store.load().handoff
        if handoff is None:
            result=advance_committed_remote_handoff(
                root=root,repo=self.repo,runner=self.runner
            )
            if result.disposition in {
                RemoteHandoffDisposition.COMPLETE,
                RemoteHandoffDisposition.ALREADY_COMPLETE,
            }:
                disposition=ProductionCoordinatorDisposition.ADVANCED
            elif result.disposition is RemoteHandoffDisposition.NOT_READY:
                disposition=ProductionCoordinatorDisposition.WAIT
            else:
                disposition=ProductionCoordinatorDisposition.BLOCKED
            return ProductionCoordinatorResult(disposition,"REMOTE_HANDOFF",result.reason,event_id)

        lifecycle=advance_managed_pr_lifecycle(
            root=root,
            handoff=handoff,
            store=__import__(
                "scripts.orchestrator.v2.ordinary_effects",
                fromlist=["OrdinaryEffectStore"],
            ).OrdinaryEffectStore.for_root(root),
            runner=self.runner,
        )
        if lifecycle.disposition is ManagedLifecycleDisposition.COMPLETE:
            return ProductionCoordinatorResult(
                ProductionCoordinatorDisposition.TASK_COMPLETE,
                "MANAGED_PR",
                lifecycle.reason,
                event_id,
            )
        if lifecycle.disposition is ManagedLifecycleDisposition.NOT_ELIGIBLE:
            if handoff.auto_merge_eligible:
                return ProductionCoordinatorResult(
                    ProductionCoordinatorDisposition.WAIT,
                    "MANAGED_PR",
                    lifecycle.reason,
                    event_id,
                )
            return ProductionCoordinatorResult(
                ProductionCoordinatorDisposition.HUMAN_GATE,
                "MANAGED_PR",
                lifecycle.reason,
                event_id,
            )
        return ProductionCoordinatorResult(
            ProductionCoordinatorDisposition.BLOCKED,
            "MANAGED_PR",
            lifecycle.reason,
            event_id,
        )


def clear_completed_task_state(root: Path) -> None:
    """Clear per-task singletons only after the caller durably retires its inbox event."""
    root=Path(root).resolve()
    HostedTaskPlanStore.for_root(root).save(HostedTaskPlanLedger())
    HostedAdmissionStore.for_root(root).save(HostedAdmissionLedger())
    DormantHandoffCommitStore.for_root(root).save(DormantHandoffCommitLedger())
    HostedManagedHandoffStore.for_root(root).save(HostedManagedHandoffLedger())
