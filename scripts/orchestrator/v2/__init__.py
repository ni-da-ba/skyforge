"""Inert Platform v2 primitives.

This package is deliberately not imported by the production Skyforge controller yet.
Platform v2 receives no runtime or repository-mutating authority merely because these
modules exist.
"""

from .fence import FenceBusyError, WriterFence
from .identity import AcceptanceIdentity, FrozenTaskSpec, TaskAttemptIdentity, canonical_digest
from .replay import ReplayRecord

__all__ = [
    "AcceptanceIdentity",
    "FenceBusyError",
    "FrozenTaskSpec",
    "ReplayRecord",
    "TaskAttemptIdentity",
    "WriterFence",
    "canonical_digest",
]
