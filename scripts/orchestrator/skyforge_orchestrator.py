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
import json
import os
import re
import subprocess
import threading
import time
from dataclasses import asdict, dataclass
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
DEFAULT_MAX_CLASSIFIER_CALLS_PER_DAY = 48
DEFAULT_MAX_WORKER_CALLS_PER_DAY = 8
DEFAULT_QUOTA_BACKOFF_SECONDS = 3600
DEFAULT_RATE_LIMIT_BACKOFF_SECONDS = 300
DEFAULT_TRANSIENT_BACKOFF_SECONDS = 300

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

Do not dispatch work merely because a lane exists. Do not poll CI. Do not expand expensive validation
without a distinct risk. Honor VALIDATION_POLICY.md and ORCHESTRATION_PROTOCOL.md.

Return ONLY one JSON object:
{
  "decision": "NOOP" | "DISPATCH" | "HUMAN_GATE" | "MERGE",
  "lane": "Implementation" | "Authorship" | "Content" | "Music" | "Presentation" | "Audit" | null,
  "pr_number": integer | null,
  "objective": string | null,
  "stop_boundary": string | null,
  "reusable_evidence": string | null,
  "reason": string,
  "human_message": string | null
}

MERGE may be selected only for a controller-managed PR whose required machine evidence is already
green and whose repository policy has no remaining human/product gate.
"""

WORKER_INSTRUCTIONS = """You are a bounded Skyforge repository worker.

Read AGENTS.md, PROGRAM_CHARTER.md, VALIDATION_POLICY.md, the relevant lane state,
CROSS_LANE_CONTRACTS.md, and only the source/tests/history needed for the objective.

You have local filesystem access in a dedicated clone but NO GitHub/network authority. Do not try to
push, open PRs, merge, modify secrets, or request credentials. The outer controller owns all git/gh
network writes.

Work only on the supplied objective. Do not expand into unrelated cleanup. Do not cross a human or
product-strategy gate. Do not repeat expensive evidence unless the prompt identifies the distinct
uncertainty it retires. Reuse portable evidence under VALIDATION_POLICY.md.

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

    def summary(self) -> str:
        bits = [self.event]
        if self.action:
            bits.append(self.action)
        if self.pr_number:
            bits.append(f"PR#{self.pr_number}")
        if self.head_sha:
            bits.append(self.head_sha[:10])
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
        )


def _event_key(value: EventDecision | dict[str, Any]) -> str:
    payload = value.to_state() if isinstance(value, EventDecision) else value
    return json.dumps(payload, sort_keys=True, separators=(",", ":"))


def classify_event(event: str, payload: dict[str, Any]) -> EventDecision:
    """Cheap deterministic gate. Irrelevant events never reach Codex."""
    event = (event or "").strip().lower()

    if event == "ping":
        return EventDecision(False, "webhook ping", event)

    if event == "push":
        ref = str(payload.get("ref") or "")
        if ref == "refs/heads/main":
            return EventDecision(True, "main advanced", event, head_sha=payload.get("after"))
        return EventDecision(False, "non-main push; PR/workflow events cover producer branches", event)

    if event == "pull_request":
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
        body = str((payload.get("comment") or {}).get("body") or "")
        body_lower = body.lower()
        if SELF_COMMENT_MARKER in body_lower:
            return EventDecision(False, "controller-authored comment; prevent wake loop", event, action)
        if any(token in body_lower for token in AUDIT_WAKE_TOKENS):
            issue = payload.get("issue") or {}
            return EventDecision(
                True,
                "Audit/manual orchestration comment",
                event,
                action,
                pr_number=issue.get("number"),
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
        self.dir.mkdir(parents=True, exist_ok=True)
        self.data: dict[str, Any] = {
            "parent_thread_id": None,
            "parent_turns": 0,
            "last_dispatch_epoch": 0.0,
            "managed": {},
            "last_events": [],
            "pending_events": [],
            "pending_decision": None,
            "pending_worker": None,
            "blocked_until_epoch": 0.0,
            "blocked_kind": None,
            "blocked_reason": None,
            "metrics": {},
            "budget_day": _utc_day(),
            "classifier_calls_today": 0,
            "worker_calls_today": 0,
        }
        if self.path.exists():
            try:
                loaded = json.loads(self.path.read_text())
                if isinstance(loaded, dict):
                    self.data.update(loaded)
            except Exception:
                # A corrupt local state file must not damage the repository.
                pass

    def save(self) -> None:
        tmp = self.path.with_suffix(".tmp")
        tmp.write_text(json.dumps(self.data, indent=2, sort_keys=True) + "\n")
        tmp.replace(self.path)


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
    ) -> None:
        self.root = root
        self.repo = repo
        self.debounce_seconds = debounce_seconds
        self.min_dispatch_seconds = min_dispatch_seconds
        self.max_parent_turns = max_parent_turns
        self.auto_merge = auto_merge
        self.state = LocalState(root)
        self._state_lock = threading.RLock()
        self._timer_lock = threading.Lock()
        self._timer: threading.Timer | None = None
        self._dispatch_lock = threading.Lock()

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
            self.state.data["worker_calls_today"] = 0

    def _consume_budget(self, kind: str) -> None:
        with self._state_lock:
            self._reset_daily_budget_if_needed()
            if kind == "classifier":
                key = "classifier_calls_today"
                limit = _env_int(
                    "SKYFORGE_ORCHESTRATOR_MAX_CLASSIFIER_CALLS_PER_DAY",
                    DEFAULT_MAX_CLASSIFIER_CALLS_PER_DAY,
                    minimum=1,
                )
            elif kind == "worker":
                key = "worker_calls_today"
                limit = _env_int(
                    "SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY",
                    DEFAULT_MAX_WORKER_CALLS_PER_DAY,
                    minimum=1,
                )
            else:
                raise ValueError(f"unknown budget kind: {kind}")
            used = int(self.state.data.get(key) or 0)
            if used >= limit:
                raise RetryBlocked(
                    "local_budget",
                    _seconds_until_next_utc_day(),
                    f"local {kind} daily call budget exhausted ({used}/{limit})",
                )
            self.state.data[key] = used + 1
            metrics = self.state.data.setdefault("metrics", {})
            metric_key = f"{kind}_attempts"
            metrics[metric_key] = int(metrics.get(metric_key) or 0) + 1
            self.state.save()

    def _pending_events(self) -> list[EventDecision]:
        with self._state_lock:
            values = self.state.data.get("pending_events") or []
            return [EventDecision.from_state(v) for v in values if isinstance(v, dict)]

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
            self.state.data["pending_events"] = list(by_key.values())[-100:]
            if added and not self.state.data.get("pending_worker"):
                self.state.data["pending_decision"] = None
            self.state.save()

    def _decision_record(self) -> dict[str, Any] | None:
        value = self.state.data.get("pending_decision")
        return value if isinstance(value, dict) else None

    def _cache_decision(self, decision: dict[str, Any], events: list[EventDecision]) -> None:
        with self._state_lock:
            self.state.data["pending_decision"] = {
                "decision": decision,
                "event_keys": [_event_key(e) for e in events],
                "captured_at": _utc_now(),
            }
            self.state.save()

    def _clear_completed_decision(self) -> None:
        with self._state_lock:
            record = self._decision_record() or {}
            completed = set(record.get("event_keys") or [])
            pending = [v for v in (self.state.data.get("pending_events") or []) if isinstance(v, dict)]
            if completed:
                pending = [v for v in pending if _event_key(v) not in completed]
            else:
                pending = []
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
        with self._state_lock:
            if self.state.data.get("pending_decision") and not self.state.data.get("pending_worker"):
                self.state.data["pending_decision"] = None
                self.state.save()
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
        self._metric("events_actionable")
        self._persist_pending_events([event])
        print(f"[orchestrator] journaled: {event.summary()}", flush=True)
        remaining = self._blocked_remaining()
        self._schedule_pending(remaining if remaining else self.debounce_seconds)

    def _drain_and_dispatch(self) -> None:
        pending = self._pending_events()
        if not pending:
            return
        with self._dispatch_lock:
            try:
                self.dispatch(pending)
            except RetryBlocked as exc:
                print(
                    f"[orchestrator] {exc.kind} block: {exc}; retry in {exc.retry_after_seconds}s",
                    flush=True,
                )
                self._set_retry_block(exc.kind, exc.retry_after_seconds, str(exc))
            except Exception as exc:
                self._metric("dispatch_failures")
                delay = _env_int(
                    "SKYFORGE_ORCHESTRATOR_TRANSIENT_BACKOFF_SECONDS",
                    DEFAULT_TRANSIENT_BACKOFF_SECONDS,
                    minimum=30,
                )
                print(f"[orchestrator] dispatch error: {type(exc).__name__}: {exc}", flush=True)
                self._set_retry_block("controller_error", delay, f"{type(exc).__name__}: {exc}")

    def _worktree_clean(self) -> bool:
        out = _run(["git", "status", "--porcelain"], cwd=self.root).stdout.strip()
        return not out

    def _current_branch(self) -> str:
        return _run(["git", "branch", "--show-current"], cwd=self.root).stdout.strip()

    def sync_main(self) -> None:
        if not self._worktree_clean():
            raise RuntimeError("Dedicated clone is dirty; refusing autonomous checkout/sync")
        _run(["git", "fetch", "--prune", "origin"], cwd=self.root, timeout=180)
        _run(["git", "checkout", "main"], cwd=self.root)
        _run(["git", "pull", "--ff-only", "origin", "main"], cwd=self.root, timeout=180)

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
                "number,title,isDraft,headRefName,baseRefName,updatedAt,url,author,mergeStateStatus",
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
                self.state.data["parent_thread_id"] = thread.id
                self.state.data["parent_turns"] = turns + 1
                self.state.save()
                return _clean_json_object(result.final_response)
        except RetryBlocked:
            raise
        except Exception as exc:
            kind, retry = _codex_failure_policy(exc)
            raise RetryBlocked(kind, retry, f"classifier call failed: {exc}") from exc

    def _worker(self, prompt: str) -> str:
        self._consume_budget("worker")
        try:
            from openai_codex import Codex, Sandbox

            model = os.environ.get("SKYFORGE_WORKER_MODEL", "gpt-5.6-terra")
            effort = os.environ.get("SKYFORGE_WORKER_REASONING", "medium")
            with Codex() as codex:
                thread = codex.thread_start(
                    cwd=str(self.root),
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
            raise RetryBlocked(kind, retry, f"worker call failed: {exc}") from exc

    def _managed_branch(self, lane: str) -> dict[str, Any] | None:
        value = (self.state.data.get("managed") or {}).get(lane)
        return value if isinstance(value, dict) else None

    def _prepare_worker_branch(self, lane: str, source_pr: int | None) -> tuple[str, int | None]:
        managed = self._managed_branch(lane)
        if managed and managed.get("branch"):
            branch = str(managed["branch"])
            _run(["git", "fetch", "origin", branch], cwd=self.root, timeout=120)
            _run(["git", "checkout", "-B", branch, f"origin/{branch}"], cwd=self.root)
            return branch, managed.get("pr_number")

        stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
        slug = re.sub(r"[^a-z0-9]+", "-", lane.lower()).strip("-") or "lane"
        branch = f"codex/{slug}-{stamp}"
        _run(["git", "checkout", "-b", branch, "origin/main"], cwd=self.root)

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
        return branch, None

    def _resume_or_prepare_worker(
        self, lane: str, source_pr: int | None, objective: str
    ) -> tuple[str, int | None]:
        pending = self.state.data.get("pending_worker")
        if isinstance(pending, dict) and pending.get("branch"):
            branch = str(pending["branch"])
            managed_pr = pending.get("managed_pr")
            current = self._current_branch()
            if current != branch:
                if not self._worktree_clean():
                    raise RuntimeError(
                        f"Interrupted worker has dirty worktree on {current!r}; expected {branch!r}. "
                        "Manual inspection required before autonomous recovery."
                    )
                local_exists = _run(
                    ["git", "show-ref", "--verify", f"refs/heads/{branch}"],
                    cwd=self.root,
                    check=False,
                ).returncode == 0
                if local_exists:
                    _run(["git", "checkout", branch], cwd=self.root)
                else:
                    _run(["git", "fetch", "origin", branch], cwd=self.root, timeout=120)
                    _run(["git", "checkout", "-B", branch, f"origin/{branch}"], cwd=self.root)
            self._metric("worker_resumes")
            return branch, managed_pr

        branch, managed_pr = self._prepare_worker_branch(lane, source_pr)
        with self._state_lock:
            self.state.data["pending_worker"] = {
                "lane": lane,
                "branch": branch,
                "managed_pr": managed_pr,
                "objective": objective,
                "started_at": _utc_now(),
            }
            self.state.save()
        return branch, managed_pr

    def _changed_paths(self) -> list[str]:
        output = _run(["git", "status", "--porcelain"], cwd=self.root).stdout.splitlines()
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
    ) -> None:
        paths = self._changed_paths()
        if not paths:
            self._metric("worker_no_change")
            print(f"[orchestrator] worker made no repository changes for {lane}", flush=True)
            return

        forbidden = [p for p in paths if p.startswith(f"{STATE_DIR}/") or p.startswith(".git/")]
        if forbidden:
            raise RuntimeError(f"Worker touched controller/private paths: {forbidden}")

        _run(["git", "diff", "--check"], cwd=self.root)
        _run(["git", "add", "--all"], cwd=self.root)
        short = re.sub(r"\s+", " ", objective).strip()[:72]
        _run(["git", "commit", "-m", f"CODEX {lane}: {short}"], cwd=self.root, timeout=120)
        # Deliberately no force push.
        _run(["git", "push", "-u", "origin", branch], cwd=self.root, timeout=180)

        if managed_pr:
            pr_number = int(managed_pr)
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

        managed_map = self.state.data.setdefault("managed", {})
        managed_map[lane] = {
            "branch": branch,
            "pr_number": pr_number,
            "updated_at": _utc_now(),
        }
        self.state.save()
        self._metric("worker_handoffs")
        print(f"[orchestrator] handed off {lane} on {branch} / PR #{pr_number}", flush=True)

    def _post_gate(self, decision: dict[str, Any]) -> None:
        message = str(decision.get("human_message") or decision.get("reason") or "Human gate reached")
        pr = decision.get("pr_number")
        target = str(pr) if isinstance(pr, int) else "349"
        body = f"{SELF_COMMENT_MARKER} HUMAN_GATE\n\n{message[:5000]}"
        try:
            _run(
                ["gh", "issue", "comment", target, "--repo", self.repo, "--body", body],
                cwd=self.root,
                timeout=60,
            )
            self._metric("human_gates")
        except Exception as exc:
            print(f"[orchestrator] could not post human gate: {exc}", flush=True)

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

        if not (record and isinstance(pending_worker, dict)):
            heads = {e.head_sha for e in events if e.event == "workflow_run" and e.head_sha}
            for head in heads:
                if not self.workflows_quiescent(head):
                    self._schedule_pending(max(30, self.debounce_seconds))
                    return
            self.sync_main()

        if record:
            decision = record.get("decision")
            if not isinstance(decision, dict):
                decision = None
        else:
            decision = None

        if decision is None:
            snap = self.snapshot()
            summaries = [e.summary() for e in events]
            self.state.data["last_events"] = summaries[-20:]
            self.state.save()
            prompt = (
                "A filtered Skyforge repository event batch is actionable.\n\n"
                "EVENTS:\n- " + "\n- ".join(summaries) + "\n\n"
                "COMPACT REPOSITORY SNAPSHOT:\n" + json.dumps(snap, indent=2)[:24000] + "\n\n"
                "Read AGENTS.md and the compact Audit state as needed. Return only the required JSON decision."
            )
            decision = self._codex_classifier(prompt)
            self.state.data["last_dispatch_epoch"] = now
            self.state.save()
            self._cache_decision(decision, events)
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
            self._post_gate(decision)
            self._clear_completed_decision()
            return
        if kind == "MERGE":
            if not isinstance(lane, str):
                raise RuntimeError("MERGE decision missing lane")
            self._merge_managed(lane, decision.get("pr_number"))
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

        branch, managed_pr = self._resume_or_prepare_worker(lane, decision.get("pr_number"), objective)
        worker_prompt = f"""Bounded objective:
{objective}

Acceptance / stop boundary:
{stop_boundary}

Existing portable evidence:
{decision.get("reusable_evidence") or "N/A"}

Source PR or issue context:
{decision.get("pr_number") or "N/A"}

The outer controller has selected branch {branch}. This may be a resumed interrupted worker branch;
inspect and preserve any partial work already present before changing it. If a prior producer PR is
relevant, its origin ref may be available locally for comparison, but do not blindly merge stale history.

Work only until the stop boundary. Persist the bounded result as local file changes and tests. Do not
commit, push, open/merge PRs, or use network access."""
        worker_summary = self._worker(worker_prompt)
        self._handoff_changes(lane, objective, branch, managed_pr, worker_summary)
        self._clear_completed_decision()


class Handler(BaseHTTPRequestHandler):
    orchestrator: Orchestrator

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/webhook":
            self.send_error(404)
            return
        try:
            size = int(self.headers.get("Content-Length") or "0")
            if size <= 0 or size > 5_000_000:
                self.send_error(400, "invalid payload size")
                return
            payload = json.loads(self.rfile.read(size))
            event = self.headers.get("X-GitHub-Event") or ""
            decision = classify_event(event, payload)
            self.orchestrator.enqueue(decision)
            body = json.dumps({"accepted": decision.actionable, "reason": decision.reason}).encode()
            self.send_response(202)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except Exception as exc:
            self.send_error(400, str(exc))

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"[webhook] {fmt % args}", flush=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--repo", default=REPO)
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
    )
    orchestrator.validate_environment()
    Handler.orchestrator = orchestrator
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(
        f"[orchestrator] listening on http://127.0.0.1:{args.port}/webhook "
        f"for {args.repo}; auto_merge={args.auto_merge}",
        flush=True,
    )
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
