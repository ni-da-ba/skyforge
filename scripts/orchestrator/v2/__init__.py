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
from .fence import FenceBusyError, WriterFence
from .identity import AcceptanceIdentity, FrozenTaskSpec, TaskAttemptIdentity, canonical_digest
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
    "FenceBusyError",
    "FrozenTaskSpec",
    "HumanGateRecord",
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
    "decide_mechanical",
    "reduce_managed_pr",
]
