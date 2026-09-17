"""Inert Platform v2 control-plane package.

The production Skyforge controller deliberately does not import this package yet.
Modules here may model state, replay decisions, and persistence contracts, but receive
no repository-mutating authority merely because they exist.
"""

from .core import (
    ControllerState,
    CoreDecision,
    HumanGateRecord,
    ManagedPRObservation,
    ManagedPRState,
    PendingWorkerState,
    reduce_managed_pr,
)
from .domain import (
    CIState,
    MechanicalSnapshot,
    PRClass,
    TransitionKind,
    TransitionPlan,
    decide_mechanical,
)
from .effects import EffectKind, EffectStatus, RemoteEffectIdentity, RemoteEffectRecord
from .events import DurableEvent, normalize_legacy_event_key
from .fence import FenceBusyError, WriterFence
from .identity import AcceptanceIdentity, FrozenTaskSpec, TaskAttemptIdentity, canonical_digest
from .inbox import (
    InboxCompactionReport,
    InboxState,
    InboxTransition,
    compact_events,
    enqueue_events,
    select_dispatch_batch,
)
from .ownership import ControllerIdentity, OwnershipToken
from .replay import ReplayRecord
from .state_store import JsonStateSnapshot, JsonStateStoreAdapter, StateStoreError

__all__ = [
    "AcceptanceIdentity",
    "CIState",
    "ControllerIdentity",
    "ControllerState",
    "CoreDecision",
    "EffectKind",
    "EffectStatus",
    "DurableEvent",
    "FenceBusyError",
    "FrozenTaskSpec",
    "HumanGateRecord",
    "InboxCompactionReport",
    "InboxState",
    "InboxTransition",
    "JsonStateSnapshot",
    "JsonStateStoreAdapter",
    "ManagedPRObservation",
    "ManagedPRState",
    "MechanicalSnapshot",
    "OwnershipToken",
    "PRClass",
    "PendingWorkerState",
    "RemoteEffectIdentity",
    "RemoteEffectRecord",
    "ReplayRecord",
    "StateStoreError",
    "TaskAttemptIdentity",
    "TransitionKind",
    "TransitionPlan",
    "WriterFence",
    "canonical_digest",
    "compact_events",
    "decide_mechanical",
    "enqueue_events",
    "normalize_legacy_event_key",
    "reduce_managed_pr",
    "select_dispatch_batch",
]
