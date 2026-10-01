from __future__ import annotations

import hashlib
from pathlib import Path
import tempfile
import unittest

from test_platform_v2_scope_promotion import (
    candidate_bundle,
    prepare,
    runner_for,
)
from v2.classifier_provider import ClassifierDecision
from v2.context_package import ContextPackageStore
from v2.context_retrieval import ContextRetrievalStore
from v2.decision import DecisionFreshnessObservation, PendingDecisionRecord
from v2.dispatch_admission import RepositoryTaskAuthority, admit_dispatch
from v2.events import DurableEvent
from v2.hosted_admission import (
    HostedAdmissionLedger,
    HostedAdmissionOutcome,
    HostedAdmissionRecord,
    HostedAdmissionStore,
)
from v2.hosted_completion import (
    HostedCompletionLedger,
    HostedCompletionRecord,
    HostedCompletionStatus,
    HostedCompletionStore,
)
from v2.hosted_task_plan import (
    HostedTaskDispatchPlan,
    HostedTaskPlanLedger,
    HostedTaskPlanStatus,
    HostedTaskPlanStore,
)
from v2.objective_trace import build_objective_trace
from v2.ordinary_effects import (
    OrdinaryEffectLedger,
    OrdinaryEffectStore,
    OrdinaryMutationScope,
)
from v2.quota import LocalBudgetObservation
from v2.ordinary_remote import comment_payload
from v2.scope_promotion import PromotionStore, validate_promotion
from v2.task_authority import TaskAuthorityWakeReference
from v2.task_event_composition import (
    TaskAuthorityEventLedger,
    TaskAuthorityEventRecord,
    TaskAuthorityEventStore,
)
from v2.worker_provider import (
    WorkerProviderConfig,
    WorkerRunLedger,
    WorkerRunRecord,
    WorkerRunStatus,
    WorkerRunStore,
)


def digest(label: str) -> str:
    return hashlib.sha256(label.encode()).hexdigest()


def install_trace(root: Path, *, comment_id: int = 88001):
    proposal, package, retrieval, payload = candidate_bundle(root)
    ContextPackageStore.for_root(root).capture(package)
    ContextRetrievalStore.for_root(root).capture(retrieval)
    promotion = validate_promotion(
        root=root,
        repo="ni-da-ba/skyforge",
        package=package,
        retrieval=retrieval,
        gh_runner=runner_for(payload),
    )
    PromotionStore.for_root(root).capture(promotion)
    draft = promotion.draft
    assert draft is not None

    promotion_attempt = f"objective-promotion:{promotion.digest}"
    promotion_scope = OrdinaryMutationScope(
        attempt_id=promotion_attempt,
        repo="ni-da-ba/skyforge",
        base_sha=package.accepted_main_sha,
        branch=f"objective-promotion/{promotion.digest[:16]}",
        expected_head_sha=package.accepted_main_sha,
        pr_title="trace promotion",
        pr_body="trace promotion",
        issue_number=draft.issue_number,
    )
    comment_identity = promotion_scope.comment_identity(draft.body)
    effects = OrdinaryEffectLedger().begin(comment_identity).complete(
        comment_identity,
        f"comment:{comment_id}",
    )
    OrdinaryEffectStore.for_root(root).save(effects)

    created_at = "2026-09-21T03:20:00Z"
    event = DurableEvent(
        actionable=True,
        reason="trace promoted task",
        event="issue_comment",
        action="audit_signal",
        pr_number=draft.issue_number,
        observed_at=created_at,
        source_id=str(comment_id),
        signal_kind="task",
        signal_text=draft.body,
    )
    reference = TaskAuthorityWakeReference(
        repo="ni-da-ba/skyforge",
        issue_number=draft.issue_number,
        comment_id=comment_id,
        actor="ni-da-ba",
        body=comment_payload(comment_identity, draft.body),
        created_at=created_at,
        updated_at=created_at,
    )
    task_event = TaskAuthorityEventRecord(event.event_id, reference, "trace-delivery")
    TaskAuthorityEventStore.for_root(root).save(
        TaskAuthorityEventLedger((task_event,))
    )

    plan = HostedTaskDispatchPlan(
        event_id=task_event.event_id,
        issue_number=draft.issue_number,
        authority_record_digest=task_event.digest,
        status=HostedTaskPlanStatus.CLAIMED,
        reason="trace claimed",
    )
    HostedTaskPlanStore.for_root(root).save(HostedTaskPlanLedger((plan,)))

    authority = RepositoryTaskAuthority(
        task_id=f"trace-{draft.issue_number}",
        authority_key=f"trace:{draft.issue_number}:{task_event.event_id}",
        issue_numbers=(draft.issue_number,),
        lane=draft.lane,
        objective=draft.objective,
        stop_boundary=draft.stop_boundary,
        allowed_paths=draft.allowed_paths,
        protected_paths=draft.protected_paths,
        context_text="private context must not leak",
        auto_merge_eligible=draft.auto_merge_eligible,
    )
    decision = ClassifierDecision.from_legacy_mapping(
        {
            "decision": "DISPATCH",
            "lane": authority.lane,
            "objective": authority.objective,
            "stop_boundary": authority.stop_boundary,
            "worker_tier": "TERRA",
            "allowed_paths": list(authority.allowed_paths),
            "reason": "trace dispatch",
        }
    )
    pending = PendingDecisionRecord.from_legacy_mapping(
        {
            "decision": decision.as_dict(),
            "event_keys": [task_event.event_id],
            "authority_event_keys": [task_event.event_id],
            "ordinary_event_keys": [],
            "task_issue_numbers": [draft.issue_number],
            "snapshot_main": package.accepted_main_sha,
        }
    )
    dispatch = admit_dispatch(
        authority=authority,
        record=pending,
        freshness=DecisionFreshnessObservation(
            current_main=package.accepted_main_sha
        ),
        current_main=package.accepted_main_sha,
        active_external_claims=(),
        provider_quota=None,
        local_budget=LocalBudgetObservation(0, 10),
        attempt_number=1,
    )
    admission = HostedAdmissionRecord(
        plan_id=plan.plan_id,
        event_id=plan.event_id,
        issue_number=plan.issue_number,
        classifier_request_id=digest("trace-classifier-request"),
        classifier_run_id=digest("trace-classifier-run"),
        classifier_decision_digest=digest("trace-classifier-decision"),
        hydration_digest=digest("trace-hydration"),
        authority_digest=dispatch.authority_digest,
        current_main=package.accepted_main_sha,
        attempt_number=1,
        outcome=HostedAdmissionOutcome.ADMITTED,
        reason=dispatch.reason,
        pending_decision_digest=dispatch.pending_decision_digest,
        quota_digest=dispatch.quota.digest,
        consume_attempt=dispatch.consume_attempt,
        admission_digest=dispatch.digest,
        frozen_task=dispatch.frozen_task,
        attempt=dispatch.attempt,
        worker_spec=dispatch.worker_spec,
    )
    HostedAdmissionStore.for_root(root).save(HostedAdmissionLedger((admission,)))

    spec = admission.worker_spec
    assert spec is not None and admission.attempt is not None
    worker = WorkerRunRecord(
        spec=spec,
        worktree=str(root / ".trace-worker"),
        config=WorkerProviderConfig(spec.tier, "trace-model", "low"),
        status=WorkerRunStatus.HANDOFF_READY,
        summary="trace worker complete",
    )
    WorkerRunStore.for_root(root).save(WorkerRunLedger((worker,)))

    worker_scope = OrdinaryMutationScope(
        attempt_id=spec.attempt_id,
        repo="ni-da-ba/skyforge",
        base_sha=spec.base_sha,
        branch=spec.branch,
        expected_head_sha="b" * 40,
        pr_title="trace worker",
        pr_body="trace worker",
    )
    worker_effect = worker_scope.push_identity()
    OrdinaryEffectStore.for_root(root).save(
        OrdinaryEffectStore.for_root(root)
        .load()
        .begin(worker_effect)
        .complete(worker_effect, f"branch:{spec.branch}@{'b' * 40}")
    )

    completion = HostedCompletionRecord(
        plan_id=plan.plan_id,
        event_id=plan.event_id,
        issue_number=plan.issue_number,
        admission_record_id=admission.record_id,
        attempt_id=spec.attempt_id,
        worker_run_id=worker.run_id,
        handoff_digest=digest("trace-handoff"),
        lifecycle_digest=digest("trace-lifecycle"),
        status=HostedCompletionStatus.CLEANED,
    )
    return proposal, package, retrieval, promotion, task_event, plan, admission, worker, completion


class ObjectiveTraceTest(unittest.TestCase):
    def test_active_chain_uses_exact_persisted_identities(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare(root)
            values = install_trace(root)
            proposal, _package, _retrieval, _promotion, task_event, plan, admission, worker, _completion = values

            trace = build_objective_trace(
                root=root,
                correlation_id=proposal.proposal_id,
            )
            self.assertIsNotNone(trace)
            stages = {stage["stage"]: stage for stage in trace["stages"]}
            self.assertEqual(trace["correlation_id"], proposal.proposal_id)
            self.assertEqual(
                stages["TASK_AUTHORITY"]["identities"]["event_id"],
                task_event.event_id,
            )
            self.assertEqual(
                stages["TASK_PLAN"]["identities"]["plan_id"],
                plan.plan_id,
            )
            self.assertEqual(
                stages["ADMISSION"]["identities"]["attempt_id"],
                admission.attempt.attempt_id,
            )
            self.assertEqual(
                stages["WORKER"]["identities"]["worker_run_id"],
                worker.run_id,
            )
            self.assertEqual(trace["terminal_stage"], "REPOSITORY_EFFECTS")
            self.assertNotIn("private context must not leak", str(trace))
            self.assertNotIn(str(root / ".trace-worker"), str(trace))

    def test_cleaned_completion_remains_traceable_after_active_state_retirement(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare(root)
            values = install_trace(root)
            proposal, _package, _retrieval, _promotion, _task_event, plan, admission, worker, completion = values

            HostedCompletionStore.for_root(root).save(
                HostedCompletionLedger((completion,))
            )
            HostedTaskPlanStore.for_root(root).save(HostedTaskPlanLedger())
            HostedAdmissionStore.for_root(root).save(HostedAdmissionLedger())

            trace = build_objective_trace(
                root=root,
                correlation_id=proposal.proposal_id,
            )
            stages = {stage["stage"]: stage for stage in trace["stages"]}
            self.assertEqual(stages["TASK_PLAN"]["status"], "RETIRED")
            self.assertEqual(stages["ADMISSION"]["status"], "RETIRED")
            self.assertEqual(
                stages["WORKER"]["identities"]["worker_run_id"],
                worker.run_id,
            )
            self.assertEqual(
                stages["COMPLETION"]["identities"]["completion_id"],
                completion.completion_id,
            )
            self.assertEqual(trace["terminal_stage"], "COMPLETION")
            self.assertEqual(trace["terminal_status"], "CLEANED")

    def test_concurrent_objectives_do_not_cross_link(self):
        with tempfile.TemporaryDirectory() as td:
            root_a = Path(td) / "a"
            root_b = Path(td) / "b"
            root_a.mkdir()
            root_b.mkdir()
            prepare(root_a)
            prepare(root_b)
            a = install_trace(root_a, comment_id=88001)
            b = install_trace(root_b, comment_id=88002)
            trace_a = build_objective_trace(
                root=root_a,
                correlation_id=a[0].proposal_id,
            )
            trace_b = build_objective_trace(
                root=root_b,
                correlation_id=b[0].proposal_id,
            )
            self.assertNotEqual(
                trace_a["stages"][4]["identities"].get("remote_identity"),
                trace_b["stages"][4]["identities"].get("remote_identity"),
            )
            self.assertNotEqual(
                {stage["identities"].get("event_id") for stage in trace_a["stages"]},
                {stage["identities"].get("event_id") for stage in trace_b["stages"]},
            )

    def test_restart_reread_preserves_exact_trace_digest(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare(root)
            values = install_trace(root)
            proposal, *_rest, completion = values
            HostedCompletionStore.for_root(root).save(
                HostedCompletionLedger((completion,))
            )
            first = build_objective_trace(
                root=root,
                correlation_id=proposal.proposal_id,
            )
            second = build_objective_trace(
                root=Path(str(root)),
                correlation_id=proposal.proposal_id,
            )
            self.assertEqual(first, second)
            self.assertEqual(first["trace_digest"], second["trace_digest"])

    def test_stale_completion_cannot_attach_to_different_worker_attempt(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare(root)
            values = install_trace(root)
            proposal, _package, _retrieval, _promotion, _task_event, _plan, _admission, worker, completion = values
            stale = HostedCompletionRecord(
                plan_id=completion.plan_id,
                event_id=completion.event_id,
                issue_number=completion.issue_number,
                admission_record_id=completion.admission_record_id,
                attempt_id=completion.attempt_id,
                worker_run_id=digest("different-worker-run"),
                handoff_digest=completion.handoff_digest,
                lifecycle_digest=completion.lifecycle_digest,
                status=HostedCompletionStatus.CLEANED,
            )
            HostedCompletionStore.for_root(root).save(
                HostedCompletionLedger((stale,))
            )
            HostedTaskPlanStore.for_root(root).save(HostedTaskPlanLedger())
            HostedAdmissionStore.for_root(root).save(HostedAdmissionLedger())
            with self.assertRaisesRegex(ValueError, "worker/completion correlation mismatch"):
                build_objective_trace(
                    root=root,
                    correlation_id=proposal.proposal_id,
                )
            self.assertNotEqual(worker.run_id, stale.worker_run_id)

    def test_unknown_or_invalid_correlation_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare(root)
            self.assertIsNone(
                build_objective_trace(root=root, correlation_id="0" * 64)
            )
            with self.assertRaisesRegex(ValueError, "correlation_id"):
                build_objective_trace(root=root, correlation_id="not-a-digest")


if __name__ == "__main__":
    unittest.main()
