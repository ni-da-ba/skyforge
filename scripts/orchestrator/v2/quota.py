"""Pure quota/provider admission policy for inert Platform v2."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any

from .identity import canonical_digest


class QuotaAdmissionDisposition(str, Enum):
    ALLOW_PROVIDER = "ALLOW_PROVIDER"
    BLOCK_PROVIDER = "BLOCK_PROVIDER"
    ALLOW_LOCAL = "ALLOW_LOCAL"
    BLOCK_LOCAL = "BLOCK_LOCAL"


@dataclass(frozen=True)
class ProviderQuotaObservation:
    authoritative: bool
    allowed: bool
    phase: str = ""
    reason: str = ""
    block_kind: str = ""
    retry_after_seconds: int = 0

    def __post_init__(self) -> None:
        for name in ("authoritative", "allowed"):
            if not isinstance(getattr(self, name), bool):
                raise ValueError(f"{name} must be a boolean")
        for name in ("phase", "reason", "block_kind"):
            if not isinstance(getattr(self, name), str):
                raise ValueError(f"{name} must be a string")
        if (
            isinstance(self.retry_after_seconds, bool)
            or not isinstance(self.retry_after_seconds, int)
            or self.retry_after_seconds < 0
        ):
            raise ValueError("retry_after_seconds must be a nonnegative integer")

    @classmethod
    def from_legacy_mapping(cls, raw: Any) -> "ProviderQuotaObservation":
        if not isinstance(raw, dict):
            raise ValueError("provider quota observation must be an object")
        return cls(
            authoritative=raw.get("authoritative", False),
            allowed=raw.get("allowed", False),
            phase=str(raw.get("phase") or ""),
            reason=str(raw.get("reason") or ""),
            block_kind=str(raw.get("block_kind") or ""),
            retry_after_seconds=int(raw.get("retry_after_seconds") or 0),
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "authoritative": self.authoritative,
            "allowed": self.allowed,
            "phase": self.phase,
            "reason": self.reason,
            "block_kind": self.block_kind,
            "retry_after_seconds": self.retry_after_seconds,
        }


@dataclass(frozen=True)
class LocalBudgetObservation:
    allowed: bool
    reason: str = ""
    retry_after_seconds: int = 0

    def __post_init__(self) -> None:
        if not isinstance(self.allowed, bool):
            raise ValueError("local budget allowed must be a boolean")
        if not isinstance(self.reason, str):
            raise ValueError("local budget reason must be a string")
        if (
            isinstance(self.retry_after_seconds, bool)
            or not isinstance(self.retry_after_seconds, int)
            or self.retry_after_seconds < 0
        ):
            raise ValueError("local retry_after_seconds must be nonnegative")


@dataclass(frozen=True)
class QuotaAdmissionDecision:
    disposition: QuotaAdmissionDisposition
    reason: str
    block_kind: str
    retry_after_seconds: int
    consume_attempt: bool
    clear_stale_quota_pacing: bool

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "block_kind": self.block_kind,
                "retry_after_seconds": self.retry_after_seconds,
                "consume_attempt": self.consume_attempt,
                "clear_stale_quota_pacing": self.clear_stale_quota_pacing,
            }
        )


def classify_quota_admission(
    provider: ProviderQuotaObservation | None,
    local: LocalBudgetObservation,
    *,
    stale_block_kind: str | None = None,
) -> QuotaAdmissionDecision:
    if stale_block_kind is not None and not isinstance(stale_block_kind, str):
        raise ValueError("stale_block_kind must be a string or null")

    if provider is not None and provider.authoritative:
        if provider.allowed:
            return QuotaAdmissionDecision(
                QuotaAdmissionDisposition.ALLOW_PROVIDER,
                provider.reason or "authoritative provider quota allows attempt",
                "",
                0,
                True,
                stale_block_kind == "quota_pacing",
            )
        return QuotaAdmissionDecision(
            QuotaAdmissionDisposition.BLOCK_PROVIDER,
            provider.reason or "authoritative provider quota blocks attempt",
            provider.block_kind or "quota",
            provider.retry_after_seconds,
            False,
            False,
        )

    if local.allowed:
        return QuotaAdmissionDecision(
            QuotaAdmissionDisposition.ALLOW_LOCAL,
            local.reason or "provider telemetry is non-authoritative; local budget allows attempt",
            "",
            0,
            True,
            False,
        )

    return QuotaAdmissionDecision(
        QuotaAdmissionDisposition.BLOCK_LOCAL,
        local.reason or "provider telemetry is non-authoritative; local budget blocks attempt",
        "local_budget",
        local.retry_after_seconds,
        False,
        False,
    )


def select_weekly_burst_margin(
    *,
    protected_authority: bool,
    ordinary_margin_percent: float,
    protected_margin_percent: float,
) -> float:
    if not isinstance(protected_authority, bool):
        raise ValueError("protected_authority must be a boolean")
    for name, value in (
        ("ordinary_margin_percent", ordinary_margin_percent),
        ("protected_margin_percent", protected_margin_percent),
    ):
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise ValueError(f"{name} must be numeric")
        if value < 0 or value > 100:
            raise ValueError(f"{name} must be between 0 and 100")
    return float(protected_margin_percent if protected_authority else ordinary_margin_percent)
