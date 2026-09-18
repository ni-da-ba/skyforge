"""Prepared-worker ordinary execution composition for Platform v2 Release 5.

R5C2 deliberately begins after task selection/provider work.  It composes the accepted
bounded workspace adapter and durable ordinary remote effects into a restart-safe handoff
and exact managed-PR merge path.  It is not imported by the hosted production runtime.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from pathlib import Path
import re
from typing import Callable

from .core import ControllerState, ManagedPRObservation
from .domain import TransitionKind, TransitionPlan
from .identity import canonical_digest
from .ordinary_effect_executor import (
    OrdinaryEffectAdapter,
    OrdinaryEffectExecutionDisposition,
    OrdinaryEffectExecutionResult,
    advance_remote_effect,
)
from .ordinary_effects import (
    OrdinaryEffectStore,
    OrdinaryMutationScope,
)
from .ordinary_remote import OrdinaryEffectBinding
from .workspace_commit import (
    WorkspaceCommitAdapter,
    WorkspaceCommitResult,
)


_PR_REMOTE_RE = re.compile(r"^pr:(?P<number>[1-9][0-9]*):(?P<state>[A-Z_]+)@(?P<head>[0-9a-f]{40})$")


def _required(value: str, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


@dataclass(frozen=True)
class PreparedWorkerTask:
    task_id: str
    authority_key: str
    task_spec_hash: str
    attempt_id: str
    repo: str
    base_sha: str
    branch: str
    lane: str
    objective: str
    pr_title: str
    pr_body: str
    issue_number: int | None = None
    comment_body: str = ""
    auto_merge_eligible: bool = False

    def __post_init__(self) -> None:
        for name in (
            "task_id",
            "authority_key",
            "task_spec_hash",
            "attempt_id",
            "repo",
            "base_sha",
            "branch",
            "lane",
            "objective",
            "pr_title",
            "pr_body",
        ):
            object.__setattr__(self, name, _required(getattr(self, name), name))
        for name in ("task_spec_hash", "attempt_id"):
            value = getattr(self, name)
            if len(value) != 64 or any(ch not in "0123456789abcdef" for ch in value):
                raise ValueError(f"{name} must be lowercase SHA-256 hex")
        if len(self.base_sha) != 40 or any(ch not in "0123456789abcdef" for ch in self.base_sha):
            raise ValueError("base_sha must be lowercase 40-character Git SHA")
        if self.issue_number is not None and (
            isinstance(self.issue_number, bool)
            or not isinstance(self.issue_number, int)
            or self.issue_number <= 0
        ):
            raise ValueError("issue_number must be positive when present")
        if not isinstance(self.auto_merge_eligible, bool):
            raise ValueError("auto_merge_eligible must be boolean")

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "task_id": self.task_id,
                "authority_key": self.authority_key,
                "task_spec_hash": self.task_spec_hash,
                "attempt_id": self.attempt_id,
                "repo": self.repo,
                "base_sha": self.base_sha,
                "branch": self.branch,
                "lane": self.lane,
                "objective": self.objective,
                "pr_title": self.pr_title,
                "pr_body": self.pr_body,
                "issue_number": self.issue_number,
                "comment_body": self.comment_body,
                "auto_merge_eligible": self.auto_merge_eligible,
            }
        )


@dataclass(frozen=True)
class ManagedOrdinaryHandoff:
    task_id: str
    authority_key: str
    task_spec_hash: str
    lane: str
    scope: OrdinaryMutationScope
    pr_number: int
    changed_paths: tuple[str, ...]
    auto_merge_eligible: bool

    def __post_init__(self) -> None:
        if isinstance(self.pr_number, bool) or not isinstance(self.pr_number, int) or self.pr_number <= 0:
            raise ValueError("pr_number must be positive")
        if not isinstance(self.scope, OrdinaryMutationScope):
            raise ValueError("scope must be OrdinaryMutationScope")
        if not isinstance(self.changed_paths, tuple):
            raise ValueError("changed_paths must be tuple")
        if not isinstance(self.auto_merge_eligible, bool):
            raise ValueError("auto_merge_eligible must be boolean")

    def as_dict(self) -> dict[str, object]:
        return {
            "task_id": self.task_id,
            "authority_key": self.authority_key,
            "task_spec_hash": self.task_spec_hash,
            "lane": self.lane,
            "scope": self.scope.as_dict(),
            "pr_number": self.pr_number,
            "changed_paths": list(self.changed_paths),
            "auto_merge_eligible": self.auto_merge_eligible,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


class OrdinaryServiceDisposition(str, Enum):
    COMPLETE = "COMPLETE"
    BLOCKED = "BLOCKED"
    NOT_ELIGIBLE = "NOT_ELIGIBLE"


@dataclass(frozen=True)
class OrdinaryHandoffResult:
    disposition: OrdinaryServiceDisposition
    reason: str
    stage: str
    workspace: WorkspaceCommitResult
    handoff: ManagedOrdinaryHandoff | None = None
    effect_result: OrdinaryEffectExecutionResult | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "stage": self.stage,
                "workspace_digest": self.workspace.digest,
                "handoff_digest": self.handoff.digest if self.handoff else "",
                "effect_digest": self.effect_result.digest if self.effect_result else "",
            }
        )


@dataclass(frozen=True)
class OrdinaryMergeResult:
    disposition: OrdinaryServiceDisposition
    reason: str
    transition: TransitionPlan
    handoff: ManagedOrdinaryHandoff
    effect_result: OrdinaryEffectExecutionResult | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "transition": self.transition.as_dict(),
                "handoff_digest": self.handoff.digest,
                "effect_digest": self.effect_result.digest if self.effect_result else "",
            }
        )


RemoteFactory = Callable[[OrdinaryEffectBinding], OrdinaryEffectAdapter]


def _effect_ok(result: OrdinaryEffectExecutionResult) -> bool:
    return result.disposition in {
        OrdinaryEffectExecutionDisposition.EXECUTED,
        OrdinaryEffectExecutionDisposition.RECONCILED,
        OrdinaryEffectExecutionDisposition.ALREADY_COMPLETE,
    }


def _parse_open_pr_remote(remote_identity: str, expected_head: str) -> int:
    match = _PR_REMOTE_RE.fullmatch(str(remote_identity or ""))
    if not match:
        raise ValueError("CREATE_PR remote identity is malformed")
    if match.group("head") != expected_head:
        raise ValueError("CREATE_PR remote identity head drifted")
    if match.group("state") != "OPEN":
        raise ValueError("ordinary handoff requires exact open PR")
    return int(match.group("number"))


def advance_prepared_handoff(
    *,
    task: PreparedWorkerTask,
    workspace: WorkspaceCommitAdapter,
    store: OrdinaryEffectStore,
    remote_factory: RemoteFactory,
) -> OrdinaryHandoffResult:
    if task.attempt_id != workspace.scope.attempt_id:
        raise ValueError("prepared task attempt_id does not match worker scope")
    if task.branch != workspace.scope.branch:
        raise ValueError("prepared task branch does not match worker scope")
    if task.base_sha != workspace.scope.start_head:
        raise ValueError("prepared task base does not match worker start_head")

    commit = workspace.commit(lane=task.lane, objective=task.objective)
    if not commit.commit_created and commit.head_sha == workspace.scope.start_head:
        return OrdinaryHandoffResult(
            OrdinaryServiceDisposition.NOT_ELIGIBLE,
            "prepared worker produced no bounded change",
            "WORKSPACE_NO_CHANGE",
            commit,
        )

    scope = OrdinaryMutationScope(
        attempt_id=task.attempt_id,
        repo=task.repo,
        base_sha=task.base_sha,
        branch=task.branch,
        expected_head_sha=commit.head_sha,
        pr_title=task.pr_title,
        pr_body=task.pr_body,
        issue_number=task.issue_number,
    )

    push_binding = OrdinaryEffectBinding(scope=scope, identity=scope.push_identity())
    push = advance_remote_effect(
        store=store,
        identity=push_binding.identity,
        adapter=remote_factory(push_binding),
    )
    if not _effect_ok(push):
        return OrdinaryHandoffResult(
            OrdinaryServiceDisposition.BLOCKED,
            push.reason,
            "PUSH_BRANCH",
            commit,
            effect_result=push,
        )

    create_binding = OrdinaryEffectBinding(
        scope=scope,
        identity=scope.create_pr_identity(),
    )
    create_adapter = remote_factory(create_binding)
    create = advance_remote_effect(
        store=store,
        identity=create_binding.identity,
        adapter=create_adapter,
    )
    if not _effect_ok(create):
        return OrdinaryHandoffResult(
            OrdinaryServiceDisposition.BLOCKED,
            create.reason,
            "CREATE_PR",
            commit,
            effect_result=create,
        )

    # Completed effects are durable intent history, but handoff needs current exact PR
    # truth.  Re-observe so a closed/moved PR cannot become a managed record merely
    # because CREATE_PR completed earlier.
    current_pr = create_adapter.observe(create_binding.identity)
    if current_pr.presence.value != "PRESENT_EXACT":
        return OrdinaryHandoffResult(
            OrdinaryServiceDisposition.BLOCKED,
            "completed CREATE_PR lacks exact current remote PR truth",
            "CREATE_PR_REOBSERVE",
            commit,
            effect_result=create,
        )
    try:
        pr_number = _parse_open_pr_remote(
            current_pr.remote_identity,
            scope.expected_head_sha,
        )
    except ValueError as exc:
        return OrdinaryHandoffResult(
            OrdinaryServiceDisposition.BLOCKED,
            str(exc),
            "CREATE_PR_REOBSERVE",
            commit,
            effect_result=create,
        )

    if task.comment_body:
        comment_binding = OrdinaryEffectBinding(
            scope=scope,
            identity=scope.comment_identity(task.comment_body),
            comment_body=task.comment_body,
        )
        comment = advance_remote_effect(
            store=store,
            identity=comment_binding.identity,
            adapter=remote_factory(comment_binding),
        )
        if not _effect_ok(comment):
            return OrdinaryHandoffResult(
                OrdinaryServiceDisposition.BLOCKED,
                comment.reason,
                "POST_COMMENT",
                commit,
                effect_result=comment,
            )

    handoff = ManagedOrdinaryHandoff(
        task_id=task.task_id,
        authority_key=task.authority_key,
        task_spec_hash=task.task_spec_hash,
        lane=task.lane,
        scope=scope,
        pr_number=pr_number,
        changed_paths=commit.changed_paths,
        auto_merge_eligible=task.auto_merge_eligible,
    )
    return OrdinaryHandoffResult(
        OrdinaryServiceDisposition.COMPLETE,
        "bounded worker handoff reached exact managed draft PR",
        "COMPLETE",
        commit,
        handoff=handoff,
    )


def advance_managed_merge(
    *,
    state: ControllerState,
    observation: ManagedPRObservation,
    handoff: ManagedOrdinaryHandoff,
    store: OrdinaryEffectStore,
    remote_factory: RemoteFactory,
) -> OrdinaryMergeResult:
    from .core import reduce_managed_pr

    managed = state.managed_for(handoff.lane)
    if (
        managed is None
        or managed.pr_number != handoff.pr_number
        or managed.branch != handoff.scope.branch
        or managed.authority_key != handoff.authority_key
        or managed.expected_head != handoff.scope.expected_head_sha
        or managed.auto_merge_eligible != handoff.auto_merge_eligible
    ):
        return OrdinaryMergeResult(
            OrdinaryServiceDisposition.BLOCKED,
            "durable managed state does not match frozen handoff identity",
            TransitionPlan(TransitionKind.RECONCILE, "managed handoff identity mismatch"),
            handoff,
        )

    decision = reduce_managed_pr(state, observation)
    if decision.transition.kind is not TransitionKind.MERGE_ELIGIBLE:
        return OrdinaryMergeResult(
            OrdinaryServiceDisposition.NOT_ELIGIBLE,
            decision.transition.reason,
            decision.transition,
            handoff,
        )

    identity = handoff.scope.merge_identity(handoff.pr_number)
    binding = OrdinaryEffectBinding(
        scope=handoff.scope,
        identity=identity,
        pr_number=handoff.pr_number,
    )
    effect = advance_remote_effect(
        store=store,
        identity=identity,
        adapter=remote_factory(binding),
    )
    if not _effect_ok(effect):
        return OrdinaryMergeResult(
            OrdinaryServiceDisposition.BLOCKED,
            effect.reason,
            decision.transition,
            handoff,
            effect_result=effect,
        )

    return OrdinaryMergeResult(
        OrdinaryServiceDisposition.COMPLETE,
        "exact managed PR merge effect completed/reconciled",
        decision.transition,
        handoff,
        effect_result=effect,
    )
