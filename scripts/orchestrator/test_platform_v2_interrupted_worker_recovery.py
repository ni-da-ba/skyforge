from __future__ import annotations

from pathlib import Path
import tempfile
import unittest
from unittest import mock

from test_platform_v2_multi_workflow_authority import admitted, plan
from v2.concurrency_claims import (
    ConcurrencyClaimLedger,
    ConcurrencyClaimStore,
    acquire_concurrency_claim,
)
from v2.hosted_admission import HostedAdmissionLedger, HostedAdmissionStore
from v2.hosted_state import HostedIngressState, HostedStateStore
from v2.hosted_task_plan import HostedTaskPlanLedger, HostedTaskPlanStore
from v2.hosted_worker_scheduler import (
    HostedWorkerScheduleState,
    HostedWorkerSchedulerStore,
    reserve_hosted_worker,
)
from v2.inbox import InboxState
from v2.interrupted_worker_recovery import (
    InterruptedWorkerRecoveryStatus,
    InterruptedWorkerRecoveryStore,
    inspect_clean_interrupted_worker,
    recover_clean_interrupted_worker,
)
from v2.task_authority import TaskAuthorityWakeReference
from v2.task_event_composition import (
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


def install_interrupted(root: Path):
    event, plan_record = plan("recover", 754)
    admission = admitted("recover", plan_record, "docs/recover/**")
    HostedTaskPlanStore.for_root(root).save(HostedTaskPlanLedger((plan_record,)))
    HostedAdmissionStore.for_root(root).save(HostedAdmissionLedger((admission,)))

    claims = acquire_concurrency_claim(
        ConcurrencyClaimLedger(),
        admission.worker_spec,
    )
    ConcurrencyClaimStore.for_root(root).save(claims.ledger)
    reserve_hosted_worker(
        root=root,
        attempt_id=admission.attempt.attempt_id,
    )

    worktree = root / "worker"
    worktree.mkdir()
    worker = WorkerRunRecord(
        spec=admission.worker_spec,
        worktree=str(worktree),
        config=WorkerProviderConfig(
            admission.worker_spec.tier,
            "fixture-worker",
            "low",
        ),
        status=WorkerRunStatus.RUNNING,
    )
    WorkerRunStore.for_root(root).save(WorkerRunLedger((worker,)))

    HostedStateStore.for_root(root).save(
        HostedIngressState(
            inbox=InboxState(
                pending_events=(event,),
                owned_event_keys=(event.event_id,),
            )
        )
    )
    reference = TaskAuthorityWakeReference(
        repo="ni-da-ba/skyforge",
        issue_number=754,
        comment_id=1234,
        actor="ni-da-ba",
        body="AUDIT TASK\nfixture recovery authority",
        created_at="2026-09-22T03:00:00Z",
        updated_at="2026-09-22T03:00:00Z",
    )
    TaskAuthorityEventStore.for_root(root).capture(
        TaskAuthorityEventRecord(
            event_id=event.event_id,
            reference=reference,
            delivery_id="recovery-fixture",
        )
    )
    return event, plan_record, admission, worker


class InterruptedWorkerRecoveryTest(unittest.TestCase):
    def test_clean_interrupted_attempt_retires_execution_ownership_without_completion(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            event, plan_record, admission, worker = install_interrupted(root)

            def fake_git(_path, *args):
                if args == ("rev-parse", "HEAD"):
                    return worker.spec.base_sha
                if args == ("branch", "--show-current"):
                    return worker.spec.branch
                if args == ("status", "--porcelain", "--untracked-files=all"):
                    return ""
                raise AssertionError(args)

            with mock.patch(
                "v2.interrupted_worker_recovery._git",
                side_effect=fake_git,
            ):
                inspection = inspect_clean_interrupted_worker(
                    root=root,
                    attempt_id=admission.attempt.attempt_id,
                )
                self.assertTrue(inspection.ready, inspection.blockers)
                recovered = recover_clean_interrupted_worker(
                    root=root,
                    attempt_id=admission.attempt.attempt_id,
                )

            self.assertEqual(
                recovered.status,
                InterruptedWorkerRecoveryStatus.RECOVERED,
            )
            self.assertEqual(
                WorkerRunStore.for_root(root)
                .load()
                .find_attempt(admission.attempt.attempt_id)
                .status,
                WorkerRunStatus.INTERRUPTED,
            )
            scheduler = HostedWorkerSchedulerStore.for_root(root).load().get(
                admission.attempt.attempt_id
            )
            self.assertEqual(scheduler.state, HostedWorkerScheduleState.RETIRED)
            claims = ConcurrencyClaimStore.for_root(root).load()
            self.assertIsNone(claims.active_for_attempt(admission.attempt.attempt_id))
            self.assertIsNotNone(claims.retired_for_attempt(admission.attempt.attempt_id))
            self.assertIsNone(
                HostedAdmissionStore.for_root(root).load().for_plan(plan_record.plan_id)
            )
            self.assertIsNone(
                HostedTaskPlanStore.for_root(root).load().get(plan_record.plan_id)
            )
            state = HostedStateStore.for_root(root).load()
            self.assertIn(event.event_id, state.inbox.retired_event_keys)
            self.assertNotIn(event.event_id, state.inbox.completed_authority_event_keys)
            self.assertNotIn(
                event.event_id,
                {value.event_id for value in state.inbox.pending_events},
            )
            self.assertIsNotNone(
                TaskAuthorityEventStore.for_root(root).load().get(event.event_id)
            )
            self.assertEqual(
                InterruptedWorkerRecoveryStore.for_root(root)
                .load()
                .get_attempt(admission.attempt.attempt_id)
                .status,
                InterruptedWorkerRecoveryStatus.RECOVERED,
            )

            with mock.patch(
                "v2.interrupted_worker_recovery._git",
                side_effect=fake_git,
            ):
                replay = recover_clean_interrupted_worker(
                    root=root,
                    attempt_id=admission.attempt.attempt_id,
                )
            self.assertEqual(replay.recovery_id, recovered.recovery_id)

    def test_dirty_interrupted_worktree_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _event, _plan, admission, worker = install_interrupted(root)

            def fake_git(_path, *args):
                if args == ("rev-parse", "HEAD"):
                    return worker.spec.base_sha
                if args == ("branch", "--show-current"):
                    return worker.spec.branch
                if args == ("status", "--porcelain", "--untracked-files=all"):
                    return " M docs/recover/result.txt"
                raise AssertionError(args)

            with mock.patch(
                "v2.interrupted_worker_recovery._git",
                side_effect=fake_git,
            ):
                inspection = inspect_clean_interrupted_worker(
                    root=root,
                    attempt_id=admission.attempt.attempt_id,
                )
            self.assertFalse(inspection.ready)
            self.assertIn(
                "worker worktree contains changes; automatic retirement forbidden",
                inspection.blockers,
            )
            self.assertIsNotNone(
                ConcurrencyClaimStore.for_root(root)
                .load()
                .active_for_attempt(admission.attempt.attempt_id)
            )

    def test_prepared_recovery_replays_after_partial_retirement(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            event, plan_record, admission, worker = install_interrupted(root)

            def fake_git(_path, *args):
                if args == ("rev-parse", "HEAD"):
                    return worker.spec.base_sha
                if args == ("branch", "--show-current"):
                    return worker.spec.branch
                if args == ("status", "--porcelain", "--untracked-files=all"):
                    return ""
                raise AssertionError(args)

            with mock.patch(
                "v2.interrupted_worker_recovery._git",
                side_effect=fake_git,
            ):
                first = inspect_clean_interrupted_worker(
                    root=root,
                    attempt_id=admission.attempt.attempt_id,
                )
            self.assertTrue(first.ready)
            InterruptedWorkerRecoveryStore.for_root(root).save(
                InterruptedWorkerRecoveryStore.for_root(root).load().put(first.record)
            )

            # Simulate a crash after exact scheduler/claim/plan/admission retirement.
            from v2.hosted_worker_scheduler import retire_hosted_worker_schedule
            retire_hosted_worker_schedule(
                root=root,
                attempt_id=admission.attempt.attempt_id,
                admission_record_id=admission.record_id,
                reason="partial recovery fixture",
            )
            ConcurrencyClaimStore.for_root(root).retire(admission.attempt.attempt_id)
            HostedAdmissionStore.for_root(root).save(
                HostedAdmissionStore.for_root(root).load().remove_plan(plan_record.plan_id)
            )
            HostedTaskPlanStore.for_root(root).save(
                HostedTaskPlanStore.for_root(root).load().remove(plan_record.plan_id)
            )

            with mock.patch(
                "v2.interrupted_worker_recovery._git",
                side_effect=fake_git,
            ):
                replay = recover_clean_interrupted_worker(
                    root=root,
                    attempt_id=admission.attempt.attempt_id,
                )
            self.assertEqual(
                replay.status,
                InterruptedWorkerRecoveryStatus.RECOVERED,
            )
            self.assertIn(
                event.event_id,
                HostedStateStore.for_root(root).load().inbox.retired_event_keys,
            )


if __name__ == "__main__":
    unittest.main()
