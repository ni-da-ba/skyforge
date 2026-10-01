from __future__ import annotations

from pathlib import Path
import tempfile
import threading
import time
import unittest
from unittest import mock

from v2.hosted_budget import HostedBudgetStore
from v2.hosted_execution_driver import (
    BudgetedClassifierProvider,
    BudgetedWorkerProvider,
    HostedExecutionDriver,
    ProductionHostedDependencyFactory,
    provider_quota_decision,
)
from v2.hosted_execution_runtime import (
    HostedExecutionAdvanceDisposition,
    HostedExecutionAdvanceResult,
)
from v2.worker_provider import WorkerProviderConfig, WorkerTier


DAY = "2026-09-18"


def legacy_budget(**overrides):
    value = {
        "budget_day": DAY,
        "classifier_calls_today": 7,
        "luna_worker_calls_today": 2,
        "worker_calls_today": 3,
        "luna_daily_limit_override": 30,
        "terra_daily_limit_override": 6,
    }
    value.update(overrides)
    return value


class HostedBudgetMigrationTest(unittest.TestCase):
    def test_first_v2_budget_seed_preserves_same_day_legacy_usage(self):
        with tempfile.TemporaryDirectory() as td:
            store = HostedBudgetStore.for_root(Path(td))
            seeded = store.ensure_day(day=DAY, legacy_state=legacy_budget())

            self.assertEqual(seeded.classifier_calls, 7)
            self.assertEqual(seeded.luna_worker_calls, 2)
            self.assertEqual(seeded.terra_worker_calls, 3)
            self.assertEqual(seeded.luna_calls, 9)
            self.assertTrue(seeded.seeded_from_legacy)
            self.assertTrue(seeded.legacy_seed_digest)

            restarted = HostedBudgetStore.for_root(Path(td)).ensure_day(
                day=DAY,
                legacy_state=legacy_budget(classifier_calls_today=99),
            )
            self.assertEqual(restarted, seeded)

    def test_utc_day_rollover_resets_without_reseeding_legacy(self):
        with tempfile.TemporaryDirectory() as td:
            store = HostedBudgetStore.for_root(Path(td))
            store.ensure_day(day=DAY, legacy_state=legacy_budget())
            rolled = store.ensure_day(
                day="2026-09-19",
                legacy_state=legacy_budget(budget_day="2026-09-19"),
            )
            self.assertEqual(rolled.classifier_calls, 0)
            self.assertEqual(rolled.luna_worker_calls, 0)
            self.assertEqual(rolled.terra_worker_calls, 0)
            self.assertFalse(rolled.seeded_from_legacy)

    def test_local_observations_preserve_shared_luna_bucket(self):
        with tempfile.TemporaryDirectory() as td:
            store = HostedBudgetStore.for_root(Path(td))
            ledger = store.ensure_day(day=DAY, legacy_state=legacy_budget())
            self.assertEqual(
                ledger.observation("classifier", luna_limit=30, terra_limit=6).calls_used,
                9,
            )
            self.assertEqual(
                ledger.observation("luna_worker", luna_limit=30, terra_limit=6).calls_used,
                9,
            )
            self.assertEqual(
                ledger.observation("terra_worker", luna_limit=30, terra_limit=6).calls_used,
                3,
            )


class _ClassifierDelegate:
    def __init__(self, store: HostedBudgetStore, fail: bool = False):
        self.store = store
        self.fail = fail
        self.observed = None

    def classify(self, *, request, root, config):
        self.observed = self.store.load().classifier_calls
        if self.fail:
            raise RuntimeError("injected classifier failure")
        return "{}", object()


class _WorkerDelegate:
    def __init__(self, store: HostedBudgetStore, fail: bool = False):
        self.store = store
        self.fail = fail
        self.observed = None

    def run(self, *, spec, worktree, config):
        ledger = self.store.load()
        self.observed = (
            ledger.luna_worker_calls
            if config.tier is WorkerTier.LUNA
            else ledger.terra_worker_calls
        )
        if self.fail:
            raise RuntimeError("injected worker failure")
        return "done"


class ProviderAttemptAccountingTest(unittest.TestCase):
    def test_classifier_attempt_is_durable_before_provider_call_even_on_failure(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            store = HostedBudgetStore.for_root(root)
            store.ensure_day(day=DAY, legacy_state={})
            delegate = _ClassifierDelegate(store, fail=True)
            provider = BudgetedClassifierProvider(
                root=root,
                store=store,
                delegate=delegate,
                day_reader=lambda: DAY,
                legacy_reader=lambda _root: {},
            )
            with self.assertRaisesRegex(RuntimeError, "injected"):
                provider.classify(request=object(), root=root, config=object())
            self.assertEqual(delegate.observed, 1)
            self.assertEqual(store.load().classifier_calls, 1)

    def test_worker_tier_attempt_is_recorded_in_correct_bucket(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            store = HostedBudgetStore.for_root(root)
            store.ensure_day(day=DAY, legacy_state={})
            delegate = _WorkerDelegate(store)
            provider = BudgetedWorkerProvider(
                root=root,
                store=store,
                delegate=delegate,
                day_reader=lambda: DAY,
                legacy_reader=lambda _root: {},
            )
            cfg = WorkerProviderConfig(WorkerTier.LUNA, "fixture", "low")
            provider.run(spec=object(), worktree=root, config=cfg)
            self.assertEqual(delegate.observed, 1)
            self.assertEqual(store.load().luna_worker_calls, 1)
            self.assertEqual(store.load().terra_worker_calls, 0)


class ProviderQuotaIntegrationTest(unittest.TestCase):
    def test_authoritative_provider_mapping_is_preserved(self):
        with mock.patch(
            "v2.hosted_execution_driver.quota_governor.evaluate_quota",
            return_value={
                "authoritative": True,
                "allowed": False,
                "phase": "weekly_catchup",
                "block_kind": "quota_pacing",
                "retry_after_seconds": 600,
                "reason": "fixture",
            },
        ):
            decision = provider_quota_decision(
                environ={},
                snapshot_reader=lambda: {"limits": []},
            )
        self.assertIsNotNone(decision)
        self.assertTrue(decision.authoritative)
        self.assertFalse(decision.allowed)
        self.assertEqual(decision.retry_after_seconds, 600)

    def test_quota_probe_failure_falls_back_to_local_budget(self):
        def fail():
            raise RuntimeError("quota unavailable")

        self.assertIsNone(provider_quota_decision(environ={}, snapshot_reader=fail))


class _FakeDependencies:
    def __init__(self):
        self.builds = 0
        self.store = mock.Mock()
        self.legacy_reader = lambda _root: {}
        self.environ = {}

    def build(self, *, defer_worker_execution=False):
        self.builds += 1
        return object()

    def budget_snapshot(self):
        raise AssertionError("unused")


class _FakeRuntime:
    production_execution_enabled = True

    def __init__(self, root: Path, results):
        self.root = root
        self.results = list(results)
        self.calls = 0
        self.called = threading.Event()

    def advance_one_execution_step(self, _deps):
        self.calls += 1
        self.called.set()
        value = self.results[min(self.calls - 1, len(self.results) - 1)]
        if isinstance(value, Exception):
            raise value
        return value


def result(disposition):
    return HostedExecutionAdvanceResult(
        disposition,
        disposition.value,
        "a" * 64,
        "fixture",
    )


class HostedExecutionDriverTest(unittest.TestCase):
    def test_driver_requires_enabled_r5c24_gate(self):
        runtime = mock.Mock(production_execution_enabled=False)
        with self.assertRaisesRegex(ValueError, "requires enabled"):
            HostedExecutionDriver(runtime=runtime, dependencies=mock.Mock())

    def test_one_wake_drains_only_while_boundary_policy_allows(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = _FakeRuntime(
                Path(td),
                [
                    result(HostedExecutionAdvanceDisposition.TASK_CLAIMED),
                    result(HostedExecutionAdvanceDisposition.PREFLIGHT_ADVANCED),
                    result(HostedExecutionAdvanceDisposition.IDLE),
                ],
            )
            deps = _FakeDependencies()
            driver = HostedExecutionDriver(
                runtime=runtime,
                dependencies=deps,
                periodic_seconds=60,
                max_boundaries_per_wake=8,
            )
            with mock.patch(
                "v2.hosted_execution_driver.should_continue_after",
                side_effect=[True, True, False],
            ):
                driver._drain_one_wake()
            self.assertEqual(runtime.calls, 3)
            self.assertEqual(deps.builds, 3)
            self.assertEqual(driver.snapshot().advance_count, 3)

    def test_one_wake_drains_objective_scoping_advances_until_real_stop(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = _FakeRuntime(
                Path(td),
                [
                    result(HostedExecutionAdvanceDisposition.OBJECTIVE_SCOPING_ADVANCED),
                    result(HostedExecutionAdvanceDisposition.OBJECTIVE_SCOPING_ADVANCED),
                    result(HostedExecutionAdvanceDisposition.IDLE),
                ],
            )
            deps = _FakeDependencies()
            driver = HostedExecutionDriver(
                runtime=runtime,
                dependencies=deps,
                periodic_seconds=60,
                max_boundaries_per_wake=8,
            )
            driver._drain_one_wake()
            self.assertEqual(runtime.calls, 3)
            self.assertEqual(deps.builds, 3)
            self.assertEqual(driver.snapshot().advance_count, 3)
            self.assertEqual(
                driver.snapshot().last_disposition,
                HostedExecutionAdvanceDisposition.IDLE.value,
            )

    def test_one_wake_continues_after_classifier_retry_acceptance(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = _FakeRuntime(
                Path(td),
                [
                    result(
                        HostedExecutionAdvanceDisposition.CLASSIFIER_RETRY_ACCEPTED
                    ),
                    result(HostedExecutionAdvanceDisposition.IDLE),
                ],
            )
            deps = _FakeDependencies()
            driver = HostedExecutionDriver(
                runtime=runtime,
                dependencies=deps,
                periodic_seconds=60,
                max_boundaries_per_wake=8,
            )
            driver._drain_one_wake()
            self.assertEqual(runtime.calls, 2)
            self.assertEqual(deps.builds, 2)
            self.assertEqual(
                driver.snapshot().last_disposition,
                HostedExecutionAdvanceDisposition.IDLE.value,
            )

    def test_one_wake_drains_program_advances_until_real_stop(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = _FakeRuntime(
                Path(td),
                [
                    result(HostedExecutionAdvanceDisposition.PROGRAM_ADVANCED),
                    result(HostedExecutionAdvanceDisposition.PROGRAM_ADVANCED),
                    result(HostedExecutionAdvanceDisposition.IDLE),
                ],
            )
            deps = _FakeDependencies()
            driver = HostedExecutionDriver(
                runtime=runtime,
                dependencies=deps,
                periodic_seconds=60,
                max_boundaries_per_wake=8,
            )
            driver._drain_one_wake()
            self.assertEqual(runtime.calls, 3)
            self.assertEqual(deps.builds, 3)
            self.assertEqual(
                driver.snapshot().last_disposition,
                HostedExecutionAdvanceDisposition.IDLE.value,
            )

    def test_one_wake_launches_two_independent_runnable_workers(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runnable_one = HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.WORKER_RUNNABLE,
                "first worker runnable",
                "a" * 64,
                "1" * 64,
            )
            runnable_two = HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.WORKER_RUNNABLE,
                "second worker runnable",
                "a" * 64,
                "2" * 64,
            )
            runtime = _FakeRuntime(
                root,
                [
                    runnable_one,
                    runnable_two,
                    result(HostedExecutionAdvanceDisposition.IDLE),
                ],
            )
            deps = _FakeDependencies()
            driver = HostedExecutionDriver(
                runtime=runtime,
                dependencies=deps,
                periodic_seconds=60,
                max_boundaries_per_wake=8,
            )
            launched_one = HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.WORKER_EXECUTING,
                "first launched",
                "a" * 64,
                "1" * 64,
            )
            launched_two = HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.WORKER_EXECUTING,
                "second launched",
                "a" * 64,
                "2" * 64,
            )
            with mock.patch.object(
                driver,
                "_launch_runnable_worker",
                side_effect=[launched_one, launched_two],
            ) as launch:
                driver._drain_one_wake()

            self.assertEqual(
                [call.args[0] for call in launch.call_args_list],
                ["1" * 64, "2" * 64],
            )
            self.assertEqual(runtime.calls, 3)
            self.assertEqual(deps.builds, 3)
            self.assertEqual(
                driver.snapshot().last_disposition,
                HostedExecutionAdvanceDisposition.IDLE.value,
            )

    def test_background_start_wakes_once_and_idle_does_not_spin(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = _FakeRuntime(
                Path(td),
                [result(HostedExecutionAdvanceDisposition.IDLE)],
            )
            driver = HostedExecutionDriver(
                runtime=runtime,
                dependencies=_FakeDependencies(),
                periodic_seconds=60,
            )
            driver.start()
            self.assertTrue(runtime.called.wait(2))
            time.sleep(0.05)
            driver.stop()
            self.assertEqual(runtime.calls, 1)
            self.assertEqual(driver.snapshot().last_disposition, "IDLE")
            self.assertFalse(driver.snapshot().running)

    def test_driver_failure_stops_current_wake_without_retry_spin(self):
        with tempfile.TemporaryDirectory() as td:
            runtime = _FakeRuntime(Path(td), [RuntimeError("boom")])
            driver = HostedExecutionDriver(
                runtime=runtime,
                dependencies=_FakeDependencies(),
                periodic_seconds=60,
            )
            driver.start()
            self.assertTrue(runtime.called.wait(2))
            time.sleep(0.05)
            driver.stop()
            self.assertEqual(runtime.calls, 1)
            self.assertIn("RuntimeError: boom", driver.snapshot().last_error)


class DependencyFactoryBudgetTest(unittest.TestCase):
    def test_factory_uses_legacy_budget_overrides_and_seed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            factory = ProductionHostedDependencyFactory(
                root=root,
                environ={},
                quota_reader=lambda: None,
                day_reader=lambda: DAY,
                legacy_reader=lambda _root: legacy_budget(),
                classifier_provider=object(),
                worker_provider=object(),
            )
            ledger, luna_limit, terra_limit = factory.budget_snapshot()
            self.assertEqual(ledger.luna_calls, 9)
            self.assertEqual(ledger.terra_worker_calls, 3)
            self.assertEqual(luna_limit, 30)
            self.assertEqual(terra_limit, 6)


if __name__ == "__main__":
    unittest.main()
