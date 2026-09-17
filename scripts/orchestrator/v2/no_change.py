"""Pure managed-PR no-change retry/escalation policy for inert Platform v2."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any

from .identity import canonical_digest


class NoChangeDisposition(str, Enum):
    NONE = "NONE"
    RETRY_ONCE = "RETRY_ONCE"
    HUMAN_GATE = "HUMAN_GATE"


@dataclass(frozen=True)
class ManagedNoChangeObservation:
    pr_number: int | None
    head_sha: str
    managed_pr_owned: bool
    ci_failed: bool
    retry_already_used: bool
    ordinary_event: bool = False

    def __post_init__(self) -> None:
        if self.pr_number is not None and (
            isinstance(self.pr_number, bool)
            or not isinstance(self.pr_number, int)
            or self.pr_number <= 0
        ):
            raise ValueError("pr_number must be a positive integer or null")
        if not isinstance(self.head_sha, str):
            raise ValueError("head_sha must be a string")
        for name in (
            "managed_pr_owned",
            "ci_failed",
            "retry_already_used",
            "ordinary_event",
        ):
            if not isinstance(getattr(self, name), bool):
                raise ValueError(f"{name} must be a boolean")

    def as_dict(self) -> dict[str, Any]:
        return {
            "pr_number": self.pr_number,
            "head_sha": self.head_sha,
            "managed_pr_owned": self.managed_pr_owned,
            "ci_failed": self.ci_failed,
            "retry_already_used": self.retry_already_used,
            "ordinary_event": self.ordinary_event,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class NoChangeRetryPlan:
    pr_number: int
    head_sha: str

    def __post_init__(self) -> None:
        if isinstance(self.pr_number, bool) or not isinstance(self.pr_number, int) or self.pr_number <= 0:
            raise ValueError("retry plan pr_number must be positive")
        if not isinstance(self.head_sha, str) or not self.head_sha:
            raise ValueError("retry plan head_sha is required")

    @property
    def retry_key(self) -> str:
        return canonical_digest(
            {
                "kind": "managed-pr-no-change-retry",
                "pr_number": self.pr_number,
                "head_sha": self.head_sha,
            }
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "pr_number": self.pr_number,
            "head_sha": self.head_sha,
            "retry_key": self.retry_key,
        }


@dataclass(frozen=True)
class ManagedNoChangeDecision:
    disposition: NoChangeDisposition
    reason: str
    observation_digest: str
    retry_plan: NoChangeRetryPlan | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "observation_digest": self.observation_digest,
                "retry_plan": self.retry_plan.as_dict() if self.retry_plan else None,
            }
        )


def classify_managed_no_change(
    observation: ManagedNoChangeObservation,
) -> ManagedNoChangeDecision:
    if observation.ordinary_event:
        return ManagedNoChangeDecision(
            NoChangeDisposition.NONE,
            "ordinary event retains generic no-followup behavior",
            observation.digest,
        )

    if (
        observation.pr_number is None
        or not observation.head_sha
        or not observation.managed_pr_owned
        or not observation.ci_failed
    ):
        return ManagedNoChangeDecision(
            NoChangeDisposition.NONE,
            "observation is not an exact failed managed-PR no-change case",
            observation.digest,
        )

    if observation.retry_already_used:
        return ManagedNoChangeDecision(
            NoChangeDisposition.HUMAN_GATE,
            "bounded autonomous no-change retry already consumed; escalate to human gate",
            observation.digest,
        )

    return ManagedNoChangeDecision(
        NoChangeDisposition.RETRY_ONCE,
        "first failed managed-PR no-change result receives one bounded reconcile retry",
        observation.digest,
        NoChangeRetryPlan(observation.pr_number, observation.head_sha),
    )
