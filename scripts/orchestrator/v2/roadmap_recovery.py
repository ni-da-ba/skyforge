"""Pure closed-active roadmap issue recovery policy for inert Platform v2."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any

from .identity import canonical_digest


class RoadmapIssueState(str, Enum):
    OPEN = "OPEN"
    CLOSED = "CLOSED"
    UNKNOWN = "UNKNOWN"


class ClosedActiveDisposition(str, Enum):
    COMPLETE_NODE = "COMPLETE_NODE"
    DELEGATE_ROADMAP = "DELEGATE_ROADMAP"
    DELEGATE_PR = "DELEGATE_PR"
    BLOCK = "BLOCK"


@dataclass(frozen=True)
class ClosedActiveRoadmapObservation:
    node_id: str
    issue_number: int
    max_runs: int
    issue_state: RoadmapIssueState
    pr_number: int | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.node_id, str) or not self.node_id.strip():
            raise ValueError("node_id is required")
        for name in ("issue_number", "max_runs"):
            value = getattr(self, name)
            if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
                raise ValueError(f"{name} must be a positive integer")
        if not isinstance(self.issue_state, RoadmapIssueState):
            raise ValueError("issue_state must be RoadmapIssueState")
        if self.pr_number is not None and (
            isinstance(self.pr_number, bool)
            or not isinstance(self.pr_number, int)
            or self.pr_number <= 0
        ):
            raise ValueError("pr_number must be a positive integer or null")

    def as_dict(self) -> dict[str, Any]:
        return {
            "node_id": self.node_id.strip(),
            "issue_number": self.issue_number,
            "max_runs": self.max_runs,
            "issue_state": self.issue_state.value,
            "pr_number": self.pr_number,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class ClosedActiveRoadmapDecision:
    disposition: ClosedActiveDisposition
    reason: str
    observation_digest: str
    clear_active: bool = False
    completed_runs_target: int | None = None
    closed_issue_number: int | None = None

    def __post_init__(self) -> None:
        if self.completed_runs_target is not None and self.completed_runs_target <= 0:
            raise ValueError("completed_runs_target must be positive when present")
        if self.closed_issue_number is not None and self.closed_issue_number <= 0:
            raise ValueError("closed_issue_number must be positive when present")

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "observation_digest": self.observation_digest,
                "clear_active": self.clear_active,
                "completed_runs_target": self.completed_runs_target,
                "closed_issue_number": self.closed_issue_number,
            }
        )


def classify_closed_active_roadmap(
    observation: ClosedActiveRoadmapObservation,
) -> ClosedActiveRoadmapDecision:
    if observation.pr_number is not None:
        return ClosedActiveRoadmapDecision(
            ClosedActiveDisposition.DELEGATE_PR,
            "active roadmap node has a bound PR; managed-PR lifecycle remains authoritative",
            observation.digest,
        )

    if observation.issue_state is RoadmapIssueState.OPEN:
        return ClosedActiveRoadmapDecision(
            ClosedActiveDisposition.DELEGATE_ROADMAP,
            "active roadmap issue is still open; use ordinary roadmap lifecycle",
            observation.digest,
        )

    if observation.issue_state is RoadmapIssueState.CLOSED:
        return ClosedActiveRoadmapDecision(
            ClosedActiveDisposition.COMPLETE_NODE,
            "active issue closed without a bound PR; complete full node and clear active ownership",
            observation.digest,
            clear_active=True,
            completed_runs_target=observation.max_runs,
            closed_issue_number=observation.issue_number,
        )

    return ClosedActiveRoadmapDecision(
        ClosedActiveDisposition.BLOCK,
        "issue state is unknown; do not complete active roadmap work",
        observation.digest,
    )
