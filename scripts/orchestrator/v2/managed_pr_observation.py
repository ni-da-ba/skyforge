"""Strict read-only managed-PR truth observation for Platform v2 R5C11."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
from pathlib import Path
import subprocess
from typing import Any, Mapping, Sequence

from .core import (
    ControllerState,
    CoreDecision,
    ManagedPRObservation,
    ManagedPRState,
    reduce_managed_pr,
)
from .domain import CIState, PRClass
from .identity import canonical_digest
from .ordinary_service import ManagedOrdinaryHandoff


PR_VIEW_FIELDS = (
    "number,state,mergedAt,isDraft,mergeStateStatus,statusCheckRollup,"
    "headRefName,headRefOid,baseRefName,title,body,reviewDecision"
)


class ManagedPRTruthDisposition(str, Enum):
    OBSERVED = "OBSERVED"
    REJECTED = "REJECTED"


@dataclass(frozen=True)
class ManagedPRTruthResult:
    disposition: ManagedPRTruthDisposition
    reason: str
    handoff_digest: str
    merge_state: str = ""
    observation: ManagedPRObservation | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "handoff_digest": self.handoff_digest,
                "merge_state": self.merge_state,
                "observation_digest": (
                    self.observation.digest if self.observation is not None else ""
                ),
            }
        )


class ManagedPRRemoteUnavailable(RuntimeError):
    """Exact managed-PR remote truth could not be read safely."""


class ManagedPRReadCommandValidator:
    """Allow only exact PR-view and changed-path reads for one frozen handoff."""

    def __init__(self, handoff: ManagedOrdinaryHandoff) -> None:
        if not isinstance(handoff, ManagedOrdinaryHandoff):
            raise ValueError("handoff must be ManagedOrdinaryHandoff")
        self.handoff = handoff

    def allowed(self) -> set[tuple[str, ...]]:
        scope = self.handoff.scope
        number = str(self.handoff.pr_number)
        return {
            (
                "gh",
                "pr",
                "view",
                number,
                "--repo",
                scope.repo,
                "--json",
                PR_VIEW_FIELDS,
                "--jq=.",
            ),
            (
                "gh",
                "pr",
                "diff",
                number,
                "--repo",
                scope.repo,
                "--name-only",
            ),
        }

    def validate(self, args: Sequence[str]) -> tuple[str, ...]:
        command = tuple(str(value) for value in args)
        if command not in self.allowed():
            raise ValueError(
                "command is outside the exact Platform-v2 managed-PR read allowlist"
            )
        return command


def _ci_state(raw: Any) -> CIState:
    if raw is None:
        return CIState.UNKNOWN
    if not isinstance(raw, list):
        raise ValueError("statusCheckRollup must be a list")
    if not raw:
        return CIState.UNKNOWN

    active = False
    failed = False
    successful = 0
    for item in raw:
        if not isinstance(item, Mapping):
            raise ValueError("statusCheckRollup entry must be an object")
        status = str(item.get("status") or "").upper()
        conclusion = str(item.get("conclusion") or "").upper()
        if status != "COMPLETED":
            active = True
            continue
        if conclusion == "SUCCESS":
            successful += 1
        elif conclusion not in {"SKIPPED", "NEUTRAL"}:
            failed = True

    if failed:
        return CIState.FAIL
    if active:
        return CIState.PENDING
    if successful > 0:
        return CIState.PASS
    return CIState.UNKNOWN


def _paths(text: str) -> tuple[str, ...]:
    values: list[str] = []
    for line in str(text or "").splitlines():
        path = line.strip().replace("\\", "/")
        if not path:
            continue
        if (
            path.startswith("/")
            or path.startswith("../")
            or "/../" in path
            or path in {".", ".."}
        ):
            raise ValueError("managed PR returned malformed repository path")
        values.append(path)
    return tuple(sorted(set(values)))


def observe_managed_pr_truth(
    *,
    handoff: ManagedOrdinaryHandoff,
    pr: Mapping[str, Any],
    changed_paths: Sequence[str],
) -> ManagedPRTruthResult:
    """Convert exact remote PR truth into the accepted v2 mechanical observation."""

    if not isinstance(handoff, ManagedOrdinaryHandoff):
        raise ValueError("handoff must be ManagedOrdinaryHandoff")
    if not isinstance(pr, Mapping):
        raise ValueError("managed PR truth must be an object")

    scope = handoff.scope
    try:
        number = int(pr.get("number"))
    except (TypeError, ValueError):
        return ManagedPRTruthResult(
            ManagedPRTruthDisposition.REJECTED,
            "managed PR number is missing or malformed",
            handoff.digest,
        )
    if number != handoff.pr_number:
        return ManagedPRTruthResult(
            ManagedPRTruthDisposition.REJECTED,
            "managed PR number differs from durable handoff",
            handoff.digest,
        )

    state = str(pr.get("state") or "").upper()
    active = state == "OPEN" and not pr.get("mergedAt")
    base = str(pr.get("baseRefName") or "")
    branch = str(pr.get("headRefName") or "")
    head = str(pr.get("headRefOid") or "")
    title = str(pr.get("title") or "")
    body = str(pr.get("body") or "")
    merge_state = str(pr.get("mergeStateStatus") or "").upper()

    exact_identity = (
        base == "main"
        and branch == scope.branch
        and head == scope.expected_head_sha
        and title == scope.pr_title
        and body == scope.pr_body
    )
    if not exact_identity:
        return ManagedPRTruthResult(
            ManagedPRTruthDisposition.REJECTED,
            "live PR identity/head differs from frozen managed handoff",
            handoff.digest,
            merge_state=merge_state,
        )

    normalized_live = tuple(sorted(set(str(p).strip().replace("\\", "/") for p in changed_paths if str(p).strip())))
    try:
        normalized_live = _paths("\n".join(normalized_live))
    except ValueError as exc:
        return ManagedPRTruthResult(
            ManagedPRTruthDisposition.REJECTED,
            str(exc),
            handoff.digest,
            merge_state=merge_state,
        )
    recorded = set(handoff.changed_paths)
    unexpected = tuple(path for path in normalized_live if path not in recorded)
    if unexpected:
        return ManagedPRTruthResult(
            ManagedPRTruthDisposition.REJECTED,
            "live PR changed-path scope widens durable handoff: " + ",".join(unexpected),
            handoff.digest,
            merge_state=merge_state,
        )

    try:
        ci_state = _ci_state(pr.get("statusCheckRollup"))
    except ValueError as exc:
        return ManagedPRTruthResult(
            ManagedPRTruthDisposition.REJECTED,
            str(exc),
            handoff.digest,
            merge_state=merge_state,
        )

    review = str(pr.get("reviewDecision") or "").upper()
    review_required = review in {"CHANGES_REQUESTED", "REVIEW_REQUIRED"}
    human_gate = (not handoff.auto_merge_eligible) or review_required
    machine_acceptance = (
        handoff.auto_merge_eligible
        and not review_required
        and active
    )

    observation = ManagedPRObservation(
        lane=handoff.lane,
        pr_number=handoff.pr_number,
        active_pr=active,
        base_branch=base,
        head_branch=branch,
        current_head_sha=head,
        evidence_sha=head if (machine_acceptance and ci_state is CIState.PASS) else "",
        reviewed_sha=head if machine_acceptance else "",
        task_spec_hash=handoff.task_spec_hash,
        accepted_task_spec_hash=(
            handoff.task_spec_hash if machine_acceptance else ""
        ),
        ci_state=ci_state,
        pr_class=PRClass.HUMAN_GATE if human_gate else PRClass.DELIVERY,
        human_gate_pending=human_gate,
        review_required=review_required,
    )
    return ManagedPRTruthResult(
        ManagedPRTruthDisposition.OBSERVED,
        "exact managed PR truth projected into mechanical observation",
        handoff.digest,
        merge_state=merge_state,
        observation=observation,
    )


def managed_state_from_handoff(
    handoff: ManagedOrdinaryHandoff,
) -> ControllerState:
    """Project restart-safe R5C10 handoff authority into the pure managed-PR core."""
    if not isinstance(handoff, ManagedOrdinaryHandoff):
        raise ValueError("handoff must be ManagedOrdinaryHandoff")
    return ControllerState(
        managed=(
            ManagedPRState(
                lane=handoff.lane,
                pr_number=handoff.pr_number,
                branch=handoff.scope.branch,
                authority_key=handoff.authority_key,
                expected_head=handoff.scope.expected_head_sha,
                auto_merge_eligible=handoff.auto_merge_eligible,
                changed_paths=handoff.changed_paths,
            ),
        )
    )


def decide_managed_pr_truth(
    *,
    handoff: ManagedOrdinaryHandoff,
    truth: ManagedPRTruthResult,
) -> CoreDecision:
    if truth.disposition is not ManagedPRTruthDisposition.OBSERVED:
        raise ValueError("rejected managed PR truth cannot enter the mechanical reducer")
    if truth.observation is None:
        raise ValueError("observed managed PR truth lacks observation")
    if truth.handoff_digest != handoff.digest:
        raise ValueError("managed PR truth belongs to a different durable handoff")
    return reduce_managed_pr(
        managed_state_from_handoff(handoff),
        truth.observation,
    )


class GhManagedPRObserver:
    """Read-only GitHub adapter bound to one durable managed handoff."""

    def __init__(
        self,
        *,
        root: Path,
        handoff: ManagedOrdinaryHandoff,
        runner=subprocess.run,
    ) -> None:
        self.root = Path(root).resolve()
        self.handoff = handoff
        self.validator = ManagedPRReadCommandValidator(handoff)
        self.runner = runner

    def _run(self, args: Sequence[str]) -> str:
        command = self.validator.validate(args)
        try:
            result = self.runner(
                list(command),
                cwd=self.root,
                check=True,
                text=True,
                capture_output=True,
                timeout=60,
            )
        except subprocess.SubprocessError as exc:
            raise ManagedPRRemoteUnavailable(str(exc)) from exc
        return str(result.stdout or "")

    def observe(self) -> ManagedPRTruthResult:
        scope = self.handoff.scope
        number = str(self.handoff.pr_number)
        raw_pr = self._run(
            [
                "gh",
                "pr",
                "view",
                number,
                "--repo",
                scope.repo,
                "--json",
                PR_VIEW_FIELDS,
                "--jq=.",
            ]
        )
        try:
            pr = json.loads(raw_pr or "{}")
        except json.JSONDecodeError as exc:
            raise ManagedPRRemoteUnavailable(
                "managed PR read returned malformed JSON"
            ) from exc
        if not isinstance(pr, Mapping):
            raise ManagedPRRemoteUnavailable(
                "managed PR read returned malformed shape"
            )
        changed = self._run(
            [
                "gh",
                "pr",
                "diff",
                number,
                "--repo",
                scope.repo,
                "--name-only",
            ]
        )
        return observe_managed_pr_truth(
            handoff=self.handoff,
            pr=pr,
            changed_paths=_paths(changed),
        )
