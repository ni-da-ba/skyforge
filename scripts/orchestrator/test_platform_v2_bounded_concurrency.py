from __future__ import annotations

from dataclasses import replace
from pathlib import Path
import tempfile
import threading
import time
import unittest
from unittest import mock

from test_platform_v2_multi_workflow_authority import BASE, HEAD, admitted, plan
from v2.concurrency_claims import (
    ConcurrencyClaimDisposition,
    ConcurrencyClaimLedger,
    ConcurrencyClaimStore,
    acquire_concurrency_claim,
)
from v2.dormant_worker import DormantWorkerDisposition, DormantWorkerResult
from v2.hosted_admission import HostedAdmissionLedger, HostedAdmissionStore
from v2.hosted_budget import HostedBudgetStore
from v2.hosted_execution_driver import HostedExecutionDriver
from v2.hosted_execution_runtime import HostedExecutionDependencies, select_hosted_execution_plan
from v2.hosted_policy import HOSTED_WORKER_CONCURRENCY_LIMIT
from v2.hosted_task_plan import HostedTaskPlanLedger, HostedTaskPlanStore
from v2.hosted_worker_scheduler import (
    HostedWorkerReservationDisposition,
    HostedWorkerScheduleState,
    HostedWorkerSchedulerStore,
    reconcile_hosted_worker_scheduler_after_restart,
    reserve_hosted_worker,
    update_hosted_worker_schedule,
)
from v2.ordinary_effects import OrdinaryMutationScope
from v2.ordinary_pipeline import (
    OrdinaryPipelineLedger,
    OrdinaryPipelineRecord,
    OrdinaryPipelineStage,
    OrdinaryPipelineStore,
)
from v2.ordinary_service import ManagedOrdinaryHandoff
from v2.quota import LocalBudgetObservation
from v2.worker_provider import (
    WorkerAdvanceDisposition,
    WorkerProviderConfig,
    WorkerRunLedger,
    WorkerRunRecord,
    WorkerRunStatus,
    WorkerRunStore,
    WorkerTier,
    advance_worker_run,
)


def install(root: Path, specs):
    admissions = HostedAdmissionLedger()
    claims = ConcurrencyClaimLedger()
    values = {}
    for name, issue, scope in specs:
        _event, plan_record = plan(name, issue)
        admission = admitted(name, plan_record, scope)
        values[name] = admission
        admissions = admissions.put(admission)
        result = acquire_concurrency_claim(claims, admission.worker_spec)
        claims = result.ledger
    HostedAdmissionStore.for_root(root).save(admissions)
    ConcurrencyClaimStore.for_root(root).save(claims)
    return values, claims


class HostedWorkerSchedulerTest(unittest.TestCase):
    def test_two_disjoint_attempts_reserve_and_third_waits_at_exact_limit(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            values, _claims = install(
                root,
                (
                    ("a", 1001, "docs/a/**"),
                    ("b", 1002, "docs/b/**"),
                    ("c", 1003, "docs/c/**"),
                ),
            )
            a = reserve_hosted_worker(
                root=root, attempt_id=values["a"].attempt.attempt_id
            )
            b = reserve_hosted_worker(
                root=root, attempt_id=values["b"].attempt.attempt_id
            )
            c = reserve_hosted_worker(
                root=root, attempt_id=values["c"].attempt.attempt_id
            )
            self.assertEqual(HOSTED_WORKER_CONCURRENCY_LIMIT, 2)
            self.assertEqual(a.disposition, HostedWorkerReservationDisposition.RESERVED)
            self.assertEqual(b.disposition, HostedWorkerReservationDisposition.RESERVED)
            self.assertEqual(c.disposition, HostedWorkerReservationDisposition.LIMIT_REACHED)
            ledger = HostedWorkerSchedulerStore.for_root(root).load()
            self.assertEqual(len(ledger.executing), 2)
            self.assertEqual(
                ledger.get(values["c"].attempt.attempt_id).state,
                HostedWorkerScheduleState.WAIT_LIMIT,
            )

            update_hosted_worker_schedule(
                root=root,
                attempt_id=values["a"].attempt.attempt_id,
                state=HostedWorkerScheduleState.COMPLETED,
                reason="fixture A complete",
            )
            c2 = reserve_hosted_worker(
                root=root, attempt_id=values["c"].attempt.attempt_id
            )
            self.assertEqual(c2.disposition, HostedWorkerReservationDisposition.RESERVED)
            self.assertEqual(len(c2.ledger.executing), 2)

    def test_overlap_waits_on_exact_claim_without_starving_disjoint_work(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _event_a, plan_a = plan("a", 1001)
            _event_b, plan_b = plan("b", 1002)
            _event_d, plan_d = plan("d", 1004)
            a = admitted("a", plan_a, "docs/shared/**")
            b = admitted("b", plan_b, "docs/shared/file.txt")
            d_value = admitted("d", plan_d, "docs/d/**")
            HostedAdmissionStore.for_root(root).save(
                HostedAdmissionLedger((a, b, d_value))
            )

            claims = acquire_concurrency_claim(
                ConcurrencyClaimLedger(), a.worker_spec
            )
            blocked = acquire_concurrency_claim(claims.ledger, b.worker_spec)
            self.assertEqual(
                blocked.decision.disposition,
                ConcurrencyClaimDisposition.CONFLICT,
            )
            disjoint = acquire_concurrency_claim(blocked.ledger, d_value.worker_spec)
            self.assertEqual(
                disjoint.decision.disposition,
                ConcurrencyClaimDisposition.ADMIT,
            )
            ConcurrencyClaimStore.for_root(root).save(disjoint.ledger)

            first = reserve_hosted_worker(root=root, attempt_id=a.attempt.attempt_id)
            waiting = reserve_hosted_worker(root=root, attempt_id=b.attempt.attempt_id)
            other = reserve_hosted_worker(
                root=root, attempt_id=d_value.attempt.attempt_id
            )
            self.assertEqual(first.disposition, HostedWorkerReservationDisposition.RESERVED)
            self.assertEqual(waiting.disposition, HostedWorkerReservationDisposition.WAIT_CLAIM)
            self.assertEqual(other.disposition, HostedWorkerReservationDisposition.RESERVED)
            self.assertEqual(len(other.ledger.executing), 2)
            self.assertEqual(
                waiting.record.state,
                HostedWorkerScheduleState.WAIT_CLAIM,
            )

    def test_restart_fences_two_running_attempts_without_provider_replay(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            values, _claims = install(
                root,
                (
                    ("a", 1001, "docs/a/**"),
                    ("b", 1002, "docs/b/**"),
                ),
            )
            reserve_hosted_worker(
                root=root, attempt_id=values["a"].attempt.attempt_id
            )
            reserve_hosted_worker(
                root=root, attempt_id=values["b"].attempt.attempt_id
            )

            records = []
            for name in ("a", "b"):
                spec = values[name].worker_spec
                records.append(
                    WorkerRunRecord(
                        spec=spec,
                        worktree=str(root / name),
                        config=WorkerProviderConfig(
                            spec.tier, f"fixture-{name}", "low"
                        ),
                        status=WorkerRunStatus.RUNNING,
                    )
                )
            WorkerRunStore.for_root(root).save(WorkerRunLedger(tuple(records)))

            reconciled = reconcile_hosted_worker_scheduler_after_restart(root=root)
            self.assertEqual(len(reconciled.executing), 0)
            for name in ("a", "b"):
                attempt_id = values[name].attempt.attempt_id
                self.assertEqual(
                    reconciled.get(attempt_id).state,
                    HostedWorkerScheduleState.RECOVERY_REQUIRED,
                )
                self.assertEqual(
                    WorkerRunStore.for_root(root)
                    .load()
                    .find_attempt(attempt_id)
                    .status,
                    WorkerRunStatus.INTERRUPTED,
                )


class HostedPlanSelectionConcurrencyTest(unittest.TestCase):
    def test_runnable_claim_outranks_handoff_ready_plan_even_when_plan_id_sorts_later(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            candidates = []
            for offset, name in enumerate(("a", "b", "c", "d")):
                _event, plan_record = plan(name, 1101 + offset)
                candidates.append((plan_record.plan_id, name, plan_record))
            candidates.sort(key=lambda value: value[0])
            _low_id, handoff_name, handoff_plan = candidates[0]
            _high_id, runnable_name, runnable_plan = candidates[-1]

            handoff = admitted(handoff_name, handoff_plan, "docs/handoff/**")
            runnable = admitted(runnable_name, runnable_plan, "docs/runnable/**")
            HostedTaskPlanStore.for_root(root).save(
                HostedTaskPlanLedger((handoff_plan, runnable_plan))
            )
            HostedAdmissionStore.for_root(root).save(
                HostedAdmissionLedger((handoff, runnable))
            )

            claims = acquire_concurrency_claim(
                ConcurrencyClaimLedger(), handoff.worker_spec
            )
            claims = acquire_concurrency_claim(claims.ledger, runnable.worker_spec)
            self.assertEqual(
                claims.decision.disposition,
                ConcurrencyClaimDisposition.ADMIT,
            )
            ConcurrencyClaimStore.for_root(root).save(claims.ledger)

            config = WorkerProviderConfig(
                handoff.worker_spec.tier, "fixture", "low"
            )
            WorkerRunStore.for_root(root).save(
                WorkerRunLedger(
                    (
                        WorkerRunRecord(
                            spec=handoff.worker_spec,
                            worktree=str(root / "handoff-worktree"),
                            config=config,
                            status=WorkerRunStatus.HANDOFF_READY,
                            summary="fixture handoff ready",
                        ),
                    )
                )
            )

            selected = select_hosted_execution_plan(root=root)
            self.assertIsNotNone(selected)
            self.assertEqual(selected.plan_id, runnable_plan.plan_id)

    def test_handoff_without_managed_pr_outranks_lower_id_handoff_already_parked_at_pr(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            candidates = []
            for offset, name in enumerate(("a", "b", "c", "d")):
                _event, plan_record = plan(name, 1201 + offset)
                candidates.append((plan_record.plan_id, name, plan_record))
            candidates.sort(key=lambda value: value[0])
            _low_id, parked_name, parked_plan = candidates[0]
            _high_id, fresh_name, fresh_plan = candidates[-1]

            parked = admitted(parked_name, parked_plan, "docs/parked/**")
            fresh = admitted(fresh_name, fresh_plan, "docs/fresh/**")
            HostedTaskPlanStore.for_root(root).save(
                HostedTaskPlanLedger((parked_plan, fresh_plan))
            )
            HostedAdmissionStore.for_root(root).save(
                HostedAdmissionLedger((parked, fresh))
            )

            claims = acquire_concurrency_claim(
                ConcurrencyClaimLedger(), parked.worker_spec
            )
            claims = acquire_concurrency_claim(claims.ledger, fresh.worker_spec)
            self.assertEqual(
                claims.decision.disposition,
                ConcurrencyClaimDisposition.ADMIT,
            )
            ConcurrencyClaimStore.for_root(root).save(claims.ledger)

            parked_config = WorkerProviderConfig(
                parked.worker_spec.tier, "fixture", "low"
            )
            fresh_config = WorkerProviderConfig(
                fresh.worker_spec.tier, "fixture", "low"
            )
            WorkerRunStore.for_root(root).save(
                WorkerRunLedger(
                    (
                        WorkerRunRecord(
                            spec=parked.worker_spec,
                            worktree=str(root / "parked-worktree"),
                            config=parked_config,
                            status=WorkerRunStatus.HANDOFF_READY,
                            summary="parked handoff ready",
                        ),
                        WorkerRunRecord(
                            spec=fresh.worker_spec,
                            worktree=str(root / "fresh-worktree"),
                            config=fresh_config,
                            status=WorkerRunStatus.HANDOFF_READY,
                            summary="fresh handoff ready",
                        ),
                    )
                )
            )

            parked_handoff = ManagedOrdinaryHandoff(
                task_id=parked.worker_spec.task_id,
                authority_key=parked.worker_spec.authority_key,
                task_spec_hash=parked.worker_spec.task_spec_hash,
                lane=parked.worker_spec.lane,
                scope=OrdinaryMutationScope(
                    attempt_id=parked.attempt.attempt_id,
                    repo="ni-da-ba/skyforge",
                    base_sha=BASE,
                    branch=parked.worker_spec.branch,
                    expected_head_sha=HEAD,
                    pr_title="parked fixture",
                    pr_body="parked fixture body",
                    issue_number=parked.issue_number,
                ),
                pr_number=1301,
                changed_paths=("docs/parked/result.txt",),
                auto_merge_eligible=False,
            )
            OrdinaryPipelineStore.for_root(root).save(
                OrdinaryPipelineLedger(
                    (
                        OrdinaryPipelineRecord(
                            pipeline_id="pipeline-parked",
                            classifier_request_id=parked.classifier_request_id,
                            authority_digest=parked.authority_digest,
                            stage=OrdinaryPipelineStage.COMPLETE,
                            reason="managed PR already exists",
                            task_spec_hash=parked.worker_spec.task_spec_hash,
                            attempt_id=parked.attempt.attempt_id,
                            handoff_digest=parked_handoff.digest,
                            pr_number=parked_handoff.pr_number,
                            managed_handoff=parked_handoff,
                        ),
                    )
                )
            )

            selected = select_hosted_execution_plan(root=root)
            self.assertIsNotNone(selected)
            self.assertEqual(selected.plan_id, fresh_plan.plan_id)

            duplicate = OrdinaryPipelineRecord(
                pipeline_id="pipeline-parked-duplicate",
                classifier_request_id=parked.classifier_request_id,
                authority_digest=parked.authority_digest,
                stage=OrdinaryPipelineStage.COMPLETE,
                reason="ambiguous duplicate managed PR",
                task_spec_hash=parked.worker_spec.task_spec_hash,
                attempt_id=parked.attempt.attempt_id,
                handoff_digest=parked_handoff.digest,
                pr_number=parked_handoff.pr_number,
                managed_handoff=parked_handoff,
            )
            pipeline = OrdinaryPipelineStore.for_root(root).load()
            OrdinaryPipelineStore.for_root(root).save(pipeline.put(duplicate))
            HostedTaskPlanStore.for_root(root).save(
                HostedTaskPlanLedger((parked_plan,))
            )
            HostedAdmissionStore.for_root(root).save(
                HostedAdmissionLedger((parked,))
            )
            WorkerRunStore.for_root(root).save(
                WorkerRunLedger(
                    (
                        WorkerRunRecord(
                            spec=parked.worker_spec,
                            worktree=str(root / "parked-worktree"),
                            config=parked_config,
                            status=WorkerRunStatus.HANDOFF_READY,
                            summary="parked handoff ready",
                        ),
                    )
                )
            )

            self.assertIsNone(select_hosted_execution_plan(root=root))


class SharedWorkerLedgerConcurrencyTest(unittest.TestCase):
    def test_two_provider_calls_overlap_without_lost_worker_state(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            values, _claims = install(
                root,
                (
                    ("a", 1001, "docs/a/**"),
                    ("b", 1002, "docs/b/**"),
                ),
            )
            (root / "a").mkdir()
            (root / "b").mkdir()
            barrier = threading.Barrier(2)
            active = 0
            maximum = 0
            lock = threading.Lock()

            class Provider:
                def run(self, *, spec, worktree, config):
                    nonlocal active, maximum
                    with lock:
                        active += 1
                        maximum = max(maximum, active)
                    barrier.wait(timeout=2)
                    time.sleep(0.05)
                    with lock:
                        active -= 1
                    return f"done {spec.task_id}"

            provider = Provider()
            store = WorkerRunStore.for_root(root)
            results = {}

            def run(name):
                spec = values[name].worker_spec
                results[name] = advance_worker_run(
                    spec=spec,
                    worktree=root / name,
                    store=store,
                    provider=provider,
                    config=WorkerProviderConfig(spec.tier, "fixture", "low"),
                )

            ta = threading.Thread(target=run, args=("a",))
            tb = threading.Thread(target=run, args=("b",))
            ta.start()
            tb.start()
            ta.join(3)
            tb.join(3)
            self.assertFalse(ta.is_alive())
            self.assertFalse(tb.is_alive())
            self.assertEqual(maximum, 2)
            self.assertEqual(
                {value.disposition for value in results.values()},
                {WorkerAdvanceDisposition.HANDOFF_READY},
            )
            ledger = store.load()
            self.assertEqual(len(ledger.records), 2)
            self.assertTrue(
                all(value.status is WorkerRunStatus.HANDOFF_READY for value in ledger.records)
            )

            before_b = ledger.find_attempt(values["b"].attempt.attempt_id)
            replay_a = advance_worker_run(
                spec=values["a"].worker_spec,
                worktree=root / "a",
                store=store,
                provider=provider,
                config=WorkerProviderConfig(
                    values["a"].worker_spec.tier, "fixture", "low"
                ),
            )
            self.assertEqual(
                replay_a.disposition,
                WorkerAdvanceDisposition.ALREADY_READY,
            )
            self.assertEqual(
                store.load().find_attempt(values["b"].attempt.attempt_id),
                before_b,
            )


class ExactBudgetReservationTest(unittest.TestCase):
    def test_atomic_budget_reservation_binds_exact_attempt_and_blocks_third(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            values, _claims = install(
                root,
                (
                    ("a", 1001, "docs/a/**"),
                    ("b", 1002, "docs/b/**"),
                    ("c", 1003, "docs/c/**"),
                ),
            )
            store = HostedBudgetStore.for_root(root)
            for name in ("a", "b"):
                ledger, admitted_flag = store.reserve_worker_attempt(
                    attempt_id=values[name].attempt.attempt_id,
                    kind="terra_worker",
                    day="2026-09-21",
                    daily_limit=2,
                    legacy_state={},
                )
                self.assertTrue(admitted_flag)
            ledger, admitted_flag = store.reserve_worker_attempt(
                attempt_id=values["c"].attempt.attempt_id,
                kind="terra_worker",
                day="2026-09-21",
                daily_limit=2,
                legacy_state={},
            )
            self.assertFalse(admitted_flag)
            self.assertEqual(ledger.terra_worker_calls, 2)
            self.assertEqual(
                set(dict(ledger.worker_attempts)),
                {
                    values["a"].attempt.attempt_id,
                    values["b"].attempt.attempt_id,
                },
            )


class DriverBoundedConcurrencyTest(unittest.TestCase):
    def test_driver_launches_two_worker_threads_and_third_waits(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            values, _claims = install(
                root,
                (
                    ("a", 1001, "docs/a/**"),
                    ("b", 1002, "docs/b/**"),
                    ("c", 1003, "docs/c/**"),
                ),
            )
            release = threading.Event()
            both_started = threading.Event()
            lock = threading.Lock()
            active = 0
            max_active = 0

            class Gate:
                digest = "f" * 64

            class Runtime:
                production_execution_enabled = True
                execution_gate = Gate()

                def __init__(self):
                    self.root = root

            class Dependencies:
                def __init__(self):
                    self.store = mock.Mock()
                    self.legacy_reader = lambda _root: {}
                    self.environ = {}

                def reserve_and_build_worker_attempt(self, attempt_id):
                    admission = HostedAdmissionStore.for_root(root).load().for_attempt(
                        attempt_id
                    )
                    return (
                        HostedExecutionDependencies(
                            classifier_provider=object(),
                            worker_provider=object(),
                            classifier_local_budget=LocalBudgetObservation(0, 10),
                            worker_local_budget=LocalBudgetObservation(0, 10),
                            worker_config=WorkerProviderConfig(
                                admission.worker_spec.tier, "fixture", "low"
                            ),
                        ),
                        "reserved",
                    )

                def build(self, *, defer_worker_execution=False):
                    raise AssertionError("unused in direct launch test")

            def fake_worker(**kwargs):
                nonlocal active, max_active
                attempt_id = kwargs["attempt_id"]
                with lock:
                    active += 1
                    max_active = max(max_active, active)
                    if active == 2:
                        both_started.set()
                if not release.wait(2):
                    raise RuntimeError("fixture release timeout")
                with lock:
                    active -= 1
                return DormantWorkerResult(
                    DormantWorkerDisposition.HANDOFF_READY,
                    "fixture handoff ready",
                    admission_record_id=HostedAdmissionStore.for_root(root)
                    .load()
                    .for_attempt(attempt_id)
                    .record_id,
                    worker_run_id="e" * 64,
                )

            driver = HostedExecutionDriver(
                runtime=Runtime(),
                dependencies=Dependencies(),
                periodic_seconds=60,
                max_boundaries_per_wake=8,
            )
            with mock.patch(
                "v2.hosted_execution_driver.advance_dormant_admitted_worker",
                side_effect=fake_worker,
            ):
                ra = driver._launch_runnable_worker(values["a"].attempt.attempt_id)
                rb = driver._launch_runnable_worker(values["b"].attempt.attempt_id)
                self.assertTrue(both_started.wait(2))
                rc = driver._launch_runnable_worker(values["c"].attempt.attempt_id)
                self.assertEqual(
                    ra.disposition.value,
                    "WORKER_EXECUTING",
                )
                self.assertEqual(
                    rb.disposition.value,
                    "WORKER_EXECUTING",
                )
                self.assertEqual(
                    rc.disposition.value,
                    "WORKER_RUNNABLE",
                )
                self.assertEqual(max_active, 2)
                self.assertEqual(
                    len(HostedWorkerSchedulerStore.for_root(root).load().executing),
                    2,
                )

                release.set()
                for thread in tuple(driver._worker_threads.values()):
                    thread.join(3)
                driver._reap_worker_results()
                self.assertEqual(
                    len(HostedWorkerSchedulerStore.for_root(root).load().executing),
                    0,
                )


if __name__ == "__main__":
    unittest.main()
