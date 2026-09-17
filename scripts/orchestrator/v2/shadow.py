"""Deterministic offline shadow integration for inert Platform v2.

This module composes already-collected typed observations only. It performs no
network, filesystem, provider, GitHub, workspace, or production-runtime actions.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable

from .core import ControllerState, ManagedPRObservation, reduce_managed_pr
from .effects import RemoteEffectObservation, RemoteEffectRecord, reconcile_remote_effect
from .fairness import PendingTimerObservation, classify_pending_timer
from .identity import canonical_digest
from .quota import LocalBudgetObservation, ProviderQuotaDecision, classify_quota_admission


@dataclass(frozen=True)
class ShadowEntry:
    policy: str
    input_digest: str
    decision_digest: str
    disposition: str

    def __post_init__(self) -> None:
        for name in ("policy", "input_digest", "decision_digest", "disposition"):
            value = getattr(self, name)
            if not isinstance(value, str) or not value.strip():
                raise ValueError(f"{name} must be a non-empty string")

    def as_dict(self) -> dict[str, str]:
        return {
            "policy": self.policy,
            "input_digest": self.input_digest,
            "decision_digest": self.decision_digest,
            "disposition": self.disposition,
        }


@dataclass(frozen=True)
class OfflineShadowReport:
    entries: tuple[ShadowEntry, ...]

    def __post_init__(self) -> None:
        policies = [entry.policy for entry in self.entries]
        if len(policies) != len(set(policies)):
            raise ValueError("offline shadow report cannot contain duplicate policy keys")

    def as_dict(self) -> dict[str, object]:
        return {"entries": [entry.as_dict() for entry in self.entries]}

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


def build_shadow_report(entries: Iterable[ShadowEntry]) -> OfflineShadowReport:
    values = tuple(entries)
    policies = [entry.policy for entry in values]
    if len(policies) != len(set(policies)):
        raise ValueError("duplicate shadow policy key")
    return OfflineShadowReport(tuple(sorted(values, key=lambda entry: entry.policy)))


@dataclass(frozen=True)
class OfflineShadowInput:
    managed_state: ControllerState | None = None
    managed_observation: ManagedPRObservation | None = None
    quota_provider: ProviderQuotaDecision | None = None
    quota_local: LocalBudgetObservation | None = None
    effect_record: RemoteEffectRecord | None = None
    effect_observation: RemoteEffectObservation | None = None
    timer_observation: PendingTimerObservation | None = None

    def __post_init__(self) -> None:
        managed_pair = (self.managed_state is None, self.managed_observation is None)
        if managed_pair[0] != managed_pair[1]:
            raise ValueError("managed shadow inputs must be supplied together")
        quota_pair = (self.quota_provider is None, self.quota_local is None)
        # Provider may legitimately be unavailable/non-authoritative and represented by None,
        # so quota_local alone is allowed and means local fallback.
        if self.quota_local is None and self.quota_provider is not None:
            raise ValueError("quota provider observation requires quota_local fallback state")
        effect_pair = (self.effect_record is None, self.effect_observation is None)
        if effect_pair[0] != effect_pair[1]:
            raise ValueError("effect shadow inputs must be supplied together")


def evaluate_offline_shadow(value: OfflineShadowInput) -> OfflineShadowReport:
    entries: list[ShadowEntry] = []

    if value.managed_state is not None and value.managed_observation is not None:
        decision = reduce_managed_pr(value.managed_state, value.managed_observation)
        entries.append(
            ShadowEntry(
                policy="managed_pr",
                input_digest=canonical_digest(
                    {
                        "state": value.managed_state.digest,
                        "observation": value.managed_observation.digest,
                    }
                ),
                decision_digest=decision.digest,
                disposition=decision.transition.kind.value,
            )
        )

    if value.quota_local is not None:
        decision = classify_quota_admission(value.quota_provider, value.quota_local)
        provider_digest = value.quota_provider.digest if value.quota_provider else ""
        entries.append(
            ShadowEntry(
                policy="quota",
                input_digest=canonical_digest(
                    {
                        "provider": provider_digest,
                        "local": value.quota_local.as_dict(),
                    }
                ),
                decision_digest=decision.digest,
                disposition=decision.disposition.value,
            )
        )

    if value.effect_record is not None and value.effect_observation is not None:
        decision = reconcile_remote_effect(value.effect_record, value.effect_observation)
        entries.append(
            ShadowEntry(
                policy="remote_effect",
                input_digest=canonical_digest(
                    {
                        "record": value.effect_record.digest,
                        "observation": value.effect_observation.digest,
                    }
                ),
                decision_digest=decision.digest,
                disposition=decision.disposition.value,
            )
        )

    if value.timer_observation is not None:
        decision = classify_pending_timer(value.timer_observation)
        entries.append(
            ShadowEntry(
                policy="pending_timer",
                input_digest=value.timer_observation.digest,
                decision_digest=decision.digest,
                disposition=decision.disposition.value,
            )
        )

    return build_shadow_report(entries)
