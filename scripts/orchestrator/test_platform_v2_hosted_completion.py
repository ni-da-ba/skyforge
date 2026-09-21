from __future__ import annotations

from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch

import platform_v2_hosted_runtime as hosted
from test_platform_v2_hosted_execution_runtime import (
    CompositeReadRunner,
    FakeClassifier,
    FakeRemoteFactory,
    FakeWorker,
    budget,
    make_repo,
    ready_gate,
)
from test_platform_v2_hosted_runtime import SECRET, write_legacy
from test_platform_v2_hosted_task_preflight import signed, task_payload
from v2.dormant_handoff_commit import DormantHandoffCommitStore
from v2.hosted_admission import HostedAdmissionStore
from v2.hosted_completion import (
    HostedCompletionDisposition,
    HostedCompletionStatus,
    HostedCompletionStore,
    advance_hosted_completion_cleanup,
    record_completed_managed_task,
)
from v2.hosted_execution_runtime import (
    HostedExecutionAdvanceDisposition,
    HostedExecutionDependencies,
)
from v2.hosted_task_plan import HostedTaskPlanStore
from v2.hosted_worker_scheduler import (
    HostedWorkerScheduleState,
    HostedWorkerSchedulerStore,
    update_hosted_worker_schedule,
)
from v2.ordinary_effects import OrdinaryEffectStore
from v2.ordinary_pipeline import OrdinaryPipelineStore
from v2.worker_provider import WorkerProviderConfig, WorkerTier
from v2.classifier_provider import ClassifierProviderConfig


class NoChangeWorker:
    def __init__(self):
        self.calls = 0

    def run(self, *, spec, worktree, config):
        self.calls += 1
        return "requested repository state is already satisfied"


class HostedCompletionTest(unittest.TestCase):
    def _runtime(self, root: Path, gate):
        return hosted.HostedV2Substrate(
            root,
            repo="ni-da-ba/skyforge",
            require_webhook_secret=True,
            startup_reconcile=True,
            webhook_secret=SECRET,
            trusted_actors=("ni-da-ba",),
            production_execution_requested=True,
            execution_gate=gate,
        )

    def _reach_handoff(self, root: Path):
        base = make_repo(root)
        write_legacy(root)
        gate = ready_gate(base)
        app = self._runtime(root, gate)
        raw, headers = signed(task_payload(), delivery="r5c27-completion")
        status, response = app.handle_webhook(headers=headers, raw=raw)
        self.assertEqual(status, 202)
        self.assertTrue(response["accepted"])

        remote = FakeRemoteFactory()
        deps = HostedExecutionDependencies(
            classifier_provider=FakeClassifier(),
            worker_provider=FakeWorker(),
            classifier_local_budget=budget(),
            worker_local_budget=budget(),
            classifier_config=ClassifierProviderConfig("fixture-classifier", "low"),
            worker_config=WorkerProviderConfig(WorkerTier.LUNA, "fixture-worker", "low"),
            runner=CompositeReadRunner(base),
            remote_factory=lambda _worktree, binding: remote(binding),
        )
        expected = (
            HostedExecutionAdvanceDisposition.TASK_CLAIMED,
            HostedExecutionAdvanceDisposition.PREFLIGHT_ADVANCED,
            HostedExecutionAdvanceDisposition.CLASSIFIER_ADVANCED,
            HostedExecutionAdvanceDisposition.ADMISSION_ADVANCED,
            HostedExecutionAdvanceDisposition.WORKER_ADVANCED,
            HostedExecutionAdvanceDisposition.LOCAL_COMMIT_ADVANCED,
            HostedExecutionAdvanceDisposition.REMOTE_HANDOFF_ADVANCED,
        )
        for disposition in expected:
            result = app.advance_one_execution_step(deps)
            self.assertEqual(result.disposition, disposition)
        handoff = OrdinaryPipelineStore.for_root(root).load().reconstructible_managed_handoffs()[0]
        return app, gate, handoff

    def test_record_then_cleanup_reopens_singleton_slots_and_suppresses_replay(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app, gate, handoff = self._reach_handoff(root)
            plan = HostedTaskPlanStore.for_root(root).load().active
            self.assertIsNotNone(plan)

            admission = HostedAdmissionStore.for_root(root).load().record
            update_hosted_worker_schedule(
                root=root,
                attempt_id=admission.attempt.attempt_id,
                state=HostedWorkerScheduleState.COMPLETED,
                reason="fixture worker completed",
            )

            recorded = record_completed_managed_task(
                root=root,
                handoff_digest=handoff.digest,
                lifecycle_digest="lifecycle-fixture",
            )
            self.assertEqual(recorded.disposition, HostedCompletionDisposition.RECORDED)
            self.assertEqual(recorded.record.status, HostedCompletionStatus.RECORDED)

            cleaned = advance_hosted_completion_cleanup(root=root)
            self.assertEqual(cleaned.disposition, HostedCompletionDisposition.CLEANED)
            self.assertEqual(cleaned.record.status, HostedCompletionStatus.CLEANED)
            self.assertIsNone(HostedTaskPlanStore.for_root(root).load().active)
            self.assertIsNone(HostedAdmissionStore.for_root(root).load().record)
            self.assertIsNone(DormantHandoffCommitStore.for_root(root).load().record)
            scheduler = HostedWorkerSchedulerStore.for_root(root).load()
            self.assertEqual(
                scheduler.get(admission.attempt.attempt_id).state,
                HostedWorkerScheduleState.RETIRED,
            )

            state = app.store.load()
            self.assertEqual(state.inbox.pending_events, ())
            self.assertIn(plan.event_id, state.inbox.completed_authority_event_keys)
            self.assertEqual(
                HostedCompletionStore.for_root(root).load().pending(),
                None,
            )
            app.state = app.store.load()
            health = app.health_snapshot()
            self.assertEqual(health["hosted_completion_count"], 1)
            self.assertEqual(health["hosted_completion_cleaned_count"], 1)
            self.assertEqual(health["hosted_completion_pending_cleanup_id"], "")

            # A process restart sees the cleaned inbox and suppresses semantic replay
            # even when GitHub redelivers the same task under a new delivery id.
            restarted = self._runtime(root, gate)
            raw, headers = signed(task_payload(), delivery="r5c27-replay-after-complete")
            status, response = restarted.handle_webhook(headers=headers, raw=raw)
            self.assertEqual(status, 202)
            self.assertTrue(response["semantic_replay_suppressed"])
            self.assertEqual(restarted.store.load().inbox.pending_events, ())

    def test_no_change_attempt_records_completion_and_reopens_singleton_without_remote_effects(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            base = make_repo(root)
            write_legacy(root)
            gate = ready_gate(base)
            app = self._runtime(root, gate)
            raw, headers = signed(task_payload(), delivery="r5c27-no-change")
            status, response = app.handle_webhook(headers=headers, raw=raw)
            self.assertEqual(status, 202)
            self.assertTrue(response["accepted"])

            worker = NoChangeWorker()
            deps = HostedExecutionDependencies(
                classifier_provider=FakeClassifier(),
                worker_provider=worker,
                classifier_local_budget=budget(),
                worker_local_budget=budget(),
                classifier_config=ClassifierProviderConfig("fixture-classifier", "low"),
                worker_config=WorkerProviderConfig(WorkerTier.LUNA, "fixture-worker", "low"),
                runner=CompositeReadRunner(base),
            )
            expected = (
                HostedExecutionAdvanceDisposition.TASK_CLAIMED,
                HostedExecutionAdvanceDisposition.PREFLIGHT_ADVANCED,
                HostedExecutionAdvanceDisposition.CLASSIFIER_ADVANCED,
                HostedExecutionAdvanceDisposition.ADMISSION_ADVANCED,
                HostedExecutionAdvanceDisposition.WORKER_ADVANCED,
                HostedExecutionAdvanceDisposition.LOCAL_COMMIT_ADVANCED,
                HostedExecutionAdvanceDisposition.TASK_COMPLETION_RECORDED,
                HostedExecutionAdvanceDisposition.TASK_COMPLETED,
            )
            for disposition in expected:
                result = app.advance_one_execution_step(deps)
                self.assertEqual(result.disposition, disposition)

            self.assertEqual(worker.calls, 1)
            self.assertIsNone(HostedTaskPlanStore.for_root(root).load().active)
            self.assertIsNone(HostedAdmissionStore.for_root(root).load().record)
            self.assertIsNone(DormantHandoffCommitStore.for_root(root).load().record)
            self.assertEqual(OrdinaryEffectStore.for_root(root).load().records, ())
            self.assertEqual(OrdinaryPipelineStore.for_root(root).load().records, ())
            completion = HostedCompletionStore.for_root(root).load().records[-1]
            self.assertEqual(completion.status, HostedCompletionStatus.CLEANED)
            state = app.store.load()
            self.assertEqual(state.inbox.pending_events, ())
            self.assertIn(completion.event_id, state.inbox.completed_authority_event_keys)

    def test_completion_cleanup_holds_runtime_lock_against_webhook_stale_state_overwrite(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app, gate, handoff = self._reach_handoff(root)
            plan = HostedTaskPlanStore.for_root(root).load().active
            admission = HostedAdmissionStore.for_root(root).load().record
            update_hosted_worker_schedule(
                root=root,
                attempt_id=admission.attempt.attempt_id,
                state=HostedWorkerScheduleState.COMPLETED,
                reason="fixture worker completed",
            )
            record_completed_managed_task(
                root=root,
                handoff_digest=handoff.digest,
                lifecycle_digest="lifecycle-race-fixture",
            )

            deps = HostedExecutionDependencies(
                classifier_provider=FakeClassifier(),
                worker_provider=FakeWorker(),
                classifier_local_budget=budget(),
                worker_local_budget=budget(),
                classifier_config=ClassifierProviderConfig("fixture-classifier", "low"),
                worker_config=WorkerProviderConfig(WorkerTier.LUNA, "fixture-worker", "low"),
                runner=CompositeReadRunner(gate.accepted_main_sha),
            )
            cleanup_persisted = threading.Event()
            allow_cleanup_return = threading.Event()
            execution_done = threading.Event()
            webhook_done = threading.Event()
            execution_results = []
            webhook_results = []
            real_cleanup = advance_hosted_completion_cleanup

            def paused_cleanup(*, root):
                result = real_cleanup(root=root)
                cleanup_persisted.set()
                if not allow_cleanup_return.wait(5):
                    raise RuntimeError("test timed out waiting to release cleanup")
                return result

            def execute_cleanup():
                try:
                    execution_results.append(app.advance_one_execution_step(deps))
                finally:
                    execution_done.set()

            raw, headers = signed(
                task_payload(),
                delivery="r5c27-race-replay-after-complete",
            )

            def deliver_replay():
                try:
                    webhook_results.append(app.handle_webhook(headers=headers, raw=raw))
                finally:
                    webhook_done.set()

            with patch(
                "v2.hosted_execution_runtime.advance_hosted_completion_cleanup",
                side_effect=paused_cleanup,
            ):
                execution_thread = threading.Thread(target=execute_cleanup)
                execution_thread.start()
                self.assertTrue(cleanup_persisted.wait(5))

                webhook_thread = threading.Thread(target=deliver_replay)
                webhook_thread.start()
                self.assertFalse(
                    webhook_done.wait(0.15),
                    "webhook ingress must wait for completion cleanup cache reload",
                )

                allow_cleanup_return.set()
                execution_thread.join(5)
                webhook_thread.join(5)

            self.assertTrue(execution_done.is_set())
            self.assertTrue(webhook_done.is_set())
            self.assertEqual(
                execution_results[0].disposition,
                HostedExecutionAdvanceDisposition.TASK_COMPLETED,
            )
            status, response = webhook_results[0]
            self.assertEqual(status, 202)
            self.assertTrue(response["semantic_replay_suppressed"])

            state = app.store.load()
            self.assertEqual(state.inbox.pending_events, ())
            self.assertIn(plan.event_id, state.inbox.completed_authority_event_keys)

    def test_cleanup_is_idempotent_after_completion_status_is_clean(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _app, _gate, handoff = self._reach_handoff(root)
            record_completed_managed_task(
                root=root,
                handoff_digest=handoff.digest,
                lifecycle_digest="lifecycle-fixture",
            )
            self.assertEqual(
                advance_hosted_completion_cleanup(root=root).disposition,
                HostedCompletionDisposition.CLEANED,
            )
            self.assertEqual(
                advance_hosted_completion_cleanup(root=root).disposition,
                HostedCompletionDisposition.NO_PENDING,
            )


if __name__ == "__main__":
    unittest.main()
