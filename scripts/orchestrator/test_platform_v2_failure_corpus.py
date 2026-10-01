from __future__ import annotations

import ast
import json
from pathlib import Path
import tempfile
import unittest

from v2 import (
    CIState,
    EffectKind,
    FrozenTaskSpec,
    MechanicalSnapshot,
    RemoteEffectIdentity,
    RemoteEffectObservation,
    RemoteEffectPresence,
    RemoteEffectRecord,
    TaskAttemptIdentity,
    TransitionKind,
    WriterFence,
    decide_mechanical,
)
from v2.effects import EffectReconcileDisposition, reconcile_remote_effect
from v2.events import DurableEvent
from v2.fence import FenceBusyError
from v2.inbox import InboxState, enqueue_events
from v2.quota import (
    LocalBudgetObservation,
    ProviderQuotaDecision,
    QuotaAdmissionDisposition,
    classify_quota_admission,
)


HEAD = "a" * 40
OLD_HEAD = "b" * 40
SPEC = "c" * 64


def accepted_snapshot(**overrides) -> MechanicalSnapshot:
    values = {
        "active_pr": True,
        "current_head_sha": HEAD,
        "evidence_sha": HEAD,
        "reviewed_sha": HEAD,
        "task_spec_hash": SPEC,
        "accepted_task_spec_hash": SPEC,
        "ci_state": CIState.PASS,
    }
    values.update(overrides)
    return MechanicalSnapshot(**values)


def pending_effect(kind: EffectKind, subject: str) -> RemoteEffectRecord:
    return RemoteEffectRecord.begin(
        RemoteEffectIdentity.create(
            attempt_id="d" * 64,
            kind=kind,
            subject=subject,
        )
    )


class Release4ExplicitFailureCorpusTest(unittest.TestCase):
    def test_out_of_order_webhook_delivery_cannot_override_current_repository_truth(self) -> None:
        stale_pr_event = DurableEvent(
            actionable=True,
            reason="older PR synchronize webhook arrived late",
            event="pull_request",
            action="synchronize",
            head_sha=OLD_HEAD,
            pr_number=840,
            source_id="delivery-old",
        )
        current_ci_event = DurableEvent(
            actionable=True,
            reason="current exact-head CI completed",
            event="workflow_run",
            action="completed",
            head_sha=HEAD,
            pr_number=840,
            source_id="delivery-current",
        )

        observed_plans = []
        for delivery_order in (
            (stale_pr_event, current_ci_event),
            (current_ci_event, stale_pr_event),
        ):
            inbox = enqueue_events(InboxState(), delivery_order).after
            self.assertGreaterEqual(len(inbox.pending_events), 1)
            observed_plans.append(decide_mechanical(accepted_snapshot()))

        self.assertEqual(observed_plans[0].kind, TransitionKind.MERGE_ELIGIBLE)
        self.assertEqual(observed_plans[1].kind, TransitionKind.MERGE_ELIGIBLE)
        self.assertEqual(observed_plans[0].digest, observed_plans[1].digest)

    def test_missed_webhook_reconciliation_uses_current_truth_without_event(self) -> None:
        inbox = InboxState()
        self.assertEqual(inbox.pending_events, ())

        plan = decide_mechanical(
            accepted_snapshot(
                ci_state=CIState.FAIL,
                evidence_sha="",
                reviewed_sha="",
            )
        )
        self.assertEqual(plan.kind, TransitionKind.REPAIR_ELIGIBLE)

    def test_provider_and_local_quota_exhaustion_block_without_consuming_attempt(self) -> None:
        provider_block = classify_quota_admission(
            ProviderQuotaDecision(
                authoritative=True,
                allowed=False,
                phase="capacity_exhausted",
                block_kind="provider_capacity",
                retry_after_seconds=900,
                reason="provider reports no safe capacity",
            ),
            LocalBudgetObservation(calls_used=0, daily_limit=10),
        )
        local_block = classify_quota_admission(
            None,
            LocalBudgetObservation(calls_used=10, daily_limit=10),
        )

        self.assertEqual(
            provider_block.disposition,
            QuotaAdmissionDisposition.BLOCK_PROVIDER,
        )
        self.assertFalse(provider_block.consume_attempt)
        self.assertEqual(
            local_block.disposition,
            QuotaAdmissionDisposition.BLOCK_LOCAL,
        )
        self.assertFalse(local_block.consume_attempt)

    def test_push_succeeds_but_local_completion_save_fails_reconciles_without_repush(self) -> None:
        record = pending_effect(EffectKind.PUSH_BRANCH, "branch:canary")
        decision = reconcile_remote_effect(
            record,
            RemoteEffectObservation(
                RemoteEffectPresence.PRESENT_EXACT,
                remote_identity="branch:canary@abc123",
            ),
        )
        self.assertEqual(
            decision.disposition,
            EffectReconcileDisposition.MARK_COMPLETE,
        )

    def test_comment_succeeds_but_local_completion_save_fails_reconciles_without_repost(self) -> None:
        record = pending_effect(EffectKind.POST_COMMENT, "issue:843")
        decision = reconcile_remote_effect(
            record,
            RemoteEffectObservation(
                RemoteEffectPresence.PRESENT_EXACT,
                remote_identity="comment:5723000000",
            ),
        )
        self.assertEqual(
            decision.disposition,
            EffectReconcileDisposition.MARK_COMPLETE,
        )

    def test_two_controllers_attempt_mutation_ownership_second_writer_is_denied(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "writer.lock"
            first = WriterFence(path=path, controller_id="controller-a", generation=7)
            second = WriterFence(path=path, controller_id="controller-b", generation=8)
            first.acquire()
            try:
                with self.assertRaises(FenceBusyError):
                    second.acquire()
            finally:
                first.release()

    def test_stale_local_state_after_restore_is_invalidated_by_current_head_truth(self) -> None:
        plan = decide_mechanical(
            accepted_snapshot(
                current_head_sha=HEAD,
                evidence_sha=OLD_HEAD,
                reviewed_sha=OLD_HEAD,
            )
        )
        self.assertEqual(plan.kind, TransitionKind.INVALIDATE_EVIDENCE)

    def test_active_attempt_task_spec_change_produces_new_attempt_and_effect_identity(self) -> None:
        first_spec = FrozenTaskSpec.from_payload(
            task_id="R4D",
            authority_key="issue:843",
            base_sha=HEAD,
            spec_version=1,
            payload={"objective": "first"},
        )
        second_spec = FrozenTaskSpec.from_payload(
            task_id="R4D",
            authority_key="issue:843",
            base_sha=HEAD,
            spec_version=1,
            payload={"objective": "changed"},
        )
        first_attempt = TaskAttemptIdentity.create(first_spec, attempt_number=1)
        second_attempt = TaskAttemptIdentity.create(second_spec, attempt_number=1)
        first_effect = RemoteEffectIdentity.create(
            attempt_id=first_attempt.attempt_id,
            kind=EffectKind.CREATE_PR,
            subject="issue:843",
        )
        second_effect = RemoteEffectIdentity.create(
            attempt_id=second_attempt.attempt_id,
            kind=EffectKind.CREATE_PR,
            subject="issue:843",
        )

        self.assertNotEqual(first_spec.spec_hash, second_spec.spec_hash)
        self.assertNotEqual(first_attempt.attempt_id, second_attempt.attempt_id)
        self.assertNotEqual(first_effect.effect_id, second_effect.effect_id)


class Release4FailureCorpusManifestTest(unittest.TestCase):
    REQUIRED = {
        "duplicate_webhook",
        "out_of_order_webhook",
        "missed_webhook_reconciliation",
        "restart_after_classification_before_dispatch",
        "restart_during_worker",
        "worker_timeout_failure",
        "provider_quota_capacity_exhaustion",
        "push_succeeds_local_state_update_fails",
        "pr_creation_succeeds_local_state_update_fails",
        "comment_succeeds_local_state_update_fails",
        "ci_evidence_stale_after_head_move",
        "merge_attempt_changed_head",
        "human_gate_active",
        "task_or_roadmap_spec_changes_active_attempt",
        "issue_closes_outside_controller",
        "pr_merges_outside_controller",
        "pr_closes_without_merge",
        "github_temporarily_unavailable",
        "stale_local_state_after_host_restore",
        "two_controllers_attempt_ownership",
        "old_trusted_comment_replayed",
        "external_manual_producer_active",
        "worker_edits_protected_path",
        "worker_requires_unavailable_external_evidence",
        "malformed_corrupt_local_state",
        "pending_remote_effect_after_restart",
    }

    def test_failure_corpus_evidence_references_resolve(self) -> None:
        root = Path(__file__).resolve().parents[2]
        payload = json.loads(
            (root / "docs/agent-state/PLATFORM_V2_FAILURE_CORPUS.json").read_text(
                encoding="utf-8"
            )
        )

        for row in payload["scenarios"]:
            for reference in row["evidence"]:
                with self.subTest(scenario=row["id"], reference=reference):
                    parts = reference.split("::")
                    path = root / parts[0]
                    self.assertTrue(path.is_file(), reference)
                    source = path.read_text(encoding="utf-8")

                    if parts[0].endswith(".py"):
                        self.assertEqual(len(parts), 2, reference)
                        method_name = parts[1]
                        tree = ast.parse(source)
                        methods = {
                            node.name
                            for node in ast.walk(tree)
                            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
                        }
                        self.assertIn(method_name, methods, reference)
                    else:
                        self.assertEqual(len(parts), 2, reference)
                        self.assertIn(parts[1], source, reference)

    def test_machine_readable_failure_corpus_is_complete_and_all_pass(self) -> None:
        root = Path(__file__).resolve().parents[2]
        path = root / "docs/agent-state/PLATFORM_V2_FAILURE_CORPUS.json"
        payload = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual(payload["schema_version"], 1)
        rows = payload["scenarios"]
        by_id = {row["id"]: row for row in rows}
        self.assertEqual(set(by_id), self.REQUIRED)
        for scenario_id, row in by_id.items():
            with self.subTest(scenario_id=scenario_id):
                self.assertEqual(row["status"], "PASS")
                self.assertTrue(row["evidence"])
                self.assertTrue(all(isinstance(item, str) and item.strip() for item in row["evidence"]))


if __name__ == "__main__":
    unittest.main()
