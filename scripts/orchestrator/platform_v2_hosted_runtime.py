#!/usr/bin/env python3
"""Read-only hosted substrate for the Platform-v2 control plane.

This process is intentionally incapable of repository/provider mutation.  It accepts the
production webhook/health transport contract, verifies GitHub delivery identity, persists
a v2 durable inbox, and projects legacy authority for later cutover.  Ordinary task
execution is not enabled in R5B.
"""

from __future__ import annotations

import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import threading
from typing import Any, Mapping

from v2.cutover import LegacyOperationalProjection
from v2.hosted_admission import (
    HostedAdmissionAdvanceResult,
    HostedAdmissionStore,
    advance_hosted_task_admission,
)
from v2.hosted_classifier import (
    HostedClassifierResult,
    advance_hosted_classifier_proposal,
)
from v2.hosted_execution_runtime import (
    HostedExecutionAdvanceResult,
    HostedExecutionDependencies,
    HostedExecutionGateDecision,
    HostedExecutionGateDisposition,
    HostedExecutionCoordinator,
    load_hosted_execution_gate,
)
from v2.hosted_state import HostedIngressState, HostedStateStore, ingest_event
from v2.hosted_task_plan import (
    HostedTaskPlanDisposition,
    HostedTaskPlanResult,
    HostedTaskPlanStore,
    advance_claimed_task_preflight,
    claim_next_protected_task,
)
from v2.hosted_task_preflight import (
    HostedTaskPreflightDisposition,
    HostedTaskPreflightResult,
    preflight_captured_task,
)
from v2.identity import canonical_digest
from v2.ingress import (
    classify_legacy_compatible_control,
    classify_legacy_compatible_event,
    verify_github_signature,
)
from v2.state_store import JsonStateStoreAdapter, StateStoreError
from v2.task_event_composition import (
    TaskAuthorityEventStore,
    capture_task_authority_event,
)


DEFAULT_REPO = "ni-da-ba/skyforge"
DEFAULT_PORT = 3000
MAX_PAYLOAD_BYTES = 5_000_000
_REPO_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")


def _trusted_actors() -> tuple[str, ...]:
    raw = os.environ.get("SKYFORGE_TRUSTED_GITHUB_ACTORS", "ni-da-ba")
    return tuple(
        actor.strip().lower()
        for actor in raw.split(",")
        if actor.strip()
    )


def _read_legacy_projection(root: Path) -> LegacyOperationalProjection:
    adapter = JsonStateStoreAdapter.for_legacy_root(root)
    snapshot = adapter.load()
    return LegacyOperationalProjection.from_legacy_mapping(snapshot.as_dict())


class HostedV2Substrate:
    def __init__(
        self,
        root: Path,
        *,
        repo: str,
        require_webhook_secret: bool,
        startup_reconcile: bool,
        webhook_secret: str | None = None,
        trusted_actors: tuple[str, ...] | None = None,
        production_execution_requested: bool = False,
        execution_gate: HostedExecutionGateDecision | None = None,
    ) -> None:
        self.root = Path(root).resolve()
        self.repo = str(repo).strip()
        self.require_webhook_secret = bool(require_webhook_secret)
        self.startup_reconcile = bool(startup_reconcile)
        self.webhook_secret = webhook_secret or os.environ.get("SKYFORGE_WEBHOOK_SECRET")
        self.trusted_actors = trusted_actors or _trusted_actors()
        self.production_execution_requested = bool(production_execution_requested)
        self.execution_gate = execution_gate
        if self.production_execution_requested:
            if (
                self.execution_gate is None
                or self.execution_gate.disposition
                is not HostedExecutionGateDisposition.READY
            ):
                raise RuntimeError(
                    "production execution requested without an accepted R5C24 activation gate"
                )
        self.production_execution_enabled = (
            self.production_execution_requested
            and self.execution_gate is not None
            and self.execution_gate.enabled
        )
        self.store = HostedStateStore.for_root(self.root)
        self.task_authority_store = TaskAuthorityEventStore.for_root(self.root)
        self.task_plan_store = HostedTaskPlanStore.for_root(self.root)
        self.admission_store = HostedAdmissionStore.for_root(self.root)
        self._lock = threading.RLock()
        self.state = self.store.load()
        self.validate_environment()
        self.refresh_legacy_projection()

    def validate_environment(self) -> None:
        if not self.root.is_dir():
            raise RuntimeError(f"repository root does not exist: {self.root}")
        if not _REPO_RE.fullmatch(self.repo):
            raise RuntimeError("repo must be owner/name using GitHub-safe characters")
        if self.require_webhook_secret:
            if not self.webhook_secret:
                raise RuntimeError(
                    "Hosted webhook mode requires SKYFORGE_WEBHOOK_SECRET."
                )
            if len(self.webhook_secret) < 32:
                raise RuntimeError(
                    "SKYFORGE_WEBHOOK_SECRET must be at least 32 characters in hosted mode."
                )

    def refresh_legacy_projection(self) -> None:
        projection = _read_legacy_projection(self.root)
        with self._lock:
            next_state = self.state.with_projection(projection.as_dict())
            self.store.save(next_state)
            self.state = next_state

    def health_snapshot(self) -> dict[str, Any]:
        with self._lock:
            state = self.state
            projection = state.legacy_projection or {}
            external = projection.get("external_claims") or []
            roadmap = projection.get("roadmap") or {}
            authority_ledger = self.task_authority_store.load()
            task_plan = self.task_plan_store.load()
            active_plan = task_plan.active
            admission = self.admission_store.load().record
            return {
                "status": "ok",
                "controller": "platform-v2",
                "runtime_mode": (
                    "hosted-v2-gated-execution"
                    if self.production_execution_enabled
                    else "hosted-substrate-read-only"
                ),
                "schema_version": 1,
                "repo": self.repo,
                "mutation_authority": self.production_execution_enabled,
                "worker_dispatch_enabled": self.production_execution_enabled,
                "remote_effect_execution_enabled": self.production_execution_enabled,
                "startup_reconcile_requested": self.startup_reconcile,
                "legacy_classifier_adapter": True,
                "state_digest": state.digest,
                "pending_event_count": len(state.inbox.pending_events),
                "seen_delivery_count": len(state.seen_deliveries),
                "legacy_projection_digest": (
                    canonical_digest(projection) if projection else ""
                ),
                "projected_external_claim_count": len(external),
                "projected_roadmap_id": str(roadmap.get("roadmap_id") or ""),
                "ordinary_v2_mutation_authority": self.production_execution_enabled,
                "task_authority_capture_enabled": True,
                "task_authority_record_count": len(authority_ledger.records),
                "task_preflight_enabled": True,
                "hosted_task_planning_enabled": True,
                "explicit_classifier_proposal_enabled": True,
                "automatic_classifier_execution_enabled": False,
                "explicit_task_admission_enabled": True,
                "automatic_task_admission_enabled": False,
                "production_execution_requested": self.production_execution_requested,
                "production_execution_enabled": self.production_execution_enabled,
                "production_execution_gate_digest": (
                    self.execution_gate.digest if self.execution_gate is not None else ""
                ),
                "production_execution_gate_blockers": (
                    list(self.execution_gate.blockers)
                    if self.execution_gate is not None
                    else ["no production execution activation evidence supplied"]
                ),
                "active_admission_record_id": (
                    admission.record_id if admission is not None else ""
                ),
                "active_admission_outcome": (
                    admission.outcome.value if admission is not None else ""
                ),
                "active_task_plan_id": (
                    active_plan.plan_id if active_plan is not None else ""
                ),
                "active_task_plan_status": (
                    active_plan.status.value if active_plan is not None else ""
                ),
            }

    def handle_webhook(
        self,
        *,
        headers: Mapping[str, str],
        raw: bytes,
    ) -> tuple[int, dict[str, Any]]:
        if not isinstance(raw, bytes) or not raw:
            return 400, {"error": "invalid payload"}
        if len(raw) > MAX_PAYLOAD_BYTES:
            return 400, {"error": "invalid payload size"}

        if self.require_webhook_secret:
            if not verify_github_signature(
                str(self.webhook_secret or ""),
                raw,
                headers.get("X-Hub-Signature-256"),
            ):
                return 403, {"error": "invalid webhook signature"}

        try:
            payload = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            return 400, {"error": "invalid JSON payload"}
        if not isinstance(payload, dict):
            return 400, {"error": "webhook payload must be an object"}

        repository = payload.get("repository") or {}
        if not isinstance(repository, dict):
            return 400, {"error": "repository must be an object"}
        full_name = str(repository.get("full_name") or "")
        if full_name and full_name != self.repo:
            return 400, {"error": "repository mismatch"}

        event_name = str(headers.get("X-GitHub-Event") or "")
        delivery_id = headers.get("X-GitHub-Delivery")

        control = classify_legacy_compatible_control(
            event_name,
            payload,
            trusted_actors=self.trusted_actors,
        )
        if control:
            with self._lock:
                if delivery_id and delivery_id in self.state.seen_deliveries:
                    return 200, {
                        "accepted": False,
                        "duplicate": True,
                        "mutation_authority": self.production_execution_enabled,
                    }
                next_state = self.state.with_rejected_control(delivery_id)
                self.store.save(next_state)
                self.state = next_state
            return 202, {
                "accepted": False,
                "control": control,
                "read_only": True,
                "mutation_authority": self.production_execution_enabled,
            }

        event = classify_legacy_compatible_event(
            event_name,
            payload,
            repo=self.repo,
            trusted_actors=self.trusted_actors,
        )

        # For task authority, persist the exact signed-webhook actor/revision sidecar
        # before the durable event can enter the inbox. A crash after this write is
        # harmless; the inverse ordering could leave an executable task without actor
        # identity and is therefore forbidden.
        try:
            authority_record = capture_task_authority_event(
                event=event,
                payload=payload,
                repo=self.repo,
                trusted_actors=self.trusted_actors,
                delivery_id=delivery_id,
            )
            if authority_record is not None:
                with self._lock:
                    self.task_authority_store.capture(authority_record)
        except ValueError as exc:
            return 409, {
                "accepted": False,
                "error": f"task authority capture rejected: {exc}",
                "mutation_authority": self.production_execution_enabled,
            }
        except StateStoreError:
            return 503, {
                "accepted": False,
                "error": "task authority capture persistence unavailable",
                "mutation_authority": self.production_execution_enabled,
            }

        self.refresh_legacy_projection()

        with self._lock:
            transition = ingest_event(
                self.state,
                event,
                delivery_id=delivery_id,
            )
            if transition.after is not self.state:
                self.store.save(transition.after)
                self.state = transition.after

        if transition.duplicate_delivery:
            return 200, {
                "accepted": False,
                "duplicate": True,
                "mutation_authority": self.production_execution_enabled,
            }

        return 202, {
            "accepted": event.actionable,
            "reason": event.reason,
            "semantic_replay_suppressed": transition.semantic_replay_suppressed,
            "task_authority_recorded": authority_record is not None,
            "mutation_authority": self.production_execution_enabled,
        }

    def claim_next_task_plan(
        self,
        *,
        external_claims=(),
    ) -> HostedTaskPlanResult:
        """Durably claim one protected task for planning only.

        This method performs no network I/O and cannot call a classifier, worker, or
        remote-effect adapter.
        """
        with self._lock:
            ledger = self.task_plan_store.load()
            authority = self.task_authority_store.load()
            result = claim_next_protected_task(
                ledger=ledger,
                inbox=self.state.inbox,
                authority_events=authority,
                external_claims=tuple(external_claims),
            )
            if result.ledger != ledger:
                self.task_plan_store.save(result.ledger)
            return result

    def advance_task_plan_preflight(
        self,
        *,
        runner=None,
    ) -> HostedTaskPlanResult:
        """Advance the already-claimed plan through fresh read-only preflight only."""
        with self._lock:
            before = self.task_plan_store.load()
            authority = self.task_authority_store.load()

        kwargs = {
            "ledger": before,
            "authority_events": authority,
            "trusted_actors": self.trusted_actors,
            "repo": self.repo,
        }
        if runner is not None:
            kwargs["runner"] = runner
        result = advance_claimed_task_preflight(**kwargs)

        with self._lock:
            current = self.task_plan_store.load()
            before_id = before.active.plan_id if before.active is not None else ""
            current_id = current.active.plan_id if current.active is not None else ""
            if current != before or current_id != before_id:
                return HostedTaskPlanResult(
                    HostedTaskPlanDisposition.BLOCKED,
                    "hosted task plan changed during read-only preflight; refusing stale save",
                    current,
                )
            if result.ledger != current:
                self.task_plan_store.save(result.ledger)
            return result

    def advance_task_classifier(
        self,
        *,
        provider,
        provider_quota,
        local_budget,
        config=None,
    ) -> HostedClassifierResult:
        """Explicitly advance the classifier-ready plan through one durable proposal.

        The HTTP webhook path never calls this method. The classifier proposal remains
        non-authoritative until later repository dispatch admission.
        """
        with self._lock:
            plan = self.task_plan_store.load()
        return advance_hosted_classifier_proposal(
            plan_ledger=plan,
            root=self.root,
            provider=provider,
            provider_quota=provider_quota,
            local_budget=local_budget,
            config=config,
        )

    def advance_task_admission(
        self,
        *,
        active_external_claims=(),
        provider_quota,
        local_budget,
        attempt_number: int,
        runner=None,
    ) -> HostedAdmissionAdvanceResult:
        """Explicitly freeze one admitted worker identity without launching it."""
        with self._lock:
            plan = self.task_plan_store.load()
        kwargs = {
            "root": self.root,
            "plan_ledger": plan,
            "trusted_actors": self.trusted_actors,
            "repo": self.repo,
            "active_external_claims": tuple(active_external_claims),
            "provider_quota": provider_quota,
            "local_budget": local_budget,
            "attempt_number": attempt_number,
        }
        if runner is not None:
            kwargs["runner"] = runner
        return advance_hosted_task_admission(**kwargs)

    def advance_one_execution_step(
        self,
        dependencies: HostedExecutionDependencies,
    ) -> HostedExecutionAdvanceResult:
        """Advance at most one mutation-capable durable lifecycle boundary.

        Webhook handling never calls this method. Production execution must have been
        explicitly enabled at process startup with an accepted R5C24 activation gate.
        """
        if not self.production_execution_enabled or self.execution_gate is None:
            raise RuntimeError(
                "Platform v2 production execution is disabled for this hosted process"
            )
        coordinator = HostedExecutionCoordinator(
            root=self.root,
            repo=self.repo,
            trusted_actors=self.trusted_actors,
            gate=self.execution_gate,
        )
        return coordinator.advance_once(dependencies)

    def preflight_task_event(
        self,
        event_id: str,
        *,
        runner=None,
    ) -> HostedTaskPreflightResult:
        """Freshly hydrate one captured task without classifier/provider execution."""
        with self._lock:
            record = self.task_authority_store.load().get(event_id)
        if record is None:
            return HostedTaskPreflightResult(
                HostedTaskPreflightDisposition.REJECTED,
                "no captured task authority exists for durable event",
                str(event_id),
            )
        kwargs = {
            "record": record,
            "trusted_actors": self.trusted_actors,
            "repo": self.repo,
        }
        if runner is not None:
            kwargs["runner"] = runner
        return preflight_captured_task(**kwargs)



class Handler(BaseHTTPRequestHandler):
    runtime: HostedV2Substrate

    def _respond_json(self, status: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, sort_keys=True).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:  # noqa: N802
        if self.path != "/healthz":
            self.send_error(404)
            return
        self._respond_json(200, self.runtime.health_snapshot())

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/webhook":
            self.send_error(404)
            return
        try:
            size = int(self.headers.get("Content-Length") or "0")
        except ValueError:
            self._respond_json(400, {"error": "invalid payload size"})
            return
        if size <= 0 or size > MAX_PAYLOAD_BYTES:
            self._respond_json(400, {"error": "invalid payload size"})
            return
        raw = self.rfile.read(size)
        status, payload = self.runtime.handle_webhook(
            headers={key: value for key, value in self.headers.items()},
            raw=raw,
        )
        self._respond_json(status, payload)

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"[platform-v2-webhook] {fmt % args}", flush=True)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--repo", default=DEFAULT_REPO)
    parser.add_argument(
        "--bind",
        default=os.environ.get("SKYFORGE_ORCHESTRATOR_BIND", "127.0.0.1"),
    )
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    # Compatibility-only legacy service arguments.  They do not enable work.
    parser.add_argument("--debounce-seconds", type=int, default=12)
    parser.add_argument("--min-dispatch-seconds", type=int, default=90)
    parser.add_argument("--max-parent-turns", type=int, default=12)
    parser.add_argument(
        "--auto-merge",
        action="store_true",
        default=os.environ.get("SKYFORGE_ORCHESTRATOR_AUTO_MERGE") == "1",
    )
    parser.add_argument(
        "--require-webhook-secret",
        action="store_true",
        default=os.environ.get("SKYFORGE_REQUIRE_WEBHOOK_SECRET") == "1",
    )
    parser.add_argument(
        "--startup-reconcile",
        action="store_true",
        default=os.environ.get("SKYFORGE_STARTUP_RECONCILE") == "1",
    )
    parser.add_argument(
        "--enable-production-execution",
        action="store_true",
        default=os.environ.get("SKYFORGE_V2_PRODUCTION_EXECUTION") == "1",
        help="Enable the R5C24 mutation-capable coordinator only with accepted activation evidence.",
    )
    parser.add_argument(
        "--activation-evidence",
        type=Path,
        default=(
            Path(os.environ["SKYFORGE_V2_ACTIVATION_EVIDENCE"])
            if os.environ.get("SKYFORGE_V2_ACTIVATION_EVIDENCE")
            else None
        ),
        help="R5C24 activation evidence JSON; required when production execution is enabled.",
    )
    args = parser.parse_args(argv)
    if args.auto_merge:
        parser.error("auto-merge is controlled only by frozen task authority")
    if args.enable_production_execution and args.activation_evidence is None:
        parser.error(
            "--enable-production-execution requires --activation-evidence"
        )
    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    execution_gate = None
    if args.enable_production_execution:
        execution_gate = load_hosted_execution_gate(
            args.activation_evidence,
            root=args.root,
        )
        if not execution_gate.enabled:
            raise RuntimeError(
                "production execution activation evidence is blocked: "
                + "; ".join(execution_gate.blockers)
            )
    runtime = HostedV2Substrate(
        args.root,
        repo=args.repo,
        require_webhook_secret=args.require_webhook_secret,
        startup_reconcile=args.startup_reconcile,
        production_execution_requested=args.enable_production_execution,
        execution_gate=execution_gate,
    )
    Handler.runtime = runtime
    server = ThreadingHTTPServer((args.bind, args.port), Handler)
    print(
        (
            f"[platform-v2] gated execution-capable hosted runtime listening on "
            if runtime.production_execution_enabled
            else f"[platform-v2] read-only hosted substrate listening on "
        )
        + f"http://{args.bind}:{args.port}/webhook for {args.repo}",
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
