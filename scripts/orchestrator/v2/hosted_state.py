"""Durable local state for the read-only hosted Platform-v2 substrate."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping

from .events import DurableEvent
from .identity import canonical_digest
from .inbox import InboxState, enqueue_events
from .state_store import JsonStateStoreAdapter


HOSTED_STATE_RELATIVE_PATH = Path(".skyforge-platform-v2") / "hosted-state.json"
HOSTED_STATE_BACKUP_RELATIVE_PATH = Path(".skyforge-platform-v2") / "hosted-state.json.bak"
MAX_SEEN_DELIVERIES = 5000


def _strings(value: Any, label: str) -> tuple[str, ...]:
    if value is None:
        return ()
    if not isinstance(value, list):
        raise ValueError(f"{label} must be a list")
    result: list[str] = []
    seen: set[str] = set()
    for raw in value:
        text = str(raw or "").strip()
        if text and text not in seen:
            result.append(text)
            seen.add(text)
    return tuple(result)


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise ValueError(f"{label} must be an object")
    return value


@dataclass(frozen=True)
class HostedIngressState:
    inbox: InboxState = InboxState()
    seen_deliveries: tuple[str, ...] = ()
    legacy_projection: Mapping[str, Any] | None = None
    accepted_deliveries: int = 0
    rejected_controls: int = 0

    @classmethod
    def from_mapping(cls, raw: Any) -> "HostedIngressState":
        if raw is None:
            return cls()
        mapping = _mapping(raw, "hosted ingress state")
        schema = mapping.get("schema_version", 1)
        if schema != 1:
            raise ValueError(f"unsupported hosted ingress schema_version: {schema!r}")

        inbox_raw = _mapping(mapping.get("inbox") or {}, "hosted inbox")
        # InboxState's legacy reader already owns the durable queue/key schema.
        inbox = InboxState.from_legacy_mapping(
            {
                "pending_events": inbox_raw.get("pending_events", []),
                "retired_event_keys": inbox_raw.get("retired_event_keys", []),
                "completed_authority_event_keys": inbox_raw.get(
                    "completed_authority_event_keys", []
                ),
                "pending_decision": {
                    "event_keys": inbox_raw.get("owned_event_keys", [])
                }
                if inbox_raw.get("owned_event_keys")
                else None,
            }
        )

        projection = mapping.get("legacy_projection")
        if projection is not None:
            projection = dict(_mapping(projection, "legacy_projection"))

        accepted = mapping.get("accepted_deliveries", 0)
        rejected = mapping.get("rejected_controls", 0)
        for label, value in (
            ("accepted_deliveries", accepted),
            ("rejected_controls", rejected),
        ):
            if isinstance(value, bool) or not isinstance(value, int) or value < 0:
                raise ValueError(f"{label} must be a nonnegative integer")

        return cls(
            inbox=inbox,
            seen_deliveries=_strings(
                mapping.get("seen_deliveries", []),
                "seen_deliveries",
            ),
            legacy_projection=projection,
            accepted_deliveries=accepted,
            rejected_controls=rejected,
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "inbox": self.inbox.as_dict(),
            "seen_deliveries": list(self.seen_deliveries),
            "legacy_projection": (
                dict(self.legacy_projection)
                if self.legacy_projection is not None
                else None
            ),
            "accepted_deliveries": self.accepted_deliveries,
            "rejected_controls": self.rejected_controls,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())

    def with_projection(self, projection: Mapping[str, Any]) -> "HostedIngressState":
        return HostedIngressState(
            inbox=self.inbox,
            seen_deliveries=self.seen_deliveries,
            legacy_projection=dict(projection),
            accepted_deliveries=self.accepted_deliveries,
            rejected_controls=self.rejected_controls,
        )

    def with_rejected_control(self, delivery_id: str | None) -> "HostedIngressState":
        seen = _append_delivery(self.seen_deliveries, delivery_id)
        return HostedIngressState(
            inbox=self.inbox,
            seen_deliveries=seen,
            legacy_projection=self.legacy_projection,
            accepted_deliveries=self.accepted_deliveries,
            rejected_controls=self.rejected_controls + 1,
        )


def _append_delivery(
    values: tuple[str, ...],
    delivery_id: str | None,
) -> tuple[str, ...]:
    text = str(delivery_id or "").strip()
    if not text or text in values:
        return values
    return (values + (text,))[-MAX_SEEN_DELIVERIES:]


@dataclass(frozen=True)
class HostedIngressTransition:
    before_digest: str
    after: HostedIngressState
    duplicate_delivery: bool
    semantic_replay_suppressed: bool

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "before_digest": self.before_digest,
                "after_digest": self.after.digest,
                "duplicate_delivery": self.duplicate_delivery,
                "semantic_replay_suppressed": self.semantic_replay_suppressed,
            }
        )


def ingest_event(
    state: HostedIngressState,
    event: DurableEvent,
    *,
    delivery_id: str | None,
) -> HostedIngressTransition:
    before = state.digest
    delivery = str(delivery_id or "").strip()
    if delivery and delivery in state.seen_deliveries:
        return HostedIngressTransition(
            before_digest=before,
            after=state,
            duplicate_delivery=True,
            semantic_replay_suppressed=False,
        )

    inbox = state.inbox
    semantic_replay = False
    if event.actionable:
        transition = enqueue_events(inbox, (event,))
        inbox = transition.after
        semantic_replay = bool(transition.suppressed_replays)

    after = HostedIngressState(
        inbox=inbox,
        seen_deliveries=_append_delivery(state.seen_deliveries, delivery),
        legacy_projection=state.legacy_projection,
        accepted_deliveries=state.accepted_deliveries + 1,
        rejected_controls=state.rejected_controls,
    )
    return HostedIngressTransition(
        before_digest=before,
        after=after,
        duplicate_delivery=False,
        semantic_replay_suppressed=semantic_replay,
    )


@dataclass(frozen=True)
class HostedStateStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "HostedStateStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / HOSTED_STATE_RELATIVE_PATH,
                backup_path=root / HOSTED_STATE_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> HostedIngressState:
        snapshot = self.adapter.load()
        return HostedIngressState.from_mapping(snapshot.as_dict())

    def save(self, state: HostedIngressState) -> HostedIngressState:
        self.adapter.save(state.as_dict())
        return state
