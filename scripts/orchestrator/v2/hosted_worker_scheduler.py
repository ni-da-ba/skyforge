"""Durable bounded hosted-worker scheduler for OPT-5C.

Only EXECUTING records reserve scarce worker slots. Other states are durable
observations/reasons used for restart-safe scheduling and operator visibility.
The provider concurrency policy is intentionally one backend constant.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum
from pathlib import Path
import threading
from typing import Any, Mapping

from .concurrency_claims import ConcurrencyClaimRecord, ConcurrencyClaimStore
from .hosted_admission import HostedAdmissionOutcome, HostedAdmissionStore
from .hosted_policy import HOSTED_WORKER_CONCURRENCY_LIMIT
from .identity import canonical_digest
from .state_store import JsonStateStoreAdapter
from .worker_provider import (
    WorkerRunStatus,
    WorkerRunStore,
    interrupt_running_worker_attempt,
)

HOSTED_WORKER_SCHEDULER_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "worker-scheduler.json"
)
HOSTED_WORKER_SCHEDULER_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "worker-scheduler.json.bak"
)
_SCHEDULER_LOCK = threading.RLock()


def _sha64(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if len(text) != 64 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase SHA-256 hex")
    return text


class HostedWorkerScheduleState(str, Enum):
    RUNNABLE = "RUNNABLE"
    EXECUTING = "EXECUTING"
    WAIT_LIMIT = "WAIT_LIMIT"
    WAIT_CLAIM = "WAIT_CLAIM"
    WAIT_QUOTA = "WAIT_QUOTA"
    RECOVERY_REQUIRED = "RECOVERY_REQUIRED"
    COMPLETED = "COMPLETED"
    RETIRED = "RETIRED"


class HostedWorkerReservationDisposition(str, Enum):
    RESERVED = "RESERVED"
    ALREADY_EXECUTING = "ALREADY_EXECUTING"
    LIMIT_REACHED = "LIMIT_REACHED"
    WAIT_CLAIM = "WAIT_CLAIM"
    RECOVERY_REQUIRED = "RECOVERY_REQUIRED"
    ALREADY_COMPLETED = "ALREADY_COMPLETED"
    NOT_RUNNABLE = "NOT_RUNNABLE"


@dataclass(frozen=True)
class HostedWorkerScheduleRecord:
    attempt_id: str
    admission_record_id: str
    state: HostedWorkerScheduleState
    reason: str
    claim_id: str = ""
    worker_run_id: str = ""
    tier: str = ""

    def __post_init__(self) -> None:
        object.__setattr__(self, "attempt_id", _sha64(self.attempt_id, "attempt_id"))
        object.__setattr__(
            self,
            "admission_record_id",
            _sha64(self.admission_record_id, "admission_record_id"),
        )
        if not isinstance(self.state, HostedWorkerScheduleState):
            raise ValueError("state must be HostedWorkerScheduleState")
        if not str(self.reason or "").strip():
            raise ValueError("scheduler reason is required")
        if self.claim_id:
            object.__setattr__(self, "claim_id", _sha64(self.claim_id, "claim_id"))
        if self.worker_run_id:
            object.__setattr__(
                self,
                "worker_run_id",
                _sha64(self.worker_run_id, "worker_run_id"),
            )

    @property
    def record_id(self) -> str:
        return canonical_digest(
            {
                "attempt_id": self.attempt_id,
                "admission_record_id": self.admission_record_id,
            }
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "attempt_id": self.attempt_id,
            "admission_record_id": self.admission_record_id,
            "state": self.state.value,
            "reason": self.reason,
            "claim_id": self.claim_id,
            "worker_run_id": self.worker_run_id,
            "tier": self.tier,
            "record_id": self.record_id,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "HostedWorkerScheduleRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("worker scheduler record must be object")
        value = cls(
            attempt_id=raw.get("attempt_id"),
            admission_record_id=raw.get("admission_record_id"),
            state=HostedWorkerScheduleState(str(raw.get("state") or "")),
            reason=str(raw.get("reason") or ""),
            claim_id=str(raw.get("claim_id") or ""),
            worker_run_id=str(raw.get("worker_run_id") or ""),
            tier=str(raw.get("tier") or ""),
        )
        if str(raw.get("record_id") or "") != value.record_id:
            raise ValueError("worker scheduler record identity mismatch")
        return value


@dataclass(frozen=True)
class HostedWorkerSchedulerLedger:
    records: tuple[HostedWorkerScheduleRecord, ...] = ()

    def __post_init__(self) -> None:
        by_attempt: dict[str, HostedWorkerScheduleRecord] = {}
        for value in self.records:
            if not isinstance(value, HostedWorkerScheduleRecord):
                raise ValueError("invalid worker scheduler record")
            prior = by_attempt.get(value.attempt_id)
            if prior is not None and prior != value:
                raise ValueError("multiple scheduler records share one attempt")
            by_attempt[value.attempt_id] = value
        object.__setattr__(
            self,
            "records",
            tuple(sorted(by_attempt.values(), key=lambda value: value.attempt_id)),
        )
        if len(self.executing) > HOSTED_WORKER_CONCURRENCY_LIMIT:
            raise ValueError("worker scheduler exceeds authoritative concurrency limit")

    @property
    def executing(self) -> tuple[HostedWorkerScheduleRecord, ...]:
        return tuple(
            value
            for value in self.records
            if value.state is HostedWorkerScheduleState.EXECUTING
        )

    def get(self, attempt_id: str) -> HostedWorkerScheduleRecord | None:
        key = _sha64(attempt_id, "attempt_id")
        return next((value for value in self.records if value.attempt_id == key), None)

    def put(self, record: HostedWorkerScheduleRecord) -> "HostedWorkerSchedulerLedger":
        existing = self.get(record.attempt_id)
        if (
            existing is not None
            and existing.admission_record_id != record.admission_record_id
        ):
            raise ValueError("scheduler attempt changed admission identity")
        return HostedWorkerSchedulerLedger(
            tuple(
                record if value.attempt_id == record.attempt_id else value
                for value in self.records
            )
            + (() if existing is not None else (record,))
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "concurrency_limit": HOSTED_WORKER_CONCURRENCY_LIMIT,
            "records": [value.as_dict() for value in self.records],
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "HostedWorkerSchedulerLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid worker scheduler ledger")
        if raw.get("concurrency_limit") != HOSTED_WORKER_CONCURRENCY_LIMIT:
            raise ValueError("worker scheduler concurrency policy mismatch")
        values = raw.get("records")
        if not isinstance(values, list):
            raise ValueError("worker scheduler records must be a list")
        return cls(tuple(HostedWorkerScheduleRecord.from_mapping(v) for v in values))

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class HostedWorkerSchedulerStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "HostedWorkerSchedulerStore":
        root = Path(root).resolve()
        return cls(
            JsonStateStoreAdapter(
                path=root / HOSTED_WORKER_SCHEDULER_RELATIVE_PATH,
                backup_path=root / HOSTED_WORKER_SCHEDULER_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> HostedWorkerSchedulerLedger:
        return HostedWorkerSchedulerLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: HostedWorkerSchedulerLedger) -> HostedWorkerSchedulerLedger:
        self.adapter.save(ledger.as_dict())
        return ledger


@dataclass(frozen=True)
class HostedWorkerReservationResult:
    disposition: HostedWorkerReservationDisposition
    reason: str
    ledger: HostedWorkerSchedulerLedger
    record: HostedWorkerScheduleRecord | None = None


def _record_for_attempt(
    *,
    root: Path,
    attempt_id: str,
    state: HostedWorkerScheduleState,
    reason: str,
) -> HostedWorkerScheduleRecord:
    admissions = HostedAdmissionStore.for_root(root).load()
    admission = admissions.for_attempt(attempt_id)
    if (
        admission is None
        or admission.outcome is not HostedAdmissionOutcome.ADMITTED
        or admission.attempt is None
        or admission.worker_spec is None
    ):
        raise ValueError("scheduler attempt lacks admitted frozen worker authority")
    claim = ConcurrencyClaimStore.for_root(root).load().active_for_attempt(attempt_id)
    run = WorkerRunStore.for_root(root).load().find_attempt(attempt_id)
    return HostedWorkerScheduleRecord(
        attempt_id=attempt_id,
        admission_record_id=admission.record_id,
        state=state,
        reason=reason,
        claim_id=claim.claim_id if claim is not None else "",
        worker_run_id=run.run_id if run is not None else "",
        tier=admission.worker_spec.tier.value,
    )


def reserve_hosted_worker(
    *,
    root: Path,
    attempt_id: str,
) -> HostedWorkerReservationResult:
    root = Path(root).resolve()
    with _SCHEDULER_LOCK:
        store = HostedWorkerSchedulerStore.for_root(root)
        ledger = store.load()
        admissions = HostedAdmissionStore.for_root(root).load()
        admission = admissions.for_attempt(attempt_id)
        if (
            admission is None
            or admission.outcome is not HostedAdmissionOutcome.ADMITTED
            or admission.worker_spec is None
            or admission.attempt is None
        ):
            return HostedWorkerReservationResult(
                HostedWorkerReservationDisposition.NOT_RUNNABLE,
                "attempt lacks admitted frozen worker authority",
                ledger,
            )

        current = ledger.get(attempt_id)
        if current is not None and current.state is HostedWorkerScheduleState.EXECUTING:
            return HostedWorkerReservationResult(
                HostedWorkerReservationDisposition.ALREADY_EXECUTING,
                "attempt already owns a hosted worker execution slot",
                ledger,
                current,
            )

        claim = ConcurrencyClaimStore.for_root(root).load().active_for_attempt(attempt_id)
        expected = ConcurrencyClaimRecord.from_worker(admission.worker_spec)
        if claim is None or claim.claim_id != expected.claim_id:
            waiting = _record_for_attempt(
                root=root,
                attempt_id=attempt_id,
                state=HostedWorkerScheduleState.WAIT_CLAIM,
                reason="matching active OPT-5A concurrency claim is unavailable",
            )
            ledger = ledger.put(waiting)
            store.save(ledger)
            return HostedWorkerReservationResult(
                HostedWorkerReservationDisposition.WAIT_CLAIM,
                waiting.reason,
                ledger,
                waiting,
            )

        run = WorkerRunStore.for_root(root).load().find_attempt(attempt_id)
        if run is not None:
            if run.status is WorkerRunStatus.HANDOFF_READY:
                completed = _record_for_attempt(
                    root=root,
                    attempt_id=attempt_id,
                    state=HostedWorkerScheduleState.COMPLETED,
                    reason="worker handoff is already durably ready",
                )
                ledger = ledger.put(completed)
                store.save(ledger)
                return HostedWorkerReservationResult(
                    HostedWorkerReservationDisposition.ALREADY_COMPLETED,
                    completed.reason,
                    ledger,
                    completed,
                )
            if run.status in {
                WorkerRunStatus.RUNNING,
                WorkerRunStatus.INTERRUPTED,
                WorkerRunStatus.FAILED,
            }:
                recovery = _record_for_attempt(
                    root=root,
                    attempt_id=attempt_id,
                    state=HostedWorkerScheduleState.RECOVERY_REQUIRED,
                    reason="durable worker state requires reconciliation; provider replay forbidden",
                )
                ledger = ledger.put(recovery)
                store.save(ledger)
                return HostedWorkerReservationResult(
                    HostedWorkerReservationDisposition.RECOVERY_REQUIRED,
                    recovery.reason,
                    ledger,
                    recovery,
                )

        if len(ledger.executing) >= HOSTED_WORKER_CONCURRENCY_LIMIT:
            waiting = _record_for_attempt(
                root=root,
                attempt_id=attempt_id,
                state=HostedWorkerScheduleState.WAIT_LIMIT,
                reason="hosted worker concurrency limit is fully occupied",
            )
            ledger = ledger.put(waiting)
            store.save(ledger)
            return HostedWorkerReservationResult(
                HostedWorkerReservationDisposition.LIMIT_REACHED,
                waiting.reason,
                ledger,
                waiting,
            )

        executing = _record_for_attempt(
            root=root,
            attempt_id=attempt_id,
            state=HostedWorkerScheduleState.EXECUTING,
            reason="exact admitted attempt reserved one hosted worker slot",
        )
        ledger = ledger.put(executing)
        store.save(ledger)
        return HostedWorkerReservationResult(
            HostedWorkerReservationDisposition.RESERVED,
            executing.reason,
            ledger,
            executing,
        )


def update_hosted_worker_schedule(
    *,
    root: Path,
    attempt_id: str,
    state: HostedWorkerScheduleState,
    reason: str,
) -> HostedWorkerSchedulerLedger:
    root = Path(root).resolve()
    with _SCHEDULER_LOCK:
        store = HostedWorkerSchedulerStore.for_root(root)
        ledger = store.load()
        record = _record_for_attempt(
            root=root,
            attempt_id=attempt_id,
            state=state,
            reason=reason,
        )
        ledger = ledger.put(record)
        store.save(ledger)
        return ledger


def retire_hosted_worker_schedule(
    *,
    root: Path,
    attempt_id: str,
    admission_record_id: str,
    reason: str,
) -> HostedWorkerSchedulerLedger:
    """Retire exact scheduler identity without requiring live admission state.

    Completion cleanup may be replaying after admission retirement, so the durable
    completion identity is sufficient authority for this terminal scheduler transition.
    """
    root = Path(root).resolve()
    attempt_id = _sha64(attempt_id, "attempt_id")
    admission_record_id = _sha64(admission_record_id, "admission_record_id")
    with _SCHEDULER_LOCK:
        store = HostedWorkerSchedulerStore.for_root(root)
        ledger = store.load()
        current = ledger.get(attempt_id)
        if current is not None:
            if current.admission_record_id != admission_record_id:
                raise ValueError("scheduler retirement admission identity mismatch")
            retired = replace(
                current,
                state=HostedWorkerScheduleState.RETIRED,
                reason=reason,
            )
        else:
            retired = HostedWorkerScheduleRecord(
                attempt_id=attempt_id,
                admission_record_id=admission_record_id,
                state=HostedWorkerScheduleState.RETIRED,
                reason=reason,
            )
        ledger = ledger.put(retired)
        store.save(ledger)
        return ledger


def reconcile_hosted_worker_scheduler_after_restart(
    *,
    root: Path,
) -> HostedWorkerSchedulerLedger:
    """Recover persisted EXECUTING slots after process loss without provider replay."""

    root = Path(root).resolve()
    with _SCHEDULER_LOCK:
        store = HostedWorkerSchedulerStore.for_root(root)
        ledger = store.load()
        for current in tuple(ledger.executing):
            run = WorkerRunStore.for_root(root).load().find_attempt(current.attempt_id)
            if run is None or run.status is WorkerRunStatus.PREPARED:
                next_state = HostedWorkerScheduleState.RUNNABLE
                reason = "restart recovered pre-provider reservation as runnable"
            elif run.status is WorkerRunStatus.RUNNING:
                interrupt_running_worker_attempt(
                    root=root,
                    attempt_id=current.attempt_id,
                )
                next_state = HostedWorkerScheduleState.RECOVERY_REQUIRED
                reason = "restart observed RUNNING provider state; provider replay forbidden"
            elif run.status is WorkerRunStatus.HANDOFF_READY:
                next_state = HostedWorkerScheduleState.COMPLETED
                reason = "restart observed durable worker handoff"
            else:
                next_state = HostedWorkerScheduleState.RECOVERY_REQUIRED
                reason = "restart observed terminal/recovery worker state"
            ledger = ledger.put(
                _record_for_attempt(
                    root=root,
                    attempt_id=current.attempt_id,
                    state=next_state,
                    reason=reason,
                )
            )
        store.save(ledger)
        return ledger
