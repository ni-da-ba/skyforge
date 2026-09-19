from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
from pathlib import Path
import re
import subprocess
from typing import Callable, Sequence

from .context_package import ContextPackageStore
from .context_retrieval import ContextRetrievalStore
from .effects import EffectStatus, RemoteEffectPresence
from .ordinary_effect_executor import (
    OrdinaryEffectExecutionDisposition,
    OrdinaryEffectExecutionResult,
    OrdinaryRemoteUnavailable,
    advance_remote_effect,
)
from .ordinary_effects import OrdinaryEffectStore, OrdinaryMutationScope
from .ordinary_remote import GhGitOrdinaryEffectAdapter, OrdinaryEffectBinding
from .scope_promotion import (
    PromotionDisposition,
    PromotionResult,
    PromotionStore,
    validate_promotion,
)


_REMOTE_MAIN_RE = re.compile(r"^[0-9a-f]{40}$")


def _repo(value: str) -> str:
    text = str(value or "").strip()
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", text):
        raise ValueError("repo must be owner/name")
    return text


def validate_remote_main_read_command(
    args: Sequence[str], *, repo: str
) -> tuple[str, ...]:
    repo = _repo(repo)
    command = tuple(str(part) for part in args)
    expected = (
        "gh",
        "api",
        f"repos/{repo}/commits/main",
        "--jq",
        ".sha",
    )
    if command != expected:
        raise ValueError("objective promotion permits only the exact remote-main read command")
    return command


def read_remote_main(
    *,
    root: Path,
    repo: str,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> str:
    command = validate_remote_main_read_command(
        ["gh", "api", f"repos/{_repo(repo)}/commits/main", "--jq", ".sha"],
        repo=repo,
    )
    completed = runner(
        list(command),
        cwd=root,
        check=True,
        text=True,
        capture_output=True,
        timeout=60,
    )
    sha = str(completed.stdout or "").strip()
    if not _REMOTE_MAIN_RE.fullmatch(sha):
        raise ValueError("remote main SHA is malformed")
    return sha


class ObjectivePromotionPostDisposition(str, Enum):
    EXECUTED = "EXECUTED"
    RECONCILED = "RECONCILED"
    ALREADY_COMPLETE = "ALREADY_COMPLETE"
    BLOCKED = "BLOCKED"


@dataclass(frozen=True)
class ObjectivePromotionPostResult:
    disposition: ObjectivePromotionPostDisposition
    reason: str
    promotion_id: str
    draft_digest: str
    effect_id: str = ""
    remote_identity: str = ""
    effect_disposition: str = ""

    def as_dict(self) -> dict[str, str | bool]:
        return {
            "schema_version": 1,
            "disposition": self.disposition.value,
            "reason": self.reason,
            "promotion_id": self.promotion_id,
            "draft_digest": self.draft_digest,
            "effect_id": self.effect_id,
            "remote_identity": self.remote_identity,
            "effect_disposition": self.effect_disposition,
            "task_authority_synthesized_locally": False,
        }


def _effect_scope(
    *,
    repo: str,
    promotion: PromotionResult,
    accepted_main_sha: str,
) -> OrdinaryMutationScope:
    if promotion.draft is None:
        raise ValueError("promotion draft is required")
    attempt_id = f"objective-promotion:{promotion.digest}"
    short = promotion.digest[:16]
    return OrdinaryMutationScope(
        attempt_id=attempt_id,
        repo=_repo(repo),
        base_sha=accepted_main_sha,
        branch=f"objective-promotion/{short}",
        expected_head_sha=accepted_main_sha,
        pr_title=f"Objective promotion {short}",
        pr_body=f"Frozen objective promotion {promotion.digest}",
        issue_number=promotion.draft.issue_number,
    )


def _map_effect_result(
    *,
    promotion: PromotionResult,
    effect: OrdinaryEffectExecutionResult,
) -> ObjectivePromotionPostResult:
    if promotion.draft is None:
        raise ValueError("promotion draft is required")
    mapping = {
        OrdinaryEffectExecutionDisposition.EXECUTED: ObjectivePromotionPostDisposition.EXECUTED,
        OrdinaryEffectExecutionDisposition.RECONCILED: ObjectivePromotionPostDisposition.RECONCILED,
        OrdinaryEffectExecutionDisposition.ALREADY_COMPLETE: ObjectivePromotionPostDisposition.ALREADY_COMPLETE,
        OrdinaryEffectExecutionDisposition.BLOCKED: ObjectivePromotionPostDisposition.BLOCKED,
    }
    return ObjectivePromotionPostResult(
        disposition=mapping[effect.disposition],
        reason=effect.reason,
        promotion_id=promotion.digest,
        draft_digest=promotion.draft.digest,
        effect_id=effect.record.identity.effect_id,
        remote_identity=effect.record.remote_identity,
        effect_disposition=effect.disposition.value,
    )


def execute_frozen_promotion_post(
    *,
    root: Path,
    repo: str,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
    crash_after_execute: bool = False,
) -> ObjectivePromotionPostResult:
    """Post one frozen objective promotion exactly once.

    This function never writes TaskAuthorityEventStore. The posted trusted GitHub comment
    becomes task authority only if/when the ordinary signed webhook path captures and
    freshly hydrates it through the existing task-authority boundary.

    Promotion preconditions are revalidated before an effect can first execute. Once a
    durable PENDING effect is provably present remotely, retries reconcile that effect
    without reapplying pre-mutation issue-revision checks that the comment itself changes.
    A COMPLETE effect is an unconditional no-reexecution boundary.
    """
    root = Path(root).resolve()
    promotions = PromotionStore.for_root(root).load()
    if not promotions.records:
        return ObjectivePromotionPostResult(
            ObjectivePromotionPostDisposition.BLOCKED,
            "no frozen objective promotion exists",
            "",
            "",
        )
    frozen_record = promotions.records[-1]
    frozen = frozen_record.result
    if (
        frozen.disposition is not PromotionDisposition.READY_FOR_TASK_AUTHORITY_POST
        or frozen.draft is None
    ):
        return ObjectivePromotionPostResult(
            ObjectivePromotionPostDisposition.BLOCKED,
            "latest frozen promotion is not ready for task-authority posting",
            frozen.digest,
            frozen.draft.digest if frozen.draft else "",
        )

    packages = ContextPackageStore.for_root(root).load()
    package_matches = [
        package for package in packages.records if package.package_id == frozen.package_id
    ]
    retrievals = ContextRetrievalStore.for_root(root).load()
    retrieval_matches = [
        retrieval
        for retrieval in retrievals.records
        if retrieval.retrieval_id == frozen.retrieval_id
    ]
    if len(package_matches) != 1 or len(retrieval_matches) != 1:
        return ObjectivePromotionPostResult(
            ObjectivePromotionPostDisposition.BLOCKED,
            "frozen promotion package/retrieval evidence is unavailable or ambiguous",
            frozen.digest,
            frozen.draft.digest,
        )
    package = package_matches[0]
    retrieval = retrieval_matches[0]

    scope = _effect_scope(
        repo=repo,
        promotion=frozen,
        accepted_main_sha=package.accepted_main_sha,
    )
    identity = scope.comment_identity(frozen.draft.body)
    binding = OrdinaryEffectBinding(
        scope=scope,
        identity=identity,
        comment_body=frozen.draft.body,
    )
    adapter = GhGitOrdinaryEffectAdapter(
        root=root,
        binding=binding,
        runner=runner,
    )
    effect_store = OrdinaryEffectStore.for_root(root)
    effect_ledger = effect_store.load()
    existing = effect_ledger.get(identity)

    # COMPLETE is the strongest exactly-once boundary. Never make a second remote
    # mutation contingent on mutable issue/main state after durable completion.
    if existing is not None and existing.status is EffectStatus.COMPLETE:
        return ObjectivePromotionPostResult(
            ObjectivePromotionPostDisposition.ALREADY_COMPLETE,
            "promotion comment effect is already durably complete",
            frozen.digest,
            frozen.draft.digest,
            effect_id=identity.effect_id,
            remote_identity=existing.remote_identity,
            effect_disposition=OrdinaryEffectExecutionDisposition.ALREADY_COMPLETE.value,
        )

    # A crash may occur after GitHub accepted the comment but before local completion
    # persistence. If the exact marker/body is present, reconcile it first; re-running
    # the pre-mutation issue-revision check would reject our own successful mutation.
    if existing is not None and existing.status is EffectStatus.PENDING:
        try:
            observation = adapter.observe(identity)
        except OrdinaryRemoteUnavailable:
            observation = None
        if observation is not None and observation.presence is not RemoteEffectPresence.ABSENT:
            effect = advance_remote_effect(
                store=effect_store,
                identity=identity,
                adapter=adapter,
                crash_after_execute=False,
            )
            return _map_effect_result(promotion=frozen, effect=effect)

    # First execution (or a PENDING effect still provably absent) must revalidate all
    # promotion preconditions immediately before mutation.
    revalidated = validate_promotion(
        root=root,
        repo=repo,
        package=package,
        retrieval=retrieval,
        gh_runner=runner,
    )
    if (
        revalidated.disposition
        is not PromotionDisposition.READY_FOR_TASK_AUTHORITY_POST
        or revalidated.draft is None
        or revalidated.draft.digest != frozen.draft.digest
        or revalidated.digest != frozen.digest
    ):
        detail = "; ".join(revalidated.blockers) or revalidated.reason
        return ObjectivePromotionPostResult(
            ObjectivePromotionPostDisposition.BLOCKED,
            "frozen objective promotion no longer revalidates exactly: " + detail,
            frozen.digest,
            frozen.draft.digest,
            effect_id=identity.effect_id,
        )

    try:
        remote_main = read_remote_main(root=root, repo=repo, runner=runner)
    except (ValueError, subprocess.SubprocessError) as exc:
        return ObjectivePromotionPostResult(
            ObjectivePromotionPostDisposition.BLOCKED,
            f"remote main truth unavailable: {exc}",
            frozen.digest,
            frozen.draft.digest,
            effect_id=identity.effect_id,
        )
    if remote_main != package.accepted_main_sha:
        return ObjectivePromotionPostResult(
            ObjectivePromotionPostDisposition.BLOCKED,
            "remote main moved from frozen accepted main SHA",
            frozen.digest,
            frozen.draft.digest,
            effect_id=identity.effect_id,
        )

    effect = advance_remote_effect(
        store=effect_store,
        identity=identity,
        adapter=adapter,
        crash_after_execute=crash_after_execute,
    )
    return _map_effect_result(promotion=frozen, effect=effect)
