#!/usr/bin/env python3
"""Event-driven Skyforge Codex pilot.

This process is intentionally local-only and reversible:
- GitHub webhook events are forwarded to localhost by gh webhook forward.
- The controller filters/debounces events before any Codex turn is created.
- Actionable events are journaled before the webhook returns.
- A low-cost Codex classifier decides whether bounded work is actionable.
- A bounded Codex worker edits/tests only in a dedicated clone.
- The controller, not the model, performs git/gh network writes.

The hourly ChatGPT Audit watchdog remains independent and detects silence/liveness failures.
"""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import os
import re
import subprocess
import threading
import time
from dataclasses import asdict, dataclass, replace
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Iterable

REPO = "ni-da-ba/skyforge"
STATE_DIR = ".skyforge-orchestrator"
STATE_FILE = "state.json"
DEFAULT_PORT = 3000
DEFAULT_DEBOUNCE_SECONDS = 25
DEFAULT_MIN_DISPATCH_SECONDS = 120
DEFAULT_MAX_PARENT_TURNS = 24
DEFAULT_MAX_CLASSIFIER_CALLS_PER_DAY = 24
DEFAULT_MAX_WORKER_CALLS_PER_DAY = 4
DEFAULT_QUOTA_BACKOFF_SECONDS = 3600
DEFAULT_RATE_LIMIT_BACKOFF_SECONDS = 300
DEFAULT_TRANSIENT_BACKOFF_SECONDS = 300
DEFAULT_MAX_CONSECUTIVE_CLASSIFIER_FAILURES = 3
DEFAULT_MAX_SEEN_DELIVERIES = 512
DEFAULT_MAX_SEEN_ISSUE_COMMENTS = 512
DEFAULT_MAX_PENDING_EVENTS = 100
DEFAULT_STARTUP_RECONCILE_RETRY_SECONDS = 60
DEFAULT_PERIODIC_RECONCILE_SECONDS = 900
DEFAULT_TRUSTED_GITHUB_ACTORS = ("ni-da-ba",)
CONTROLLER_RUNTIME_PATHS = {
    "scripts/orchestrator/skyforge_orchestrator.py",
    "scripts/orchestrator/requirements.txt",
    "scripts/orchestrator/sync_runtime_dependencies.py",
}
PROTECTED_WORKER_PATH_PREFIXES = (
    "scripts/orchestrator/",
    "deploy/orchestrator/",
    ".github/",
)
PROTECTED_WORKER_PATHS = {
    "AGENTS.md",
    "docs/agent-state/PROGRAM_CHARTER.md",
    "docs/agent-state/VALIDATION_POLICY.md",
    "docs/agent-state/ORCHESTRATION_PROTOCOL.md",
    "docs/agent-state/HUMAN_STRATEGY_ROADMAP.md",
    "docs/agent-state/CROSS_LANE_CONTRACTS.md",
    "docs/agent-state/AUDIT_STATE.md",
}

ACTIVE_RUN_STATUSES = {"queued", "in_progress", "requested", "waiting", "pending"}
AUDIT_WAKE_TOKENS = (
    "audit",
    "restart recommended",
    "human_gate",
    "human gate",
    "loop risk",
    "/skyforge-orchestrate",
)
SELF_COMMENT_MARKER = "[skyforge-orchestrator]"

CLASSIFIER_INSTRUCTIONS = """You are the lightweight Skyforge orchestration classifier.

Repository state, current main, tests, merged history, AGENTS.md, AUDIT_STATE, VALIDATION_POLICY,
HUMAN_STRATEGY_ROADMAP, and current PR/Actions evidence are authoritative.

You do NOT edit files. You do NOT run network operations. Your only job is to decide whether the
event snapshot warrants one bounded Codex worker, a human gate, a merge of a controller-managed PR,
or no action.

Never race a healthy ordinary ChatGPT/manual producer. Recent information-bearing producer commits,
PR-head movement, or progressing Actions means RUNNING_EXTERNAL unless the evidence explicitly says
that producer is stale/dead/restart-recommended.

Trusted Audit directives are first-class liveness evidence. A structured
signal_kind="restart_recommended" means Audit has already adjudicated the prior producer stale/dead at
the signal time. Do NOT use PR/issue updatedAt, draft/open state, the Audit comment itself, bookkeeping
motion, or unchanged reruns as evidence that the producer recovered. For such a signal, choose NOOP
only when the snapshot shows substantive producer evidence strictly after the signal (for example a
new producer head/commit, genuinely new Actions work attributable to that producer, or an already
controller-managed recovery). Otherwise honor the bounded restart objective and choose DISPATCH.
A structured human_gate signal remains a HUMAN_GATE rather than a worker dispatch.

Do not dispatch work merely because a lane exists. Do not poll CI. Do not expand expensive validation
without a distinct risk. Honor VALIDATION_POLICY.md and ORCHESTRATION_PROTOCOL.md.

For DISPATCH, choose the cheapest worker tier that can safely retire the stated uncertainty:
- LUNA: documentation/lane-state/evidence reconciliation, acceptance recording, narrow low-risk text/config
  work, or similarly bounded tasks that do not require substantive source/runtime debugging.
- TERRA: source implementation, runtime/debugging, substantial tests/build integration, or complex
  engineering where lower-capability execution is likely to waste a retry.
Prefer LUNA when a trusted restart says existing source/runtime evidence is already green and the
remaining objective is to record/reconcile that accepted boundary. Do not ask a recovery worker to
recreate files already present on the source PR merely to move the same evidence onto a new branch.

For a LUNA worker, return the smallest practical non-empty allowed_paths list. Entries are exact
repository paths or directory prefixes ending in /**. For TERRA, allowed_paths may be null unless the
objective is naturally narrow enough to constrain safely.

Return ONLY one JSON object:
{
  "decision": "NOOP" | "DISPATCH" | "HUMAN_GATE" | "MERGE",
  "lane": "Implementation" | "Authorship" | "Content" | "Music" | "Presentation" | "Audit" | null,
  "pr_number": integer | null,
  "objective": string | null,
  "stop_boundary": string | null,
  "reusable_evidence": string | null,
  "worker_tier": "LUNA" | "TERRA" | null,
  "allowed_paths": [string, ...] | null,
  "reason": string,
  "human_message": string | null
}

MERGE may be selected only for a controller-managed PR whose required machine evidence is already
green and whose repository policy has no remaining human/product gate.
"""

CLASSIFIER_POLICY_FINGERPRINT = hashlib.sha256(
    CLASSIFIER_INSTRUCTIONS.encode("utf-8")
).hexdigest()

WORKER_INSTRUCTIONS = """You are a bounded Skyforge repository worker.

Read AGENTS.md, PROGRAM_CHARTER.md, VALIDATION_POLICY.md, the relevant lane state,
CROSS_LANE_CONTRACTS.md, and only the source/tests/history needed for the objective.

You have local filesystem access in a dedicated clone but NO GitHub/network authority. Do not try to
push, open PRs, merge, modify secrets, or request credentials. The outer controller owns all git/gh
network writes.

Work only on the supplied objective. Do not expand into unrelated cleanup. Do not cross a human or
product-strategy gate. Do not repeat expensive evidence unless the prompt identifies the distinct
uncertainty it retires. Reuse portable evidence under VALIDATION_POLICY.md. If the prompt lists files
already changed by a source PR, treat those files as existing durable work: do not recreate/copy them
onto the controller branch unless the objective explicitly requires changing that existing source work.
If the prompt supplies an allowed-path scope, edit nothing outside it.

Make local source/test/doc changes and run appropriate local verification. Preserve and inspect any
partial changes already present from an interrupted prior attempt before editing further. Leave the
worktree clean of generated junk. Do not create or amend git commits; the controller handles
commit/push after reviewing the worktree state.

At completion, give a concise final response with:
- what changed;
- local verification performed;
- any gate/blocker;
- whether the bounded objective is ready for controller handoff.
"""


@dataclass(frozen=True)
class EventDecision:
    actionable: bool
    reason: str
    event: str
    action: str | None = None
    head_sha: str | None = None
    pr_number: int | None = None
    observed_at: str | None = None
    source_id: str | None = None
    signal_kind: str | None = None
    signal_text: str | None = None

    def summary(self) -> str:
        bits = [self.event]
        if self.action:
            bits.append(self.action)
        if self.pr_number:
            bits.append(f"PR#{self.pr_number}")
        if self.head_sha:
            bits.append(self.head_sha[:10])
        if self.signal_kind:
            bits.append(f"signal={self.signal_kind}")
        bits.append(self.reason)
        return " | ".join(bits)

    def to_state(self) -> dict[str, Any]:
        return asdict(self)

    @staticmethod
    def from_state(value: dict[str, Any]) -> "EventDecision":
        return EventDecision(
            actionable=bool(value.get("actionable")),
            reason=str(value.get("reason") or ""),
            event=str(value.get("event") or ""),
            action=value.get("action"),
            head_sha=value.get("head_sha"),
            pr_number=value.get("pr_number"),
            observed_at=value.get("observed_at"),
            source_id=value.get("source_id"),
            signal_kind=value.get("signal_kind"),
            signal_text=value.get("signal_text"),
        )


def _event_key(value: EventDecision | dict[str, Any]) -> str:
    payload = dict(value.to_state() if isinstance(value, EventDecision) else value)
    # Observation time is telemetry, not event identity. Redelivery/replay must deduplicate the same
    # repository transition even when it is observed at a different wall-clock instant.
    payload.pop("observed_at", None)
    return json.dumps(payload, sort_keys=True, separators=(",", ":"))


def _trusted_actor(payload: dict[str, Any], trusted_actors: Iterable[str]) -> bool:
    login = str((((payload.get("comment") or {}).get("user") or {}).get("login")) or "").strip().lower()
    allowed = {str(actor).strip().lower() for actor in trusted_actors if str(actor).strip()}
    return bool(login and login in allowed)


def _internal_pr_payload(payload: dict[str, Any], repo: str) -> bool:
    pr = payload.get("pull_request") or {}
    head_repo = ((pr.get("head") or {}).get("repo") or {}).get("full_name")
    return not head_repo or str(head_repo).lower() == repo.lower()


def _internal_workflow_payload(payload: dict[str, Any], repo: str) -> bool:
    run = payload.get("workflow_run") or {}
    head_repo = (run.get("head_repository") or {}).get("full_name")
    return not head_repo or str(head_repo).lower() == repo.lower()


def _audit_signal_kind(body_lower: str) -> str | None:
    if "restart recommended" in body_lower:
        return "restart_recommended"
    if "loop risk" in body_lower:
        return "loop_risk"
    if "human_gate" in body_lower or "human gate" in body_lower:
        return "human_gate"
    if "audit" in body_lower:
        return "audit"
    return None


def classify_control_command(
    event: str,
    payload: dict[str, Any],
    *,
    trusted_actors: Iterable[str] = DEFAULT_TRUSTED_GITHUB_ACTORS,
) -> str | None:
    if (event or "").strip().lower() != "issue_comment":
        return None
    if str(payload.get("action") or "").lower() != "created":
        return None
    body = str((payload.get("comment") or {}).get("body") or "").strip().lower()
    if body not in {
        "/skyforge-pause",
        "/skyforge-resume",
        "/skyforge-status",
        "/skyforge-reset-budget",
        "/skyforge-refresh-runtime",
        "/skyforge-discard-worker",
    }:
        return None
    if not _trusted_actor(payload, trusted_actors):
        return None
    return {
        "/skyforge-pause": "pause",
        "/skyforge-resume": "resume",
        "/skyforge-status": "status",
        "/skyforge-reset-budget": "reset_budget",
        "/skyforge-refresh-runtime": "refresh_runtime",
        "/skyforge-discard-worker": "discard_worker",
    }[body]


def classify_event(
    event: str,
    payload: dict[str, Any],
    *,
    repo: str = REPO,
    trusted_actors: Iterable[str] = DEFAULT_TRUSTED_GITHUB_ACTORS,
) -> EventDecision:
    """Cheap deterministic gate. Irrelevant or unauthorized events never reach Codex."""
    event = (event or "").strip().lower()

    if event == "ping":
        return EventDecision(False, "webhook ping", event)

    if event == "push":
        ref = str(payload.get("ref") or "")
        if ref == "refs/heads/main":
            return EventDecision(True, "main advanced", event, head_sha=payload.get("after"))
        return EventDecision(False, "non-main push; PR/workflow events cover producer branches", event)

    if event == "pull_request":
        if not _internal_pr_payload(payload, repo):
            return EventDecision(False, "external/fork PR event cannot wake orchestration", event)
        action = str(payload.get("action") or "").lower()
        pr = payload.get("pull_request") or {}
        number = payload.get("number")
        head_sha = ((pr.get("head") or {}).get("sha"))
        if action == "synchronize":
            return EventDecision(
                False,
                "branch push; wait for workflow completion rather than racing the producer",
                event,
                action,
                head_sha,
                number,
            )
        if action in {"closed", "reopened", "ready_for_review", "converted_to_draft"}:
            return EventDecision(True, "PR lifecycle changed", event, action, head_sha, number)
        if action == "opened":
            return EventDecision(
                False,
                "new PR normally waits for first CI completion before orchestration",
                event,
                action,
                head_sha,
                number,
            )
        return EventDecision(False, "non-actionable PR event", event, action, head_sha, number)

    if event == "workflow_run":
        if not _internal_workflow_payload(payload, repo):
            return EventDecision(False, "external/fork workflow cannot wake orchestration", event)
        action = str(payload.get("action") or "").lower()
        run = payload.get("workflow_run") or {}
        if action != "completed":
            return EventDecision(False, "workflow has not completed", event, action)
        return EventDecision(
            True,
            "workflow completed; controller will require head quiescence",
            event,
            action,
            run.get("head_sha"),
            ((run.get("pull_requests") or [{}])[0] or {}).get("number"),
        )

    if event == "issue_comment":
        action = str(payload.get("action") or "").lower()
        if action != "created":
            return EventDecision(False, "only newly-created comments can wake orchestration", event, action)
        comment = payload.get("comment") or {}
        body = str(comment.get("body") or "")
        body_lower = body.lower()
        if SELF_COMMENT_MARKER in body_lower:
            return EventDecision(False, "controller-authored comment; prevent wake loop", event, action)
        issue = payload.get("issue") or {}
        signal_kind = _audit_signal_kind(body_lower)
        wake_requested = "/skyforge-orchestrate" in body_lower or signal_kind is not None
        if wake_requested and not _trusted_actor(payload, trusted_actors):
            return EventDecision(False, "untrusted commenter cannot wake orchestration", event, action)
        source_id = str(comment.get("id")) if comment.get("id") is not None else None
        observed_at = comment.get("created_at")
        if "/skyforge-orchestrate" in body_lower:
            return EventDecision(
                True,
                "manual orchestration command",
                event,
                "manual_command",
                pr_number=issue.get("number"),
                observed_at=observed_at,
                source_id=source_id,
            )
        if signal_kind is not None:
            return EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                event,
                "audit_signal",
                pr_number=issue.get("number"),
                observed_at=observed_at,
                source_id=source_id,
                signal_kind=signal_kind,
                signal_text=body[:6000],
            )
        return EventDecision(False, "ordinary comment", event, action)

    return EventDecision(False, "event type not in pilot allowlist", event)


def _run(
    args: list[str],
    *,
    cwd: Path,
    check: bool = True,
    timeout: int = 120,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=check,
        timeout=timeout,
    )


def _json_cmd(args: list[str], *, cwd: Path, timeout: int = 120) -> Any:
    result = _run(args, cwd=cwd, timeout=timeout)
    return json.loads(result.stdout or "null")


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _utc_day() -> str:
    return datetime.now(timezone.utc).date().isoformat()


def _seconds_until_next_utc_day() -> int:
    now = datetime.now(timezone.utc)
    tomorrow = datetime.combine(now.date() + timedelta(days=1), datetime.min.time(), tzinfo=timezone.utc)
    return max(60, int((tomorrow - now).total_seconds()))


def _env_int(name: str, default: int, *, minimum: int = 0) -> int:
    raw = os.environ.get(name)
    if raw is None:
        return default
    try:
        return max(minimum, int(raw))
    except ValueError:
        return default


def verify_webhook_signature(secret: str | None, payload: bytes, signature: str | None) -> bool:
    if not secret or not signature or not signature.startswith("sha256="):
        return False
    expected = "sha256=" + hmac.new(secret.encode("utf-8"), payload, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature)


def _clean_json_object(text: str) -> dict[str, Any]:
    raw = text.strip()
    fenced = re.match(r"^\`\`\`(?:json)?\s*(.*?)\s*\`\`\`$", raw, re.S | re.I)
    if fenced:
        raw = fenced.group(1).strip()
    start = raw.find("{")
    end = raw.rfind("}")
    if start < 0 or end < start:
        raise ValueError(f"Classifier did not return JSON: {text[:500]!r}")
    value = json.loads(raw[start : end + 1])
    if not isinstance(value, dict):
        raise ValueError("Classifier JSON must be an object")
    return value


def _classifier_prompt(events: list[EventDecision], snapshot: dict[str, Any]) -> str:
    structured_events = [event.to_state() for event in events]
    return (
        "A filtered Skyforge repository event batch is actionable.\n\n"
        "STRUCTURED EVENTS:\n" + json.dumps(structured_events, indent=2) + "\n\n"
        "COMPACT REPOSITORY SNAPSHOT:\n" + json.dumps(snapshot, indent=2)[:24000] + "\n\n"
        "Treat structured trusted Audit directives as first-class evidence. PR/issue updatedAt is not "
        "producer-liveness evidence because comments and bookkeeping mutate it. "
        "Read AGENTS.md and the compact Audit state as needed. Return only the required JSON decision."
    )


def _classifier_input_fingerprint(
    events: list[EventDecision],
    snapshot: dict[str, Any],
) -> str:
    """Fingerprint only semantic classifier inputs; exclude wall-clock/metrics noise."""
    open_prs = []
    for pr in snapshot.get("open_prs") or []:
        if not isinstance(pr, dict):
            continue
        author = pr.get("author") if isinstance(pr.get("author"), dict) else {}
        open_prs.append(
            {
                "number": pr.get("number"),
                "title": pr.get("title"),
                "isDraft": pr.get("isDraft"),
                "headRefName": pr.get("headRefName"),
                "headRefOid": pr.get("headRefOid"),
                "baseRefName": pr.get("baseRefName"),
                "mergeStateStatus": pr.get("mergeStateStatus"),
                "author": author.get("login") if isinstance(author, dict) else None,
            }
        )
    open_prs.sort(key=lambda item: int(item.get("number") or 0))

    recent_runs = []
    for run in snapshot.get("recent_runs") or []:
        if not isinstance(run, dict):
            continue
        recent_runs.append(
            {
                "databaseId": run.get("databaseId"),
                "name": run.get("name"),
                "status": run.get("status"),
                "conclusion": run.get("conclusion"),
                "headSha": run.get("headSha"),
                "headBranch": run.get("headBranch"),
                "event": run.get("event"),
            }
        )
    recent_runs.sort(key=lambda item: int(item.get("databaseId") or 0), reverse=True)

    payload = {
        "policy": CLASSIFIER_POLICY_FINGERPRINT,
        "events": sorted(_event_key(event) for event in events),
        "snapshot": {
            "main": snapshot.get("main"),
            "open_prs": open_prs,
            "recent_runs": recent_runs,
            "controller_managed": snapshot.get("controller_managed") or {},
        },
    }
    encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _restart_signal_events(events: Iterable[EventDecision]) -> list[EventDecision]:
    return [
        event
        for event in events
        if event.action == "audit_signal" and event.signal_kind == "restart_recommended"
    ]


def _restart_signal_expected_head(event: EventDecision) -> str | None:
    text = event.signal_text or ""
    match = re.search(r"\bhead\s+[`'\"]?([0-9a-f]{40})\b", text, re.I)
    if match:
        return match.group(1).lower()
    candidates = re.findall(r"\b[0-9a-f]{40}\b", text, re.I)
    if len(candidates) == 1:
        return candidates[0].lower()
    return None


def _restart_noop_has_post_signal_evidence(
    events: Iterable[EventDecision], snapshot: dict[str, Any]
) -> bool:
    restarts = _restart_signal_events(events)
    if not restarts:
        return True
    open_prs = snapshot.get("open_prs") or []
    by_number = {
        int(pr.get("number")): pr
        for pr in open_prs
        if isinstance(pr, dict) and pr.get("number") is not None
    }
    for event in restarts:
        if event.pr_number is None:
            return False
        current = by_number.get(int(event.pr_number))
        # A target that is no longer open has materially changed after the watchdog handoff.
        if current is None:
            continue
        expected_head = _restart_signal_expected_head(event)
        current_head = str(current.get("headRefOid") or "").lower()
        # Without an immutable signal-time head we cannot prove recovery from PR metadata alone.
        if not expected_head or not current_head:
            return False
        if current_head == expected_head:
            return False
    return True


class SafetyPause(RuntimeError):
    """Fail-closed controller safety event that must not also create a retry circuit-breaker."""


class RetryBlocked(RuntimeError):
    def __init__(self, kind: str, retry_after_seconds: int, message: str) -> None:
        super().__init__(message)
        self.kind = kind
        self.retry_after_seconds = max(1, int(retry_after_seconds))


def _codex_failure_policy(exc: Exception) -> tuple[str, int]:
    """Map opaque SDK/App-Server failures into conservative retry classes.

    The SDK surface may change exception classes, so the pilot intentionally avoids a hard dependency
    on private exception types and uses the stable human-readable failure text as the compatibility
    boundary. Unknown model-call failures are treated as transient and never discard durable work.
    """
    text = f"{type(exc).__name__}: {exc}".lower()
    quota_tokens = (
        "insufficient_quota",
        "usage limit",
        "quota",
        "credits exhausted",
        "credit balance",
        "weekly limit",
        "daily limit",
        "limit reached",
    )
    rate_tokens = (
        "rate limit",
        "too many requests",
        "429",
        "capacity",
        "overloaded",
    )
    auth_tokens = (
        "authentication",
        "unauthorized",
        "invalid api key",
        "401",
        "403",
    )
    if any(token in text for token in quota_tokens):
        return "quota", _env_int(
            "SKYFORGE_ORCHESTRATOR_QUOTA_BACKOFF_SECONDS", DEFAULT_QUOTA_BACKOFF_SECONDS, minimum=60
        )
    if any(token in text for token in rate_tokens):
        return "rate_limit", _env_int(
            "SKYFORGE_ORCHESTRATOR_RATE_BACKOFF_SECONDS", DEFAULT_RATE_LIMIT_BACKOFF_SECONDS, minimum=30
        )
    if any(token in text for token in auth_tokens):
        return "authentication", _env_int(
            "SKYFORGE_ORCHESTRATOR_AUTH_BACKOFF_SECONDS", DEFAULT_QUOTA_BACKOFF_SECONDS, minimum=60
        )
    return "transient", _env_int(
        "SKYFORGE_ORCHESTRATOR_TRANSIENT_BACKOFF_SECONDS", DEFAULT_TRANSIENT_BACKOFF_SECONDS, minimum=30
    )


class LocalState:
    def __init__(self, root: Path) -> None:
        self.dir = root / STATE_DIR
        self.path = self.dir / STATE_FILE
        self.backup_path = self.dir / f"{STATE_FILE}.bak"
        self._lock = threading.RLock()
        self.dir.mkdir(parents=True, exist_ok=True)
        self.data: dict[str, Any] = {
            "parent_thread_id": None,
            "parent_turns": 0,
            "classifier_policy_fingerprint": None,
            "classifier_decision_cache": {},
            "last_dispatch_epoch": 0.0,
            "managed": {},
            "human_gate_records": {},
            "last_events": [],
            "pending_events": [],
            "pending_decision": None,
            "pending_worker": None,
            "blocked_until_epoch": 0.0,
            "blocked_kind": None,
            "blocked_reason": None,
            "classifier_failure_streak": 0,
            "last_classifier_error_kind": None,
            "last_classifier_error_at": None,
            "last_classifier_error_summary": None,
            "last_classifier_success_at": None,
            "metrics": {},
            "budget_day": _utc_day(),
            "classifier_calls_today": 0,
            "luna_worker_calls_today": 0,
            "worker_calls_today": 0,
            "last_budget_reset_at": None,
            "last_budget_reset_by": None,
            "seen_deliveries": [],
            "seen_issue_comment_ids": [],
            "issue_comment_reconcile_initialized": False,
            "last_issue_comment_reconcile_at": None,
            "reconcile_fingerprint": None,
            "reconcile_snapshot": None,
            "last_reconcile_at": None,
            "paused": False,
            "paused_at": None,
            "paused_by": None,
            "last_state_recovery": None,
            "last_startup_reconcile_error": None,
            "last_startup_reconcile_success_at": None,
            "startup_reconcile_retry_at": None,
            "next_periodic_reconcile_at": None,
            "last_periodic_reconcile_success_at": None,
            "last_periodic_reconcile_error": None,
            "controller_started_at": None,
        }

        primary_error: Exception | None = None
        loaded: dict[str, Any] | None = None
        if self.path.exists():
            try:
                loaded = self._read_mapping(self.path)
            except Exception as exc:
                primary_error = exc
        elif self.backup_path.exists():
            primary_error = FileNotFoundError(str(self.path))

        recovered_from_backup = False
        if loaded is None and self.backup_path.exists():
            try:
                loaded = self._read_mapping(self.backup_path)
                recovered_from_backup = True
            except Exception as backup_error:
                raise RuntimeError(
                    "Skyforge orchestrator state is unreadable in both primary and backup files"
                ) from backup_error

        if loaded is None and primary_error is not None:
            raise RuntimeError(
                "Skyforge orchestrator state file is unreadable and no valid backup is available"
            ) from primary_error

        if loaded is not None:
            self.data.update(loaded)

        if recovered_from_backup:
            metrics = self.data.get("metrics")
            if not isinstance(metrics, dict):
                metrics = {}
                self.data["metrics"] = metrics
            metrics["state_backup_recoveries"] = int(
                metrics.get("state_backup_recoveries") or 0
            ) + 1
            self.data["last_state_recovery"] = {
                "at": _utc_now(),
                "source": self.backup_path.name,
                "primary_error": type(primary_error).__name__ if primary_error else "missing",
            }
            self.save()

    @staticmethod
    def _read_mapping(path: Path) -> dict[str, Any]:
        value = json.loads(path.read_text())
        if not isinstance(value, dict):
            raise ValueError(f"State file {path} must contain a JSON object")
        return value

    @staticmethod
    def _atomic_write(path: Path, payload: str) -> None:
        tmp = path.with_name(path.name + ".tmp")
        with tmp.open("w") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp, path)

    def save(self) -> None:
        with self._lock:
            payload = json.dumps(self.data, indent=2, sort_keys=True) + "\n"
            self._atomic_write(self.path, payload)
            self._atomic_write(self.backup_path, payload)


def _ensure_classifier_policy_state(state: LocalState) -> None:
    stored = state.data.get("classifier_policy_fingerprint")
    if stored == CLASSIFIER_POLICY_FINGERPRINT:
        return
    had_parent = bool(state.data.get("parent_thread_id"))
    state.data["parent_thread_id"] = None
    state.data["parent_turns"] = 0
    state.data["classifier_policy_fingerprint"] = CLASSIFIER_POLICY_FINGERPRINT
    state.data["classifier_decision_cache"] = {}
    if had_parent:
        metrics = state.data.setdefault("metrics", {})
        metrics["classifier_policy_rotations"] = int(
            metrics.get("classifier_policy_rotations") or 0
        ) + 1
    state.save()


class Orchestrator:
    def __init__(
        self,
        root: Path,
        *,
        repo: str,
        debounce_seconds: int,
        min_dispatch_seconds: int,
        max_parent_turns: int,
        auto_merge: bool,
        webhook_secret: str | None = None,
        require_webhook_secret: bool = False,
        startup_reconcile: bool = False,
    ) -> None:
        self.root = root
        self.repo = repo
        self.debounce_seconds = debounce_seconds
        self.min_dispatch_seconds = min_dispatch_seconds
        self.max_parent_turns = max_parent_turns
        self.auto_merge = auto_merge
        self.webhook_secret = webhook_secret or os.environ.get("SKYFORGE_WEBHOOK_SECRET")
        self.require_webhook_secret = require_webhook_secret
        self.startup_reconcile = startup_reconcile
        actors_raw = os.environ.get("SKYFORGE_TRUSTED_GITHUB_ACTORS", ",".join(DEFAULT_TRUSTED_GITHUB_ACTORS))
        self.trusted_actors = tuple(
            actor.strip().lower() for actor in actors_raw.split(",") if actor.strip()
        )
        if not self.trusted_actors:
            raise RuntimeError("At least one trusted GitHub actor is required")
        self.state = LocalState(root)
        _ensure_classifier_policy_state(self.state)
        self._state_lock = threading.RLock()
        self._timer_lock = threading.Lock()
        self._timer: threading.Timer | None = None
        self._dispatch_lock = threading.Lock()
        self._startup_reconcile_retry_lock = threading.Lock()
        self._startup_reconcile_retry_timer: threading.Timer | None = None
        self._periodic_reconcile_lock = threading.Lock()
        self._periodic_reconcile_timer: threading.Timer | None = None
        self._reconcile_observation_lock = threading.Lock()
        self.runtime_head: str | None = None

    def validate_environment(self) -> None:
        if os.environ.get("SKYFORGE_ORCHESTRATOR_DEDICATED_CLONE") != "1":
            raise RuntimeError(
                "Refusing to run without SKYFORGE_ORCHESTRATOR_DEDICATED_CLONE=1. "
                "Use a dedicated clone, never your normal interactive worktree."
            )
        if not (self.root / ".git").exists():
            raise RuntimeError(f"{self.root} is not a Git clone")
        _run(["git", "rev-parse", "--is-inside-work-tree"], cwd=self.root)
        for key in ("user.name", "user.email"):
            value = _run(["git", "config", "--get", key], cwd=self.root, check=False).stdout.strip()
            if not value:
                raise RuntimeError(f"Git {key} is required in the dedicated clone before autonomous commits")
        _run(["gh", "auth", "status"], cwd=self.root, timeout=30)
        if self.require_webhook_secret:
            if not self.webhook_secret:
                raise RuntimeError(
                    "Hosted webhook mode requires SKYFORGE_WEBHOOK_SECRET."
                )
            if len(self.webhook_secret) < 32:
                raise RuntimeError(
                    "SKYFORGE_WEBHOOK_SECRET must be at least 32 characters in hosted mode."
                )
        self.runtime_head = _run(
            ["git", "rev-parse", "HEAD"],
            cwd=self.root,
        ).stdout.strip()
        with self._state_lock:
            self.state.data["controller_started_at"] = _utc_now()
            requested = self.state.data.pop("runtime_restart_requested", None)
            if requested:
                self.state.data["last_runtime_restart"] = {
                    **requested,
                    "completed_at": _utc_now(),
                    "runtime_head": self.runtime_head,
                }
                self.state.save()
        if requested:
            self._metric("runtime_restart_completions")

    def delivery_seen(self, delivery_id: str | None) -> bool:
        if not delivery_id:
            return False
        with self._state_lock:
            return delivery_id in (self.state.data.get("seen_deliveries") or [])

    def record_delivery(self, delivery_id: str | None) -> None:
        if not delivery_id:
            return
        with self._state_lock:
            values = [
                str(value)
                for value in (self.state.data.get("seen_deliveries") or [])
                if value
            ]
            if delivery_id not in values:
                values.append(delivery_id)
            self.state.data["seen_deliveries"] = values[-DEFAULT_MAX_SEEN_DELIVERIES:]
            self.state.save()

    def issue_comment_seen(self, source_id: str | None) -> bool:
        if not source_id:
            return False
        with self._state_lock:
            return source_id in {
                str(value)
                for value in (self.state.data.get("seen_issue_comment_ids") or [])
                if value is not None
            }

    def record_issue_comment(self, source_id: str | None) -> None:
        if not source_id:
            return
        with self._state_lock:
            values = [
                str(value)
                for value in (self.state.data.get("seen_issue_comment_ids") or [])
                if value is not None
            ]
            if source_id not in values:
                values.append(source_id)
            self.state.data["seen_issue_comment_ids"] = values[
                -DEFAULT_MAX_SEEN_ISSUE_COMMENTS:
            ]
            self.state.save()

    def _apply_control_payload(self, control: str, payload: dict[str, Any]) -> None:
        actor = str(
            ((((payload.get("comment") or {}).get("user") or {}).get("login")) or "")
        )
        if control == "status":
            issue = payload.get("issue") or {}
            target = issue.get("number") or 349
            self.post_status(target)
        elif control == "reset_budget":
            self.reset_local_budget(actor=actor)
        elif control == "refresh_runtime":
            self.refresh_runtime(actor=actor)
        elif control == "discard_worker":
            self.discard_pending_worker(actor=actor)
        else:
            self.set_paused(control == "pause", actor=actor)

    @staticmethod
    def _issue_number_from_comment(value: dict[str, Any]) -> int | None:
        issue_url = str(value.get("issue_url") or "")
        match = re.search(r"/issues/(\d+)$", issue_url)
        return int(match.group(1)) if match else None

    def reconcile_issue_comments(self, *, source: str) -> None:
        """Recover trusted issue-comment wakes/controls when webhook transport is silent."""
        scan_started_at = _utc_now()
        with self._state_lock:
            initialized = bool(self.state.data.get("issue_comment_reconcile_initialized"))
            since = self.state.data.get("last_issue_comment_reconcile_at")
            controller_started_at = self.state.data.get("controller_started_at")

        args = [
            "gh",
            "api",
            "--method",
            "GET",
            "--paginate",
            "--slurp",
            f"repos/{self.repo}/issues/comments",
            "-f",
            "sort=created",
            "-f",
            "direction=asc",
            "-f",
            "per_page=100",
        ]
        if initialized and since:
            args.extend(["-f", f"since={since}"])

        raw_comments = _json_cmd(args, cwd=self.root, timeout=120)
        if not isinstance(raw_comments, list):
            raise RuntimeError("GitHub issue-comment reconciliation returned a non-list payload")

        # gh api --paginate --slurp returns one list per page. Accept a flat list too so tests and
        # older gh behavior remain straightforward.
        comments: list[dict[str, Any]] = []
        for value in raw_comments:
            if isinstance(value, list):
                comments.extend(item for item in value if isinstance(item, dict))
            elif isinstance(value, dict):
                comments.append(value)

        ordered = sorted(comments, key=lambda value: int(value.get("id") or 0))

        if not initialized:
            cutoff = None
            if controller_started_at:
                try:
                    cutoff = datetime.fromisoformat(
                        str(controller_started_at).replace("Z", "+00:00")
                    )
                except ValueError:
                    cutoff = None
            for value in ordered:
                created = None
                try:
                    created = datetime.fromisoformat(
                        str(value.get("created_at") or "").replace("Z", "+00:00")
                    )
                except ValueError:
                    pass
                # Migration safety: seed only comments that predate this controller process. A
                # command created while the upgraded service is starting remains eligible below.
                if cutoff is None or created is None or created < cutoff:
                    source_id = (
                        str(value.get("id")) if value.get("id") is not None else None
                    )
                    self.record_issue_comment(source_id)
            with self._state_lock:
                self.state.data["issue_comment_reconcile_initialized"] = True
                self.state.save()
            self._metric("issue_comment_reconcile_baselines")

        recovered_wakes = 0
        recovered_controls = 0
        for value in ordered:
            source_id = str(value.get("id")) if value.get("id") is not None else None
            if not source_id or self.issue_comment_seen(source_id):
                continue
            issue_number = self._issue_number_from_comment(value)
            payload = {
                "action": "created",
                "comment": {
                    "id": value.get("id"),
                    "body": value.get("body"),
                    "created_at": value.get("created_at"),
                    "user": {"login": ((value.get("user") or {}).get("login"))},
                },
                "issue": {"number": issue_number},
            }

            control = classify_control_command(
                "issue_comment",
                payload,
                trusted_actors=self.trusted_actors,
            )
            if control:
                self._apply_control_payload(control, payload)
                recovered_controls += 1
            else:
                decision = classify_event(
                    "issue_comment",
                    payload,
                    repo=self.repo,
                    trusted_actors=self.trusted_actors,
                )
                self.enqueue(decision)
                if decision.actionable:
                    recovered_wakes += 1

            # Record only after the recovered control or durable enqueue succeeds.
            self.record_issue_comment(source_id)

        # Store the instant from before the API read, not "now". A comment created while pagination
        # was in progress is included again by the next inclusive since-scan and deduped by durable id.
        with self._state_lock:
            self.state.data["last_issue_comment_reconcile_at"] = scan_started_at
            self.state.save()
        self._metric("issue_comment_reconcile_checks")
        if recovered_wakes:
            self._metric("issue_comment_recovered_wakes", recovered_wakes)
        if recovered_controls:
            self._metric("issue_comment_recovered_controls", recovered_controls)
        if recovered_wakes or recovered_controls:
            print(
                f"[orchestrator] {source} issue-comment reconciliation recovered "
                f"{recovered_wakes} wake(s) and {recovered_controls} control(s)",
                flush=True,
            )

    def health_snapshot(self) -> dict[str, Any]:
        with self._state_lock:
            pending = self.state.data.get("pending_worker")
            pending = pending if isinstance(pending, dict) else None
            started_at = pending.get("started_at") if pending else None
            worker_age_seconds = None
            if started_at:
                try:
                    worker_age_seconds = max(
                        0,
                        int(
                            (
                                datetime.now(timezone.utc)
                                - datetime.fromisoformat(str(started_at))
                            ).total_seconds()
                        ),
                    )
                except ValueError:
                    worker_age_seconds = None
            classifier_calls = int(self.state.data.get("classifier_calls_today") or 0)
            luna_worker_calls = int(self.state.data.get("luna_worker_calls_today") or 0)
            terra_worker_calls = int(self.state.data.get("worker_calls_today") or 0)
            decision_record = self._decision_record()
            decision_payload = (
                decision_record.get("decision")
                if isinstance(decision_record, dict)
                and isinstance(decision_record.get("decision"), dict)
                else None
            )
            return {
                "status": "ok",
                "repo": self.repo,
                "runtime_head": self.runtime_head,
                "pending_events": len(self.state.data.get("pending_events") or []),
                "pending_event_soft_limit": _env_int(
                    "SKYFORGE_ORCHESTRATOR_MAX_PENDING_EVENTS",
                    DEFAULT_MAX_PENDING_EVENTS,
                    minimum=10,
                ),
                "pending_event_high_water": int(
                    (self.state.data.get("metrics") or {}).get("pending_event_high_water") or 0
                ),
                "pending_event_protected_overflow": int(
                    (self.state.data.get("metrics") or {}).get("pending_event_protected_overflow") or 0
                ),
                "last_pending_event_compaction": self.state.data.get(
                    "last_pending_event_compaction"
                ),
                "pending_event_summaries": [
                    EventDecision.from_state(value).summary()
                    for value in (self.state.data.get("pending_events") or [])[-10:]
                    if isinstance(value, dict)
                ],
                "pending_decision": bool(decision_payload),
                "pending_decision_kind": (
                    str(decision_payload.get("decision") or "").upper()
                    if decision_payload
                    else None
                ),
                "pending_decision_lane": (
                    decision_payload.get("lane") if decision_payload else None
                ),
                "pending_decision_pr": (
                    decision_payload.get("pr_number") if decision_payload else None
                ),
                "pending_decision_event_count": (
                    len(decision_record.get("event_keys") or [])
                    if isinstance(decision_record, dict)
                    else 0
                ),
                "pending_worker": bool(pending),
                "worker_lane": pending.get("lane") if pending else None,
                "worker_stage": pending.get("stage") if pending else None,
                "worker_tier": pending.get("worker_tier") if pending else None,
                "worker_started_at": started_at,
                "worker_age_seconds": worker_age_seconds,
                "blocked_kind": self.state.data.get("blocked_kind"),
                "blocked_until_epoch": float(self.state.data.get("blocked_until_epoch") or 0.0),
                "classifier_failure_streak": int(
                    self.state.data.get("classifier_failure_streak") or 0
                ),
                "last_classifier_error_kind": self.state.data.get("last_classifier_error_kind"),
                "last_classifier_error_at": self.state.data.get("last_classifier_error_at"),
                "last_classifier_error_summary": self.state.data.get("last_classifier_error_summary"),
                "last_classifier_success_at": self.state.data.get("last_classifier_success_at"),
                "classifier_calls_today": classifier_calls,
                "luna_worker_calls_today": luna_worker_calls,
                "luna_calls_today_total": classifier_calls + luna_worker_calls,
                "worker_calls_today": terra_worker_calls,
                "terra_worker_calls_today": terra_worker_calls,
                "last_budget_reset_at": self.state.data.get("last_budget_reset_at"),
                "last_budget_reset_by": self.state.data.get("last_budget_reset_by"),
                "last_classifier_decision": self.state.data.get("last_classifier_decision"),
                "last_completed_decision": self.state.data.get("last_completed_decision"),
                "last_decision_invalidation": self.state.data.get("last_decision_invalidation"),
                "last_worker_discard": self.state.data.get("last_worker_discard"),
                "last_runtime_refresh_request": self.state.data.get("last_runtime_refresh_request"),
                "last_runtime_refresh_error": self.state.data.get("last_runtime_refresh_error"),
                "last_human_gate_error": self.state.data.get("last_human_gate_error"),
                "managed_prs": len(self.state.data.get("managed") or {}),
                "last_reconcile_at": self.state.data.get("last_reconcile_at"),
                "last_state_recovery": self.state.data.get("last_state_recovery"),
                "last_startup_reconcile_error": self.state.data.get(
                    "last_startup_reconcile_error"
                ),
                "last_startup_reconcile_success_at": self.state.data.get(
                    "last_startup_reconcile_success_at"
                ),
                "startup_reconcile_retry_at": self.state.data.get(
                    "startup_reconcile_retry_at"
                ),
                "next_periodic_reconcile_at": self.state.data.get(
                    "next_periodic_reconcile_at"
                ),
                "last_periodic_reconcile_success_at": self.state.data.get(
                    "last_periodic_reconcile_success_at"
                ),
                "last_periodic_reconcile_error": self.state.data.get(
                    "last_periodic_reconcile_error"
                ),
                "last_issue_comment_reconcile_at": self.state.data.get(
                    "last_issue_comment_reconcile_at"
                ),
                "issue_comment_reconcile_initialized": bool(
                    self.state.data.get("issue_comment_reconcile_initialized")
                ),
                "paused": bool(self.state.data.get("paused")),
                "paused_at": self.state.data.get("paused_at"),
            }

    def set_paused(self, paused: bool, *, actor: str | None = None) -> None:
        with self._state_lock:
            self.state.data["paused"] = bool(paused)
            self.state.data["paused_at"] = _utc_now() if paused else None
            self.state.data["paused_by"] = actor if paused else None
            self.state.save()
        self._metric("pause_commands" if paused else "resume_commands")
        if not paused and self._pending_events():
            self._schedule_pending(1)

    def is_paused(self) -> bool:
        with self._state_lock:
            return bool(self.state.data.get("paused"))

    def reset_local_budget(self, *, actor: str | None = None) -> None:
        with self._state_lock:
            if not self.state.data.get("paused"):
                raise RuntimeError("Local budget reset requires the controller to be paused")
            if isinstance(self.state.data.get("pending_worker"), dict):
                raise RuntimeError("Local budget reset is forbidden while a worker is pending")
            self.state.data["budget_day"] = _utc_day()
            self.state.data["classifier_calls_today"] = 0
            self.state.data["luna_worker_calls_today"] = 0
            self.state.data["worker_calls_today"] = 0
            if self.state.data.get("blocked_kind") == "local_budget":
                self.state.data["blocked_until_epoch"] = 0.0
                self.state.data["blocked_kind"] = None
                self.state.data["blocked_reason"] = None
            self.state.data["last_budget_reset_at"] = _utc_now()
            self.state.data["last_budget_reset_by"] = actor
            self.state.save()
        self._metric("operator_budget_resets")

    def refresh_runtime(self, *, actor: str | None = None) -> None:
        """Paused-only model-free refresh of the stable controller checkout."""
        with self._state_lock:
            if not self.state.data.get("paused"):
                raise RuntimeError("Runtime refresh requires the controller to be paused")
            pending = self.state.data.get("pending_worker")
            if isinstance(pending, dict) and pending.get("stage") != "handoff":
                raise RuntimeError(
                    "Runtime refresh is forbidden while an isolated worker is still editing"
                )
            if not self._worktree_clean():
                raise RuntimeError("Runtime refresh requires a clean controller checkout")
            self.state.data["last_runtime_refresh_request"] = {
                "requested_at": _utc_now(),
                "requested_by": actor,
            }
            self.state.data["last_runtime_refresh_error"] = None
            self.state.save()
        self._metric("operator_runtime_refreshes")

        def refresh() -> None:
            try:
                with self._dispatch_lock:
                    if not self.is_paused():
                        raise RuntimeError(
                            "Runtime refresh cancelled because the controller is no longer paused"
                        )
                    self.sync_main()
            except Exception as exc:
                with self._state_lock:
                    self.state.data["last_runtime_refresh_error"] = {
                        "at": _utc_now(),
                        "kind": type(exc).__name__,
                        "summary": str(exc)[:500],
                    }
                    self.state.save()
                self._metric("operator_runtime_refresh_failures")
                print(
                    f"[orchestrator] operator runtime refresh failed: {type(exc).__name__}: {exc}",
                    flush=True,
                )

        timer = threading.Timer(1.0, refresh)
        timer.daemon = True
        timer.start()

    def discard_pending_worker(self, *, actor: str | None = None) -> None:
        """Discard only an isolated uncommitted worker while preserving durable decision/events."""
        with self._dispatch_lock:
            with self._state_lock:
                if not self.state.data.get("paused"):
                    raise RuntimeError("Worker discard requires the controller to be paused")
                pending = self.state.data.get("pending_worker")
                if not isinstance(pending, dict):
                    raise RuntimeError("No pending worker exists to discard")
                pending = dict(pending)

            if pending.get("managed_pr"):
                raise RuntimeError("Refusing to discard a worker already associated with a managed PR")
            if pending.get("stage") != "handoff":
                raise RuntimeError("Refusing to discard a worker that has not reached durable handoff")
            raw_worktree = pending.get("worktree")
            if not raw_worktree:
                raise RuntimeError("Refusing to discard a legacy worker without an isolated worktree")
            worktree = Path(str(raw_worktree))
            if not worktree.is_absolute():
                worktree = self.root / worktree
            if worktree.resolve() == self.root.resolve():
                raise RuntimeError("Refusing to discard the controller checkout as a worker")
            if not worktree.exists():
                raise RuntimeError(f"Pending worker worktree is missing: {worktree}")

            current_head = _run(["git", "rev-parse", "HEAD"], cwd=worktree).stdout.strip()
            start_head = str(pending.get("start_head") or "").strip()
            if start_head:
                if current_head != start_head:
                    raise RuntimeError(
                        "Refusing to discard a worker whose HEAD moved after worker preparation"
                    )
            else:
                # Backward-compatible guard for workers created before start-head tracking existed.
                ahead = int(
                    _run(
                        ["git", "rev-list", "--count", "origin/main..HEAD"],
                        cwd=worktree,
                    ).stdout.strip()
                    or "0"
                )
                if ahead > 0:
                    raise RuntimeError(
                        f"Refusing to discard legacy worker with {ahead} commit(s) ahead of origin/main"
                    )

            changed_paths = self._changed_paths(worktree)
            _run(
                ["git", "worktree", "remove", "--force", str(worktree)],
                cwd=self.root,
                timeout=120,
            )
            _run(["git", "worktree", "prune"], cwd=self.root, check=False)
            with self._state_lock:
                current = self.state.data.get("pending_worker")
                if not isinstance(current, dict) or current.get("branch") != pending.get("branch"):
                    raise RuntimeError("Pending worker ownership changed during discard")
                self.state.data["pending_worker"] = None
                self.state.data["last_worker_discard"] = {
                    "discarded_at": _utc_now(),
                    "discarded_by": actor,
                    "branch": pending.get("branch"),
                    "stage": pending.get("stage"),
                    "changed_paths": changed_paths[:50],
                }
                self.state.save()
            self._metric("operator_worker_discards")

    def post_status(self, target: int | str = 349) -> None:
        status = dict(self.health_snapshot())
        status["checkout_head"] = _run(
            ["git", "rev-parse", "HEAD"],
            cwd=self.root,
        ).stdout.strip()
        body = (
            f"{SELF_COMMENT_MARKER} STATUS\n\n"
            + "~~~json\n"
            + json.dumps(status, indent=2, sort_keys=True)
            + "\n~~~"
        )
        _run(
            ["gh", "issue", "comment", str(target), "--repo", self.repo, "--body", body],
            cwd=self.root,
            timeout=60,
        )
        self._metric("status_commands")

    @staticmethod
    def _reconcile_projection(snapshot: dict[str, Any]) -> dict[str, Any]:
        """Project any repository snapshot onto the model-free reconciliation contract."""
        prs = []
        for value in snapshot.get("open_prs") or []:
            if not isinstance(value, dict):
                continue
            prs.append(
                {
                    "number": value.get("number"),
                    "title": value.get("title"),
                    "isDraft": value.get("isDraft"),
                    "headRefName": value.get("headRefName"),
                    "headRefOid": value.get("headRefOid"),
                    "baseRefName": value.get("baseRefName"),
                    "mergeStateStatus": value.get("mergeStateStatus"),
                }
            )
        runs = []
        for value in snapshot.get("recent_runs") or []:
            if not isinstance(value, dict):
                continue
            runs.append(
                {
                    "databaseId": value.get("databaseId"),
                    "name": value.get("name"),
                    "status": value.get("status"),
                    "conclusion": value.get("conclusion"),
                    "headSha": value.get("headSha"),
                    "headBranch": value.get("headBranch"),
                    "event": value.get("event"),
                }
            )
        return {
            "main": snapshot.get("main"),
            "open_prs": sorted(prs, key=lambda item: int(item.get("number") or 0)),
            "recent_runs": sorted(
                runs,
                key=lambda item: int(item.get("databaseId") or 0),
                reverse=True,
            ),
        }

    def _remote_reconcile_snapshot(self) -> dict[str, Any]:
        remote = _run(
            ["git", "ls-remote", "origin", "refs/heads/main"],
            cwd=self.root,
            timeout=60,
        ).stdout.strip()
        main_sha = remote.split()[0] if remote else None
        prs = _json_cmd(
            [
                "gh", "pr", "list",
                "--repo", self.repo,
                "--state", "open",
                "--limit", "50",
                "--json",
                "number,title,isDraft,headRefName,headRefOid,baseRefName,mergeStateStatus",
            ],
            cwd=self.root,
            timeout=60,
        )
        runs = _json_cmd(
            [
                "gh", "run", "list",
                "--repo", self.repo,
                "--limit", "35",
                "--json",
                "databaseId,name,status,conclusion,headSha,headBranch,event",
            ],
            cwd=self.root,
            timeout=60,
        )
        return self._reconcile_projection(
            {
                "main": main_sha,
                "open_prs": prs,
                "recent_runs": runs,
            }
        )

    @staticmethod
    def _reconcile_fingerprint(snapshot: dict[str, Any]) -> str:
        encoded = json.dumps(snapshot, sort_keys=True, separators=(",", ":")).encode("utf-8")
        return hashlib.sha256(encoded).hexdigest()

    def _record_reconcile_observation(
        self,
        snapshot: dict[str, Any],
        *,
        source: str,
    ) -> tuple[str | None, str, dict[str, Any]]:
        projected = self._reconcile_projection(snapshot)
        fingerprint = self._reconcile_fingerprint(projected)
        with self._state_lock:
            previous = self.state.data.get("reconcile_fingerprint")
            self.state.data["reconcile_fingerprint"] = fingerprint
            self.state.data["reconcile_snapshot"] = projected
            self.state.data["last_reconcile_at"] = _utc_now()
            self.state.data["last_reconcile_source"] = source
            self.state.save()
        return previous, fingerprint, projected

    def _checkpoint_classifier_reconcile_observation(self, snapshot: dict[str, Any]) -> None:
        # This is intentionally the exact repository state already presented to the classifier.
        # Never perform a fresh post-dispatch read here: doing so could acknowledge a later webhook
        # transition that the classifier never saw.
        with self._reconcile_observation_lock:
            self._record_reconcile_observation(snapshot, source="classifier_snapshot")
        self._metric("classifier_reconcile_checkpoints")

    def startup_reconcile_repository(self, *, source: str = "startup") -> None:
        with self._reconcile_observation_lock:
            snapshot = self._remote_reconcile_snapshot()
            previous, fingerprint, snapshot = self._record_reconcile_observation(
                snapshot,
                source=source,
            )

        if not previous:
            metric = "startup_reconcile_baselines" if source == "startup" else "periodic_reconcile_baselines"
            self._metric(metric)
            print(
                f"[orchestrator] established first hosted {source} reconciliation baseline",
                flush=True,
            )
            return
        if previous == fingerprint:
            metric = "startup_reconcile_noops" if source == "startup" else "periodic_reconcile_noops"
            self._metric(metric)
            print(
                f"[orchestrator] {source} reconciliation found no repository-state change",
                flush=True,
            )
            return

        if source == "periodic" and any(
            str(run.get("status") or "").lower() in ACTIVE_RUN_STATUSES
            for run in snapshot.get("recent_runs") or []
            if isinstance(run, dict)
        ):
            self._metric("periodic_reconcile_deferred_active_runs")
            print(
                "[orchestrator] periodic reconciliation observed changed state but Actions are still "
                "active; checkpointed model-free and waiting for a quiescent observation",
                flush=True,
            )
            return

        metric = "startup_reconciliations" if source == "startup" else "periodic_reconciliations"
        self._metric(metric)
        self.enqueue(
            EventDecision(
                True,
                "repository state changed since previous controller observation",
                "reconcile",
                action=source,
                head_sha=snapshot.get("main"),
            )
        )
        print(
            f"[orchestrator] {source} reconciliation journaled current repository state after "
            "an uncheckpointed change",
            flush=True,
        )

    def attempt_startup_reconcile(self) -> bool:
        """Run model-free startup reconciliation and retry later if GitHub is temporarily unavailable."""
        try:
            self.startup_reconcile_repository()
            self.reconcile_issue_comments(source="startup")
        except Exception as exc:
            retry_seconds = _env_int(
                "SKYFORGE_STARTUP_RECONCILE_RETRY_SECONDS",
                DEFAULT_STARTUP_RECONCILE_RETRY_SECONDS,
                minimum=30,
            )
            retry_at = datetime.now(timezone.utc) + timedelta(seconds=retry_seconds)
            with self._state_lock:
                self.state.data["last_startup_reconcile_error"] = {
                    "at": _utc_now(),
                    "kind": type(exc).__name__,
                    "summary": str(exc)[:500],
                }
                self.state.data["startup_reconcile_retry_at"] = retry_at.isoformat()
                self.state.save()
            self._metric("startup_reconcile_failures")
            print(
                f"[orchestrator] startup reconciliation failed closed: "
                f"{type(exc).__name__}: {exc}; retrying model-free in {retry_seconds}s",
                flush=True,
            )
            self._schedule_startup_reconcile_retry(retry_seconds)
            return False

        with self._state_lock:
            self.state.data["last_startup_reconcile_error"] = None
            self.state.data["last_startup_reconcile_success_at"] = _utc_now()
            self.state.data["startup_reconcile_retry_at"] = None
            self.state.save()
        self._schedule_periodic_reconcile()
        return True

    def _schedule_startup_reconcile_retry(self, delay_seconds: int | float) -> None:
        if not self.startup_reconcile:
            return
        delay = max(1.0, float(delay_seconds))
        with self._startup_reconcile_retry_lock:
            if self._startup_reconcile_retry_timer is not None:
                self._startup_reconcile_retry_timer.cancel()
            timer = threading.Timer(delay, self._retry_startup_reconcile)
            timer.daemon = True
            self._startup_reconcile_retry_timer = timer
            timer.start()

    def _retry_startup_reconcile(self) -> None:
        with self._startup_reconcile_retry_lock:
            self._startup_reconcile_retry_timer = None
        self.attempt_startup_reconcile()

    def _schedule_periodic_reconcile(self, delay_seconds: int | float | None = None) -> None:
        if not self.startup_reconcile:
            return
        interval = (
            float(delay_seconds)
            if delay_seconds is not None
            else float(
                _env_int(
                    "SKYFORGE_PERIODIC_RECONCILE_SECONDS",
                    DEFAULT_PERIODIC_RECONCILE_SECONDS,
                    minimum=60,
                )
            )
        )
        interval = max(1.0, interval)
        next_at = datetime.now(timezone.utc) + timedelta(seconds=interval)
        with self._state_lock:
            self.state.data["next_periodic_reconcile_at"] = next_at.isoformat()
            self.state.save()
        with self._periodic_reconcile_lock:
            if self._periodic_reconcile_timer is not None:
                self._periodic_reconcile_timer.cancel()
            timer = threading.Timer(interval, self._run_periodic_reconcile)
            timer.daemon = True
            self._periodic_reconcile_timer = timer
            timer.start()

    def _run_periodic_reconcile(self) -> None:
        with self._periodic_reconcile_lock:
            self._periodic_reconcile_timer = None
        with self._state_lock:
            self.state.data["next_periodic_reconcile_at"] = None
            self.state.save()

        try:
            # Trusted controls/Audit comments remain recoverable even when the controller already
            # owns work. Only an additional synthetic repository wake is suppressed while busy.
            self.reconcile_issue_comments(source="periodic")
            with self._state_lock:
                busy = bool(
                    self.state.data.get("pending_events")
                    or self.state.data.get("pending_decision")
                    or isinstance(self.state.data.get("pending_worker"), dict)
                )
            if busy:
                self._metric("periodic_reconcile_deferred_busy")
            else:
                self.startup_reconcile_repository(source="periodic")
        except Exception as exc:
            retry_seconds = _env_int(
                "SKYFORGE_STARTUP_RECONCILE_RETRY_SECONDS",
                DEFAULT_STARTUP_RECONCILE_RETRY_SECONDS,
                minimum=30,
            )
            with self._state_lock:
                self.state.data["last_periodic_reconcile_error"] = {
                    "at": _utc_now(),
                    "kind": type(exc).__name__,
                    "summary": str(exc)[:500],
                }
                self.state.save()
            self._metric("periodic_reconcile_failures")
            print(
                f"[orchestrator] periodic repository reconciliation failed: "
                f"{type(exc).__name__}: {exc}; retrying model-free in {retry_seconds}s",
                flush=True,
            )
            self._schedule_periodic_reconcile(retry_seconds)
            return

        with self._state_lock:
            self.state.data["last_periodic_reconcile_error"] = None
            self.state.data["last_periodic_reconcile_success_at"] = _utc_now()
            self.state.save()
        self._metric("periodic_reconcile_checks")
        self._schedule_periodic_reconcile()

    def _metric(self, name: str, amount: int = 1) -> None:
        with self._state_lock:
            metrics = self.state.data.setdefault("metrics", {})
            metrics[name] = int(metrics.get(name) or 0) + amount
            self.state.save()

    def _reset_daily_budget_if_needed(self) -> None:
        today = _utc_day()
        if self.state.data.get("budget_day") != today:
            self.state.data["budget_day"] = today
            self.state.data["classifier_calls_today"] = 0
            self.state.data["luna_worker_calls_today"] = 0
            self.state.data["worker_calls_today"] = 0

    def _consume_budget(self, kind: str) -> None:
        with self._state_lock:
            self._reset_daily_budget_if_needed()
            if kind in {"classifier", "luna_worker"}:
                classifier_used = int(self.state.data.get("classifier_calls_today") or 0)
                luna_worker_used = int(self.state.data.get("luna_worker_calls_today") or 0)
                used = classifier_used + luna_worker_used
                limit = _env_int(
                    "SKYFORGE_ORCHESTRATOR_MAX_CLASSIFIER_CALLS_PER_DAY",
                    DEFAULT_MAX_CLASSIFIER_CALLS_PER_DAY,
                    minimum=1,
                )
                if used >= limit:
                    raise RetryBlocked(
                        "local_budget",
                        _seconds_until_next_utc_day(),
                        f"local Luna daily call budget exhausted ({used}/{limit})",
                    )
                key = "classifier_calls_today" if kind == "classifier" else "luna_worker_calls_today"
                metric_key = "classifier_attempts" if kind == "classifier" else "luna_worker_attempts"
            elif kind == "worker":
                key = "worker_calls_today"
                used = int(self.state.data.get(key) or 0)
                limit = _env_int(
                    "SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY",
                    DEFAULT_MAX_WORKER_CALLS_PER_DAY,
                    minimum=1,
                )
                if used >= limit:
                    raise RetryBlocked(
                        "local_budget",
                        _seconds_until_next_utc_day(),
                        f"local Terra daily call budget exhausted ({used}/{limit})",
                    )
                metric_key = "terra_worker_attempts"
            else:
                raise ValueError(f"unknown budget kind: {kind}")
            self.state.data[key] = int(self.state.data.get(key) or 0) + 1
            metrics = self.state.data.setdefault("metrics", {})
            metrics[metric_key] = int(metrics.get(metric_key) or 0) + 1
            if kind in {"luna_worker", "worker"}:
                metrics["worker_attempts"] = int(metrics.get("worker_attempts") or 0) + 1
            self.state.save()

    def _pending_events(self) -> list[EventDecision]:
        with self._state_lock:
            values = self.state.data.get("pending_events") or []
            return [EventDecision.from_state(v) for v in values if isinstance(v, dict)]

    @staticmethod
    def _pending_event_priority(value: dict[str, Any]) -> bool:
        event = EventDecision.from_state(value)
        return bool(event.signal_kind) or event.action in {"audit_signal", "manual_command"}

    @staticmethod
    def _pending_event_compaction_slot(value: dict[str, Any]) -> tuple[str, ...]:
        event = EventDecision.from_state(value)
        if event.event == "push":
            return ("push", "main")
        if event.event == "pull_request" and event.pr_number is not None:
            return ("pull_request", str(event.pr_number))
        if event.event == "workflow_run" and event.head_sha:
            return ("workflow_run", str(event.head_sha))
        if event.event == "reconcile":
            return ("reconcile", str(event.action or "reconcile"))
        return (
            str(event.event or "event"),
            str(event.pr_number or ""),
            str(event.head_sha or ""),
            str(event.action or ""),
        )

    def _compact_pending_event_values(
        self,
        values: list[dict[str, Any]],
    ) -> tuple[list[dict[str, Any]], dict[str, Any] | None]:
        """Bound ordinary webhook history without silently losing durable authority.

        Trusted Audit/manual signals and event keys already owned by a cached classifier decision are
        never compacted away. Ordinary repository transitions first coalesce by subject, then any
        remaining excess is represented by one synthetic current-state reconcile event.
        """
        limit = _env_int(
            "SKYFORGE_ORCHESTRATOR_MAX_PENDING_EVENTS",
            DEFAULT_MAX_PENDING_EVENTS,
            minimum=10,
        )
        if len(values) <= limit:
            return values, None

        decision_record = self._decision_record() or {}
        owned_keys = set(decision_record.get("event_keys") or [])
        protected_keys = {
            _event_key(value)
            for value in values
            if _event_key(value) in owned_keys or self._pending_event_priority(value)
        }

        # Coalesce ordinary repository transitions by semantic subject while preserving protected
        # events at their original relative positions. Later ordinary state supersedes earlier state
        # for the same subject.
        latest_unprotected_by_slot: dict[tuple[str, ...], tuple[int, dict[str, Any]]] = {}
        for index, value in enumerate(values):
            key = _event_key(value)
            if key in protected_keys:
                continue
            latest_unprotected_by_slot[self._pending_event_compaction_slot(value)] = (index, value)

        selected_indexes = {
            index
            for index, _ in latest_unprotected_by_slot.values()
        }
        first_pass = [
            value
            for index, value in enumerate(values)
            if _event_key(value) in protected_keys or index in selected_indexes
        ]
        if len(first_pass) <= limit:
            report = {
                "at": _utc_now(),
                "before": len(values),
                "after": len(first_pass),
                "coalesced": len(values) - len(first_pass),
                "reconcile_inserted": False,
                "protected_overflow": max(0, len(protected_keys) - limit),
            }
            return first_pass, report

        protected = [value for value in first_pass if _event_key(value) in protected_keys]
        ordinary = [value for value in first_pass if _event_key(value) not in protected_keys]
        # Leave one slot for a synthetic reconcile whenever ordinary history must be elided. If
        # protected authority itself exceeds the soft cap, preserve it all and append one reconcile.
        ordinary_slots = max(0, limit - len(protected) - 1)
        kept_ordinary = ordinary[-ordinary_slots:] if ordinary_slots else []
        kept_ordinary_keys = {_event_key(value) for value in kept_ordinary}
        dropped_ordinary = max(0, len(ordinary) - len(kept_ordinary))

        reconcile = EventDecision(
            True,
            "pending event history compacted; reconcile current repository truth",
            "reconcile",
            action="queue_compaction",
            observed_at=_utc_now(),
        ).to_state()

        compacted = [
            value
            for value in first_pass
            if _event_key(value) in protected_keys or _event_key(value) in kept_ordinary_keys
        ]
        if dropped_ordinary:
            compacted.append(reconcile)

        report = {
            "at": _utc_now(),
            "before": len(values),
            "after": len(compacted),
            "coalesced": len(values) - len(first_pass),
            "dropped_to_reconcile": dropped_ordinary,
            "reconcile_inserted": bool(dropped_ordinary),
            "protected_overflow": max(0, len(protected) - limit),
        }
        return compacted, report

    def _persist_pending_events(self, events: Iterable[EventDecision]) -> None:
        with self._state_lock:
            current = [v for v in (self.state.data.get("pending_events") or []) if isinstance(v, dict)]
            by_key = {_event_key(v): v for v in current}
            added = False
            for event in events:
                payload = event.to_state()
                key = _event_key(payload)
                if key not in by_key:
                    by_key[key] = payload
                    added = True

            values = list(by_key.values())
            metrics = self.state.data.setdefault("metrics", {})
            metrics["pending_event_high_water"] = max(
                int(metrics.get("pending_event_high_water") or 0),
                len(values),
            )
            compacted, report = self._compact_pending_event_values(values)
            self.state.data["pending_events"] = compacted
            if report:
                self.state.data["last_pending_event_compaction"] = report
                metrics["pending_event_compactions"] = int(
                    metrics.get("pending_event_compactions") or 0
                ) + 1
                metrics["pending_events_compacted"] = int(
                    metrics.get("pending_events_compacted") or 0
                ) + int(report.get("coalesced") or 0) + int(
                    report.get("dropped_to_reconcile") or 0
                )
                metrics["pending_event_protected_overflow"] = max(
                    int(metrics.get("pending_event_protected_overflow") or 0),
                    int(report.get("protected_overflow") or 0),
                )

            # A successful classifier decision owns exactly the event keys it captured. Later webhook
            # events queue behind that batch; they must not erase the cached decision and force Luna
            # to pay for the same earlier batch again. DISPATCH decisions are revalidated against
            # current main/source-PR identity immediately before execution.
            if added and self.state.data.get("pending_decision"):
                metrics["events_queued_behind_cached_decision"] = int(
                    metrics.get("events_queued_behind_cached_decision") or 0
                ) + 1
            self.state.save()

    def _decision_record(self) -> dict[str, Any] | None:
        value = self.state.data.get("pending_decision")
        return value if isinstance(value, dict) else None

    def _cache_decision(
        self,
        decision: dict[str, Any],
        events: list[EventDecision],
        snapshot: dict[str, Any] | None = None,
        classifier_input_fingerprint: str | None = None,
    ) -> None:
        snapshot = snapshot or {}
        source_pr_head = None
        pr_number = decision.get("pr_number")
        if pr_number is not None:
            for pr in snapshot.get("open_prs") or []:
                if (
                    isinstance(pr, dict)
                    and int(pr.get("number") or 0) == int(pr_number)
                ):
                    source_pr_head = pr.get("headRefOid")
                    break
        with self._state_lock:
            self.state.data["pending_decision"] = {
                "decision": decision,
                "event_keys": [_event_key(e) for e in events],
                "captured_at": _utc_now(),
                "snapshot_main": snapshot.get("main"),
                "source_pr_head": source_pr_head,
            }
            self.state.data["last_classifier_decision"] = {
                "kind": str(decision.get("decision") or "NOOP").upper(),
                "lane": decision.get("lane"),
                "pr_number": decision.get("pr_number"),
                "captured_at": _utc_now(),
                "event_count": len(events),
                "input_fingerprint": classifier_input_fingerprint,
            }
            if classifier_input_fingerprint:
                cache = self.state.data.setdefault("classifier_decision_cache", {})
                cache[classifier_input_fingerprint] = {
                    "decision": decision,
                    "cached_at": _utc_now(),
                }
                while len(cache) > 16:
                    cache.pop(next(iter(cache)))
            self.state.save()

    def _classifier_cache_lookup(self, input_fingerprint: str) -> dict[str, Any] | None:
        with self._state_lock:
            cache = self.state.data.get("classifier_decision_cache") or {}
            value = cache.get(input_fingerprint) if isinstance(cache, dict) else None
            if not isinstance(value, dict) or not isinstance(value.get("decision"), dict):
                return None
            return dict(value["decision"])

    def _invalidate_cached_decision(self, reason: str) -> None:
        with self._state_lock:
            if not self.state.data.get("pending_decision"):
                return
            self.state.data["pending_decision"] = None
            self.state.data["last_decision_invalidation"] = {
                "reason": reason[:500],
                "at": _utc_now(),
            }
            metrics = self.state.data.setdefault("metrics", {})
            metrics["cached_decision_invalidations"] = int(
                metrics.get("cached_decision_invalidations") or 0
            ) + 1
            self.state.save()

    def _cached_decision_still_current(self, record: dict[str, Any]) -> bool:
        decision = record.get("decision")
        if not isinstance(decision, dict):
            return False
        kind = str(decision.get("decision") or "NOOP").upper()
        if kind in {"NOOP", "HUMAN_GATE", "MERGE"}:
            # These actions either only retire their captured batch or perform their own current-state
            # validation. Newer events remain queued for the next batch.
            return True
        if kind != "DISPATCH":
            return True

        captured_main = str(record.get("snapshot_main") or "")
        current_main = _run(["git", "rev-parse", "HEAD"], cwd=self.root).stdout.strip()
        if captured_main and captured_main != current_main:
            return False

        pr_number = decision.get("pr_number")
        expected_head = str(record.get("source_pr_head") or "")
        if pr_number is None or not expected_head:
            return True
        current = _json_cmd(
            [
                "gh", "pr", "view", str(pr_number),
                "--repo", self.repo,
                "--json", "state,headRefOid",
            ],
            cwd=self.root,
            timeout=60,
        )
        if str(current.get("state") or "").upper() != "OPEN":
            return False
        return str(current.get("headRefOid") or "") == expected_head

    def _clear_completed_decision(self) -> None:
        with self._state_lock:
            record = self._decision_record() or {}
            completed = set(record.get("event_keys") or [])
            pending = [v for v in (self.state.data.get("pending_events") or []) if isinstance(v, dict)]
            if completed:
                pending = [v for v in pending if _event_key(v) not in completed]
            else:
                pending = []
            completed_decision = record.get("decision") if isinstance(record, dict) else None
            if isinstance(completed_decision, dict):
                self.state.data["last_completed_decision"] = {
                    "kind": str(completed_decision.get("decision") or "NOOP").upper(),
                    "lane": completed_decision.get("lane"),
                    "pr_number": completed_decision.get("pr_number"),
                    "completed_at": _utc_now(),
                    "event_count": len(completed),
                }
            self.state.data["pending_events"] = pending
            self.state.data["pending_decision"] = None
            self.state.data["pending_worker"] = None
            self.state.save()
        if pending:
            self._schedule_pending(1)

    def _set_retry_block(self, kind: str, seconds: int, reason: str) -> None:
        until = time.time() + max(1, int(seconds))
        with self._state_lock:
            previous = float(self.state.data.get("blocked_until_epoch") or 0.0)
            self.state.data["blocked_until_epoch"] = max(previous, until)
            self.state.data["blocked_kind"] = kind
            self.state.data["blocked_reason"] = reason[:2000]
            metrics = self.state.data.setdefault("metrics", {})
            metrics["retry_blocks"] = int(metrics.get("retry_blocks") or 0) + 1
            if kind in {"quota", "rate_limit", "authentication"}:
                metrics["codex_blocks"] = int(metrics.get("codex_blocks") or 0) + 1
            self.state.save()
        self._schedule_pending(max(1, int(self.state.data["blocked_until_epoch"] - time.time())))

    def _blocked_remaining(self) -> int:
        with self._state_lock:
            remaining = int(float(self.state.data.get("blocked_until_epoch") or 0.0) - time.time())
            if remaining <= 0:
                if self.state.data.get("blocked_kind") is not None:
                    self.state.data["blocked_until_epoch"] = 0.0
                    self.state.data["blocked_kind"] = None
                    self.state.data["blocked_reason"] = None
                    self.state.save()
                return 0
            return remaining

    def _schedule_pending(self, delay_seconds: int | float) -> None:
        delay = max(0.1, float(delay_seconds))
        with self._timer_lock:
            if self._timer is not None:
                self._timer.cancel()
            self._timer = threading.Timer(delay, self._drain_and_dispatch)
            self._timer.daemon = True
            self._timer.start()

    def resume_pending(self) -> None:
        pending = self._pending_events()
        if not pending:
            return
        # Successful classifier decisions own their captured event batch across process restarts.
        # Startup reconciliation may queue newer work behind them; cached DISPATCH still revalidates
        # current main/source-PR identity before execution.
        self._metric("restart_replays")
        remaining = self._blocked_remaining()
        self._schedule_pending(remaining if remaining else 1)
        print(
            f"[orchestrator] restored {len(pending)} durable pending event(s) after restart",
            flush=True,
        )

    def enqueue(self, event: EventDecision) -> None:
        self._metric("events_seen")
        if not event.actionable:
            self._metric("events_filtered")
            print(f"[orchestrator] ignored: {event.summary()}", flush=True)
            return
        if event.observed_at is None:
            event = replace(event, observed_at=_utc_now())
        self._metric("events_actionable")
        if event.action == "manual_command":
            self._metric("manual_wakes")
        elif event.action == "audit_signal":
            self._metric("audit_wakes")
        self._persist_pending_events([event])
        print(f"[orchestrator] journaled: {event.summary()}", flush=True)
        if self.is_paused():
            print("[orchestrator] paused; actionable event retained without dispatch", flush=True)
            return
        remaining = self._blocked_remaining()
        self._schedule_pending(remaining if remaining else self.debounce_seconds)

    def _drain_and_dispatch(self) -> None:
        # Timer callbacks may overlap while waiting for the single dispatch lock. Read the durable
        # queue only after acquiring that lock so a delayed callback cannot classify an event snapshot
        # that an earlier dispatch already retired.
        if self.is_paused():
            return
        with self._dispatch_lock:
            if self.is_paused():
                return
            pending = self._pending_events()
            if not pending:
                return
            try:
                self.dispatch(pending)
            except RetryBlocked as exc:
                print(
                    f"[orchestrator] {exc.kind} block: {exc}; retry in {exc.retry_after_seconds}s",
                    flush=True,
                )
                self._set_retry_block(exc.kind, exc.retry_after_seconds, str(exc))
                if exc.kind == "authentication":
                    self._post_gate(
                        {
                            "human_message": (
                                "AUTHENTICATION BLOCK: hosted Codex/ChatGPT authentication is "
                                "unavailable. Durable work is preserved and the controller will retry "
                                "on its normal backoff. Re-authenticate the Skyforge service user if "
                                "this persists."
                            ),
                        }
                    )
            except SafetyPause as exc:
                self._metric("safety_pauses")
                print(f"[orchestrator] safety pause: {exc}", flush=True)
                with self._state_lock:
                    paused_by = self.state.data.get("paused_by")
                if paused_by == "classifier-failure-circuit":
                    self._post_gate(
                        {
                            "human_message": (
                                "SAFETY PAUSE: the hosted classifier hit its consecutive-failure "
                                "circuit breaker. Durable events and decision state are preserved. "
                                "Inspect the classifier/authentication/provider condition before a "
                                "trusted /skyforge-resume."
                            ),
                        }
                    )
            except Exception as exc:
                self._metric("dispatch_failures")
                delay = _env_int(
                    "SKYFORGE_ORCHESTRATOR_TRANSIENT_BACKOFF_SECONDS",
                    DEFAULT_TRANSIENT_BACKOFF_SECONDS,
                    minimum=30,
                )
                print(f"[orchestrator] dispatch error: {type(exc).__name__}: {exc}", flush=True)
                self._set_retry_block("controller_error", delay, f"{type(exc).__name__}: {exc}")

    def _worktree_clean(self, cwd: Path | None = None) -> bool:
        target = cwd or self.root
        out = _run(["git", "status", "--porcelain"], cwd=target).stdout.strip()
        return not out

    def _current_branch(self, cwd: Path | None = None) -> str:
        target = cwd or self.root
        return _run(["git", "branch", "--show-current"], cwd=target).stdout.strip()

    def _worker_worktree_path(self, branch: str) -> Path:
        slug = re.sub(r"[^A-Za-z0-9._-]+", "-", branch).strip("-") or "worker"
        return self.root / STATE_DIR / "worktrees" / slug[-120:]

    def _ensure_worker_worktree(self, branch: str, start_ref: str) -> Path:
        worktree = self._worker_worktree_path(branch)
        worktree.parent.mkdir(parents=True, exist_ok=True)

        if worktree.exists():
            probe = _run(
                ["git", "rev-parse", "--is-inside-work-tree"],
                cwd=worktree,
                check=False,
            )
            if probe.returncode != 0:
                raise RuntimeError(
                    f"Worker worktree path exists but is not a Git worktree: {worktree}"
                )
            current = self._current_branch(worktree)
            if current != branch:
                raise RuntimeError(
                    f"Worker worktree {worktree} is on {current!r}; expected {branch!r}"
                )
            if not self._worktree_clean(worktree):
                raise RuntimeError(
                    f"Orphaned worker worktree is dirty without pending-worker ownership: {worktree}"
                )
            # This path is reached only for a new dispatch with no pending-worker state. A clean
            # leftover worktree from a prior cleanup failure is safe to realign to the requested
            # durable start ref before reuse.
            _run(["git", "reset", "--hard", start_ref], cwd=worktree)
            return worktree

        local_exists = _run(
            ["git", "show-ref", "--verify", f"refs/heads/{branch}"],
            cwd=self.root,
            check=False,
        ).returncode == 0
        if local_exists:
            _run(["git", "worktree", "prune"], cwd=self.root, check=False)
            _run(["git", "branch", "-f", branch, start_ref], cwd=self.root)
            _run(["git", "worktree", "add", str(worktree), branch], cwd=self.root, timeout=120)
        else:
            _run(
                ["git", "worktree", "add", "-b", branch, str(worktree), start_ref],
                cwd=self.root,
                timeout=120,
            )
        return worktree

    def _retire_worker_worktree(self, worktree: Path | None) -> None:
        if worktree is None or worktree.resolve() == self.root.resolve():
            return
        if worktree.exists():
            if not self._worktree_clean(worktree):
                raise RuntimeError(
                    f"Refusing to retire dirty worker worktree after handoff: {worktree}"
                )
            _run(
                ["git", "worktree", "remove", "--force", str(worktree)],
                cwd=self.root,
                timeout=120,
            )
        _run(["git", "worktree", "prune"], cwd=self.root, check=False)

    def _request_runtime_restart(
        self,
        previous_head: str,
        current_head: str,
        changed_paths: list[str],
    ) -> None:
        with self._state_lock:
            self.state.data["runtime_restart_requested"] = {
                "from_head": previous_head,
                "to_head": current_head,
                "changed_paths": changed_paths,
                "requested_at": _utc_now(),
            }
            self.state.save()
        self._metric("runtime_restarts_requested")
        print(
            "[orchestrator] controller Python changed on main; durable state is preserved and "
            f"systemd restart is required ({previous_head[:10]} -> {current_head[:10]}): "
            + ", ".join(changed_paths),
            flush=True,
        )
        # The systemd unit uses Restart=on-failure. Exit non-zero so ExecStart reloads the
        # just-synchronized Python source from the stable main checkout. Actionable events are
        # already durable and will be replayed by resume_pending() in the replacement process.
        os._exit(75)

    def sync_main(self) -> None:
        if not self._worktree_clean():
            raise RuntimeError("Dedicated clone is dirty; refusing autonomous checkout/sync")
        previous_head = self.runtime_head or _run(
            ["git", "rev-parse", "HEAD"],
            cwd=self.root,
        ).stdout.strip()
        _run(["git", "fetch", "--prune", "origin"], cwd=self.root, timeout=180)
        _run(["git", "checkout", "main"], cwd=self.root)
        _run(["git", "pull", "--ff-only", "origin", "main"], cwd=self.root, timeout=180)
        current_head = _run(
            ["git", "rev-parse", "HEAD"],
            cwd=self.root,
        ).stdout.strip()
        if previous_head != current_head:
            changed = [
                line.strip()
                for line in _run(
                    [
                        "git", "diff", "--name-only",
                        previous_head, current_head,
                        "--", "scripts/orchestrator",
                    ],
                    cwd=self.root,
                ).stdout.splitlines()
                if line.strip()
            ]
            runtime_changes = [path for path in changed if path in CONTROLLER_RUNTIME_PATHS]
            self.runtime_head = current_head
            if runtime_changes:
                self._request_runtime_restart(previous_head, current_head, runtime_changes)
        else:
            self.runtime_head = current_head

    def workflows_quiescent(self, head_sha: str | None) -> bool:
        if not head_sha:
            return True
        runs = _json_cmd(
            [
                "gh", "run", "list",
                "--repo", self.repo,
                "--limit", "50",
                "--json", "headSha,status,conclusion,name,databaseId",
            ],
            cwd=self.root,
            timeout=60,
        )
        relevant = [r for r in runs if r.get("headSha") == head_sha]
        active = [r for r in relevant if str(r.get("status") or "").lower() in ACTIVE_RUN_STATUSES]
        if active:
            names = ", ".join(str(r.get("name")) for r in active[:5])
            print(f"[orchestrator] head {head_sha[:10]} still has active runs: {names}", flush=True)
            return False
        return True

    def snapshot(self) -> dict[str, Any]:
        prs = _json_cmd(
            [
                "gh", "pr", "list",
                "--repo", self.repo,
                "--state", "open",
                "--limit", "50",
                "--json",
                "number,title,isDraft,headRefName,headRefOid,baseRefName,url,author,mergeStateStatus",
            ],
            cwd=self.root,
        )
        runs = _json_cmd(
            [
                "gh", "run", "list",
                "--repo", self.repo,
                "--limit", "35",
                "--json",
                "databaseId,name,status,conclusion,headSha,headBranch,event,updatedAt",
            ],
            cwd=self.root,
        )
        log = _run(
            ["git", "log", "-12", "--pretty=format:%h %cI %s"],
            cwd=self.root,
        ).stdout.splitlines()
        return {
            "captured_at": _utc_now(),
            "main": _run(["git", "rev-parse", "HEAD"], cwd=self.root).stdout.strip(),
            "open_prs": prs,
            "recent_runs": runs,
            "recent_commits": log,
            "controller_managed": self.state.data.get("managed", {}),
            "orchestrator_metrics": self.state.data.get("metrics", {}),
        }

    def _record_classifier_success(self) -> None:
        with self._state_lock:
            self.state.data["classifier_failure_streak"] = 0
            self.state.data["last_classifier_success_at"] = _utc_now()
            self.state.save()

    def _record_classifier_failure(self, kind: str, exc: Exception) -> tuple[int, bool]:
        summary = f"{type(exc).__name__}: {exc}"
        summary = re.sub(
            r"(?i)\b(authorization)\b\s*[:=]?\s*(?:bearer\s+)?\S+",
            r"\1=[REDACTED]",
            summary,
        )
        summary = re.sub(
            r"(?i)\b(bearer)\b\s+\S+",
            r"\1 [REDACTED]",
            summary,
        )
        summary = re.sub(
            r"(?i)\b(api[_ -]?key)\b\s*[:=]?\s*\S+",
            r"\1=[REDACTED]",
            summary,
        )
        with self._state_lock:
            streak = int(self.state.data.get("classifier_failure_streak") or 0) + 1
            self.state.data["classifier_failure_streak"] = streak
            self.state.data["last_classifier_error_kind"] = kind
            self.state.data["last_classifier_error_at"] = _utc_now()
            self.state.data["last_classifier_error_summary"] = summary[:500]
            self.state.save()
        self._metric("classifier_failures")
        threshold = _env_int(
            "SKYFORGE_ORCHESTRATOR_MAX_CONSECUTIVE_CLASSIFIER_FAILURES",
            DEFAULT_MAX_CONSECUTIVE_CLASSIFIER_FAILURES,
            minimum=1,
        )
        circuit_open = streak >= threshold
        if circuit_open:
            self.set_paused(True, actor="classifier-failure-circuit")
            self._metric("classifier_failure_circuit_pauses")
        return streak, circuit_open

    def _codex_classifier(self, prompt: str) -> dict[str, Any]:
        self._consume_budget("classifier")
        try:
            from openai_codex import Codex, Sandbox

            parent_model = os.environ.get("SKYFORGE_ORCHESTRATOR_MODEL", "gpt-5.6-luna")
            parent_effort = os.environ.get("SKYFORGE_ORCHESTRATOR_REASONING", "low")
            thread_id = self.state.data.get("parent_thread_id")
            turns = int(self.state.data.get("parent_turns") or 0)

            with Codex() as codex:
                if thread_id and turns < self.max_parent_turns:
                    try:
                        thread = codex.thread_resume(
                            thread_id,
                            cwd=str(self.root),
                            model=parent_model,
                            config={"model_reasoning_effort": parent_effort},
                            sandbox=Sandbox.read_only,
                            developer_instructions=CLASSIFIER_INSTRUCTIONS,
                        )
                    except Exception as exc:
                        kind, retry = _codex_failure_policy(exc)
                        if kind in {"quota", "rate_limit", "authentication"}:
                            raise RetryBlocked(kind, retry, f"classifier resume failed: {exc}") from exc
                        thread = codex.thread_start(
                            cwd=str(self.root),
                            model=parent_model,
                            config={"model_reasoning_effort": parent_effort},
                            sandbox=Sandbox.read_only,
                            developer_instructions=CLASSIFIER_INSTRUCTIONS,
                        )
                        turns = 0
                else:
                    thread = codex.thread_start(
                        cwd=str(self.root),
                        model=parent_model,
                        config={"model_reasoning_effort": parent_effort},
                        sandbox=Sandbox.read_only,
                        developer_instructions=CLASSIFIER_INSTRUCTIONS,
                    )
                    turns = 0

                result = thread.run(prompt, sandbox=Sandbox.read_only)
                decision = _clean_json_object(result.final_response)
                with self._state_lock:
                    self.state.data["parent_thread_id"] = thread.id
                    self.state.data["parent_turns"] = turns + 1
                    self.state.save()
                self._record_classifier_success()
                return decision
        except RetryBlocked:
            raise
        except Exception as exc:
            kind, retry = _codex_failure_policy(exc)
            streak, circuit_open = self._record_classifier_failure(kind, exc)
            if circuit_open:
                raise SafetyPause(
                    f"classifier failed {streak} consecutive attempts; controller safety-paused "
                    f"with durable events retained (last kind={kind})"
                ) from exc
            raise RetryBlocked(kind, retry, f"classifier call failed: {exc}") from exc

    def _worker(
        self,
        prompt: str,
        worker_tier: str,
        worker_root: Path | None = None,
    ) -> str:
        tier = str(worker_tier or "TERRA").strip().upper()
        if tier == "LUNA":
            self._consume_budget("luna_worker")
            model = os.environ.get(
                "SKYFORGE_LUNA_WORKER_MODEL",
                os.environ.get("SKYFORGE_ORCHESTRATOR_MODEL", "gpt-5.6-luna"),
            )
            effort = os.environ.get("SKYFORGE_LUNA_WORKER_REASONING", "low")
        elif tier == "TERRA":
            self._consume_budget("worker")
            model = os.environ.get("SKYFORGE_WORKER_MODEL", "gpt-5.6-terra")
            effort = os.environ.get("SKYFORGE_WORKER_REASONING", "medium")
        else:
            raise RuntimeError(f"Unknown worker tier: {tier}")
        try:
            from openai_codex import Codex, Sandbox

            with Codex() as codex:
                thread = codex.thread_start(
                    cwd=str(worker_root or self.root),
                    model=model,
                    config={"model_reasoning_effort": effort},
                    sandbox=Sandbox.workspace_write,
                    developer_instructions=WORKER_INSTRUCTIONS,
                    ephemeral=True,
                )
                result = thread.run(prompt, sandbox=Sandbox.workspace_write)
                return result.final_response
        except RetryBlocked:
            raise
        except Exception as exc:
            kind, retry = _codex_failure_policy(exc)
            raise RetryBlocked(kind, retry, f"{tier.lower()} worker call failed: {exc}") from exc

    def _managed_branch(self, lane: str) -> dict[str, Any] | None:
        value = (self.state.data.get("managed") or {}).get(lane)
        return value if isinstance(value, dict) else None

    def _validated_managed_branch(self, lane: str) -> dict[str, Any] | None:
        """Return only a still-open controller-managed PR record.

        Manual merges are expected while auto-merge is disabled. Do not let a locally stale managed
        record pin the next worker in that lane to an already-closed branch/PR.
        """
        managed = self._managed_branch(lane)
        if not managed:
            return None
        branch = str(managed.get("branch") or "").strip()
        pr_number = int(managed.get("pr_number") or 0)
        if not branch or pr_number <= 0:
            with self._state_lock:
                self.state.data.setdefault("managed", {}).pop(lane, None)
                metrics = self.state.data.setdefault("metrics", {})
                metrics["stale_managed_records_retired"] = int(
                    metrics.get("stale_managed_records_retired") or 0
                ) + 1
                self.state.save()
            return None

        pr = _json_cmd(
            [
                "gh", "pr", "view", str(pr_number),
                "--repo", self.repo,
                "--json", "state,headRefName",
            ],
            cwd=self.root,
            timeout=60,
        )
        if (
            str(pr.get("state") or "").upper() == "OPEN"
            and str(pr.get("headRefName") or "") == branch
        ):
            return managed

        with self._state_lock:
            current = self._managed_branch(lane)
            if (
                current
                and int(current.get("pr_number") or 0) == pr_number
                and str(current.get("branch") or "") == branch
            ):
                self.state.data.setdefault("managed", {}).pop(lane, None)
                metrics = self.state.data.setdefault("metrics", {})
                metrics["stale_managed_records_retired"] = int(
                    metrics.get("stale_managed_records_retired") or 0
                ) + 1
                self.state.save()
        print(
            f"[orchestrator] retired stale managed record for {lane}: "
            f"PR #{pr_number} / {branch}",
            flush=True,
        )
        return None

    def _source_pr_changed_paths(self, source_pr: int | None) -> list[str]:
        if not source_pr:
            return []
        try:
            output = _run(
                ["gh", "pr", "diff", str(source_pr), "--repo", self.repo, "--name-only"],
                cwd=self.root,
                timeout=60,
            ).stdout.splitlines()
            return sorted({line.strip() for line in output if line.strip()})
        except Exception as exc:
            print(
                f"[orchestrator] could not inspect source PR #{source_pr} changed paths: {exc}",
                flush=True,
            )
            return []

    def _prepare_worker_branch(
        self,
        lane: str,
        source_pr: int | None,
    ) -> tuple[str, int | None, Path]:
        managed = self._validated_managed_branch(lane)
        if managed and managed.get("branch"):
            branch = str(managed["branch"])
            _run(["git", "fetch", "origin", branch], cwd=self.root, timeout=120)
            worktree = self._ensure_worker_worktree(branch, f"origin/{branch}")
            return branch, managed.get("pr_number"), worktree

        stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
        slug = re.sub(r"[^a-z0-9]+", "-", lane.lower()).strip("-") or "lane"
        branch = f"codex/{slug}-{stamp}"
        worktree = self._ensure_worker_worktree(branch, "origin/main")

        if source_pr:
            try:
                pr = _json_cmd(
                    ["gh", "pr", "view", str(source_pr), "--repo", self.repo, "--json", "headRefName"],
                    cwd=self.root,
                )
                old_branch = pr.get("headRefName")
                if old_branch:
                    _run(["git", "fetch", "origin", old_branch], cwd=self.root, timeout=120)
            except Exception:
                pass
        return branch, None, worktree

    def _resume_or_prepare_worker(
        self,
        lane: str,
        source_pr: int | None,
        objective: str,
        worker_tier: str,
        allowed_paths: list[str] | None,
    ) -> tuple[str, int | None, Path]:
        pending = self.state.data.get("pending_worker")
        if isinstance(pending, dict) and pending.get("branch"):
            branch = str(pending["branch"])
            managed_pr = pending.get("managed_pr")
            stored_worktree = pending.get("worktree")
            if stored_worktree:
                worktree = Path(str(stored_worktree))
                if not worktree.is_absolute():
                    worktree = self.root / worktree
                if not worktree.exists():
                    raise RuntimeError(
                        f"Interrupted worker worktree is missing: {worktree}. "
                        "Manual inspection required before autonomous recovery."
                    )
                current = self._current_branch(worktree)
                if current != branch:
                    raise RuntimeError(
                        f"Interrupted worker worktree is on {current!r}; expected {branch!r}. "
                        "Manual inspection required before autonomous recovery."
                    )
            else:
                # Backward-compatible recovery for pre-isolation state. Preserve the legacy worktree
                # exactly where it is rather than moving dirty partial work implicitly.
                current = self._current_branch()
                if current != branch:
                    raise RuntimeError(
                        f"Legacy interrupted worker expected branch {branch!r}, but controller root "
                        f"is on {current!r}. Manual inspection required before autonomous recovery."
                    )
                worktree = self.root
            self._metric("worker_resumes")
            return branch, managed_pr, worktree

        branch, managed_pr, worktree = self._prepare_worker_branch(lane, source_pr)
        start_head = _run(["git", "rev-parse", "HEAD"], cwd=worktree).stdout.strip()
        with self._state_lock:
            self.state.data["pending_worker"] = {
                "lane": lane,
                "branch": branch,
                "worktree": str(worktree),
                "start_head": start_head,
                "managed_pr": managed_pr,
                "objective": objective,
                "worker_tier": worker_tier,
                "allowed_paths": allowed_paths,
                "source_pr": source_pr,
                "stage": "editing",
                "worker_summary": None,
                "started_at": _utc_now(),
            }
            self.state.save()
        return branch, managed_pr, worktree

    def _mark_worker_handoff(self, worker_summary: str) -> None:
        with self._state_lock:
            pending = self.state.data.get("pending_worker")
            if not isinstance(pending, dict):
                raise RuntimeError("Cannot persist worker handoff without pending worker state")
            pending["stage"] = "handoff"
            pending["worker_summary"] = worker_summary[:8000]
            pending["worker_completed_at"] = _utc_now()
            self.state.save()

    @staticmethod
    def _worker_path_forbidden(
        path: str,
        *,
        lane: str | None = None,
        allowed_paths: list[str] | None = None,
    ) -> bool:
        normalized = path.replace("\\", "/")
        while normalized.startswith("./"):
            normalized = normalized[2:]

        if normalized == "docs/agent-state/AUDIT_STATE.md":
            exact_scopes = {
                str(entry or "").replace("\\", "/").lstrip("./")
                for entry in (allowed_paths or [])
            }
            return not (
                str(lane or "").strip().lower() == "audit"
                and normalized in exact_scopes
            )

        return (
            normalized in PROTECTED_WORKER_PATHS
            or any(normalized.startswith(prefix) for prefix in PROTECTED_WORKER_PATH_PREFIXES)
            or normalized.startswith(f"{STATE_DIR}/")
            or normalized.startswith(".git/")
        )

    @staticmethod
    def _worker_path_allowed(path: str, allowed_paths: list[str] | None) -> bool:
        if allowed_paths is None:
            return True
        normalized = path.replace("\\", "/").lstrip("./")
        for entry in allowed_paths:
            scope = str(entry or "").replace("\\", "/").lstrip("./")
            if not scope:
                continue
            if scope.endswith("/**"):
                prefix = scope[:-3].rstrip("/") + "/"
                if normalized.startswith(prefix):
                    return True
            elif normalized == scope:
                return True
        return False

    def _changed_paths(self, cwd: Path | None = None) -> list[str]:
        target = cwd or self.root
        output = _run(["git", "status", "--porcelain"], cwd=target).stdout.splitlines()
        paths: list[str] = []
        for line in output:
            if not line:
                continue
            raw = line[3:] if len(line) > 3 else line
            if " -> " in raw:
                raw = raw.split(" -> ", 1)[1]
            paths.append(raw.strip())
        return paths

    def _handoff_changes(
        self,
        lane: str,
        objective: str,
        branch: str,
        managed_pr: int | None,
        worker_summary: str,
        allowed_paths: list[str] | None = None,
        worker_root: Path | None = None,
    ) -> bool:
        worktree = worker_root or self.root
        paths = self._changed_paths(worktree)
        forbidden = [
            p
            for p in paths
            if self._worker_path_forbidden(
                p,
                lane=lane,
                allowed_paths=allowed_paths,
            )
        ]
        out_of_scope = [
            p for p in paths if not self._worker_path_allowed(p, allowed_paths)
        ]
        if forbidden or out_of_scope:
            if forbidden:
                self._metric("worker_protected_path_rejections")
            if out_of_scope:
                self._metric("worker_scope_rejections")
            self.set_paused(True, actor="controller-safety")
            details = []
            if forbidden:
                details.append(f"protected paths {forbidden}")
            if out_of_scope:
                details.append(f"paths outside the bounded edit scope {out_of_scope}")
            detail_text = "; ".join(details)
            self._post_gate(
                {
                    "pr_number": managed_pr,
                    "human_message": (
                        "SAFETY PAUSE: a hosted worker modified "
                        f"{detail_text}. No autonomous commit/push occurred. Inspect or discard the "
                        "local changes on the hosted worker branch, then use /skyforge-resume only after "
                        "the worktree is safe."
                    ),
                }
            )
            raise SafetyPause(
                f"Worker handoff rejected ({detail_text}); controller safety-paused"
            )

        short = re.sub(r"\s+", " ", objective).strip()[:72]
        if paths:
            _run(["git", "diff", "--check"], cwd=worktree)
            _run(["git", "add", "--all"], cwd=worktree)
            _run(["git", "commit", "-m", f"CODEX {lane}: {short}"], cwd=worktree, timeout=120)

        ahead = int(
            _run(["git", "rev-list", "--count", "origin/main..HEAD"], cwd=worktree).stdout.strip() or "0"
        )
        if ahead <= 0:
            self._metric("worker_no_change")
            print(f"[orchestrator] worker made no repository changes for {lane}", flush=True)
            return False

        # Push and PR creation are intentionally idempotent so an interrupted handoff resumes without
        # rerunning the model or losing a commit that already exists locally/remotely.
        _run(["git", "push", "-u", "origin", branch], cwd=worktree, timeout=180)

        if managed_pr:
            pr_number = int(managed_pr)
        else:
            existing = _json_cmd(
                [
                    "gh", "pr", "list",
                    "--repo", self.repo,
                    "--state", "open",
                    "--head", branch,
                    "--limit", "5",
                    "--json", "number",
                ],
                cwd=self.root,
                timeout=60,
            )
            if existing:
                pr_number = int(existing[0]["number"])
            else:
                body = (
                    "Automated bounded-work pilot from the Skyforge event-driven orchestrator.\n\n"
                    f"**Lane:** {lane}\n\n"
                    f"**Objective:** {objective}\n\n"
                    "Worker summary:\n\n"
                    f"{worker_summary[:4000]}\n\n"
                    "This PR is intentionally draft until ordinary machine/human acceptance gates are satisfied."
                )
                created = _run(
                    [
                        "gh", "pr", "create",
                        "--repo", self.repo,
                        "--draft",
                        "--base", "main",
                        "--head", branch,
                        "--title", f"CODEX {lane}: {short}",
                        "--body", body,
                    ],
                    cwd=self.root,
                    timeout=120,
                ).stdout.strip()
                match = re.search(r"/pull/(\d+)", created)
                if not match:
                    raise RuntimeError(f"Could not parse created PR URL: {created}")
                pr_number = int(match.group(1))

        with self._state_lock:
            managed_map = self.state.data.setdefault("managed", {})
            managed_map[lane] = {
                "branch": branch,
                "pr_number": pr_number,
                "updated_at": _utc_now(),
            }
            self.state.save()
        self._metric("worker_handoffs")
        print(f"[orchestrator] handed off {lane} on {branch} / PR #{pr_number}", flush=True)
        return True

    def _schedule_no_change_followup(self, events: list[EventDecision]) -> bool:
        """Preserve one bounded reclassification opportunity after a multi-event no-change dispatch."""
        if len(events) <= 1 or self._pending_events():
            return False
        self._metric("no_change_followup_reconciliations")
        self.enqueue(
            EventDecision(
                True,
                "multi-event batch worker made no repository changes; re-evaluate remaining state once",
                "reconcile",
                action="no_change_followup",
                head_sha=self.runtime_head,
            )
        )
        return True

    @staticmethod
    def _parse_github_time(value: Any) -> datetime | None:
        if not value:
            return None
        text = str(value).strip()
        if text.endswith("Z"):
            text = text[:-1] + "+00:00"
        try:
            parsed = datetime.fromisoformat(text)
        except ValueError:
            return None
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed.astimezone(timezone.utc)

    def _human_gate_identity(
        self,
        decision: dict[str, Any],
    ) -> tuple[str, str, str, bool]:
        pr = decision.get("pr_number")
        lane = str(decision.get("lane") or "program").strip().lower() or "program"
        if isinstance(pr, int):
            target = str(pr)
            key = f"pr:{pr}:{lane}"
            try:
                info = _json_cmd(
                    [
                        "gh", "pr", "view", target,
                        "--repo", self.repo,
                        "--json", "headRefOid,state,commits,comments",
                    ],
                    cwd=self.root,
                    timeout=60,
                )
                head = str(info.get("headRefOid") or "unknown")
                state = str(info.get("state") or "unknown").upper()
                token = f"{head}:{state}"

                commits = info.get("commits") or []
                head_time = None
                if commits:
                    last = commits[-1] or {}
                    head_time = self._parse_github_time(
                        last.get("committedDate") or last.get("authoredDate")
                    )
                prior_gate_after_head = False
                if head_time is not None:
                    for comment in info.get("comments") or []:
                        body = str((comment or {}).get("body") or "")
                        if SELF_COMMENT_MARKER not in body or "HUMAN_GATE" not in body:
                            continue
                        created = self._parse_github_time((comment or {}).get("createdAt"))
                        if created is not None and created >= head_time:
                            prior_gate_after_head = True
                            break
                return key, token, target, prior_gate_after_head
            except Exception as exc:
                print(
                    f"[orchestrator] could not inspect PR #{pr} for gate deduplication: {exc}",
                    flush=True,
                )
                return key, "unknown", target, False

        target = "349"
        message = str(
            decision.get("human_message")
            or decision.get("reason")
            or "Human gate reached"
        )
        normalized = re.sub(r"[^a-z0-9]+", " ", message.lower()).strip()
        digest = hashlib.sha256(normalized.encode("utf-8")).hexdigest()
        return f"issue:{target}:{lane}", digest, target, False

    def _post_gate(self, decision: dict[str, Any]) -> bool:
        message = str(decision.get("human_message") or decision.get("reason") or "Human gate reached")
        key, token, target, already_visible = self._human_gate_identity(decision)

        with self._state_lock:
            records = self.state.data.setdefault("human_gate_records", {})
            prior = records.get(key)
            if isinstance(prior, dict) and prior.get("token") == token:
                self._metric("human_gate_duplicates_suppressed")
                print(
                    f"[orchestrator] human gate already surfaced for {key} at state {token}; suppressing duplicate",
                    flush=True,
                )
                return True
            if already_visible:
                records[key] = {
                    "token": token,
                    "target": target,
                    "seeded_from_github": True,
                    "recorded_at": _utc_now(),
                }
                self.state.save()
                self._metric("human_gate_duplicates_suppressed")
                print(
                    f"[orchestrator] existing controller human gate already covers {key} at state {token}; suppressing duplicate",
                    flush=True,
                )
                return True

        body = f"{SELF_COMMENT_MARKER} HUMAN_GATE\n\n{message[:5000]}"
        try:
            _run(
                ["gh", "issue", "comment", target, "--repo", self.repo, "--body", body],
                cwd=self.root,
                timeout=60,
            )
            with self._state_lock:
                records = self.state.data.setdefault("human_gate_records", {})
                records[key] = {
                    "token": token,
                    "target": target,
                    "seeded_from_github": False,
                    "recorded_at": _utc_now(),
                }
                self.state.data["last_human_gate_error"] = None
                self.state.save()
            self._metric("human_gates")
            return True
        except Exception as exc:
            with self._state_lock:
                self.state.data["last_human_gate_error"] = {
                    "at": _utc_now(),
                    "kind": type(exc).__name__,
                    "summary": str(exc)[:500],
                    "target": target,
                }
                self.state.save()
            self._metric("human_gate_post_failures")
            print(f"[orchestrator] could not post human gate: {exc}", flush=True)
            return False

    def _merge_managed(self, lane: str, pr_number: int | None) -> None:
        if not self.auto_merge:
            print("[orchestrator] MERGE selected, but auto-merge is disabled for pilot", flush=True)
            return
        managed = self._managed_branch(lane)
        if not managed or int(managed.get("pr_number") or 0) != int(pr_number or 0):
            raise RuntimeError("Refusing to merge a PR not owned by the local orchestrator state")
        pr = _json_cmd(
            [
                "gh", "pr", "view", str(pr_number),
                "--repo", self.repo,
                "--json", "isDraft,mergeStateStatus,statusCheckRollup,state",
            ],
            cwd=self.root,
        )
        if pr.get("state") != "OPEN":
            return
        checks = pr.get("statusCheckRollup") or []
        bad = []
        active = []
        for check in checks:
            status = str(check.get("status") or "").upper()
            conclusion = str(check.get("conclusion") or "").upper()
            name = check.get("name") or check.get("context") or "check"
            if status != "COMPLETED":
                active.append(name)
            elif conclusion not in {"SUCCESS", "SKIPPED", "NEUTRAL"}:
                bad.append(f"{name}:{conclusion}")
        if active or bad:
            raise RuntimeError(f"Managed PR not machine-green; active={active}, bad={bad}")
        if pr.get("isDraft"):
            _run(["gh", "pr", "ready", str(pr_number), "--repo", self.repo], cwd=self.root)
        _run(["gh", "pr", "merge", str(pr_number), "--repo", self.repo, "--merge"], cwd=self.root, timeout=120)
        with self._state_lock:
            self.state.data.setdefault("managed", {}).pop(lane, None)
            self.state.save()
        self._metric("managed_merges")
        print(f"[orchestrator] merged managed PR #{pr_number}", flush=True)

    def dispatch(self, events: list[EventDecision]) -> None:
        remaining = self._blocked_remaining()
        if remaining:
            self._schedule_pending(remaining)
            return

        now = time.time()
        last = float(self.state.data.get("last_dispatch_epoch") or 0.0)
        if now - last < self.min_dispatch_seconds:
            remaining = max(1, int(self.min_dispatch_seconds - (now - last)))
            print(
                f"[orchestrator] minimum dispatch interval not elapsed; retrying durable batch in {remaining}s",
                flush=True,
            )
            self._schedule_pending(remaining)
            return

        record = self._decision_record()
        pending_worker = self.state.data.get("pending_worker")

        if record is None:
            # Quiescence is a pre-classification gate. Newer events that arrived behind an already
            # owned decision must not delay completion of that older decision.
            heads = sorted({e.head_sha for e in events if e.head_sha})
            for head in heads:
                if not self.workflows_quiescent(head):
                    self._schedule_pending(max(30, self.debounce_seconds))
                    return

        if not (record and isinstance(pending_worker, dict)):
            self.sync_main()

        if record:
            decision = record.get("decision")
            if not isinstance(decision, dict):
                decision = None
        else:
            decision = None

        if record and not isinstance(pending_worker, dict):
            if not self._cached_decision_still_current(record):
                self._invalidate_cached_decision(
                    "cached DISPATCH no longer matches current main/source PR identity"
                )
                record = None
                decision = None
                self._metric("cached_dispatch_reclassifications")

        if decision is None:
            observed_epochs = []
            for event in events:
                if not event.observed_at:
                    continue
                try:
                    observed_epochs.append(datetime.fromisoformat(event.observed_at).timestamp())
                except ValueError:
                    continue
            if observed_epochs:
                latency_ms = max(0, int((time.time() - min(observed_epochs)) * 1000))
                self._metric("dispatch_latency_ms_total", latency_ms)
                self._metric("dispatch_latency_samples")
            snap = self.snapshot()
            summaries = [e.summary() for e in events]
            with self._state_lock:
                self.state.data["last_events"] = summaries[-20:]
                self.state.save()
            prompt = _classifier_prompt(events, snap)
            input_fingerprint = _classifier_input_fingerprint(events, snap)
            cached_classifier_decision = self._classifier_cache_lookup(input_fingerprint)
            if cached_classifier_decision is not None:
                decision = cached_classifier_decision
                self._metric("classifier_decision_cache_hits")
            else:
                decision = self._codex_classifier(prompt)
            if (
                str(decision.get("decision") or "NOOP").upper() == "NOOP"
                and _restart_signal_events(events)
                and not _restart_noop_has_post_signal_evidence(events, snap)
            ):
                self._metric("restart_noop_rechecks")
                guarded_prompt = (
                    prompt
                    + "\n\nRESTART NOOP GUARD:\n"
                    + "Your previous NOOP conflicts with a trusted RESTART RECOMMENDED signal and "
                    + "the compact snapshot does not prove post-signal producer recovery by immutable "
                    + "head/closure evidence. Re-evaluate once. Choose DISPATCH with the directive's "
                    + "bounded lane/objective/stop boundary, or HUMAN_GATE if a real human/product "
                    + "boundary prevents execution. Do not return NOOP merely because the PR is open, "
                    + "draft, recently commented on, or has unchanged checks."
                )
                decision = self._codex_classifier(guarded_prompt)
                if str(decision.get("decision") or "NOOP").upper() == "NOOP":
                    restart = _restart_signal_events(events)[0]
                    self._metric("restart_noop_escalations")
                    decision = {
                        "decision": "HUMAN_GATE",
                        "lane": None,
                        "pr_number": restart.pr_number,
                        "objective": None,
                        "stop_boundary": None,
                        "reusable_evidence": restart.signal_text,
                        "reason": (
                            "Trusted RESTART RECOMMENDED remained NOOP after guarded reclassification "
                            "without immutable post-signal recovery evidence."
                        ),
                        "human_message": (
                            "Hosted restart classifier could not reconcile the trusted stale-producer "
                            "handoff after one guarded reclassification. Review the target before any "
                            "further autonomous dispatch."
                        ),
                    }
            with self._state_lock:
                self.state.data["last_dispatch_epoch"] = now
                self.state.save()
            self._cache_decision(
                decision,
                events,
                snap,
                classifier_input_fingerprint=input_fingerprint,
            )
            self._checkpoint_classifier_reconcile_observation(snap)
        else:
            self._metric("cached_decision_reuses")

        kind = str(decision.get("decision") or "NOOP").upper()
        lane = decision.get("lane")
        print(f"[orchestrator] classifier decision: {kind} lane={lane} reason={decision.get('reason')}", flush=True)

        if kind == "NOOP":
            self._metric("classifier_noops")
            self._clear_completed_decision()
            return
        if kind == "HUMAN_GATE":
            if not self._post_gate(decision):
                retry = _env_int(
                    "SKYFORGE_ORCHESTRATOR_TRANSIENT_BACKOFF_SECONDS",
                    DEFAULT_TRANSIENT_BACKOFF_SECONDS,
                    minimum=30,
                )
                raise RetryBlocked(
                    "transient",
                    retry,
                    "human-gate visibility handoff failed; durable decision retained",
                )
            self._clear_completed_decision()
            return
        if kind == "MERGE":
            if not isinstance(lane, str):
                raise RuntimeError("MERGE decision missing lane")
            pr_number = decision.get("pr_number")
            managed = self._managed_branch(lane)
            if not managed or int(managed.get("pr_number") or 0) != int(pr_number or 0):
                raise RuntimeError("Refusing MERGE for a PR not owned by local orchestrator state")
            if not self.auto_merge:
                gate = dict(decision)
                gate["decision"] = "HUMAN_GATE"
                gate["human_message"] = (
                    f"Controller-managed PR #{pr_number} is ready for merge, but auto-merge remains "
                    "disabled by policy. Review the machine gates and merge manually if appropriate."
                )
                if not self._post_gate(gate):
                    retry = _env_int(
                        "SKYFORGE_ORCHESTRATOR_TRANSIENT_BACKOFF_SECONDS",
                        DEFAULT_TRANSIENT_BACKOFF_SECONDS,
                        minimum=30,
                    )
                    raise RetryBlocked(
                        "transient",
                        retry,
                        "manual-merge gate visibility failed; durable decision retained",
                    )
                self._metric("manual_merge_gates")
            else:
                self._merge_managed(lane, pr_number)
            self._clear_completed_decision()
            return
        if kind != "DISPATCH":
            raise RuntimeError(f"Unknown classifier decision: {kind}")

        if not isinstance(lane, str) or not lane:
            raise RuntimeError("DISPATCH decision missing lane")
        objective = str(decision.get("objective") or "").strip()
        stop_boundary = str(decision.get("stop_boundary") or "").strip()
        if not objective or not stop_boundary:
            raise RuntimeError("DISPATCH requires objective and stop_boundary")

        worker_tier = str(decision.get("worker_tier") or "TERRA").strip().upper()
        if worker_tier not in {"LUNA", "TERRA"}:
            raise RuntimeError(f"DISPATCH has invalid worker_tier: {worker_tier}")
        raw_allowed = decision.get("allowed_paths")
        allowed_paths = (
            [str(value).strip() for value in raw_allowed if str(value).strip()]
            if isinstance(raw_allowed, list)
            else None
        )
        if worker_tier == "LUNA" and not allowed_paths:
            raise RuntimeError("LUNA DISPATCH requires a non-empty allowed_paths scope")

        branch, managed_pr, worker_root = self._resume_or_prepare_worker(
            lane,
            decision.get("pr_number"),
            objective,
            worker_tier,
            allowed_paths,
        )
        pending_worker = self.state.data.get("pending_worker")
        if isinstance(pending_worker, dict):
            worker_tier = str(pending_worker.get("worker_tier") or worker_tier).upper()
            stored_scope = pending_worker.get("allowed_paths")
            if isinstance(stored_scope, list):
                allowed_paths = [str(value) for value in stored_scope]
        source_pr_paths = self._source_pr_changed_paths(decision.get("pr_number"))
        source_pr_text = (
            "\n".join(f"- {path}" for path in source_pr_paths)
            if source_pr_paths
            else "N/A"
        )
        scope_text = (
            "\n".join(f"- {path}" for path in allowed_paths)
            if allowed_paths
            else "No additional allowlist; ordinary protected-path rules still apply."
        )
        worker_prompt = f"""Bounded objective:
{objective}

Acceptance / stop boundary:
{stop_boundary}

Existing portable evidence:
{decision.get("reusable_evidence") or "N/A"}

Source PR or issue context:
{decision.get("pr_number") or "N/A"}

Files already changed by that source PR (durable existing work; do not recreate merely to copy it):
{source_pr_text}

Allowed edit scope for this worker:
{scope_text}

Worker tier:
{worker_tier}

The outer controller has selected branch {branch} in isolated worker worktree {worker_root}. This may
be a resumed interrupted worker branch; inspect and preserve any partial work already present before
changing it. The controller checkout remains separate and must not be modified. If a prior producer PR is
relevant, its origin ref may be available locally for comparison, but do not blindly merge stale history.

Work only until the stop boundary. Persist the bounded result as local file changes and tests. Do not
commit, push, open/merge PRs, or use network access."""
        if isinstance(pending_worker, dict) and pending_worker.get("stage") == "handoff":
            worker_summary = str(pending_worker.get("worker_summary") or "Interrupted worker completed.")
            self._metric("handoff_resumes")
        else:
            worker_summary = self._worker(worker_prompt, worker_tier, worker_root)
            self._mark_worker_handoff(worker_summary)

        handoff_created = self._handoff_changes(
            lane,
            objective,
            branch,
            managed_pr,
            worker_summary,
            allowed_paths,
            worker_root,
        )
        # Clear durable event/worker state before retiring the linked worktree. If the process
        # crashes during handoff, the retained worktree remains available for idempotent replay.
        self._clear_completed_decision()
        if not handoff_created:
            self._schedule_no_change_followup(events)
        try:
            self._retire_worker_worktree(worker_root)
        except Exception as exc:
            self._metric("worker_worktree_cleanup_failures")
            print(
                f"[orchestrator] worker worktree cleanup warning: {type(exc).__name__}: {exc}",
                flush=True,
            )


class Handler(BaseHTTPRequestHandler):
    orchestrator: Orchestrator

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
        self._respond_json(200, self.orchestrator.health_snapshot())

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/webhook":
            self.send_error(404)
            return
        try:
            size = int(self.headers.get("Content-Length") or "0")
            if size <= 0 or size > 5_000_000:
                self._respond_json(400, {"error": "invalid payload size"})
                return

            raw = self.rfile.read(size)
            if self.orchestrator.require_webhook_secret:
                signature = self.headers.get("X-Hub-Signature-256")
                if not verify_webhook_signature(
                    self.orchestrator.webhook_secret,
                    raw,
                    signature,
                ):
                    self.orchestrator._metric("webhook_signature_rejections")
                    self._respond_json(403, {"error": "invalid webhook signature"})
                    return

            delivery_id = self.headers.get("X-GitHub-Delivery")
            if self.orchestrator.delivery_seen(delivery_id):
                self.orchestrator._metric("duplicate_deliveries")
                self._respond_json(200, {"accepted": False, "duplicate": True})
                return

            payload = json.loads(raw.decode("utf-8"))
            repository = payload.get("repository") or {}
            full_name = repository.get("full_name")
            if full_name and full_name != self.orchestrator.repo:
                self._respond_json(400, {"error": "repository mismatch"})
                return

            event = self.headers.get("X-GitHub-Event") or ""
            control = classify_control_command(
                event,
                payload,
                trusted_actors=self.orchestrator.trusted_actors,
            )
            if control:
                self.orchestrator._apply_control_payload(control, payload)
                comment = payload.get("comment") or {}
                source_id = str(comment.get("id")) if comment.get("id") is not None else None
                self.orchestrator.record_issue_comment(source_id)
                self.orchestrator.record_delivery(delivery_id)
                self._respond_json(
                    202,
                    {"accepted": False, "control": control, "paused": self.orchestrator.is_paused()},
                )
                return

            decision = classify_event(
                event,
                payload,
                repo=self.orchestrator.repo,
                trusted_actors=self.orchestrator.trusted_actors,
            )
            self.orchestrator.enqueue(decision)
            # Record only after enqueue has durably journaled any actionable event.
            if (event or "").strip().lower() == "issue_comment":
                comment = payload.get("comment") or {}
                source_id = str(comment.get("id")) if comment.get("id") is not None else None
                self.orchestrator.record_issue_comment(source_id)
            self.orchestrator.record_delivery(delivery_id)
            self._respond_json(
                202,
                {"accepted": decision.actionable, "reason": decision.reason},
            )
        except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as exc:
            self._respond_json(400, {"error": str(exc)})
        except Exception as exc:
            print(f"[webhook] handler error: {type(exc).__name__}: {exc}", flush=True)
            self._respond_json(500, {"error": "internal webhook error"})

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"[webhook] {fmt % args}", flush=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--repo", default=REPO)
    parser.add_argument(
        "--bind",
        default=os.environ.get("SKYFORGE_ORCHESTRATOR_BIND", "127.0.0.1"),
    )
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--debounce-seconds", type=int, default=DEFAULT_DEBOUNCE_SECONDS)
    parser.add_argument("--min-dispatch-seconds", type=int, default=DEFAULT_MIN_DISPATCH_SECONDS)
    parser.add_argument("--max-parent-turns", type=int, default=DEFAULT_MAX_PARENT_TURNS)
    parser.add_argument(
        "--auto-merge",
        action="store_true",
        default=os.environ.get("SKYFORGE_ORCHESTRATOR_AUTO_MERGE") == "1",
        help="Allow controller-managed PRs to merge after classifier + green checks. Off by default.",
    )
    parser.add_argument(
        "--require-webhook-secret",
        action="store_true",
        default=os.environ.get("SKYFORGE_REQUIRE_WEBHOOK_SECRET") == "1",
        help="Reject webhook deliveries without a valid GitHub HMAC-SHA256 signature.",
    )
    parser.add_argument(
        "--startup-reconcile",
        action="store_true",
        default=os.environ.get("SKYFORGE_STARTUP_RECONCILE") == "1",
        help="Compare current GitHub state with the prior startup baseline and synthesize one wake if changed.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    orchestrator = Orchestrator(
        root,
        repo=args.repo,
        debounce_seconds=args.debounce_seconds,
        min_dispatch_seconds=args.min_dispatch_seconds,
        max_parent_turns=args.max_parent_turns,
        auto_merge=args.auto_merge,
        require_webhook_secret=args.require_webhook_secret,
        startup_reconcile=args.startup_reconcile,
    )
    orchestrator.validate_environment()
    orchestrator._metric("controller_starts")
    Handler.orchestrator = orchestrator
    server = ThreadingHTTPServer((args.bind, args.port), Handler)
    print(
        f"[orchestrator] listening on http://{args.bind}:{args.port}/webhook "
        f"for {args.repo}; auto_merge={args.auto_merge}; "
        f"signed_webhooks={args.require_webhook_secret}; startup_reconcile={args.startup_reconcile}",
        flush=True,
    )
    if orchestrator.startup_reconcile:
        orchestrator.attempt_startup_reconcile()
    orchestrator.resume_pending()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
