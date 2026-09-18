from __future__ import annotations

import copy
import json
from pathlib import Path
import tempfile
import unittest

import platform_v2_hosted_runtime as hosted
from test_platform_v2_hosted_runtime import SECRET, write_legacy
from test_platform_v2_hosted_task_preflight import (
    BODY,
    MAIN,
    LiveTruthRunner,
    signed,
    task_payload,
)
from v2.classifier_provider import ClassifierProviderConfig, parse_classifier_response
from v2.external import ExternalProducerClaim
from v2.hosted_admission import (
    HostedAdmissionDisposition,
    HostedAdmissionLedger,
    HostedAdmissionOutcome,
    HostedAdmissionStore,
)
from v2.quota import LocalBudgetObservation, ProviderQuotaDecision


DISPATCH = json.dumps(
    {
        "decision": "DISPATCH",
        "lane": "Implementation",
        "objective": "Implement bounded feature",
        "stop_boundary": "merge boundary",
        "worker_tier": "LUNA",
        "allowed_paths": ["docs/operations/**"],
        "reason": "bounded exact proposal",
    },
    separators=(",", ":"),
)


class FakeProvider:
    def __init__(self, response=DISPATCH):
        self.calls = 0
        self.response = response

    def classify(self, *, request, root, config):
        self.calls += 1
        return self.response, parse_classifier_response(self.response)


def budget(*, used=0, limit=10):
    return LocalBudgetObservation(calls_used=used, daily_limit=limit)


def runtime(root: Path):
    write_legacy(root)
    return hosted.HostedV2Substrate(
        root,
        repo="ni-da-ba/skyforge",
        require_webhook_secret=True,
        startup_reconcile=True,
        webhook_secret=SECRET,
        trusted_actors=("ni-da-ba",),
    )


def classified_runtime(root: Path, *, response=DISPATCH):
    app = runtime(root)
    raw, headers = signed(task_payload())
    status, response_payload = app.handle_webhook(headers=headers, raw=raw)
    assert status == 202 and response_payload["task_authority_recorded"]
    assert app.claim_next_task_plan().disposition.value == "CLAIMED"
    assert (
        app.advance_task_plan_preflight(runner=LiveTruthRunner()).disposition.value
        == "READY_FOR_CLASSIFIER"
    )
    provider = FakeProvider(response)
    classified = app.advance_task_classifier(
        provider=provider,
        provider_quota=None,
        local_budget=budget(),
        config=ClassifierProviderConfig("fixture", "low"),
    )
    assert classified.disposition.value == "COMPLETE"
    assert provider.calls == 1
    return app


class HostedAdmissionPolicyTest(unittest.TestCase):
    def test_exact_dispatch_admits_and_persists_full_frozen_identity(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = classified_runtime(root)
            result = app.advance_task_admission(
                active_external_claims=(),
                provider_quota=None,
                local_budget=budget(),
                attempt_number=1,
                runner=LiveTruthRunner(),
            )
            self.assertEqual(result.disposition, HostedAdmissionDisposition.RECORDED)
            record = result.ledger.record
            self.assertEqual(record.outcome, HostedAdmissionOutcome.ADMITTED)
            self.assertIsNotNone(record.frozen_task)
            self.assertIsNotNone(record.attempt)
            self.assertIsNotNone(record.worker_spec)
            self.assertEqual(record.current_main, MAIN)
            self.assertEqual(record.frozen_task.base_sha, MAIN)
            self.assertEqual(record.attempt.base_sha, MAIN)
            self.assertEqual(record.worker_spec.base_sha, MAIN)
            self.assertEqual(record.worker_spec.task_spec_hash, record.frozen_task.spec_hash)
            self.assertEqual(record.worker_spec.attempt_id, record.attempt.attempt_id)
            self.assertTrue(record.worker_spec.branch.startswith("codex/implementation-"))

            store = HostedAdmissionStore.for_root(root)
            restarted = store.load()
            self.assertEqual(restarted, result.ledger)
            self.assertEqual(
                store.adapter.path.read_bytes(),
                store.adapter.backup_path.read_bytes(),
            )

            health = app.health_snapshot()
            self.assertTrue(health["explicit_dispatch_admission_enabled"])
            self.assertFalse(health["automatic_dispatch_admission_enabled"])
            self.assertEqual(health["active_admission_record_id"], record.record_id)
            self.assertEqual(health["active_admission_outcome"], "ADMITTED")
            self.assertFalse(health["worker_dispatch_enabled"])
            self.assertFalse(health["mutation_authority"])

    def test_restart_returns_already_recorded_without_remote_reads(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = classified_runtime(root)
            first = app.advance_task_admission(
                active_external_claims=(),
                provider_quota=None,
                local_budget=budget(),
                attempt_number=1,
                runner=LiveTruthRunner(),
            )
            self.assertEqual(first.ledger.record.outcome, HostedAdmissionOutcome.ADMITTED)

            restarted = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                trusted_actors=("ni-da-ba",),
            )

            def forbidden(*args, **kwargs):
                raise AssertionError("already-recorded admission must not read remote truth")

            second = restarted.advance_task_admission(
                active_external_claims=(),
                provider_quota=None,
                local_budget=budget(),
                attempt_number=1,
                runner=forbidden,
            )
            self.assertEqual(
                second.disposition,
                HostedAdmissionDisposition.ALREADY_RECORDED,
            )
            self.assertEqual(second.ledger, first.ledger)

    def test_main_movement_records_reclassify_without_worker_authority(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = classified_runtime(root)
            result = app.advance_task_admission(
                active_external_claims=(),
                provider_quota=None,
                local_budget=budget(),
                attempt_number=1,
                runner=LiveTruthRunner(main="b" * 40),
            )
            record = result.ledger.record
            self.assertEqual(record.outcome, HostedAdmissionOutcome.RECLASSIFY)
            self.assertIsNone(record.frozen_task)
            self.assertIsNone(record.attempt)
            self.assertIsNone(record.worker_spec)
            self.assertFalse(record.consume_attempt)

    def test_source_pr_without_classification_head_records_reclassify(self):
        response = json.dumps(
            {
                "decision": "DISPATCH",
                "lane": "Implementation",
                "pr_number": 901,
                "objective": "Implement bounded feature",
                "stop_boundary": "merge boundary",
                "worker_tier": "LUNA",
                "allowed_paths": ["docs/operations/**"],
                "reason": "proposal references existing PR",
            }
        )
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = classified_runtime(root, response=response)
            result = app.advance_task_admission(
                active_external_claims=(),
                provider_quota=None,
                local_budget=budget(),
                attempt_number=1,
                runner=LiveTruthRunner(),
            )
            self.assertEqual(
                result.ledger.record.outcome,
                HostedAdmissionOutcome.RECLASSIFY,
            )

    def test_authority_edit_is_durably_blocked_without_worker_identity(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = classified_runtime(root)
            result = app.advance_task_admission(
                active_external_claims=(),
                provider_quota=None,
                local_budget=budget(),
                attempt_number=1,
                runner=LiveTruthRunner(comment_body=BODY + " "),
            )
            record = result.ledger.record
            self.assertEqual(record.outcome, HostedAdmissionOutcome.BLOCKED)
            self.assertIn("changed", record.reason)
            self.assertFalse(record.admission_digest)
            self.assertIsNone(record.worker_spec)

    def test_external_claim_blocks_admission(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = classified_runtime(root)
            claim = ExternalProducerClaim(
                issue_number=900,
                claimed_by="ni-da-ba",
                lane="Implementation",
                branch="manual/900",
            )
            result = app.advance_task_admission(
                active_external_claims=(claim,),
                provider_quota=None,
                local_budget=budget(),
                attempt_number=1,
                runner=LiveTruthRunner(),
            )
            record = result.ledger.record
            self.assertEqual(record.outcome, HostedAdmissionOutcome.BLOCKED)
            self.assertIn("external/manual producer", record.reason)
            self.assertIsNone(record.worker_spec)

    def test_quota_block_records_no_worker_identity(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = classified_runtime(root)
            result = app.advance_task_admission(
                active_external_claims=(),
                provider_quota=ProviderQuotaDecision(
                    authoritative=True,
                    allowed=False,
                    block_kind="quota",
                    retry_after_seconds=900,
                    reason="worker quota blocked",
                ),
                local_budget=budget(),
                attempt_number=1,
                runner=LiveTruthRunner(),
            )
            record = result.ledger.record
            self.assertEqual(record.outcome, HostedAdmissionOutcome.BLOCKED)
            self.assertEqual(record.reason, "worker quota blocked")
            self.assertIsNone(record.worker_spec)
            self.assertFalse(record.consume_attempt)

    def test_non_dispatch_proposal_records_non_dispatch(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = classified_runtime(
                root,
                response='{"decision":"NOOP","reason":"nothing executable"}',
            )
            result = app.advance_task_admission(
                active_external_claims=(),
                provider_quota=None,
                local_budget=budget(),
                attempt_number=1,
                runner=LiveTruthRunner(),
            )
            record = result.ledger.record
            self.assertEqual(record.outcome, HostedAdmissionOutcome.NOT_DISPATCH)
            self.assertIsNone(record.worker_spec)

    def test_scope_widening_is_blocked(self):
        response = json.dumps(
            {
                "decision": "DISPATCH",
                "lane": "Implementation",
                "objective": "Implement bounded feature",
                "stop_boundary": "merge boundary",
                "worker_tier": "LUNA",
                "allowed_paths": ["scripts/**"],
                "reason": "attempted widening",
            }
        )
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = classified_runtime(root, response=response)
            result = app.advance_task_admission(
                active_external_claims=(),
                provider_quota=None,
                local_budget=budget(),
                attempt_number=1,
                runner=LiveTruthRunner(),
            )
            record = result.ledger.record
            self.assertEqual(record.outcome, HostedAdmissionOutcome.BLOCKED)
            self.assertIn("widens repository authority", record.reason)
            self.assertIsNone(record.worker_spec)


class HostedAdmissionPreconditionsTest(unittest.TestCase):
    def test_classifier_must_be_complete(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = runtime(root)
            raw, headers = signed(task_payload())
            app.handle_webhook(headers=headers, raw=raw)
            app.claim_next_task_plan()
            app.advance_task_plan_preflight(runner=LiveTruthRunner())
            result = app.advance_task_admission(
                active_external_claims=(),
                provider_quota=None,
                local_budget=budget(),
                attempt_number=1,
                runner=LiveTruthRunner(),
            )
            self.assertEqual(
                result.disposition,
                HostedAdmissionDisposition.CLASSIFIER_NOT_READY,
            )
            self.assertIsNone(result.ledger.record)

    def test_remote_unavailability_waits_without_record(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = classified_runtime(root)

            def unavailable(args, **kwargs):
                import subprocess
                raise subprocess.CalledProcessError(1, args, stderr="offline")

            result = app.advance_task_admission(
                active_external_claims=(),
                provider_quota=None,
                local_budget=budget(),
                attempt_number=1,
                runner=unavailable,
            )
            self.assertEqual(result.disposition, HostedAdmissionDisposition.WAIT_REMOTE)
            self.assertIsNone(result.ledger.record)


class PersistenceCorruptionTest(unittest.TestCase):
    def test_tampered_worker_or_attempt_identity_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = classified_runtime(root)
            result = app.advance_task_admission(
                active_external_claims=(),
                provider_quota=None,
                local_budget=budget(),
                attempt_number=1,
                runner=LiveTruthRunner(),
            )
            raw = result.ledger.as_dict()

            bad_branch = copy.deepcopy(raw)
            bad_branch["record"]["worker_spec"]["branch"] = "codex/tampered"
            with self.assertRaises(ValueError):
                HostedAdmissionLedger.from_mapping(bad_branch)

            bad_attempt = copy.deepcopy(raw)
            bad_attempt["record"]["attempt"]["attempt_id"] = "f" * 64
            with self.assertRaises(ValueError):
                HostedAdmissionLedger.from_mapping(bad_attempt)

            partial = copy.deepcopy(raw)
            partial["record"]["worker_spec"] = None
            with self.assertRaises(ValueError):
                HostedAdmissionLedger.from_mapping(partial)

            bad_digest = copy.deepcopy(raw)
            bad_digest["record"]["admission_digest"] = "0" * 64
            with self.assertRaises(ValueError):
                HostedAdmissionLedger.from_mapping(bad_digest)


class CapabilityBoundaryTest(unittest.TestCase):
    def test_admission_source_has_no_worker_execution_or_mutation_surface(self):
        root = Path(__file__).resolve().parent
        source = (root / "v2/hosted_admission.py").read_text(encoding="utf-8")
        for token in (
            "advance_worker_run",
            "WorkerProvider",
            "WriterFence",
            "WorkspaceCommit",
            "advance_ordinary_pipeline",
            "git push",
            "gh pr",
            "gh issue",
            "ordinary_effect",
            "remote_factory",
        ):
            self.assertNotIn(token, source)

        runtime_source = (root / "platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        self.assertNotIn("advance_worker_run", runtime_source)
        self.assertNotIn("WriterFence", runtime_source)


if __name__ == "__main__":
    unittest.main()
