"""Gated production execution driver for Platform v2 R5C25.

The R5C24 coordinator intentionally advances only one durable lifecycle boundary per
call. This module supplies the production driver around that coordinator. It never
weakens the R5C24 activation gate: when production execution is disabled, no driver is
started and no provider/remote-effect capability is constructed.

Webhook handling only signals this background driver. Provider/model/GitHub mutation is
never executed synchronously in the HTTP request thread.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import os
import queue
from pathlib import Path
import threading
import time
from typing import Any, Callable, Mapping

import codex_quota
import quota_governor

from .classifier_provider import (
    ClassifierRunStatus,
    ClassifierRunStore,
    CodexClassifierProvider,
    classifier_provider_config,
)
from .dormant_handoff_commit import DormantCommitOutcome, DormantHandoffCommitStore
from .dormant_worker import DormantWorkerDisposition, advance_dormant_admitted_worker
from .hosted_admission import HostedAdmissionOutcome, HostedAdmissionStore
from .hosted_budget import HostedBudgetLedger, HostedBudgetStore, utc_day
from .hosted_execution_runtime import (
    HostedExecutionAdvanceDisposition,
    HostedExecutionAdvanceResult,
    HostedExecutionDependencies,
    select_hosted_execution_plan,
)
from .hosted_task_plan import HostedTaskPlanStatus, HostedTaskPlanStore
from .hosted_worker_scheduler import (
    HOSTED_WORKER_CONCURRENCY_LIMIT,
    HostedWorkerReservationDisposition,
    HostedWorkerScheduleState,
    HostedWorkerSchedulerStore,
    reconcile_hosted_worker_scheduler_after_restart,
    reserve_hosted_worker,
    update_hosted_worker_schedule,
)
from .quota import (
    ProviderQuotaDecision,
    QuotaAdmissionDisposition,
    classify_quota_admission,
)
from .state_store import JsonStateStoreAdapter
from .worker_provider import (
    CodexWorkerProvider,
    WorkerProviderConfig,
    WorkerRunStatus,
    WorkerRunStore,
    provider_config_for_tier,
)


DEFAULT_LUNA_DAILY_LIMIT = 24
DEFAULT_TERRA_DAILY_LIMIT = 4
DEFAULT_PERIODIC_WAKE_SECONDS = 900
DEFAULT_MAX_BOUNDARIES_PER_WAKE = 32


def _env_int(
    environ: Mapping[str, str],
    name: str,
    default: int,
    *,
    minimum: int = 1,
) -> int:
    try:
        value = int(str(environ.get(name, default)).strip())
    except (TypeError, ValueError):
        return default
    return max(minimum, value)


def _env_float(
    environ: Mapping[str, str],
    name: str,
    default: float,
) -> float:
    try:
        return float(str(environ.get(name, default)).strip())
    except (TypeError, ValueError):
        return float(default)


def read_legacy_state(root: Path) -> dict[str, Any]:
    return JsonStateStoreAdapter.for_legacy_root(Path(root).resolve()).load().as_dict()


def _limit_from_legacy(
    legacy: Mapping[str, Any],
    override_key: str,
    *,
    environ: Mapping[str, str],
    env_key: str,
    default: int,
) -> int:
    override = legacy.get(override_key)
    if isinstance(override, int) and not isinstance(override, bool) and override >= 1:
        return override
    return _env_int(environ, env_key, default)


def provider_quota_decision(
    *,
    environ: Mapping[str, str] | None = None,
    snapshot_reader: Callable[[], Mapping[str, Any]] | None = None,
) -> ProviderQuotaDecision | None:
    """Return accepted provider pacing when telemetry is usable, else local fallback."""

    env = os.environ if environ is None else environ
    reader = snapshot_reader or codex_quota.quota_snapshot
    try:
        snapshot = dict(reader())
        raw = quota_governor.evaluate_quota(
            snapshot,
            preferred_limit_id=env.get("SKYFORGE_QUOTA_LIMIT_ID") or None,
            weekly_reserve_percent=_env_float(
                env,
                "SKYFORGE_WEEKLY_RESERVE_PERCENT",
                quota_governor.DEFAULT_WEEKLY_RESERVE_PERCENT,
            ),
            five_hour_reserve_percent=_env_float(
                env,
                "SKYFORGE_FIVE_HOUR_RESERVE_PERCENT",
                quota_governor.DEFAULT_FIVE_HOUR_RESERVE_PERCENT,
            ),
            weekly_burst_margin_percent=_env_float(
                env,
                "SKYFORGE_ORDINARY_WEEKLY_BURST_MARGIN_PERCENT",
                quota_governor.DEFAULT_WEEKLY_BURST_MARGIN_PERCENT,
            ),
            meter_tolerance_percent=_env_float(
                env,
                "SKYFORGE_QUOTA_METER_TOLERANCE_PERCENT",
                quota_governor.DEFAULT_METER_TOLERANCE_PERCENT,
            ),
        )
    except Exception:
        return None
    return ProviderQuotaDecision.from_mapping(raw)


class BudgetedClassifierProvider:
    """Record a durable attempt before delegating to the accepted classifier provider."""

    def __init__(
        self,
        *,
        root: Path,
        store: HostedBudgetStore,
        delegate=None,
        day_reader: Callable[[], str] = utc_day,
        legacy_reader: Callable[[Path], Mapping[str, Any]] = read_legacy_state,
    ) -> None:
        self.root = Path(root).resolve()
        self.store = store
        self.delegate = delegate or CodexClassifierProvider()
        self.day_reader = day_reader
        self.legacy_reader = legacy_reader

    def classify(self, *, request, root: Path, config):
        self.store.record_attempt(
            "classifier",
            day=self.day_reader(),
            legacy_state=self.legacy_reader(self.root),
        )
        return self.delegate.classify(request=request, root=root, config=config)


class BudgetedWorkerProvider:
    """Record the exact worker-tier attempt before invoking the accepted worker provider."""

    def __init__(
        self,
        *,
        root: Path,
        store: HostedBudgetStore,
        delegate=None,
        day_reader: Callable[[], str] = utc_day,
        legacy_reader: Callable[[Path], Mapping[str, Any]] = read_legacy_state,
    ) -> None:
        self.root = Path(root).resolve()
        self.store = store
        self.delegate = delegate or CodexWorkerProvider()
        self.day_reader = day_reader
        self.legacy_reader = legacy_reader

    def run(self, *, spec, worktree: Path, config: WorkerProviderConfig):
        kind = (
            "luna_worker"
            if str(config.tier.value).upper() == "LUNA"
            else "terra_worker"
        )
        self.store.record_attempt(
            kind,
            day=self.day_reader(),
            legacy_state=self.legacy_reader(self.root),
            attempt_id=getattr(spec, "attempt_id", None),
        )
        return self.delegate.run(spec=spec, worktree=worktree, config=config)


def _selected_plan_and_admission(root: Path):
    root = Path(root).resolve()
    plans = HostedTaskPlanStore.for_root(root).load()
    plan = select_hosted_execution_plan(root=root, ledger=plans)
    admissions = HostedAdmissionStore.for_root(root).load()
    admission = admissions.for_plan(plan.plan_id) if plan is not None else None
    return plan, admission


def _active_worker_budget_kind(root: Path) -> str:
    root = Path(root).resolve()
    plan, admission = _selected_plan_and_admission(root)
    if (
        admission is not None
        and admission.outcome is HostedAdmissionOutcome.ADMITTED
        and admission.worker_spec is not None
    ):
        return (
            "luna_worker"
            if admission.worker_spec.tier.value == "LUNA"
            else "terra_worker"
        )

    if plan is None or plan.seed is None:
        return "terra_worker"
    record = ClassifierRunStore.for_root(root).load().get(
        plan.seed.classifier_request.request_id
    )
    if (
        record is None
        or record.status is not ClassifierRunStatus.COMPLETE
        or record.decision is None
        or record.decision.effective_worker_tier is None
    ):
        return "terra_worker"
    return (
        "luna_worker"
        if record.decision.effective_worker_tier.value == "LUNA"
        else "terra_worker"
    )


def _quota_is_needed(root: Path) -> bool:
    root = Path(root).resolve()
    plan, admission = _selected_plan_and_admission(root)
    if plan is None:
        return False
    if plan.status in {HostedTaskPlanStatus.CLAIMED, HostedTaskPlanStatus.WAIT_REMOTE}:
        return False
    if plan.status is HostedTaskPlanStatus.BLOCKED or plan.seed is None:
        return False

    classifier = ClassifierRunStore.for_root(root).load().get(
        plan.seed.classifier_request.request_id
    )
    if classifier is None or classifier.status is not ClassifierRunStatus.COMPLETE:
        return True

    if admission is None:
        return True
    if (
        admission.outcome is not HostedAdmissionOutcome.ADMITTED
        or admission.worker_spec is None
        or admission.attempt is None
    ):
        return False

    worker = WorkerRunStore.for_root(root).load().find_attempt(
        admission.attempt.attempt_id
    )
    return worker is None or worker.status is not WorkerRunStatus.HANDOFF_READY


class ProductionHostedDependencyFactory:
    """Construct fresh R5C24 dependencies from accepted production adapters."""

    def __init__(
        self,
        *,
        root: Path,
        environ: Mapping[str, str] | None = None,
        budget_store: HostedBudgetStore | None = None,
        quota_reader: Callable[[], ProviderQuotaDecision | None] | None = None,
        day_reader: Callable[[], str] = utc_day,
        legacy_reader: Callable[[Path], Mapping[str, Any]] = read_legacy_state,
        classifier_provider=None,
        worker_provider=None,
    ) -> None:
        self.root = Path(root).resolve()
        self.environ = dict(os.environ if environ is None else environ)
        self.store = budget_store or HostedBudgetStore.for_root(self.root)
        self.day_reader = day_reader
        self.legacy_reader = legacy_reader
        self._quota_reader = quota_reader
        self.classifier_provider = classifier_provider or BudgetedClassifierProvider(
            root=self.root,
            store=self.store,
            day_reader=day_reader,
            legacy_reader=legacy_reader,
        )
        self.worker_provider = worker_provider or BudgetedWorkerProvider(
            root=self.root,
            store=self.store,
            day_reader=day_reader,
            legacy_reader=legacy_reader,
        )

    def _quota(self) -> ProviderQuotaDecision | None:
        if not _quota_is_needed(self.root):
            return None
        if self._quota_reader is not None:
            return self._quota_reader()
        return provider_quota_decision(environ=self.environ)

    def budget_snapshot(self) -> tuple[HostedBudgetLedger, int, int]:
        legacy = dict(self.legacy_reader(self.root))
        ledger = self.store.ensure_day(
            day=self.day_reader(),
            legacy_state=legacy,
        )
        luna_limit = _limit_from_legacy(
            legacy,
            "luna_daily_limit_override",
            environ=self.environ,
            env_key="SKYFORGE_ORCHESTRATOR_MAX_CLASSIFIER_CALLS_PER_DAY",
            default=DEFAULT_LUNA_DAILY_LIMIT,
        )
        terra_limit = _limit_from_legacy(
            legacy,
            "terra_daily_limit_override",
            environ=self.environ,
            env_key="SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY",
            default=DEFAULT_TERRA_DAILY_LIMIT,
        )
        return ledger, luna_limit, terra_limit

    def build(
        self,
        *,
        defer_worker_execution: bool = False,
    ) -> HostedExecutionDependencies:
        ledger, luna_limit, terra_limit = self.budget_snapshot()
        worker_kind = _active_worker_budget_kind(self.root)
        provider = self._quota()
        return HostedExecutionDependencies(
            classifier_provider=self.classifier_provider,
            worker_provider=self.worker_provider,
            classifier_local_budget=ledger.observation(
                "classifier",
                luna_limit=luna_limit,
                terra_limit=terra_limit,
            ),
            worker_local_budget=ledger.observation(
                worker_kind,
                luna_limit=luna_limit,
                terra_limit=terra_limit,
            ),
            classifier_provider_quota=provider,
            worker_provider_quota=provider,
            classifier_config=classifier_provider_config(self.environ),
            defer_worker_execution=defer_worker_execution,
        )

    def reserve_and_build_worker_attempt(
        self,
        attempt_id: str,
    ) -> tuple[HostedExecutionDependencies | None, str]:
        """Atomically admit exact local spend for one reserved worker attempt."""
        admissions = HostedAdmissionStore.for_root(self.root).load()
        admission = admissions.for_attempt(attempt_id)
        if (
            admission is None
            or admission.outcome is not HostedAdmissionOutcome.ADMITTED
            or admission.worker_spec is None
            or admission.attempt is None
        ):
            return None, "attempt lacks admitted worker authority"

        ledger, luna_limit, terra_limit = self.budget_snapshot()
        kind = (
            "luna_worker"
            if admission.worker_spec.tier.value == "LUNA"
            else "terra_worker"
        )
        local = ledger.observation(
            kind,
            luna_limit=luna_limit,
            terra_limit=terra_limit,
        )
        provider = (
            self._quota_reader()
            if self._quota_reader is not None
            else provider_quota_decision(environ=self.environ)
        )
        decision = classify_quota_admission(provider, local)
        if decision.disposition in {
            QuotaAdmissionDisposition.BLOCK_PROVIDER,
            QuotaAdmissionDisposition.BLOCK_LOCAL,
        }:
            return None, decision.reason

        daily_limit = (
            luna_limit if kind == "luna_worker" else terra_limit
        )
        reserved_ledger, admitted = self.store.reserve_worker_attempt(
            attempt_id=attempt_id,
            kind=kind,
            day=self.day_reader(),
            daily_limit=daily_limit,
            legacy_state=self.legacy_reader(self.root),
        )
        if not admitted:
            return None, "local worker budget exhausted during atomic reservation"

        return HostedExecutionDependencies(
            classifier_provider=self.classifier_provider,
            worker_provider=self.worker_provider,
            classifier_local_budget=reserved_ledger.observation(
                "classifier",
                luna_limit=luna_limit,
                terra_limit=terra_limit,
            ),
            worker_local_budget=reserved_ledger.observation(
                kind,
                luna_limit=luna_limit,
                terra_limit=terra_limit,
            ),
            classifier_provider_quota=provider,
            worker_provider_quota=provider,
            classifier_config=classifier_provider_config(self.environ),
            worker_config=provider_config_for_tier(
                admission.worker_spec.tier,
                self.environ,
            ),
            defer_worker_execution=False,
        ), "exact worker budget/provider admission reserved"


@dataclass(frozen=True)
class HostedExecutionDriverSnapshot:
    enabled: bool
    running: bool
    wake_count: int = 0
    advance_count: int = 0
    last_disposition: str = ""
    last_reason: str = ""
    last_durable_identity: str = ""
    last_advance_at: str = ""
    last_error: str = ""

    def as_dict(self) -> dict[str, Any]:
        return {
            "enabled": self.enabled,
            "running": self.running,
            "wake_count": self.wake_count,
            "advance_count": self.advance_count,
            "last_disposition": self.last_disposition,
            "last_reason": self.last_reason,
            "last_durable_identity": self.last_durable_identity,
            "last_advance_at": self.last_advance_at,
            "last_error": self.last_error,
        }


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def should_continue_after(
    result: HostedExecutionAdvanceResult,
    *,
    root: Path,
) -> bool:
    disposition = result.disposition
    if disposition in {
        HostedExecutionAdvanceDisposition.TASK_CLAIMED,
        HostedExecutionAdvanceDisposition.TASK_REVISION_ACCEPTED,
        HostedExecutionAdvanceDisposition.TASK_COMPLETION_RECORDED,
        HostedExecutionAdvanceDisposition.TASK_COMPLETED,
    }:
        return True

    root = Path(root).resolve()
    plan, admission = _selected_plan_and_admission(root)

    if disposition is HostedExecutionAdvanceDisposition.PREFLIGHT_ADVANCED:
        return plan is not None and plan.status in {
            HostedTaskPlanStatus.READY_FOR_CLASSIFIER,
            HostedTaskPlanStatus.CLAIMED,
            HostedTaskPlanStatus.WAIT_REMOTE,
        }
    if disposition is HostedExecutionAdvanceDisposition.CLASSIFIER_ADVANCED:
        if plan is None:
            return False
        if plan.status in {
            HostedTaskPlanStatus.CLAIMED,
            HostedTaskPlanStatus.WAIT_REMOTE,
        }:
            return True
        if plan.seed is None:
            return False
        run = ClassifierRunStore.for_root(root).load().get(
            plan.seed.classifier_request.request_id
        )
        return run is not None and run.status is ClassifierRunStatus.COMPLETE
    if disposition is HostedExecutionAdvanceDisposition.ADMISSION_ADVANCED:
        if plan is None:
            return False
        if admission is None:
            return plan.status in {
                HostedTaskPlanStatus.CLAIMED,
                HostedTaskPlanStatus.WAIT_REMOTE,
                HostedTaskPlanStatus.READY_FOR_CLASSIFIER,
            }
        return admission.outcome is HostedAdmissionOutcome.ADMITTED
    if disposition is HostedExecutionAdvanceDisposition.WORKER_ADVANCED:
        if admission is None or admission.attempt is None:
            return False
        run = WorkerRunStore.for_root(root).load().find_attempt(
            admission.attempt.attempt_id
        )
        return run is not None and run.status is WorkerRunStatus.HANDOFF_READY
    if disposition is HostedExecutionAdvanceDisposition.LOCAL_COMMIT_ADVANCED:
        if admission is None or admission.attempt is None:
            return False
        record = DormantHandoffCommitStore.for_root(root).load().for_attempt(
            admission.attempt.attempt_id
        )
        return record is not None and record.outcome is DormantCommitOutcome.COMMITTED
    return False


class HostedExecutionDriver:
    """Background event/periodic driver around the one-boundary R5C24 coordinator."""

    def __init__(
        self,
        *,
        runtime,
        dependencies: ProductionHostedDependencyFactory,
        periodic_seconds: int = DEFAULT_PERIODIC_WAKE_SECONDS,
        max_boundaries_per_wake: int = DEFAULT_MAX_BOUNDARIES_PER_WAKE,
    ) -> None:
        if not bool(getattr(runtime, "production_execution_enabled", False)):
            raise ValueError("hosted execution driver requires enabled R5C24 gate")
        if periodic_seconds < 1 or max_boundaries_per_wake < 1:
            raise ValueError("driver bounds must be positive")
        self.runtime = runtime
        self.dependencies = dependencies
        self.root = Path(runtime.root).resolve()
        self.periodic_seconds = int(periodic_seconds)
        self.max_boundaries_per_wake = int(max_boundaries_per_wake)
        self._wake = threading.Event()
        self._stop = threading.Event()
        self._lock = threading.RLock()
        self._thread: threading.Thread | None = None
        self._worker_threads: dict[str, threading.Thread] = {}
        self._worker_results: queue.Queue[tuple[str, object, Exception | None]] = queue.Queue()
        reconcile_hosted_worker_scheduler_after_restart(root=self.root)
        self._snapshot = HostedExecutionDriverSnapshot(enabled=True, running=False)

    def snapshot(self) -> HostedExecutionDriverSnapshot:
        with self._lock:
            return self._snapshot

    def budget_snapshot(self) -> dict[str, Any]:
        legacy = dict(self.dependencies.legacy_reader(self.root))
        ledger = self.dependencies.store.load()
        luna_limit = _limit_from_legacy(
            legacy,
            "luna_daily_limit_override",
            environ=self.dependencies.environ,
            env_key="SKYFORGE_ORCHESTRATOR_MAX_CLASSIFIER_CALLS_PER_DAY",
            default=DEFAULT_LUNA_DAILY_LIMIT,
        )
        terra_limit = _limit_from_legacy(
            legacy,
            "terra_daily_limit_override",
            environ=self.dependencies.environ,
            env_key="SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY",
            default=DEFAULT_TERRA_DAILY_LIMIT,
        )
        return {
            **ledger.as_dict(),
            "luna_calls": ledger.luna_calls,
            "luna_daily_limit": luna_limit,
            "terra_daily_limit": terra_limit,
        }

    def start(self) -> None:
        with self._lock:
            if self._thread is not None and self._thread.is_alive():
                return
            self._stop.clear()
            self._snapshot = HostedExecutionDriverSnapshot(
                **{**self._snapshot.as_dict(), "running": True}
            )
            self._thread = threading.Thread(
                target=self._run,
                name="skyforge-v2-execution-driver",
                daemon=True,
            )
            self._thread.start()
        self.wake()

    def stop(self, timeout: float = 5.0) -> None:
        self._stop.set()
        self._wake.set()
        thread = self._thread
        if thread is not None:
            thread.join(timeout=max(0.0, timeout))
        with self._lock:
            data = self._snapshot.as_dict()
            data["running"] = False
            self._snapshot = HostedExecutionDriverSnapshot(**data)

    def wake(self) -> None:
        self._wake.set()

    def _record_result(self, result: HostedExecutionAdvanceResult) -> None:
        with self._lock:
            current = self._snapshot
            self._snapshot = HostedExecutionDriverSnapshot(
                enabled=True,
                running=True,
                wake_count=current.wake_count,
                advance_count=current.advance_count + 1,
                last_disposition=result.disposition.value,
                last_reason=result.reason,
                last_durable_identity=result.durable_identity,
                last_advance_at=_utc_now(),
                last_error="",
            )

    def _record_error(self, exc: Exception) -> None:
        with self._lock:
            current = self._snapshot
            self._snapshot = HostedExecutionDriverSnapshot(
                enabled=True,
                running=True,
                wake_count=current.wake_count,
                advance_count=current.advance_count,
                last_disposition=current.last_disposition,
                last_reason=current.last_reason,
                last_durable_identity=current.last_durable_identity,
                last_advance_at=current.last_advance_at,
                last_error=f"{type(exc).__name__}: {exc}",
            )

    def _worker_target(
        self,
        attempt_id: str,
        dependencies: HostedExecutionDependencies,
    ) -> None:
        try:
            result = advance_dormant_admitted_worker(
                root=self.root,
                provider=dependencies.worker_provider,
                provider_quota=dependencies.worker_provider_quota,
                local_budget=dependencies.worker_local_budget,
                config=dependencies.worker_config,
                attempt_id=attempt_id,
                local_budget_reserved=True,
            )
            self._worker_results.put((attempt_id, result, None))
        except Exception as exc:
            self._worker_results.put((attempt_id, None, exc))
        finally:
            self._wake.set()

    def _launch_runnable_worker(
        self,
        attempt_id: str,
    ) -> HostedExecutionAdvanceResult:
        reservation = reserve_hosted_worker(
            root=self.root,
            attempt_id=attempt_id,
        )
        if reservation.disposition is HostedWorkerReservationDisposition.LIMIT_REACHED:
            return HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.WORKER_RUNNABLE,
                reservation.reason,
                self.runtime.execution_gate.digest,
                attempt_id,
            )
        if reservation.disposition is HostedWorkerReservationDisposition.ALREADY_EXECUTING:
            return HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.WORKER_EXECUTING,
                reservation.reason,
                self.runtime.execution_gate.digest,
                attempt_id,
            )
        if reservation.disposition is not HostedWorkerReservationDisposition.RESERVED:
            return HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.BLOCKED,
                reservation.reason,
                self.runtime.execution_gate.digest,
                attempt_id,
            )

        dependencies, reason = self.dependencies.reserve_and_build_worker_attempt(
            attempt_id
        )
        if dependencies is None:
            update_hosted_worker_schedule(
                root=self.root,
                attempt_id=attempt_id,
                state=HostedWorkerScheduleState.WAIT_QUOTA,
                reason=reason,
            )
            return HostedExecutionAdvanceResult(
                HostedExecutionAdvanceDisposition.WORKER_RUNNABLE,
                reason,
                self.runtime.execution_gate.digest,
                attempt_id,
            )

        thread = threading.Thread(
            target=self._worker_target,
            args=(attempt_id, dependencies),
            name=f"skyforge-v2-worker-{attempt_id[:10]}",
            daemon=True,
        )
        with self._lock:
            self._worker_threads[attempt_id] = thread
        thread.start()
        return HostedExecutionAdvanceResult(
            HostedExecutionAdvanceDisposition.WORKER_EXECUTING,
            "exact admitted worker launched under bounded concurrency slot",
            self.runtime.execution_gate.digest,
            attempt_id,
        )

    def _reap_worker_results(self) -> int:
        reaped = 0
        while True:
            try:
                attempt_id, result, error = self._worker_results.get_nowait()
            except queue.Empty:
                break
            reaped += 1
            with self._lock:
                self._worker_threads.pop(attempt_id, None)
            if error is not None:
                update_hosted_worker_schedule(
                    root=self.root,
                    attempt_id=attempt_id,
                    state=HostedWorkerScheduleState.RECOVERY_REQUIRED,
                    reason=f"worker thread failed closed: {type(error).__name__}: {error}",
                )
                self._record_error(error)
                continue

            disposition = result.disposition
            if disposition in {
                DormantWorkerDisposition.HANDOFF_READY,
                DormantWorkerDisposition.ALREADY_READY,
            }:
                state = HostedWorkerScheduleState.COMPLETED
            elif disposition is DormantWorkerDisposition.QUOTA_BLOCKED:
                state = HostedWorkerScheduleState.WAIT_QUOTA
            elif disposition is DormantWorkerDisposition.CONFLICT:
                state = HostedWorkerScheduleState.WAIT_CLAIM
            else:
                state = HostedWorkerScheduleState.RECOVERY_REQUIRED
            update_hosted_worker_schedule(
                root=self.root,
                attempt_id=attempt_id,
                state=state,
                reason=result.reason,
            )
            self._record_result(
                HostedExecutionAdvanceResult(
                    HostedExecutionAdvanceDisposition.WORKER_ADVANCED,
                    result.reason,
                    self.runtime.execution_gate.digest,
                    result.worker_run_id or attempt_id,
                )
            )
        return reaped

    def _drain_one_wake(self) -> None:
        self._reap_worker_results()
        for _ in range(self.max_boundaries_per_wake):
            if self._stop.is_set():
                return
            result = self.runtime.advance_one_execution_step(
                self.dependencies.build(defer_worker_execution=True)
            )
            if result.disposition is HostedExecutionAdvanceDisposition.WORKER_RUNNABLE:
                launched = self._launch_runnable_worker(result.durable_identity)
                self._record_result(launched)
                if (
                    launched.disposition
                    is HostedExecutionAdvanceDisposition.WORKER_EXECUTING
                ):
                    # Continue the same wake so a second independent runnable attempt
                    # can claim the remaining slot.
                    continue
                return

            self._record_result(result)
            if result.disposition is HostedExecutionAdvanceDisposition.WORKER_EXECUTING:
                return
            if not should_continue_after(result, root=self.root):
                return
            self._reap_worker_results()
        raise RuntimeError("hosted execution driver exceeded bounded lifecycle drain")

    def _run(self) -> None:
        while not self._stop.is_set():
            self._wake.wait(self.periodic_seconds)
            self._wake.clear()
            if self._stop.is_set():
                break
            with self._lock:
                current = self._snapshot
                self._snapshot = HostedExecutionDriverSnapshot(
                    enabled=True,
                    running=True,
                    wake_count=current.wake_count + 1,
                    advance_count=current.advance_count,
                    last_disposition=current.last_disposition,
                    last_reason=current.last_reason,
                    last_durable_identity=current.last_durable_identity,
                    last_advance_at=current.last_advance_at,
                    last_error=current.last_error,
                )
            try:
                self._drain_one_wake()
            except Exception as exc:
                self._record_error(exc)
                # Fail closed for this wake. A later webhook/periodic wake may retry a
                # model-free/reconstructible boundary after the durable error is inspected.
                continue
