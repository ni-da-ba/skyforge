from __future__ import annotations

from dataclasses import replace
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

import platform_v2_hosted_runtime as hosted
from test_platform_v2_hosted_runtime import SECRET, write_legacy
from test_platform_v2_hosted_task_preflight import (
    LiveTruthRunner,
    signed,
    task_payload,
)
from v2.classifier_provider import (
    ClassifierProviderConfig,
    parse_classifier_response,
)
from v2.dormant_handoff_commit import (
    DormantCommitOutcome,
    DormantHandoffCommitLedger,
    DormantHandoffCommitStore,
    advance_dormant_handoff_commit,
)
from v2.dormant_worker import (
    DormantWorkerDisposition,
    advance_dormant_admitted_worker,
)
from v2.effects import EffectKind, RemoteEffectIdentity
from v2.hosted_execution_evidence import (
    HostedExecutionEvidenceDisposition,
    evaluate_hosted_execution_evidence,
)
from v2.ordinary_effects import OrdinaryEffectStore
from v2.quota import LocalBudgetObservation
from v2.worker_provider import WorkerProviderConfig


DISPATCH = json.dumps(
    {
        "decision": "DISPATCH",
        "lane": "Implementation",
        "objective": "Implement bounded feature",
        "stop_boundary": "merge boundary",
        "worker_tier": "LUNA",
        "allowed_paths": ["docs/operations/**"],
        "reason": "bounded exact rehearsal proposal",
    },
    separators=(",", ":"),
)


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout.strip()


def make_repo(root: Path) -> str:
    git(root, "init", "-b", "main")
    git(root, "config", "user.email", "test@example.invalid")
    git(root, "config", "user.name", "Skyforge R5C21 Rehearsal")
    (root / ".gitignore").write_text(
        ".skyforge-platform-v2/\n.skyforge-orchestrator/\n",
        encoding="utf-8",
    )
    target = root / "docs" / "operations"
    target.mkdir(parents=True)
    (target / "base.txt").write_text("base\n", encoding="utf-8")
    git(root, "add", ".gitignore", "docs/operations/base.txt")
    git(root, "commit", "-m", "rehearsal base")
    return git(root, "rev-parse", "HEAD")


def budget():
    return LocalBudgetObservation(calls_used=0, daily_limit=10)


class FakeClassifier:
    def __init__(self):
        self.calls = 0

    def classify(self, *, request, root, config):
        self.calls += 1
        return DISPATCH, parse_classifier_response(DISPATCH)


class FakeWorker:
    def __init__(self):
        self.calls = 0

    def run(self, *, spec, worktree, config):
        self.calls += 1
        target = Path(worktree) / "docs" / "operations" / "r5c21-rehearsal.txt"
        target.write_text("bounded rehearsal output\n", encoding="utf-8")
        return "bounded rehearsal worker complete"


def prepare_through_admission(root: Path):
    base = make_repo(root)
    write_legacy(root)
    app = hosted.HostedV2Substrate(
        root,
        repo="ni-da-ba/skyforge",
        require_webhook_secret=True,
        startup_reconcile=True,
        webhook_secret=SECRET,
        trusted_actors=("ni-da-ba",),
    )
    raw, headers = signed(task_payload(), delivery="r5c21-rehearsal")
    status, response = app.handle_webhook(headers=headers, raw=raw)
    assert status == 202 and response["task_authority_recorded"]

    claimed = app.claim_next_task_plan()
    assert claimed.disposition.value == "CLAIMED"

    truth = LiveTruthRunner(main=base)
    ready = app.advance_task_plan_preflight(runner=truth)
    assert ready.disposition.value == "READY_FOR_CLASSIFIER"

    classifier = FakeClassifier()
    classified = app.advance_task_classifier(
        provider=classifier,
        provider_quota=None,
        local_budget=budget(),
        config=ClassifierProviderConfig("fixture-classifier", "low"),
    )
    assert classified.disposition.value == "COMPLETE"
    assert classifier.calls == 1

    admitted = app.advance_task_admission(
        active_external_claims=(),
        provider_quota=None,
        local_budget=budget(),
        attempt_number=1,
        runner=LiveTruthRunner(main=base),
    )
    assert admitted.disposition.value == "RECORDED"
    assert admitted.ledger.record.outcome.value == "ADMITTED"
    return app, base, admitted.ledger.record


def complete_rehearsal(root: Path):
    app, base, admission = prepare_through_admission(root)
    worker = FakeWorker()
    worker_result = advance_dormant_admitted_worker(
        root=root,
        provider=worker,
        provider_quota=None,
        local_budget=budget(),
        config=WorkerProviderConfig(
            tier=admission.worker_spec.tier,
            model="fixture-worker",
            reasoning_effort="low",
        ),
    )
    assert worker_result.disposition is DormantWorkerDisposition.HANDOFF_READY
    assert worker.calls == 1

    handoff = advance_dormant_handoff_commit(root=root)
    assert handoff.ledger.record.outcome is DormantCommitOutcome.COMMITTED
    return app, base, admission, worker_result, handoff


class HostedExecutionRehearsalTest(unittest.TestCase):
    def test_end_to_end_nonproduction_rehearsal_is_accepted(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app, base, admission, worker_result, handoff = complete_rehearsal(root)

            evidence = evaluate_hosted_execution_evidence(root)
            self.assertEqual(
                evidence.disposition,
                HostedExecutionEvidenceDisposition.ACCEPTED,
            )
            self.assertEqual(evidence.blockers, ())
            self.assertEqual(evidence.accepted_main_sha, base)
            self.assertEqual(evidence.plan_id, admission.plan_id)
            self.assertEqual(
                evidence.classifier_run_id,
                admission.classifier_run_id,
            )
            self.assertEqual(evidence.admission_record_id, admission.record_id)
            self.assertEqual(
                evidence.worker_run_id,
                worker_result.worker.record.run_id,
            )
            self.assertEqual(
                evidence.handoff_record_id,
                handoff.ledger.record.record_id,
            )
            self.assertEqual(
                evidence.changed_paths,
                ("docs/operations/r5c21-rehearsal.txt",),
            )
            self.assertNotEqual(evidence.result_head_sha, base)

            self.assertEqual(git(root, "rev-parse", "HEAD"), base)
            worktree = Path(worker_result.worker.record.worktree)
            self.assertEqual(
                git(worktree, "rev-parse", "HEAD"),
                evidence.result_head_sha,
            )
            self.assertEqual(git(worktree, "status", "--porcelain"), "")
            self.assertEqual(
                len(OrdinaryEffectStore.for_root(root).load().records),
                0,
            )

            health = app.health_snapshot()
            self.assertFalse(health["worker_dispatch_enabled"])
            self.assertFalse(health["remote_effect_execution_enabled"])
            self.assertFalse(health["mutation_authority"])

    def test_missing_later_boundary_blocks_evidence(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_through_admission(root)
            evidence = evaluate_hosted_execution_evidence(root)
            self.assertEqual(
                evidence.disposition,
                HostedExecutionEvidenceDisposition.BLOCKED,
            )
            self.assertIn("matching durable worker run is absent", evidence.blockers)
            self.assertIn(
                "bounded local handoff commit record is absent",
                evidence.blockers,
            )

    def test_no_change_handoff_is_not_sufficient_acceptance_evidence(self):
        class NoChangeWorker:
            def run(self, *, spec, worktree, config):
                return "completed with no repository delta"

        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, _, admission = prepare_through_admission(root)
            result = advance_dormant_admitted_worker(
                root=root,
                provider=NoChangeWorker(),
                provider_quota=None,
                local_budget=budget(),
                config=WorkerProviderConfig(
                    tier=admission.worker_spec.tier,
                    model="fixture-worker",
                    reasoning_effort="low",
                ),
            )
            self.assertEqual(
                result.disposition,
                DormantWorkerDisposition.HANDOFF_READY,
            )
            handoff = advance_dormant_handoff_commit(root=root)
            self.assertEqual(
                handoff.ledger.record.outcome,
                DormantCommitOutcome.NO_CHANGE,
            )
            evidence = evaluate_hosted_execution_evidence(root)
            self.assertEqual(
                evidence.disposition,
                HostedExecutionEvidenceDisposition.BLOCKED,
            )
            self.assertIn(
                "bounded local handoff outcome is not COMMITTED",
                evidence.blockers,
            )

    def test_handoff_identity_drift_blocks_evidence(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, _, _, _, handoff = complete_rehearsal(root)
            record = handoff.ledger.record
            drifted = replace(record, worker_run_id="f" * 64)
            DormantHandoffCommitStore.for_root(root).save(
                DormantHandoffCommitLedger(drifted)
            )
            evidence = evaluate_hosted_execution_evidence(root)
            self.assertEqual(
                evidence.disposition,
                HostedExecutionEvidenceDisposition.BLOCKED,
            )
            self.assertIn(
                "handoff worker-run identity differs from durable worker",
                evidence.blockers,
            )

    def test_any_remote_effect_blocks_hosted_execution_acceptance(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, _, admission, _, _ = complete_rehearsal(root)
            identity = RemoteEffectIdentity.create(
                attempt_id=admission.attempt.attempt_id,
                kind=EffectKind.PUSH_BRANCH,
                subject=f"branch:{admission.worker_spec.branch}",
            )
            store = OrdinaryEffectStore.for_root(root)
            store.save(store.load().begin(identity))

            evidence = evaluate_hosted_execution_evidence(root)
            self.assertEqual(
                evidence.disposition,
                HostedExecutionEvidenceDisposition.BLOCKED,
            )
            self.assertIn(
                "remote-effect ledger is non-empty; rehearsal crossed remote-effect boundary",
                evidence.blockers,
            )


class CapabilityIsolationTest(unittest.TestCase):
    def test_evaluator_is_read_only_and_hosted_runtime_still_cannot_execute_worker(self):
        root = Path(__file__).resolve().parent
        source = (root / "v2/hosted_execution_evidence.py").read_text(encoding="utf-8")
        for token in (
            "subprocess",
            "advance_dormant_admitted_worker",
            "advance_dormant_handoff_commit",
            "WriterFence",
            "git push",
            "gh pr",
            "gh issue",
            ".save(",
        ):
            self.assertNotIn(token, source)

        hosted_source = (root / "platform_v2_hosted_runtime.py").read_text(
            encoding="utf-8"
        )
        self.assertNotIn("dormant_worker", hosted_source)
        self.assertNotIn("dormant_handoff_commit", hosted_source)
        self.assertNotIn("worker_provider", hosted_source)
        self.assertNotIn("worker_workspace", hosted_source)


if __name__ == "__main__":
    unittest.main()
