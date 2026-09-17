"""Pure managed-PR base refresh/rebase policy for inert Platform v2."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any

from .core import ManagedPRState
from .decision import SourcePRState
from .identity import canonical_digest


class ManagedMergeState(str, Enum):
    CLEAN = "CLEAN"
    BEHIND = "BEHIND"
    DIRTY = "DIRTY"
    UNKNOWN = "UNKNOWN"


class RebaseDisposition(str, Enum):
    BLOCK_PENDING_WORKER = "BLOCK_PENDING_WORKER"
    NOOP_CLEAN = "NOOP_CLEAN"
    REBASE_REQUIRED = "REBASE_REQUIRED"
    RECONCILE = "RECONCILE"


class RebaseResultDisposition(str, Enum):
    ADVANCE_HEAD_AND_DEFER = "ADVANCE_HEAD_AND_DEFER"
    PAUSE_HUMAN_GATE = "PAUSE_HUMAN_GATE"
    BLOCK = "BLOCK"


@dataclass(frozen=True)
class ManagedRebaseObservation:
    remote_pr_state: SourcePRState
    base_branch: str
    head_branch: str
    head_sha: str
    merge_state: ManagedMergeState

    def __post_init__(self) -> None:
        if not isinstance(self.remote_pr_state, SourcePRState):
            raise ValueError("remote_pr_state must be SourcePRState")
        if not isinstance(self.merge_state, ManagedMergeState):
            raise ValueError("merge_state must be ManagedMergeState")
        for name in ("base_branch", "head_branch", "head_sha"):
            value = getattr(self, name)
            if not isinstance(value, str):
                raise ValueError(f"{name} must be a string")

    def as_dict(self) -> dict[str, Any]:
        return {
            "remote_pr_state": self.remote_pr_state.value,
            "base_branch": self.base_branch,
            "head_branch": self.head_branch,
            "head_sha": self.head_sha,
            "merge_state": self.merge_state.value,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class RebasePlan:
    lane: str
    pr_number: int
    branch: str
    expected_old_head: str
    base_branch: str = "main"

    def __post_init__(self) -> None:
        if not self.lane or not self.branch or not self.expected_old_head:
            raise ValueError("rebase plan requires lane, branch, and expected old head")
        if isinstance(self.pr_number, bool) or not isinstance(self.pr_number, int) or self.pr_number <= 0:
            raise ValueError("rebase plan pr_number must be positive")
        if self.base_branch != "main":
            raise ValueError("managed rebase plan must target main")

    @property
    def force_with_lease(self) -> str:
        return f"refs/heads/{self.branch}:{self.expected_old_head}"

    def as_dict(self) -> dict[str, Any]:
        return {
            "lane": self.lane,
            "pr_number": self.pr_number,
            "branch": self.branch,
            "expected_old_head": self.expected_old_head,
            "base_branch": self.base_branch,
            "force_with_lease": self.force_with_lease,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class RebaseDecision:
    disposition: RebaseDisposition
    reason: str
    managed_digest: str
    observation_digest: str
    plan: RebasePlan | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "managed_digest": self.managed_digest,
                "observation_digest": self.observation_digest,
                "plan": self.plan.as_dict() if self.plan else None,
            }
        )


def _managed_digest(managed: ManagedPRState) -> str:
    return canonical_digest(managed.as_dict())


def classify_managed_rebase(
    managed: ManagedPRState,
    observation: ManagedRebaseObservation,
    *,
    pending_worker: bool,
) -> RebaseDecision:
    if not isinstance(pending_worker, bool):
        raise ValueError("pending_worker must be a boolean")
    managed_digest = _managed_digest(managed)

    if pending_worker:
        return RebaseDecision(
            RebaseDisposition.BLOCK_PENDING_WORKER,
            "pending worker prevents managed PR base mutation",
            managed_digest,
            observation.digest,
        )

    if observation.remote_pr_state is not SourcePRState.OPEN:
        return RebaseDecision(
            RebaseDisposition.RECONCILE,
            "managed PR is not provably open",
            managed_digest,
            observation.digest,
        )

    if observation.base_branch != "main":
        return RebaseDecision(
            RebaseDisposition.RECONCILE,
            "managed PR no longer targets main",
            managed_digest,
            observation.digest,
        )

    if observation.head_branch != managed.branch:
        return RebaseDecision(
            RebaseDisposition.RECONCILE,
            "managed PR branch identity drift",
            managed_digest,
            observation.digest,
        )

    if not managed.expected_head or observation.head_sha != managed.expected_head:
        return RebaseDecision(
            RebaseDisposition.RECONCILE,
            "managed PR head differs from durable expected head",
            managed_digest,
            observation.digest,
        )

    if observation.merge_state is ManagedMergeState.CLEAN:
        return RebaseDecision(
            RebaseDisposition.NOOP_CLEAN,
            "managed PR is already clean against main",
            managed_digest,
            observation.digest,
        )

    if observation.merge_state in {ManagedMergeState.BEHIND, ManagedMergeState.DIRTY}:
        plan = RebasePlan(
            lane=managed.lane,
            pr_number=managed.pr_number,
            branch=managed.branch,
            expected_old_head=managed.expected_head,
        )
        return RebaseDecision(
            RebaseDisposition.REBASE_REQUIRED,
            "exact managed PR requires bounded rebase onto current main",
            managed_digest,
            observation.digest,
            plan,
        )

    return RebaseDecision(
        RebaseDisposition.RECONCILE,
        "merge state is not sufficient to authorize rebase",
        managed_digest,
        observation.digest,
    )


@dataclass(frozen=True)
class RebaseResultDecision:
    disposition: RebaseResultDisposition
    reason: str
    plan_digest: str
    new_expected_head: str = ""
    dispatch_defer_seconds: int = 0

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "plan_digest": self.plan_digest,
                "new_expected_head": self.new_expected_head,
                "dispatch_defer_seconds": self.dispatch_defer_seconds,
            }
        )


def classify_rebase_result(
    plan: RebasePlan,
    *,
    rebase_succeeded: bool,
    conflict: bool,
    observed_old_head: str,
    new_head: str = "",
) -> RebaseResultDecision:
    if not isinstance(rebase_succeeded, bool) or not isinstance(conflict, bool):
        raise ValueError("rebase_succeeded and conflict must be booleans")
    if not isinstance(observed_old_head, str) or not isinstance(new_head, str):
        raise ValueError("rebase result heads must be strings")

    if observed_old_head != plan.expected_old_head:
        return RebaseResultDecision(
            RebaseResultDisposition.BLOCK,
            "rebase worktree did not start at the exact durable expected head",
            plan.digest,
        )

    if conflict:
        return RebaseResultDecision(
            RebaseResultDisposition.PAUSE_HUMAN_GATE,
            "rebase conflict requires safety pause and human gate before model spend",
            plan.digest,
        )

    if not rebase_succeeded:
        return RebaseResultDecision(
            RebaseResultDisposition.BLOCK,
            "rebase failed without a classified conflict",
            plan.digest,
        )

    if not new_head or new_head == plan.expected_old_head:
        return RebaseResultDecision(
            RebaseResultDisposition.BLOCK,
            "successful rebase did not produce a distinct provable new head",
            plan.digest,
        )

    return RebaseResultDecision(
        RebaseResultDisposition.ADVANCE_HEAD_AND_DEFER,
        "rebase advanced exact managed head; persist new expected head and re-observe before dispatch",
        plan.digest,
        new_expected_head=new_head,
        dispatch_defer_seconds=30,
    )
