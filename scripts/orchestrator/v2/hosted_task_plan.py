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


@dataclass(frozen=True)
class HostedTaskPlanLedger:
    active: HostedTaskDispatchPlan | None = None

    @classmethod
    def from_mapping(cls, raw: Any) -> "HostedTaskPlanLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid hosted task plan ledger")
        active_raw = raw.get("active")
        return cls(
            active=(
                None
                if active_raw is None
                else HostedTaskDispatchPlan.from_mapping(active_raw)
            )
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "active": self.active.as_dict() if self.active is not None else None,
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
    if ledger.active is not None:
        return HostedTaskPlanResult(
            HostedTaskPlanDisposition.ALREADY_ACTIVE,
            "one hosted task plan already owns protected authority",
            ledger,
        )

    selected = select_dispatch_batch(inbox.pending_events)
    if (
        len(selected) != 1
        or selected[0].signal_kind != "task"
        or not selected[0].protected_authority
    ):
        return HostedTaskPlanResult(
            HostedTaskPlanDisposition.NO_PROTECTED_TASK,
            "no single protected task is next under accepted inbox precedence",
            ledger,
        )

    event = selected[0]
    issue = event.task_issue_number
    if issue is None or issue <= 0:
        return HostedTaskPlanResult(
            HostedTaskPlanDisposition.BLOCKED,
            "selected protected task lacks valid issue identity",
            ledger,
        )

    hold = classify_external_dispatch_hold(external_claims, (issue,))
    if hold.hold:
        return HostedTaskPlanResult(
            HostedTaskPlanDisposition.EXTERNAL_HOLD,
            "external/manual producer owns the selected task issue",
            ledger,
        )

    record = authority_events.get(event.event_id)
    if record is None:
        return HostedTaskPlanResult(
            HostedTaskPlanDisposition.MISSING_CAPTURE,
            "selected task lacks signed-webhook authority capture",
            ledger,
        )
    if record.reference.issue_number != issue:
        return HostedTaskPlanResult(
            HostedTaskPlanDisposition.BLOCKED,
            "captured task authority issue differs from durable event",
            ledger,
        )

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
        HostedTaskPlanLedger(plan),
    )


def advance_claimed_task_preflight(
    *,
    ledger: HostedTaskPlanLedger,
    authority_events: TaskAuthorityEventLedger,
    trusted_actors: Iterable[str],
    repo: str,
    runner=None,
) -> HostedTaskPlanResult:
    plan = ledger.active
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
            HostedTaskPlanLedger(blocked),
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
            HostedTaskPlanLedger(waiting),
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
            HostedTaskPlanLedger(ready),
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
        HostedTaskPlanLedger(blocked),
    )
