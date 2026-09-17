"""Pure pending-timer fairness and ordinary-event quiescence policy for inert Platform v2."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Iterable, Mapping

from .events import DurableEvent
from .identity import canonical_digest
from .inbox import select_dispatch_batch as select_protected_batch


class TimerDisposition(str, Enum):
    SCHEDULE = "SCHEDULE"
    KEEP_EARLIER = "KEEP_EARLIER"
    REPLACE_EARLIER = "REPLACE_EARLIER"
    REPLACE_DEAD = "REPLACE_DEAD"


@dataclass(frozen=True)
class PendingTimerObservation:
    now_epoch: float
    requested_delay_seconds: float
    existing_due_epoch: float | None = None
    existing_timer_alive: bool = False

    def __post_init__(self) -> None:
        for name in ("now_epoch", "requested_delay_seconds"):
            value = getattr(self, name)
            if isinstance(value, bool) or not isinstance(value, (int, float)):
                raise ValueError(f"{name} must be numeric")
        if self.requested_delay_seconds < 0:
            raise ValueError("requested_delay_seconds must be nonnegative")
        if self.existing_due_epoch is not None and (
            isinstance(self.existing_due_epoch, bool)
            or not isinstance(self.existing_due_epoch, (int, float))
        ):
            raise ValueError("existing_due_epoch must be numeric or null")
        if not isinstance(self.existing_timer_alive, bool):
            raise ValueError("existing_timer_alive must be boolean")

    @property
    def requested_due_epoch(self) -> float:
        return float(self.now_epoch + self.requested_delay_seconds)

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "now_epoch": self.now_epoch,
                "requested_delay_seconds": self.requested_delay_seconds,
                "existing_due_epoch": self.existing_due_epoch,
                "existing_timer_alive": self.existing_timer_alive,
            }
        )


@dataclass(frozen=True)
class PendingTimerDecision:
    disposition: TimerDisposition
    due_epoch: float
    reason: str
    observation_digest: str

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "due_epoch": self.due_epoch,
                "reason": self.reason,
                "observation_digest": self.observation_digest,
            }
        )


def classify_pending_timer(observation: PendingTimerObservation) -> PendingTimerDecision:
    requested_due = observation.requested_due_epoch
    if observation.existing_due_epoch is None:
        return PendingTimerDecision(
            TimerDisposition.SCHEDULE,
            requested_due,
            "no existing timer",
            observation.digest,
        )
    if not observation.existing_timer_alive:
        return PendingTimerDecision(
            TimerDisposition.REPLACE_DEAD,
            requested_due,
            "existing timer is not alive",
            observation.digest,
        )
    if requested_due >= observation.existing_due_epoch:
        return PendingTimerDecision(
            TimerDisposition.KEEP_EARLIER,
            float(observation.existing_due_epoch),
            "later request cannot postpone an earlier live timer",
            observation.digest,
        )
    return PendingTimerDecision(
        TimerDisposition.REPLACE_EARLIER,
        requested_due,
        "earlier request pulls pending work forward",
        observation.digest,
    )


@dataclass(frozen=True)
class QuiescentBatchDecision:
    selected_event_ids: tuple[str, ...]
    split: bool
    reason: str

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "selected_event_ids": list(self.selected_event_ids),
                "split": self.split,
                "reason": self.reason,
            }
        )


def select_quiescent_dispatch_batch(
    events: Iterable[DurableEvent],
    head_quiescent: Mapping[str, bool],
) -> QuiescentBatchDecision:
    values = tuple(events)
    protected = select_protected_batch(values)
    if len(protected) == 1 and protected[0].protected_authority:
        return QuiescentBatchDecision(
            (protected[0].event_id,),
            False,
            "protected authority retains one-at-a-time precedence",
        )

    heads = tuple(dict.fromkeys(event.head_sha for event in values if event.head_sha))
    if not heads:
        return QuiescentBatchDecision(
            tuple(event.event_id for event in values),
            False,
            "batch has no workflow-head quiescence dimension",
        )
    if any(head not in head_quiescent for head in heads):
        raise ValueError("quiescence observation missing for one or more unique heads")

    ready = {head for head in heads if head_quiescent[head]}
    if not ready or len(ready) == len(heads):
        return QuiescentBatchDecision(
            tuple(event.event_id for event in values),
            False,
            "all unique heads share the same readiness; preserve coalesced batch",
        )

    selected = tuple(
        event.event_id
        for event in values
        if event.head_sha and event.head_sha in ready
    )
    return QuiescentBatchDecision(
        selected,
        True,
        "mixed readiness: dispatch only events on quiescent heads",
    )
