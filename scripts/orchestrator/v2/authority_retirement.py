"""Explicit retirement of exact terminal, non-executed Platform-v2 task authority.

This module never creates authority and never marks work complete.  It is intended for
root/operator use while Platform-v2 is inactive and legacy owns the writer.  One exact
task event may be retired only after durable state proves that admission never acquired
worker execution identity.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum
from pathlib import Path
import re
import time
from typing import Any, Mapping

from .concurrency_claims import ConcurrencyClaimStore
from .dormant_handoff_commit import DormantHandoffCommitStore
from .hosted_admission import (
    HostedAdmissionOutcome,
    HostedAdmissionRecord,
    HostedAdmissionStore,
)
from .hosted_completion import HostedCompletionStore
from .hosted_state import HostedIngressState, HostedStateStore
from .hosted_task_plan import HostedTaskDispatchPlan, HostedTaskPlanStore
from .hosted_worker_scheduler import HostedWorkerSchedulerStore
from .identity import canonical_digest
from .inbox import InboxState
from .ordinary_pipeline import OrdinaryPipelineStore
from .state_store import JsonStateStoreAdapter
from .task_event_composition import TaskAuthorityEventStore
from .worker_provider import WorkerRunStore


V2_AUTHORITY_RETIREMENTS_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "operator-authority-retirements.json"
)
V2_AUTHORITY_RETIREMENTS_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "operator-authority-retirements.json.bak"
)
MAX_RETIRED_EVENT_KEYS = 1024
_EVENT_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
_SHA64_RE = re.compile(r"^[0-9a-f]{64}$")
_TERMINAL_NONEXECUTED = {
    HostedAdmissionOutcome.BLOCKED,
    HostedAdmissionOutcome.RECLASSIFY,
    HostedAdmissionOutcome.NOT_DISPATCH,
}


def _event_id(value: Any) -> str:
    text = str(value or "").strip()
    if not _EVENT_RE.fullmatch(text):
        raise ValueError("event_id must be canonical sha256 durable-event identity")
    return text


def _sha64(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not _SHA64_RE.fullmatch(text):
        raise ValueError(f"{label} must be lowercase SHA-256 hex")
    return text


def _positive(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be positive integer")
    return value


def _source_id(value: Any) -> str:
    text = str(value or "").strip()
    if not text or not text.isdigit() or int(text) <= 0:
        raise ValueError("source_id must be a positive GitHub comment id")
    return text


class V2AuthorityRetirementPhase(str, Enum):
    PREPARED = "PREPARED"
    COMPLETE = "COMPLETE"


@dataclass(frozen=True)
class V2AuthorityRetirementRecord:
    event_id: str
    issue_number: int
    source_id: str
    plan_id: str
    admission_record_id: str
    authority_event_digest: str
    authority_digest: str
    outcome: HostedAdmissionOutcome
    phase: V2AuthorityRetirementPhase
    recorded_at: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "event_id", _event_id(self.event_id))
        object.__setattr__(self, "issue_number", _positive(self.issue_number, "issue_number"))
        object.__setattr__(self, "source_id", _source_id(self.source_id))
        for name in (
            "plan_id",
            "admission_record_id",
            "authority_event_digest",
            "authority_digest",
        ):
            object.__setattr__(self, name, _sha64(getattr(self, name), name))
        if self.outcome not in _TERMINAL_NONEXECUTED:
            raise ValueError("retirement record outcome must be terminal non-executed")
        if not isinstance(self.phase, V2AuthorityRetirementPhase):
            raise ValueError("phase must be V2AuthorityRetirementPhase")
        if not str(self.recorded_at or "").strip():
            raise ValueError("recorded_at is required")

    @property
    def retirement_id(self) -> str:
        return canonical_digest(
            {
                "event_id": self.event_id,
                "issue_number": self.issue_number,
                "source_id": self.source_id,
                "plan_id": self.plan_id,
                "admission_record_id": self.admission_record_id,
                "authority_event_digest": self.authority_event_digest,
                "authority_digest": self.authority_digest,
                "outcome": self.outcome.value,
            }
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "retirement_id": self.retirement_id,
            "event_id": self.event_id,
            "issue_number": self.issue_number,
            "source_id": self.source_id,
            "plan_id": self.plan_id,
            "admission_record_id": self.admission_record_id,
            "authority_event_digest": self.authority_event_digest,
            "authority_digest": self.authority_digest,
            "outcome": self.outcome.value,
            "phase": self.phase.value,
            "recorded_at": self.recorded_at,
            # Deliberately distinguish retirement from successful task completion.
            "completed": False,
            "executed": False,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "V2AuthorityRetirementRecord":
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid V2 authority retirement record")
        if raw.get("completed") is not False or raw.get("executed") is not False:
            raise ValueError("retired authority evidence must remain non-completed/non-executed")
        record = cls(
            event_id=raw.get("event_id"),
            issue_number=raw.get("issue_number"),
            source_id=raw.get("source_id"),
            plan_id=raw.get("plan_id"),
            admission_record_id=raw.get("admission_record_id"),
            authority_event_digest=raw.get("authority_event_digest"),
            authority_digest=raw.get("authority_digest"),
            outcome=HostedAdmissionOutcome(str(raw.get("outcome") or "")),
            phase=V2AuthorityRetirementPhase(str(raw.get("phase") or "")),
            recorded_at=str(raw.get("recorded_at") or ""),
        )
        if str(raw.get("retirement_id") or "") != record.retirement_id:
            raise ValueError("V2 authority retirement identity mismatch")
        return record


@dataclass(frozen=True)
class V2AuthorityRetirementLedger:
    records: tuple[V2AuthorityRetirementRecord, ...] = ()

    def get(self, event_id: str) -> V2AuthorityRetirementRecord | None:
        key = _event_id(event_id)
        matches = [record for record in self.records if record.event_id == key]
        if len(matches) > 1:
            raise ValueError("duplicate V2 authority retirement event identity")
        return matches[0] if matches else None

    def put(self, record: V2AuthorityRetirementRecord) -> "V2AuthorityRetirementLedger":
        current = self.get(record.event_id)
        if current is not None and current.retirement_id != record.retirement_id:
            raise ValueError("V2 authority retirement immutable identity drifted")
        values = (
            tuple(
                record if value.event_id == record.event_id else value
                for value in self.records
            )
            if current is not None
            else self.records + (record,)
        )
        return V2AuthorityRetirementLedger(values)

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "records": [record.as_dict() for record in self.records],
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "V2AuthorityRetirementLedger":
        if raw in (None, {}):
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid V2 authority retirement ledger")
        values = raw.get("records")
        if not isinstance(values, list):
            raise ValueError("V2 authority retirement records must be a list")
        ledger = cls()
        for value in values:
            ledger = ledger.put(V2AuthorityRetirementRecord.from_mapping(value))
        return ledger


@dataclass(frozen=True)
class V2AuthorityRetirementStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "V2AuthorityRetirementStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / V2_AUTHORITY_RETIREMENTS_RELATIVE_PATH,
                backup_path=root / V2_AUTHORITY_RETIREMENTS_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> V2AuthorityRetirementLedger:
        return V2AuthorityRetirementLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: V2AuthorityRetirementLedger) -> V2AuthorityRetirementLedger:
        self.adapter.save(ledger.as_dict())
        return ledger

    def put(self, record: V2AuthorityRetirementRecord) -> V2AuthorityRetirementRecord:
        self.save(self.load().put(record))
        return record


@dataclass(frozen=True)
class V2AuthorityRetirementInspection:
    blockers: tuple[str, ...]
    record: V2AuthorityRetirementRecord | None
    already_complete: bool

    @property
    def ready(self) -> bool:
        return not self.blockers and self.record is not None


def _pending_event(state: HostedIngressState, event_id: str):
    matches = [event for event in state.inbox.pending_events if event.event_id == event_id]
    if len(matches) > 1:
        raise ValueError("duplicate pending V2 event identity")
    return matches[0] if matches else None


def _downstream_blockers(
    *,
    root: Path,
    event_id: str,
    issue_number: int,
    source_id: str,
    admission_record_id: str,
    authority_digest: str,
) -> list[str]:
    blockers: list[str] = []
    task_id = f"github-issue-{issue_number}-comment-{source_id}"

    if any(record.spec.task_id == task_id for record in WorkerRunStore.for_root(root).load().records):
        blockers.append("worker run exists for terminal authority")

    claims = ConcurrencyClaimStore.for_root(root).load()
    if any(record.task_id == task_id for record in claims.active):
        blockers.append("active concurrency claim exists for terminal authority")
    if any(record.task_id == task_id for record in claims.retired):
        blockers.append("retired concurrency claim proves terminal authority previously executed")

    scheduler = HostedWorkerSchedulerStore.for_root(root).load()
    if any(
        record.admission_record_id == admission_record_id
        for record in scheduler.records
    ):
        blockers.append("worker scheduler record exists for terminal authority")

    commits = DormantHandoffCommitStore.for_root(root).load()
    if any(
        record.admission_record_id == admission_record_id
        for record in commits.records
    ):
        blockers.append("dormant handoff commit exists for terminal authority")

    completions = HostedCompletionStore.for_root(root).load()
    if any(
        record.admission_record_id == admission_record_id or record.event_id == event_id
        for record in completions.records
    ):
        blockers.append("hosted completion exists for terminal authority")

    pipelines = OrdinaryPipelineStore.for_root(root).load()
    if any(
        record.authority_digest == authority_digest
        for record in pipelines.records
    ):
        blockers.append("ordinary execution pipeline exists for terminal authority")

    return blockers


def inspect_v2_authority_retirement(
    *,
    root: Path,
    event_id: str,
    issue_number: int,
    source_id: str,
) -> V2AuthorityRetirementInspection:
    root = Path(root).resolve()
    event_id = _event_id(event_id)
    issue_number = _positive(issue_number, "issue_number")
    source_id = _source_id(source_id)

    evidence_store = V2AuthorityRetirementStore.for_root(root)
    existing = evidence_store.load().get(event_id)

    state = HostedStateStore.for_root(root).load()
    if event_id in state.inbox.completed_authority_event_keys:
        return V2AuthorityRetirementInspection(
            ("authority is already marked completed and cannot be retired",),
            existing,
            False,
        )

    authority_event = TaskAuthorityEventStore.for_root(root).load().get(event_id)
    blockers: list[str] = []
    if authority_event is None:
        blockers.append("exact task-authority provenance is unavailable")
    else:
        if authority_event.reference.issue_number != issue_number:
            blockers.append("task-authority issue differs from requested retirement")
        if str(authority_event.reference.comment_id) != source_id:
            blockers.append("task-authority source comment differs from requested retirement")

    if existing is not None:
        if (
            existing.issue_number != issue_number
            or existing.source_id != source_id
        ):
            blockers.append("existing retirement evidence has different source identity")
        if authority_event is not None and existing.authority_event_digest != authority_event.digest:
            blockers.append("task-authority provenance changed after retirement preparation")

        plan = HostedTaskPlanStore.for_root(root).load().get(existing.plan_id)
        if plan is not None and (
            plan.event_id != event_id or plan.issue_number != issue_number
        ):
            blockers.append("remaining task plan differs from prepared retirement identity")
        admission = HostedAdmissionStore.for_root(root).load().get(
            existing.admission_record_id
        )
        if admission is not None and (
            admission.plan_id != existing.plan_id
            or admission.event_id != event_id
            or admission.issue_number != issue_number
            or admission.authority_digest != existing.authority_digest
            or admission.outcome != existing.outcome
        ):
            blockers.append("remaining admission differs from prepared retirement identity")
        blockers.extend(
            _downstream_blockers(
                root=root,
                event_id=event_id,
                issue_number=issue_number,
                source_id=source_id,
                admission_record_id=existing.admission_record_id,
                authority_digest=existing.authority_digest,
            )
        )

        pending = _pending_event(state, event_id)
        if pending is None and event_id not in state.inbox.retired_event_keys:
            blockers.append("prepared retirement lost both pending and retired event identity")
        if pending is not None and (
            pending.signal_kind != "task"
            or pending.task_issue_number != issue_number
            or str(pending.source_id or "") != source_id
        ):
            blockers.append("pending event differs from prepared task-authority identity")

        if existing.phase is V2AuthorityRetirementPhase.COMPLETE:
            if pending is not None:
                blockers.append("completed retirement still has pending event")
            if event_id not in state.inbox.retired_event_keys:
                blockers.append("completed retirement is not durably retired")
            if plan is not None:
                blockers.append("completed retirement still has active task plan")
            if admission is not None:
                blockers.append("completed retirement still has active admission")
            return V2AuthorityRetirementInspection(
                tuple(sorted(set(blockers))),
                existing,
                not blockers,
            )

        return V2AuthorityRetirementInspection(
            tuple(sorted(set(blockers))),
            existing,
            False,
        )

    pending = _pending_event(state, event_id)
    if pending is None:
        blockers.append("exact task authority is not pending in Platform-v2")
    elif (
        pending.signal_kind != "task"
        or pending.task_issue_number != issue_number
        or str(pending.source_id or "") != source_id
    ):
        blockers.append("pending event differs from requested task-authority identity")
    if event_id in state.inbox.retired_event_keys:
        blockers.append("authority is already retired without operator retirement evidence")

    plans = HostedTaskPlanStore.for_root(root).load()
    plan = plans.get_event(event_id)
    if plan is None:
        blockers.append("exact active task plan is unavailable")
    elif plan.issue_number != issue_number:
        blockers.append("task plan issue differs from requested retirement")

    admission = None
    if plan is not None:
        admission = HostedAdmissionStore.for_root(root).load().for_plan(plan.plan_id)
        if admission is None:
            blockers.append("exact active admission is unavailable")
        else:
            if admission.event_id != event_id or admission.issue_number != issue_number:
                blockers.append("admission source identity differs from requested retirement")
            if admission.outcome not in _TERMINAL_NONEXECUTED:
                blockers.append("admission is not terminal non-executed")
            if admission.consume_attempt:
                blockers.append("admission consumed an executable attempt")
            if any(
                value is not None
                for value in (admission.frozen_task, admission.attempt, admission.worker_spec)
            ):
                blockers.append("admission carries executable worker identity")
            blockers.extend(
                _downstream_blockers(
                    root=root,
                    event_id=event_id,
                    issue_number=issue_number,
                    source_id=source_id,
                    admission=admission,
                )
            )

    if blockers or authority_event is None or plan is None or admission is None:
        return V2AuthorityRetirementInspection(
            tuple(sorted(set(blockers))),
            None,
            False,
        )

    record = V2AuthorityRetirementRecord(
        event_id=event_id,
        issue_number=issue_number,
        source_id=source_id,
        plan_id=plan.plan_id,
        admission_record_id=admission.record_id,
        authority_event_digest=authority_event.digest,
        authority_digest=admission.authority_digest,
        outcome=admission.outcome,
        phase=V2AuthorityRetirementPhase.PREPARED,
        recorded_at=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    )
    return V2AuthorityRetirementInspection((), record, False)


def _retire_inbox_event(state: HostedIngressState, event_id: str) -> HostedIngressState:
    retired = list(state.inbox.retired_event_keys)
    if event_id not in retired:
        retired.append(event_id)
    inbox = InboxState(
        pending_events=tuple(
            event for event in state.inbox.pending_events if event.event_id != event_id
        ),
        retired_event_keys=tuple(retired[-MAX_RETIRED_EVENT_KEYS:]),
        completed_authority_event_keys=state.inbox.completed_authority_event_keys,
        owned_event_keys=tuple(
            key for key in state.inbox.owned_event_keys if key != event_id
        ),
    )
    return replace(state, inbox=inbox)


def retire_v2_authority(
    *,
    root: Path,
    event_id: str,
    issue_number: int,
    source_id: str,
) -> V2AuthorityRetirementRecord:
    root = Path(root).resolve()
    inspection = inspect_v2_authority_retirement(
        root=root,
        event_id=event_id,
        issue_number=issue_number,
        source_id=source_id,
    )
    if inspection.blockers or inspection.record is None:
        raise ValueError(
            "V2 authority retirement blocked: " + "; ".join(inspection.blockers)
        )
    if inspection.already_complete:
        return inspection.record

    store = V2AuthorityRetirementStore.for_root(root)
    record = inspection.record
    if store.load().get(record.event_id) is None:
        store.put(record)

    # Crash-safe order while V2 is inactive: suppress the ingress event first, then
    # release non-executed singleton ownership.  A PREPARED record makes every step
    # replayable without fabricating completion.
    state_store = HostedStateStore.for_root(root)
    state_store.save(_retire_inbox_event(state_store.load(), record.event_id))

    admission_store = HostedAdmissionStore.for_root(root)
    admission_store.save(
        admission_store.load().remove_plan(record.plan_id)
    )

    plan_store = HostedTaskPlanStore.for_root(root)
    plan_store.save(plan_store.load().remove(record.plan_id))

    verify = inspect_v2_authority_retirement(
        root=root,
        event_id=event_id,
        issue_number=issue_number,
        source_id=source_id,
    )
    if verify.blockers:
        raise RuntimeError(
            "V2 authority retirement verification failed: " + "; ".join(verify.blockers)
        )
    prepared = store.load().get(record.event_id)
    if prepared is None:
        raise RuntimeError("V2 authority retirement evidence disappeared")
    complete = replace(prepared, phase=V2AuthorityRetirementPhase.COMPLETE)
    store.put(complete)

    final = inspect_v2_authority_retirement(
        root=root,
        event_id=event_id,
        issue_number=issue_number,
        source_id=source_id,
    )
    if final.blockers or not final.already_complete or final.record is None:
        raise RuntimeError(
            "V2 authority retirement final verification failed: "
            + "; ".join(final.blockers)
        )
    return final.record
