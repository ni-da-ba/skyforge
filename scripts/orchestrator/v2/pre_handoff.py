"""Pure pre-handoff validation policy for inert Platform v2."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any

from .identity import canonical_digest


class PreHandoffDisposition(str, Enum):
    ADVANCE_HANDOFF = "ADVANCE_HANDOFF"
    KEEP_EDITING = "KEEP_EDITING"
    BLOCK = "BLOCK"


@dataclass(frozen=True)
class PreHandoffObservation:
    worker_stage: str
    diff_check_ran: bool
    diff_check_passed: bool
    validation_error: str = ""

    def __post_init__(self) -> None:
        if not isinstance(self.worker_stage, str) or not self.worker_stage.strip():
            raise ValueError("worker_stage is required")
        for name in ("diff_check_ran", "diff_check_passed"):
            if not isinstance(getattr(self, name), bool):
                raise ValueError(f"{name} must be a boolean")
        if not isinstance(self.validation_error, str):
            raise ValueError("validation_error must be a string")

    def as_dict(self) -> dict[str, Any]:
        return {
            "worker_stage": self.worker_stage,
            "diff_check_ran": self.diff_check_ran,
            "diff_check_passed": self.diff_check_passed,
            "validation_error": self.validation_error,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class PreHandoffDecision:
    disposition: PreHandoffDisposition
    next_stage: str
    reason: str
    record_error: str
    required_hosted_command: tuple[str, ...]
    observation_digest: str

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "next_stage": self.next_stage,
                "reason": self.reason,
                "record_error": self.record_error,
                "required_hosted_command": list(self.required_hosted_command),
                "observation_digest": self.observation_digest,
            }
        )


HOSTED_PREFLIGHT = ("git", "diff", "--check", "HEAD")


def classify_pre_handoff(
    observation: PreHandoffObservation,
) -> PreHandoffDecision:
    if observation.worker_stage != "editing":
        return PreHandoffDecision(
            PreHandoffDisposition.BLOCK,
            observation.worker_stage,
            "pre-handoff transition is authorized only from editing stage",
            "",
            HOSTED_PREFLIGHT,
            observation.digest,
        )

    if not observation.diff_check_ran:
        return PreHandoffDecision(
            PreHandoffDisposition.BLOCK,
            "editing",
            "hosted git diff --check HEAD has not been proven",
            "",
            HOSTED_PREFLIGHT,
            observation.digest,
        )

    if not observation.diff_check_passed:
        error = observation.validation_error.strip() or "diff check failed"
        return PreHandoffDecision(
            PreHandoffDisposition.KEEP_EDITING,
            "editing",
            "pre-handoff validation failed; keep worker repairable",
            error,
            HOSTED_PREFLIGHT,
            observation.digest,
        )

    if observation.validation_error.strip():
        return PreHandoffDecision(
            PreHandoffDisposition.BLOCK,
            "editing",
            "validation observation is internally inconsistent",
            observation.validation_error.strip(),
            HOSTED_PREFLIGHT,
            observation.digest,
        )

    return PreHandoffDecision(
        PreHandoffDisposition.ADVANCE_HANDOFF,
        "handoff",
        "cheap hosted diff-integrity preflight passed",
        "",
        HOSTED_PREFLIGHT,
        observation.digest,
    )
