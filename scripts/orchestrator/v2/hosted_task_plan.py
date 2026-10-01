"""Restart-safe hosted protected-task dispatch planning for Platform v2 R5C15.

This module may perform the already-accepted read-only task preflight. It has no classifier
provider, worker, worktree, Git/GitHub mutation, remote-effect, or writer-fence capability.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from pathlib import Path
import re
from typing import Any, Iterable, Mapping

from .external import ExternalProducerClaim, classify_external_dispatch_hold
from .hosted_task_preflight import (
    AcceptedMainRemoteUnavailable,
    HostedTaskPreflightDisposition,
    preflight_captured_task,
)
from .identity import canonical_digest
from .inbox import InboxState, select_dispatch_batch
from .state_store import JsonStateStoreAdapter
from .task_event_composition import (
    TaskAuthorityEventLedger,
    TaskPipelineSeed,
)


HOSTED_TASK_PLAN_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "hosted-task-plan.json"
)
HOSTED_TASK_PLAN_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "hosted-task-plan.json.bak"
)

_EVENT_ID_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
_DIGEST_RE = re.compile(r"^[0-9a-f]{64}$")


def _event_id(value: Any) -> str:
    text = str(value or "").strip()
    if not _EVENT_ID_RE.fullmatch(text):
        raise ValueError("event_id must be canonical durable-event identity")
    return text


def _digest(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not _DIGEST_RE.fullmatch(text):
        raise ValueError(f"{label} must be lowercase SHA-256 hex")
    return text


def _positive(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be positive integer")
    return value


class HostedTaskPlanStatus(str, Enum):
    CLAIMED = "CLAIMED"
    WAIT_REMOTE = "WAIT_REMOTE"
    READY_FOR_CLASSIFIER = "READY_FOR_CLASSIFIER"
    BLOCKED = "BLOCKED"


class HostedTaskPlanDisposition(str, Enum):
    CLAIMED = "CLAIMED"
    ALREADY_ACTIVE = "ALREADY_ACTIVE"
    NO_PROTECTED_TASK = "NO_PROTECTED_TASK"
    EXTERNAL_HOLD = "EXTERNAL_HOLD"
    MISSING_CAPTURE = "MISSING_CAPTURE"
    READY_FOR_CLASSIFIER = "READY_FOR_CLASSIFIER"
    WAIT_REMOTE = "WAIT_REMOTE"
    BLOCKED = "BLOCKED"
    ALREADY_TERMINAL = "ALREADY_TERMINAL"


@dataclass(frozen=True)
class HostedTaskDispatchPlan:
    event_id: str
    issue_number: int
    authority_record_digest: str
    status: HostedTaskPlanStatus
    reason: str
    seed: TaskPipelineSeed | None = None
    preflight_digest: str = ""

    def __post_init__(self) -> None:
        object.__setattr__(self, "event_id", _event_id(self.event_id))
        object.__setattr__(
            self,
            "issue_number",
            _positive(self.issue_number, "issue_number"),
        )
        object.__setattr__(
            self,
            "authority_record_digest",
            _digest(self.authority_record_digest, "authority_record_digest"),
        )
        if not isinstance(self.status, HostedTaskPlanStatus):
            raise ValueError("status must be HostedTaskPlanStatus")
        if not isinstance(self.reason, str) or not self.reason.strip():
            raise ValueError("reason is required")
        if self.preflight_digest:
            object.__setattr__(
                self,
                "preflight_digest",
                _digest(self.preflight_digest, "preflight_digest"),
            )
        if self.status is HostedTaskPlanStatus.READY_FOR_CLASSIFIER:
            if self.seed is None:
                raise ValueError("READY_FOR_CLASSIFIER plan requires seed")
            if self.seed.event_id != self.event_id:
                raise ValueError("plan seed event identity mismatch")
            if self.seed.issue_number != self.issue_number:
                raise ValueError("plan seed issue identity mismatch")
            if not self.preflight_digest:
                raise ValueError("READY_FOR_CLASSIFIER plan requires preflight digest")
        elif self.seed is not None:
            raise ValueError("only READY_FOR_CLASSIFIER plan may carry seed")

    @property
    def plan_id(self) -> str:
        return canonical_digest(
            {
                "event_id": self.event_id,
                "issue_number": self.issue_number,
                "authority_record_digest": self.authority_record_digest,
            }
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "event_id": self.event_id,
            "issue_number": self.issue_number,
            "authority_record_digest": self.authority_record_digest,
            "status": self.status.value,
            "reason": self.reason,
            "seed": self.seed.as_dict() if self.seed is not None else None,
            "seed_digest": self.seed.digest if self.seed is not None else "",
            "preflight_digest": self.preflight_digest,
            "plan_id": self.plan_id,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "HostedTaskDispatchPlan":
        if not isinstance(raw, Mapping):
            raise ValueError("hosted task dispatch plan must be object")
        seed_raw = raw.get("seed")
        seed = None if seed_raw is None else TaskPipelineSeed.from_mapping(seed_raw)
        if seed is not None:
            recorded_seed_digest = _digest(raw.get("seed_digest"), "seed_digest")
            if recorded_seed_digest != seed.digest:
                raise ValueError("hosted plan seed digest mismatch")
        plan = cls(
            event_id=raw.get("event_id"),
            issue_number=raw.get("issue_number"),
            authority_record_digest=raw.get("authority_record_digest"),
            status=HostedTaskPlanStatus(str(raw.get("status") or "")),
            reason=str(raw.get("reason") or ""),
            seed=seed,
            preflight_digest=str(raw.get("preflight_digest") or ""),
        )
        recorded_plan_id = _digest(raw.get("plan_id"), "plan_id")
        if recorded_plan_id != plan.plan_id:
            raise ValueError("hosted task plan identity mismatch")
        return plan


@dataclass(frozen=True, init=False)
class HostedTaskPlanLedger:
    records: tuple[HostedTaskDispatchPlan, ...] = ()

    def __init__(
        self,
        records: tuple[HostedTaskDispatchPlan, ...] | HostedTaskDispatchPlan = (),
        *,
        active: HostedTaskDispatchPlan | None = None,
    ) -> None:
        if active is not None:
            if records not in ((), None):
                raise ValueError("hosted task plan ledger cannot receive records and active")
            records = (active,)
        object.__setattr__(self, "records", records)
        self.__post_init__()

    def __post_init__(self) -> None:
        values = self.records
        if isinstance(values, HostedTaskDispatchPlan):
            values = (values,)
        elif not isinstance(values, tuple):
            values = tuple(values)
        by_plan: dict[str, HostedTaskDispatchPlan] = {}
        by_event: dict[str, HostedTaskDispatchPlan] = {}
        by_issue: dict[int, HostedTaskDispatchPlan] = {}
        for record in values:
            if not isinstance(record, HostedTaskDispatchPlan):
                raise ValueError("hosted task plan ledger contains invalid record")
            prior_plan = by_plan.get(record.plan_id)
            if prior_plan is not None and prior_plan != record:
                raise ValueError("conflicting hosted task plan identity")
            prior_event = by_event.get(record.event_id)
            if prior_event is not None and prior_event.plan_id != record.plan_id:
                raise ValueError("multiple hosted task plans share one event")
            prior_issue = by_issue.get(record.issue_number)
            if prior_issue is not None and prior_issue.plan_id != record.plan_id:
                raise ValueError("multiple hosted task plans share one issue")
            by_plan[record.plan_id] = record
            by_event[record.event_id] = record
            by_issue[record.issue_number] = record
        object.__setattr__(
            self,
            "records",
            tuple(sorted(by_plan.values(), key=lambda value: value.plan_id)),
        )

    @property
    def active(self) -> HostedTaskDispatchPlan | None:
        """Legacy deterministic selector while hosted execution remains singleton."""
        if not self.records:
            return None
        priority = {
            HostedTaskPlanStatus.READY_FOR_CLASSIFIER: 0,
            HostedTaskPlanStatus.CLAIMED: 1,
            HostedTaskPlanStatus.WAIT_REMOTE: 2,
            HostedTaskPlanStatus.BLOCKED: 3,
        }
        return min(
            self.records,
            key=lambda value: (priority[value.status], value.plan_id),
        )

    def get(self, plan_id: str) -> HostedTaskDispatchPlan | None:
        key = _digest(plan_id, "plan_id")
        return next((value for value in self.records if value.plan_id == key), None)

    def get_event(self, event_id: str) -> HostedTaskDispatchPlan | None:
        key = _event_id(event_id)
        return next((value for value in self.records if value.event_id == key), None)

    def get_issue(self, issue_number: int) -> HostedTaskDispatchPlan | None:
        if isinstance(issue_number, bool) or not isinstance(issue_number, int) or issue_number <= 0:
            raise ValueError("issue_number must be positive integer")
        return next(
            (value for value in self.records if value.issue_number == issue_number),
            None,
        )

    def put(self, record: HostedTaskDispatchPlan) -> "HostedTaskPlanLedger":
        existing = self.get(record.plan_id)
        if existing is not None and existing.event_id != record.event_id:
            raise ValueError("hosted task plan identity changed event")
        event_owner = self.get_event(record.event_id)
        if event_owner is not None and event_owner.plan_id != record.plan_id:
            raise ValueError("hosted task event already owns another plan")
        issue_owner = self.get_issue(record.issue_number)
        if issue_owner is not None and issue_owner.plan_id != record.plan_id:
            raise ValueError("hosted task issue already owns another plan")
        return HostedTaskPlanLedger(
            tuple(
                record if value.plan_id == record.plan_id else value
                for value in self.records
            )
            + (() if existing is not None else (record,))
        )

    def remove(self, plan_id: str) -> "HostedTaskPlanLedger":
        key = _digest(plan_id, "plan_id")
        return HostedTaskPlanLedger(
            tuple(value for value in self.records if value.plan_id != key)
        )

    @classmethod
    def from_mapping(cls, raw: Any) -> "HostedTaskPlanLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping):
            raise ValueError("invalid hosted task plan ledger")
        version = raw.get("schema_version")
        if version == 1:
            active_raw = raw.get("active")
            return cls(
                ()
                if active_raw is None
                else (HostedTaskDispatchPlan.from_mapping(active_raw),)
            )
        if version != 2:
            raise ValueError("invalid hosted task plan ledger")
        records = raw.get("records")
        if not isinstance(records, list):
            raise ValueError("hosted task plan records must be a list")
        return cls(tuple(HostedTaskDispatchPlan.from_mapping(value) for value in records))

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 2,
            "records": [value.as_dict() for value in self.records],
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class HostedTaskPlanStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "HostedTaskPlanStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / HOSTED_TASK_PLAN_RELATIVE_PATH,
                backup_path=root / HOSTED_TASK_PLAN_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> HostedTaskPlanLedger:
        return HostedTaskPlanLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: HostedTaskPlanLedger) -> HostedTaskPlanLedger:
        self.adapter.save(ledger.as_dict())
        return ledger


@dataclass(frozen=True)
class HostedTaskPlanResult:
    disposition: HostedTaskPlanDisposition
    reason: str
    ledger: HostedTaskPlanLedger

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "ledger_digest": self.ledger.digest,
            }
        )


def claim_next_protected_task(
    *,
    ledger: HostedTaskPlanLedger,
    inbox: InboxState,
    authority_events: TaskAuthorityEventLedger,
    external_claims: Iterable[ExternalProducerClaim] = (),
) -> HostedTaskPlanResult:
    task_candidates = tuple(
        event
        for event in inbox.pending_events
        if event.signal_kind == "task" and event.protected_authority
    )
    if not task_candidates:
        selected = select_dispatch_batch(inbox.pending_events)
        return HostedTaskPlanResult(
            HostedTaskPlanDisposition.NO_PROTECTED_TASK,
            (
                "no unplanned protected task is next under accepted inbox precedence"
                if selected
                else "no protected task is pending"
            ),
            ledger,
        )

    planned_issues = {value.issue_number for value in ledger.records}
    blockers: list[HostedTaskPlanResult] = []
    saw_unplanned = False

    for event in task_candidates:
        issue = event.task_issue_number
        if ledger.get_event(event.event_id) is not None:
            continue
        if issue in planned_issues:
            # Same-issue signed revisions belong to explicit supersession logic,
            # never to independent multi-objective scheduling.
            continue
        saw_unplanned = True

        if issue is None or issue <= 0:
            blockers.append(
                HostedTaskPlanResult(
                    HostedTaskPlanDisposition.BLOCKED,
                    "selected protected task lacks valid issue identity",
                    ledger,
                )
            )
            continue

        hold = classify_external_dispatch_hold(external_claims, (issue,))
        if hold.hold:
            blockers.append(
                HostedTaskPlanResult(
                    HostedTaskPlanDisposition.EXTERNAL_HOLD,
                    "external/manual producer owns the selected task issue",
                    ledger,
                )
            )
            continue

        record = authority_events.get(event.event_id)
        if record is None:
            blockers.append(
                HostedTaskPlanResult(
                    HostedTaskPlanDisposition.MISSING_CAPTURE,
                    "selected task lacks signed-webhook authority capture",
                    ledger,
                )
            )
            continue
        if record.reference.issue_number != issue:
            blockers.append(
                HostedTaskPlanResult(
                    HostedTaskPlanDisposition.BLOCKED,
                    "captured task authority issue differs from durable event",
                    ledger,
                )
            )
            continue

        plan = HostedTaskDispatchPlan(
            event_id=event.event_id,
            issue_number=issue,
            authority_record_digest=record.digest,
            status=HostedTaskPlanStatus.CLAIMED,
            reason="protected task authority durably claimed for read-only preflight",
        )
        return HostedTaskPlanResult(
            HostedTaskPlanDisposition.CLAIMED,
            plan.reason,
            ledger.put(plan),
        )

    if blockers:
        return blockers[0]
    return HostedTaskPlanResult(
        HostedTaskPlanDisposition.ALREADY_ACTIVE,
        (
            "all pending protected task events already have durable hosted plans"
            if not saw_unplanned
            else "no independent protected task can be claimed"
        ),
        ledger,
    )


def advance_claimed_task_preflight(
    *,
    ledger: HostedTaskPlanLedger,
    authority_events: TaskAuthorityEventLedger,
    trusted_actors: Iterable[str],
    repo: str,
    runner=None,
    plan_id: str | None = None,
) -> HostedTaskPlanResult:
    plan = ledger.get(plan_id) if plan_id is not None else ledger.active
    if plan is None:
        return HostedTaskPlanResult(
            HostedTaskPlanDisposition.NO_PROTECTED_TASK,
            "no hosted task plan is active",
            ledger,
        )
    if plan.status in {
        HostedTaskPlanStatus.READY_FOR_CLASSIFIER,
        HostedTaskPlanStatus.BLOCKED,
    }:
        return HostedTaskPlanResult(
            HostedTaskPlanDisposition.ALREADY_TERMINAL,
            "hosted task plan is already terminal for planning",
            ledger,
        )

    record = authority_events.get(plan.event_id)
    if record is None or record.digest != plan.authority_record_digest:
        blocked = HostedTaskDispatchPlan(
            event_id=plan.event_id,
            issue_number=plan.issue_number,
            authority_record_digest=plan.authority_record_digest,
            status=HostedTaskPlanStatus.BLOCKED,
            reason="captured task authority is missing or changed after plan claim",
        )
        return HostedTaskPlanResult(
            HostedTaskPlanDisposition.BLOCKED,
            blocked.reason,
            ledger.put(blocked),
        )

    try:
        preflight_kwargs = {
            "record": record,
            "trusted_actors": tuple(trusted_actors),
            "repo": repo,
        }
        if runner is not None:
            preflight_kwargs["runner"] = runner
        preflight = preflight_captured_task(**preflight_kwargs)
    except AcceptedMainRemoteUnavailable as exc:
        waiting = HostedTaskDispatchPlan(
            event_id=plan.event_id,
            issue_number=plan.issue_number,
            authority_record_digest=plan.authority_record_digest,
            status=HostedTaskPlanStatus.WAIT_REMOTE,
            reason=f"fresh task preflight truth unavailable: {exc}",
        )
        return HostedTaskPlanResult(
            HostedTaskPlanDisposition.WAIT_REMOTE,
            waiting.reason,
            ledger.put(waiting),
        )

    if preflight.disposition is HostedTaskPreflightDisposition.READY_FOR_CLASSIFIER:
        if preflight.seed is None:
            raise ValueError("classifier-ready preflight lacks TaskPipelineSeed")
        ready = HostedTaskDispatchPlan(
            event_id=plan.event_id,
            issue_number=plan.issue_number,
            authority_record_digest=plan.authority_record_digest,
            status=HostedTaskPlanStatus.READY_FOR_CLASSIFIER,
            reason=preflight.reason,
            seed=preflight.seed,
            preflight_digest=preflight.digest,
        )
        return HostedTaskPlanResult(
            HostedTaskPlanDisposition.READY_FOR_CLASSIFIER,
            ready.reason,
            ledger.put(ready),
        )

    blocked = HostedTaskDispatchPlan(
        event_id=plan.event_id,
        issue_number=plan.issue_number,
        authority_record_digest=plan.authority_record_digest,
        status=HostedTaskPlanStatus.BLOCKED,
        reason=preflight.reason,
        preflight_digest=preflight.digest,
    )
    return HostedTaskPlanResult(
        HostedTaskPlanDisposition.BLOCKED,
        blocked.reason,
        ledger.put(blocked),
    )
