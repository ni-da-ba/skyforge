"""Explicit hosted classifier-proposal boundary for Platform v2 R5C16.

Consumes only an R5C15 READY_FOR_CLASSIFIER plan. Quota admission is evaluated before
provider spend. This service cannot admit dispatch, launch a worker, create a worktree,
acquire repository writer authority, or execute remote effects.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from pathlib import Path

from .classifier_provider import (
    ClassifierAdvanceDisposition,
    ClassifierAdvanceResult,
    ClassifierProvider,
    ClassifierProviderConfig,
    ClassifierRunStore,
    advance_classifier,
    classifier_provider_config,
)
from .hosted_task_plan import HostedTaskPlanLedger, HostedTaskPlanStatus
from .identity import canonical_digest
from .quota import (
    LocalBudgetObservation,
    ProviderQuotaDecision,
    QuotaAdmissionDecision,
    QuotaAdmissionDisposition,
    classify_quota_admission,
)


class HostedClassifierDisposition(str, Enum):
    NO_ACTIVE_PLAN = "NO_ACTIVE_PLAN"
    PLAN_NOT_READY = "PLAN_NOT_READY"
    QUOTA_BLOCKED = "QUOTA_BLOCKED"
    COMPLETE = "COMPLETE"
    ALREADY_COMPLETE = "ALREADY_COMPLETE"
    FAILED = "FAILED"
    RECOVERY_REQUIRED = "RECOVERY_REQUIRED"


@dataclass(frozen=True)
class HostedClassifierResult:
    disposition: HostedClassifierDisposition
    reason: str
    plan_id: str = ""
    request_id: str = ""
    run_id: str = ""
    quota: QuotaAdmissionDecision | None = None
    classifier: ClassifierAdvanceResult | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "plan_id": self.plan_id,
                "request_id": self.request_id,
                "run_id": self.run_id,
                "quota_digest": self.quota.digest if self.quota else "",
                "classifier_digest": (
                    self.classifier.digest if self.classifier is not None else ""
                ),
            }
        )


def advance_hosted_classifier_proposal(
    *,
    plan_ledger: HostedTaskPlanLedger,
    root: Path,
    provider: ClassifierProvider,
    provider_quota: ProviderQuotaDecision | None,
    local_budget: LocalBudgetObservation,
    config: ClassifierProviderConfig | None = None,
) -> HostedClassifierResult:
    """Advance exactly one hosted plan through durable classifier proposal only."""

    if not isinstance(plan_ledger, HostedTaskPlanLedger):
        raise ValueError("plan_ledger must be HostedTaskPlanLedger")
    if not isinstance(local_budget, LocalBudgetObservation):
        raise ValueError("local_budget must be LocalBudgetObservation")
    if provider_quota is not None and not isinstance(
        provider_quota, ProviderQuotaDecision
    ):
        raise ValueError("provider_quota must be ProviderQuotaDecision or null")

    plan = plan_ledger.active
    if plan is None:
        return HostedClassifierResult(
            HostedClassifierDisposition.NO_ACTIVE_PLAN,
            "no hosted task plan is active",
        )
    if (
        plan.status is not HostedTaskPlanStatus.READY_FOR_CLASSIFIER
        or plan.seed is None
    ):
        return HostedClassifierResult(
            HostedClassifierDisposition.PLAN_NOT_READY,
            "active hosted task plan is not classifier-ready",
            plan_id=plan.plan_id,
        )

    request = plan.seed.classifier_request
    quota = classify_quota_admission(provider_quota, local_budget)
    if quota.disposition in {
        QuotaAdmissionDisposition.BLOCK_PROVIDER,
        QuotaAdmissionDisposition.BLOCK_LOCAL,
    }:
        return HostedClassifierResult(
            HostedClassifierDisposition.QUOTA_BLOCKED,
            quota.reason,
            plan_id=plan.plan_id,
            request_id=request.request_id,
            quota=quota,
        )

    cfg = config or classifier_provider_config()
    store = ClassifierRunStore.for_root(Path(root))
    classifier = advance_classifier(
        request=request,
        root=Path(root),
        store=store,
        provider=provider,
        config=cfg,
    )

    mapping = {
        ClassifierAdvanceDisposition.COMPLETE: HostedClassifierDisposition.COMPLETE,
        ClassifierAdvanceDisposition.ALREADY_COMPLETE: (
            HostedClassifierDisposition.ALREADY_COMPLETE
        ),
        ClassifierAdvanceDisposition.FAILED: HostedClassifierDisposition.FAILED,
        ClassifierAdvanceDisposition.RECOVERY_REQUIRED: (
            HostedClassifierDisposition.RECOVERY_REQUIRED
        ),
    }
    disposition = mapping[classifier.disposition]
    return HostedClassifierResult(
        disposition,
        classifier.reason,
        plan_id=plan.plan_id,
        request_id=request.request_id,
        run_id=classifier.record.run_id,
        quota=quota,
        classifier=classifier,
    )
