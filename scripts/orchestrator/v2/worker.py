"""Pure pending-worker recovery policy for inert Platform v2."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any, Mapping

from .decision import SourcePRState
from .identity import canonical_digest


class WorkerRecoveryDisposition(str, Enum):
    DETACH_OPEN_HANDOFF = "DETACH_OPEN_HANDOFF"
    DETACH_MERGED_HANDOFF = "DETACH_MERGED_HANDOFF"
    DETACH_MERGED_EDITING = "DETACH_MERGED_EDITING"
    DISCARD_STALLED_CLEAN = "DISCARD_STALLED_CLEAN"
    BLOCK = "BLOCK"
    DELEGATE_EXISTING_GUARD = "DELEGATE_EXISTING_GUARD"


def _optional_text(value: Any, label: str) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str):
        raise ValueError(f"{label} must be a string or null")
    return value.strip() or None


def _optional_positive_int(value: Any, label: str) -> int | None:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer or null")
    return value


@dataclass(frozen=True)
class WorkerLifecycleState:
    branch: str
    stage: str
    managed_pr: int | None = None
    worktree: str | None = None
    start_head: str | None = None
    authority_key: str | None = None
    worker_retry_circuit_open: bool = False

    @classmethod
    def from_legacy_mapping(cls, raw: Any) -> "WorkerLifecycleState":
        if not isinstance(raw, Mapping):
            raise ValueError("pending worker must be an object")

        branch = raw.get("branch")
        stage = raw.get("stage")
        if not isinstance(branch, str) or not branch.strip():
            raise ValueError("pending worker branch is required")
        if not isinstance(stage, str) or not stage.strip():
            raise ValueError("pending worker stage is required")

        retry_open = raw.get("worker_retry_circuit_open", False)
        if not isinstance(retry_open, bool):
            raise ValueError("worker_retry_circuit_open must be a boolean")

        return cls(
            branch=branch.strip(),
            stage=stage.strip(),
            managed_pr=_optional_positive_int(
                raw.get("managed_pr"), "pending worker managed_pr"
            ),
            worktree=_optional_text(raw.get("worktree"), "pending worker worktree"),
            start_head=_optional_text(
                raw.get("start_head"), "pending worker start_head"
            ),
            authority_key=_optional_text(
                raw.get("authority_key"), "pending worker authority_key"
            ),
            worker_retry_circuit_open=retry_open,
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "branch": self.branch,
            "stage": self.stage,
            "managed_pr": self.managed_pr,
            "worktree": self.worktree,
            "start_head": self.start_head,
            "authority_key": self.authority_key,
            "worker_retry_circuit_open": self.worker_retry_circuit_open,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class WorkerRecoveryObservation:
    controller_paused: bool
    worktree_available: bool = False
    worktree_is_controller: bool = False
    managed_owner_present: bool = False
    managed_branch: str = ""
    remote_pr_state: SourcePRState = SourcePRState.UNKNOWN
    remote_pr_merged_at_present: bool = False
    remote_pr_branch: str = ""
    current_head: str = ""
    changed_paths: tuple[str, ...] = ()

    def __post_init__(self) -> None:
        for name in (
            "controller_paused",
            "worktree_available",
            "worktree_is_controller",
            "managed_owner_present",
            "remote_pr_merged_at_present",
        ):
            if not isinstance(getattr(self, name), bool):
                raise ValueError(f"{name} must be a boolean")
        if not isinstance(self.remote_pr_state, SourcePRState):
            raise ValueError("remote_pr_state must be SourcePRState")
        for name in ("managed_branch", "remote_pr_branch", "current_head"):
            if not isinstance(getattr(self, name), str):
                raise ValueError(f"{name} must be a string")
        if not isinstance(self.changed_paths, tuple) or any(
            not isinstance(path, str) or not path.strip()
            for path in self.changed_paths
        ):
            raise ValueError("changed_paths must be a tuple of non-empty strings")

    def as_dict(self) -> dict[str, Any]:
        return {
            "controller_paused": self.controller_paused,
            "worktree_available": self.worktree_available,
            "worktree_is_controller": self.worktree_is_controller,
            "managed_owner_present": self.managed_owner_present,
            "managed_branch": self.managed_branch,
            "remote_pr_state": self.remote_pr_state.value,
            "remote_pr_merged_at_present": self.remote_pr_merged_at_present,
            "remote_pr_branch": self.remote_pr_branch,
            "current_head": self.current_head,
            "changed_paths": list(self.changed_paths),
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class WorkerRecoveryDecision:
    disposition: WorkerRecoveryDisposition
    reason: str
    worker_digest: str
    observation_digest: str

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "worker_digest": self.worker_digest,
                "observation_digest": self.observation_digest,
            }
        )


def _decision(
    worker: WorkerLifecycleState,
    observation: WorkerRecoveryObservation,
    disposition: WorkerRecoveryDisposition,
    reason: str,
) -> WorkerRecoveryDecision:
    return WorkerRecoveryDecision(
        disposition=disposition,
        reason=reason,
        worker_digest=worker.digest,
        observation_digest=observation.digest,
    )


def _managed_recovery_guard(
    worker: WorkerLifecycleState,
    observation: WorkerRecoveryObservation,
) -> str | None:
    if not observation.managed_owner_present:
        return "managed PR is not owned by durable orchestrator state"
    if not observation.managed_branch or observation.managed_branch != worker.branch:
        return "managed branch identity drift"
    if (
        observation.remote_pr_branch
        and observation.remote_pr_branch != worker.branch
    ):
        return "live PR branch identity drift"
    if not worker.worktree:
        return "managed-worker recovery lacks isolated worktree identity"
    if not observation.worktree_available:
        return "managed-worker worktree is unavailable"
    if observation.worktree_is_controller:
        return "refusing to recover controller checkout as worker"
    return None


def classify_worker_recovery(
    worker: WorkerLifecycleState,
    observation: WorkerRecoveryObservation,
) -> WorkerRecoveryDecision:
    """Classify a recovery request without executing any side effects."""

    if not observation.controller_paused:
        return _decision(
            worker,
            observation,
            WorkerRecoveryDisposition.BLOCK,
            "worker recovery requires paused controller",
        )

    if worker.managed_pr is not None and worker.stage in {"handoff", "editing"}:
        guard = _managed_recovery_guard(worker, observation)
        if guard:
            return _decision(
                worker,
                observation,
                WorkerRecoveryDisposition.BLOCK,
                guard,
            )

        if worker.stage == "handoff":
            if (
                observation.remote_pr_state is SourcePRState.MERGED
                and observation.remote_pr_merged_at_present
            ):
                return _decision(
                    worker,
                    observation,
                    WorkerRecoveryDisposition.DETACH_MERGED_HANDOFF,
                    "exact managed PR is already merged; archive local delta before retirement",
                )
            if observation.remote_pr_state is SourcePRState.OPEN:
                return _decision(
                    worker,
                    observation,
                    WorkerRecoveryDisposition.DETACH_OPEN_HANDOFF,
                    "open managed handoff may detach while preserving PR ownership",
                )
            return _decision(
                worker,
                observation,
                WorkerRecoveryDisposition.BLOCK,
                "managed handoff PR is neither open nor provably merged",
            )

        if (
            observation.remote_pr_state is SourcePRState.MERGED
            and observation.remote_pr_merged_at_present
        ):
            return _decision(
                worker,
                observation,
                WorkerRecoveryDisposition.DETACH_MERGED_EDITING,
                "editing worker exact managed PR is already merged; archive before retirement",
            )
        return _decision(
            worker,
            observation,
            WorkerRecoveryDisposition.BLOCK,
            "managed editing worker remains protected unless exact PR is provably merged",
        )

    if (
        worker.managed_pr is None
        and worker.stage == "editing"
        and worker.worker_retry_circuit_open
    ):
        if not worker.worktree:
            return _decision(
                worker,
                observation,
                WorkerRecoveryDisposition.BLOCK,
                "stalled editing-worker discard lacks isolated worktree identity",
            )
        if not observation.worktree_available:
            return _decision(
                worker,
                observation,
                WorkerRecoveryDisposition.BLOCK,
                "stalled editing-worker worktree is unavailable",
            )
        if observation.worktree_is_controller:
            return _decision(
                worker,
                observation,
                WorkerRecoveryDisposition.BLOCK,
                "refusing to discard controller checkout as worker",
            )
        if not worker.start_head:
            return _decision(
                worker,
                observation,
                WorkerRecoveryDisposition.BLOCK,
                "stalled editing-worker discard lacks recorded start HEAD",
            )
        if not observation.current_head or observation.current_head != worker.start_head:
            return _decision(
                worker,
                observation,
                WorkerRecoveryDisposition.BLOCK,
                "stalled editing-worker HEAD moved or cannot be proven unchanged",
            )
        if observation.changed_paths:
            return _decision(
                worker,
                observation,
                WorkerRecoveryDisposition.BLOCK,
                "stalled editing-worker has local changes",
            )
        return _decision(
            worker,
            observation,
            WorkerRecoveryDisposition.DISCARD_STALLED_CLEAN,
            "retry-circuit worker is clean and remains at recorded start HEAD",
        )

    return _decision(
        worker,
        observation,
        WorkerRecoveryDisposition.DELEGATE_EXISTING_GUARD,
        "worker state is outside explicit v2 recovery cases",
    )
