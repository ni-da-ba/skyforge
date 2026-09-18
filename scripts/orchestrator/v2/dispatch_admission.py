"""Repository-authoritative dispatch admission for Platform v2 R5C4."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Any, Iterable

from .decision import (
    CachedDecisionDisposition,
    DecisionFreshnessObservation,
    DecisionKind,
    PendingDecisionRecord,
    WorkerTier as DecisionWorkerTier,
    cached_decision_disposition,
)
from .external import ExternalProducerClaim, classify_external_dispatch_hold
from .identity import FrozenTaskSpec, TaskAttemptIdentity, canonical_digest
from .quota import (
    LocalBudgetObservation,
    ProviderQuotaDecision,
    QuotaAdmissionDecision,
    QuotaAdmissionDisposition,
    classify_quota_admission,
)
from .worker_provider import (
    FrozenWorkerSpec,
    WorkerTier as ExecutionWorkerTier,
)


def _required(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


def _positive_issue_tuple(values: Iterable[int]) -> tuple[int, ...]:
    issues: set[int] = set()
    for raw in values:
        if isinstance(raw, bool) or not isinstance(raw, int) or raw <= 0:
            raise ValueError("governing issue numbers must be positive integers")
        issues.add(raw)
    if not issues:
        raise ValueError("at least one governing issue number is required")
    return tuple(sorted(issues))


def _path(value: str) -> str:
    text = str(value or "").replace("\\", "/").strip().lstrip("./")
    if not text or text.startswith("/") or ".." in text.split("/"):
        raise ValueError("task path scope is malformed")
    return text


def _scope_subset(candidate: str, authority: str) -> bool:
    child = _path(candidate)
    parent = _path(authority)
    if child == parent:
        return True
    if parent.endswith("/**"):
        prefix = parent[:-3].rstrip("/")
        if child == prefix:
            return False
        return child.startswith(prefix + "/")
    return False


@dataclass(frozen=True)
class RepositoryTaskAuthority:
    task_id: str
    authority_key: str
    issue_numbers: tuple[int, ...]
    lane: str
    objective: str
    stop_boundary: str
    allowed_paths: tuple[str, ...]
    protected_paths: tuple[str, ...] = ()
    context_text: str = ""
    auto_merge_eligible: bool = False
    spec_version: int = 2

    def __post_init__(self) -> None:
        for name in ("task_id", "authority_key", "lane", "objective", "stop_boundary"):
            object.__setattr__(self, name, _required(getattr(self, name), name))
        object.__setattr__(self, "issue_numbers", _positive_issue_tuple(self.issue_numbers))
        if not self.allowed_paths:
            raise ValueError("repository task authority requires allowed_paths")
        object.__setattr__(
            self,
            "allowed_paths",
            tuple(sorted({_path(p) for p in self.allowed_paths})),
        )
        object.__setattr__(
            self,
            "protected_paths",
            tuple(sorted({_path(p) for p in self.protected_paths})),
        )
        if not isinstance(self.auto_merge_eligible, bool):
            raise ValueError("auto_merge_eligible must be boolean")
        if isinstance(self.spec_version, bool) or not isinstance(self.spec_version, int) or self.spec_version < 1:
            raise ValueError("spec_version must be positive")

    def as_dict(self) -> dict[str, Any]:
        return {
            "task_id": self.task_id,
            "authority_key": self.authority_key,
            "issue_numbers": list(self.issue_numbers),
            "lane": self.lane,
            "objective": self.objective,
            "stop_boundary": self.stop_boundary,
            "allowed_paths": list(self.allowed_paths),
            "protected_paths": list(self.protected_paths),
            "context_text": self.context_text,
            "auto_merge_eligible": self.auto_merge_eligible,
            "spec_version": self.spec_version,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


class DispatchAdmissionDisposition(str, Enum):
    ADMIT = "ADMIT"
    BLOCK = "BLOCK"
    RECLASSIFY = "RECLASSIFY"
    NOT_DISPATCH = "NOT_DISPATCH"


@dataclass(frozen=True)
class DispatchAdmissionResult:
    disposition: DispatchAdmissionDisposition
    reason: str
    authority_digest: str
    pending_decision_digest: str
    quota: QuotaAdmissionDecision
    consume_attempt: bool
    frozen_task: FrozenTaskSpec | None = None
    attempt: TaskAttemptIdentity | None = None
    worker_spec: FrozenWorkerSpec | None = None

    @property
    def digest(self) -> str:
        return canonical_digest({
            "disposition": self.disposition.value,
            "reason": self.reason,
            "authority_digest": self.authority_digest,
            "pending_decision_digest": self.pending_decision_digest,
            "quota_digest": self.quota.digest,
            "consume_attempt": self.consume_attempt,
            "frozen_task_hash": self.frozen_task.spec_hash if self.frozen_task else "",
            "attempt_id": self.attempt.attempt_id if self.attempt else "",
            "worker_spec_digest": self.worker_spec.digest if self.worker_spec else "",
        })


def _blocked(
    *,
    authority: RepositoryTaskAuthority,
    record: PendingDecisionRecord,
    quota: QuotaAdmissionDecision,
    disposition: DispatchAdmissionDisposition,
    reason: str,
) -> DispatchAdmissionResult:
    return DispatchAdmissionResult(
        disposition=disposition,
        reason=reason,
        authority_digest=authority.digest,
        pending_decision_digest=record.digest,
        quota=quota,
        consume_attempt=False,
    )


def admit_dispatch(
    *,
    authority: RepositoryTaskAuthority,
    record: PendingDecisionRecord,
    freshness: DecisionFreshnessObservation,
    current_main: str,
    active_external_claims: Iterable[ExternalProducerClaim],
    provider_quota: ProviderQuotaDecision | None,
    local_budget: LocalBudgetObservation,
    attempt_number: int,
) -> DispatchAdmissionResult:
    if len(current_main) != 40 or any(ch not in "0123456789abcdef" for ch in current_main):
        raise ValueError("current_main must be lowercase 40-character Git SHA")
    if freshness.current_main != current_main:
        raise ValueError("freshness current_main must equal admission current_main")
    if attempt_number < 1:
        raise ValueError("attempt_number must be >= 1")

    quota = classify_quota_admission(provider_quota, local_budget)

    freshness_result = cached_decision_disposition(record, freshness)
    if freshness_result is CachedDecisionDisposition.RECLASSIFY:
        return _blocked(
            authority=authority,
            record=record,
            quota=quota,
            disposition=DispatchAdmissionDisposition.RECLASSIFY,
            reason="classifier proposal is stale against current repository truth",
        )
    if freshness_result is CachedDecisionDisposition.INVALID:
        return _blocked(
            authority=authority,
            record=record,
            quota=quota,
            disposition=DispatchAdmissionDisposition.BLOCK,
            reason="classifier proposal is structurally invalid",
        )

    decision = record.decision
    if decision.kind is not DecisionKind.DISPATCH:
        return _blocked(
            authority=authority,
            record=record,
            quota=quota,
            disposition=DispatchAdmissionDisposition.NOT_DISPATCH,
            reason=f"{decision.kind.value} is not an ordinary worker dispatch",
        )

    if decision.lane != authority.lane:
        return _blocked(
            authority=authority,
            record=record,
            quota=quota,
            disposition=DispatchAdmissionDisposition.BLOCK,
            reason="classifier lane differs from repository task authority",
        )
    if decision.objective != authority.objective:
        return _blocked(
            authority=authority,
            record=record,
            quota=quota,
            disposition=DispatchAdmissionDisposition.BLOCK,
            reason="classifier objective differs from repository task authority",
        )
    if decision.stop_boundary != authority.stop_boundary:
        return _blocked(
            authority=authority,
            record=record,
            quota=quota,
            disposition=DispatchAdmissionDisposition.BLOCK,
            reason="classifier stop boundary differs from repository task authority",
        )

    decision_issues = tuple(sorted(record.task_issue_numbers))
    if decision_issues != authority.issue_numbers:
        return _blocked(
            authority=authority,
            record=record,
            quota=quota,
            disposition=DispatchAdmissionDisposition.BLOCK,
            reason="classifier decision issue authority differs from repository task authority",
        )

    proposed_paths = (
        tuple(_path(p) for p in decision.allowed_paths)
        if decision.allowed_paths is not None
        else authority.allowed_paths
    )
    for proposed in proposed_paths:
        if not any(_scope_subset(proposed, allowed) for allowed in authority.allowed_paths):
            return _blocked(
                authority=authority,
                record=record,
                quota=quota,
                disposition=DispatchAdmissionDisposition.BLOCK,
                reason=f"classifier path scope widens repository authority: {proposed}",
            )
    effective_paths = tuple(sorted(set(proposed_paths)))
    if not effective_paths:
        return _blocked(
            authority=authority,
            record=record,
            quota=quota,
            disposition=DispatchAdmissionDisposition.BLOCK,
            reason="effective worker path scope is empty",
        )

    external_hold = classify_external_dispatch_hold(
        active_external_claims,
        authority.issue_numbers,
    )
    if external_hold.hold:
        return _blocked(
            authority=authority,
            record=record,
            quota=quota,
            disposition=DispatchAdmissionDisposition.BLOCK,
            reason=(
                "active external/manual producer owns governing issue(s): "
                + ",".join(str(v) for v in external_hold.issue_numbers)
            ),
        )

    if quota.disposition in {
        QuotaAdmissionDisposition.BLOCK_PROVIDER,
        QuotaAdmissionDisposition.BLOCK_LOCAL,
    }:
        return _blocked(
            authority=authority,
            record=record,
            quota=quota,
            disposition=DispatchAdmissionDisposition.BLOCK,
            reason=quota.reason,
        )

    tier = decision.effective_worker_tier or DecisionWorkerTier.TERRA
    execution_tier = ExecutionWorkerTier(tier.value)

    payload = {
        "authority": authority.as_dict(),
        "execution": {
            "lane": authority.lane,
            "worker_tier": execution_tier.value,
            "allowed_paths": list(effective_paths),
            "source_pr_number": decision.pr_number,
            "reusable_evidence": decision.reusable_evidence,
            "classifier_reason": decision.reason,
        },
    }
    frozen = FrozenTaskSpec.from_payload(
        task_id=authority.task_id,
        authority_key=authority.authority_key,
        base_sha=current_main,
        spec_version=authority.spec_version,
        payload=payload,
    )
    attempt = TaskAttemptIdentity.create(frozen, attempt_number=attempt_number)
    worker = FrozenWorkerSpec(
        task_id=frozen.task_id,
        authority_key=frozen.authority_key,
        task_spec_hash=frozen.spec_hash,
        attempt_id=attempt.attempt_id,
        lane=authority.lane,
        objective=authority.objective,
        stop_boundary=authority.stop_boundary,
        base_sha=current_main,
        allowed_paths=effective_paths,
        protected_paths=authority.protected_paths,
        tier=execution_tier,
        context_text=authority.context_text,
    )

    return DispatchAdmissionResult(
        disposition=DispatchAdmissionDisposition.ADMIT,
        reason="repository authority, freshness, external ownership, and quota admit one frozen worker attempt",
        authority_digest=authority.digest,
        pending_decision_digest=record.digest,
        quota=quota,
        consume_attempt=quota.consume_attempt,
        frozen_task=frozen,
        attempt=attempt,
        worker_spec=worker,
    )
