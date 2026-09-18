"""Gated hosted execution coordinator for Platform v2 R5C24.

This module composes already-accepted Release-5 capabilities behind a fail-closed
production activation gate.  It does not change service/proxy configuration or writer authority.
The coordinator advances at most one durable lifecycle boundary per call.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
from pathlib import Path
import subprocess
from typing import Any, Callable, Iterable, Mapping

from .activation_gate import (
    ProductionActivationDisposition,
    ProductionActivationInput,
    evaluate_production_activation,
)
from .classifier_provider import (
    ClassifierProvider,
    ClassifierProviderConfig,
    ClassifierRunStatus,
    ClassifierRunStore,
)
from .cutover import (
    CutoverReadinessDecision,
    CutoverReadinessDisposition,
    WriterAuthority,
)
from .decision import DecisionFreshnessObservation
from .dispatch_admission import RepositoryTaskAuthority
from .dormant_handoff_commit import (
    DormantCommitOutcome,
    DormantHandoffCommitStore,
    advance_dormant_handoff_commit,
)
from .dormant_worker import (
    DormantWorkerDisposition,
    advance_dormant_admitted_worker,
)
from .external import ExternalProducerClaim
from .external_service import ExternalClaimStore
from .hosted_admission import (
    HostedAdmissionOutcome,
    HostedAdmissionStore,
    advance_hosted_task_admission,
)
from .hosted_classifier import advance_hosted_classifier_proposal
from .hosted_state import HostedStateStore
from .hosted_task_plan import (
    HostedTaskPlanDisposition,
    HostedTaskPlanStatus,
    HostedTaskPlanStore,
    advance_claimed_task_preflight,
    claim_next_protected_task,
)
from .identity import canonical_digest
from .ordinary_effects import OrdinaryEffectStore
from .ordinary_pipeline import (
    OrdinaryPipelineLedger,
    OrdinaryPipelineRecord,
    OrdinaryPipelineStage,
    OrdinaryPipelineStore,
)
from .ordinary_remote import (
    GhGitOrdinaryEffectAdapter,
    OrdinaryEffectBinding,
)
from .ordinary_service import (
    ManagedOrdinaryHandoff,
    OrdinaryServiceDisposition,
    PreparedWorkerTask,
    advance_prepared_handoff,
)
from .managed_pr_lifecycle import (
    ManagedLifecycleDisposition,
    advance_managed_pr_lifecycle,
)
from .quota import LocalBudgetObservation, ProviderQuotaDecision
from .task_event_composition import TaskAuthorityEventStore
from .worker_provider import (
    WorkerProvider,
    WorkerProviderConfig,
    WorkerRunStatus,
    WorkerRunStore,
)
from .worker_workspace import WorkerWorkspaceManager
from .workspace_commit import WorkspaceCommitAdapter, WorkspaceCommitScope


def _required(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


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


def _bool(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise ValueError(f"{label} must be boolean")
    return value


def read_checkout_head(
    root: Path,
    *,
    runner=subprocess.run,
) -> str:
    try:
        result = runner(
            ["git", "rev-parse", "HEAD"],
            cwd=Path(root).resolve(),
            check=True,
            text=True,
            capture_output=True,
            timeout=30,
        )
    except subprocess.SubprocessError as exc:
        raise RuntimeError("unable to read hosted checkout HEAD") from exc
    return _sha40(result.stdout.strip(), "checkout HEAD")


def _cutover_from_mapping(raw: Any) -> CutoverReadinessDecision:
    if not isinstance(raw, Mapping):
        raise ValueError("cutover decision must be an object")
    blockers = raw.get("blockers")
    if not isinstance(blockers, list) or any(
        not isinstance(value, str) or not value.strip() for value in blockers
    ):
        raise ValueError("cutover blockers must be a list of non-empty strings")
    return CutoverReadinessDecision(
        disposition=CutoverReadinessDisposition(str(raw.get("disposition") or "")),
        blockers=tuple(blockers),
        projection_digest=_sha64(raw.get("projection_digest"), "projection_digest"),
        accepted_main_sha=_sha40(raw.get("accepted_main_sha"), "accepted_main_sha"),
        legacy_runtime_sha=_sha40(raw.get("legacy_runtime_sha"), "legacy_runtime_sha"),
    )


def production_activation_input_from_mapping(
    raw: Any,
) -> ProductionActivationInput:
    if not isinstance(raw, Mapping):
        raise ValueError("production activation input must be an object")
    return ProductionActivationInput(
        cutover=_cutover_from_mapping(raw.get("cutover")),
        primary_workstation_preservation_pass=_bool(
            raw.get("primary_workstation_preservation_pass"),
            "primary_workstation_preservation_pass",
        ),
        dr70_migration_hold_cleared=_bool(
            raw.get("dr70_migration_hold_cleared"),
            "dr70_migration_hold_cleared",
        ),
        hosted_shadow_parity_accepted=_bool(
            raw.get("hosted_shadow_parity_accepted"),
            "hosted_shadow_parity_accepted",
        ),
        hosted_execution_path_accepted=_bool(
            raw.get("hosted_execution_path_accepted"),
            "hosted_execution_path_accepted",
        ),
        remote_effect_path_accepted=_bool(
            raw.get("remote_effect_path_accepted"),
            "remote_effect_path_accepted",
        ),
        cutover_rollback_rehearsal_accepted=_bool(
            raw.get("cutover_rollback_rehearsal_accepted"),
            "cutover_rollback_rehearsal_accepted",
        ),
    )


class HostedExecutionGateDisposition(str, Enum):
    BLOCKED = "BLOCKED"
    READY = "READY"


@dataclass(frozen=True)
class HostedExecutionActivationInput:
    operator_activation_requested: bool
    production_activation: ProductionActivationInput
    accepted_main_sha: str
    checkout_head_sha: str
    legacy_writer_revoked_observed: bool
    writer_authority: WriterAuthority

    def __post_init__(self) -> None:
        _bool(self.operator_activation_requested, "operator_activation_requested")
        if not isinstance(self.production_activation, ProductionActivationInput):
            raise ValueError("production_activation must be ProductionActivationInput")
        object.__setattr__(
            self,
            "accepted_main_sha",
            _sha40(self.accepted_main_sha, "accepted_main_sha"),
        )
        object.__setattr__(
            self,
            "checkout_head_sha",
            _sha40(self.checkout_head_sha, "checkout_head_sha"),
        )
        _bool(
            self.legacy_writer_revoked_observed,
            "legacy_writer_revoked_observed",
        )
        if not isinstance(self.writer_authority, WriterAuthority):
            raise ValueError("writer_authority must be WriterAuthority")

    @property
    def digest(self) -> str:
        activation = evaluate_production_activation(self.production_activation)
        return canonical_digest(
            {
                "operator_activation_requested": self.operator_activation_requested,
                "production_activation_digest": activation.digest,
                "accepted_main_sha": self.accepted_main_sha,
                "checkout_head_sha": self.checkout_head_sha,
                "legacy_writer_revoked_observed": self.legacy_writer_revoked_observed,
                "writer_authority": self.writer_authority.value,
            }
        )


@dataclass(frozen=True)
class HostedExecutionGateDecision:
    disposition: HostedExecutionGateDisposition
    blockers: tuple[str, ...]
    activation_input_digest: str
    production_activation_digest: str
    accepted_main_sha: str

    @property
    def enabled(self) -> bool:
        return self.disposition is HostedExecutionGateDisposition.READY

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "blockers": list(self.blockers),
                "activation_input_digest": self.activation_input_digest,
                "production_activation_digest": self.production_activation_digest,
                "accepted_main_sha": self.accepted_main_sha,
            }
        )


def evaluate_hosted_execution_gate(
    value: HostedExecutionActivationInput,
) -> HostedExecutionGateDecision:
    if not isinstance(value, HostedExecutionActivationInput):
        raise ValueError("value must be HostedExecutionActivationInput")

    production = evaluate_production_activation(value.production_activation)
    blockers: list[str] = []

    if not value.operator_activation_requested:
        blockers.append("operator production-execution activation was not requested")
    if production.disposition is not ProductionActivationDisposition.READY_FOR_OPERATOR_REVIEW:
        if production.blockers:
            blockers.extend(
                f"production activation: {reason}"
                for reason in production.blockers
            )
        else:
            blockers.append("production activation evidence is not accepted")
    if value.accepted_main_sha != production.accepted_main_sha:
        blockers.append("supplied accepted main differs from production activation evidence")
    if value.checkout_head_sha != value.accepted_main_sha:
        blockers.append("hosted checkout HEAD differs from supplied accepted main")
    if not value.legacy_writer_revoked_observed:
        blockers.append("legacy writer revocation is not observably confirmed")
    if value.writer_authority is not WriterAuthority.NONE:
        blockers.append("writer authority must be NONE before Platform v2 activation")

    return HostedExecutionGateDecision(
        disposition=(
            HostedExecutionGateDisposition.READY
            if not blockers
            else HostedExecutionGateDisposition.BLOCKED
        ),
        blockers=tuple(blockers),
        activation_input_digest=value.digest,
        production_activation_digest=production.digest,
        accepted_main_sha=value.accepted_main_sha,
    )


def load_hosted_execution_gate(
    path: Path,
    *,
    root: Path,
    runner=subprocess.run,
) -> HostedExecutionGateDecision:
    raw = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
        raise ValueError("hosted execution activation evidence schema_version must be 1")
    production = production_activation_input_from_mapping(
        raw.get("production_activation_input")
    )
    accepted_main = _sha40(raw.get("accepted_main_sha"), "accepted_main_sha")
    supplied_digest = _sha64(
        raw.get("production_activation_input_digest"),
        "production_activation_input_digest",
    )
    if supplied_digest != production.digest:
        raise ValueError("production activation input digest mismatch")

    value = HostedExecutionActivationInput(
        operator_activation_requested=_bool(
            raw.get("operator_activation_requested"),
            "operator_activation_requested",
        ),
        production_activation=production,
        accepted_main_sha=accepted_main,
        checkout_head_sha=read_checkout_head(root, runner=runner),
        legacy_writer_revoked_observed=_bool(
            raw.get("legacy_writer_revoked_observed"),
            "legacy_writer_revoked_observed",
        ),
        writer_authority=WriterAuthority(str(raw.get("writer_authority") or "")),
    )
    return evaluate_hosted_execution_gate(value)


@dataclass(frozen=True)
class HostedExecutionDependencies:
    classifier_provider: ClassifierProvider
    worker_provider: WorkerProvider
    classifier_local_budget: LocalBudgetObservation
    worker_local_budget: LocalBudgetObservation
    classifier_provider_quota: ProviderQuotaDecision | None = None
    worker_provider_quota: ProviderQuotaDecision | None = None
    classifier_config: ClassifierProviderConfig | None = None
    worker_config: WorkerProviderConfig | None = None
    attempt_number: int = 1
    external_claims: tuple[ExternalProducerClaim, ...] = ()
    runner: Callable[..., Any] = subprocess.run
    remote_factory: Callable[[Path, OrdinaryEffectBinding], Any] | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.classifier_local_budget, LocalBudgetObservation):
            raise ValueError("classifier_local_budget must be LocalBudgetObservation")
        if not isinstance(self.worker_local_budget, LocalBudgetObservation):
            raise ValueError("worker_local_budget must be LocalBudgetObservation")
        if isinstance(self.attempt_number, bool) or self.attempt_number < 1:
            raise ValueError("attempt_number must be >= 1")
        if not isinstance(self.external_claims, tuple):
            raise ValueError("external_claims must be tuple")


class HostedExecutionAdvanceDisposition(str, Enum):
    GATE_BLOCKED = "GATE_BLOCKED"
    IDLE = "IDLE"
    TASK_CLAIMED = "TASK_CLAIMED"
    PREFLIGHT_ADVANCED = "PREFLIGHT_ADVANCED"
    CLASSIFIER_ADVANCED = "CLASSIFIER_ADVANCED"
    ADMISSION_ADVANCED = "ADMISSION_ADVANCED"
    WORKER_ADVANCED = "WORKER_ADVANCED"
    LOCAL_COMMIT_ADVANCED = "LOCAL_COMMIT_ADVANCED"
    REMOTE_HANDOFF_ADVANCED = "REMOTE_HANDOFF_ADVANCED"
    MANAGED_PR_ADVANCED = "MANAGED_PR_ADVANCED"
    BLOCKED = "BLOCKED"


@dataclass(frozen=True)
class HostedExecutionAdvanceResult:
    disposition: HostedExecutionAdvanceDisposition
    reason: str
    gate_digest: str
    durable_identity: str = ""

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "gate_digest": self.gate_digest,
                "durable_identity": self.durable_identity,
            }
        )


def _authority_from_frozen_payload(raw: Any) -> RepositoryTaskAuthority:
    if not isinstance(raw, Mapping):
        raise ValueError("frozen task payload must be object")
    authority = raw.get("authority")
    if not isinstance(authority, Mapping):
        raise ValueError("frozen task payload lacks repository authority")
    issues = authority.get("issue_numbers")
    allowed = authority.get("allowed_paths")
    protected = authority.get("protected_paths") or []
    if not isinstance(issues, list) or not isinstance(allowed, list) or not isinstance(
        protected, list
    ):
        raise ValueError("frozen repository authority has malformed list fields")
    return RepositoryTaskAuthority(
        task_id=authority.get("task_id"),
        authority_key=authority.get("authority_key"),
        issue_numbers=tuple(issues),
        lane=authority.get("lane"),
        objective=authority.get("objective"),
        stop_boundary=authority.get("stop_boundary"),
        allowed_paths=tuple(allowed),
        protected_paths=tuple(protected),
        context_text=str(authority.get("context_text") or ""),
        auto_merge_eligible=authority.get("auto_merge_eligible", False),
        spec_version=authority.get("spec_version", 2),
    )


def _pr_title(lane: str, objective: str) -> str:
    short = " ".join(str(objective or "").split())[:120] or "bounded task"
    return f"CODEX {str(lane).strip()}: {short}"


def _pr_body(*, admission, worker_spec) -> str:
    return (
        "Skyforge Platform v2 bounded worker handoff.\n\n"
        f"Task: {worker_spec.task_id}\n"
        f"Authority: {worker_spec.authority_key}\n"
        f"Task spec: {worker_spec.task_spec_hash}\n"
        f"Attempt: {worker_spec.attempt_id}\n"
        f"Base: {worker_spec.base_sha}\n"
        f"Stop boundary: {worker_spec.stop_boundary}\n"
        f"Admission: {admission.record_id}\n"
    )


class HostedExecutionCoordinator:
    """Advance at most one accepted durable lifecycle boundary per call."""

    def __init__(
        self,
        *,
        root: Path,
        repo: str,
        trusted_actors: Iterable[str],
        gate: HostedExecutionGateDecision,
    ) -> None:
        self.root = Path(root).resolve()
        self.repo = _required(repo, "repo")
        self.trusted_actors = tuple(str(value).strip().lower() for value in trusted_actors)
        self.gate = gate

    def _blocked(self, reason: str) -> HostedExecutionAdvanceResult:
        return HostedExecutionAdvanceResult(
            HostedExecutionAdvanceDisposition.BLOCKED,
            reason,
            self.gate.digest,
        )

    def _verify_gate(self, runner) -> HostedExecutionAdvanceResult | None:
        if not self.gate.enabled:
            return HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.GATE_BLOCKED,
                "; ".join(self.gate.blockers) or "hosted execution gate is blocked",
                self.gate.digest,
            )
        current = read_checkout_head(self.root, runner=runner)
        if current != self.gate.accepted_main_sha:
            return HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.GATE_BLOCKED,
                "hosted checkout HEAD moved after activation evidence was accepted",
                self.gate.digest,
                current,
            )
        return None

    def _claims(
        self,
        extra: tuple[ExternalProducerClaim, ...],
    ) -> tuple[ExternalProducerClaim, ...]:
        durable = ExternalClaimStore.for_root(self.root).load().claims
        by_issue = {claim.issue_number: claim for claim in durable}
        for claim in extra:
            by_issue.setdefault(claim.issue_number, claim)
        return tuple(by_issue[key] for key in sorted(by_issue))

    def _managed_handoff_for_attempt(
        self,
        attempt_id: str,
    ) -> ManagedOrdinaryHandoff | None:
        ledger = OrdinaryPipelineStore.for_root(self.root).load()
        values = [
            handoff
            for handoff in ledger.reconstructible_managed_handoffs()
            if handoff.scope.attempt_id == attempt_id
        ]
        if len(values) > 1:
            raise RuntimeError("multiple managed handoffs exist for one admitted attempt")
        return values[0] if values else None

    def _persist_handoff(
        self,
        *,
        admission,
        worker_run,
        handoff: ManagedOrdinaryHandoff,
    ) -> None:
        pipeline = OrdinaryPipelineStore.for_root(self.root)
        ledger = pipeline.load()
        record = OrdinaryPipelineRecord(
            pipeline_id=admission.record_id,
            classifier_request_id=admission.classifier_request_id,
            authority_digest=admission.authority_digest,
            stage=OrdinaryPipelineStage.COMPLETE,
            reason="hosted v2 bounded worker handoff reached exact managed draft PR",
            classifier_run_id=admission.classifier_run_id,
            classifier_decision_digest=admission.classifier_decision_digest,
            admission_digest=admission.admission_digest,
            task_spec_hash=admission.frozen_task.spec_hash,
            attempt_id=admission.attempt.attempt_id,
            worker_spec_digest=admission.worker_spec.digest,
            worker_run_id=worker_run.run_id,
            handoff_digest=handoff.digest,
            pr_number=handoff.pr_number,
            managed_handoff=handoff,
        )
        current = ledger.get(record.pipeline_id)
        if current is not None and current.digest != record.digest:
            raise RuntimeError("durable ordinary pipeline handoff identity drifted")
        if current is None:
            pipeline.save(ledger.put(record))

    def advance_once(
        self,
        deps: HostedExecutionDependencies,
    ) -> HostedExecutionAdvanceResult:
        gate_block = self._verify_gate(deps.runner)
        if gate_block is not None:
            return gate_block

        claims = self._claims(deps.external_claims)
        state = HostedStateStore.for_root(self.root).load()
        plan_store = HostedTaskPlanStore.for_root(self.root)
        plan_ledger = plan_store.load()
        plan = plan_ledger.active

        if plan is None:
            authority_events = TaskAuthorityEventStore.for_root(self.root).load()
            result = claim_next_protected_task(
                ledger=plan_ledger,
                inbox=state.inbox,
                authority_events=authority_events,
                external_claims=claims,
            )
            if result.ledger != plan_ledger:
                plan_store.save(result.ledger)
            if result.disposition is HostedTaskPlanDisposition.CLAIMED:
                return HostedExecutionAdvanceResult(
                    HostedExecutionAdvanceDisposition.TASK_CLAIMED,
                    result.reason,
                    self.gate.digest,
                    result.ledger.active.plan_id,
                )
            if result.disposition is HostedTaskPlanDisposition.NO_PROTECTED_TASK:
                return HostedExecutionAdvanceResult(
                    HostedExecutionAdvanceDisposition.IDLE,
                    result.reason,
                    self.gate.digest,
                )
            return self._blocked(result.reason)

        if plan.status in {
            HostedTaskPlanStatus.CLAIMED,
            HostedTaskPlanStatus.WAIT_REMOTE,
        }:
            authority_events = TaskAuthorityEventStore.for_root(self.root).load()
            result = advance_claimed_task_preflight(
                ledger=plan_ledger,
                authority_events=authority_events,
                trusted_actors=self.trusted_actors,
                repo=self.repo,
                runner=deps.runner,
            )
            if result.ledger != plan_ledger:
                plan_store.save(result.ledger)
            return HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.PREFLIGHT_ADVANCED,
                result.reason,
                self.gate.digest,
                result.ledger.active.plan_id if result.ledger.active else "",
            )

        if plan.status is HostedTaskPlanStatus.BLOCKED or plan.seed is None:
            return self._blocked(plan.reason)

        classifier_store = ClassifierRunStore.for_root(self.root)
        classifier = classifier_store.load().get(plan.seed.classifier_request.request_id)
        if classifier is None or classifier.status is not ClassifierRunStatus.COMPLETE:
            result = advance_hosted_classifier_proposal(
                plan_ledger=plan_ledger,
                root=self.root,
                provider=deps.classifier_provider,
                provider_quota=deps.classifier_provider_quota,
                local_budget=deps.classifier_local_budget,
                config=deps.classifier_config,
            )
            return HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.CLASSIFIER_ADVANCED,
                result.reason,
                self.gate.digest,
                result.run_id or plan.plan_id,
            )

        admission_store = HostedAdmissionStore.for_root(self.root)
        admission = admission_store.load().record
        if admission is None:
            result = advance_hosted_task_admission(
                root=self.root,
                plan_ledger=plan_ledger,
                trusted_actors=self.trusted_actors,
                repo=self.repo,
                active_external_claims=claims,
                provider_quota=deps.worker_provider_quota,
                local_budget=deps.worker_local_budget,
                attempt_number=deps.attempt_number,
                runner=deps.runner,
            )
            return HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.ADMISSION_ADVANCED,
                result.reason,
                self.gate.digest,
                result.ledger.record.record_id if result.ledger.record else plan.plan_id,
            )

        if admission.outcome is not HostedAdmissionOutcome.ADMITTED:
            return self._blocked(admission.reason)
        if admission.worker_spec is None or admission.attempt is None or admission.frozen_task is None:
            return self._blocked("admitted record lacks frozen worker identity")

        worker_store = WorkerRunStore.for_root(self.root)
        worker_run = worker_store.load().find_attempt(admission.attempt.attempt_id)
        if worker_run is None or worker_run.status is not WorkerRunStatus.HANDOFF_READY:
            result = advance_dormant_admitted_worker(
                root=self.root,
                provider=deps.worker_provider,
                provider_quota=deps.worker_provider_quota,
                local_budget=deps.worker_local_budget,
                config=deps.worker_config,
            )
            return HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.WORKER_ADVANCED,
                result.reason,
                self.gate.digest,
                result.worker_run_id or admission.record_id,
            )

        commit_store = DormantHandoffCommitStore.for_root(self.root)
        commit_record = commit_store.load().record
        if commit_record is None:
            result = advance_dormant_handoff_commit(root=self.root)
            identity = (
                result.ledger.record.record_id
                if result.ledger.record is not None
                else admission.record_id
            )
            return HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.LOCAL_COMMIT_ADVANCED,
                result.reason,
                self.gate.digest,
                identity,
            )
        if commit_record.outcome is not DormantCommitOutcome.COMMITTED:
            return self._blocked(commit_record.reason)

        handoff = self._managed_handoff_for_attempt(admission.attempt.attempt_id)
        if handoff is None:
            authority = _authority_from_frozen_payload(admission.frozen_task.payload())
            manager = WorkerWorkspaceManager(root=self.root)
            worktree = manager.expected_path(admission.worker_spec)
            scope = WorkspaceCommitScope(
                attempt_id=admission.worker_spec.attempt_id,
                branch=admission.worker_spec.branch,
                start_head=admission.worker_spec.base_sha,
                allowed_paths=admission.worker_spec.allowed_paths,
                protected_paths=admission.worker_spec.protected_paths,
            )
            workspace = WorkspaceCommitAdapter(worktree=worktree, scope=scope)
            prepared = PreparedWorkerTask(
                task_id=admission.worker_spec.task_id,
                authority_key=admission.worker_spec.authority_key,
                task_spec_hash=admission.worker_spec.task_spec_hash,
                attempt_id=admission.worker_spec.attempt_id,
                repo=self.repo,
                base_sha=admission.worker_spec.base_sha,
                branch=admission.worker_spec.branch,
                lane=admission.worker_spec.lane,
                objective=admission.worker_spec.objective,
                pr_title=_pr_title(
                    admission.worker_spec.lane,
                    admission.worker_spec.objective,
                ),
                pr_body=_pr_body(
                    admission=admission,
                    worker_spec=admission.worker_spec,
                ),
                issue_number=admission.issue_number,
                comment_body="",
                auto_merge_eligible=authority.auto_merge_eligible,
            )

            if deps.remote_factory is None:
                def remote_factory(binding: OrdinaryEffectBinding):
                    return GhGitOrdinaryEffectAdapter(
                        root=worktree,
                        binding=binding,
                        runner=deps.runner,
                    )
            else:
                def remote_factory(binding: OrdinaryEffectBinding):
                    return deps.remote_factory(worktree, binding)

            result = advance_prepared_handoff(
                task=prepared,
                workspace=workspace,
                store=OrdinaryEffectStore.for_root(self.root),
                remote_factory=remote_factory,
            )
            if result.disposition is OrdinaryServiceDisposition.COMPLETE and result.handoff:
                self._persist_handoff(
                    admission=admission,
                    worker_run=worker_run,
                    handoff=result.handoff,
                )
            return HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.REMOTE_HANDOFF_ADVANCED,
                result.reason,
                self.gate.digest,
                result.handoff.digest if result.handoff else admission.record_id,
            )

        result = advance_managed_pr_lifecycle(
            root=self.root,
            handoff=handoff,
            store=OrdinaryEffectStore.for_root(self.root),
            runner=deps.runner,
        )
        return HostedExecutionAdvanceResult(
            HostedExecutionAdvanceDisposition.MANAGED_PR_ADVANCED,
            result.reason,
            self.gate.digest,
            result.digest,
        )
