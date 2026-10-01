"""Restart-safe ordinary control-pipeline composition for Platform v2 R5C6.

The classifier proposes; repository authority admits; the admitted frozen worker runs in
an isolated worktree; only a durable HANDOFF_READY run may enter the accepted ordinary
handoff/effect service. Managed merge remains a separate transition.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum
from pathlib import Path
from typing import Any, Callable, Iterable, Mapping, Protocol

from .canary import WRITER_FENCE_RELATIVE_PATH
from .classifier_provider import (
    ClassifierAdvanceDisposition,
    ClassifierProvider,
    ClassifierProviderConfig,
    ClassifierRequest,
    ClassifierRunStore,
    advance_classifier,
    classifier_provider_config,
)
from .decision import (
    DecisionFreshnessObservation,
    PendingDecisionRecord,
)
from .dispatch_admission import (
    DispatchAdmissionDisposition,
    DispatchAdmissionResult,
    RepositoryTaskAuthority,
    admit_dispatch,
)
from .external import ExternalProducerClaim
from .fence import FenceBusyError, WriterFence
from .identity import canonical_digest
from .ordinary_effect_executor import OrdinaryEffectAdapter
from .ordinary_effects import OrdinaryEffectStore
from .ordinary_remote import GhGitOrdinaryEffectAdapter, OrdinaryEffectBinding
from .ordinary_service import (
    ManagedOrdinaryHandoff,
    OrdinaryHandoffResult,
    OrdinaryServiceDisposition,
    PreparedWorkerTask,
    advance_prepared_handoff,
)
from .ownership import OwnershipToken
from .quota import LocalBudgetObservation, ProviderQuotaDecision
from .state_store import JsonStateStoreAdapter
from .worker_provider import (
    FrozenWorkerSpec,
    WorkerAdvanceDisposition,
    WorkerProvider,
    WorkerProviderConfig,
    WorkerRunStore,
    advance_worker_run,
    provider_config_for_tier,
)
from .worker_workspace import WorkerWorkspaceManager
from .workspace_commit import WorkspaceCommitAdapter, WorkspaceCommitScope


PIPELINE_STATE_RELATIVE_PATH = Path(".skyforge-platform-v2") / "ordinary-pipelines.json"
PIPELINE_STATE_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "ordinary-pipelines.json.bak"
)


class OrdinaryPipelineStage(str, Enum):
    NEW = "NEW"
    CLASSIFIER_BLOCKED = "CLASSIFIER_BLOCKED"
    NOT_DISPATCH = "NOT_DISPATCH"
    RECLASSIFY = "RECLASSIFY"
    ADMISSION_BLOCKED = "ADMISSION_BLOCKED"
    ADMITTED = "ADMITTED"
    WORKER_BLOCKED = "WORKER_BLOCKED"
    HANDOFF_READY = "HANDOFF_READY"
    COMPLETE = "COMPLETE"


class OrdinaryPipelineDisposition(str, Enum):
    COMPLETE = "COMPLETE"
    BLOCKED = "BLOCKED"
    NOT_DISPATCH = "NOT_DISPATCH"
    RECLASSIFY = "RECLASSIFY"


@dataclass(frozen=True)
class OrdinaryPipelineRequest:
    classifier_request: ClassifierRequest
    authority: RepositoryTaskAuthority
    freshness: DecisionFreshnessObservation
    current_main: str
    external_claims: tuple[ExternalProducerClaim, ...]
    provider_quota: ProviderQuotaDecision | None
    local_budget: LocalBudgetObservation
    attempt_number: int
    repo: str
    pr_title: str
    pr_body: str
    event_keys: tuple[str, ...] = ()
    authority_event_keys: tuple[str, ...] = ()
    ordinary_event_keys: tuple[str, ...] = ()
    source_pr_head_at_classification: str = ""
    controller_comment_issue: int | None = None
    controller_comment_body: str = ""

    def __post_init__(self) -> None:
        if self.classifier_request.current_main != self.current_main:
            raise ValueError("classifier request main must equal pipeline current_main")
        if self.freshness.current_main != self.current_main:
            raise ValueError("freshness main must equal pipeline current_main")
        if len(self.current_main) != 40 or any(
            ch not in "0123456789abcdef" for ch in self.current_main
        ):
            raise ValueError("current_main must be lowercase 40-character Git SHA")
        if isinstance(self.attempt_number, bool) or self.attempt_number < 1:
            raise ValueError("attempt_number must be >= 1")
        if "/" not in self.repo or self.repo.count("/") != 1:
            raise ValueError("repo must be owner/name")
        if not self.pr_title.strip() or not self.pr_body.strip():
            raise ValueError("PR title/body are required")
        if self.controller_comment_issue is not None:
            if self.controller_comment_issue not in self.authority.issue_numbers:
                raise ValueError(
                    "controller comment issue must be governed by repository authority"
                )
            if not self.controller_comment_body.strip():
                raise ValueError("controller comment issue requires a comment body")
        elif self.controller_comment_body.strip():
            raise ValueError("controller comment body requires an issue number")

    @property
    def pipeline_id(self) -> str:
        return canonical_digest(
            {
                "classifier_request_id": self.classifier_request.request_id,
                "authority_digest": self.authority.digest,
                "current_main": self.current_main,
                "attempt_number": self.attempt_number,
                "repo": self.repo,
                "pr_title": self.pr_title,
                "pr_body": self.pr_body,
                "event_keys": list(self.event_keys),
                "authority_event_keys": list(self.authority_event_keys),
                "ordinary_event_keys": list(self.ordinary_event_keys),
                "source_pr_head_at_classification": self.source_pr_head_at_classification,
                "controller_comment_issue": self.controller_comment_issue,
                "controller_comment_body": self.controller_comment_body,
            }
        )


@dataclass(frozen=True)
class OrdinaryPipelineRecord:
    pipeline_id: str
    classifier_request_id: str
    authority_digest: str
    stage: OrdinaryPipelineStage = OrdinaryPipelineStage.NEW
    reason: str = ""
    classifier_run_id: str = ""
    classifier_decision_digest: str = ""
    admission_digest: str = ""
    task_spec_hash: str = ""
    attempt_id: str = ""
    worker_spec_digest: str = ""
    worker_run_id: str = ""
    handoff_digest: str = ""
    pr_number: int | None = None
    managed_handoff: ManagedOrdinaryHandoff | None = None

    def as_dict(self) -> dict[str, Any]:
        return {
            "pipeline_id": self.pipeline_id,
            "classifier_request_id": self.classifier_request_id,
            "authority_digest": self.authority_digest,
            "stage": self.stage.value,
            "reason": self.reason,
            "classifier_run_id": self.classifier_run_id,
            "classifier_decision_digest": self.classifier_decision_digest,
            "admission_digest": self.admission_digest,
            "task_spec_hash": self.task_spec_hash,
            "attempt_id": self.attempt_id,
            "worker_spec_digest": self.worker_spec_digest,
            "worker_run_id": self.worker_run_id,
            "handoff_digest": self.handoff_digest,
            "pr_number": self.pr_number,
            "managed_handoff": (
                self.managed_handoff.as_dict()
                if self.managed_handoff is not None
                else None
            ),
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "OrdinaryPipelineRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("pipeline record must be an object")
        pr_number = raw.get("pr_number")
        if pr_number is not None and (
            isinstance(pr_number, bool)
            or not isinstance(pr_number, int)
            or pr_number <= 0
        ):
            raise ValueError("pipeline pr_number must be positive or null")
        handoff_raw = raw.get("managed_handoff")
        managed_handoff = (
            ManagedOrdinaryHandoff.from_mapping(handoff_raw)
            if handoff_raw is not None
            else None
        )
        record = cls(
            pipeline_id=str(raw.get("pipeline_id") or ""),
            classifier_request_id=str(raw.get("classifier_request_id") or ""),
            authority_digest=str(raw.get("authority_digest") or ""),
            stage=OrdinaryPipelineStage(str(raw.get("stage") or "")),
            reason=str(raw.get("reason") or ""),
            classifier_run_id=str(raw.get("classifier_run_id") or ""),
            classifier_decision_digest=str(raw.get("classifier_decision_digest") or ""),
            admission_digest=str(raw.get("admission_digest") or ""),
            task_spec_hash=str(raw.get("task_spec_hash") or ""),
            attempt_id=str(raw.get("attempt_id") or ""),
            worker_spec_digest=str(raw.get("worker_spec_digest") or ""),
            worker_run_id=str(raw.get("worker_run_id") or ""),
            handoff_digest=str(raw.get("handoff_digest") or ""),
            pr_number=pr_number,
            managed_handoff=managed_handoff,
        )
        if managed_handoff is not None:
            if record.handoff_digest != managed_handoff.digest:
                raise ValueError("pipeline managed handoff digest mismatch")
            if record.pr_number != managed_handoff.pr_number:
                raise ValueError("pipeline managed handoff PR number mismatch")
            if record.task_spec_hash != managed_handoff.task_spec_hash:
                raise ValueError("pipeline managed handoff task_spec_hash mismatch")
            if record.attempt_id != managed_handoff.scope.attempt_id:
                raise ValueError("pipeline managed handoff attempt_id mismatch")
        return record

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class OrdinaryPipelineLedger:
    records: tuple[OrdinaryPipelineRecord, ...] = ()

    @classmethod
    def from_mapping(cls, raw: Any) -> "OrdinaryPipelineLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid ordinary pipeline ledger")
        values = raw.get("records")
        if not isinstance(values, list):
            raise ValueError("pipeline records must be a list")
        records = tuple(OrdinaryPipelineRecord.from_mapping(v) for v in values)
        ids = [record.pipeline_id for record in records]
        if len(set(ids)) != len(ids):
            raise ValueError("duplicate pipeline_id")
        return cls(records)

    def as_dict(self) -> dict[str, Any]:
        return {"schema_version": 1, "records": [r.as_dict() for r in self.records]}

    def get(self, pipeline_id: str) -> OrdinaryPipelineRecord | None:
        for record in self.records:
            if record.pipeline_id == pipeline_id:
                return record
        return None

    def put(self, record: OrdinaryPipelineRecord) -> "OrdinaryPipelineLedger":
        current = self.get(record.pipeline_id)
        if current is None:
            return OrdinaryPipelineLedger(self.records + (record,))
        return OrdinaryPipelineLedger(
            tuple(
                record if item.pipeline_id == record.pipeline_id else item
                for item in self.records
            )
        )

    def reconstructible_managed_handoffs(self) -> tuple[ManagedOrdinaryHandoff, ...]:
        """Return only exact durable handoffs safe for later lifecycle processing."""
        handoffs: list[ManagedOrdinaryHandoff] = []
        for record in self.records:
            if (
                record.stage is OrdinaryPipelineStage.COMPLETE
                and record.managed_handoff is not None
            ):
                handoffs.append(record.managed_handoff)
        return tuple(handoffs)

    def incomplete_completed_records(self) -> tuple[OrdinaryPipelineRecord, ...]:
        """Pre-R5C10 completed records remain visible but fail closed for automation."""
        return tuple(
            record
            for record in self.records
            if (
                record.stage is OrdinaryPipelineStage.COMPLETE
                and record.managed_handoff is None
            )
        )


@dataclass(frozen=True)
class OrdinaryPipelineStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "OrdinaryPipelineStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / PIPELINE_STATE_RELATIVE_PATH,
                backup_path=root / PIPELINE_STATE_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> OrdinaryPipelineLedger:
        return OrdinaryPipelineLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: OrdinaryPipelineLedger) -> OrdinaryPipelineLedger:
        self.adapter.save(ledger.as_dict())
        return ledger


@dataclass(frozen=True)
class OrdinaryPipelineResult:
    disposition: OrdinaryPipelineDisposition
    reason: str
    record: OrdinaryPipelineRecord
    admission: DispatchAdmissionResult | None = None
    handoff: ManagedOrdinaryHandoff | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "record_digest": self.record.digest,
                "admission_digest": self.admission.digest if self.admission else "",
                "handoff_digest": self.handoff.digest if self.handoff else "",
            }
        )


class WorkerHandoffPort(Protocol):
    def advance(
        self,
        *,
        worker_spec: FrozenWorkerSpec,
        authority: RepositoryTaskAuthority,
        request: OrdinaryPipelineRequest,
    ) -> tuple[WorkerAdvanceDisposition, str, str, ManagedOrdinaryHandoff | None]: ...


class AcceptedWorkerHandoffPort:
    """Concrete R5C3 + R5C2 composition using accepted lower-layer stores/adapters."""

    def __init__(
        self,
        *,
        root: Path,
        worker_store: WorkerRunStore,
        worker_provider: WorkerProvider,
        effect_store: OrdinaryEffectStore,
        remote_factory: Callable[[Path, OrdinaryEffectBinding], OrdinaryEffectAdapter]
        | None = None,
        worker_config_factory: Callable[[FrozenWorkerSpec], WorkerProviderConfig]
        | None = None,
    ) -> None:
        self.root = Path(root).resolve()
        self.worker_store = worker_store
        self.worker_provider = worker_provider
        self.effect_store = effect_store
        self.workspace_manager = WorkerWorkspaceManager(root=self.root)
        self.remote_factory = remote_factory or (
            lambda worktree, binding: GhGitOrdinaryEffectAdapter(
                root=worktree,
                binding=binding,
            )
        )
        self.worker_config_factory = worker_config_factory or (
            lambda spec: provider_config_for_tier(spec.tier)
        )

    def _worktree_for(self, spec: FrozenWorkerSpec) -> Path:
        existing = self.worker_store.load().find_attempt(spec.attempt_id)
        expected = self.workspace_manager.expected_path(spec)
        if existing is None:
            return self.workspace_manager.prepare(spec).worktree
        if existing.spec.digest != spec.digest:
            raise ValueError("durable worker spec drifted from admitted worker")
        actual = Path(existing.worktree).resolve()
        if actual != expected or not actual.is_dir():
            raise RuntimeError("durable worker worktree identity is unavailable")
        return actual

    def advance(
        self,
        *,
        worker_spec: FrozenWorkerSpec,
        authority: RepositoryTaskAuthority,
        request: OrdinaryPipelineRequest,
    ) -> tuple[WorkerAdvanceDisposition, str, str, ManagedOrdinaryHandoff | None]:
        worktree = self._worktree_for(worker_spec)
        worker = advance_worker_run(
            spec=worker_spec,
            worktree=worktree,
            store=self.worker_store,
            provider=self.worker_provider,
            config=self.worker_config_factory(worker_spec),
        )
        if worker.disposition not in {
            WorkerAdvanceDisposition.HANDOFF_READY,
            WorkerAdvanceDisposition.ALREADY_READY,
        }:
            return worker.disposition, worker.reason, worker.record.run_id, None

        scope = WorkspaceCommitScope(
            attempt_id=worker_spec.attempt_id,
            branch=worker_spec.branch,
            start_head=worker_spec.base_sha,
            allowed_paths=worker_spec.allowed_paths,
            protected_paths=worker_spec.protected_paths,
        )
        workspace = WorkspaceCommitAdapter(worktree=worktree, scope=scope)
        prepared = PreparedWorkerTask(
            task_id=worker_spec.task_id,
            authority_key=worker_spec.authority_key,
            task_spec_hash=worker_spec.task_spec_hash,
            attempt_id=worker_spec.attempt_id,
            repo=request.repo,
            base_sha=worker_spec.base_sha,
            branch=worker_spec.branch,
            lane=worker_spec.lane,
            objective=worker_spec.objective,
            pr_title=request.pr_title,
            pr_body=request.pr_body,
            issue_number=request.controller_comment_issue,
            comment_body=request.controller_comment_body,
            auto_merge_eligible=authority.auto_merge_eligible,
        )
        handoff = advance_prepared_handoff(
            task=prepared,
            workspace=workspace,
            store=self.effect_store,
            remote_factory=lambda binding: self.remote_factory(worktree, binding),
        )
        if handoff.disposition is OrdinaryServiceDisposition.COMPLETE:
            return (
                worker.disposition,
                handoff.reason,
                worker.record.run_id,
                handoff.handoff,
            )
        if handoff.disposition is OrdinaryServiceDisposition.NOT_ELIGIBLE:
            return (
                worker.disposition,
                handoff.reason,
                worker.record.run_id,
                None,
            )
        return (
            WorkerAdvanceDisposition.FAILED,
            handoff.reason,
            worker.record.run_id,
            None,
        )


def _pending_record(
    request: OrdinaryPipelineRequest,
    decision: Any,
) -> PendingDecisionRecord:
    return PendingDecisionRecord.from_legacy_mapping(
        {
            "decision": decision.as_dict(),
            "event_keys": list(request.event_keys),
            "authority_event_keys": list(request.authority_event_keys),
            "ordinary_event_keys": list(request.ordinary_event_keys),
            "task_issue_numbers": list(request.authority.issue_numbers),
            "snapshot_main": request.classifier_request.current_main,
            "source_pr_head": request.source_pr_head_at_classification or None,
        }
    )


def advance_ordinary_pipeline(
    *,
    request: OrdinaryPipelineRequest,
    root: Path,
    pipeline_store: OrdinaryPipelineStore,
    classifier_store: ClassifierRunStore,
    classifier_provider: ClassifierProvider,
    worker_handoff: WorkerHandoffPort,
    ownership_token: OwnershipToken,
    classifier_config: ClassifierProviderConfig | None = None,
) -> OrdinaryPipelineResult:
    ledger = pipeline_store.load()
    current = ledger.get(request.pipeline_id)
    if current is None:
        current = OrdinaryPipelineRecord(
            pipeline_id=request.pipeline_id,
            classifier_request_id=request.classifier_request.request_id,
            authority_digest=request.authority.digest,
        )
        ledger = ledger.put(current)
        pipeline_store.save(ledger)
    elif (
        current.classifier_request_id != request.classifier_request.request_id
        or current.authority_digest != request.authority.digest
    ):
        raise ValueError("durable pipeline authority identity drifted")

    classifier = advance_classifier(
        request=request.classifier_request,
        root=Path(root),
        store=classifier_store,
        provider=classifier_provider,
        config=classifier_config or classifier_provider_config(),
    )
    current = replace(current, classifier_run_id=classifier.record.run_id)

    if classifier.disposition in {
        ClassifierAdvanceDisposition.FAILED,
        ClassifierAdvanceDisposition.RECOVERY_REQUIRED,
    }:
        current = replace(
            current,
            stage=OrdinaryPipelineStage.CLASSIFIER_BLOCKED,
            reason=classifier.reason,
        )
        pipeline_store.save(ledger.put(current))
        return OrdinaryPipelineResult(
            OrdinaryPipelineDisposition.BLOCKED,
            classifier.reason,
            current,
        )

    decision = classifier.record.decision
    if decision is None:
        raise RuntimeError("completed classifier run lacks typed decision")
    current = replace(
        current,
        classifier_decision_digest=canonical_digest(decision.as_dict()),
    )

    pending = _pending_record(request, decision)
    admission = admit_dispatch(
        authority=request.authority,
        record=pending,
        freshness=request.freshness,
        current_main=request.current_main,
        active_external_claims=request.external_claims,
        provider_quota=request.provider_quota,
        local_budget=request.local_budget,
        attempt_number=request.attempt_number,
    )
    current = replace(current, admission_digest=admission.digest)

    if admission.disposition is DispatchAdmissionDisposition.RECLASSIFY:
        current = replace(
            current,
            stage=OrdinaryPipelineStage.RECLASSIFY,
            reason=admission.reason,
        )
        pipeline_store.save(ledger.put(current))
        return OrdinaryPipelineResult(
            OrdinaryPipelineDisposition.RECLASSIFY,
            admission.reason,
            current,
            admission=admission,
        )

    if admission.disposition is DispatchAdmissionDisposition.NOT_DISPATCH:
        current = replace(
            current,
            stage=OrdinaryPipelineStage.NOT_DISPATCH,
            reason=admission.reason,
        )
        pipeline_store.save(ledger.put(current))
        return OrdinaryPipelineResult(
            OrdinaryPipelineDisposition.NOT_DISPATCH,
            admission.reason,
            current,
            admission=admission,
        )

    if admission.disposition is not DispatchAdmissionDisposition.ADMIT:
        current = replace(
            current,
            stage=OrdinaryPipelineStage.ADMISSION_BLOCKED,
            reason=admission.reason,
        )
        pipeline_store.save(ledger.put(current))
        return OrdinaryPipelineResult(
            OrdinaryPipelineDisposition.BLOCKED,
            admission.reason,
            current,
            admission=admission,
        )

    if (
        admission.frozen_task is None
        or admission.attempt is None
        or admission.worker_spec is None
    ):
        raise RuntimeError("ADMIT result lacks frozen task/attempt/worker identities")

    current = replace(
        current,
        stage=OrdinaryPipelineStage.ADMITTED,
        reason=admission.reason,
        task_spec_hash=admission.frozen_task.spec_hash,
        attempt_id=admission.attempt.attempt_id,
        worker_spec_digest=admission.worker_spec.digest,
    )
    pipeline_store.save(ledger.put(current))

    fence_path = Path(root) / WRITER_FENCE_RELATIVE_PATH
    try:
        with WriterFence.for_token(fence_path, ownership_token):
            worker_disp, reason, worker_run_id, handoff = worker_handoff.advance(
                worker_spec=admission.worker_spec,
                authority=request.authority,
                request=request,
            )
    except FenceBusyError:
        current = replace(
            current,
            stage=OrdinaryPipelineStage.WORKER_BLOCKED,
            reason="Platform-v2 writer fence is busy",
        )
        pipeline_store.save(ledger.put(current))
        return OrdinaryPipelineResult(
            OrdinaryPipelineDisposition.BLOCKED,
            current.reason,
            current,
            admission=admission,
        )

    current = replace(current, worker_run_id=worker_run_id)
    if handoff is None:
        current = replace(
            current,
            stage=OrdinaryPipelineStage.WORKER_BLOCKED,
            reason=reason,
        )
        pipeline_store.save(ledger.put(current))
        return OrdinaryPipelineResult(
            OrdinaryPipelineDisposition.BLOCKED,
            reason,
            current,
            admission=admission,
        )

    current = replace(
        current,
        stage=OrdinaryPipelineStage.COMPLETE,
        reason=reason,
        handoff_digest=handoff.digest,
        pr_number=handoff.pr_number,
        managed_handoff=handoff,
    )
    pipeline_store.save(ledger.put(current))
    return OrdinaryPipelineResult(
        OrdinaryPipelineDisposition.COMPLETE,
        reason,
        current,
        admission=admission,
        handoff=handoff,
    )
