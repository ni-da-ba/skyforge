"""Durable remote-effect identities for future exactly-once logical execution."""

from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum

from .identity import canonical_digest


class EffectKind(str, Enum):
    DISPATCH_WORKER = "DISPATCH_WORKER"
    PUSH_BRANCH = "PUSH_BRANCH"
    CREATE_PR = "CREATE_PR"
    UPDATE_PR = "UPDATE_PR"
    POST_COMMENT = "POST_COMMENT"
    MERGE_PR = "MERGE_PR"
    CLOSE_ISSUE = "CLOSE_ISSUE"


class EffectStatus(str, Enum):
    PENDING = "PENDING"
    COMPLETE = "COMPLETE"


@dataclass(frozen=True)
class RemoteEffectIdentity:
    attempt_id: str
    kind: EffectKind
    subject: str
    effect_id: str

    @classmethod
    def create(
        cls,
        *,
        attempt_id: str,
        kind: EffectKind,
        subject: str,
    ) -> "RemoteEffectIdentity":
        attempt_id = str(attempt_id).strip()
        subject = str(subject).strip()
        if not attempt_id:
            raise ValueError("attempt_id is required")
        if not subject:
            raise ValueError("effect subject is required")
        envelope = {
            "attempt_id": attempt_id,
            "kind": kind.value,
            "subject": subject,
        }
        return cls(
            attempt_id=attempt_id,
            kind=kind,
            subject=subject,
            effect_id=canonical_digest(envelope),
        )


@dataclass(frozen=True)
class RemoteEffectRecord:
    identity: RemoteEffectIdentity
    status: EffectStatus
    remote_identity: str = ""

    @classmethod
    def begin(cls, identity: RemoteEffectIdentity) -> "RemoteEffectRecord":
        return cls(identity=identity, status=EffectStatus.PENDING)

    def complete(self, remote_identity: str) -> "RemoteEffectRecord":
        remote_identity = str(remote_identity).strip()
        if not remote_identity:
            raise ValueError("remote_identity is required for a completed effect")
        return replace(
            self,
            status=EffectStatus.COMPLETE,
            remote_identity=remote_identity,
        )
