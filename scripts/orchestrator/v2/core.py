"""Pure Platform v2 managed-PR controller core.

The production controller does not import this module.  It converts a typed snapshot of
current durable JSON state plus a repository observation into a deterministic transition
plan.  All network and mutation execution remains outside the pure core.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping

from .domain import (
    CIState,
    MechanicalSnapshot,
    PRClass,
    TransitionKind,
    TransitionPlan,
    decide_mechanical,
)
from .identity import canonical_digest


def _optional_text(mapping: Mapping[str, Any], key: str) -> str:
    value = mapping.get(key)
    if value is None:
        return ""
    if not isinstance(value, str):
        raise ValueError(f"{key} must be a string when present")
    return value.strip()


def _positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer")
    return value


@dataclass(frozen=True)
class ManagedPRState:
    lane: str
    pr_number: int
    branch: str
    authority_key: str = ""
    expected_head: str = ""
    auto_merge_eligible: bool = False
    changed_paths: tuple[str, ...] = ()

    @classmethod
    def from_legacy(cls, lane: str, raw: Any) -> "ManagedPRState":
        lane_text = str(lane or "").strip()
        if not lane_text:
            raise ValueError("managed PR lane is required")
        if not isinstance(raw, Mapping):
            raise ValueError(f"managed.{lane_text} must be an object")

        branch = raw.get("branch")
        if not isinstance(branch, str) or not branch.strip():
            raise ValueError(f"managed.{lane_text}.branch is required")

        eligible = raw.get("auto_merge_eligible", False)
        if not isinstance(eligible, bool):
            raise ValueError(f"managed.{lane_text}.auto_merge_eligible must be a boolean")

        changed = raw.get("changed_paths", [])
        if changed is None:
            changed = []
        if not isinstance(changed, list) or any(
            not isinstance(path, str) or not path.strip() for path in changed
        ):
            raise ValueError(f"managed.{lane_text}.changed_paths must be a list of paths")

        return cls(
            lane=lane_text,
            pr_number=_positive_int(raw.get("pr_number"), f"managed.{lane_text}.pr_number"),
            branch=branch.strip(),
            authority_key=_optional_text(raw, "authority_key"),
            expected_head=_optional_text(raw, "expected_head"),
            auto_merge_eligible=eligible,
            changed_paths=tuple(sorted({path.strip() for path in changed})),
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "lane": self.lane,
            "pr_number": self.pr_number,
            "branch": self.branch,
            "authority_key": self.authority_key,
            "expected_head": self.expected_head,
            "auto_merge_eligible": self.auto_merge_eligible,
            "changed_paths": list(self.changed_paths),
        }


@dataclass(frozen=True)
class PendingWorkerState:
    branch: str
    authority_key: str = ""

    @classmethod
    def from_legacy(cls, raw: Any) -> "PendingWorkerState":
        if not isinstance(raw, Mapping):
            raise ValueError("pending_worker must be an object or null")
        branch = raw.get("branch")
        if not isinstance(branch, str) or not branch.strip():
            raise ValueError("pending_worker.branch is required")
        return cls(
            branch=branch.strip(),
            authority_key=_optional_text(raw, "authority_key"),
        )

    def as_dict(self) -> dict[str, str]:
        return {"branch": self.branch, "authority_key": self.authority_key}


@dataclass(frozen=True)
class HumanGateRecord:
    key: str
    token: str
    target: str
    seeded_from_github: bool = False

    @classmethod
    def from_legacy(cls, key: str, raw: Any) -> "HumanGateRecord":
        key_text = str(key or "").strip()
        if not key_text:
            raise ValueError("human gate key is required")
        if not isinstance(raw, Mapping):
            raise ValueError(f"human_gate_records.{key_text} must be an object")

        token = raw.get("token")
        target = raw.get("target")
        if not isinstance(token, str) or not token.strip():
            raise ValueError(f"human_gate_records.{key_text}.token is required")
        if not isinstance(target, (str, int)) or isinstance(target, bool) or not str(target).strip():
            raise ValueError(f"human_gate_records.{key_text}.target is required")

        seeded = raw.get("seeded_from_github", False)
        if not isinstance(seeded, bool):
            raise ValueError(
                f"human_gate_records.{key_text}.seeded_from_github must be a boolean"
            )
        return cls(
            key=key_text,
            token=token.strip(),
            target=str(target).strip(),
            seeded_from_github=seeded,
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "key": self.key,
            "token": self.token,
            "target": self.target,
            "seeded_from_github": self.seeded_from_github,
        }


@dataclass(frozen=True)
class ControllerState:
    managed: tuple[ManagedPRState, ...] = ()
    pending_worker: PendingWorkerState | None = None
    human_gates: tuple[HumanGateRecord, ...] = ()
    paused: bool = False

    @classmethod
    def from_legacy_mapping(cls, mapping: Any) -> "ControllerState":
        if not isinstance(mapping, Mapping):
            raise ValueError("legacy controller state must be an object")

        managed_raw = mapping.get("managed", {})
        if not isinstance(managed_raw, Mapping):
            raise ValueError("managed must be an object")
        managed = tuple(
            sorted(
                (
                    ManagedPRState.from_legacy(str(lane), record)
                    for lane, record in managed_raw.items()
                ),
                key=lambda record: record.lane,
            )
        )

        pending_raw = mapping.get("pending_worker")
        pending = (
            None
            if pending_raw is None
            else PendingWorkerState.from_legacy(pending_raw)
        )

        gates_raw = mapping.get("human_gate_records", {})
        if not isinstance(gates_raw, Mapping):
            raise ValueError("human_gate_records must be an object")
        gates = tuple(
            sorted(
                (
                    HumanGateRecord.from_legacy(str(key), record)
                    for key, record in gates_raw.items()
                ),
                key=lambda record: record.key,
            )
        )

        paused = mapping.get("paused", False)
        if not isinstance(paused, bool):
            raise ValueError("paused must be a boolean")

        return cls(
            managed=managed,
            pending_worker=pending,
            human_gates=gates,
            paused=paused,
        )

    def managed_for(self, lane: str) -> ManagedPRState | None:
        for record in self.managed:
            if record.lane == lane:
                return record
        return None

    def as_dict(self) -> dict[str, Any]:
        return {
            "managed": [record.as_dict() for record in self.managed],
            "pending_worker": (
                self.pending_worker.as_dict() if self.pending_worker is not None else None
            ),
            "human_gates": [record.as_dict() for record in self.human_gates],
            "paused": self.paused,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class ManagedPRObservation:
    lane: str
    pr_number: int
    active_pr: bool
    base_branch: str
    head_branch: str
    current_head_sha: str
    evidence_sha: str
    reviewed_sha: str
    task_spec_hash: str
    accepted_task_spec_hash: str
    ci_state: CIState
    pr_class: PRClass = PRClass.DELIVERY
    human_gate_pending: bool = False
    review_required: bool = False

    def __post_init__(self) -> None:
        if not self.lane.strip():
            raise ValueError("observation lane is required")
        _positive_int(self.pr_number, "observation pr_number")
        if not isinstance(self.active_pr, bool):
            raise ValueError("observation active_pr must be a boolean")
        if not isinstance(self.ci_state, CIState):
            raise ValueError("observation ci_state must be CIState")
        if not isinstance(self.pr_class, PRClass):
            raise ValueError("observation pr_class must be PRClass")
        for name in (
            "base_branch",
            "head_branch",
            "current_head_sha",
            "evidence_sha",
            "reviewed_sha",
            "task_spec_hash",
            "accepted_task_spec_hash",
        ):
            if not isinstance(getattr(self, name), str):
                raise ValueError(f"observation {name} must be a string")
        if not isinstance(self.human_gate_pending, bool):
            raise ValueError("observation human_gate_pending must be a boolean")
        if not isinstance(self.review_required, bool):
            raise ValueError("observation review_required must be a boolean")

    def as_dict(self) -> dict[str, Any]:
        return {
            "lane": self.lane,
            "pr_number": self.pr_number,
            "active_pr": self.active_pr,
            "base_branch": self.base_branch,
            "head_branch": self.head_branch,
            "current_head_sha": self.current_head_sha,
            "evidence_sha": self.evidence_sha,
            "reviewed_sha": self.reviewed_sha,
            "task_spec_hash": self.task_spec_hash,
            "accepted_task_spec_hash": self.accepted_task_spec_hash,
            "ci_state": self.ci_state.value,
            "pr_class": self.pr_class.value,
            "human_gate_pending": self.human_gate_pending,
            "review_required": self.review_required,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class CoreDecision:
    transition: TransitionPlan
    state_digest: str
    observation_digest: str

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "transition": self.transition.as_dict(),
                "state_digest": self.state_digest,
                "observation_digest": self.observation_digest,
            }
        )


def _decision(
    state: ControllerState,
    observation: ManagedPRObservation,
    kind: TransitionKind,
    reason: str,
) -> CoreDecision:
    return CoreDecision(
        transition=TransitionPlan(kind, reason),
        state_digest=state.digest,
        observation_digest=observation.digest,
    )


def reduce_managed_pr(
    state: ControllerState,
    observation: ManagedPRObservation,
) -> CoreDecision:
    """Purely evaluate one managed PR observation against current durable state."""

    if not observation.active_pr:
        return _decision(
            state,
            observation,
            TransitionKind.NOOP,
            "observed PR is not active",
        )

    managed = state.managed_for(observation.lane)
    if managed is None or managed.pr_number != observation.pr_number:
        return _decision(
            state,
            observation,
            TransitionKind.RECONCILE,
            "observed PR is not owned by the durable managed-state record",
        )

    if observation.base_branch != "main":
        return _decision(
            state,
            observation,
            TransitionKind.RECONCILE,
            "managed PR does not target main",
        )

    if observation.head_branch != managed.branch:
        return _decision(
            state,
            observation,
            TransitionKind.RECONCILE,
            "managed PR branch identity drifted from durable state",
        )

    if managed.expected_head and observation.current_head_sha != managed.expected_head:
        return _decision(
            state,
            observation,
            TransitionKind.INVALIDATE_EVIDENCE,
            "current PR head does not match durable expected head",
        )

    if not managed.authority_key or not managed.expected_head:
        return _decision(
            state,
            observation,
            TransitionKind.RECONCILE,
            "durable managed record lacks authority or expected-head identity",
        )

    if not managed.auto_merge_eligible:
        return _decision(
            state,
            observation,
            TransitionKind.HUMAN_GATE,
            "managed PR is outside the machine-only auto-merge policy",
        )

    mechanical = MechanicalSnapshot(
        pr_class=observation.pr_class,
        active_pr=True,
        current_head_sha=observation.current_head_sha,
        evidence_sha=observation.evidence_sha,
        reviewed_sha=observation.reviewed_sha,
        task_spec_hash=observation.task_spec_hash,
        accepted_task_spec_hash=observation.accepted_task_spec_hash,
        ci_state=observation.ci_state,
        human_gate_pending=observation.human_gate_pending,
        pending_worker=state.pending_worker is not None,
        review_required=observation.review_required,
    )
    plan = decide_mechanical(mechanical)
    return CoreDecision(
        transition=plan,
        state_digest=state.digest,
        observation_digest=observation.digest,
    )
