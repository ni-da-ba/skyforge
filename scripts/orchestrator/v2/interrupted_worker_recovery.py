"""Explicit recovery for a clean interrupted hosted worker attempt.

This boundary is intentionally operator-only. It preserves the immutable worker-run
record/worktree as forensic evidence, retires exact execution ownership, and retires
the signed task event without marking it completed. A later signed task revision may
then produce a distinct attempt identity.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum
from pathlib import Path
import subprocess
from typing import Any, Mapping

from .concurrency_claims import ConcurrencyClaimStore
from .dormant_handoff_commit import DormantHandoffCommitStore
from .hosted_admission import HostedAdmissionOutcome, HostedAdmissionStore
from .hosted_completion import HostedCompletionStore
from .hosted_state import HostedStateStore
from .hosted_task_plan import HostedTaskPlanStore
from .hosted_worker_scheduler import (
    HostedWorkerScheduleState,
    HostedWorkerSchedulerStore,
    retire_hosted_worker_schedule,
)
from .identity import canonical_digest
from .state_store import JsonStateStoreAdapter
from .task_event_composition import TaskAuthorityEventStore
from .worker_provider import (
    WorkerRunStatus,
    WorkerRunStore,
    interrupt_running_worker_attempt,
)


RECOVERY_RELATIVE_PATH = Path(".skyforge-platform-v2") / "interrupted-worker-recoveries.json"
RECOVERY_BACKUP_RELATIVE_PATH = Path(".skyforge-platform-v2") / "interrupted-worker-recoveries.json.bak"


def _required(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


def _sha64(value: Any, label: str) -> str:
    text = _required(value, label)
    if len(text) != 64 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase SHA-256 hex")
    return text


class InterruptedWorkerRecoveryStatus(str, Enum):
    PREPARED = "PREPARED"
    RECOVERED = "RECOVERED"


@dataclass(frozen=True)
class InterruptedWorkerRecoveryRecord:
    attempt_id: str
    plan_id: str
    admission_record_id: str
    event_id: str
    issue_number: int
    worker_run_id: str
    worktree: str
    branch: str
    base_sha: str
    status: InterruptedWorkerRecoveryStatus
    reason: str

    def __post_init__(self) -> None:
        for name in ("attempt_id", "plan_id", "admission_record_id", "worker_run_id"):
            object.__setattr__(self, name, _sha64(getattr(self, name), name))
        object.__setattr__(self, "event_id", _required(self.event_id, "event_id"))
        if isinstance(self.issue_number, bool) or self.issue_number <= 0:
            raise ValueError("issue_number must be positive")
        for name in ("worktree", "branch", "base_sha", "reason"):
            object.__setattr__(self, name, _required(getattr(self, name), name))
        if not isinstance(self.status, InterruptedWorkerRecoveryStatus):
            raise ValueError("status must be InterruptedWorkerRecoveryStatus")

    @property
    def recovery_id(self) -> str:
        return canonical_digest({
            "attempt_id": self.attempt_id,
            "plan_id": self.plan_id,
            "admission_record_id": self.admission_record_id,
            "event_id": self.event_id,
            "worker_run_id": self.worker_run_id,
        })

    def as_dict(self) -> dict[str, Any]:
        return {
            "attempt_id": self.attempt_id,
            "plan_id": self.plan_id,
            "admission_record_id": self.admission_record_id,
            "event_id": self.event_id,
            "issue_number": self.issue_number,
            "worker_run_id": self.worker_run_id,
            "worktree": self.worktree,
            "branch": self.branch,
            "base_sha": self.base_sha,
            "status": self.status.value,
            "reason": self.reason,
            "recovery_id": self.recovery_id,
        }

    @classmethod
    def from_mapping(cls, raw: Mapping[str, Any]) -> "InterruptedWorkerRecoveryRecord":
        value = cls(
            attempt_id=raw.get("attempt_id"),
            plan_id=raw.get("plan_id"),
            admission_record_id=raw.get("admission_record_id"),
            event_id=raw.get("event_id"),
            issue_number=int(raw.get("issue_number")),
            worker_run_id=raw.get("worker_run_id"),
            worktree=raw.get("worktree"),
            branch=raw.get("branch"),
            base_sha=raw.get("base_sha"),
            status=InterruptedWorkerRecoveryStatus(str(raw.get("status") or "")),
            reason=raw.get("reason"),
        )
        if raw.get("recovery_id") != value.recovery_id:
            raise ValueError("interrupted worker recovery identity mismatch")
        return value


@dataclass(frozen=True)
class InterruptedWorkerRecoveryLedger:
    records: tuple[InterruptedWorkerRecoveryRecord, ...] = ()

    def get_attempt(self, attempt_id: str) -> InterruptedWorkerRecoveryRecord | None:
        key = _sha64(attempt_id, "attempt_id")
        return next((value for value in self.records if value.attempt_id == key), None)

    def put(self, record: InterruptedWorkerRecoveryRecord) -> "InterruptedWorkerRecoveryLedger":
        current = self.get_attempt(record.attempt_id)
        if current is not None and current.recovery_id != record.recovery_id:
            raise ValueError("interrupted worker recovery attempt identity changed")
        return InterruptedWorkerRecoveryLedger(
            tuple(
                record if value.attempt_id == record.attempt_id else value
                for value in self.records
            ) + (() if current is not None else (record,))
        )

    def as_dict(self) -> dict[str, Any]:
        return {"schema_version": 1, "records": [value.as_dict() for value in self.records]}

    @classmethod
    def from_mapping(cls, raw: Any) -> "InterruptedWorkerRecoveryLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid interrupted worker recovery ledger")
        values = raw.get("records")
        if not isinstance(values, list):
            raise ValueError("interrupted worker recovery records must be list")
        return cls(tuple(InterruptedWorkerRecoveryRecord.from_mapping(value) for value in values))


@dataclass(frozen=True)
class InterruptedWorkerRecoveryStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "InterruptedWorkerRecoveryStore":
        root = Path(root).resolve()
        return cls(JsonStateStoreAdapter(
            path=root / RECOVERY_RELATIVE_PATH,
            backup_path=root / RECOVERY_BACKUP_RELATIVE_PATH,
        ))

    def load(self) -> InterruptedWorkerRecoveryLedger:
        return InterruptedWorkerRecoveryLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: InterruptedWorkerRecoveryLedger) -> None:
        self.adapter.save(ledger.as_dict())


@dataclass(frozen=True)
class InterruptedWorkerRecoveryInspection:
    blockers: tuple[str, ...]
    record: InterruptedWorkerRecoveryRecord | None

    @property
    def ready(self) -> bool:
        return not self.blockers and self.record is not None


def _git(path: Path, *args: str) -> str:
    completed = subprocess.run(
        ["git", "-C", str(path), *args],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return completed.stdout.strip()


def inspect_clean_interrupted_worker(
    *,
    root: Path,
    attempt_id: str,
) -> InterruptedWorkerRecoveryInspection:
    root = Path(root).resolve()
    attempt_id = _sha64(attempt_id, "attempt_id")
    recovery_store = InterruptedWorkerRecoveryStore.for_root(root)
    existing_recovery = recovery_store.load().get_attempt(attempt_id)

    worker = WorkerRunStore.for_root(root).load().find_attempt(attempt_id)
    if worker is None:
        return InterruptedWorkerRecoveryInspection(("worker run is unavailable",), existing_recovery)

    admission = HostedAdmissionStore.for_root(root).load().for_attempt(attempt_id)
    plan = (
        HostedTaskPlanStore.for_root(root).load().get(admission.plan_id)
        if admission is not None
        else None
    )

    if existing_recovery is not None:
        record = existing_recovery
    elif admission is not None and plan is not None:
        record = InterruptedWorkerRecoveryRecord(
            attempt_id=attempt_id,
            plan_id=plan.plan_id,
            admission_record_id=admission.record_id,
            event_id=plan.event_id,
            issue_number=plan.issue_number,
            worker_run_id=worker.run_id,
            worktree=worker.worktree,
            branch=worker.spec.branch,
            base_sha=worker.spec.base_sha,
            status=InterruptedWorkerRecoveryStatus.PREPARED,
            reason="clean interrupted worker recovery prepared",
        )
    else:
        return InterruptedWorkerRecoveryInspection(
            ("admission/plan identity is unavailable and no prepared recovery exists",),
            existing_recovery,
        )

    blockers: list[str] = []
    if worker.run_id != record.worker_run_id or worker.spec.attempt_id != record.attempt_id:
        blockers.append("worker run identity differs from prepared recovery")
    if worker.status not in {WorkerRunStatus.RUNNING, WorkerRunStatus.INTERRUPTED}:
        blockers.append(f"worker status is not recoverable: {worker.status.value}")

    worktree = Path(record.worktree)
    if not worktree.exists():
        blockers.append("worker worktree is missing")
    else:
        try:
            if _git(worktree, "rev-parse", "HEAD") != record.base_sha:
                blockers.append("worker worktree HEAD differs from frozen base")
            if _git(worktree, "branch", "--show-current") != record.branch:
                blockers.append("worker worktree branch differs from frozen branch")
            if _git(worktree, "status", "--porcelain", "--untracked-files=all"):
                blockers.append("worker worktree contains changes; automatic retirement forbidden")
        except (OSError, subprocess.CalledProcessError):
            blockers.append("worker worktree identity could not be verified")

    if admission is not None:
        if (
            admission.record_id != record.admission_record_id
            or admission.plan_id != record.plan_id
            or admission.event_id != record.event_id
            or admission.issue_number != record.issue_number
            or admission.outcome is not HostedAdmissionOutcome.ADMITTED
            or admission.attempt is None
            or admission.attempt.attempt_id != attempt_id
            or not admission.consume_attempt
        ):
            blockers.append("admission identity differs from prepared interrupted attempt")
    elif record.status is not InterruptedWorkerRecoveryStatus.RECOVERED:
        blockers.append("admission disappeared before recovery completed")

    if plan is not None:
        if plan.plan_id != record.plan_id or plan.event_id != record.event_id:
            blockers.append("task plan identity differs from prepared interrupted attempt")
    elif record.status is not InterruptedWorkerRecoveryStatus.RECOVERED:
        blockers.append("task plan disappeared before recovery completed")

    scheduler = HostedWorkerSchedulerStore.for_root(root).load().get(attempt_id)
    if scheduler is None:
        blockers.append("worker scheduler record is unavailable")
    elif scheduler.admission_record_id != record.admission_record_id:
        blockers.append("worker scheduler admission identity differs")
    elif scheduler.state not in {
        HostedWorkerScheduleState.EXECUTING,
        HostedWorkerScheduleState.RECOVERY_REQUIRED,
        HostedWorkerScheduleState.RETIRED,
    }:
        blockers.append(f"worker scheduler state is not recoverable: {scheduler.state.value}")

    claims = ConcurrencyClaimStore.for_root(root).load()
    active_claim = claims.active_for_attempt(attempt_id)
    retired_claim = claims.retired_for_attempt(attempt_id)
    if active_claim is None and retired_claim is None:
        blockers.append("concurrency claim identity is unavailable")

    if DormantHandoffCommitStore.for_root(root).load().for_attempt(attempt_id) is not None:
        blockers.append("worker already has dormant handoff commit evidence")
    if any(
        value.attempt_id == attempt_id
        for value in HostedCompletionStore.for_root(root).load().records
    ):
        blockers.append("worker already has hosted completion evidence")

    authority = TaskAuthorityEventStore.for_root(root).load().get(record.event_id)
    if authority is None or authority.reference.issue_number != record.issue_number:
        blockers.append("signed task authority provenance is unavailable")

    state = HostedStateStore.for_root(root).load()
    if record.event_id in state.inbox.completed_authority_event_keys:
        blockers.append("task authority is already marked completed")

    return InterruptedWorkerRecoveryInspection(tuple(blockers), record)


def recover_clean_interrupted_worker(
    *,
    root: Path,
    attempt_id: str,
) -> InterruptedWorkerRecoveryRecord:
    root = Path(root).resolve()
    inspection = inspect_clean_interrupted_worker(root=root, attempt_id=attempt_id)
    if not inspection.ready or inspection.record is None:
        raise RuntimeError("; ".join(inspection.blockers) or "interrupted worker recovery is not ready")

    store = InterruptedWorkerRecoveryStore.for_root(root)
    record = inspection.record
    if record.status is InterruptedWorkerRecoveryStatus.RECOVERED:
        return record
    store.save(store.load().put(record))

    # Fence the signed authority first. It is retired, never completed.
    state_store = HostedStateStore.for_root(root)
    state = state_store.load()
    retired = list(state.inbox.retired_event_keys)
    if record.event_id not in retired:
        retired.append(record.event_id)
    state_store.save(replace(
        state,
        inbox=replace(
            state.inbox,
            pending_events=tuple(
                event for event in state.inbox.pending_events
                if event.event_id != record.event_id
            ),
            owned_event_keys=tuple(
                key for key in state.inbox.owned_event_keys
                if key != record.event_id
            ),
            retired_event_keys=tuple(retired[-1024:]),
        ),
    ))

    interrupt_running_worker_attempt(root=root, attempt_id=record.attempt_id)
    retire_hosted_worker_schedule(
        root=root,
        attempt_id=record.attempt_id,
        admission_record_id=record.admission_record_id,
        reason="operator recovered clean interrupted worker without task completion",
    )
    ConcurrencyClaimStore.for_root(root).retire(record.attempt_id)

    admission_store = HostedAdmissionStore.for_root(root)
    admission_store.save(admission_store.load().remove_plan(record.plan_id))
    plan_store = HostedTaskPlanStore.for_root(root)
    plan_store.save(plan_store.load().remove(record.plan_id))

    recovered = replace(
        record,
        status=InterruptedWorkerRecoveryStatus.RECOVERED,
        reason=(
            "clean interrupted worker retired; immutable run/worktree preserved; "
            "task authority retired without completion"
        ),
    )
    store.save(store.load().put(recovered))
    return recovered
