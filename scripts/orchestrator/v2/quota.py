"""Pure provider/quota admission policy for inert Platform v2."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any

from .identity import canonical_digest


class QuotaAdmissionDisposition(str, Enum):
    ALLOW_PROVIDER = "ALLOW_PROVIDER"
    ALLOW_LOCAL = "ALLOW_LOCAL"
    BLOCK_PROVIDER = "BLOCK_PROVIDER"
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
            raise ValueError("authoritative and allowed must be booleans")
        for name in ("phase", "block_kind", "reason"):
            if not isinstance(getattr(self, name), str):
                raise ValueError(f"{name} must be a string")
        if (
            isinstance(self.retry_after_seconds, bool)
            or not isinstance(self.retry_after_seconds, int)
            or self.retry_after_seconds < 0
        ):
            raise ValueError("retry_after_seconds must be a non-negative integer")

    @classmethod
    def from_mapping(cls, raw: Any) -> "ProviderQuotaDecision | None":
        if raw is None:
            return None
        if not isinstance(raw, dict):
            return None
        authoritative = raw.get("authoritative")
        allowed = raw.get("allowed")
        if not isinstance(authoritative, bool) or not isinstance(allowed, bool):
            return None
        retry = raw.get("retry_after_seconds", 0)
        if isinstance(retry, bool) or not isinstance(retry, int) or retry < 0:
            return None
        return cls(
            authoritative=authoritative,
            allowed=allowed,
            phase=str(raw.get("phase") or ""),
            block_kind=str(raw.get("block_kind") or ""),
            retry_after_seconds=retry,
            reason=str(raw.get("reason") or ""),
        )

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
class LocalBudgetObservation:
    calls_used: int
    daily_limit: int

    def __post_init__(self) -> None:
        for name in ("calls_used", "daily_limit"):
            value = getattr(self, name)
            if isinstance(value, bool) or not isinstance(value, int) or value < 0:
                raise ValueError(f"{name} must be a non-negative integer")

    @property
    def available(self) -> bool:
        return self.calls_used < self.daily_limit

    def as_dict(self) -> dict[str, int]:
        return {"calls_used": self.calls_used, "daily_limit": self.daily_limit}


@dataclass(frozen=True)
class QuotaPolicySettings:
    weekly_burst_margin_percent: float
    protected_weekly_burst_margin_percent: float

    def __post_init__(self) -> None:
        for name in (
            "weekly_burst_margin_percent",
            "protected_weekly_burst_margin_percent",
        ):
            value = getattr(self, name)
            if isinstance(value, bool) or not isinstance(value, (int, float)):
                raise ValueError(f"{name} must be numeric")
            if value < 0 or value > 100:
                raise ValueError(f"{name} must be between 0 and 100")

    def burst_margin(self, *, protected_authority: bool) -> float:
        if not isinstance(protected_authority, bool):
            raise ValueError("protected_authority must be a boolean")
        return float(
            self.protected_weekly_burst_margin_percent
            if protected_authority
            else self.weekly_burst_margin_percent
        )


@dataclass(frozen=True)
class QuotaAdmissionDecision:
    disposition: QuotaAdmissionDisposition
    reason: str
    consume_attempt: bool
    block_kind: str = ""
    retry_after_seconds: int = 0
    clear_stale_quota_pacing_block: bool = False
    provider_digest: str = ""

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "consume_attempt": self.consume_attempt,
                "block_kind": self.block_kind,
                "retry_after_seconds": self.retry_after_seconds,
                "clear_stale_quota_pacing_block": self.clear_stale_quota_pacing_block,
                "provider_digest": self.provider_digest,
            }
        )


def classify_quota_admission(
    provider: ProviderQuotaDecision | None,
    local: LocalBudgetObservation,
) -> QuotaAdmissionDecision:
    if provider is not None and provider.authoritative:
        if provider.allowed:
            return QuotaAdmissionDecision(
                QuotaAdmissionDisposition.ALLOW_PROVIDER,
                provider.reason or "authoritative provider quota allows attempt",
                consume_attempt=True,
                clear_stale_quota_pacing_block=True,
                provider_digest=provider.digest,
            )
        return QuotaAdmissionDecision(
            QuotaAdmissionDisposition.BLOCK_PROVIDER,
            provider.reason or "authoritative provider quota blocks attempt",
            consume_attempt=False,
            block_kind=provider.block_kind or "quota",
            retry_after_seconds=provider.retry_after_seconds,
            provider_digest=provider.digest,
        )

    if local.available:
        return QuotaAdmissionDecision(
            QuotaAdmissionDisposition.ALLOW_LOCAL,
            "provider quota is non-authoritative; local budget permits attempt",
            consume_attempt=True,
            provider_digest=provider.digest if provider else "",
        )

    return QuotaAdmissionDecision(
        QuotaAdmissionDisposition.BLOCK_LOCAL,
        "provider quota is non-authoritative and local budget is exhausted",
        consume_attempt=False,
        block_kind="local_budget",
        provider_digest=provider.digest if provider else "",
    )
