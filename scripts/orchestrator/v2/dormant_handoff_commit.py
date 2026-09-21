"""Dormant bounded local handoff-commit boundary for Platform v2 R5C19.

Consumes an admitted HANDOFF_READY worker run, validates its local delta, and creates at
most one controller-owned local commit. This module is intentionally disconnected from the
hosted runtime and has no push/GitHub/remote-effect capability.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Any, Mapping, Protocol

from .hosted_admission import HostedAdmissionOutcome, HostedAdmissionStore
from .identity import canonical_digest
from .state_store import JsonStateStoreAdapter
from .worker_provider import WorkerRunStatus, WorkerRunStore
from .worker_workspace import WorkerWorkspaceManager
from .workspace_commit import (
    WorkspaceCommitAdapter,
    WorkspaceCommitResult,
    WorkspaceCommitScope,
)


DORMANT_HANDOFF_COMMIT_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "dormant-handoff-commit.json"
)
DORMANT_HANDOFF_COMMIT_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "dormant-handoff-commit.json.bak"
)


def _required(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label} is required")
    return value.strip()


def _sha40(value: Any, label: str) -> str:
    text = _required(value, label)
    if len(text) != 40 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase 40-character Git SHA")
    return text


def _sha64(value: Any, label: str) -> str:
    text = _required(value, label)
    if len(text) != 64 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase SHA-256 hex")
    return text


def _paths(value: Any) -> tuple[str, ...]:
    if not isinstance(value, list):
        raise ValueError("changed_paths must be a list")
    normalized = tuple(sorted({str(path).strip().replace("\\", "/") for path in value}))
    if any(
        not path
        or path.startswith("/")
        or path.startswith("../")
        or "/../" in path
        or path in {".", ".."}
        for path in normalized
    ):
        raise ValueError("changed_paths must be normalized repository-relative paths")
    return normalized


class DormantCommitOutcome(str, Enum):
    COMMITTED = "COMMITTED"
    NO_CHANGE = "NO_CHANGE"
    BLOCKED = "BLOCKED"


class DormantCommitDisposition(str, Enum):
    RECORDED = "RECORDED"
    ALREADY_RECORDED = "ALREADY_RECORDED"
    NO_ADMISSION = "NO_ADMISSION"
    NOT_ADMITTED = "NOT_ADMITTED"
    WORKER_NOT_READY = "WORKER_NOT_READY"
    CONFLICT = "CONFLICT"


@dataclass(frozen=True)
class DormantHandoffCommitRecord:
    admission_record_id: str
    worker_run_id: str
    attempt_id: str
    branch: str
    base_sha: str
    outcome: DormantCommitOutcome
    reason: str
    head_sha: str = ""
    changed_paths: tuple[str, ...] = ()

    def __post_init__(self) -> None:
        for name in ("admission_record_id", "worker_run_id", "attempt_id"):
            object.__setattr__(self, name, _sha64(getattr(self, name), name))
        object.__setattr__(self, "branch", _required(self.branch, "branch"))
        object.__setattr__(self, "base_sha", _sha40(self.base_sha, "base_sha"))
        if not isinstance(self.outcome, DormantCommitOutcome):
            raise ValueError("outcome must be DormantCommitOutcome")
        object.__setattr__(self, "reason", _required(self.reason, "reason"))
        object.__setattr__(
            self,
            "changed_paths",
            tuple(sorted({str(path).strip().replace("\\", "/") for path in self.changed_paths})),
        )
        if any(
            not path
            or path.startswith("/")
            or path.startswith("../")
            or "/../" in path
            or path in {".", ".."}
            for path in self.changed_paths
        ):
            raise ValueError("changed_paths must be normalized repository-relative paths")

        if self.outcome is DormantCommitOutcome.COMMITTED:
            object.__setattr__(self, "head_sha", _sha40(self.head_sha, "head_sha"))
            if self.head_sha == self.base_sha:
                raise ValueError("COMMITTED record must advance base SHA")
            if not self.changed_paths:
                raise ValueError("COMMITTED record requires changed paths")
        elif self.outcome is DormantCommitOutcome.NO_CHANGE:
            object.__setattr__(self, "head_sha", _sha40(self.head_sha, "head_sha"))
            if self.head_sha != self.base_sha:
                raise ValueError("NO_CHANGE record head must equal base SHA")
            if self.changed_paths:
                raise ValueError("NO_CHANGE record cannot carry changed paths")
        else:
            if self.head_sha or self.changed_paths:
                raise ValueError("BLOCKED record cannot carry committed workspace identity")

    @property
    def record_id(self) -> str:
        return canonical_digest(
            {
                "admission_record_id": self.admission_record_id,
                "worker_run_id": self.worker_run_id,
                "attempt_id": self.attempt_id,
                "branch": self.branch,
                "base_sha": self.base_sha,
                "outcome": self.outcome.value,
                "reason": self.reason,
                "head_sha": self.head_sha,
                "changed_paths": list(self.changed_paths),
            }
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "admission_record_id": self.admission_record_id,
            "worker_run_id": self.worker_run_id,
            "attempt_id": self.attempt_id,
            "branch": self.branch,
            "base_sha": self.base_sha,
            "outcome": self.outcome.value,
            "reason": self.reason,
            "head_sha": self.head_sha,
            "changed_paths": list(self.changed_paths),
            "record_id": self.record_id,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "DormantHandoffCommitRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("dormant handoff commit record must be object")
        record = cls(
            admission_record_id=raw.get("admission_record_id"),
            worker_run_id=raw.get("worker_run_id"),
            attempt_id=raw.get("attempt_id"),
            branch=raw.get("branch"),
            base_sha=raw.get("base_sha"),
            outcome=DormantCommitOutcome(str(raw.get("outcome") or "")),
            reason=str(raw.get("reason") or ""),
            head_sha=str(raw.get("head_sha") or ""),
            changed_paths=_paths(raw.get("changed_paths")),
        )
        if _sha64(raw.get("record_id"), "record_id") != record.record_id:
            raise ValueError("dormant handoff commit record identity mismatch")
        return record


@dataclass(frozen=True, init=False)
class DormantHandoffCommitLedger:
    records: tuple[DormantHandoffCommitRecord, ...] = ()

    def __init__(
        self,
        records: tuple[DormantHandoffCommitRecord, ...] | DormantHandoffCommitRecord = (),
        *,
        record: DormantHandoffCommitRecord | None = None,
    ) -> None:
        if record is not None:
            if records not in ((), None):
                raise ValueError("dormant commit ledger cannot receive records and record")
            records = (record,)
        object.__setattr__(self, "records", records)
        self.__post_init__()

    def __post_init__(self) -> None:
        values = self.records
        if isinstance(values, DormantHandoffCommitRecord):
            values = (values,)
        elif not isinstance(values, tuple):
            values = tuple(values)
        by_record: dict[str, DormantHandoffCommitRecord] = {}
        by_attempt: dict[str, DormantHandoffCommitRecord] = {}
        for record in values:
            if not isinstance(record, DormantHandoffCommitRecord):
                raise ValueError("dormant handoff commit ledger contains invalid record")
            prior = by_record.get(record.record_id)
            if prior is not None and prior != record:
                raise ValueError("conflicting dormant handoff commit identity")
            attempt_owner = by_attempt.get(record.attempt_id)
            if attempt_owner is not None and attempt_owner.record_id != record.record_id:
                raise ValueError("multiple dormant handoff commits share one attempt")
            by_record[record.record_id] = record
            by_attempt[record.attempt_id] = record
        object.__setattr__(
            self,
            "records",
            tuple(sorted(by_record.values(), key=lambda value: value.record_id)),
        )

    @property
    def record(self) -> DormantHandoffCommitRecord | None:
        """Legacy deterministic selector while hosted execution remains singleton."""
        return self.records[0] if self.records else None

    def for_attempt(self, attempt_id: str) -> DormantHandoffCommitRecord | None:
        key = _sha64(attempt_id, "attempt_id")
        return next((value for value in self.records if value.attempt_id == key), None)

    def put(self, record: DormantHandoffCommitRecord) -> "DormantHandoffCommitLedger":
        existing = self.for_attempt(record.attempt_id)
        if existing is not None and existing.record_id != record.record_id:
            raise ValueError("dormant handoff attempt already owns another record")
        return DormantHandoffCommitLedger(
            tuple(
                record if value.attempt_id == record.attempt_id else value
                for value in self.records
            )
            + (() if existing is not None else (record,))
        )

    def remove_attempt(self, attempt_id: str) -> "DormantHandoffCommitLedger":
        key = _sha64(attempt_id, "attempt_id")
        return DormantHandoffCommitLedger(
            tuple(value for value in self.records if value.attempt_id != key)
        )

    @classmethod
    def from_mapping(cls, raw: Any) -> "DormantHandoffCommitLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping):
            raise ValueError("invalid dormant handoff commit ledger")
        version = raw.get("schema_version")
        if version == 1:
            value = raw.get("record")
            return cls(
                ()
                if value is None
                else (DormantHandoffCommitRecord.from_mapping(value),)
            )
        if version != 2:
            raise ValueError("invalid dormant handoff commit ledger")
        values = raw.get("records")
        if not isinstance(values, list):
            raise ValueError("dormant handoff commit records must be a list")
        return cls(
            tuple(DormantHandoffCommitRecord.from_mapping(value) for value in values)
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 2,
            "records": [value.as_dict() for value in self.records],
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


class DormantCommitStorePort(Protocol):
    def load(self) -> DormantHandoffCommitLedger: ...
    def save(
        self,
        ledger: DormantHandoffCommitLedger,
    ) -> DormantHandoffCommitLedger: ...


@dataclass(frozen=True)
class DormantHandoffCommitStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "DormantHandoffCommitStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / DORMANT_HANDOFF_COMMIT_RELATIVE_PATH,
                backup_path=root / DORMANT_HANDOFF_COMMIT_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> DormantHandoffCommitLedger:
        return DormantHandoffCommitLedger.from_mapping(self.adapter.load().as_dict())

    def save(
        self,
        ledger: DormantHandoffCommitLedger,
    ) -> DormantHandoffCommitLedger:
        self.adapter.save(ledger.as_dict())
        return ledger


@dataclass(frozen=True)
class DormantHandoffCommitResult:
    disposition: DormantCommitDisposition
    reason: str
    ledger: DormantHandoffCommitLedger
    workspace: WorkspaceCommitResult | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "ledger_digest": self.ledger.digest,
                "workspace_digest": self.workspace.digest if self.workspace else "",
            }
        )


def advance_dormant_handoff_commit(
    *,
    root: Path,
    store: DormantCommitStorePort | None = None,
    workspace_runner=None,
    attempt_id: str | None = None,
) -> DormantHandoffCommitResult:
    """Validate and commit one completed local worker delta; never perform remote effects."""

    root = Path(root).resolve()
    durable_store = store or DormantHandoffCommitStore.for_root(root)
    existing = durable_store.load()

    admissions = HostedAdmissionStore.for_root(root).load()
    admission = (
        admissions.for_attempt(attempt_id)
        if attempt_id is not None
        else admissions.record
    )
    if admission is None:
        return DormantHandoffCommitResult(
            DormantCommitDisposition.NO_ADMISSION,
            "no durable hosted admission record exists",
            existing,
        )
    if (
        admission.outcome is not HostedAdmissionOutcome.ADMITTED
        or admission.worker_spec is None
        or admission.attempt is None
    ):
        return DormantHandoffCommitResult(
            DormantCommitDisposition.NOT_ADMITTED,
            "durable admission does not authorize a worker handoff commit",
            existing,
        )

    spec = admission.worker_spec
    run = WorkerRunStore.for_root(root).load().find_attempt(spec.attempt_id)
    if run is None or run.status is not WorkerRunStatus.HANDOFF_READY:
        return DormantHandoffCommitResult(
            DormantCommitDisposition.WORKER_NOT_READY,
            "exact admitted worker run is not HANDOFF_READY",
            existing,
        )
    if run.spec.digest != spec.digest:
        return DormantHandoffCommitResult(
            DormantCommitDisposition.CONFLICT,
            "durable worker run spec differs from admitted frozen worker identity",
            existing,
        )

    manager_kwargs = {"root": root}
    if workspace_runner is not None:
        manager_kwargs["runner"] = workspace_runner
    manager = WorkerWorkspaceManager(**manager_kwargs)
    expected_worktree = manager.expected_path(spec)
    if Path(run.worktree).resolve() != expected_worktree:
        return DormantHandoffCommitResult(
            DormantCommitDisposition.CONFLICT,
            "durable worker worktree differs from deterministic admitted identity",
            existing,
        )

    existing_record = existing.for_attempt(spec.attempt_id)
    if existing_record is not None:
        if (
            existing_record.admission_record_id != admission.record_id
            or existing_record.worker_run_id != run.run_id
            or existing_record.branch != spec.branch
            or existing_record.base_sha != spec.base_sha
        ):
            return DormantHandoffCommitResult(
                DormantCommitDisposition.CONFLICT,
                "attempt owns conflicting dormant handoff commit state",
                existing,
            )
        return DormantHandoffCommitResult(
            DormantCommitDisposition.ALREADY_RECORDED,
            "dormant handoff commit outcome is already durable",
            existing,
        )

    scope = WorkspaceCommitScope(
        attempt_id=spec.attempt_id,
        branch=spec.branch,
        start_head=spec.base_sha,
        allowed_paths=spec.allowed_paths,
        protected_paths=spec.protected_paths,
    )
    adapter_kwargs = {"worktree": expected_worktree, "scope": scope}
    if workspace_runner is not None:
        adapter_kwargs["runner"] = workspace_runner
    workspace = WorkspaceCommitAdapter(**adapter_kwargs)

    try:
        committed = workspace.commit(
            lane=spec.lane,
            objective=spec.objective,
        )
    except (RuntimeError, ValueError) as exc:
        record = DormantHandoffCommitRecord(
            admission_record_id=admission.record_id,
            worker_run_id=run.run_id,
            attempt_id=spec.attempt_id,
            branch=spec.branch,
            base_sha=spec.base_sha,
            outcome=DormantCommitOutcome.BLOCKED,
            reason=f"bounded workspace commit rejected: {exc}",
        )
        ledger = existing.put(record)
        durable_store.save(ledger)
        return DormantHandoffCommitResult(
            DormantCommitDisposition.RECORDED,
            record.reason,
            ledger,
        )

    if committed.head_sha == spec.base_sha and not committed.changed_paths:
        outcome = DormantCommitOutcome.NO_CHANGE
        reason = "worker produced no bounded repository delta"
    else:
        outcome = DormantCommitOutcome.COMMITTED
        reason = "bounded worker delta is committed locally with exact attempt identity"

    record = DormantHandoffCommitRecord(
        admission_record_id=admission.record_id,
        worker_run_id=run.run_id,
        attempt_id=spec.attempt_id,
        branch=spec.branch,
        base_sha=spec.base_sha,
        outcome=outcome,
        reason=reason,
        head_sha=committed.head_sha,
        changed_paths=committed.changed_paths,
    )
    ledger = existing.put(record)
    durable_store.save(ledger)
    return DormantHandoffCommitResult(
        DormantCommitDisposition.RECORDED,
        reason,
        ledger,
        workspace=committed,
    )
