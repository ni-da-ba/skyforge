from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

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
from v2.ordinary_pipeline import OrdinaryPipelineStore
from v2.worker_provider import WorkerProviderConfig, WorkerTier
from v2.classifier_provider import ClassifierProviderConfig


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
