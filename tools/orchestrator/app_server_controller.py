#!/usr/bin/env python3
"""Low-usage event-driven Codex App Server controller for Skyforge."""

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
from typing import Any, Optional

REPOSITORY = "ni-da-ba/skyforge"
STATE_PATH = Path(".skyforge-orchestrator-state.json")
PR_ACTIONS = {"opened", "reopened", "ready_for_review", "synchronize", "closed"}


@dataclass(frozen=True)
class DispatchEvent:
    delivery_id: str
    event_name: str
    action: str
    summary: str


def verify_github_signature(secret: bytes, body: bytes, signature_header: str) -> bool:
    if not secret or not signature_header.startswith("sha256="):
        return False
    expected = "sha256=" + hmac.new(secret, body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature_header)


def classify_webhook(event_name: str, payload: dict[str, Any], delivery_id: str) -> Optional[DispatchEvent]:
    repo = (payload.get("repository") or {}).get("full_name")
    if repo != REPOSITORY:
        return None
    action = str(payload.get("action") or "")

    if event_name == "pull_request" and action in PR_ACTIONS:
        pr = payload.get("pull_request") or {}
        number = pr.get("number") or payload.get("number")
        title = pr.get("title") or ""
        head = str((pr.get("head") or {}).get("sha") or "")[:12]
        return DispatchEvent(delivery_id, event_name, action, f"PR #{number} {action}: {title} head={head}")

    if event_name == "workflow_run" and action == "completed":
        run = payload.get("workflow_run") or {}
        return DispatchEvent(
            delivery_id,
            event_name,
            action,
            f"Workflow completed: {run.get('name')} conclusion={run.get('conclusion')} head={str(run.get('head_sha') or '')[:12]}",
        )

    if event_name == "push" and payload.get("ref") == "refs/heads/main":
        return DispatchEvent(
            delivery_id,
            event_name,
            "push",
            f"main advanced to {str(payload.get('after') or '')[:12]} with {len(payload.get('commits') or [])} commit(s)",
        )

    if event_name == "issue_comment" and action == "created":
        issue = payload.get("issue") or {}
        if "pull_request" not in issue:
            return None
        comment = payload.get("comment") or {}
        actor = str((comment.get("user") or {}).get("login") or "")
        if actor.endswith("[bot]"):
            return None
        body = str(comment.get("body") or "")[:240]
        return DispatchEvent(delivery_id, event_name, action, f"PR #{issue.get('number')} comment by {actor}: {body}")

    return None


def build_orchestrator_prompt(events: list[DispatchEvent]) -> str:
    lines = "\n".join(f"- {event.summary}" for event in events)
    return f"""Skyforge repository events occurred:\n\n{lines}\n\nFollow AGENTS.md and docs/agent-state/ORCHESTRATION_PROTOCOL.md. This is an event-driven orchestrator turn, not permission to manufacture work. Inspect only enough current repository state to determine whether these events made a lane actionable. Treat a healthy ordinary ChatGPT/manual producer with information-bearing GitHub or Actions movement as RUNNING_EXTERNAL and do not race it.\n\nIf no lane is actionable, end promptly. During the pilot, execute or dispatch at most one bounded worker objective. Do not poll CI. Stop at human/product/permission gates. Persist useful repository progress before ending.\n\nReport only: lane classification, action taken or waiting condition, repository evidence created, and any human gate."""


class DeliveryDeduper:
    def __init__(self, ttl_seconds: float = 3600.0) -> None:
        self.ttl_seconds = ttl_seconds
        self.seen: dict[str, float] = {}
        self.lock = threading.Lock()

    def first_seen(self, delivery_id: str) -> bool:
        now = time.monotonic()
        with self.lock:
            for key, stamp in list(self.seen.items()):
                if now - stamp > self.ttl_seconds:
                    self.seen.pop(key, None)
            if delivery_id in self.seen:
                return False
            self.seen[delivery_id] = now
            return True


class AppServerClient:
    """Minimal JSONL client for one persistent Codex App Server thread."""

    def __init__(self, codex_bin: str, cwd: Path, model: str, state_path: Path = STATE_PATH) -> None:
        self.codex_bin = codex_bin
        self.cwd = cwd
        self.model = model
        self.state_path = state_path
        self.process: Optional[subprocess.Popen[str]] = None
        self.messages: "queue.Queue[dict[str, Any]]" = queue.Queue()
        self.next_id = 1
        self.thread_id = self._load_thread_id()

    def _load_thread_id(self) -> Optional[str]:
        try:
            value = json.loads(self.state_path.read_text(encoding="utf-8")).get("thread_id")
            return str(value) if value else None
        except (FileNotFoundError, OSError, json.JSONDecodeError):
            return None

    def _save_thread_id(self) -> None:
        assert self.thread_id
        self.state_path.write_text(json.dumps({"thread_id": self.thread_id}, indent=2) + "\n", encoding="utf-8")

    def start(self) -> None:
        if self.process is not None:
            return
        self.process = subprocess.Popen(
            [self.codex_bin, "app-server"],
            cwd=self.cwd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=sys.stderr,
            text=True,
            bufsize=1,
        )
        threading.Thread(target=self._reader, name="codex-app-server-reader", daemon=True).start()
        self._request(
            "initialize",
            {"clientInfo": {"name": "skyforge_orchestrator", "title": "Skyforge Orchestrator", "version": "0.1.0"}},
            30.0,
        )
        self._ensure_thread()

    def close(self) -> None:
        if self.process is None:
            return
        self.process.terminate()
        try:
            self.process.wait(timeout=5.0)
        except subprocess.TimeoutExpired:
            self.process.kill()
        self.process = None

    def _reader(self) -> None:
        assert self.process is not None and self.process.stdout is not None
        for raw in self.process.stdout:
            try:
                self.messages.put(json.loads(raw))
            except json.JSONDecodeError:
                print(f"[app-server] non-JSON stdout: {raw.rstrip()}", file=sys.stderr)

    def _send(self, payload: dict[str, Any]) -> None:
        assert self.process is not None and self.process.stdin is not None
        self.process.stdin.write(json.dumps(payload, separators=(",", ":")) + "\n")
        self.process.stdin.flush()

    def _request(self, method: str, params: dict[str, Any], timeout: float) -> dict[str, Any]:
        request_id = self.next_id
        self.next_id += 1
        self._send({"method": method, "id": request_id, "params": params})
        deadline = time.monotonic() + timeout
        parked: list[dict[str, Any]] = []
        try:
            while time.monotonic() < deadline:
                message = self.messages.get(timeout=max(0.01, deadline - time.monotonic()))
                if message.get("id") == request_id:
                    if "error" in message:
                        raise RuntimeError(f"{method} failed: {message['error']}")
                    return message.get("result") or {}
                parked.append(message)
        finally:
            for message in parked:
                self.messages.put(message)
        raise TimeoutError(f"Timed out waiting for {method}")

    @staticmethod
    def _id_from(result: dict[str, Any], kind: str) -> Optional[str]:
        camel = kind + "Id"
        snake = kind + "_id"
        nested = result.get(kind) if isinstance(result.get(kind), dict) else {}
        value = result.get(camel) or result.get(snake) or nested.get("id")
        return str(value) if value else None

    def _ensure_thread(self) -> None:
        if self.thread_id:
            try:
                self._request("thread/resume", {"threadId": self.thread_id}, 30.0)
                return
            except Exception as exc:
                print(f"[app-server] resume failed ({exc}); starting a fresh orchestrator thread", file=sys.stderr)
                self.thread_id = None
        result = self._request("thread/start", {"cwd": str(self.cwd), "model": self.model}, 30.0)
        self.thread_id = self._id_from(result, "thread")
        if not self.thread_id:
            raise RuntimeError(f"thread/start returned no thread id: {result}")
        self._save_thread_id()

    def run_turn(self, prompt: str, timeout: float = 3600.0) -> None:
        if self.process is None:
            self.start()
        assert self.thread_id
        result = self._request(
            "turn/start",
            {"threadId": self.thread_id, "input": [{"type": "text", "text": prompt}]},
            30.0,
        )
        turn_id = self._id_from(result, "turn")
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            message = self.messages.get(timeout=max(0.01, deadline - time.monotonic()))
            method = str(message.get("method") or "")
            params = message.get("params") or {}
            if "approval" in method.lower():
                raise RuntimeError("Codex requested approval; handle this as a human gate")
            if method in {"turn/failed", "turn/cancelled", "turn/ended_with_error"}:
                raise RuntimeError(f"Codex turn failed: {message}")
            if method == "turn/completed":
                observed = self._id_from(params, "turn")
                if not turn_id or not observed or observed == turn_id:
                    return
        raise TimeoutError("Timed out waiting for turn/completed")


class EventBatcher(threading.Thread):
    def __init__(self, app: Optional[AppServerClient], debounce_seconds: float, dry_run: bool) -> None:
        super().__init__(name="skyforge-event-batcher", daemon=True)
        self.app = app
        self.debounce_seconds = debounce_seconds
        self.dry_run = dry_run
        self.events: "queue.Queue[DispatchEvent]" = queue.Queue()
        self.stop_event = threading.Event()

    def submit(self, event: DispatchEvent) -> None:
        self.events.put(event)

    def close(self) -> None:
        self.stop_event.set()
        self.events.put(DispatchEvent("shutdown", "shutdown", "shutdown", "shutdown"))

    def run(self) -> None:
        while not self.stop_event.is_set():
            first = self.events.get()
            if first.delivery_id == "shutdown":
                return
            batch = [first]
            deadline = time.monotonic() + self.debounce_seconds
            while time.monotonic() < deadline:
                try:
                    batch.append(self.events.get(timeout=max(0.01, deadline - time.monotonic())))
                except queue.Empty:
                    break
            prompt = build_orchestrator_prompt(batch)
            if self.dry_run:
                print("\n=== ACTIONABLE SKYFORGE EVENT BATCH ===\n" + prompt + "\n=== END BATCH ===\n")
                continue
            assert self.app is not None
            try:
                self.app.run_turn(prompt)
            except Exception as exc:
                # No automated retries: avoid usage loops; hourly Audit remains the fallback.
                print(f"[orchestrator] Codex turn failed: {exc}", file=sys.stderr)


@dataclass
class ControllerState:
    secret: bytes
    deduper: DeliveryDeduper
    batcher: EventBatcher


class WebhookHandler(http.server.BaseHTTPRequestHandler):
    server_version = "SkyforgeOrchestrator/0.1"

    def do_POST(self) -> None:  # noqa: N802
        state: ControllerState = self.server.controller  # type: ignore[attr-defined]
        if self.path != "/github-webhook":
            self.send_error(404)
            return
        try:
            body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        except ValueError:
            self.send_error(400, "invalid content length")
            return
        if not verify_github_signature(state.secret, body, self.headers.get("X-Hub-Signature-256", "")):
            self.send_error(401, "invalid signature")
            return
        delivery = self.headers.get("X-GitHub-Delivery", "")
        if not delivery or not state.deduper.first_seen(delivery):
            self.send_response(202)
            self.end_headers()
            return
        try:
            payload = json.loads(body)
        except json.JSONDecodeError:
            self.send_error(400, "invalid JSON")
            return
        event = classify_webhook(self.headers.get("X-GitHub-Event", ""), payload, delivery)
        if event:
            state.batcher.submit(event)
        self.send_response(202)
        self.end_headers()

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"[webhook] {fmt % args}", file=sys.stderr)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default=os.environ.get("SKYFORGE_ORCHESTRATOR_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("SKYFORGE_ORCHESTRATOR_PORT", "8765")))
    parser.add_argument("--repo", type=Path, default=Path(os.environ.get("SKYFORGE_REPO", ".")).resolve())
    parser.add_argument("--codex-bin", default=os.environ.get("SKYFORGE_CODEX_BIN", "codex"))
    parser.add_argument("--model", default=os.environ.get("SKYFORGE_ORCHESTRATOR_MODEL", "gpt-5.6-luna"))
    parser.add_argument("--debounce-seconds", type=float, default=float(os.environ.get("SKYFORGE_ORCHESTRATOR_DEBOUNCE_SECONDS", "20")))
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    secret = os.environ.get("SKYFORGE_GITHUB_WEBHOOK_SECRET", "")
    if not secret:
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
    server = http.server.ThreadingHTTPServer((args.host, args.port), WebhookHandler)
    server.controller = ControllerState(secret.encode(), DeliveryDeduper(), batcher)  # type: ignore[attr-defined]

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
        if app:
            app.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
