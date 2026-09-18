#!/usr/bin/env python3
"""Mutation-capable Platform-v2 hosted entrypoint, gated by operator permit.

This is intentionally separate from platform_v2_hosted_runtime.py so the accepted
read-only substrate remains incapable of repository/provider mutation.
"""

from __future__ import annotations

import argparse
from http.server import ThreadingHTTPServer
import os
from pathlib import Path
import threading
import time

from platform_v2_hosted_runtime import (
    DEFAULT_PORT,
    DEFAULT_REPO,
    Handler,
    HostedV2Substrate,
)
from v2.classifier_provider import CodexClassifierProvider
from v2.dormant_handoff_commit import (
    DormantHandoffCommitLedger,
    DormantHandoffCommitStore,
)
from v2.hosted_admission import HostedAdmissionLedger, HostedAdmissionStore
from v2.hosted_state import HostedIngressState
from v2.hosted_task_plan import HostedTaskPlanLedger, HostedTaskPlanStore
from v2.inbox import InboxState
from v2.production_coordinator import (
    ProductionCoordinator,
    ProductionCoordinatorDisposition,
    clear_completed_task_state,
)
from v2.production_execution import (
    HostedManagedHandoffLedger,
    HostedManagedHandoffStore,
    ProductionExecutionPermitStore,
    validate_production_permit,
)
from v2.worker_provider import CodexWorkerProvider


class ProductionHostedRuntime(HostedV2Substrate):
    def __init__(
        self,
        root: Path,
        *,
        repo: str,
        require_webhook_secret: bool,
        startup_reconcile: bool,
        classifier_daily_limit: int,
        worker_daily_limit: int,
        webhook_secret: str | None = None,
        trusted_actors: tuple[str, ...] | None = None,
        classifier_provider=None,
        worker_provider=None,
        runner=None,
        day_provider=None,
    ) -> None:
        super().__init__(
            root,
            repo=repo,
            require_webhook_secret=require_webhook_secret,
            startup_reconcile=startup_reconcile,
            webhook_secret=webhook_secret,
            trusted_actors=trusted_actors,
        )
        permit=ProductionExecutionPermitStore.for_root(self.root).load()
        if permit is None:
            raise RuntimeError(
                "production Platform-v2 runtime requires an operator execution permit"
            )
        validate_kwargs={}
        if runner is not None:
            validate_kwargs["runner"]=runner
        validate_production_permit(self.root,permit,**validate_kwargs)
        self.execution_permit=permit
        self._wake=threading.Event()
        self._stop=threading.Event()
        self._execution_thread: threading.Thread | None=None
        self._last_execution_result=None
        self._production_lock=threading.RLock()
        self._runner=runner

        coordinator_kwargs={
            "root":self.root,
            "repo":self.repo,
            "permit":permit,
            "trusted_actors":self.trusted_actors,
            "classifier_provider":classifier_provider or CodexClassifierProvider(),
            "worker_provider":worker_provider or CodexWorkerProvider(),
            "classifier_daily_limit":classifier_daily_limit,
            "worker_daily_limit":worker_daily_limit,
            "day_provider":day_provider,
        }
        if runner is not None:
            coordinator_kwargs["runner"]=runner
        self.coordinator=ProductionCoordinator(**coordinator_kwargs)

    def handle_webhook(self, *, headers, raw):
        status,payload=super().handle_webhook(headers=headers,raw=raw)
        if status in (200,202) and payload.get("accepted"):
            self._wake.set()
        return status,payload

    def _retire_completed_task(self, event_id: str) -> None:
        with self._lock:
            latest=self.store.load()
            inbox=latest.inbox
            completed=tuple(dict.fromkeys(
                inbox.completed_authority_event_keys + (event_id,)
            ))
            next_inbox=InboxState(
                pending_events=tuple(
                    event for event in inbox.pending_events
                    if event.event_id != event_id
                ),
                retired_event_keys=inbox.retired_event_keys,
                completed_authority_event_keys=completed,
                owned_event_keys=tuple(
                    key for key in inbox.owned_event_keys if key != event_id
                ),
            )
            next_state=HostedIngressState(
                inbox=next_inbox,
                seen_deliveries=latest.seen_deliveries,
                legacy_projection=latest.legacy_projection,
                accepted_deliveries=latest.accepted_deliveries,
                rejected_controls=latest.rejected_controls,
            )
            self.store.save(next_state)
            self.state=next_state
        clear_completed_task_state(self.root)

    def advance_production_once(self):
        with self._production_lock:
            result=self.coordinator.advance_once()
            self._last_execution_result=result
            if result.disposition is ProductionCoordinatorDisposition.TASK_COMPLETE:
                self._retire_completed_task(result.event_id)
                self._wake.set()
            return result

    def _execution_loop(self) -> None:
        self._wake.set()
        while not self._stop.is_set():
            self._wake.wait(timeout=30)
            self._wake.clear()
            if self._stop.is_set():
                break
            # Advance bounded durable transitions until a remote wait, human gate,
            # block, or idle boundary. This never bypasses the permit gate.
            for _ in range(16):
                try:
                    result=self.advance_production_once()
                except Exception as exc:
                    print(f"[platform-v2-production] execution blocked: {exc}",flush=True)
                    break
                if result.disposition is ProductionCoordinatorDisposition.ADVANCED:
                    continue
                if result.disposition is ProductionCoordinatorDisposition.TASK_COMPLETE:
                    continue
                break

    def start_execution_loop(self) -> None:
        if self._execution_thread is not None and self._execution_thread.is_alive():
            return
        self._stop.clear()
        self._execution_thread=threading.Thread(
            target=self._execution_loop,
            name="skyforge-v2-production-coordinator",
            daemon=True,
        )
        self._execution_thread.start()

    def stop_execution_loop(self) -> None:
        self._stop.set()
        self._wake.set()
        thread=self._execution_thread
        if thread is not None:
            thread.join(timeout=5)

    def health_snapshot(self):
        health=super().health_snapshot()
        last=self._last_execution_result
        health.update({
            "runtime_mode":"platform-v2-production-gated",
            "mutation_authority":True,
            "worker_dispatch_enabled":True,
            "remote_effect_execution_enabled":True,
            "ordinary_v2_mutation_authority":True,
            "production_permit_id":self.execution_permit.permit_id,
            "production_accepted_main":self.execution_permit.accepted_main_sha,
            "legacy_writer_revoked":self.execution_permit.legacy_writer_revoked,
            "last_execution_phase":last.phase if last is not None else "",
            "last_execution_disposition":(
                last.disposition.value if last is not None else ""
            ),
        })
        return health


def _positive_env(name: str, default: int) -> int:
    raw=os.environ.get(name,str(default))
    try:
        value=int(raw)
    except ValueError as exc:
        raise RuntimeError(f"{name} must be integer") from exc
    if value < 1:
        raise RuntimeError(f"{name} must be positive")
    return value


def parse_args(argv=None):
    parser=argparse.ArgumentParser()
    parser.add_argument("--root",type=Path,default=Path.cwd())
    parser.add_argument("--repo",default=DEFAULT_REPO)
    parser.add_argument("--bind",default=os.environ.get("SKYFORGE_ORCHESTRATOR_BIND","127.0.0.1"))
    parser.add_argument("--port",type=int,default=DEFAULT_PORT)
    parser.add_argument("--require-webhook-secret",action="store_true",
        default=os.environ.get("SKYFORGE_REQUIRE_WEBHOOK_SECRET")=="1")
    parser.add_argument("--startup-reconcile",action="store_true",
        default=os.environ.get("SKYFORGE_STARTUP_RECONCILE")=="1")
    parser.add_argument("--classifier-daily-limit",type=int,
        default=_positive_env("SKYFORGE_ORCHESTRATOR_MAX_CLASSIFIER_CALLS_PER_DAY",24))
    parser.add_argument("--worker-daily-limit",type=int,
        default=_positive_env("SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY",4))
    return parser.parse_args(argv)


def main(argv=None) -> int:
    args=parse_args(argv)
    runtime=ProductionHostedRuntime(
        args.root,
        repo=args.repo,
        require_webhook_secret=args.require_webhook_secret,
        startup_reconcile=args.startup_reconcile,
        classifier_daily_limit=args.classifier_daily_limit,
        worker_daily_limit=args.worker_daily_limit,
    )
    Handler.runtime=runtime
    server=ThreadingHTTPServer((args.bind,args.port),Handler)
    runtime.start_execution_loop()
    print(
        f"[platform-v2-production] gated writer listening on "
        f"http://{args.bind}:{args.port}/webhook for {args.repo}",
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        runtime.stop_execution_loop()
        server.server_close()
    return 0


if __name__=="__main__":
    raise SystemExit(main())
