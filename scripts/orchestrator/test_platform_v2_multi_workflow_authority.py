from __future__ import annotations

import hashlib
from pathlib import Path
import tempfile
import unittest

from v2.concurrency_claims import (
    ConcurrencyClaimDisposition,
    ConcurrencyClaimLedger,
    ConcurrencyClaimStore,
    acquire_concurrency_claim,
)
from v2.decision import ClassifierDecision, DecisionFreshnessObservation, PendingDecisionRecord
from v2.dispatch_admission import RepositoryTaskAuthority, admit_dispatch
from v2.dormant_handoff_commit import (
    DormantCommitOutcome,
    DormantHandoffCommitLedger,
    DormantHandoffCommitRecord,
    DormantHandoffCommitStore,
)
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
    advance_hosted_completion_cleanup,
)
from v2.hosted_state import HostedIngressState, HostedStateStore
from v2.hosted_task_plan import (
    HostedTaskDispatchPlan,
    HostedTaskPlanLedger,
    HostedTaskPlanStatus,
    HostedTaskPlanStore,
)
from v2.identity import canonical_digest
from v2.inbox import InboxState
from v2.quota import LocalBudgetObservation


BASE = "a" * 40
HEAD = "b" * 40


def digest(label: str) -> str:
    return hashlib.sha256(label.encode("utf-8")).hexdigest()


def event(name: str, issue: int) -> DurableEvent:
    return DurableEvent(
        actionable=True,
        reason=f"multi workflow {name}",
        event="issue_comment",
        action="created",
        pr_number=issue,
        source_id=f"multi:{name}",
        signal_kind="task",
        signal_text=f"task-{name}",
    )


def plan(name: str, issue: int) -> tuple[DurableEvent, HostedTaskDispatchPlan]:
    value = event(name, issue)
    return value, HostedTaskDispatchPlan(
        event_id=value.event_id,
        issue_number=issue,
        authority_record_digest=digest(f"authority-record:{name}"),
        status=HostedTaskPlanStatus.CLAIMED,
        reason=f"claimed {name}",
    )


def admitted(
    name: str,
    plan_record: HostedTaskDispatchPlan,
    allowed_path: str,
) -> HostedAdmissionRecord:
    authority = RepositoryTaskAuthority(
        task_id=f"task-{name}",
        authority_key=f"issue:{plan_record.issue_number}:{name}",
        issue_numbers=(plan_record.issue_number,),
        lane="Implementation",
        objective=f"implement {name}",
        stop_boundary=f"stop after {name}",
        allowed_paths=(allowed_path,),
        protected_paths=("scripts/orchestrator/**",),
        context_text=f"bounded {name}",
        auto_merge_eligible=False,
    )
    decision = ClassifierDecision.from_legacy_mapping(
        {
            "decision": "DISPATCH",
            "lane": authority.lane,
            "objective": authority.objective,
            "stop_boundary": authority.stop_boundary,
            "worker_tier": "TERRA",
            "allowed_paths": [allowed_path],
            "reason": f"dispatch {name}",
        }
    )
    pending = PendingDecisionRecord.from_legacy_mapping(
        {
            "decision": decision.as_dict(),
            "event_keys": [plan_record.event_id],
            "authority_event_keys": [plan_record.event_id],
            "ordinary_event_keys": [],
            "task_issue_numbers": [plan_record.issue_number],
            "snapshot_main": BASE,
        }
    )
    result = admit_dispatch(
        authority=authority,
        record=pending,
        freshness=DecisionFreshnessObservation(current_main=BASE),
        current_main=BASE,
        active_external_claims=(),
        provider_quota=None,
        local_budget=LocalBudgetObservation(calls_used=0, daily_limit=10),
        attempt_number=1,
    )
    assert result.disposition.value == "ADMIT"
    return HostedAdmissionRecord(
        plan_id=plan_record.plan_id,
        event_id=plan_record.event_id,
        issue_number=plan_record.issue_number,
        classifier_request_id=digest(f"classifier-request:{name}"),
        classifier_run_id=digest(f"classifier-run:{name}"),
        classifier_decision_digest=canonical_digest(decision.as_dict()),
        hydration_digest=digest(f"hydration:{name}"),
        authority_digest=result.authority_digest,
        current_main=BASE,
        attempt_number=1,
        outcome=HostedAdmissionOutcome.ADMITTED,
        reason=result.reason,
        pending_decision_digest=result.pending_decision_digest,
        quota_digest=result.quota.digest,
        consume_attempt=result.consume_attempt,
        admission_digest=result.digest,
        frozen_task=result.frozen_task,
        attempt=result.attempt,
        worker_spec=result.worker_spec,
    )


def commit(name: str, admission: HostedAdmissionRecord) -> DormantHandoffCommitRecord:
    assert admission.worker_spec is not None
    assert admission.attempt is not None
    scope = admission.worker_spec.allowed_paths[0]
    prefix = scope[:-3].rstrip("/") if scope.endswith("/**") else str(Path(scope).parent)
    changed = f"{prefix}/result-{name}.txt"
    return DormantHandoffCommitRecord(
        admission_record_id=admission.record_id,
        worker_run_id=digest(f"worker-run:{name}"),
        attempt_id=admission.attempt.attempt_id,
        branch=admission.worker_spec.branch,
        base_sha=BASE,
        outcome=DormantCommitOutcome.COMMITTED,
        reason=f"committed {name}",
        head_sha=HEAD,
        changed_paths=(changed,),
    )


class MultiWorkflowSchemaTest(unittest.TestCase):
    def test_schema_v1_singletons_migrate_exactly_to_v2_records(self):
        _event_a, plan_a = plan("a", 1001)
        admission_a = admitted("a", plan_a, "docs/a/**")
        commit_a = commit("a", admission_a)

        plans = HostedTaskPlanLedger.from_mapping(
            {"schema_version": 1, "active": plan_a.as_dict()}
        )
        admissions = HostedAdmissionLedger.from_mapping(
            {"schema_version": 1, "record": admission_a.as_dict()}
        )
        commits = DormantHandoffCommitLedger.from_mapping(
            {"schema_version": 1, "record": commit_a.as_dict()}
        )

        self.assertEqual(plans.active, plan_a)
        self.assertEqual(admissions.record, admission_a)
        self.assertEqual(commits.record, commit_a)
        self.assertEqual(plans.as_dict()["schema_version"], 2)
        self.assertEqual(admissions.as_dict()["schema_version"], 2)
        self.assertEqual(commits.as_dict()["schema_version"], 2)
        self.assertEqual(HostedTaskPlanLedger.from_mapping(plans.as_dict()), plans)
        self.assertEqual(HostedAdmissionLedger.from_mapping(admissions.as_dict()), admissions)
        self.assertEqual(DormantHandoffCommitLedger.from_mapping(commits.as_dict()), commits)

    def test_same_issue_cannot_become_two_live_plans(self):
        _first_event, first = plan("first", 1001)
        _second_event, second = plan("second", 1001)
        with self.assertRaisesRegex(ValueError, "share one issue"):
            HostedTaskPlanLedger((first, second))


class MultiWorkflowAuthorityTest(unittest.TestCase):
    def fixture(self):
        event_a, plan_a = plan("a", 1001)
        event_b, plan_b = plan("b", 1002)
        admission_a = admitted("a", plan_a, "docs/a/**")
        admission_b = admitted("b", plan_b, "docs/b/**")
        commit_a = commit("a", admission_a)
        commit_b = commit("b", admission_b)
        return (
            event_a,
            event_b,
            plan_a,
            plan_b,
            admission_a,
            admission_b,
            commit_a,
            commit_b,
        )

    def test_two_independent_workflows_coexist_and_restart_exactly(self):
        (
            _event_a,
            _event_b,
            plan_a,
            plan_b,
            admission_a,
            admission_b,
            commit_a,
            commit_b,
        ) = self.fixture()
        plans = HostedTaskPlanLedger().put(plan_a).put(plan_b)
        admissions = HostedAdmissionLedger().put(admission_a).put(admission_b)
        commits = DormantHandoffCommitLedger().put(commit_a).put(commit_b)

        claims = ConcurrencyClaimLedger()
        first = acquire_concurrency_claim(claims, admission_a.worker_spec)
        second = acquire_concurrency_claim(first.ledger, admission_b.worker_spec)
        self.assertEqual(first.decision.disposition, ConcurrencyClaimDisposition.ADMIT)
        self.assertEqual(second.decision.disposition, ConcurrencyClaimDisposition.ADMIT)
        self.assertEqual(len(second.ledger.active), 2)

        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            HostedTaskPlanStore.for_root(root).save(plans)
            HostedAdmissionStore.for_root(root).save(admissions)
            DormantHandoffCommitStore.for_root(root).save(commits)
            ConcurrencyClaimStore.for_root(root).save(second.ledger)

            self.assertEqual(HostedTaskPlanStore.for_root(root).load(), plans)
            self.assertEqual(HostedAdmissionStore.for_root(root).load(), admissions)
            self.assertEqual(DormantHandoffCommitStore.for_root(root).load(), commits)
            self.assertEqual(ConcurrencyClaimStore.for_root(root).load(), second.ledger)

    def test_overlapping_second_admission_is_serialized_with_exact_conflict(self):
        _event_a, plan_a = plan("a", 1001)
        _event_b, plan_b = plan("b", 1002)
        admission_a = admitted("a", plan_a, "docs/shared/**")
        admission_b = admitted("b", plan_b, "docs/shared/file.txt")
        admissions = HostedAdmissionLedger().put(admission_a).put(admission_b)
        self.assertEqual(len(admissions.records), 2)

        first = acquire_concurrency_claim(
            ConcurrencyClaimLedger(), admission_a.worker_spec
        )
        blocked = acquire_concurrency_claim(first.ledger, admission_b.worker_spec)
        self.assertEqual(blocked.decision.disposition, ConcurrencyClaimDisposition.CONFLICT)
        self.assertEqual(len(blocked.decision.conflicts), 1)
        self.assertEqual(
            blocked.decision.conflicts[0].attempt_id,
            admission_a.attempt.attempt_id,
        )
        self.assertEqual(len(blocked.ledger.active), 1)

    def test_cleanup_retires_only_exact_completed_workflow(self):
        (
            event_a,
            event_b,
            plan_a,
            plan_b,
            admission_a,
            admission_b,
            commit_a,
            commit_b,
        ) = self.fixture()
        assert admission_a.attempt is not None
        assert admission_b.attempt is not None

        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            HostedTaskPlanStore.for_root(root).save(
                HostedTaskPlanLedger((plan_a, plan_b))
            )
            HostedAdmissionStore.for_root(root).save(
                HostedAdmissionLedger((admission_a, admission_b))
            )
            DormantHandoffCommitStore.for_root(root).save(
                DormantHandoffCommitLedger((commit_a, commit_b))
            )
            claims = acquire_concurrency_claim(
                ConcurrencyClaimLedger(), admission_a.worker_spec
            ).ledger
            claims = acquire_concurrency_claim(claims, admission_b.worker_spec).ledger
            ConcurrencyClaimStore.for_root(root).save(claims)
            HostedStateStore.for_root(root).save(
                HostedIngressState(
                    inbox=InboxState(pending_events=(event_a, event_b))
                )
            )

            completion = HostedCompletionRecord(
                plan_id=plan_a.plan_id,
                event_id=plan_a.event_id,
                issue_number=plan_a.issue_number,
                admission_record_id=admission_a.record_id,
                attempt_id=admission_a.attempt.attempt_id,
                worker_run_id=commit_a.worker_run_id,
                handoff_digest=digest("handoff:a"),
                lifecycle_digest=digest("lifecycle:a"),
                status=HostedCompletionStatus.RECORDED,
            )
            HostedCompletionStore.for_root(root).save(
                HostedCompletionLedger((completion,))
            )

            cleaned = advance_hosted_completion_cleanup(root=root)
            self.assertEqual(cleaned.record.completion_id, completion.completion_id)

            plans = HostedTaskPlanStore.for_root(root).load()
            admissions = HostedAdmissionStore.for_root(root).load()
            commits = DormantHandoffCommitStore.for_root(root).load()
            claim_ledger = ConcurrencyClaimStore.for_root(root).load()
            state = HostedStateStore.for_root(root).load()

            self.assertIsNone(plans.get(plan_a.plan_id))
            self.assertEqual(plans.get(plan_b.plan_id), plan_b)
            self.assertIsNone(admissions.for_plan(plan_a.plan_id))
            self.assertEqual(admissions.for_plan(plan_b.plan_id), admission_b)
            self.assertIsNone(commits.for_attempt(admission_a.attempt.attempt_id))
            self.assertEqual(
                commits.for_attempt(admission_b.attempt.attempt_id),
                commit_b,
            )
            self.assertIsNone(
                claim_ledger.active_for_attempt(admission_a.attempt.attempt_id)
            )
            self.assertIsNotNone(
                claim_ledger.retired_for_attempt(admission_a.attempt.attempt_id)
            )
            self.assertIsNotNone(
                claim_ledger.active_for_attempt(admission_b.attempt.attempt_id)
            )
            self.assertEqual(
                tuple(value.event_id for value in state.inbox.pending_events),
                (event_b.event_id,),
            )
            self.assertIn(
                event_a.event_id,
                state.inbox.completed_authority_event_keys,
            )


if __name__ == "__main__":
    unittest.main()
