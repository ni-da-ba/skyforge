"""Dormant isolated worker-execution boundary for Platform v2 R5C18.

This module is intentionally disconnected from the hosted runtime. It consumes only a
durable R5C17 ADMITTED record, prepares the deterministic isolated worktree, and invokes
the accepted durable worker-provider state machine. It does not commit, push, create PRs,
execute remote effects, or acquire production writer authority.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from pathlib import Path

from .concurrency_claims import ConcurrencyClaimRecord, ConcurrencyClaimStore
from .hosted_admission import (
    HostedAdmissionOutcome,
    HostedAdmissionStore,
)
from .identity import canonical_digest
from .quota import (
    LocalBudgetObservation,
    ProviderQuotaDecision,
    QuotaAdmissionDecision,
    QuotaAdmissionDisposition,
    classify_quota_admission,
)
from .worker_provider import (
    WorkerAdvanceDisposition,
    WorkerAdvanceResult,
    WorkerProvider,
    WorkerProviderConfig,
    WorkerRunStatus,
    WorkerRunStore,
    advance_worker_run,
    provider_config_for_tier,
)
from .worker_workspace import WorkerWorkspaceManager


class DormantWorkerDisposition(str, Enum):
    NO_ADMISSION = "NO_ADMISSION"
    NOT_ADMITTED = "NOT_ADMITTED"
    QUOTA_BLOCKED = "QUOTA_BLOCKED"
    HANDOFF_READY = "HANDOFF_READY"
    ALREADY_READY = "ALREADY_READY"
    FAILED = "FAILED"
    RECOVERY_REQUIRED = "RECOVERY_REQUIRED"
    CONFLICT = "CONFLICT"


@dataclass(frozen=True)
class DormantWorkerResult:
    disposition: DormantWorkerDisposition
    reason: str
    admission_record_id: str = ""
    worker_run_id: str = ""
    quota: QuotaAdmissionDecision | None = None
    worker: WorkerAdvanceResult | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "admission_record_id": self.admission_record_id,
                "worker_run_id": self.worker_run_id,
                "quota_digest": self.quota.digest if self.quota else "",
                "worker_digest": self.worker.digest if self.worker else "",
            }
        )


def _map_worker(result: WorkerAdvanceResult) -> DormantWorkerDisposition:
    return {
        WorkerAdvanceDisposition.HANDOFF_READY: DormantWorkerDisposition.HANDOFF_READY,
        WorkerAdvanceDisposition.ALREADY_READY: DormantWorkerDisposition.ALREADY_READY,
        WorkerAdvanceDisposition.FAILED: DormantWorkerDisposition.FAILED,
        WorkerAdvanceDisposition.RECOVERY_REQUIRED: (
            DormantWorkerDisposition.RECOVERY_REQUIRED
        ),
    }[result.disposition]


def advance_dormant_admitted_worker(
    *,
    root: Path,
    provider: WorkerProvider,
    provider_quota: ProviderQuotaDecision | None,
    local_budget: LocalBudgetObservation,
    config: WorkerProviderConfig | None = None,
    workspace_runner=None,
    attempt_id: str | None = None,
) -> DormantWorkerResult:
    """Advance one frozen admitted worker locally, without any remote side effects."""

    root = Path(root).resolve()
    if not isinstance(local_budget, LocalBudgetObservation):
        raise ValueError("local_budget must be LocalBudgetObservation")
    if provider_quota is not None and not isinstance(
        provider_quota,
        ProviderQuotaDecision,
    ):
        raise ValueError("provider_quota must be ProviderQuotaDecision or null")

    admissions = HostedAdmissionStore.for_root(root).load()
    admission = (
        admissions.for_attempt(attempt_id)
        if attempt_id is not None
        else admissions.record
    )
    if admission is None:
        return DormantWorkerResult(
            DormantWorkerDisposition.NO_ADMISSION,
            "no durable hosted admission record exists",
        )
    if (
        admission.outcome is not HostedAdmissionOutcome.ADMITTED
        or admission.worker_spec is None
        or admission.attempt is None
        or admission.frozen_task is None
    ):
        return DormantWorkerResult(
            DormantWorkerDisposition.NOT_ADMITTED,
            "durable hosted admission does not authorize worker execution",
            admission_record_id=admission.record_id,
        )

    spec = admission.worker_spec
    claim = ConcurrencyClaimStore.for_root(root).load().active_for_attempt(
        spec.attempt_id
    )
    if claim is None:
        return DormantWorkerResult(
            DormantWorkerDisposition.CONFLICT,
            "admitted worker lacks active concurrency claim",
            admission_record_id=admission.record_id,
        )
    expected_claim = ConcurrencyClaimRecord.from_worker(spec)
    if claim.claim_id != expected_claim.claim_id:
        return DormantWorkerResult(
            DormantWorkerDisposition.CONFLICT,
            "active concurrency claim differs from admitted frozen worker",
            admission_record_id=admission.record_id,
        )

    run_store = WorkerRunStore.for_root(root)
    ledger = run_store.load()
    current = ledger.find_attempt(spec.attempt_id)

    manager_kwargs = {"root": root}
    if workspace_runner is not None:
        manager_kwargs["runner"] = workspace_runner
    manager = WorkerWorkspaceManager(**manager_kwargs)
    expected_worktree = manager.expected_path(spec)

    cfg = config or provider_config_for_tier(spec.tier)

    if current is not None:
        if current.spec.digest != spec.digest:
            return DormantWorkerResult(
                DormantWorkerDisposition.CONFLICT,
                "durable worker run spec differs from admitted frozen worker identity",
                admission_record_id=admission.record_id,
                worker_run_id=current.run_id,
            )
        if Path(current.worktree).resolve() != expected_worktree:
            return DormantWorkerResult(
                DormantWorkerDisposition.CONFLICT,
                "durable worker run worktree differs from deterministic admitted identity",
                admission_record_id=admission.record_id,
                worker_run_id=current.run_id,
            )
        if current.config != cfg:
            return DormantWorkerResult(
                DormantWorkerDisposition.CONFLICT,
                "durable worker provider configuration differs from requested configuration",
                admission_record_id=admission.record_id,
                worker_run_id=current.run_id,
            )

        # PREPARED means provider spend has not begun and therefore the worktree must
        # still be clean. RUNNING/INTERRUPTED/FAILED/HANDOFF_READY may contain provider
        # edits; verify identity without resetting or requiring cleanliness.
        if current.status is WorkerRunStatus.PREPARED:
            manager.verify_for_run(spec, expected_worktree)
        else:
            manager.verify_recovery_identity(spec, expected_worktree)

        worker = advance_worker_run(
            spec=spec,
            worktree=expected_worktree,
            store=run_store,
            provider=provider,
            config=cfg,
        )
        return DormantWorkerResult(
            _map_worker(worker),
            worker.reason,
            admission_record_id=admission.record_id,
            worker_run_id=worker.record.run_id,
            worker=worker,
        )

    # There is no durable worker spend yet. Re-check provider/local quota before
    # creating even the local worktree, so a blocked attempt does not mutate Git state.
    quota = classify_quota_admission(provider_quota, local_budget)
    if quota.disposition in {
        QuotaAdmissionDisposition.BLOCK_PROVIDER,
        QuotaAdmissionDisposition.BLOCK_LOCAL,
    }:
        return DormantWorkerResult(
            DormantWorkerDisposition.QUOTA_BLOCKED,
            quota.reason,
            admission_record_id=admission.record_id,
            quota=quota,
        )

    workspace = manager.prepare(spec)
    worker = advance_worker_run(
        spec=spec,
        worktree=workspace.worktree,
        store=run_store,
        provider=provider,
        config=cfg,
    )
    return DormantWorkerResult(
        _map_worker(worker),
        worker.reason,
        admission_record_id=admission.record_id,
        worker_run_id=worker.record.run_id,
        quota=quota,
        worker=worker,
    )
