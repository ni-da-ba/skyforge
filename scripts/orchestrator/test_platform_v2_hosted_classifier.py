from __future__ import annotations

import tempfile
from pathlib import Path
import unittest

import platform_v2_hosted_runtime as hosted
from test_platform_v2_hosted_runtime import SECRET, write_legacy
from test_platform_v2_hosted_task_preflight import LiveTruthRunner, signed, task_payload
from v2.classifier_provider import (
    ClassifierProviderConfig,
    ClassifierProviderError,
    ClassifierRunStatus,
    ClassifierRunStore,
    parse_classifier_response,
)
from v2.hosted_classifier import (
    HostedClassifierDisposition,
    advance_hosted_classifier_proposal,
)
from v2.hosted_task_plan import HostedTaskPlanLedger
from v2.quota import LocalBudgetObservation, ProviderQuotaDecision


class FakeProvider:
    def __init__(self, *, response=None, error=None):
        self.calls = 0
        self.response = response or '{"decision":"NOOP","reason":"bounded proposal"}'
        self.error = error

    def classify(self, *, request, root, config):
        self.calls += 1
        if self.error is not None:
            raise self.error
        return self.response, parse_classifier_response(self.response)


class InjectedClassifierCrash(BaseException):
    pass


class CrashProvider:
    def __init__(self):
        self.calls = 0

    def classify(self, *, request, root, config):
        self.calls += 1
        raise InjectedClassifierCrash("controller died during classifier call")


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


def ready_runtime(root: Path):
    app = runtime(root)
    raw, headers = signed(task_payload())
    status, response = app.handle_webhook(headers=headers, raw=raw)
    assert status == 202 and response["task_authority_recorded"]
    claimed = app.claim_next_task_plan()
    assert claimed.disposition.value == "CLAIMED"
    ready = app.advance_task_plan_preflight(runner=LiveTruthRunner())
    assert ready.disposition.value == "READY_FOR_CLASSIFIER"
    return app, ready.ledger


def local_budget(*, used=0, limit=10):
    return LocalBudgetObservation(calls_used=used, daily_limit=limit)


class HostedClassifierBoundaryTest(unittest.TestCase):
    def test_no_plan_or_non_ready_plan_never_calls_provider(self):
        provider = FakeProvider()
        result = advance_hosted_classifier_proposal(
            plan_ledger=HostedTaskPlanLedger(),
            root=Path("."),
            provider=provider,
            provider_quota=None,
            local_budget=local_budget(),
            config=ClassifierProviderConfig("fixture", "low"),
        )
        self.assertEqual(result.disposition, HostedClassifierDisposition.NO_ACTIVE_PLAN)
        self.assertEqual(provider.calls, 0)

        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = runtime(root)
            raw, headers = signed(task_payload())
            status, _ = app.handle_webhook(headers=headers, raw=raw)
            self.assertEqual(status, 202)
            claimed = app.claim_next_task_plan()
            result = advance_hosted_classifier_proposal(
                plan_ledger=claimed.ledger,
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=local_budget(),
                config=ClassifierProviderConfig("fixture", "low"),
            )
            self.assertEqual(result.disposition, HostedClassifierDisposition.PLAN_NOT_READY)
            self.assertEqual(provider.calls, 0)

    def test_quota_blocks_before_provider_spend(self):
        cases = [
            (
                ProviderQuotaDecision(
                    authoritative=True,
                    allowed=False,
                    block_kind="quota",
                    retry_after_seconds=900,
                    reason="provider says no",
                ),
                local_budget(),
            ),
            (None, local_budget(used=10, limit=10)),
        ]
        for provider_quota, budget in cases:
            with self.subTest(provider_quota=provider_quota), tempfile.TemporaryDirectory() as td:
                root = Path(td)
                _, plan = ready_runtime(root)
                provider = FakeProvider()
                result = advance_hosted_classifier_proposal(
                    plan_ledger=plan,
                    root=root,
                    provider=provider,
                    provider_quota=provider_quota,
                    local_budget=budget,
                    config=ClassifierProviderConfig("fixture", "low"),
                )
                self.assertEqual(
                    result.disposition,
                    HostedClassifierDisposition.QUOTA_BLOCKED,
                )
                self.assertEqual(provider.calls, 0)
                self.assertEqual(
                    len(ClassifierRunStore.for_root(root).load().records),
                    0,
                )

    def test_one_call_success_is_durable_and_restart_does_not_recall(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, plan = ready_runtime(root)
            provider = FakeProvider()
            kwargs = dict(
                plan_ledger=plan,
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=local_budget(),
                config=ClassifierProviderConfig("fixture", "low"),
            )
            first = advance_hosted_classifier_proposal(**kwargs)
            self.assertEqual(first.disposition, HostedClassifierDisposition.COMPLETE)
            self.assertEqual(provider.calls, 1)
            self.assertIsNotNone(first.classifier.record.decision)

            second = advance_hosted_classifier_proposal(**kwargs)
            self.assertEqual(
                second.disposition,
                HostedClassifierDisposition.ALREADY_COMPLETE,
            )
            self.assertEqual(provider.calls, 1)
            self.assertEqual(second.run_id, first.run_id)

    def test_crash_during_provider_becomes_recovery_required_without_second_call(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, plan = ready_runtime(root)
            provider = CrashProvider()
            kwargs = dict(
                plan_ledger=plan,
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=local_budget(),
                config=ClassifierProviderConfig("fixture", "low"),
            )
            with self.assertRaises(InjectedClassifierCrash):
                advance_hosted_classifier_proposal(**kwargs)
            request = plan.active.seed.classifier_request
            self.assertEqual(
                ClassifierRunStore.for_root(root).load().get(request.request_id).status,
                ClassifierRunStatus.RUNNING,
            )
            restarted = advance_hosted_classifier_proposal(**kwargs)
            self.assertEqual(
                restarted.disposition,
                HostedClassifierDisposition.RECOVERY_REQUIRED,
            )
            self.assertEqual(provider.calls, 1)
            self.assertEqual(restarted.classifier.record.status, ClassifierRunStatus.INTERRUPTED)

    def test_provider_failure_is_durable_and_not_auto_retried(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, plan = ready_runtime(root)
            provider = FakeProvider(
                error=ClassifierProviderError("provider_capacity", 600, "capacity")
            )
            kwargs = dict(
                plan_ledger=plan,
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=local_budget(),
                config=ClassifierProviderConfig("fixture", "low"),
            )
            first = advance_hosted_classifier_proposal(**kwargs)
            second = advance_hosted_classifier_proposal(**kwargs)
            self.assertEqual(first.disposition, HostedClassifierDisposition.FAILED)
            self.assertEqual(second.disposition, HostedClassifierDisposition.FAILED)
            self.assertEqual(provider.calls, 1)
            self.assertEqual(first.classifier.record.failure_kind, "provider_capacity")

    def test_provider_config_drift_for_same_plan_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, plan = ready_runtime(root)
            provider = FakeProvider()
            advance_hosted_classifier_proposal(
                plan_ledger=plan,
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=local_budget(),
                config=ClassifierProviderConfig("model-a", "low"),
            )
            with self.assertRaisesRegex(ValueError, "identity drifted"):
                advance_hosted_classifier_proposal(
                    plan_ledger=plan,
                    root=root,
                    provider=provider,
                    provider_quota=None,
                    local_budget=local_budget(),
                    config=ClassifierProviderConfig("model-b", "low"),
                )
            self.assertEqual(provider.calls, 1)


class HostedRuntimeClassifierIntegrationTest(unittest.TestCase):
    def test_explicit_runtime_method_calls_provider_once_and_health_remains_nonautomatic(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app, _ = ready_runtime(root)
            provider = FakeProvider()
            result = app.advance_task_classifier(
                provider=provider,
                provider_quota=None,
                local_budget=local_budget(),
                config=ClassifierProviderConfig("fixture", "low"),
            )
            self.assertEqual(result.disposition, HostedClassifierDisposition.COMPLETE)
            self.assertEqual(provider.calls, 1)

            health = app.health_snapshot()
            self.assertTrue(health["explicit_classifier_proposal_enabled"])
            self.assertFalse(health["automatic_classifier_execution_enabled"])
            self.assertFalse(health["mutation_authority"])
            self.assertFalse(health["worker_dispatch_enabled"])
            self.assertFalse(health["remote_effect_execution_enabled"])

    def test_webhook_and_preflight_never_invoke_provider_implicitly(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = runtime(root)
            provider = FakeProvider()
            raw, headers = signed(task_payload())
            status, _ = app.handle_webhook(headers=headers, raw=raw)
            self.assertEqual(status, 202)
            app.claim_next_task_plan()
            app.advance_task_plan_preflight(runner=LiveTruthRunner())
            self.assertEqual(provider.calls, 0)
            self.assertEqual(
                len(ClassifierRunStore.for_root(root).load().records),
                0,
            )


class CapabilityBoundaryTest(unittest.TestCase):
    def test_hosted_classifier_source_has_no_worker_or_mutation_surface(self):
        root = Path(__file__).resolve().parent
        source = (root / "v2/hosted_classifier.py").read_text(encoding="utf-8")
        for token in (
            "advance_ordinary_pipeline",
            "admit_dispatch",
            "WorkerProvider",
            "WriterFence",
            "git push",
            "gh pr",
            "gh issue",
            "ordinary_effect",
            "workspace",
        ):
            self.assertNotIn(token, source)

        runtime_source = (root / "platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        self.assertNotIn("CodexClassifierProvider", runtime_source)


if __name__ == "__main__":
    unittest.main()
