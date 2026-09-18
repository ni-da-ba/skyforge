"""Hosted dispatch-admission boundary for Platform v2 R5C17.

Freshly revalidates repository-owned task authority and current main, then applies the
accepted pure dispatch admission policy. A successful result freezes worker identity but
does not launch a worker, create a worktree, acquire a writer fence, or mutate Git/GitHub.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
from pathlib import Path
from typing import Any, Iterable, Mapping

from .classifier_provider import ClassifierRunStatus, ClassifierRunStore
from .decision import DecisionFreshnessObservation, PendingDecisionRecord
from .dispatch_admission import (
    DispatchAdmissionDisposition,
    DispatchAdmissionResult,
    admit_dispatch,
)
from .external import ExternalProducerClaim
from .hosted_task_plan import HostedTaskPlanLedger, HostedTaskPlanStatus
from .hosted_task_preflight import (
    AcceptedMainRemoteUnavailable,
    GhAcceptedMainReader,
)
from .identity import FrozenTaskSpec, TaskAttemptIdentity, canonical_digest
from .quota import LocalBudgetObservation, ProviderQuotaDecision
from .state_store import JsonStateStoreAdapter
from .task_authority import (
    GhTaskAuthorityHydrator,
    TaskAuthorityDisposition,
    TaskAuthorityRemoteUnavailable,
)
from .task_event_composition import TaskAuthorityEventStore
from .worker_provider import FrozenWorkerSpec, WorkerTier


HOSTED_ADMISSION_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "hosted-admission.json"
)
HOSTED_ADMISSION_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "hosted-admission.json.bak"
)


def _required(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label} is required")
    return value.strip()


def _sha40(value: Any, label: str) -> str:
    text = _required(value, label)
    if len(text) != 40 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase 40-character Git SHA")
    return text


def _sha64(value: Any, label: str) -> str:
    text = _required(value, label)
    if len(text) != 64 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase SHA-256 hex")
    return text


def _positive(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be positive integer")
    return value


def _frozen_task_dict(spec: FrozenTaskSpec) -> dict[str, Any]:
    return {
        "task_id": spec.task_id,
        "authority_key": spec.authority_key,
        "base_sha": spec.base_sha,
        "spec_version": spec.spec_version,
        "payload": spec.payload(),
        "spec_hash": spec.spec_hash,
    }


def _frozen_task_from_mapping(raw: Any) -> FrozenTaskSpec:
    if not isinstance(raw, Mapping):
        raise ValueError("frozen_task must be object")
    spec = FrozenTaskSpec.from_payload(
        task_id=_required(raw.get("task_id"), "frozen_task.task_id"),
        authority_key=_required(
            raw.get("authority_key"),
            "frozen_task.authority_key",
        ),
        base_sha=_sha40(raw.get("base_sha"), "frozen_task.base_sha"),
        spec_version=_positive(raw.get("spec_version"), "frozen_task.spec_version"),
        payload=raw.get("payload"),
    )
    if _sha64(raw.get("spec_hash"), "frozen_task.spec_hash") != spec.spec_hash:
        raise ValueError("frozen task spec hash mismatch")
    return spec


def _attempt_dict(attempt: TaskAttemptIdentity) -> dict[str, Any]:
    return {
        "task_id": attempt.task_id,
        "spec_hash": attempt.spec_hash,
        "base_sha": attempt.base_sha,
        "attempt_number": attempt.attempt_number,
        "attempt_id": attempt.attempt_id,
    }


def _attempt_from_mapping(raw: Any, spec: FrozenTaskSpec) -> TaskAttemptIdentity:
    if not isinstance(raw, Mapping):
        raise ValueError("attempt must be object")
    attempt = TaskAttemptIdentity.create(
        spec,
        attempt_number=_positive(raw.get("attempt_number"), "attempt.attempt_number"),
    )
    if _required(raw.get("task_id"), "attempt.task_id") != attempt.task_id:
        raise ValueError("attempt task_id mismatch")
    if _sha64(raw.get("spec_hash"), "attempt.spec_hash") != attempt.spec_hash:
        raise ValueError("attempt spec_hash mismatch")
    if _sha40(raw.get("base_sha"), "attempt.base_sha") != attempt.base_sha:
        raise ValueError("attempt base_sha mismatch")
    if _sha64(raw.get("attempt_id"), "attempt.attempt_id") != attempt.attempt_id:
        raise ValueError("attempt identity mismatch")
    return attempt


def _worker_dict(worker: FrozenWorkerSpec) -> dict[str, Any]:
    value = worker.as_dict()
    value["digest"] = worker.digest
    return value


def _worker_from_mapping(
    raw: Any,
    *,
    spec: FrozenTaskSpec,
    attempt: TaskAttemptIdentity,
) -> FrozenWorkerSpec:
    if not isinstance(raw, Mapping):
        raise ValueError("worker_spec must be object")
    paths = raw.get("allowed_paths")
    protected = raw.get("protected_paths")
    if not isinstance(paths, list) or not isinstance(protected, list):
        raise ValueError("worker path scopes must be lists")
    worker = FrozenWorkerSpec(
        task_id=_required(raw.get("task_id"), "worker.task_id"),
        authority_key=_required(raw.get("authority_key"), "worker.authority_key"),
        task_spec_hash=_sha64(raw.get("task_spec_hash"), "worker.task_spec_hash"),
        attempt_id=_sha64(raw.get("attempt_id"), "worker.attempt_id"),
        lane=_required(raw.get("lane"), "worker.lane"),
        objective=_required(raw.get("objective"), "worker.objective"),
        stop_boundary=_required(raw.get("stop_boundary"), "worker.stop_boundary"),
        base_sha=_sha40(raw.get("base_sha"), "worker.base_sha"),
        allowed_paths=tuple(str(value) for value in paths),
        protected_paths=tuple(str(value) for value in protected),
        tier=WorkerTier(str(raw.get("tier") or "")),
        context_text=str(raw.get("context_text") or ""),
    )
    if worker.task_id != spec.task_id:
        raise ValueError("worker task_id differs from frozen task")
    if worker.authority_key != spec.authority_key:
        raise ValueError("worker authority differs from frozen task")
    if worker.task_spec_hash != spec.spec_hash:
        raise ValueError("worker task spec hash mismatch")
    if worker.attempt_id != attempt.attempt_id:
        raise ValueError("worker attempt identity mismatch")
    if worker.base_sha != spec.base_sha:
        raise ValueError("worker base SHA mismatch")
    recorded_branch = _required(raw.get("branch"), "worker.branch")
    if recorded_branch != worker.branch:
        raise ValueError("worker deterministic branch mismatch")
    if _sha64(raw.get("digest"), "worker.digest") != worker.digest:
        raise ValueError("worker digest mismatch")
    return worker


class HostedAdmissionOutcome(str, Enum):
    ADMITTED = "ADMITTED"
    RECLASSIFY = "RECLASSIFY"
    NOT_DISPATCH = "NOT_DISPATCH"
    BLOCKED = "BLOCKED"


class HostedAdmissionDisposition(str, Enum):
    RECORDED = "RECORDED"
    ALREADY_RECORDED = "ALREADY_RECORDED"
    NO_ACTIVE_PLAN = "NO_ACTIVE_PLAN"
    PLAN_NOT_READY = "PLAN_NOT_READY"
    CLASSIFIER_NOT_READY = "CLASSIFIER_NOT_READY"
    WAIT_REMOTE = "WAIT_REMOTE"
    CONFLICT = "CONFLICT"


@dataclass(frozen=True)
class HostedAdmissionRecord:
    plan_id: str
    event_id: str
    issue_number: int
    classifier_request_id: str
    classifier_run_id: str
    classifier_decision_digest: str
    hydration_digest: str
    authority_digest: str
    current_main: str
    attempt_number: int
    outcome: HostedAdmissionOutcome
    reason: str
    pending_decision_digest: str = ""
    quota_digest: str = ""
    consume_attempt: bool = False
    admission_digest: str = ""
    frozen_task: FrozenTaskSpec | None = None
    attempt: TaskAttemptIdentity | None = None
    worker_spec: FrozenWorkerSpec | None = None

    def __post_init__(self) -> None:
        for name in (
            "plan_id",
            "classifier_request_id",
            "classifier_run_id",
            "classifier_decision_digest",
            "hydration_digest",
            "authority_digest",
        ):
            object.__setattr__(self, name, _sha64(getattr(self, name), name))
        object.__setattr__(self, "event_id", _required(self.event_id, "event_id"))
        object.__setattr__(
            self, "issue_number", _positive(self.issue_number, "issue_number")
        )
        object.__setattr__(self, "current_main", _sha40(self.current_main, "current_main"))
        object.__setattr__(
            self, "attempt_number", _positive(self.attempt_number, "attempt_number")
        )
        if not isinstance(self.outcome, HostedAdmissionOutcome):
            raise ValueError("outcome must be HostedAdmissionOutcome")
        object.__setattr__(self, "reason", _required(self.reason, "reason"))
        if not isinstance(self.consume_attempt, bool):
            raise ValueError("consume_attempt must be boolean")

        if self.admission_digest:
            for name in ("pending_decision_digest", "quota_digest", "admission_digest"):
                object.__setattr__(self, name, _sha64(getattr(self, name), name))
            expected = canonical_digest(
                {
                    "disposition": self._dispatch_disposition().value,
                    "reason": self.reason,
                    "authority_digest": self.authority_digest,
                    "pending_decision_digest": self.pending_decision_digest,
                    "quota_digest": self.quota_digest,
                    "consume_attempt": self.consume_attempt,
                    "frozen_task_hash": (
                        self.frozen_task.spec_hash if self.frozen_task else ""
                    ),
                    "attempt_id": self.attempt.attempt_id if self.attempt else "",
                    "worker_spec_digest": (
                        self.worker_spec.digest if self.worker_spec else ""
                    ),
                }
            )
            if expected != self.admission_digest:
                raise ValueError("admission digest mismatch")
        elif self.pending_decision_digest or self.quota_digest or self.consume_attempt:
            raise ValueError(
                "pre-admission block cannot carry admission-only identity"
            )

        if self.outcome is HostedAdmissionOutcome.ADMITTED:
            if not self.admission_digest:
                raise ValueError("ADMITTED record requires admission digest")
            if self.frozen_task is None or self.attempt is None or self.worker_spec is None:
                raise ValueError("ADMITTED record requires frozen task/attempt/worker")
            if not self.consume_attempt:
                raise ValueError("ADMITTED record must consume one admitted attempt")
        elif any(
            value is not None
            for value in (self.frozen_task, self.attempt, self.worker_spec)
        ):
            raise ValueError("non-admitted record cannot carry frozen worker authority")

    def _dispatch_disposition(self) -> DispatchAdmissionDisposition:
        mapping = {
            HostedAdmissionOutcome.ADMITTED: DispatchAdmissionDisposition.ADMIT,
            HostedAdmissionOutcome.RECLASSIFY: DispatchAdmissionDisposition.RECLASSIFY,
            HostedAdmissionOutcome.NOT_DISPATCH: DispatchAdmissionDisposition.NOT_DISPATCH,
            HostedAdmissionOutcome.BLOCKED: DispatchAdmissionDisposition.BLOCK,
        }
        return mapping[self.outcome]

    @property
    def record_id(self) -> str:
        return canonical_digest(
            {
                "plan_id": self.plan_id,
                "classifier_run_id": self.classifier_run_id,
                "attempt_number": self.attempt_number,
            }
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "plan_id": self.plan_id,
            "event_id": self.event_id,
            "issue_number": self.issue_number,
            "classifier_request_id": self.classifier_request_id,
            "classifier_run_id": self.classifier_run_id,
            "classifier_decision_digest": self.classifier_decision_digest,
            "hydration_digest": self.hydration_digest,
            "authority_digest": self.authority_digest,
            "current_main": self.current_main,
            "attempt_number": self.attempt_number,
            "outcome": self.outcome.value,
            "reason": self.reason,
            "pending_decision_digest": self.pending_decision_digest,
            "quota_digest": self.quota_digest,
            "consume_attempt": self.consume_attempt,
            "admission_digest": self.admission_digest,
            "frozen_task": (
                _frozen_task_dict(self.frozen_task)
                if self.frozen_task is not None
                else None
            ),
            "attempt": (
                _attempt_dict(self.attempt) if self.attempt is not None else None
            ),
            "worker_spec": (
                _worker_dict(self.worker_spec)
                if self.worker_spec is not None
                else None
            ),
            "record_id": self.record_id,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "HostedAdmissionRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("hosted admission record must be object")
        task_raw = raw.get("frozen_task")
        attempt_raw = raw.get("attempt")
        worker_raw = raw.get("worker_spec")
        spec = None if task_raw is None else _frozen_task_from_mapping(task_raw)
        attempt = (
            None
            if attempt_raw is None
            else _attempt_from_mapping(attempt_raw, spec)
        )
        worker = (
            None
            if worker_raw is None
            else _worker_from_mapping(worker_raw, spec=spec, attempt=attempt)
        )
        record = cls(
            plan_id=raw.get("plan_id"),
            event_id=raw.get("event_id"),
            issue_number=raw.get("issue_number"),
            classifier_request_id=raw.get("classifier_request_id"),
            classifier_run_id=raw.get("classifier_run_id"),
            classifier_decision_digest=raw.get("classifier_decision_digest"),
            hydration_digest=raw.get("hydration_digest"),
            authority_digest=raw.get("authority_digest"),
            current_main=raw.get("current_main"),
            attempt_number=raw.get("attempt_number"),
            outcome=HostedAdmissionOutcome(str(raw.get("outcome") or "")),
            reason=str(raw.get("reason") or ""),
            pending_decision_digest=str(raw.get("pending_decision_digest") or ""),
            quota_digest=str(raw.get("quota_digest") or ""),
            consume_attempt=raw.get("consume_attempt", False),
            admission_digest=str(raw.get("admission_digest") or ""),
            frozen_task=spec,
            attempt=attempt,
            worker_spec=worker,
        )
        if _sha64(raw.get("record_id"), "record_id") != record.record_id:
            raise ValueError("hosted admission record identity mismatch")
        return record


@dataclass(frozen=True)
class HostedAdmissionLedger:
    record: HostedAdmissionRecord | None = None

    @classmethod
    def from_mapping(cls, raw: Any) -> "HostedAdmissionLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid hosted admission ledger")
        value = raw.get("record")
        return cls(
            None if value is None else HostedAdmissionRecord.from_mapping(value)
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "record": self.record.as_dict() if self.record is not None else None,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class HostedAdmissionStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "HostedAdmissionStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / HOSTED_ADMISSION_RELATIVE_PATH,
                backup_path=root / HOSTED_ADMISSION_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> HostedAdmissionLedger:
        return HostedAdmissionLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: HostedAdmissionLedger) -> HostedAdmissionLedger:
        self.adapter.save(ledger.as_dict())
        return ledger


@dataclass(frozen=True)
class HostedAdmissionAdvanceResult:
    disposition: HostedAdmissionDisposition
    reason: str
    ledger: HostedAdmissionLedger

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "ledger_digest": self.ledger.digest,
            }
        )


def _outcome(disposition: DispatchAdmissionDisposition) -> HostedAdmissionOutcome:
    return {
        DispatchAdmissionDisposition.ADMIT: HostedAdmissionOutcome.ADMITTED,
        DispatchAdmissionDisposition.RECLASSIFY: HostedAdmissionOutcome.RECLASSIFY,
        DispatchAdmissionDisposition.NOT_DISPATCH: HostedAdmissionOutcome.NOT_DISPATCH,
        DispatchAdmissionDisposition.BLOCK: HostedAdmissionOutcome.BLOCKED,
    }[disposition]


def _record_from_admission(
    *,
    plan,
    classifier,
    hydration,
    current_main: str,
    attempt_number: int,
    admission: DispatchAdmissionResult,
) -> HostedAdmissionRecord:
    decision = classifier.decision
    assert decision is not None
    return HostedAdmissionRecord(
        plan_id=plan.plan_id,
        event_id=plan.event_id,
        issue_number=plan.issue_number,
        classifier_request_id=classifier.request.request_id,
        classifier_run_id=classifier.run_id,
        classifier_decision_digest=canonical_digest(decision.as_dict()),
        hydration_digest=hydration.digest,
        authority_digest=admission.authority_digest,
        current_main=current_main,
        attempt_number=attempt_number,
        outcome=_outcome(admission.disposition),
        reason=admission.reason,
        pending_decision_digest=admission.pending_decision_digest,
        quota_digest=admission.quota.digest,
        consume_attempt=admission.consume_attempt,
        admission_digest=admission.digest,
        frozen_task=admission.frozen_task,
        attempt=admission.attempt,
        worker_spec=admission.worker_spec,
    )


def _blocked_before_admission(
    *,
    plan,
    classifier,
    hydration_digest: str,
    authority_digest: str,
    current_main: str,
    attempt_number: int,
    reason: str,
) -> HostedAdmissionRecord:
    decision = classifier.decision
    assert decision is not None
    return HostedAdmissionRecord(
        plan_id=plan.plan_id,
        event_id=plan.event_id,
        issue_number=plan.issue_number,
        classifier_request_id=classifier.request.request_id,
        classifier_run_id=classifier.run_id,
        classifier_decision_digest=canonical_digest(decision.as_dict()),
        hydration_digest=_sha64(hydration_digest, "hydration_digest"),
        authority_digest=_sha64(authority_digest, "authority_digest"),
        current_main=current_main,
        attempt_number=attempt_number,
        outcome=HostedAdmissionOutcome.BLOCKED,
        reason=reason,
    )


def advance_hosted_dispatch_admission(
    *,
    root: Path,
    plan_ledger: HostedTaskPlanLedger,
    trusted_actors: Iterable[str],
    repo: str,
    active_external_claims: Iterable[ExternalProducerClaim],
    provider_quota: ProviderQuotaDecision | None,
    local_budget: LocalBudgetObservation,
    attempt_number: int,
    runner=None,
) -> HostedAdmissionAdvanceResult:
    """Freshly admit one classifier proposal, freezing but never launching a worker."""

    root = Path(root).resolve()
    store = HostedAdmissionStore.for_root(root)
    existing = store.load()
    plan = plan_ledger.active

    if plan is None:
        return HostedAdmissionAdvanceResult(
            HostedAdmissionDisposition.NO_ACTIVE_PLAN,
            "no hosted task plan is active",
            existing,
        )
    if plan.status is not HostedTaskPlanStatus.READY_FOR_CLASSIFIER or plan.seed is None:
        return HostedAdmissionAdvanceResult(
            HostedAdmissionDisposition.PLAN_NOT_READY,
            "active hosted task plan is not classifier-ready",
            existing,
        )
    _positive(attempt_number, "attempt_number")

    if existing.record is not None:
        if existing.record.plan_id != plan.plan_id:
            return HostedAdmissionAdvanceResult(
                HostedAdmissionDisposition.CONFLICT,
                "different hosted task plan already owns admission state",
                existing,
            )
        if existing.record.attempt_number != attempt_number:
            return HostedAdmissionAdvanceResult(
                HostedAdmissionDisposition.CONFLICT,
                "attempt number differs from durable hosted admission",
                existing,
            )
        return HostedAdmissionAdvanceResult(
            HostedAdmissionDisposition.ALREADY_RECORDED,
            "hosted admission result is already durable",
            existing,
        )

    classifier_store = ClassifierRunStore.for_root(root)
    classifier = classifier_store.load().get(
        plan.seed.classifier_request.request_id
    )
    if classifier is None or classifier.status is not ClassifierRunStatus.COMPLETE:
        return HostedAdmissionAdvanceResult(
            HostedAdmissionDisposition.CLASSIFIER_NOT_READY,
            "exact classifier proposal is not durably complete",
            existing,
        )
    if classifier.request != plan.seed.classifier_request or classifier.decision is None:
        return HostedAdmissionAdvanceResult(
            HostedAdmissionDisposition.CONFLICT,
            "durable classifier proposal identity differs from hosted plan",
            existing,
        )

    authority_events = TaskAuthorityEventStore.for_root(root).load()
    authority_record = authority_events.get(plan.event_id)
    if authority_record is None:
        return HostedAdmissionAdvanceResult(
            HostedAdmissionDisposition.CONFLICT,
            "signed task-authority capture is missing",
            existing,
        )
    if authority_record.reference.issue_number != plan.issue_number:
        return HostedAdmissionAdvanceResult(
            HostedAdmissionDisposition.CONFLICT,
            "signed task-authority issue differs from hosted plan",
            existing,
        )

    hydrator_kwargs = {
        "reference": authority_record.reference,
        "trusted_actors": tuple(trusted_actors),
    }
    main_kwargs = {"repo": repo}
    if runner is not None:
        hydrator_kwargs["runner"] = runner
        main_kwargs["runner"] = runner

    try:
        hydration = GhTaskAuthorityHydrator(**hydrator_kwargs).hydrate()
        current_main = GhAcceptedMainReader(**main_kwargs).read()
    except (TaskAuthorityRemoteUnavailable, AcceptedMainRemoteUnavailable) as exc:
        return HostedAdmissionAdvanceResult(
            HostedAdmissionDisposition.WAIT_REMOTE,
            f"fresh admission truth unavailable: {exc}",
            existing,
        )

    if (
        hydration.disposition is not TaskAuthorityDisposition.EXECUTABLE_V2
        or hydration.identity is None
        or hydration.authority is None
    ):
        identity_digest = (
            hydration.identity.digest
            if hydration.identity is not None
            else plan.seed.authority_identity_digest
        )
        authority_digest = (
            hydration.authority.digest
            if hydration.authority is not None
            else plan.seed.authority_digest
        )
        blocked = _blocked_before_admission(
            plan=plan,
            classifier=classifier,
            hydration_digest=hydration.digest,
            authority_digest=authority_digest,
            current_main=current_main,
            attempt_number=attempt_number,
            reason=hydration.reason,
        )
        ledger = HostedAdmissionLedger(blocked)
        store.save(ledger)
        return HostedAdmissionAdvanceResult(
            HostedAdmissionDisposition.RECORDED,
            blocked.reason,
            ledger,
        )

    if (
        hydration.identity.digest != plan.seed.authority_identity_digest
        or hydration.authority.digest != plan.seed.authority_digest
    ):
        blocked = _blocked_before_admission(
            plan=plan,
            classifier=classifier,
            hydration_digest=hydration.digest,
            authority_digest=hydration.authority.digest,
            current_main=current_main,
            attempt_number=attempt_number,
            reason="fresh repository task authority differs from classifier seed",
        )
        ledger = HostedAdmissionLedger(blocked)
        store.save(ledger)
        return HostedAdmissionAdvanceResult(
            HostedAdmissionDisposition.RECORDED,
            blocked.reason,
            ledger,
        )

    pending = PendingDecisionRecord.from_legacy_mapping(
        {
            "decision": classifier.decision.as_dict(),
            "event_keys": [plan.event_id],
            "authority_event_keys": [plan.event_id],
            "ordinary_event_keys": [],
            "task_issue_numbers": [plan.issue_number],
            "snapshot_main": plan.seed.classifier_request.current_main,
            # R5C15/R5C16 did not capture classification-time source-PR head.
            # Any proposal that supplies pr_number therefore fails freshness and
            # is forced through RECLASSIFY rather than inventing evidence here.
            "source_pr_head": None,
        }
    )
    freshness = DecisionFreshnessObservation(current_main=current_main)

    admission = admit_dispatch(
        authority=hydration.authority,
        record=pending,
        freshness=freshness,
        current_main=current_main,
        active_external_claims=tuple(active_external_claims),
        provider_quota=provider_quota,
        local_budget=local_budget,
        attempt_number=attempt_number,
    )
    record = _record_from_admission(
        plan=plan,
        classifier=classifier,
        hydration=hydration,
        current_main=current_main,
        attempt_number=attempt_number,
        admission=admission,
    )
    ledger = HostedAdmissionLedger(record)
    store.save(ledger)
    return HostedAdmissionAdvanceResult(
        HostedAdmissionDisposition.RECORDED,
        admission.reason,
        ledger,
    )
