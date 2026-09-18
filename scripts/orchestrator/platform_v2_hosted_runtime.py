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
from v2.hosted_state import HostedIngressState, HostedStateStore, ingest_event
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
    ) -> None:
        self.root = Path(root).resolve()
        self.repo = str(repo).strip()
        self.require_webhook_secret = bool(require_webhook_secret)
        self.startup_reconcile = bool(startup_reconcile)
        self.webhook_secret = webhook_secret or os.environ.get("SKYFORGE_WEBHOOK_SECRET")
        self.trusted_actors = trusted_actors or _trusted_actors()
        self.store = HostedStateStore.for_root(self.root)
        self.task_authority_store = TaskAuthorityEventStore.for_root(self.root)
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
            return {
                "status": "ok",
                "controller": "platform-v2",
                "runtime_mode": "hosted-substrate-read-only",
                "schema_version": 1,
                "repo": self.repo,
                "mutation_authority": False,
                "worker_dispatch_enabled": False,
                "remote_effect_execution_enabled": False,
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
                "ordinary_v2_mutation_authority": False,
                "task_authority_capture_enabled": True,
                "task_authority_record_count": len(authority_ledger.records),
                "task_preflight_enabled": True,
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
                        "mutation_authority": False,
                    }
                next_state = self.state.with_rejected_control(delivery_id)
                self.store.save(next_state)
                self.state = next_state
            return 202, {
                "accepted": False,
                "control": control,
                "read_only": True,
                "mutation_authority": False,
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
                "mutation_authority": False,
            }
        except StateStoreError:
            return 503, {
                "accepted": False,
                "error": "task authority capture persistence unavailable",
                "mutation_authority": False,
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
                "mutation_authority": False,
            }

        return 202, {
            "accepted": event.actionable,
            "reason": event.reason,
            "semantic_replay_suppressed": transition.semantic_replay_suppressed,
            "task_authority_recorded": authority_record is not None,
            "mutation_authority": False,
        }

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
    args = parser.parse_args(argv)
    if args.auto_merge:
        parser.error("R5B hosted substrate has no merge/mutation authority")
    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    runtime = HostedV2Substrate(
        args.root,
        repo=args.repo,
        require_webhook_secret=args.require_webhook_secret,
        startup_reconcile=args.startup_reconcile,
    )
    Handler.runtime = runtime
    server = ThreadingHTTPServer((args.bind, args.port), Handler)
    print(
        f"[platform-v2] read-only hosted substrate listening on "
        f"http://{args.bind}:{args.port}/webhook for {args.repo}",
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
