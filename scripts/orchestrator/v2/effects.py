"""Durable remote-effect identities and pure reconciliation for future exactly-once logical execution."""

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
    ABANDONED = "ABANDONED"


class RemoteEffectPresence(str, Enum):
    ABSENT = "ABSENT"
    PRESENT_EXACT = "PRESENT_EXACT"
    PRESENT_CONFLICT = "PRESENT_CONFLICT"
    UNKNOWN = "UNKNOWN"


class EffectReconcileDisposition(str, Enum):
    EXECUTE = "EXECUTE"
    MARK_COMPLETE = "MARK_COMPLETE"
    NOOP_COMPLETE = "NOOP_COMPLETE"
    BLOCK = "BLOCK"


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
        if not isinstance(kind, EffectKind):
            raise ValueError("kind must be EffectKind")
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

    def __post_init__(self) -> None:
        if not isinstance(self.identity, RemoteEffectIdentity):
            raise ValueError("identity must be RemoteEffectIdentity")
        if not isinstance(self.status, EffectStatus):
            raise ValueError("status must be EffectStatus")
        if not isinstance(self.remote_identity, str):
            raise ValueError("remote_identity must be a string")
        if self.status in {EffectStatus.PENDING, EffectStatus.ABANDONED} and self.remote_identity:
            raise ValueError("non-complete effect cannot carry remote_identity")
        if self.status is EffectStatus.COMPLETE and not self.remote_identity.strip():
            raise ValueError("completed effect requires remote_identity")

    @classmethod
    def begin(cls, identity: RemoteEffectIdentity) -> "RemoteEffectRecord":
        return cls(identity=identity, status=EffectStatus.PENDING)

    def complete(self, remote_identity: str) -> "RemoteEffectRecord":
        if self.status is EffectStatus.ABANDONED:
            raise ValueError("abandoned effect cannot be completed")
        remote_identity = str(remote_identity).strip()
        if not remote_identity:
            raise ValueError("remote_identity is required for a completed effect")
        return replace(
            self,
            status=EffectStatus.COMPLETE,
            remote_identity=remote_identity,
        )

    def abandon(self) -> "RemoteEffectRecord":
        if self.status is EffectStatus.COMPLETE:
            raise ValueError("completed effect cannot be abandoned")
        return replace(self, status=EffectStatus.ABANDONED, remote_identity="")

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "effect_id": self.identity.effect_id,
                "status": self.status.value,
                "remote_identity": self.remote_identity,
            }
        )


@dataclass(frozen=True)
class RemoteEffectObservation:
    presence: RemoteEffectPresence
    remote_identity: str = ""

    def __post_init__(self) -> None:
        if not isinstance(self.presence, RemoteEffectPresence):
            raise ValueError("presence must be RemoteEffectPresence")
        if not isinstance(self.remote_identity, str):
            raise ValueError("remote_identity must be a string")
        if self.presence is RemoteEffectPresence.PRESENT_EXACT and not self.remote_identity.strip():
            raise ValueError("PRESENT_EXACT requires remote_identity")
        if self.presence is not RemoteEffectPresence.PRESENT_EXACT and self.remote_identity:
            raise ValueError("remote_identity is valid only for PRESENT_EXACT")

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "presence": self.presence.value,
                "remote_identity": self.remote_identity,
            }
        )


@dataclass(frozen=True)
class EffectReconcileDecision:
    disposition: EffectReconcileDisposition
    reason: str
    effect_record_digest: str
    observation_digest: str
    remote_identity: str = ""

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "effect_record_digest": self.effect_record_digest,
                "observation_digest": self.observation_digest,
                "remote_identity": self.remote_identity,
            }
        )


def reconcile_remote_effect(
    record: RemoteEffectRecord,
    observation: RemoteEffectObservation,
) -> EffectReconcileDecision:
    if record.status is EffectStatus.ABANDONED:
        return EffectReconcileDecision(
            EffectReconcileDisposition.BLOCK,
            "effect is terminally abandoned and must never be executed",
            record.digest,
            observation.digest,
        )

    if record.status is EffectStatus.COMPLETE:
        return EffectReconcileDecision(
            EffectReconcileDisposition.NOOP_COMPLETE,
            "effect is already durably complete and must never be re-executed",
            record.digest,
            observation.digest,
            remote_identity=record.remote_identity,
        )

    if observation.presence is RemoteEffectPresence.ABSENT:
        return EffectReconcileDecision(
            EffectReconcileDisposition.EXECUTE,
            "pending effect is provably absent remotely; execute exactly once then persist completion",
            record.digest,
            observation.digest,
        )

    if observation.presence is RemoteEffectPresence.PRESENT_EXACT:
        return EffectReconcileDecision(
            EffectReconcileDisposition.MARK_COMPLETE,
            "pending effect already exists remotely; reconcile local completion without re-execution",
            record.digest,
            observation.digest,
            remote_identity=observation.remote_identity,
        )

    if observation.presence is RemoteEffectPresence.PRESENT_CONFLICT:
        return EffectReconcileDecision(
            EffectReconcileDisposition.BLOCK,
            "remote effect conflicts with durable identity; fail closed",
            record.digest,
            observation.digest,
        )

    return EffectReconcileDecision(
        EffectReconcileDisposition.BLOCK,
        "remote effect truth is unknown; fail closed rather than duplicate",
        record.digest,
        observation.digest,
    )
