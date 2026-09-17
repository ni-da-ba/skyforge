"""Pure provider/local quota admission policy for inert Platform v2."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any

from .identity import canonical_digest


class AttemptKind(str, Enum):
    CLASSIFIER = "classifier"
    WORKER = "worker"


class QuotaAdmissionDisposition(str, Enum):
    ALLOW_PROVIDER = "ALLOW_PROVIDER"
    BLOCK_PROVIDER = "BLOCK_PROVIDER"
    ALLOW_LOCAL = "ALLOW_LOCAL"
    BLOCK_LOCAL = "BLOCK_LOCAL"


@dataclass(frozen=True)
class ProviderQuotaDecision:
    authoritative: bool
    allowed: bool
    phase: str = ""
    block_kind: str = ""
    retry_after_seconds: int = 0
    reason: str = ""

    def __post_init__(self) -> None:
        if not isinstance(self.authoritative, bool) or not isinstance(self.allowed, bool):
            raise ValueError("provider authoritative/allowed must be booleans")
        for name in ("phase", "block_kind", "reason"):
            if not isinstance(getattr(self, name), str):
                raise ValueError(f"{name} must be a string")
        if (
            isinstance(self.retry_after_seconds, bool)
            or not isinstance(self.retry_after_seconds, int)
            or self.retry_after_seconds < 0
        ):
            raise ValueError("retry_after_seconds must be a nonnegative integer")

    @classmethod
    def unavailable(cls) -> "ProviderQuotaDecision":
        return cls(authoritative=False, allowed=False, phase="fallback_local")

    def as_dict(self) -> dict[str, Any]:
        return {
            "authoritative": self.authoritative,
            "allowed": self.allowed,
            "phase": self.phase,
            "block_kind": self.block_kind,
            "retry_after_seconds": self.retry_after_seconds,
            "reason": self.reason,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class LocalQuotaState:
    used: int
    limit: int

    def __post_init__(self) -> None:
        for name in ("used", "limit"):
            value = getattr(self, name)
            if isinstance(value, bool) or not isinstance(value, int) or value < 0:
                raise ValueError(f"{name} must be a nonnegative integer")

    @property
    def exhausted(self) -> bool:
        return self.used >= self.limit

    def as_dict(self) -> dict[str, int]:
        return {"used": self.used, "limit": self.limit}


@dataclass(frozen=True)
class QuotaAdmissionObservation:
    attempt_kind: AttemptKind
    provider: ProviderQuotaDecision
    local: LocalQuotaState
    stale_block_kind: str = ""

    def __post_init__(self) -> None:
        if not isinstance(self.attempt_kind, AttemptKind):
            raise ValueError("attempt_kind must be AttemptKind")
        if not isinstance(self.provider, ProviderQuotaDecision):
            raise ValueError("provider must be ProviderQuotaDecision")
        if not isinstance(self.local, LocalQuotaState):
            raise ValueError("local must be LocalQuotaState")
        if not isinstance(self.stale_block_kind, str):
            raise ValueError("stale_block_kind must be a string")

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "attempt_kind": self.attempt_kind.value,
                "provider": self.provider.as_dict(),
                "local": self.local.as_dict(),
                "stale_block_kind": self.stale_block_kind,
            }
        )


@dataclass(frozen=True)
class QuotaAdmissionDecision:
    disposition: QuotaAdmissionDisposition
    allowed: bool
    consume_attempt: bool
    block_kind: str
    retry_after_seconds: int
    reason: str
    clear_stale_quota_pacing_block: bool
    observation_digest: str

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "allowed": self.allowed,
                "consume_attempt": self.consume_attempt,
                "block_kind": self.block_kind,
                "retry_after_seconds": self.retry_after_seconds,
                "reason": self.reason,
                "clear_stale_quota_pacing_block": self.clear_stale_quota_pacing_block,
                "observation_digest": self.observation_digest,
            }
        )


def classify_quota_admission(
    observation: QuotaAdmissionObservation,
) -> QuotaAdmissionDecision:
    provider = observation.provider

    if provider.authoritative:
        if provider.allowed:
            return QuotaAdmissionDecision(
                QuotaAdmissionDisposition.ALLOW_PROVIDER,
                True,
                True,
                "",
                0,
                provider.reason or "authoritative provider quota admits attempt",
                observation.stale_block_kind == "quota_pacing",
                observation.digest,
            )
        return QuotaAdmissionDecision(
            QuotaAdmissionDisposition.BLOCK_PROVIDER,
            False,
            False,
            provider.block_kind or "quota",
            provider.retry_after_seconds,
            provider.reason or "authoritative provider quota blocks attempt",
            False,
            observation.digest,
        )

    if observation.local.exhausted:
        return QuotaAdmissionDecision(
            QuotaAdmissionDisposition.BLOCK_LOCAL,
            False,
            False,
            "local_budget",
            0,
            "provider signal is non-authoritative and local budget is exhausted",
            False,
            observation.digest,
        )

    return QuotaAdmissionDecision(
        QuotaAdmissionDisposition.ALLOW_LOCAL,
        True,
        True,
        "",
        0,
        "provider signal is non-authoritative; local budget admits attempt",
        observation.stale_block_kind == "quota_pacing",
        observation.digest,
    )


@dataclass(frozen=True)
class ProtectedAuthorityObservation:
    attempt_kind: AttemptKind
    protected_pending_task: bool = False
    pending_worker_authority_key: str = ""

    def __post_init__(self) -> None:
        if not isinstance(self.attempt_kind, AttemptKind):
            raise ValueError("attempt_kind must be AttemptKind")
        if not isinstance(self.protected_pending_task, bool):
            raise ValueError("protected_pending_task must be a boolean")
        if not isinstance(self.pending_worker_authority_key, str):
            raise ValueError("pending_worker_authority_key must be a string")

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "attempt_kind": self.attempt_kind.value,
                "protected_pending_task": self.protected_pending_task,
                "pending_worker_authority_key": self.pending_worker_authority_key,
            }
        )


def is_protected_authority_attempt(
    observation: ProtectedAuthorityObservation,
) -> bool:
    if observation.attempt_kind is AttemptKind.CLASSIFIER:
        return observation.protected_pending_task
    if observation.attempt_kind is AttemptKind.WORKER:
        return observation.pending_worker_authority_key.startswith("task:")
    return False
