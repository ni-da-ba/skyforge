"""Durable Release-4 pre-staged documentation canary executor.

This is the first Platform-v2 mutation-capable adapter boundary. It is not imported by
legacy production. The executor can only create and merge one pre-staged documentation
PR after the pure R4A guard authorizes the exact task and a WriterFence is held.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum
from pathlib import Path
from typing import Any, Mapping, Protocol

from .canary import (
    CanaryGuardDisposition,
    CanaryTaskContract,
    LegacyCanaryExclusion,
    MutationGateRecord,
    WRITER_FENCE_RELATIVE_PATH,
    evaluate_canary_guard,
)
from .domain import CIState, MechanicalSnapshot, PRClass, TransitionKind, decide_mechanical
from .effects import (
    EffectKind,
    EffectReconcileDisposition,
    EffectStatus,
    RemoteEffectIdentity,
    RemoteEffectObservation,
    RemoteEffectPresence,
    RemoteEffectRecord,
    reconcile_remote_effect,
)
from .fence import WriterFence
from .identity import canonical_digest
from .ownership import OwnershipToken
from .state_store import JsonStateStoreAdapter


CANARY_STATE_RELATIVE_PATH = ".skyforge-platform-v2/canary-state.json"


class CanaryRemoteUnavailable(RuntimeError):
    """Raised when exact remote truth cannot be established safely."""


class CanaryExecutionDisposition(str, Enum):
    BLOCKED = "BLOCKED"
    PR_CREATED = "PR_CREATED"
    PR_RECONCILED = "PR_RECONCILED"
    WAIT_CI = "WAIT_CI"
    MERGED = "MERGED"
    MERGE_RECONCILED = "MERGE_RECONCILED"
    COMPLETE = "COMPLETE"


@dataclass(frozen=True)
class CanaryPRSnapshot:
    pr_number: int
    state: str
    head_branch: str
    head_sha: str
    base_branch: str
    title: str
    body: str
    changed_paths: tuple[str, ...]
    ci_state: CIState

    def __post_init__(self) -> None:
        if isinstance(self.pr_number, bool) or not isinstance(self.pr_number, int) or self.pr_number <= 0:
            raise ValueError("pr_number must be a positive integer")
        if self.state not in {"OPEN", "MERGED", "CLOSED"}:
            raise ValueError("state must be OPEN, MERGED, or CLOSED")
        if not isinstance(self.ci_state, CIState):
            raise ValueError("ci_state must be CIState")
        for name in ("head_branch", "head_sha", "base_branch", "title", "body"):
            if not isinstance(getattr(self, name), str):
                raise ValueError(f"{name} must be a string")
        if any(not isinstance(path, str) or not path for path in self.changed_paths):
            raise ValueError("changed_paths must contain non-empty strings")

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "pr_number": self.pr_number,
                "state": self.state,
                "head_branch": self.head_branch,
                "head_sha": self.head_sha,
                "base_branch": self.base_branch,
                "title": self.title,
                "body": self.body,
                "changed_paths": list(self.changed_paths),
                "ci_state": self.ci_state.value,
            }
        )


class CanaryRemoteAdapter(Protocol):
    def current_main_sha(self) -> str: ...
    def branch_head_sha(self, branch: str) -> str: ...
    def observe_pr(self, task: CanaryTaskContract) -> CanaryPRSnapshot | None: ...
    def create_pr(self, task: CanaryTaskContract) -> CanaryPRSnapshot: ...
    def merge_pr(self, pr_number: int, expected_head_sha: str) -> None: ...


@dataclass(frozen=True)
class CanaryDurableState:
    schema_version: int = 1
    attempt_id: str = ""
    task_spec_hash: str = ""
    base_sha: str = ""
    expected_head_sha: str = ""
    branch: str = ""
    pr_number: int | None = None
    evidence_sha: str = ""
    reviewed_sha: str = ""
    accepted_task_spec_hash: str = ""
    create_pr_effect: RemoteEffectRecord | None = None
    merge_pr_effect: RemoteEffectRecord | None = None
    completed: bool = False

    @classmethod
    def empty(cls) -> "CanaryDurableState":
        return cls()

    @staticmethod
    def _effect_from_mapping(raw: Any) -> RemoteEffectRecord | None:
        if raw is None:
            return None
        if not isinstance(raw, Mapping):
            raise ValueError("effect record must be an object or null")
        identity_raw = raw.get("identity")
        if not isinstance(identity_raw, Mapping):
            raise ValueError("effect identity must be an object")
        try:
            kind = EffectKind(str(identity_raw.get("kind") or ""))
            status = EffectStatus(str(raw.get("status") or ""))
        except ValueError as exc:
            raise ValueError("effect record contains unknown enum") from exc
        identity = RemoteEffectIdentity.create(
            attempt_id=str(identity_raw.get("attempt_id") or ""),
            kind=kind,
            subject=str(identity_raw.get("subject") or ""),
        )
        expected_effect_id = str(identity_raw.get("effect_id") or "")
        if expected_effect_id != identity.effect_id:
            raise ValueError("effect_id does not match canonical effect identity")
        return RemoteEffectRecord(
            identity=identity,
            status=status,
            remote_identity=str(raw.get("remote_identity") or ""),
        )

    @classmethod
    def from_mapping(cls, raw: Any) -> "CanaryDurableState":
        if raw is None or raw == {}:
            return cls.empty()
        if not isinstance(raw, Mapping):
            raise ValueError("canary durable state must be an object")
        if raw.get("schema_version") != 1:
            raise ValueError("unsupported canary state schema_version")
        pr_number = raw.get("pr_number")
        if pr_number is not None and (
            isinstance(pr_number, bool) or not isinstance(pr_number, int) or pr_number <= 0
        ):
            raise ValueError("pr_number must be null or positive")
        completed = raw.get("completed", False)
        if not isinstance(completed, bool):
            raise ValueError("completed must be boolean")
        return cls(
            schema_version=1,
            attempt_id=str(raw.get("attempt_id") or ""),
            task_spec_hash=str(raw.get("task_spec_hash") or ""),
            base_sha=str(raw.get("base_sha") or ""),
            expected_head_sha=str(raw.get("expected_head_sha") or ""),
            branch=str(raw.get("branch") or ""),
            pr_number=pr_number,
            evidence_sha=str(raw.get("evidence_sha") or ""),
            reviewed_sha=str(raw.get("reviewed_sha") or ""),
            accepted_task_spec_hash=str(raw.get("accepted_task_spec_hash") or ""),
            create_pr_effect=cls._effect_from_mapping(raw.get("create_pr_effect")),
            merge_pr_effect=cls._effect_from_mapping(raw.get("merge_pr_effect")),
            completed=completed,
        )

    @staticmethod
    def _effect_dict(record: RemoteEffectRecord | None) -> dict[str, Any] | None:
        if record is None:
            return None
        return {
            "identity": {
                "attempt_id": record.identity.attempt_id,
                "kind": record.identity.kind.value,
                "subject": record.identity.subject,
                "effect_id": record.identity.effect_id,
            },
            "status": record.status.value,
            "remote_identity": record.remote_identity,
        }

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "attempt_id": self.attempt_id,
            "task_spec_hash": self.task_spec_hash,
            "base_sha": self.base_sha,
            "expected_head_sha": self.expected_head_sha,
            "branch": self.branch,
            "pr_number": self.pr_number,
            "evidence_sha": self.evidence_sha,
            "reviewed_sha": self.reviewed_sha,
            "accepted_task_spec_hash": self.accepted_task_spec_hash,
            "create_pr_effect": self._effect_dict(self.create_pr_effect),
            "merge_pr_effect": self._effect_dict(self.merge_pr_effect),
            "completed": self.completed,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class CanaryExecutionResult:
    disposition: CanaryExecutionDisposition
    reason: str
    state: CanaryDurableState
    pr_snapshot_digest: str = ""

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "state_digest": self.state.digest,
                "pr_snapshot_digest": self.pr_snapshot_digest,
            }
        )


class InjectedCanaryCrash(RuntimeError):
    """Test-only injected crash after remote mutation and before local completion."""


def canary_state_store(root: Path) -> JsonStateStoreAdapter:
    path = Path(root) / CANARY_STATE_RELATIVE_PATH
    return JsonStateStoreAdapter(path=path, backup_path=path.with_name(path.name + ".bak"))


def _load_state(store: JsonStateStoreAdapter) -> CanaryDurableState:
    return CanaryDurableState.from_mapping(store.load().as_dict())


def _save_state(store: JsonStateStoreAdapter, state: CanaryDurableState) -> CanaryDurableState:
    store.save(state.as_dict())
    return state


def _block(reason: str, state: CanaryDurableState) -> CanaryExecutionResult:
    return CanaryExecutionResult(CanaryExecutionDisposition.BLOCKED, reason, state)


def _validate_exact_pr(task: CanaryTaskContract, snapshot: CanaryPRSnapshot) -> str | None:
    if snapshot.head_branch != task.head_branch:
        return "observed PR head branch does not match frozen canary branch"
    if snapshot.head_sha != task.expected_head_sha:
        return "observed PR head SHA moved from frozen canary candidate"
    if snapshot.base_branch != "main":
        return "observed PR does not target main"
    if snapshot.title != task.pr_title or snapshot.body != task.pr_body:
        return "observed PR metadata conflicts with frozen canary spec"
    if tuple(sorted(set(snapshot.changed_paths))) != (task.documentation_path,):
        return "observed PR changed paths exceed the single authorized canary artifact"
    return None


def _effect_observation_for_create(
    task: CanaryTaskContract,
    snapshot: CanaryPRSnapshot | None,
) -> RemoteEffectObservation:
    if snapshot is None:
        return RemoteEffectObservation(RemoteEffectPresence.ABSENT)
    conflict = _validate_exact_pr(task, snapshot)
    if conflict is not None:
        return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
    return RemoteEffectObservation(
        RemoteEffectPresence.PRESENT_EXACT,
        remote_identity=f"pr:{snapshot.pr_number}",
    )


def _effect_observation_for_merge(
    task: CanaryTaskContract,
    snapshot: CanaryPRSnapshot | None,
) -> RemoteEffectObservation:
    if snapshot is None:
        return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
    conflict = _validate_exact_pr(task, snapshot)
    if conflict is not None:
        return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
    if snapshot.state == "MERGED":
        return RemoteEffectObservation(
            RemoteEffectPresence.PRESENT_EXACT,
            remote_identity=f"merge:pr:{snapshot.pr_number}@{task.expected_head_sha}",
        )
    if snapshot.state == "OPEN":
        return RemoteEffectObservation(RemoteEffectPresence.ABSENT)
    return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)


def advance_canary(
    *,
    root: Path,
    legacy_state: Mapping[str, Any],
    gate: MutationGateRecord,
    task: CanaryTaskContract,
    ownership_token: OwnershipToken,
    attempt_number: int,
    remote: CanaryRemoteAdapter,
    crash_after_create: bool = False,
    crash_after_merge: bool = False,
) -> CanaryExecutionResult:
    """Advance one exact canary lifecycle while preserving crash-safe effects.

    Read-only reconciliation of an already-recorded PENDING effect is allowed before
    current authorization/base checks so a crash after a successful remote mutation can
    be completed locally without attempting the mutation again. Any *new* remote
    mutation still requires the full R4A guard, exact base/head identity, and writer
    fence.
    """

    store = canary_state_store(root)
    fence_path = Path(root) / WRITER_FENCE_RELATIVE_PATH
    with WriterFence.for_token(fence_path, ownership_token):
        state = _load_state(store)
        spec = task.frozen_spec()
        attempt = task.attempt(attempt_number)

        if state.attempt_id:
            immutable = {
                "attempt_id": attempt.attempt_id,
                "task_spec_hash": spec.spec_hash,
                "base_sha": task.base_sha,
                "expected_head_sha": task.expected_head_sha,
                "branch": task.head_branch,
            }
            for field, expected in immutable.items():
                if getattr(state, field) != expected:
                    return _block(
                        f"durable canary state belongs to a different frozen attempt: {field}",
                        state,
                    )

        if state.completed:
            return CanaryExecutionResult(
                CanaryExecutionDisposition.COMPLETE,
                "canary attempt is already durably complete",
                state,
            )

        # First reconcile already-recorded effects. This is intentionally allowed before
        # the mutation guard because MARK_COMPLETE/NOOP_COMPLETE performs no remote write.
        snapshot: CanaryPRSnapshot | None = None
        if state.create_pr_effect is not None or state.merge_pr_effect is not None:
            try:
                snapshot = remote.observe_pr(task)
            except CanaryRemoteUnavailable:
                return _block("cannot establish remote effect truth", state)

        create_identity = RemoteEffectIdentity.create(
            attempt_id=attempt.attempt_id,
            kind=EffectKind.CREATE_PR,
            subject=f"{task.head_branch}->{task.base_sha}",
        )
        if state.create_pr_effect is not None:
            if state.create_pr_effect.identity != create_identity:
                return _block("durable CREATE_PR effect identity drifted", state)
            create_reconcile = reconcile_remote_effect(
                state.create_pr_effect,
                _effect_observation_for_create(task, snapshot),
            )
            if create_reconcile.disposition is EffectReconcileDisposition.BLOCK:
                return _block(create_reconcile.reason, state)
            if create_reconcile.disposition is EffectReconcileDisposition.MARK_COMPLETE:
                if snapshot is None:
                    return _block("CREATE_PR reconciliation lost exact remote snapshot", state)
                state = replace(
                    state,
                    pr_number=snapshot.pr_number,
                    create_pr_effect=state.create_pr_effect.complete(
                        create_reconcile.remote_identity
                    ),
                )
                _save_state(store, state)
                return CanaryExecutionResult(
                    CanaryExecutionDisposition.PR_RECONCILED,
                    "existing exact canary PR reconciled after restart",
                    state,
                    snapshot.digest,
                )
            if (
                create_reconcile.disposition is EffectReconcileDisposition.NOOP_COMPLETE
                and snapshot is None
            ):
                return _block(
                    "durably completed CREATE_PR no longer exists remotely",
                    state,
                )

        if state.merge_pr_effect is not None:
            if state.pr_number is None:
                return _block("MERGE_PR effect exists without durable PR identity", state)
            merge_identity = RemoteEffectIdentity.create(
                attempt_id=attempt.attempt_id,
                kind=EffectKind.MERGE_PR,
                subject=f"pr:{state.pr_number}@{task.expected_head_sha}",
            )
            if state.merge_pr_effect.identity != merge_identity:
                return _block("durable MERGE_PR effect identity drifted", state)
            merge_reconcile = reconcile_remote_effect(
                state.merge_pr_effect,
                _effect_observation_for_merge(task, snapshot),
            )
            if merge_reconcile.disposition is EffectReconcileDisposition.BLOCK:
                return _block(merge_reconcile.reason, state)
            if merge_reconcile.disposition is EffectReconcileDisposition.MARK_COMPLETE:
                state = replace(
                    state,
                    merge_pr_effect=state.merge_pr_effect.complete(
                        merge_reconcile.remote_identity
                    ),
                    completed=True,
                )
                _save_state(store, state)
                return CanaryExecutionResult(
                    CanaryExecutionDisposition.MERGE_RECONCILED,
                    "remote merge reconciled after restart without re-execution",
                    state,
                    snapshot.digest if snapshot else "",
                )
            if merge_reconcile.disposition is EffectReconcileDisposition.NOOP_COMPLETE:
                if snapshot is None or snapshot.state != "MERGED":
                    return _block(
                        "completed MERGE_PR lacks exact merged remote truth",
                        state,
                    )
                if not state.completed:
                    state = replace(state, completed=True)
                    _save_state(store, state)
                return CanaryExecutionResult(
                    CanaryExecutionDisposition.COMPLETE,
                    "merge effect is already durably complete",
                    state,
                    snapshot.digest,
                )
            # EXECUTE is handled only after the full mutation guard below.

        exclusion = LegacyCanaryExclusion.from_legacy_state(
            legacy_state,
            issue_number=task.issue_number,
        )
        try:
            current_main = remote.current_main_sha()
            branch_head = remote.branch_head_sha(task.head_branch)
        except CanaryRemoteUnavailable:
            return _block("remote repository truth is unavailable", state)

        guard = evaluate_canary_guard(
            gate=gate,
            task=task,
            exclusion=exclusion,
            current_main=current_main,
            ownership_token=ownership_token,
            attempt_number=attempt_number,
            execute_requested=True,
        )
        if guard.disposition is not CanaryGuardDisposition.ALLOW_MUTATION:
            return _block(guard.reason, state)
        if branch_head != task.expected_head_sha:
            return _block("candidate branch head moved from frozen expected SHA", state)

        if not state.attempt_id:
            state = replace(
                state,
                attempt_id=attempt.attempt_id,
                task_spec_hash=spec.spec_hash,
                base_sha=task.base_sha,
                expected_head_sha=task.expected_head_sha,
                branch=task.head_branch,
            )
            _save_state(store, state)

        # Refresh when no prior-effect reconciliation needed an observation.
        if snapshot is None:
            try:
                snapshot = remote.observe_pr(task)
            except CanaryRemoteUnavailable:
                return _block("cannot establish remote CREATE_PR truth", state)

        # CREATE_PR: PENDING must exist durably before any create call.
        if state.create_pr_effect is None:
            state = replace(
                state,
                create_pr_effect=RemoteEffectRecord.begin(create_identity),
            )
            _save_state(store, state)

        create_reconcile = reconcile_remote_effect(
            state.create_pr_effect,
            _effect_observation_for_create(task, snapshot),
        )
        if create_reconcile.disposition is EffectReconcileDisposition.BLOCK:
            return _block(create_reconcile.reason, state)
        if create_reconcile.disposition is EffectReconcileDisposition.EXECUTE:
            # Recheck exact base/head immediately before the first remote mutation.
            try:
                create_main = remote.current_main_sha()
                create_head = remote.branch_head_sha(task.head_branch)
            except CanaryRemoteUnavailable:
                return _block("pre-create repository truth is unavailable", state)
            create_guard = evaluate_canary_guard(
                gate=gate,
                task=task,
                exclusion=exclusion,
                current_main=create_main,
                ownership_token=ownership_token,
                attempt_number=attempt_number,
                execute_requested=True,
            )
            if create_guard.disposition is not CanaryGuardDisposition.ALLOW_MUTATION:
                return _block(create_guard.reason, state)
            if create_head != task.expected_head_sha:
                return _block("candidate branch head moved before PR creation", state)
            try:
                snapshot = remote.create_pr(task)
            except CanaryRemoteUnavailable:
                return _block(
                    "CREATE_PR outcome is unknown; pending effect preserved",
                    state,
                )
            conflict = _validate_exact_pr(task, snapshot)
            if conflict is not None:
                return _block(conflict, state)
            if crash_after_create:
                raise InjectedCanaryCrash("injected crash after CREATE_PR")
            state = replace(
                state,
                pr_number=snapshot.pr_number,
                create_pr_effect=state.create_pr_effect.complete(
                    f"pr:{snapshot.pr_number}"
                ),
            )
            _save_state(store, state)
            return CanaryExecutionResult(
                CanaryExecutionDisposition.PR_CREATED,
                "exact canary PR created and durably recorded",
                state,
                snapshot.digest,
            )
        if create_reconcile.disposition is EffectReconcileDisposition.MARK_COMPLETE:
            if snapshot is None:
                return _block("CREATE_PR reconciliation lost exact remote snapshot", state)
            state = replace(
                state,
                pr_number=snapshot.pr_number,
                create_pr_effect=state.create_pr_effect.complete(
                    create_reconcile.remote_identity
                ),
            )
            _save_state(store, state)
            return CanaryExecutionResult(
                CanaryExecutionDisposition.PR_RECONCILED,
                "pre-existing exact canary PR reconciled without duplicate creation",
                state,
                snapshot.digest,
            )

        # CREATE_PR is now complete; exact PR truth is mandatory.
        if snapshot is None:
            try:
                snapshot = remote.observe_pr(task)
            except CanaryRemoteUnavailable:
                return _block("cannot refresh exact canary PR truth", state)
        if snapshot is None:
            return _block("durably completed CREATE_PR no longer exists remotely", state)
        conflict = _validate_exact_pr(task, snapshot)
        if conflict is not None:
            return _block(conflict, state)
        if state.pr_number is not None and state.pr_number != snapshot.pr_number:
            return _block("remote PR number drifted from durable canary state", state)
        if state.pr_number is None:
            state = replace(state, pr_number=snapshot.pr_number)
            _save_state(store, state)

        if snapshot.state == "MERGED":
            return _block(
                "PR is merged remotely without a matching durable MERGE_PR effect",
                state,
            )
        if snapshot.state != "OPEN":
            return _block("canary PR closed without the authorized merge", state)

        if snapshot.ci_state is not CIState.PASS:
            return CanaryExecutionResult(
                CanaryExecutionDisposition.WAIT_CI,
                f"exact canary PR CI is {snapshot.ci_state.value}",
                state,
                snapshot.digest,
            )

        # Structural review for the first canary is exact immutable metadata/head/path.
        state = replace(
            state,
            evidence_sha=task.expected_head_sha,
            reviewed_sha=task.expected_head_sha,
            accepted_task_spec_hash=spec.spec_hash,
        )
        _save_state(store, state)

        plan = decide_mechanical(
            MechanicalSnapshot(
                pr_class=PRClass.DELIVERY,
                active_pr=True,
                current_head_sha=snapshot.head_sha,
                evidence_sha=state.evidence_sha,
                reviewed_sha=state.reviewed_sha,
                task_spec_hash=state.task_spec_hash,
                accepted_task_spec_hash=state.accepted_task_spec_hash,
                ci_state=snapshot.ci_state,
            )
        )
        if plan.kind is not TransitionKind.MERGE_ELIGIBLE:
            return _block(
                f"exact canary acceptance did not reach MERGE_ELIGIBLE: {plan.reason}",
                state,
            )

        # Recheck base/head immediately before recording/executing the merge effect.
        try:
            latest_main = remote.current_main_sha()
            latest_branch = remote.branch_head_sha(task.head_branch)
        except CanaryRemoteUnavailable:
            return _block("pre-merge repository truth is unavailable", state)
        guard = evaluate_canary_guard(
            gate=gate,
            task=task,
            exclusion=exclusion,
            current_main=latest_main,
            ownership_token=ownership_token,
            attempt_number=attempt_number,
            execute_requested=True,
        )
        if guard.disposition is not CanaryGuardDisposition.ALLOW_MUTATION:
            return _block(guard.reason, state)
        if latest_branch != task.expected_head_sha:
            return _block("candidate branch head moved before merge", state)

        merge_identity = RemoteEffectIdentity.create(
            attempt_id=attempt.attempt_id,
            kind=EffectKind.MERGE_PR,
            subject=f"pr:{snapshot.pr_number}@{task.expected_head_sha}",
        )
        if state.merge_pr_effect is None:
            state = replace(
                state,
                merge_pr_effect=RemoteEffectRecord.begin(merge_identity),
            )
            _save_state(store, state)
        elif state.merge_pr_effect.identity != merge_identity:
            return _block("durable MERGE_PR effect identity drifted", state)

        try:
            latest = remote.observe_pr(task)
        except CanaryRemoteUnavailable:
            return _block("cannot establish remote MERGE_PR truth", state)

        merge_reconcile = reconcile_remote_effect(
            state.merge_pr_effect,
            _effect_observation_for_merge(task, latest),
        )
        if merge_reconcile.disposition is EffectReconcileDisposition.BLOCK:
            return _block(merge_reconcile.reason, state)
        if merge_reconcile.disposition is EffectReconcileDisposition.MARK_COMPLETE:
            state = replace(
                state,
                merge_pr_effect=state.merge_pr_effect.complete(
                    merge_reconcile.remote_identity
                ),
                completed=True,
            )
            _save_state(store, state)
            return CanaryExecutionResult(
                CanaryExecutionDisposition.MERGE_RECONCILED,
                "already-merged exact canary reconciled without duplicate merge",
                state,
                latest.digest if latest else "",
            )
        if merge_reconcile.disposition is EffectReconcileDisposition.NOOP_COMPLETE:
            if not state.completed:
                state = replace(state, completed=True)
                _save_state(store, state)
            return CanaryExecutionResult(
                CanaryExecutionDisposition.COMPLETE,
                "merge effect already complete",
                state,
                latest.digest if latest else "",
            )

        if latest is None or latest.state != "OPEN":
            return _block("merge execution requires exact open PR snapshot", state)
        try:
            remote.merge_pr(latest.pr_number, task.expected_head_sha)
        except CanaryRemoteUnavailable:
            return _block(
                "MERGE_PR outcome is unknown; pending effect preserved",
                state,
            )
        if crash_after_merge:
            raise InjectedCanaryCrash("injected crash after MERGE_PR")

        try:
            merged_snapshot = remote.observe_pr(task)
        except CanaryRemoteUnavailable:
            return _block(
                "merge executed but confirmation unavailable; pending effect preserved",
                state,
            )
        observation = _effect_observation_for_merge(task, merged_snapshot)
        if observation.presence is not RemoteEffectPresence.PRESENT_EXACT:
            return _block(
                "merge executed but exact merged state is not yet provable",
                state,
            )

        state = replace(
            state,
            merge_pr_effect=state.merge_pr_effect.complete(
                observation.remote_identity
            ),
            completed=True,
        )
        _save_state(store, state)
        return CanaryExecutionResult(
            CanaryExecutionDisposition.MERGED,
            "exact-head canary merge completed and durably recorded",
            state,
            merged_snapshot.digest if merged_snapshot else "",
        )
