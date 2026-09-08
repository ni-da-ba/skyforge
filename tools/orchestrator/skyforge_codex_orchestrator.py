#!/usr/bin/env python3
"""Event-driven Skyforge Codex App Server orchestrator pilot.

The controller is deliberately small and standard-library only. GitHub webhooks are
filtered before Codex is invoked so idle/non-actionable events consume no agentic
usage. A single persistent Codex thread is resumed for actionable events.

The App Server protocol is JSONL over stdio. This client uses the stable primitives
documented by OpenAI: initialize, thread/start or thread/resume, turn/start, and
turn/completed notifications. Keep the Codex CLI/App Server version pinned while
piloting this controller.
"""

from __future__ import annotations

import argparse
import hashlib
import hmac
import http.server
import json
import os
import queue
import signal
import subprocess
import sys
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Optional

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 8765
DEFAULT_DEBOUNCE_SECONDS = 20.0
STATE_PATH = Path(".skyforge-orchestrator-state.json")

ACTIONABLE_PR_ACTIONS = {
    "opened",
    "reopened",
    "ready_for_review",
    "synchronize",
    "closed",
}
ACTIONABLE_WORKFLOW_ACTIONS = {"completed"}
ACTIONABLE_ISSUE_COMMENT_ACTIONS = {"created"}


@dataclass(frozen=True)
class DispatchEvent:
    delivery_id: str
    event_name: str
    action: str
    repository: str
    summary: str
    payload: Dict[str, Any]


class DeliveryDeduper:
    def __init__(self, ttl_seconds: float = 3600.0) -> None:
        self._ttl_seconds = ttl_seconds
        self._seen: Dict[str, float] = {}
        self._lock = threading.Lock()

    def first_seen(self, delivery_id: str) -> bool:
        now = time.monotonic()
        with self._lock:
            expired = [key for key, stamp in self._seen.items() if now - stamp > self._ttl_seconds]
            for key in expired:
                self._seen.pop(key, None)
            if delivery_id in self._seen:
                return False
            self._seen[delivery_id] = now
            return True


def verify_github_signature(secret: bytes, body: bytes, signature_header: str) -> bool:
    if not secret:
        return False
    if not signature_header.startswith("sha256="):
        return False
    expected = "sha256=" + hmac.new(secret, body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature_header)


def _repo_name(payload: Dict[str, Any]) -> str:
    repository = payload.get("repository") or {}
    return str(repository.get("full_name") or "")


def classify_webhook(event_name: str, payload: Dict[str, Any], delivery_id: str) -> Optional[DispatchEvent]:
    """Return a compact actionable event or None for noise.

    The model is intentionally not called here. This is the primary usage-saving gate.
    """
    repository = _repo_name(payload)
    if repository != "ni-da-ba/skyforge":
        return None

    action = str(payload.get("action") or "")

    if event_name == "pull_request" and action in ACTIONABLE_PR_ACTIONS:
        pr = payload.get("pull_request") or {}
        number = pr.get("number") or payload.get("number")
        title = pr.get("title") or ""
        head = ((pr.get("head") or {}).get("sha") or "")[:12]
        merged = bool(pr.get("merged"))
        return DispatchEvent(
            delivery_id,
            event_name,
            action,
            repository,
            f"PR #{number} {action}: {title} head={head} merged={merged}",
            payload,
        )

    if event_name == "workflow_run" and action in ACTIONABLE_WORKFLOW_ACTIONS:
        run = payload.get("workflow_run") or {}
        conclusion = run.get("conclusion")
        # Completed workflow evidence is actionable; queued/in-progress noise is not.
        return DispatchEvent(
            delivery_id,
            event_name,
            action,
            repository,
            f"Workflow completed: {run.get('name')} conclusion={conclusion} head={(run.get('head_sha') or '')[:12]}",
            payload,
        )

    if event_name == "push":
        ref = str(payload.get("ref") or "")
        if ref != "refs/heads/main":
            return None
        after = str(payload.get("after") or "")[:12]
        commits = payload.get("commits") or []
        return DispatchEvent(
            delivery_id,
            event_name,
            "push",
            repository,
            f"main advanced to {after} with {len(commits)} commit(s)",
            payload,
        )

    if event_name == "issue_comment" and action in ACTIONABLE_ISSUE_COMMENT_ACTIONS:
        issue = payload.get("issue") or {}
        # Only PR comments are orchestration-relevant by default.
        if "pull_request" not in issue:
            return None
        comment = payload.get("comment") or {}
        actor = (comment.get("user") or {}).get("login") or ""
        body = str(comment.get("body") or "")
        # Avoid waking Codex for comments emitted by obvious automation accounts.
        if actor.endswith("[bot]"):
            return None
        return DispatchEvent(
            delivery_id,
            event_name,
            action,
            repository,
            f"PR #{issue.get('number')} comment by {actor}: {body[:240]}",
            payload,
        )

    return None


def build_orchestrator_prompt(events: list[DispatchEvent]) -> str:
    event_lines = "\n".join(f"- {event.summary}" for event in events)
    return f"""Skyforge repository events have occurred.\n\n{event_lines}\n\nFollow AGENTS.md and docs/agent-state/ORCHESTRATION_PROTOCOL.md.\n\nThis is an event-driven orchestrator turn, not permission to manufacture work. Inspect only the current repository state needed to determine whether these events made a lane actionable. Treat a healthy ordinary ChatGPT/manual producer with information-bearing GitHub or Actions movement as RUNNING_EXTERNAL and do not race it.\n\nIf no lane is actionable, end promptly. If one lane is RUN, execute or dispatch at most one bounded worker objective during the pilot. Do not poll CI. Stop at human/product/permission gates. Persist useful repository progress before ending.\n\nAt the end report only: lane classification, action taken or waiting condition, repository evidence created, and any human gate."""


class AppServerClient:
    """Minimal Codex App Server JSONL client for one persistent orchestrator thread."""

    def __init__(self, codex_bin: str, cwd: Path, model: str, state_path: Path = STATE_PATH) -> None:
        self._codex_bin = codex_bin
        self._cwd = cwd
        self._model = model
        self._state_path = state_path
        self._process: Optional[subprocess.Popen[str]] = None
        self._responses: "queue.Queue[dict[str, Any]]" = queue.Queue()
        self._reader: Optional[threading.Thread] = None
        self._next_id = 1
        self._thread_id = self._load_thread_id()

    def _load_thread_id(self) -> Optional[str]:
        try:
            data = json.loads(self._state_path.read_text(encoding="utf-8"))
            value = data.get("thread_id")
            return str(value) if value else None
        except (FileNotFoundError, json.JSONDecodeError, OSError):
            return None

    def _save_thread_id(self, thread_id: str) -> None:
        self._state_path.write_text(json.dumps({"thread_id": thread_id}, indent=2) + "\n", encoding="utf-8")

    def start(self) -> None:
        if self._process is not None:
            return
        self._process = subprocess.Popen(
            [self._codex_bin, "app-server"],
            cwd=self._cwd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=sys.stderr,
            text=True,
            bufsize=1,
        )
        assert self._process.stdout is not None
        self._reader = threading.Thread(target=self._read_loop, name="codex-app-server-reader", daemon=True)
        self._reader.start()
        self._request(
            "initialize",
            {"clientInfo": {"name": "skyforge_orchestrator", "title": "Skyforge Orchestrator", "version": "0.1.0"}},
            timeout=30.0,
        )
        self._ensure_thread()

    def close(self) -> None:
        if self._process is None:
            return
        self._process.terminate()
        try:
            self._process.wait(timeout=5.0)
        except subprocess.TimeoutExpired:
            self._process.kill()
        self._process = None

    def _read_loop(self) -> None:
        assert self._process is not None and self._process.stdout is not None
        for line in self._process.stdout:
            line = line.strip()
            if not line:
                continue
            try:
                self._responses.put(json.loads(line))
            except json.JSONDecodeError:
                print(f"[orchestrator] non-JSON app-server stdout: {line}", file=sys.stderr)

    def _send(self, message: Dict[str, Any]) -> None:
        assert self._process is not None and self._process.stdin is not None
        self._process.stdin.write(json.dumps(message, separators=(",", ":")) + "\n")
        self._process.stdin.flush()

    def _request(self, method: str, params: Dict[str, Any], timeout: float) -> Dict[str, Any]:
        request_id = self._next_id
        self._next_id += 1
        self._send({"method": method, "id": request_id, "params": params})
        deadline = time.monotonic() + timeout
        deferred: list[dict[str, Any]] = []
        try:
            while time.monotonic() < deadline:
                remaining = max(0.01, deadline - time.monotonic())
                message = self._responses.get(timeout=remaining)
                if message.get("id") == request_id:
                    if "error" in message:
                        raise RuntimeError(f"Codex App Server {method} failed: {message['error']}")
                    return message.get("result") or {}
                deferred.append(message)
        finally:
            for message in deferred:
                self._responses.put(message)
        raise TimeoutError(f"Timed out waiting for App Server response to {method}")

    @staticmethod
    def _extract_thread_id(result: Dict[str, Any]) -> Optional[str]:
        candidates = [
            result.get("threadId"),
            result.get("thread_id"),
            (result.get("thread") or {}).get("id") if isinstance(result.get("thread"), dict) else None,
        ]
        for value in candidates:
            if value:
                return str(value)
        return None

    def _ensure_thread(self) -> None:
        if self._thread_id:
            try:
                self._request("thread/resume", {"threadId": self._thread_id}, timeout=30.0)
                return
            except Exception as exc:  # stale/incompatible local state: make a fresh durable thread
                print(f"[orchestrator] unable to resume thread {self._thread_id}: {exc}; starting fresh", file=sys.stderr)
                self._thread_id = None

        result = self._request(
            "thread/start",
            {
                "cwd": str(self._cwd),
                "model": self._model,
            },
            timeout=30.0,
        )
        thread_id = self._extract_thread_id(result)
        if not thread_id:
            raise RuntimeError(f"thread/start returned no thread id: {result}")
        self._thread_id = thread_id
        self._save_thread_id(thread_id)

    def run_turn(self, prompt: str, timeout: float = 3600.0) -> None:
        if self._process is None:
            self.start()
        assert self._thread_id
        result = self._request(
            "turn/start",
            {
                "threadId": self._thread_id,
                "input": [{"type": "text", "text": prompt}],
            },
            timeout=30.0,
        )
        turn_id = result.get("turnId") or result.get("turn_id") or ((result.get("turn") or {}).get("id") if isinstance(result.get("turn"), dict) else None)
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            remaining = max(0.01, deadline - time.monotonic())
            message = self._responses.get(timeout=remaining)
            method = str(message.get("method") or "")
            params = message.get("params") or {}
            if method == "turn/completed":
                observed = params.get("turnId") or params.get("turn_id") or ((params.get("turn") or {}).get("id") if isinstance(params.get("turn"), dict) else None)
                if not turn_id or not observed or str(observed) == str(turn_id):
                    return
            if method in {"turn/failed", "turn/cancelled", "turn/ended_with_error"}:
                raise RuntimeError(f"Codex turn ended unsuccessfully: {message}")
            if message.get("method") and "approval" in method.lower():
                raise RuntimeError("Codex requested approval; stop controller and handle the gate manually")
        raise TimeoutError("Timed out waiting for Codex turn/completed")


class EventBatcher(threading.Thread):
    def __init__(self, app: Optional[AppServerClient], debounce_seconds: float, dry_run: bool) -> None:
        super().__init__(name="skyforge-event-batcher", daemon=True)
        self._app = app
        self._debounce_seconds = debounce_seconds
        self._dry_run = dry_run
        self._queue: "queue.Queue[DispatchEvent]" = queue.Queue()
        self._stop = threading.Event()

    def submit(self, event: DispatchEvent) -> None:
        self._queue.put(event)

    def close(self) -> None:
        self._stop.set()
        self._queue.put(DispatchEvent("shutdown", "shutdown", "shutdown", "", "shutdown", {}))

    def run(self) -> None:
        while not self._stop.is_set():
            first = self._queue.get()
            if first.delivery_id == "shutdown":
                return
            events = [first]
            deadline = time.monotonic() + self._debounce_seconds
            while time.monotonic() < deadline:
                try:
                    events.append(self._queue.get(timeout=max(0.01, deadline - time.monotonic())))
                except queue.Empty:
                    break
            prompt = build_orchestrator_prompt(events)
            if self._dry_run:
                print("\n=== ACTIONABLE SKYFORGE EVENT BATCH ===")
                print(prompt)
                print("=== END BATCH ===\n")
                continue
            assert self._app is not None
            try:
                self._app.run_turn(prompt)
            except Exception as exc:
                # Do not silently retry and burn usage. The independent Audit watchdog is the fallback.
                print(f"[orchestrator] Codex turn failed: {exc}", file=sys.stderr)


class WebhookHandler(http.server.BaseHTTPRequestHandler):
    server_version = "SkyforgeOrchestrator/0.1"

    def do_POST(self) -> None:  # noqa: N802
        controller = self.server.controller  # type: ignore[attr-defined]
        if self.path != "/github-webhook":
            self.send_error(404)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self.send_error(400, "invalid content length")
            return
        body = self.rfile.read(length)
        signature = self.headers.get("X-Hub-Signature-256", "")
        if not verify_github_signature(controller.secret, body, signature):
            self.send_error(401, "invalid signature")
            return
        delivery_id = self.headers.get("X-GitHub-Delivery", "")
        if not delivery_id or not controller.deduper.first_seen(delivery_id):
            self.send_response(202)
            self.end_headers()
            return
        event_name = self.headers.get("X-GitHub-Event", "")
        try:
            payload = json.loads(body)
        except json.JSONDecodeError:
            self.send_error(400, "invalid JSON")
            return
        event = classify_webhook(event_name, payload, delivery_id)
        if event is not None:
            controller.batcher.submit(event)
        self.send_response(202)
        self.end_headers()

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"[webhook] {self.address_string()} - {fmt % args}", file=sys.stderr)


@dataclass
class Controller:
    secret: bytes
    deduper: DeliveryDeduper
    batcher: EventBatcher


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default=os.environ.get("SKYFORGE_ORCHESTRATOR_HOST", DEFAULT_HOST))
    parser.add_argument("--port", type=int, default=int(os.environ.get("SKYFORGE_ORCHESTRATOR_PORT", DEFAULT_PORT)))
    parser.add_argument("--repo", type=Path, default=Path(os.environ.get("SKYFORGE_REPO", ".")).resolve())
    parser.add_argument("--codex-bin", default=os.environ.get("SKYFORGE_CODEX_BIN", "codex"))
    parser.add_argument("--model", default=os.environ.get("SKYFORGE_ORCHESTRATOR_MODEL", "gpt-5.6-luna"))
    parser.add_argument("--debounce-seconds", type=float, default=float(os.environ.get("SKYFORGE_ORCHESTRATOR_DEBOUNCE_SECONDS", DEFAULT_DEBOUNCE_SECONDS)))
    parser.add_argument("--dry-run", action="store_true", help="Filter/batch webhooks but do not launch Codex")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    secret_text = os.environ.get("SKYFORGE_GITHUB_WEBHOOK_SECRET", "")
    if not secret_text:
        print("SKYFORGE_GITHUB_WEBHOOK_SECRET is required", file=sys.stderr)
        return 2

    app: Optional[AppServerClient] = None
    if not args.dry_run:
        app = AppServerClient(args.codex_bin, args.repo, args.model)
        try:
            app.start()
        except Exception as exc:
            print(f"Unable to start Codex App Server: {exc}", file=sys.stderr)
            return 3

    batcher = EventBatcher(app, args.debounce_seconds, args.dry_run)
    batcher.start()
    controller = Controller(secret_text.encode("utf-8"), DeliveryDeduper(), batcher)

    server = http.server.ThreadingHTTPServer((args.host, args.port), WebhookHandler)
    server.controller = controller  # type: ignore[attr-defined]

    def stop_handler(signum: int, frame: Any) -> None:
        del signum, frame
        threading.Thread(target=server.shutdown, daemon=True).start()

    signal.signal(signal.SIGINT, stop_handler)
    signal.signal(signal.SIGTERM, stop_handler)

    print(f"Skyforge orchestrator listening on http://{args.host}:{args.port}/github-webhook")
    print("dry-run mode" if args.dry_run else "Codex App Server dispatch enabled")
    try:
        server.serve_forever(poll_interval=0.5)
    finally:
        batcher.close()
        batcher.join(timeout=5.0)
        server.server_close()
        if app is not None:
            app.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
