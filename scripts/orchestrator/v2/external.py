"""Pure external/manual producer authority policy for inert Platform v2."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any, Iterable, Mapping

from .decision import SourcePRState
from .events import DurableEvent
from .identity import canonical_digest


def _positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer")
    return value


def _optional_positive_int(value: Any, label: str) -> int | None:
    if value is None:
        return None
    return _positive_int(value, label)


def _optional_text(value: Any, label: str) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str):
        raise ValueError(f"{label} must be a string or null")
    text = value.strip()
    return text or None


class ControllerIssueOwner(str, Enum):
    NONE = "NONE"
    PENDING_WORKER = "PENDING_WORKER"
    ACTIVE_ROADMAP = "ACTIVE_ROADMAP"
    MANAGED_PR = "MANAGED_PR"
    PENDING_DECISION = "PENDING_DECISION"


class ClaimAdmissionDisposition(str, Enum):
    ACCEPT = "ACCEPT"
    REJECT_UNTRUSTED = "REJECT_UNTRUSTED"
    REJECT_CONTROLLER_OWNED = "REJECT_CONTROLLER_OWNED"


class ClaimReleaseDisposition(str, Enum):
    RELEASE = "RELEASE"
    KEEP = "KEEP"


class ClaimRetentionDisposition(str, Enum):
    RETIRE = "RETIRE"
    KEEP = "KEEP"


class ExternalPREventDisposition(str, Enum):
    KEEP = "KEEP"
    RETIRE_EXTERNAL = "RETIRE_EXTERNAL"


@dataclass(frozen=True)
class ExternalProducerClaim:
    issue_number: int
    claimed_by: str
    state: str = "active"
    lane: str | None = None
    branch: str | None = None
    pr_number: int | None = None

    def __post_init__(self) -> None:
        _positive_int(self.issue_number, "external claim issue_number")
        if not isinstance(self.claimed_by, str) or not self.claimed_by.strip():
            raise ValueError("external claim claimed_by is required")
        if self.state != "active":
            raise ValueError("external claim state must be active")
        for name in ("lane", "branch"):
            value = getattr(self, name)
            if value is not None and (not isinstance(value, str) or not value.strip()):
                raise ValueError(f"external claim {name} must be non-empty when present")
        _optional_positive_int(self.pr_number, "external claim pr_number")

    @classmethod
    def from_legacy_mapping(cls, raw: Any) -> "ExternalProducerClaim":
        if not isinstance(raw, Mapping):
            raise ValueError("external claim must be an object")
        return cls(
            issue_number=_positive_int(raw.get("issue_number"), "external claim issue_number"),
            claimed_by=str(raw.get("claimed_by") or "").strip(),
            state=str(raw.get("state") or "active").strip(),
            lane=_optional_text(raw.get("lane"), "external claim lane"),
            branch=_optional_text(raw.get("branch"), "external claim branch"),
            pr_number=_optional_positive_int(raw.get("pr_number"), "external claim pr_number"),
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "issue_number": self.issue_number,
            "claimed_by": self.claimed_by,
            "state": self.state,
            "lane": self.lane,
            "branch": self.branch,
            "pr_number": self.pr_number,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class ExternalClaimRequest:
    issue_number: int
    actor: str
    trusted_actor: bool
    lane: str | None = None
    branch: str | None = None
    pr_number: int | None = None

    def __post_init__(self) -> None:
        _positive_int(self.issue_number, "claim request issue_number")
        if not isinstance(self.actor, str) or not self.actor.strip():
            raise ValueError("claim request actor is required")
        if not isinstance(self.trusted_actor, bool):
            raise ValueError("claim request trusted_actor must be a boolean")
        for name in ("lane", "branch"):
            value = getattr(self, name)
            if value is not None and (not isinstance(value, str) or not value.strip()):
                raise ValueError(f"claim request {name} must be non-empty when present")
        _optional_positive_int(self.pr_number, "claim request pr_number")

    def as_dict(self) -> dict[str, Any]:
        return {
            "issue_number": self.issue_number,
            "actor": self.actor,
            "trusted_actor": self.trusted_actor,
            "lane": self.lane,
            "branch": self.branch,
            "pr_number": self.pr_number,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class ClaimAdmissionDecision:
    disposition: ClaimAdmissionDisposition
    reason: str
    request_digest: str
    controller_owner: ControllerIssueOwner

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "request_digest": self.request_digest,
                "controller_owner": self.controller_owner.value,
            }
        )


def classify_claim_admission(
    request: ExternalClaimRequest,
    controller_owner: ControllerIssueOwner,
) -> ClaimAdmissionDecision:
    if not isinstance(controller_owner, ControllerIssueOwner):
        raise ValueError("controller_owner must be ControllerIssueOwner")
    if not request.trusted_actor:
        return ClaimAdmissionDecision(
            ClaimAdmissionDisposition.REJECT_UNTRUSTED,
            "external producer claim actor is not trusted",
            request.digest,
            controller_owner,
        )
    if controller_owner is not ControllerIssueOwner.NONE:
        return ClaimAdmissionDecision(
            ClaimAdmissionDisposition.REJECT_CONTROLLER_OWNED,
            "controller authority already owns the claimed issue",
            request.digest,
            controller_owner,
        )
    return ClaimAdmissionDecision(
        ClaimAdmissionDisposition.ACCEPT,
        "issue is unowned and trusted external producer claim may be recorded",
        request.digest,
        controller_owner,
    )


@dataclass(frozen=True)
class ExternalDispatchDecision:
    hold: bool
    issue_numbers: tuple[int, ...]

    @property
    def digest(self) -> str:
        return canonical_digest(
            {"hold": self.hold, "issue_numbers": list(self.issue_numbers)}
        )


def classify_external_dispatch_hold(
    claims: Iterable[ExternalProducerClaim],
    task_issue_numbers: Iterable[int],
) -> ExternalDispatchDecision:
    task_issues = tuple(sorted({_positive_int(value, "task issue number") for value in task_issue_numbers}))
    active_claims = {claim.issue_number for claim in claims if claim.state == "active"}
    overlap = tuple(issue for issue in task_issues if issue in active_claims)
    return ExternalDispatchDecision(hold=bool(overlap), issue_numbers=overlap)


def classify_claim_release(
    claim: ExternalProducerClaim,
    issue_number: int,
) -> ClaimReleaseDisposition:
    issue = _positive_int(issue_number, "release issue_number")
    return (
        ClaimReleaseDisposition.RELEASE
        if claim.issue_number == issue
        else ClaimReleaseDisposition.KEEP
    )


@dataclass(frozen=True)
class ClaimRetentionDecision:
    disposition: ClaimRetentionDisposition
    reason: str
    claim_digest: str
    remote_pr_state: SourcePRState
    remote_observation_available: bool

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "claim_digest": self.claim_digest,
                "remote_pr_state": self.remote_pr_state.value,
                "remote_observation_available": self.remote_observation_available,
            }
        )


def classify_claim_retention(
    claim: ExternalProducerClaim,
    remote_pr_state: SourcePRState = SourcePRState.UNKNOWN,
    *,
    remote_observation_available: bool = True,
) -> ClaimRetentionDecision:
    if not isinstance(remote_pr_state, SourcePRState):
        raise ValueError("remote_pr_state must be SourcePRState")
    if not isinstance(remote_observation_available, bool):
        raise ValueError("remote_observation_available must be a boolean")
    if claim.pr_number is None:
        return ClaimRetentionDecision(
            ClaimRetentionDisposition.KEEP,
            "claim has no bound PR and requires explicit release",
            claim.digest,
            remote_pr_state,
            remote_observation_available,
        )
    if not remote_observation_available:
        return ClaimRetentionDecision(
            ClaimRetentionDisposition.KEEP,
            "remote PR truth is unavailable; keep claim fail-closed",
            claim.digest,
            remote_pr_state,
            remote_observation_available,
        )
    if remote_pr_state in {SourcePRState.MERGED, SourcePRState.CLOSED}:
        return ClaimRetentionDecision(
            ClaimRetentionDisposition.RETIRE,
            (
                "exact bound PR is provably merged"
                if remote_pr_state is SourcePRState.MERGED
                else "exact bound PR is provably closed without merge"
            ),
            claim.digest,
            remote_pr_state,
            remote_observation_available,
        )
    return ClaimRetentionDecision(
        ClaimRetentionDisposition.KEEP,
        "bound PR is not provably terminal",
        claim.digest,
        remote_pr_state,
        remote_observation_available,
    )


@dataclass(frozen=True)
class ExternalPREventDecision:
    disposition: ExternalPREventDisposition
    reason: str
    event_id: str

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "event_id": self.event_id,
            }
        )


def classify_external_pr_event(
    event: DurableEvent,
    controller_owned_pr_numbers: Iterable[int],
) -> ExternalPREventDecision:
    owned = {
        _positive_int(value, "controller-owned PR number")
        for value in controller_owned_pr_numbers
    }
    if event.event != "workflow_run" or event.pr_number is None:
        return ExternalPREventDecision(
            ExternalPREventDisposition.KEEP,
            "event is not a PR-bound workflow completion",
            event.event_id,
        )

    try:
        pr_number = int(event.pr_number)
    except (TypeError, ValueError):
        return ExternalPREventDecision(
            ExternalPREventDisposition.KEEP,
            "PR identity is ambiguous; preserve event fail-closed",
            event.event_id,
        )
    if pr_number <= 0:
        return ExternalPREventDecision(
            ExternalPREventDisposition.KEEP,
            "PR identity is invalid; preserve event fail-closed",
            event.event_id,
        )
    if pr_number in owned:
        return ExternalPREventDecision(
            ExternalPREventDisposition.KEEP,
            "workflow event belongs to controller-owned PR",
            event.event_id,
        )
    return ExternalPREventDecision(
        ExternalPREventDisposition.RETIRE_EXTERNAL,
        "workflow event belongs to an unowned external PR",
        event.event_id,
    )
