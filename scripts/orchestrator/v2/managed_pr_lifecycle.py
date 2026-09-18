"""Exact managed PR ready/merge lifecycle for Platform v2 R5C12.

This module remains unhooked from the hosted production runtime. It composes the accepted
R5C11 truth observer with the durable exactly-once ordinary effect ledger.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from pathlib import Path
import subprocess
from typing import Sequence

from .domain import TransitionKind
from .effects import (
    EffectKind,
    RemoteEffectIdentity,
    RemoteEffectObservation,
    RemoteEffectPresence,
)
from .identity import canonical_digest
from .managed_pr_observation import (
    GhManagedPRObserver,
    ManagedPRRemoteUnavailable,
    ManagedPRTruthDisposition,
    ManagedPRTruthResult,
    decide_managed_pr_truth,
)
from .ordinary_effect_executor import (
    OrdinaryEffectExecutionDisposition,
    OrdinaryEffectExecutionResult,
    OrdinaryRemoteUnavailable,
    advance_remote_effect,
)
from .ordinary_effects import OrdinaryEffectStore
from .ordinary_service import ManagedOrdinaryHandoff


class ManagedLifecycleDisposition(str, Enum):
    COMPLETE = "COMPLETE"
    BLOCKED = "BLOCKED"
    NOT_ELIGIBLE = "NOT_ELIGIBLE"


@dataclass(frozen=True)
class ManagedLifecycleResult:
    disposition: ManagedLifecycleDisposition
    reason: str
    handoff_digest: str
    transition_kind: str = ""
    ready_effect: OrdinaryEffectExecutionResult | None = None
    merge_effect: OrdinaryEffectExecutionResult | None = None
    truth_digest: str = ""

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "handoff_digest": self.handoff_digest,
                "transition_kind": self.transition_kind,
                "ready_effect_digest": (
                    self.ready_effect.digest if self.ready_effect is not None else ""
                ),
                "merge_effect_digest": (
                    self.merge_effect.digest if self.merge_effect is not None else ""
                ),
                "truth_digest": self.truth_digest,
            }
        )


def _effect_ok(result: OrdinaryEffectExecutionResult) -> bool:
    return result.disposition in {
        OrdinaryEffectExecutionDisposition.EXECUTED,
        OrdinaryEffectExecutionDisposition.RECONCILED,
        OrdinaryEffectExecutionDisposition.ALREADY_COMPLETE,
    }


class ManagedLifecycleMutationValidator:
    """Allow only ready/merge commands for one exact durable handoff."""

    def __init__(self, handoff: ManagedOrdinaryHandoff) -> None:
        if not isinstance(handoff, ManagedOrdinaryHandoff):
            raise ValueError("handoff must be ManagedOrdinaryHandoff")
        self.handoff = handoff

    def ready_command(self) -> tuple[str, ...]:
        scope = self.handoff.scope
        return (
            "gh",
            "pr",
            "ready",
            str(self.handoff.pr_number),
            "--repo",
            scope.repo,
        )

    def merge_command(self) -> tuple[str, ...]:
        scope = self.handoff.scope
        return (
            "gh",
            "pr",
            "merge",
            str(self.handoff.pr_number),
            "--repo",
            scope.repo,
            "--merge",
            "--match-head-commit",
            scope.expected_head_sha,
        )

    def validate(self, args: Sequence[str]) -> tuple[str, ...]:
        command = tuple(str(value) for value in args)
        if command not in {self.ready_command(), self.merge_command()}:
            raise ValueError(
                "command is outside the exact Platform-v2 managed lifecycle mutation allowlist"
            )
        return command


class ManagedLifecycleEffectAdapter:
    """Effect adapter that rechecks full R5C11 truth inside each mutation boundary."""

    def __init__(
        self,
        *,
        root: Path,
        handoff: ManagedOrdinaryHandoff,
        identity: RemoteEffectIdentity,
        runner=subprocess.run,
    ) -> None:
        self.root = Path(root).resolve()
        self.handoff = handoff
        self.identity = identity
        self.runner = runner
        self.validator = ManagedLifecycleMutationValidator(handoff)

        expected = self._expected_identity(identity.kind)
        if identity != expected:
            raise ValueError("managed lifecycle effect identity does not match frozen handoff")

    def _expected_identity(self, kind: EffectKind) -> RemoteEffectIdentity:
        if kind is EffectKind.UPDATE_PR:
            return self.handoff.scope.ready_identity(self.handoff.pr_number)
        if kind is EffectKind.MERGE_PR:
            return self.handoff.scope.merge_identity(self.handoff.pr_number)
        raise ValueError("managed lifecycle adapter supports only UPDATE_PR and MERGE_PR")

    def _truth(self) -> ManagedPRTruthResult:
        try:
            return GhManagedPRObserver(
                root=self.root,
                handoff=self.handoff,
                runner=self.runner,
            ).observe()
        except ManagedPRRemoteUnavailable as exc:
            raise OrdinaryRemoteUnavailable(str(exc)) from exc

    def _exact_truth(self) -> ManagedPRTruthResult:
        truth = self._truth()
        if truth.disposition is not ManagedPRTruthDisposition.OBSERVED:
            raise OrdinaryRemoteUnavailable(
                f"managed PR truth rejected: {truth.reason}"
            )
        return truth

    def _run_mutation(self, args: Sequence[str]) -> None:
        command = self.validator.validate(args)
        try:
            self.runner(
                list(command),
                cwd=self.root,
                check=True,
                text=True,
                capture_output=True,
                timeout=120,
            )
        except subprocess.SubprocessError as exc:
            raise OrdinaryRemoteUnavailable(str(exc)) from exc

    def observe(
        self,
        identity: RemoteEffectIdentity,
    ) -> RemoteEffectObservation:
        if identity != self.identity:
            raise ValueError("adapter is bound to a different effect identity")

        truth = self._truth()
        if truth.disposition is not ManagedPRTruthDisposition.OBSERVED:
            return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)

        scope = self.handoff.scope
        number = self.handoff.pr_number

        if identity.kind is EffectKind.UPDATE_PR:
            if truth.remote_state == "MERGED":
                return RemoteEffectObservation(
                    RemoteEffectPresence.PRESENT_EXACT,
                    remote_identity=f"ready:pr:{number}@{scope.expected_head_sha}",
                )
            if truth.remote_state != "OPEN":
                return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
            if truth.is_draft:
                return RemoteEffectObservation(RemoteEffectPresence.ABSENT)
            return RemoteEffectObservation(
                RemoteEffectPresence.PRESENT_EXACT,
                remote_identity=f"ready:pr:{number}@{scope.expected_head_sha}",
            )

        if identity.kind is EffectKind.MERGE_PR:
            if truth.remote_state == "MERGED":
                return RemoteEffectObservation(
                    RemoteEffectPresence.PRESENT_EXACT,
                    remote_identity=f"merge:pr:{number}@{scope.expected_head_sha}",
                )
            if truth.remote_state == "OPEN":
                return RemoteEffectObservation(RemoteEffectPresence.ABSENT)
            return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)

        raise ValueError("unsupported managed lifecycle effect kind")

    def execute(self, identity: RemoteEffectIdentity) -> str:
        if identity != self.identity:
            raise ValueError("adapter is bound to a different effect identity")

        truth = self._exact_truth()
        decision = decide_managed_pr_truth(handoff=self.handoff, truth=truth)
        if decision.transition.kind is not TransitionKind.MERGE_ELIGIBLE:
            raise OrdinaryRemoteUnavailable(
                "fresh managed PR truth is no longer merge eligible"
            )

        scope = self.handoff.scope
        number = self.handoff.pr_number

        if identity.kind is EffectKind.UPDATE_PR:
            if truth.remote_state != "OPEN" or not truth.is_draft:
                raise OrdinaryRemoteUnavailable(
                    "ready mutation requires exact open draft PR"
                )
            self._run_mutation(self.validator.ready_command())
            return f"ready:pr:{number}@{scope.expected_head_sha}"

        if identity.kind is EffectKind.MERGE_PR:
            if truth.remote_state != "OPEN":
                raise OrdinaryRemoteUnavailable(
                    "merge mutation requires exact open PR"
                )
            if truth.is_draft:
                raise OrdinaryRemoteUnavailable(
                    "merge mutation requires non-draft PR"
                )
            if truth.merge_state != "CLEAN":
                raise OrdinaryRemoteUnavailable(
                    f"merge mutation requires CLEAN merge state, got {truth.merge_state or 'UNKNOWN'}"
                )
            self._run_mutation(self.validator.merge_command())
            return f"merge:pr:{number}@{scope.expected_head_sha}"

        raise ValueError("unsupported managed lifecycle effect kind")


def _fresh_truth(
    *,
    root: Path,
    handoff: ManagedOrdinaryHandoff,
    runner,
) -> ManagedPRTruthResult:
    try:
        return GhManagedPRObserver(
            root=root,
            handoff=handoff,
            runner=runner,
        ).observe()
    except ManagedPRRemoteUnavailable as exc:
        raise OrdinaryRemoteUnavailable(str(exc)) from exc


def advance_managed_pr_lifecycle(
    *,
    root: Path,
    handoff: ManagedOrdinaryHandoff,
    store: OrdinaryEffectStore,
    runner=subprocess.run,
    crash_after_ready_execute: bool = False,
    crash_after_merge_execute: bool = False,
) -> ManagedLifecycleResult:
    """Advance exact managed PR from draft through merge, fail-closed on any drift."""

    root = Path(root).resolve()
    try:
        truth = _fresh_truth(root=root, handoff=handoff, runner=runner)
    except OrdinaryRemoteUnavailable as exc:
        return ManagedLifecycleResult(
            ManagedLifecycleDisposition.BLOCKED,
            f"managed PR truth unavailable: {exc}",
            handoff.digest,
        )

    if truth.disposition is not ManagedPRTruthDisposition.OBSERVED:
        return ManagedLifecycleResult(
            ManagedLifecycleDisposition.BLOCKED,
            truth.reason,
            handoff.digest,
            truth_digest=truth.digest,
        )

    ready_identity = handoff.scope.ready_identity(handoff.pr_number)
    merge_identity = handoff.scope.merge_identity(handoff.pr_number)
    ledger = store.load()

    # A merged exact PR may be an effect-recovery case (including a crash after
    # mutation or an exact external/manual merge). Reconcile any durable ready
    # prerequisite first, then the merge, before the pure reducer sees inactive PR.
    if truth.remote_state == "MERGED":
        ready_result = None
        if ledger.get(ready_identity) is not None:
            ready_result = advance_remote_effect(
                store=store,
                identity=ready_identity,
                adapter=ManagedLifecycleEffectAdapter(
                    root=root,
                    handoff=handoff,
                    identity=ready_identity,
                    runner=runner,
                ),
            )
            if not _effect_ok(ready_result):
                return ManagedLifecycleResult(
                    ManagedLifecycleDisposition.BLOCKED,
                    ready_result.reason,
                    handoff.digest,
                    ready_effect=ready_result,
                    truth_digest=truth.digest,
                )

        merge_result = advance_remote_effect(
            store=store,
            identity=merge_identity,
            adapter=ManagedLifecycleEffectAdapter(
                root=root,
                handoff=handoff,
                identity=merge_identity,
                runner=runner,
            ),
        )
        if _effect_ok(merge_result):
            return ManagedLifecycleResult(
                ManagedLifecycleDisposition.COMPLETE,
                "exact merged PR reconciled into durable lifecycle state",
                handoff.digest,
                transition_kind=TransitionKind.MERGE_ELIGIBLE.value,
                ready_effect=ready_result,
                merge_effect=merge_result,
                truth_digest=truth.digest,
            )
        return ManagedLifecycleResult(
            ManagedLifecycleDisposition.BLOCKED,
            merge_result.reason,
            handoff.digest,
            ready_effect=ready_result,
            merge_effect=merge_result,
            truth_digest=truth.digest,
        )

    decision = decide_managed_pr_truth(handoff=handoff, truth=truth)

    ready_result = None
    existing_ready = ledger.get(ready_identity)
    should_advance_ready = (
        existing_ready is not None
        or (
            truth.is_draft
            and decision.transition.kind is TransitionKind.MERGE_ELIGIBLE
        )
    )
    if should_advance_ready:
        ready_result = advance_remote_effect(
            store=store,
            identity=ready_identity,
            adapter=ManagedLifecycleEffectAdapter(
                root=root,
                handoff=handoff,
                identity=ready_identity,
                runner=runner,
            ),
            crash_after_execute=crash_after_ready_execute,
        )
        if not _effect_ok(ready_result):
            return ManagedLifecycleResult(
                ManagedLifecycleDisposition.BLOCKED,
                ready_result.reason,
                handoff.digest,
                transition_kind=decision.transition.kind.value,
                ready_effect=ready_result,
                truth_digest=truth.digest,
            )

        try:
            truth = _fresh_truth(root=root, handoff=handoff, runner=runner)
        except OrdinaryRemoteUnavailable as exc:
            return ManagedLifecycleResult(
                ManagedLifecycleDisposition.BLOCKED,
                f"post-ready truth unavailable: {exc}",
                handoff.digest,
                transition_kind=decision.transition.kind.value,
                ready_effect=ready_result,
            )
        if truth.disposition is not ManagedPRTruthDisposition.OBSERVED:
            return ManagedLifecycleResult(
                ManagedLifecycleDisposition.BLOCKED,
                truth.reason,
                handoff.digest,
                transition_kind=decision.transition.kind.value,
                ready_effect=ready_result,
                truth_digest=truth.digest,
            )
        decision = decide_managed_pr_truth(handoff=handoff, truth=truth)

    if decision.transition.kind is not TransitionKind.MERGE_ELIGIBLE:
        return ManagedLifecycleResult(
            ManagedLifecycleDisposition.NOT_ELIGIBLE,
            decision.transition.reason,
            handoff.digest,
            transition_kind=decision.transition.kind.value,
            ready_effect=ready_result,
            truth_digest=truth.digest,
        )
    if truth.is_draft:
        return ManagedLifecycleResult(
            ManagedLifecycleDisposition.BLOCKED,
            "PR remained draft after ready effect reconciliation",
            handoff.digest,
            transition_kind=decision.transition.kind.value,
            ready_effect=ready_result,
            truth_digest=truth.digest,
        )
    if truth.merge_state != "CLEAN":
        return ManagedLifecycleResult(
            ManagedLifecycleDisposition.BLOCKED,
            f"managed PR merge state is {truth.merge_state or 'UNKNOWN'}",
            handoff.digest,
            transition_kind=decision.transition.kind.value,
            ready_effect=ready_result,
            truth_digest=truth.digest,
        )

    merge_result = advance_remote_effect(
        store=store,
        identity=merge_identity,
        adapter=ManagedLifecycleEffectAdapter(
            root=root,
            handoff=handoff,
            identity=merge_identity,
            runner=runner,
        ),
        crash_after_execute=crash_after_merge_execute,
    )
    if not _effect_ok(merge_result):
        return ManagedLifecycleResult(
            ManagedLifecycleDisposition.BLOCKED,
            merge_result.reason,
            handoff.digest,
            transition_kind=decision.transition.kind.value,
            ready_effect=ready_result,
            merge_effect=merge_result,
            truth_digest=truth.digest,
        )

    return ManagedLifecycleResult(
        ManagedLifecycleDisposition.COMPLETE,
        "exact managed PR ready/merge lifecycle completed or reconciled",
        handoff.digest,
        transition_kind=decision.transition.kind.value,
        ready_effect=ready_result,
        merge_effect=merge_result,
        truth_digest=truth.digest,
    )
