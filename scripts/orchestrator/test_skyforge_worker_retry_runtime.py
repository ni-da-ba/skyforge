from __future__ import annotations

import subprocess
import sys
import tempfile
import threading
import types
import unittest
from pathlib import Path
from unittest import mock

import skyforge_worker_retry_runtime as runtime


class _FakeState:
    def __init__(self, data: dict | None = None) -> None:
        self.data = data or {}
        self.saves = 0

    def save(self) -> None:
        self.saves += 1


class _FakeOrchestrator:
    def __init__(self, root: Path) -> None:
        self.root = root
        self._state_lock = threading.RLock()
        self.metrics: list[str] = []
        self.paused = False
        self.gates: list[dict] = []
        self.state = _FakeState(
            {
                "pending_worker": {
                    "lane": "Implementation",
                    "branch": "codex/implementation-test",
                    "worktree": str(root),
                    "stage": "editing",
                    "worker_tier": "TERRA",
                }
            }
        )

    def _metric(self, name: str) -> None:
        self.metrics.append(name)

    def _changed_paths(self, worktree: Path) -> list[str]:
        out = subprocess.run(
            ["git", "status", "--porcelain"],
            cwd=worktree,
            check=True,
            text=True,
            capture_output=True,
        ).stdout.splitlines()
        return [line[3:].strip() for line in out if line]

    def set_paused(self, paused: bool, *, actor: str | None = None) -> None:
        self.paused = paused
        self.state.data["paused"] = paused
        self.state.data["paused_by"] = actor
        self.state.save()

    def _post_gate(self, payload: dict) -> bool:
        self.gates.append(payload)
        return True

    def _consume_budget(self, kind: str) -> None:
        self.metrics.append(f"budget:{kind}")


def _git_repo(path: Path) -> None:
    subprocess.run(["git", "init", "-q"], cwd=path, check=True)
    subprocess.run(["git", "config", "user.email", "test@example.com"], cwd=path, check=True)
    subprocess.run(["git", "config", "user.name", "Test"], cwd=path, check=True)
    (path / "seed.txt").write_text("seed\n")
    subprocess.run(["git", "add", "seed.txt"], cwd=path, check=True)
    subprocess.run(["git", "commit", "-qm", "seed"], cwd=path, check=True)


class WorkerRetryRuntimeTests(unittest.TestCase):
    def test_fingerprint_tracks_edits_to_existing_untracked_file(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            scratch = root / "scratch.txt"
            scratch.write_text("one\n")
            first = runtime._worktree_fingerprint(fake, root)
            scratch.write_text("two\n")
            second = runtime._worktree_fingerprint(fake, root)
            self.assertNotEqual(first, second)

    def test_interrupted_no_progress_attempt_opens_circuit_before_new_turn(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            fingerprint = runtime._worktree_fingerprint(fake, root)
            pending = fake.state.data["pending_worker"]
            pending.update(
                {
                    "worker_attempt_count": 2,
                    "worker_stalled_attempts": 1,
                    "last_worker_attempt_result": "in_flight",
                    "last_worker_attempt_before_fingerprint": fingerprint,
                }
            )
            with mock.patch.object(runtime, "_max_no_progress_attempts", return_value=2):
                with self.assertRaises(runtime.core.SafetyPause):
                    runtime._begin_worker_attempt(fake, root)

            self.assertTrue(fake.paused)
            self.assertEqual(fake.state.data["paused_by"], "worker-retry-circuit")
            self.assertEqual(pending["worker_stalled_attempts"], 2)
            self.assertTrue(pending["worker_retry_circuit_open"])
            self.assertEqual(pending["worker_attempt_count"], 2)
            self.assertEqual(len(fake.gates), 1)

    def test_interrupted_attempt_with_progress_resets_stall_count(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            pending = fake.state.data["pending_worker"]
            pending.update(
                {
                    "worker_attempt_count": 1,
                    "worker_stalled_attempts": 1,
                    "last_worker_attempt_result": "in_flight",
                    "last_worker_attempt_before_fingerprint": "different-fingerprint",
                }
            )
            before, snapshot = runtime._begin_worker_attempt(fake, root)
            self.assertIsNotNone(snapshot)
            self.assertEqual(pending["worker_stalled_attempts"], 0)
            self.assertEqual(pending["worker_attempt_count"], 2)
            self.assertEqual(pending["last_worker_attempt_result"], "in_flight")
            self.assertEqual(pending["last_worker_attempt_before_fingerprint"], before)
            self.assertFalse(fake.paused)

    def test_completed_response_replays_without_another_budgeted_turn(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            pending = fake.state.data["pending_worker"]
            pending["last_worker_attempt_result"] = "completed"
            pending["last_worker_response"] = "already finished"

            with mock.patch.object(runtime, "_begin_worker_attempt") as begin:
                response = runtime._worker(fake, "prompt", "TERRA", root)

            self.assertEqual(response, "already finished")
            begin.assert_not_called()
            self.assertNotIn("budget:worker", fake.metrics)
            self.assertIn("worker_completed_response_replays", fake.metrics)

    def test_existing_worker_thread_is_resumed_not_recreated(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            fake.state.data["pending_worker"]["worker_thread_id"] = "thread-123"
            calls: list[tuple[str, str | None]] = []

            class _Thread:
                id = "thread-123"

                def run(self, prompt: str, *, sandbox=None):
                    calls.append(("run", prompt))
                    return types.SimpleNamespace(final_response="done")

            class _Codex:
                def __enter__(self):
                    return self

                def __exit__(self, exc_type, exc, tb):
                    return False

                def thread_resume(self, thread_id: str, **kwargs):
                    calls.append(("resume", thread_id))
                    return _Thread()

                def thread_start(self, **kwargs):
                    calls.append(("start", None))
                    return _Thread()

            module = types.SimpleNamespace(
                Codex=_Codex,
                Sandbox=types.SimpleNamespace(workspace_write="workspace-write"),
            )
            with mock.patch.dict(sys.modules, {"openai_codex": module}):
                response = runtime._worker(fake, "continue", "TERRA", root)

            self.assertEqual(response, "done")
            self.assertIn(("resume", "thread-123"), calls)
            self.assertNotIn(("start", None), calls)
            self.assertIn("budget:worker", fake.metrics)
            self.assertEqual(
                fake.state.data["pending_worker"]["last_worker_attempt_result"],
                "completed",
            )


if __name__ == "__main__":
    unittest.main()
