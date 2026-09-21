"""Read-only end-to-end objective correlation projection for Platform v2 X-2.

The objective proposal id is the root correlation identity. This module never writes
authority or state; it joins only exact persisted child identities. Ambiguous joins fail
closed instead of guessing from issue titles, task text, timestamps, or branch names.
"""

from __future__ import annotations

from pathlib import Path
import re
from typing import Any

from .context_package import ContextPackageStore
from .context_retrieval import ContextRetrievalStore
from .effects import EffectKind
from .hosted_admission import HostedAdmissionStore
from .hosted_completion import HostedCompletionStore
from .hosted_task_plan import HostedTaskPlanStore
from .identity import canonical_digest
from .objective_ingress import ObjectiveProposalStore
from .ordinary_effects import OrdinaryEffectStore
from .ordinary_pipeline import OrdinaryPipelineStore
from .scope_promotion import PromotionStore
from .task_event_composition import TaskAuthorityEventStore
from .worker_provider import WorkerRunStore

_CORRELATION_RE = re.compile(r"^[0-9a-f]{64}$")
_COMMENT_REMOTE_RE = re.compile(r"^comment:(?P<comment_id>[1-9][0-9]*)$")


def _exact_one(values, label: str):
    items = tuple(values)
    if len(items) > 1:
        raise ValueError(f"ambiguous {label} correlation")
    return items[0] if items else None


def _stage(name: str, status: str, **identities: Any) -> dict[str, Any]:
    return {
        "stage": name,
        "status": str(status),
        "identities": {
            key: value
            for key, value in identities.items()
            if value not in (None, "", (), [])
        },
    }


def _proposal_source_identity(proposal) -> dict[str, Any]:
    source = proposal.source
    value: dict[str, Any] = {"kind": source.as_dict().get("kind", "GITHUB")}
    for key in ("request_id", "issue_number", "comment_id"):
        item = getattr(source, key, None)
        if item not in (None, ""):
            value[key] = item
    return value


def build_objective_trace(*, root: Path, correlation_id: str) -> dict[str, Any] | None:
    root = Path(root).resolve()
    correlation_id = str(correlation_id or "").strip().lower()
    if not _CORRELATION_RE.fullmatch(correlation_id):
        raise ValueError("objective correlation_id must be lowercase SHA-256 hex")

    proposals = ObjectiveProposalStore.for_root(root).load()
    proposal = _exact_one(
        (value for value in proposals.records if value.proposal_id == correlation_id),
        "objective proposal",
    )
    if proposal is None:
        return None

    stages: list[dict[str, Any]] = [
        _stage(
            "OBJECTIVE",
            proposal.compiled.disposition.value,
            proposal_id=proposal.proposal_id,
            source=_proposal_source_identity(proposal),
        )
    ]

    package = ContextPackageStore.for_root(root).load().get(proposal.proposal_id)
    if package is None:
        return _trace_payload(proposal.proposal_id, stages)
    stages.append(
        _stage(
            "CONTEXT_PACKAGE",
            package.disposition.value,
            package_id=package.package_id,
            accepted_main_sha=package.accepted_main_sha,
            lane=package.lane,
            issue_number=package.issue_number,
            node_id=package.node_id,
        )
    )

    retrieval = ContextRetrievalStore.for_root(root).load().get(package.package_id)
    if retrieval is None:
        return _trace_payload(proposal.proposal_id, stages)
    if retrieval.proposal_id != proposal.proposal_id:
        raise ValueError("context retrieval proposal correlation mismatch")
    stages.append(
        _stage(
            "CONTEXT_RETRIEVAL",
            retrieval.disposition.value,
            retrieval_id=retrieval.retrieval_id,
            package_id=retrieval.package_id,
        )
    )

    promotion = PromotionStore.for_root(root).load().get(package.package_id)
    if promotion is None:
        return _trace_payload(proposal.proposal_id, stages)
    result = promotion.result
    if result.retrieval_id != retrieval.retrieval_id:
        raise ValueError("objective promotion retrieval correlation mismatch")
    draft = result.draft
    stages.append(
        _stage(
            "TASK_PROMOTION",
            result.disposition.value,
            promotion_id=promotion.promotion_id,
            package_id=result.package_id,
            retrieval_id=result.retrieval_id,
            issue_number=(draft.issue_number if draft is not None else None),
            draft_digest=(draft.digest if draft is not None else ""),
        )
    )
    if draft is None:
        return _trace_payload(proposal.proposal_id, stages)

    promotion_attempt = f"objective-promotion:{promotion.promotion_id}"
    promotion_effect = _exact_one(
        (
            value
            for value in OrdinaryEffectStore.for_root(root).load().records
            if value.identity.attempt_id == promotion_attempt
            and value.identity.kind is EffectKind.POST_COMMENT
        ),
        "objective promotion comment effect",
    )
    if promotion_effect is None:
        return _trace_payload(proposal.proposal_id, stages)
    stages.append(
        _stage(
            "PROMOTION_EFFECT",
            promotion_effect.status.value,
            effect_id=promotion_effect.identity.effect_id,
            remote_identity=promotion_effect.remote_identity,
            issue_number=draft.issue_number,
        )
    )

    remote_match = _COMMENT_REMOTE_RE.fullmatch(promotion_effect.remote_identity)
    if remote_match is None:
        return _trace_payload(proposal.proposal_id, stages)
    comment_id = int(remote_match.group("comment_id"))

    task_event = _exact_one(
        (
            value
            for value in TaskAuthorityEventStore.for_root(root).load().records
            if value.reference.comment_id == comment_id
        ),
        "task authority event",
    )
    if task_event is None:
        return _trace_payload(proposal.proposal_id, stages)
    if (
        task_event.reference.issue_number != draft.issue_number
        or task_event.reference.body != draft.body
    ):
        raise ValueError("captured task authority differs from exact objective promotion")
    stages.append(
        _stage(
            "TASK_AUTHORITY",
            "CAPTURED",
            event_id=task_event.event_id,
            issue_number=task_event.reference.issue_number,
            comment_id=task_event.reference.comment_id,
            authority_digest=task_event.authority_digest,
        )
    )

    plans = HostedTaskPlanStore.for_root(root).load()
    plan = plans.get_event(task_event.event_id)
    completions = HostedCompletionStore.for_root(root).load()
    completion = _exact_one(
        (
            value
            for value in completions.records
            if value.event_id == task_event.event_id
        ),
        "hosted completion",
    )

    if plan is not None:
        stages.append(
            _stage(
                "TASK_PLAN",
                plan.status.value,
                plan_id=plan.plan_id,
                event_id=plan.event_id,
                issue_number=plan.issue_number,
            )
        )
    elif completion is not None:
        stages.append(
            _stage(
                "TASK_PLAN",
                "RETIRED",
                plan_id=completion.plan_id,
                event_id=completion.event_id,
                issue_number=completion.issue_number,
            )
        )

    admission = (
        HostedAdmissionStore.for_root(root).load().for_plan(plan.plan_id)
        if plan is not None
        else None
    )
    attempt_id = ""
    worker_run_id = ""
    if admission is not None:
        if admission.event_id != task_event.event_id:
            raise ValueError("hosted admission event correlation mismatch")
        attempt_id = admission.attempt.attempt_id if admission.attempt is not None else ""
        stages.append(
            _stage(
                "ADMISSION",
                admission.outcome.value,
                admission_record_id=admission.record_id,
                plan_id=admission.plan_id,
                attempt_id=attempt_id,
            )
        )
    elif completion is not None:
        attempt_id = completion.attempt_id
        worker_run_id = completion.worker_run_id
        stages.append(
            _stage(
                "ADMISSION",
                "RETIRED",
                admission_record_id=completion.admission_record_id,
                plan_id=completion.plan_id,
                attempt_id=completion.attempt_id,
            )
        )

    if completion is not None:
        if attempt_id and completion.attempt_id != attempt_id:
            raise ValueError("completion attempt correlation mismatch")
        attempt_id = completion.attempt_id
        worker_run_id = completion.worker_run_id

    worker = (
        WorkerRunStore.for_root(root).load().find_attempt(attempt_id)
        if attempt_id
        else None
    )
    if worker is not None:
        if worker_run_id and worker.run_id != worker_run_id:
            raise ValueError("worker/completion correlation mismatch")
        worker_run_id = worker.run_id
        stages.append(
            _stage(
                "WORKER",
                worker.status.value,
                attempt_id=worker.spec.attempt_id,
                worker_run_id=worker.run_id,
                task_id=worker.spec.task_id,
                lane=worker.spec.lane,
                branch=worker.spec.branch,
                tier=worker.spec.tier.value,
            )
        )

    pipeline = (
        _exact_one(
            (
                value
                for value in OrdinaryPipelineStore.for_root(root).load().records
                if value.attempt_id == attempt_id
            ),
            "ordinary pipeline",
        )
        if attempt_id
        else None
    )
    if pipeline is not None:
        if worker_run_id and pipeline.worker_run_id and pipeline.worker_run_id != worker_run_id:
            raise ValueError("pipeline worker correlation mismatch")
        stages.append(
            _stage(
                "REPOSITORY_HANDOFF",
                pipeline.stage.value,
                pipeline_id=pipeline.pipeline_id,
                attempt_id=pipeline.attempt_id,
                worker_run_id=pipeline.worker_run_id,
                handoff_digest=pipeline.handoff_digest,
                pr_number=pipeline.pr_number,
            )
        )

    if attempt_id:
        effects = tuple(
            value
            for value in OrdinaryEffectStore.for_root(root).load().records
            if value.identity.attempt_id == attempt_id
        )
        if effects:
            stages.append(
                {
                    "stage": "REPOSITORY_EFFECTS",
                    "status": (
                        "COMPLETE"
                        if all(value.status.value == "COMPLETE" for value in effects)
                        else "PENDING"
                    ),
                    "identities": {
                        "attempt_id": attempt_id,
                        "effects": [
                            {
                                "effect_id": value.identity.effect_id,
                                "kind": value.identity.kind.value,
                                "status": value.status.value,
                                "remote_identity": value.remote_identity,
                            }
                            for value in sorted(
                                effects,
                                key=lambda value: value.identity.effect_id,
                            )
                        ],
                    },
                }
            )

    if completion is not None:
        stages.append(
            _stage(
                "COMPLETION",
                completion.status.value,
                completion_id=completion.completion_id,
                plan_id=completion.plan_id,
                attempt_id=completion.attempt_id,
                worker_run_id=completion.worker_run_id,
                handoff_digest=completion.handoff_digest,
            )
        )

    return _trace_payload(proposal.proposal_id, stages)


def _trace_payload(correlation_id: str, stages: list[dict[str, Any]]) -> dict[str, Any]:
    terminal = stages[-1]["status"] if stages else "UNKNOWN"
    payload = {
        "schema_version": 1,
        "correlation_id": correlation_id,
        "terminal_stage": stages[-1]["stage"] if stages else "",
        "terminal_status": terminal,
        "stages": stages,
    }
    return {**payload, "trace_digest": canonical_digest(payload)}
