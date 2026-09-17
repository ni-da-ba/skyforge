"""Inert Platform v2 primitives.

This package is deliberately not imported by the production Skyforge controller yet.
Platform v2 receives no runtime or repository-mutating authority merely because these
modules exist.
"""

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
from .replay import ReplayRecord

__all__ = [
    "AcceptanceIdentity",
    "CIState",
    "EffectKind",
    "EffectStatus",
    "FenceBusyError",
    "FrozenTaskSpec",
    "MechanicalSnapshot",
    "PRClass",
    "RemoteEffectIdentity",
    "RemoteEffectRecord",
    "ReplayRecord",
    "TaskAttemptIdentity",
    "TransitionKind",
    "TransitionPlan",
    "WriterFence",
    "canonical_digest",
    "decide_mechanical",
]
