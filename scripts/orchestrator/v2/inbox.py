"""Pure durable inbox policy for Platform v2."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Iterable, Mapping

from .events import (
    DurableEvent,
    PROTECTED_AUTHORITY_SIGNAL_KINDS,
    normalize_legacy_event_key,
)
from .identity import canonical_digest


@dataclass(frozen=True)
class InboxState:
    pending_events: tuple[DurableEvent, ...] = ()
    retired_event_keys: tuple[str, ...] = ()
    completed_authority_event_keys: tuple[str, ...] = ()
    owned_event_keys: tuple[str, ...] = ()

    @classmethod
    def from_legacy_mapping(cls, mapping: Any) -> "InboxState":
        if not isinstance(mapping, Mapping):
            raise ValueError("legacy inbox state must be an object")

        raw_pending = mapping.get("pending_events", [])
        if not isinstance(raw_pending, list):
            raise ValueError("pending_events must be a list")
        pending: list[DurableEvent] = []
        for raw in raw_pending:
            pending.append(DurableEvent.from_legacy_mapping(raw))

        def read_key_list(field: str, value: Any) -> tuple[str, ...]:
            if value is None:
                value = []
            if not isinstance(value, list):
                raise ValueError(f"{field} must be a list")
            normalized: list[str] = []
            seen: set[str] = set()
            for raw_key in value:
                key = normalize_legacy_event_key(raw_key)
                if not key:
                    continue
                if key not in seen:
                    normalized.append(key)
                    seen.add(key)
            return tuple(normalized)

        retired = read_key_list(
            "retired_event_keys", mapping.get("retired_event_keys", [])
        )
        completed = read_key_list(
            "completed_authority_event_keys",
            mapping.get("completed_authority_event_keys", []),
        )

        decision = mapping.get("pending_decision")
        if decision is None:
            owned: tuple[str, ...] = ()
        else:
            if not isinstance(decision, Mapping):
                raise ValueError("pending_decision must be an object or null")
            owned = read_key_list("pending_decision.event_keys", decision.get("event_keys", []))

        return cls(
            pending_events=tuple(pending),
            retired_event_keys=retired,
            completed_authority_event_keys=completed,
            owned_event_keys=owned,
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "pending_events": [event.as_dict() for event in self.pending_events],
            "retired_event_keys": list(self.retired_event_keys),
            "completed_authority_event_keys": list(self.completed_authority_event_keys),
            "owned_event_keys": list(self.owned_event_keys),
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class InboxCompactionReport:
    before: int
    after: int
    coalesced: int
    dropped_to_reconcile: int
    reconcile_inserted: bool
    protected_overflow: int

    def as_dict(self) -> dict[str, Any]:
        return {
            "before": self.before,
            "after": self.after,
            "coalesced": self.coalesced,
            "dropped_to_reconcile": self.dropped_to_reconcile,
            "reconcile_inserted": self.reconcile_inserted,
            "protected_overflow": self.protected_overflow,
        }


@dataclass(frozen=True)
class InboxTransition:
    before_digest: str
    after: InboxState
    suppressed_replays: int
    compaction: InboxCompactionReport

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "before_digest": self.before_digest,
                "after_digest": self.after.digest,
                "suppressed_replays": self.suppressed_replays,
                "compaction": self.compaction.as_dict(),
            }
        )


def _compaction_slot(event: DurableEvent) -> tuple[str, ...]:
    if event.event == "push":
        return ("push", "main")
    if event.event == "pull_request" and event.pr_number is not None:
        return ("pull_request", str(event.pr_number))
    if event.event == "workflow_run" and event.head_sha:
        return ("workflow_run", str(event.head_sha))
    if event.event == "reconcile":
        return ("reconcile", str(event.action or "reconcile"))
    return (
        str(event.event or "event"),
        str(event.pr_number or ""),
        str(event.head_sha or ""),
        str(event.action or ""),
    )


def compact_events(
    events: Iterable[DurableEvent],
    *,
    owned_event_keys: Iterable[str] = (),
    limit: int = 100,
) -> tuple[tuple[DurableEvent, ...], InboxCompactionReport]:
    if isinstance(limit, bool) or not isinstance(limit, int) or limit < 1:
        raise ValueError("inbox compaction limit must be a positive integer")

    values = tuple(events)
    owned = {str(key) for key in owned_event_keys if str(key)}
    protected_keys = {
        event.event_id
        for event in values
        if event.event_id in owned or event.protected_authority
    }

    latest_unprotected_by_slot: dict[tuple[str, ...], tuple[int, DurableEvent]] = {}
    for index, event in enumerate(values):
        if event.event_id in protected_keys:
            continue
        latest_unprotected_by_slot[_compaction_slot(event)] = (index, event)

    selected_indexes = {index for index, _ in latest_unprotected_by_slot.values()}
    first_pass = tuple(
        event
        for index, event in enumerate(values)
        if event.event_id in protected_keys or index in selected_indexes
    )
    coalesced = len(values) - len(first_pass)

    if len(first_pass) <= limit:
        report = InboxCompactionReport(
            before=len(values),
            after=len(first_pass),
            coalesced=coalesced,
            dropped_to_reconcile=0,
            reconcile_inserted=False,
            protected_overflow=max(0, len(protected_keys) - limit),
        )
        return first_pass, report

    protected = tuple(
        event for event in first_pass if event.event_id in protected_keys
    )
    ordinary = tuple(
        event for event in first_pass if event.event_id not in protected_keys
    )
    ordinary_slots = max(0, limit - len(protected) - 1)
    kept_ordinary = ordinary[-ordinary_slots:] if ordinary_slots else ()
    kept_ordinary_keys = {event.event_id for event in kept_ordinary}
    dropped_ordinary = max(0, len(ordinary) - len(kept_ordinary))

    compacted = [
        event
        for event in first_pass
        if event.event_id in protected_keys or event.event_id in kept_ordinary_keys
    ]
    if dropped_ordinary:
        compacted.append(
            DurableEvent(
                actionable=True,
                reason="pending event history compacted; reconcile current repository truth",
                event="reconcile",
                action="queue_compaction",
            )
        )

    report = InboxCompactionReport(
        before=len(values),
        after=len(compacted),
        coalesced=coalesced,
        dropped_to_reconcile=dropped_ordinary,
        reconcile_inserted=bool(dropped_ordinary),
        protected_overflow=max(0, len(protected) - limit),
    )
    return tuple(compacted), report


def enqueue_events(
    state: InboxState,
    incoming: Iterable[DurableEvent],
    *,
    limit: int = 100,
) -> InboxTransition:
    before_digest = state.digest
    current = list(state.pending_events)
    by_key = {event.event_id for event in current}
    suppressed_keys = set(state.retired_event_keys) | set(
        state.completed_authority_event_keys
    )
    suppressed = 0

    for event in incoming:
        key = event.event_id
        if key in by_key or key in suppressed_keys:
            suppressed += 1
            continue
        current.append(event)
        by_key.add(key)

    compacted, report = compact_events(
        current,
        owned_event_keys=state.owned_event_keys,
        limit=limit,
    )
    after = InboxState(
        pending_events=compacted,
        retired_event_keys=state.retired_event_keys,
        completed_authority_event_keys=state.completed_authority_event_keys,
        owned_event_keys=state.owned_event_keys,
    )
    return InboxTransition(
        before_digest=before_digest,
        after=after,
        suppressed_replays=suppressed,
        compaction=report,
    )


def select_dispatch_batch(events: Iterable[DurableEvent]) -> tuple[DurableEvent, ...]:
    values = tuple(events)
    for signal_kind in ("task", "restart_recommended", "human_gate", "loop_risk"):
        for event in values:
            if event.signal_kind == signal_kind:
                return (event,)
    return values
