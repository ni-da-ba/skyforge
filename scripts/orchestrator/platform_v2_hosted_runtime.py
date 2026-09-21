#!/usr/bin/env python3
"""Read-only hosted substrate for the Platform-v2 control plane.

This process is intentionally incapable of repository/provider mutation.  It accepts the
production webhook/health transport contract, verifies GitHub delivery identity, persists
a v2 durable inbox, and projects legacy authority for later cutover.  Ordinary task
execution is not enabled in R5B.
"""

from __future__ import annotations

import argparse
from dataclasses import replace
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import hmac
import json
import os
from pathlib import Path
import re
import threading
from typing import Any, Mapping
from urllib.parse import unquote, urlsplit

from v2.cutover import LegacyOperationalProjection
from v2.development_read_model import build_development_snapshot
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
    read_checkout_head,
)
from v2.hosted_execution_driver import (
    HostedExecutionDriver,
    ProductionHostedDependencyFactory,
)
from v2.hosted_completion import HostedCompletionStatus, HostedCompletionStore
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
from v2.objective_ingress import (
    DevelopmentApiObjectiveSource,
    ObjectiveProposalStore,
    parse_objective_comment,
)
from v2.objective_command import (
    ObjectiveCommandPhase,
    ObjectiveCommandStore,
    advance_objective_command,
    prepare_objective_command,
    reconcile_pending_objective_commands,
)
from v2.objective_intake import load_manifest
from v2.human_review import (
    DevelopmentApiHumanReviewSource,
    HumanReviewStore,
    HumanReviewSubmission,
    HumanReviewVerdict,
    evaluate_repeat_review_readiness,
    parse_human_review_comment,
)
from v2.human_review_command import (
    HumanReviewCommandPhase,
    HumanReviewCommandStore,
    advance_human_review_command,
    prepare_human_review_command,
    reconcile_pending_human_review_commands,
)
from v2.events import DurableEvent
from v2.external import ControllerIssueOwner
from v2.external_service import (
    EXTERNAL_CLAIMS_RELATIVE_PATH,
    ExternalClaimLedger,
    ExternalClaimStore,
    apply_external_control,
    parse_external_control,
)
from v2.inbox import InboxState
from v2.ingress import (
    classify_legacy_compatible_control,
    classify_legacy_compatible_event,
    reclassify_legacy_compatible_audit_event,
    verify_github_signature,
)
from v2.review_artifacts import (
    ReviewArtifactCatalog,
    ReviewArtifactError,
    ReviewArtifactKind,
    retrieve_file_artifact,
    validate_artifact_source,
)
from v2.roadmap_service import RoadmapAuthorityStore
from v2.state_store import JsonStateStoreAdapter, StateStoreError
from v2.task_event_composition import (
    TaskAuthorityEventStore,
    capture_task_authority_event,
)


DEFAULT_REPO = "ni-da-ba/skyforge"
DEFAULT_PORT = 3000
MAX_PAYLOAD_BYTES = 5_000_000
MAX_DOMAIN_PAYLOAD_BYTES = 256_000
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


def _review_text_tuple(
    value: Any,
    label: str,
    *,
    required: bool,
) -> tuple[str, ...]:
    if value is None and not required:
        return ()
    if not isinstance(value, list):
        raise ValueError(f"{label} must be a list")
    result = tuple(str(item or "").strip() for item in value)
    if any(not item for item in result):
        raise ValueError(f"{label} entries must be non-empty strings")
    if required and not result:
        raise ValueError(f"{label} must not be empty")
    return result


class HostedV2Substrate:
    def __init__(
        self,
        root: Path,
        *,
        repo: str,
        require_webhook_secret: bool,
        startup_reconcile: bool,
        webhook_secret: str | None = None,
        development_api_token: str | None = None,
        development_write_token: str | None = None,
        development_write_actor: str | None = None,
        trusted_actors: tuple[str, ...] | None = None,
        production_execution_requested: bool = False,
        execution_gate: HostedExecutionGateDecision | None = None,
    ) -> None:
        self.root = Path(root).resolve()
        self.repo = str(repo).strip()
        self.require_webhook_secret = bool(require_webhook_secret)
        self.startup_reconcile = bool(startup_reconcile)
        self.webhook_secret = webhook_secret or os.environ.get("SKYFORGE_WEBHOOK_SECRET")
        self.development_api_token = (
            development_api_token
            if development_api_token is not None
            else os.environ.get("SKYFORGE_DEVELOPMENT_API_TOKEN", "")
        ).strip()
        self.development_write_token = (
            development_write_token
            if development_write_token is not None
            else os.environ.get("SKYFORGE_DEVELOPMENT_WRITE_TOKEN", "")
        ).strip()
        self.development_write_actor = (
            development_write_actor
            if development_write_actor is not None
            else os.environ.get("SKYFORGE_DEVELOPMENT_WRITE_ACTOR", "")
        ).strip().lower()
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
        self.external_claim_store = ExternalClaimStore.for_root(self.root)
        self.objective_proposal_store = ObjectiveProposalStore.for_root(self.root)
        self.objective_command_store = ObjectiveCommandStore.for_root(self.root)
        self.human_review_store = HumanReviewStore.for_root(self.root)
        self.human_review_command_store = HumanReviewCommandStore.for_root(self.root)
        self.roadmap_authority_store = RoadmapAuthorityStore.for_root(self.root)
        self.task_plan_store = HostedTaskPlanStore.for_root(self.root)
        self.admission_store = HostedAdmissionStore.for_root(self.root)
        worker_state_dir = self.root / ".skyforge-platform-v2"
        self.worker_read_store = JsonStateStoreAdapter(
            path=worker_state_dir / "worker-runs.json",
            backup_path=worker_state_dir / "worker-runs.json.bak",
        )
        self.execution_driver = None
        self._lock = threading.RLock()
        self.state = self.store.load()
        self.validate_environment()
        self.startup_audit_signal_reclassifications = self._migrate_pending_audit_signals()
        self.startup_external_claim_migrations = self._migrate_external_claims_once()
        self.startup_objective_command_reconciliations = (
            self._reconcile_pending_objective_commands()
        )
        self.startup_human_review_command_reconciliations = (
            self._reconcile_pending_human_review_commands()
        )
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
        if self.development_api_token and len(self.development_api_token) < 32:
            raise RuntimeError(
                "SKYFORGE_DEVELOPMENT_API_TOKEN must be at least 32 characters when configured."
            )
        if bool(self.development_write_token) != bool(self.development_write_actor):
            raise RuntimeError(
                "SKYFORGE_DEVELOPMENT_WRITE_TOKEN and "
                "SKYFORGE_DEVELOPMENT_WRITE_ACTOR must be configured together."
            )
        if self.development_write_token and len(self.development_write_token) < 32:
            raise RuntimeError(
                "SKYFORGE_DEVELOPMENT_WRITE_TOKEN must be at least 32 characters when configured."
            )
        if (
            self.development_write_actor
            and self.development_write_actor not in self.trusted_actors
        ):
            raise RuntimeError(
                "SKYFORGE_DEVELOPMENT_WRITE_ACTOR must be a trusted configured actor."
            )

    def _reconcile_pending_objective_commands(self) -> int:
        """Finish crash-interrupted objective proposal persistence on startup."""
        pending = self.objective_command_store.load().pending
        if not pending:
            return 0
        reconciled = reconcile_pending_objective_commands(
            command_store=self.objective_command_store,
            proposal_store=self.objective_proposal_store,
        )
        return len(reconciled)

    def _reconcile_pending_human_review_commands(self) -> int:
        """Finish any crash-interrupted review -> roadmap transition on startup."""
        pending = self.human_review_command_store.load().pending
        if not pending:
            return 0
        manifest = load_manifest(self.root)
        reconciled = reconcile_pending_human_review_commands(
            command_store=self.human_review_command_store,
            review_store=self.human_review_store,
            roadmap_store=self.roadmap_authority_store,
            manifest=manifest,
        )
        return len(reconciled)

    def _effective_legacy_projection(self) -> dict[str, Any]:
        """Overlay canonical v2 roadmap authority onto the cutover projection when present."""
        projection = dict(self.state.legacy_projection or {})
        if not self.roadmap_authority_store.adapter.path.is_file():
            return projection
        manifest = load_manifest(self.root)
        ledger = self.roadmap_authority_store.load()
        ledger.validate_manifest(manifest)
        projection["roadmap"] = {
            "roadmap_id": ledger.roadmap_id,
            "manifest_fingerprint": ledger.manifest_fingerprint,
            "completed_runs": dict(ledger.completed_runs),
            "blocked_nodes": {
                record.node_id: {"reason": record.reason}
                for record in ledger.blocked_nodes
            },
            "active": (
                ledger.active.as_dict() if ledger.active is not None else None
            ),
            "claims_day": ledger.claims_day,
            "claims_today": ledger.claims_today,
        }
        return projection

    def _migrate_pending_audit_signals(self) -> int:
        """Apply current deterministic Audit parsing to durable pre-upgrade events."""
        inbox = self.state.inbox
        migrated = tuple(
            reclassify_legacy_compatible_audit_event(event)
            for event in inbox.pending_events
        )
        changed = sum(before != after for before, after in zip(inbox.pending_events, migrated))
        if not changed:
            return 0
        next_inbox = InboxState(
            pending_events=migrated,
            retired_event_keys=inbox.retired_event_keys,
            completed_authority_event_keys=inbox.completed_authority_event_keys,
            owned_event_keys=inbox.owned_event_keys,
        )
        next_state = HostedIngressState(
            inbox=next_inbox,
            seen_deliveries=self.state.seen_deliveries,
            legacy_projection=self.state.legacy_projection,
            accepted_deliveries=self.state.accepted_deliveries,
            rejected_controls=self.state.rejected_controls,
        )
        self.store.save(next_state)
        self.state = next_state
        return changed

    def _migrate_external_claims_once(self) -> int:
        """Seed V2 external ownership exactly once from the cutover projection."""
        path = self.root / EXTERNAL_CLAIMS_RELATIVE_PATH
        if path.exists():
            return 0
        projection = _read_legacy_projection(self.root)
        ledger = ExternalClaimLedger(tuple(projection.external_claims))
        self.external_claim_store.save(ledger)
        return len(ledger.claims)

    def refresh_legacy_projection(self) -> None:
        projection = _read_legacy_projection(self.root)
        if (self.root / EXTERNAL_CLAIMS_RELATIVE_PATH).exists():
            projection = replace(
                projection,
                external_claims=self.external_claim_store.load().claims,
            )
        with self._lock:
            next_state = self.state.with_projection(projection.as_dict())
            self.store.save(next_state)
            self.state = next_state

    def _controller_issue_owner(self, issue_number: int) -> ControllerIssueOwner:
        """Return concrete controller ownership for external-claim admission."""
        with self._lock:
            plan = self.task_plan_store.load().active
            admission = self.admission_store.load().record
            projection = dict(self.state.legacy_projection or {})

        if plan is not None and plan.issue_number == issue_number:
            return ControllerIssueOwner.PENDING_WORKER
        if admission is not None and admission.issue_number == issue_number:
            return ControllerIssueOwner.PENDING_WORKER

        roadmap = projection.get("roadmap") or {}
        active = roadmap.get("active") if isinstance(roadmap, Mapping) else None
        if isinstance(active, Mapping) and active.get("issue_number") == issue_number:
            return ControllerIssueOwner.ACTIVE_ROADMAP

        pending = projection.get("pending_worker")
        if isinstance(pending, Mapping) and pending.get("authority_issue") == issue_number:
            return ControllerIssueOwner.PENDING_WORKER

        decision = projection.get("pending_decision")
        if isinstance(decision, Mapping) and issue_number in (decision.get("task_issue_numbers") or []):
            return ControllerIssueOwner.PENDING_DECISION

        managed = projection.get("managed") or {}
        if isinstance(managed, Mapping):
            for value in managed.values():
                if isinstance(value, Mapping) and value.get("authority_issue") == issue_number:
                    return ControllerIssueOwner.MANAGED_PR
        return ControllerIssueOwner.NONE

    def _retire_terminal_external_claim(
        self,
        event_name: str,
        payload: Mapping[str, Any],
    ) -> bool:
        """Retire exact external ownership from signed terminal GitHub facts."""
        action = str(payload.get("action") or "").lower()
        ledger = self.external_claim_store.load()
        issue_to_release = None

        if event_name == "pull_request" and action == "closed":
            pull_request = payload.get("pull_request") or {}
            number = payload.get("number")
            if number is None and isinstance(pull_request, Mapping):
                number = pull_request.get("number")
            if isinstance(number, int) and not isinstance(number, bool):
                for claim in ledger.claims:
                    if claim.pr_number == number:
                        issue_to_release = claim.issue_number
                        break
        elif event_name == "issues" and action == "closed":
            issue = payload.get("issue") or {}
            number = issue.get("number") if isinstance(issue, Mapping) else None
            if isinstance(number, int) and not isinstance(number, bool):
                claim = ledger.get(number)
                if claim is not None and claim.pr_number is None:
                    issue_to_release = claim.issue_number

        if issue_to_release is None:
            return False
        updated, changed = ledger.release(issue_to_release)
        if changed:
            self.external_claim_store.save(updated)
            self.refresh_legacy_projection()
        return changed

    def attach_execution_driver(self, driver) -> None:
        if not self.production_execution_enabled:
            raise RuntimeError("cannot attach execution driver while production execution is disabled")
        self.execution_driver = driver

    def _signal_execution_driver(self) -> None:
        driver = self.execution_driver
        if driver is not None:
            driver.wake()

    def health_snapshot(self) -> dict[str, Any]:
        with self._lock:
            state = self.state
            projection = self._effective_legacy_projection()
            external = [claim.as_dict() for claim in self.external_claim_store.load().claims]
            roadmap = projection.get("roadmap") or {}
            authority_ledger = self.task_authority_store.load()
            task_plan = self.task_plan_store.load()
            active_plan = task_plan.active
            admission = self.admission_store.load().record
            completions = HostedCompletionStore.for_root(self.root).load()
            pending_completion = completions.pending()
            objective_ledger = self.objective_proposal_store.load()
            latest_objective = objective_ledger.records[-1] if objective_ledger.records else None
            human_review_ledger = self.human_review_store.load()
            latest_human_review = (
                human_review_ledger.records[-1] if human_review_ledger.records else None
            )
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
                "startup_audit_signal_reclassifications": self.startup_audit_signal_reclassifications,
                "startup_external_claim_migrations": self.startup_external_claim_migrations,
                "startup_objective_command_reconciliations": (
                    self.startup_objective_command_reconciliations
                ),
                "startup_human_review_command_reconciliations": (
                    self.startup_human_review_command_reconciliations
                ),
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
                "objective_intake_enabled": True,
                "pending_objective_command_count": len(
                    self.objective_command_store.load().pending
                ),
                "objective_proposal_count": len(objective_ledger.records),
                "latest_objective_disposition": (
                    latest_objective.compiled.disposition.value if latest_objective else ""
                ),
                "latest_objective_proposal_id": (
                    latest_objective.proposal_id if latest_objective else ""
                ),
                "human_review_ingress_enabled": True,
                "development_read_api_enabled": bool(self.development_api_token),
                "development_write_api_enabled": bool(self.development_write_token),
                "pending_human_review_command_count": len(
                    self.human_review_command_store.load().pending
                ),
                "human_review_count": len(human_review_ledger.records),
                "latest_human_review_id": (
                    latest_human_review.review_id if latest_human_review else ""
                ),
                "latest_human_review_gate_id": (
                    latest_human_review.gate_id if latest_human_review else ""
                ),
                "latest_human_review_artifact_id": (
                    latest_human_review.artifact_id if latest_human_review else ""
                ),
                "latest_human_review_verdict": (
                    latest_human_review.verdict.value if latest_human_review else ""
                ),
                "latest_human_review_next_boundary": (
                    latest_human_review.next_boundary if latest_human_review else ""
                ),
                "task_preflight_enabled": True,
                "hosted_task_planning_enabled": True,
                "explicit_classifier_proposal_enabled": True,
                "automatic_classifier_execution_enabled": (
                    self.production_execution_enabled and self.execution_driver is not None
                ),
                "explicit_task_admission_enabled": True,
                "automatic_task_admission_enabled": (
                    self.production_execution_enabled and self.execution_driver is not None
                ),
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
                "hosted_completion_count": len(completions.records),
                "hosted_completion_cleaned_count": sum(
                    record.status is HostedCompletionStatus.CLEANED
                    for record in completions.records
                ),
                "hosted_completion_pending_cleanup_id": (
                    pending_completion.completion_id
                    if pending_completion is not None
                    else ""
                ),
                "production_execution_driver": (
                    self.execution_driver.snapshot().as_dict()
                    if self.execution_driver is not None
                    else {
                        "enabled": False,
                        "running": False,
                        "wake_count": 0,
                        "advance_count": 0,
                        "last_disposition": "",
                        "last_reason": "",
                        "last_durable_identity": "",
                        "last_advance_at": "",
                        "last_error": "",
                    }
                ),
                "production_execution_budget": (
                    self.execution_driver.budget_snapshot()
                    if self.execution_driver is not None
                    else {}
                ),
            }

    def development_snapshot(self) -> dict[str, Any]:
        """Project current durable workflow truth for authorized thin clients."""
        with self._lock:
            health = self.health_snapshot()
            state = self.state
            projection = self._effective_legacy_projection()
            objectives = self.objective_proposal_store.load()
            reviews = self.human_review_store.load()
            plan = self.task_plan_store.load().active
            admission = self.admission_store.load().record
            worker_raw = self.worker_read_store.load().as_dict()
            if worker_raw not in ({}, None) and worker_raw.get("schema_version") != 1:
                raise ValueError("invalid worker-run read ledger")
            worker_records = worker_raw.get("records") or []
            if not isinstance(worker_records, list):
                raise ValueError("worker-run read records must be a list")
            completions = HostedCompletionStore.for_root(self.root).load()
            external = self.external_claim_store.load()
            artifacts = ReviewArtifactCatalog.for_root(self.root)
            for artifact in artifacts.records:
                validate_artifact_source(self.root, artifact)

            human_gates: list[dict[str, Any]] = []
            manifest_path = (
                self.root / "docs" / "agent-state" / "ORCHESTRATOR_ROADMAP.json"
            )
            if manifest_path.is_file():
                manifest = load_manifest(self.root)
                roadmap_view = projection.get("roadmap") or {}
                blocked = roadmap_view.get("blocked_nodes") or {}
                completed = roadmap_view.get("completed_runs") or {}
                if not isinstance(blocked, Mapping) or not isinstance(completed, Mapping):
                    raise ValueError("projected roadmap block/completion state is invalid")
                for node in manifest.nodes:
                    if node.kind.value != "gate" or node.node_id not in blocked:
                        continue
                    if int(completed.get(node.node_id) or 0) >= node.max_runs:
                        continue
                    block = blocked.get(node.node_id)
                    reason = (
                        str(block.get("reason") or "")
                        if isinstance(block, Mapping)
                        else str(block or "")
                    )
                    human_gates.append(
                        {
                            "gate_id": node.node_id,
                            "lane": str(node.lane or ""),
                            "message": str(node.human_message or ""),
                            "blocked_reason": reason,
                        }
                    )

            runtime = {
                "status": health["status"],
                "controller": health["controller"],
                "runtime_mode": health["runtime_mode"],
                "state_digest": health["state_digest"],
                "pending_event_count": health["pending_event_count"],
                "production_execution_enabled": health["production_execution_enabled"],
                "development_write_api_enabled": health[
                    "development_write_api_enabled"
                ],
                "production_execution_gate_digest": health[
                    "production_execution_gate_digest"
                ],
                "production_execution_gate_blockers": list(
                    health["production_execution_gate_blockers"]
                ),
                "production_execution_driver": health["production_execution_driver"],
                "production_execution_budget": health["production_execution_budget"],
            }
            snapshot = build_development_snapshot(
                repo=self.repo,
                checkout_head_sha=read_checkout_head(self.root),
                legacy_projection=projection,
                objective_records=(
                    record.as_dict() for record in objectives.records
                ),
                active_plan=(plan.as_dict() if plan is not None else None),
                admission=(
                    admission.as_dict() if admission is not None else None
                ),
                worker_records=worker_records,
                completion_records=(
                    record.as_dict() for record in completions.records
                ),
                external_claims=(
                    claim.as_dict() for claim in external.claims
                ),
                artifact_records=(
                    artifact.as_dict() for artifact in artifacts.records
                ),
                human_reviews=(
                    record.as_dict() for record in reviews.records
                ),
                human_gates=human_gates,
                runtime=runtime,
            )
            return snapshot.as_dict()

    def _development_api_auth_error(
        self,
        authorization: str | None,
    ) -> tuple[int, dict[str, Any]] | None:
        token = self.development_api_token
        if not token:
            return 503, {"error": "development API is not configured"}
        supplied = str(authorization or "")
        expected = f"Bearer {token}"
        if not hmac.compare_digest(supplied, expected):
            return 401, {"error": "development API authorization required"}
        return None

    def _development_write_auth_error(
        self,
        authorization: str | None,
    ) -> tuple[int, dict[str, Any]] | None:
        token = self.development_write_token
        actor = self.development_write_actor
        if not token or not actor:
            return 503, {"error": "development write API is not configured"}
        supplied = str(authorization or "")
        expected = f"Bearer {token}"
        if not hmac.compare_digest(supplied, expected):
            return 401, {"error": "development write authorization required"}
        return None

    def _validated_artifact_catalog(self) -> ReviewArtifactCatalog:
        catalog = ReviewArtifactCatalog.for_root(self.root)
        for record in catalog.records:
            validate_artifact_source(self.root, record)
        return catalog

    def handle_development_read(
        self,
        authorization: str | None,
    ) -> tuple[int, dict[str, Any]]:
        auth_error = self._development_api_auth_error(authorization)
        if auth_error is not None:
            return auth_error
        try:
            return 200, self.development_snapshot()
        except (
            OSError,
            RuntimeError,
            StateStoreError,
            ReviewArtifactError,
            ValueError,
        ) as exc:
            return 503, {
                "error": "development state is unavailable",
                "failure_kind": type(exc).__name__,
            }

    def handle_artifact_list(
        self,
        authorization: str | None,
    ) -> tuple[int, dict[str, Any]]:
        auth_error = self._development_api_auth_error(authorization)
        if auth_error is not None:
            return auth_error
        try:
            catalog = self._validated_artifact_catalog()
            return 200, {
                "schema_version": 1,
                "catalog_digest": catalog.digest,
                "artifact_count": len(catalog.records),
                "artifacts": [record.as_dict() for record in catalog.records],
            }
        except (OSError, ReviewArtifactError, ValueError) as exc:
            return 503, {
                "error": "artifact catalog is unavailable",
                "failure_kind": type(exc).__name__,
            }

    def handle_artifact_get(
        self,
        authorization: str | None,
        artifact_id: str,
    ) -> tuple[int, dict[str, Any]]:
        auth_error = self._development_api_auth_error(authorization)
        if auth_error is not None:
            return auth_error
        try:
            catalog = self._validated_artifact_catalog()
            record = catalog.get(artifact_id)
            if record is None:
                return 404, {"error": "artifact not found"}
            return 200, record.as_dict()
        except (OSError, ReviewArtifactError, ValueError) as exc:
            return 503, {
                "error": "artifact catalog is unavailable",
                "failure_kind": type(exc).__name__,
            }

    def handle_artifact_content(
        self,
        authorization: str | None,
        artifact_id: str,
    ) -> tuple[int, str | None, bytes | dict[str, Any]]:
        auth_error = self._development_api_auth_error(authorization)
        if auth_error is not None:
            status, payload = auth_error
            return status, None, payload
        try:
            catalog = self._validated_artifact_catalog()
            record = catalog.get(artifact_id)
            if record is None:
                return 404, None, {"error": "artifact not found"}
            if record.kind is not ReviewArtifactKind.FILE or record.file is None:
                return 409, None, {
                    "error": "artifact is interactive and has no direct file content"
                }
            payload = retrieve_file_artifact(self.root, record)
            return 200, record.file.media_type, payload
        except (OSError, ReviewArtifactError, ValueError) as exc:
            return 503, None, {
                "error": "artifact content is unavailable",
                "failure_kind": type(exc).__name__,
            }

    def handle_objective_submit(
        self,
        authorization: str | None,
        payload: Mapping[str, Any],
        *,
        client: str | None,
    ) -> tuple[int, dict[str, Any]]:
        auth_error = self._development_write_auth_error(authorization)
        if auth_error is not None:
            return auth_error
        if not isinstance(payload, Mapping):
            return 400, {"error": "objective payload must be an object"}

        allowed = {"request_id", "objective"}
        unknown = sorted(str(key) for key in payload if key not in allowed)
        if unknown:
            return 400, {
                "error": "objective payload contains unsupported fields",
                "unsupported_fields": unknown,
            }

        try:
            request_id = str(payload.get("request_id") or "").strip()
            existing = self.objective_command_store.load().get(request_id)
            submitted_at = (
                existing.proposal.source.submitted_at
                if existing is not None
                and isinstance(
                    existing.proposal.source,
                    DevelopmentApiObjectiveSource,
                )
                else datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
            )
            source = DevelopmentApiObjectiveSource(
                repo=self.repo,
                request_id=request_id,
                actor=self.development_write_actor,
                client=str(client or "development-api").strip().lower(),
                submitted_at=submitted_at,
                objective_text=payload.get("objective"),
            )
        except (TypeError, ValueError) as exc:
            return 400, {"error": f"invalid objective payload: {exc}"}

        with self._lock:
            try:
                existing = self.objective_command_store.load().get(request_id)
                was_reconciled = (
                    existing is not None
                    and existing.phase is ObjectiveCommandPhase.RECONCILED
                )
                prepare_objective_command(
                    store=self.objective_command_store,
                    source=source,
                    root=self.root,
                )
                complete = advance_objective_command(
                    command_store=self.objective_command_store,
                    proposal_store=self.objective_proposal_store,
                    request_id=request_id,
                )
            except StateStoreError as exc:
                return 503, {
                    "error": "objective durable state is unavailable",
                    "failure_kind": type(exc).__name__,
                }
            except (OSError, RuntimeError) as exc:
                return 503, {
                    "error": "objective compilation state is unavailable",
                    "failure_kind": type(exc).__name__,
                }
            except ValueError as exc:
                phase = None
                durable = self.objective_command_store.load().get(request_id)
                if durable is not None:
                    phase = durable.phase.value
                return 409, {
                    "error": str(exc),
                    "command_phase": phase,
                }

        if (
            complete.phase is ObjectiveCommandPhase.RECONCILED
            and not was_reconciled
        ):
            self._signal_execution_driver()

        compiled = complete.proposal.compiled.as_dict()
        return (200 if existing is not None else 202), {
            "accepted": True,
            "idempotent_replay": existing is not None,
            "request_id": request_id,
            "command_id": complete.command_id,
            "command_phase": complete.phase.value,
            "proposal_id": complete.proposal.proposal_id,
            "objective": complete.proposal.source.objective_text,
            "objective_disposition": complete.proposal.compiled.disposition.value,
            "reason": complete.proposal.compiled.reason,
            "candidate_task": compiled.get("candidate_task"),
            "human_gate": compiled.get("human_gate"),
            "executable_task_authority": False,
            "task_authority_recorded": False,
            "actor": complete.proposal.source.actor,
            "client": complete.proposal.source.client,
        }

    def handle_human_review_submit(
        self,
        authorization: str | None,
        payload: Mapping[str, Any],
        *,
        client: str | None,
    ) -> tuple[int, dict[str, Any]]:
        auth_error = self._development_write_auth_error(authorization)
        if auth_error is not None:
            return auth_error
        if not isinstance(payload, Mapping):
            return 400, {"error": "human review payload must be an object"}
        if "actor" in payload or "source" in payload:
            return 400, {"error": "human review actor/source identity is server-bound"}

        try:
            request_id = str(payload.get("request_id") or "").strip()
            existing = self.human_review_command_store.load().get(request_id)
            submitted_at = (
                existing.review.source.submitted_at
                if existing is not None
                and isinstance(existing.review.source, DevelopmentApiHumanReviewSource)
                else datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
            )
            source = DevelopmentApiHumanReviewSource(
                repo=self.repo,
                request_id=request_id,
                actor=self.development_write_actor,
                client=str(client or "development-api").strip().lower(),
                submitted_at=submitted_at,
            )
            deferred = payload.get("deferred_product_work")
            if not isinstance(deferred, bool):
                raise ValueError("deferred_product_work must be a boolean")
            prior_review_id = payload.get("prior_review_id")
            if prior_review_id is not None:
                prior_review_id = str(prior_review_id).strip() or None
            review = HumanReviewSubmission(
                source=source,
                gate_id=payload.get("gate_id"),
                artifact_id=payload.get("artifact_id"),
                source_sha=payload.get("source_sha"),
                verdict=HumanReviewVerdict(str(payload.get("verdict") or "")),
                findings=_review_text_tuple(
                    payload.get("findings"),
                    "findings",
                    required=True,
                ),
                positive_findings=_review_text_tuple(
                    payload.get("positive_findings"),
                    "positive_findings",
                    required=False,
                ),
                material_delta=payload.get("material_delta"),
                next_boundary=payload.get("next_boundary"),
                deferred_product_work=deferred,
                prior_review_id=prior_review_id,
            )
        except (TypeError, ValueError) as exc:
            return 400, {"error": f"invalid human review payload: {exc}"}

        with self._lock:
            try:
                existing = self.human_review_command_store.load().get(request_id)
                was_reconciled = (
                    existing is not None
                    and existing.phase is HumanReviewCommandPhase.RECONCILED
                )
                if existing is None:
                    catalog = ReviewArtifactCatalog.for_root(self.root)
                    artifact = catalog.get(review.artifact_id)
                    if artifact is None:
                        return 409, {"error": "human review artifact is not registered"}
                    if artifact.source_sha != review.source_sha:
                        return 409, {
                            "error": (
                                "human review source SHA does not match registered artifact"
                            )
                        }
                    validate_artifact_source(self.root, artifact)

                    latest = self.human_review_store.load().latest_for_gate(
                        review.gate_id
                    )
                    if latest is not None:
                        readiness = evaluate_repeat_review_readiness(
                            self.human_review_store.load(),
                            gate_id=review.gate_id,
                            artifact_id=review.artifact_id,
                            source_sha=review.source_sha,
                            material_delta=review.material_delta,
                            prior_review_id=review.prior_review_id,
                        )
                        if not readiness.ready:
                            return 409, {
                                "error": readiness.reason,
                                "repeat_review_disposition": (
                                    readiness.disposition.value
                                ),
                                "prior_review_id": readiness.prior_review_id,
                            }

                manifest = load_manifest(self.root)
                if not self.roadmap_authority_store.adapter.path.is_file():
                    return 409, {
                        "error": (
                            "canonical Platform-v2 roadmap authority is unavailable"
                        )
                    }
                roadmap = self.roadmap_authority_store.load()
                prepared = prepare_human_review_command(
                    store=self.human_review_command_store,
                    review=review,
                    roadmap=roadmap,
                    manifest=manifest,
                )
                complete = advance_human_review_command(
                    command_store=self.human_review_command_store,
                    review_store=self.human_review_store,
                    roadmap_store=self.roadmap_authority_store,
                    manifest=manifest,
                    request_id=request_id,
                )
            except ReviewArtifactError as exc:
                return 503, {
                    "error": "human review artifact provenance is unavailable",
                    "failure_kind": type(exc).__name__,
                }
            except StateStoreError as exc:
                return 503, {
                    "error": "human review durable state is unavailable",
                    "failure_kind": type(exc).__name__,
                }
            except ValueError as exc:
                phase = None
                durable = self.human_review_command_store.load().get(request_id)
                if durable is not None:
                    phase = durable.phase.value
                return 409, {
                    "error": str(exc),
                    "command_phase": phase,
                }

        if (
            complete.phase is HumanReviewCommandPhase.RECONCILED
            and not was_reconciled
        ):
            self._signal_execution_driver()
        return (200 if existing is not None else 202), {
            "accepted": True,
            "idempotent_replay": existing is not None,
            "request_id": request_id,
            "command_id": complete.command_id,
            "command_phase": complete.phase.value,
            "review_id": complete.review.review_id,
            "gate_id": complete.review.gate_id,
            "artifact_id": complete.review.artifact_id,
            "source_sha": complete.review.source_sha,
            "verdict": complete.review.verdict.value,
            "roadmap_disposition": complete.roadmap_disposition.value,
            "actor": complete.review.source.actor,
            "client": complete.review.source.client,
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

        normalized_headers = {str(key).lower(): value for key, value in headers.items()}

        if self.require_webhook_secret:
            if not verify_github_signature(
                str(self.webhook_secret or ""),
                raw,
                normalized_headers.get("x-hub-signature-256"),
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

        event_name = str(normalized_headers.get("x-github-event") or "")
        delivery_id = normalized_headers.get("x-github-delivery")

        try:
            human_review = parse_human_review_comment(
                event_name=event_name,
                payload=payload,
                repo=self.repo,
                trusted_actors=self.trusted_actors,
            )
        except ValueError as exc:
            return 409, {
                "accepted": False,
                "error": f"human review ingress rejected: {exc}",
                "mutation_authority": self.production_execution_enabled,
            }

        if human_review is not None:
            with self._lock:
                if delivery_id and delivery_id in self.state.seen_deliveries:
                    return 200, {
                        "accepted": False,
                        "duplicate": True,
                        "human_review": True,
                        "mutation_authority": self.production_execution_enabled,
                    }
                try:
                    captured = self.human_review_store.capture(human_review)
                except (ValueError, StateStoreError) as exc:
                    return 409 if isinstance(exc, ValueError) else 503, {
                        "accepted": False,
                        "error": (
                            f"human review rejected: {exc}"
                            if isinstance(exc, ValueError)
                            else "human review persistence unavailable"
                        ),
                        "mutation_authority": self.production_execution_enabled,
                    }
                marker_event = DurableEvent(
                    actionable=False,
                    reason="signed human review captured as durable project truth",
                    event="human_review",
                    action=human_review.verdict.value.lower(),
                    head_sha=human_review.source_sha,
                    pr_number=human_review.source.issue_number,
                    source_id=str(human_review.source.comment_id),
                    signal_kind=None,
                    signal_text=None,
                )
                transition = ingest_event(
                    self.state,
                    marker_event,
                    delivery_id=delivery_id,
                )
                if transition.after is not self.state:
                    self.store.save(transition.after)
                    self.state = transition.after
            return 202, {
                "accepted": True,
                "human_review": True,
                "human_review_recorded": captured.created,
                "review_id": captured.record.review_id,
                "gate_id": captured.record.gate_id,
                "artifact_id": captured.record.artifact_id,
                "verdict": captured.record.verdict.value,
                "deferred_product_work": captured.record.deferred_product_work,
                "mutation_authority": self.production_execution_enabled,
            }

        try:
            objective_source = parse_objective_comment(
                event_name=event_name,
                payload=payload,
                repo=self.repo,
                trusted_actors=self.trusted_actors,
            )
        except ValueError as exc:
            return 409, {
                "accepted": False,
                "error": f"objective ingress rejected: {exc}",
                "mutation_authority": self.production_execution_enabled,
            }

        if objective_source is not None:
            with self._lock:
                if delivery_id and delivery_id in self.state.seen_deliveries:
                    return 200, {
                        "accepted": False,
                        "duplicate": True,
                        "objective": True,
                        "mutation_authority": self.production_execution_enabled,
                    }
                try:
                    captured = self.objective_proposal_store.capture(
                        source=objective_source,
                        delivery_id=str(delivery_id or ""),
                        root=self.root,
                    )
                except (ValueError, StateStoreError) as exc:
                    return 409 if isinstance(exc, ValueError) else 503, {
                        "accepted": False,
                        "error": (
                            f"objective proposal rejected: {exc}"
                            if isinstance(exc, ValueError)
                            else "objective proposal persistence unavailable"
                        ),
                        "mutation_authority": self.production_execution_enabled,
                    }
                marker_event = DurableEvent(
                    actionable=False,
                    reason="signed objective proposal captured without task authority",
                    event="objective",
                    action="compile",
                    pr_number=objective_source.issue_number,
                    source_id=str(objective_source.comment_id),
                    signal_kind=None,
                    signal_text=None,
                )
                transition = ingest_event(
                    self.state,
                    marker_event,
                    delivery_id=delivery_id,
                )
                if transition.after is not self.state:
                    self.store.save(transition.after)
                    self.state = transition.after
            return 202, {
                "accepted": True,
                "objective": True,
                "objective_recorded": captured.created,
                "proposal_id": captured.record.proposal_id,
                "objective_disposition": captured.record.compiled.disposition.value,
                "executable_task_authority": False,
                "task_authority_recorded": False,
                "mutation_authority": self.production_execution_enabled,
            }

        # External producer ownership is a V2-native control surface after cutover.
        # Retire exact terminal ownership before processing any ordinary event, then handle
        # claim/release directives without placing them in the work inbox.
        self._retire_terminal_external_claim(event_name, payload)
        try:
            external_control = parse_external_control(
                event=event_name,
                payload=payload,
                trusted_actors=self.trusted_actors,
            )
        except ValueError as exc:
            return 409, {
                "accepted": False,
                "error": f"external producer control rejected: {exc}",
                "mutation_authority": self.production_execution_enabled,
            }

        if external_control is not None:
            with self._lock:
                if delivery_id and delivery_id in self.state.seen_deliveries:
                    return 200, {
                        "accepted": False,
                        "duplicate": True,
                        "external_control": True,
                        "mutation_authority": self.production_execution_enabled,
                    }
                before = self.external_claim_store.load()
                result = apply_external_control(
                    ledger=before,
                    control=external_control,
                    controller_owner=self._controller_issue_owner(external_control.issue_number),
                )
                if result.ledger != before:
                    self.external_claim_store.save(result.ledger)

                if result.accepted:
                    marker = DurableEvent(
                        actionable=False,
                        reason=result.reason,
                        event="external_control",
                        action=external_control.kind.value.lower(),
                        pr_number=external_control.issue_number,
                        source_id=str(external_control.source_comment_id or ""),
                    )
                    transition = ingest_event(
                        self.state,
                        marker,
                        delivery_id=delivery_id,
                    )
                    if transition.after is not self.state:
                        self.store.save(transition.after)
                        self.state = transition.after
                else:
                    next_state = self.state.with_rejected_control(delivery_id)
                    self.store.save(next_state)
                    self.state = next_state

            self.refresh_legacy_projection()
            if result.accepted:
                self._signal_execution_driver()
            return 202, {
                "accepted": result.accepted,
                "external_control": external_control.kind.value,
                "reason": result.reason,
                "external_claim_count": len(result.ledger.claims),
                "mutation_authority": self.production_execution_enabled,
            }

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

        if event.actionable and not transition.semantic_replay_suppressed:
            self._signal_execution_driver()

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
        result = coordinator.advance_once(dependencies)
        # Execution completion may retire an inbox event directly through the durable
        # store. Reload it here so webhook/health state cannot later overwrite that
        # accepted retirement with a stale in-memory snapshot.
        with self._lock:
            self.state = self.store.load()
        return result

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

    def _respond_json(
        self,
        status: int,
        payload: dict[str, Any],
        *,
        headers: Mapping[str, str] | None = None,
    ) -> None:
        body = json.dumps(payload, sort_keys=True).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def _respond_bytes(
        self,
        status: int,
        media_type: str,
        payload: bytes,
        *,
        headers: Mapping[str, str] | None = None,
    ) -> None:
        self.send_response(status)
        self.send_header("Content-Type", media_type)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        for key, value in (headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(payload)

    def _respond_not_modified(self, etag: str) -> None:
        self.send_response(304)
        self.send_header("ETag", etag)
        self.send_header("Cache-Control", "no-store")
        self.end_headers()

    def _console_asset(self, path: str) -> tuple[str, bytes] | None:
        assets = {
            "/console": ("text/html; charset=utf-8", "index.html"),
            "/console/": ("text/html; charset=utf-8", "index.html"),
            "/console/app.js": ("text/javascript; charset=utf-8", "app.js"),
            "/console/styles.css": ("text/css; charset=utf-8", "styles.css"),
        }
        entry = assets.get(path)
        if entry is None:
            return None
        media_type, filename = entry
        content = (
            Path(__file__).resolve().parent
            / "console"
            / filename
        ).read_bytes()
        return media_type, content

    def _respond_console_asset(self, media_type: str, payload: bytes) -> None:
        self._respond_bytes(
            200,
            media_type,
            payload,
            headers={
                "Content-Security-Policy": (
                    "default-src 'self'; script-src 'self'; style-src 'self'; "
                    "connect-src 'self'; img-src 'self' blob:; object-src 'none'; "
                    "base-uri 'none'; frame-ancestors 'none'"
                ),
                "Referrer-Policy": "no-referrer",
                "X-Content-Type-Options": "nosniff",
            },
        )

    def do_GET(self) -> None:  # noqa: N802
        path = urlsplit(self.path).path
        authorization = self.headers.get("Authorization")
        if path == "/healthz":
            self._respond_json(200, self.runtime.health_snapshot())
            return
        console_asset = self._console_asset(path)
        if console_asset is not None:
            media_type, payload = console_asset
            self._respond_console_asset(media_type, payload)
            return
        if path == "/api/v1/development-state":
            status, payload = self.runtime.handle_development_read(authorization)
            if status == 200:
                digest = str(payload.get("snapshot_digest") or "")
                etag = f'"{digest}"' if digest else ""
                if etag and self.headers.get("If-None-Match") == etag:
                    self._respond_not_modified(etag)
                    return
                headers = {"ETag": etag} if etag else None
                self._respond_json(status, payload, headers=headers)
            else:
                self._respond_json(status, payload)
            return
        if path == "/api/v1/artifacts":
            status, payload = self.runtime.handle_artifact_list(authorization)
            self._respond_json(status, payload)
            return
        prefix = "/api/v1/artifacts/"
        if path.startswith(prefix):
            remainder = unquote(path[len(prefix):])
            if remainder.endswith("/content"):
                artifact_id = remainder[:-len("/content")]
                status, media_type, payload = self.runtime.handle_artifact_content(
                    authorization,
                    artifact_id,
                )
                if isinstance(payload, bytes) and media_type is not None:
                    self._respond_bytes(status, media_type, payload)
                else:
                    self._respond_json(status, payload)
                return
            if "/" not in remainder and remainder:
                status, payload = self.runtime.handle_artifact_get(
                    authorization,
                    remainder,
                )
                self._respond_json(status, payload)
                return
        self.send_error(404)

    def do_POST(self) -> None:  # noqa: N802
        path = urlsplit(self.path).path
        if path == "/api/v1/objectives":
            try:
                size = int(self.headers.get("Content-Length") or "0")
            except ValueError:
                self._respond_json(400, {"error": "invalid payload size"})
                return
            if size <= 0 or size > MAX_DOMAIN_PAYLOAD_BYTES:
                self._respond_json(400, {"error": "invalid payload size"})
                return
            content_type = (
                str(self.headers.get("Content-Type") or "")
                .split(";", 1)[0]
                .strip()
                .lower()
            )
            if content_type != "application/json":
                self._respond_json(
                    415,
                    {"error": "objective endpoint requires application/json"},
                )
                return
            try:
                payload = json.loads(self.rfile.read(size).decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError):
                self._respond_json(400, {"error": "invalid JSON payload"})
                return
            if not isinstance(payload, Mapping):
                self._respond_json(
                    400,
                    {"error": "objective payload must be an object"},
                )
                return
            status, response = self.runtime.handle_objective_submit(
                self.headers.get("Authorization"),
                payload,
                client=self.headers.get("X-Skyforge-Client"),
            )
            self._respond_json(status, response)
            return

        if path == "/api/v1/human-reviews":
            try:
                size = int(self.headers.get("Content-Length") or "0")
            except ValueError:
                self._respond_json(400, {"error": "invalid payload size"})
                return
            if size <= 0 or size > MAX_DOMAIN_PAYLOAD_BYTES:
                self._respond_json(400, {"error": "invalid payload size"})
                return
            content_type = str(self.headers.get("Content-Type") or "").split(";", 1)[0].strip().lower()
            if content_type != "application/json":
                self._respond_json(415, {"error": "human review endpoint requires application/json"})
                return
            try:
                payload = json.loads(self.rfile.read(size).decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError):
                self._respond_json(400, {"error": "invalid JSON payload"})
                return
            if not isinstance(payload, Mapping):
                self._respond_json(400, {"error": "human review payload must be an object"})
                return
            status, response = self.runtime.handle_human_review_submit(
                self.headers.get("Authorization"),
                payload,
                client=self.headers.get("X-Skyforge-Client"),
            )
            self._respond_json(status, response)
            return

        if path != "/webhook":
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
        "--execution-periodic-seconds",
        type=int,
        default=int(os.environ.get("SKYFORGE_V2_PERIODIC_WAKE_SECONDS", "900")),
        help="Model-free periodic driver wake used to recover missed webhook progress.",
    )
    parser.add_argument(
        "--execution-max-boundaries-per-wake",
        type=int,
        default=int(os.environ.get("SKYFORGE_V2_MAX_BOUNDARIES_PER_WAKE", "32")),
        help="Hard bound on durable lifecycle advances drained by one driver wake.",
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
    if args.execution_periodic_seconds < 1:
        parser.error("--execution-periodic-seconds must be positive")
    if args.execution_max_boundaries_per_wake < 1:
        parser.error("--execution-max-boundaries-per-wake must be positive")
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
    execution_driver = None
    if runtime.production_execution_enabled:
        execution_driver = HostedExecutionDriver(
            runtime=runtime,
            dependencies=ProductionHostedDependencyFactory(root=args.root),
            periodic_seconds=args.execution_periodic_seconds,
            max_boundaries_per_wake=args.execution_max_boundaries_per_wake,
        )
        runtime.attach_execution_driver(execution_driver)

    Handler.runtime = runtime
    server = ThreadingHTTPServer((args.bind, args.port), Handler)
    if execution_driver is not None:
        execution_driver.start()
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
        if execution_driver is not None:
            execution_driver.stop()
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())