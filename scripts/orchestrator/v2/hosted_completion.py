"""Restart-safe hosted task completion and reusable-state retirement for R5C27."""

from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum
from pathlib import Path
from typing import Any, Mapping

from .concurrency_claims import ConcurrencyClaimStore
from .dormant_handoff_commit import (
    DormantHandoffCommitLedger,
    DormantHandoffCommitStore,
)
from .hosted_admission import HostedAdmissionLedger, HostedAdmissionStore
from .hosted_state import HostedIngressState, HostedStateStore
from .hosted_task_plan import HostedTaskPlanLedger, HostedTaskPlanStore
from .hosted_worker_scheduler import retire_hosted_worker_schedule
from .identity import canonical_digest
from .inbox import InboxState
from .ordinary_pipeline import OrdinaryPipelineStore
from .state_store import JsonStateStoreAdapter
from .worker_provider import WorkerRunStore


HOSTED_COMPLETION_RELATIVE_PATH = Path(".skyforge-platform-v2") / "hosted-completions.json"
HOSTED_COMPLETION_BACKUP_RELATIVE_PATH = Path(".skyforge-platform-v2") / "hosted-completions.json.bak"


def _required(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label} is required")
    return value.strip()


def _positive(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be positive integer")
    return value


class HostedCompletionStatus(str, Enum):
    RECORDED = "RECORDED"
    CLEANED = "CLEANED"


class HostedCompletionDisposition(str, Enum):
    RECORDED = "RECORDED"
    ALREADY_RECORDED = "ALREADY_RECORDED"
    CLEANED = "CLEANED"
    ALREADY_CLEAN = "ALREADY_CLEAN"
    NO_PENDING = "NO_PENDING"


@dataclass(frozen=True)
class HostedCompletionRecord:
    plan_id: str
    event_id: str
    issue_number: int
    admission_record_id: str
    attempt_id: str
    worker_run_id: str
    handoff_digest: str
    lifecycle_digest: str
    status: HostedCompletionStatus

    def __post_init__(self) -> None:
        for name in (
            "plan_id",
            "event_id",
            "admission_record_id",
            "attempt_id",
            "worker_run_id",
            "handoff_digest",
            "lifecycle_digest",
        ):
            _required(getattr(self, name), name)
        _positive(self.issue_number, "issue_number")
        if not isinstance(self.status, HostedCompletionStatus):
            raise ValueError("status must be HostedCompletionStatus")

    @property
    def completion_id(self) -> str:
        return canonical_digest(
            {
                "plan_id": self.plan_id,
                "event_id": self.event_id,
                "admission_record_id": self.admission_record_id,
                "attempt_id": self.attempt_id,
                "worker_run_id": self.worker_run_id,
                "handoff_digest": self.handoff_digest,
            }
        )

    def as_dict(self) -> dict[str, object]:
        return {
            "plan_id": self.plan_id,
            "event_id": self.event_id,
            "issue_number": self.issue_number,
            "admission_record_id": self.admission_record_id,
            "attempt_id": self.attempt_id,
            "worker_run_id": self.worker_run_id,
            "handoff_digest": self.handoff_digest,
            "lifecycle_digest": self.lifecycle_digest,
            "status": self.status.value,
            "completion_id": self.completion_id,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "HostedCompletionRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("hosted completion record must be object")
        record = cls(
            plan_id=raw.get("plan_id"),
            event_id=raw.get("event_id"),
            issue_number=raw.get("issue_number"),
            admission_record_id=raw.get("admission_record_id"),
            attempt_id=raw.get("attempt_id"),
            worker_run_id=raw.get("worker_run_id"),
            handoff_digest=raw.get("handoff_digest"),
            lifecycle_digest=raw.get("lifecycle_digest"),
            status=HostedCompletionStatus(str(raw.get("status") or "")),
        )
        if raw.get("completion_id") != record.completion_id:
            raise ValueError("hosted completion identity mismatch")
        return record


@dataclass(frozen=True)
class HostedCompletionLedger:
    records: tuple[HostedCompletionRecord, ...] = ()

    @classmethod
    def from_mapping(cls, raw: Any) -> "HostedCompletionLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid hosted completion ledger")
        values = raw.get("records")
        if not isinstance(values, list):
            raise ValueError("hosted completion records must be list")
        records = tuple(HostedCompletionRecord.from_mapping(value) for value in values)
        ids = [record.completion_id for record in records]
        if len(set(ids)) != len(ids):
            raise ValueError("duplicate hosted completion identity")
        if sum(record.status is HostedCompletionStatus.RECORDED for record in records) > 1:
            raise ValueError("multiple hosted completions are pending cleanup")
        return cls(records)

    def as_dict(self) -> dict[str, object]:
        return {
            "schema_version": 1,
            "records": [record.as_dict() for record in self.records],
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())

    def get(self, completion_id: str) -> HostedCompletionRecord | None:
        matches = [record for record in self.records if record.completion_id == completion_id]
        if len(matches) > 1:
            raise ValueError("duplicate hosted completion identity")
        return matches[0] if matches else None

    def pending(self) -> HostedCompletionRecord | None:
        values = [record for record in self.records if record.status is HostedCompletionStatus.RECORDED]
        if len(values) > 1:
            raise ValueError("multiple hosted completions are pending cleanup")
        return values[0] if values else None

    def put(self, record: HostedCompletionRecord) -> "HostedCompletionLedger":
        current = self.get(record.completion_id)
        if current is None:
            return HostedCompletionLedger(self.records + (record,))
        return HostedCompletionLedger(
            tuple(
                record if value.completion_id == record.completion_id else value
                for value in self.records
            )
        )


@dataclass(frozen=True)
class HostedCompletionStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "HostedCompletionStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / HOSTED_COMPLETION_RELATIVE_PATH,
                backup_path=root / HOSTED_COMPLETION_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> HostedCompletionLedger:
        return HostedCompletionLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: HostedCompletionLedger) -> HostedCompletionLedger:
        self.adapter.save(ledger.as_dict())
        return ledger


@dataclass(frozen=True)
class HostedCompletionResult:
    disposition: HostedCompletionDisposition
    reason: str
    record: HostedCompletionRecord | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "record": self.record.as_dict() if self.record is not None else None,
            }
        )


def record_completed_managed_task(
    *,
    root: Path,
    handoff_digest: str,
    lifecycle_digest: str,
    plan_id: str | None = None,
) -> HostedCompletionResult:
    """Persist exact completion identity before any singleton state is retired."""

    root = Path(root).resolve()
    plans = HostedTaskPlanStore.for_root(root).load()
    plan = plans.get(plan_id) if plan_id is not None else plans.active
    admissions = HostedAdmissionStore.for_root(root).load()
    admission = admissions.for_plan(plan.plan_id) if plan is not None else None
    if plan is None or admission is None:
        raise RuntimeError("cannot record hosted completion without active plan/admission")
    if admission.plan_id != plan.plan_id:
        raise RuntimeError("hosted completion plan/admission identity mismatch")
    if admission.attempt is None:
        raise RuntimeError("hosted completion admission lacks attempt identity")
    worker = WorkerRunStore.for_root(root).load().find_attempt(admission.attempt.attempt_id)
    if worker is None:
        raise RuntimeError("hosted completion lacks durable worker run")

    handoffs = [
        value
        for value in OrdinaryPipelineStore.for_root(root).load().reconstructible_managed_handoffs()
        if value.scope.attempt_id == admission.attempt.attempt_id
    ]
    if len(handoffs) != 1 or handoffs[0].digest != handoff_digest:
        raise RuntimeError("hosted completion managed handoff identity mismatch")

    record = HostedCompletionRecord(
        plan_id=plan.plan_id,
        event_id=plan.event_id,
        issue_number=plan.issue_number,
        admission_record_id=admission.record_id,
        attempt_id=admission.attempt.attempt_id,
        worker_run_id=worker.run_id,
        handoff_digest=handoff_digest,
        lifecycle_digest=_required(lifecycle_digest, "lifecycle_digest"),
        status=HostedCompletionStatus.RECORDED,
    )
    store = HostedCompletionStore.for_root(root)
    ledger = store.load()
    current = ledger.get(record.completion_id)
    if current is not None:
        return HostedCompletionResult(
            HostedCompletionDisposition.ALREADY_RECORDED,
            "hosted task completion identity is already durable",
            current,
        )
    if ledger.pending() is not None:
        raise RuntimeError("another hosted completion still requires cleanup")
    store.save(ledger.put(record))
    return HostedCompletionResult(
        HostedCompletionDisposition.RECORDED,
        "hosted managed task completion durably recorded before cleanup",
        record,
    )


def _complete_ingress_event(state: HostedIngressState, event_id: str) -> HostedIngressState:
    inbox = state.inbox
    completed = list(inbox.completed_authority_event_keys)
    if event_id not in completed:
        completed.append(event_id)
    next_inbox = InboxState(
        pending_events=tuple(
            event for event in inbox.pending_events if event.event_id != event_id
        ),
        retired_event_keys=inbox.retired_event_keys,
        completed_authority_event_keys=tuple(completed),
        owned_event_keys=tuple(
            key for key in inbox.owned_event_keys if key != event_id
        ),
    )
    return HostedIngressState(
        inbox=next_inbox,
        seen_deliveries=state.seen_deliveries,
        legacy_projection=state.legacy_projection,
        accepted_deliveries=state.accepted_deliveries,
        rejected_controls=state.rejected_controls,
    )


def advance_hosted_completion_cleanup(*, root: Path) -> HostedCompletionResult:
    """Idempotently retire one completed task and reopen the singleton runtime slots."""

    root = Path(root).resolve()
    store = HostedCompletionStore.for_root(root)
    ledger = store.load()
    pending = ledger.pending()
    if pending is None:
        return HostedCompletionResult(
            HostedCompletionDisposition.NO_PENDING,
            "no hosted completion requires cleanup",
        )

    state_store = HostedStateStore.for_root(root)
    state = state_store.load()
    state_store.save(_complete_ingress_event(state, pending.event_id))

    commit_store = DormantHandoffCommitStore.for_root(root)
    commits = commit_store.load()
    commit = commits.for_attempt(pending.attempt_id)
    if commit is not None:
        if commit.admission_record_id != pending.admission_record_id:
            raise RuntimeError("completion cleanup found mismatched dormant commit identity")
        commit_store.save(commits.remove_attempt(pending.attempt_id))

    # Retire scheduler ownership while exact admission identity is still available.
    # This is attempt-scoped and leaves unrelated executing/waiting workers untouched.
    retire_hosted_worker_schedule(
        root=root,
        attempt_id=pending.attempt_id,
        admission_record_id=pending.admission_record_id,
        reason="completed hosted workflow retired exact scheduler attempt",
    )

    admission_store = HostedAdmissionStore.for_root(root)
    admissions = admission_store.load()
    admission = admissions.for_plan(pending.plan_id)
    if admission is not None:
        if admission.record_id != pending.admission_record_id:
            raise RuntimeError("completion cleanup found mismatched admission identity")
        admission_store.save(admissions.remove_plan(pending.plan_id))

    plan_store = HostedTaskPlanStore.for_root(root)
    plans = plan_store.load()
    plan = plans.get(pending.plan_id)
    if plan is not None:
        plan_store.save(plans.remove(pending.plan_id))

    concurrency = ConcurrencyClaimStore.for_root(root)
    claim = concurrency.load().active_for_attempt(pending.attempt_id)
    if claim is not None:
        concurrency.retire(pending.attempt_id)

    cleaned = replace(pending, status=HostedCompletionStatus.CLEANED)
    store.save(store.load().put(cleaned))
    return HostedCompletionResult(
        HostedCompletionDisposition.CLEANED,
        "completed hosted task retired without altering unrelated workflow authority",
        cleaned,
    )
