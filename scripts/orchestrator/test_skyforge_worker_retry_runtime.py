from __future__ import annotations

import os
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

    @staticmethod
    def _worker_path_forbidden(path: str, *, lane=None, allowed_paths=None) -> bool:
        return runtime.core.Orchestrator._worker_path_forbidden(
            path, lane=lane, allowed_paths=allowed_paths
        )

    @staticmethod
    def _worker_path_allowed(path: str, allowed_paths) -> bool:
        return runtime.core.Orchestrator._worker_path_allowed(path, allowed_paths)


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
                    "last_pre_handoff_validation_error": {"summary": "compile failed"},
                }
            )
            before, snapshot = runtime._begin_worker_attempt(fake, root)
            self.assertIsNotNone(snapshot)
            self.assertEqual(pending["worker_stalled_attempts"], 0)
            self.assertEqual(pending["worker_attempt_count"], 2)
            self.assertEqual(pending["last_worker_attempt_result"], "in_flight")
            self.assertEqual(pending["last_worker_attempt_before_fingerprint"], before)
            self.assertIsNone(pending["last_pre_handoff_validation_error"])
            self.assertFalse(fake.paused)

    def test_quota_pacing_failure_does_not_count_as_worker_stall(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            pending = fake.state.data["pending_worker"]
            pending["worker_stalled_attempts"] = 1
            fingerprint = runtime._worktree_fingerprint(fake, root)

            runtime._record_worker_result(
                fake,
                root,
                before_fingerprint=fingerprint,
                result="failed",
                failure_kind="quota_pacing",
            )

            self.assertEqual(pending["worker_stalled_attempts"], 1)
            self.assertEqual(pending["last_worker_attempt_failure_kind"], "quota_pacing")
            self.assertNotIn("worker_attempts_without_durable_progress", fake.metrics)

    def test_provider_pacing_does_not_consume_per_worker_turn_cap(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            pending = fake.state.data["pending_worker"]
            with mock.patch.object(
                runtime,
                "_ORIGINAL_CONSUME_BUDGET",
                side_effect=runtime.core.RetryBlocked("quota_pacing", 300, "pace"),
            ):
                with self.assertRaises(runtime.core.RetryBlocked):
                    runtime._consume_budget(fake, "worker")
            self.assertEqual(int(pending.get("worker_model_turn_count") or 0), 0)

    def test_admitted_worker_turn_increments_per_worker_count(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            pending = fake.state.data["pending_worker"]
            pending["worker_model_turn_count"] = 2
            with mock.patch.object(runtime, "_ORIGINAL_CONSUME_BUDGET") as consume:
                runtime._consume_budget(fake, "worker")
            consume.assert_called_once_with(fake, "worker")
            self.assertEqual(pending["worker_model_turn_count"], 3)

    def test_model_turn_cap_pauses_before_provider_admission(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            pending = fake.state.data["pending_worker"]
            pending["worker_model_turn_count"] = 4
            with (
                mock.patch.object(runtime, "_max_model_turns_per_worker", return_value=4),
                mock.patch.object(runtime, "_ORIGINAL_CONSUME_BUDGET") as consume,
            ):
                with self.assertRaises(runtime.core.SafetyPause):
                    runtime._consume_budget(fake, "worker")
            consume.assert_not_called()
            self.assertTrue(fake.paused)
            self.assertEqual(fake.state.data["paused_by"], "worker-turn-cap")
            self.assertTrue(pending["worker_retry_circuit_open"])
            self.assertEqual(len(fake.gates), 1)

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

    def test_pre_handoff_failure_does_not_replay_rejected_response(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            pending = fake.state.data["pending_worker"]
            pending["last_worker_attempt_result"] = "completed"
            pending["last_worker_response"] = "response that failed compile"
            pending["last_pre_handoff_validation_error"] = {"summary": "compile failed"}

            with mock.patch.object(
                runtime,
                "_begin_worker_attempt",
                side_effect=runtime.core.SafetyPause("repair attempt reached"),
            ) as begin:
                with self.assertRaisesRegex(runtime.core.SafetyPause, "repair attempt reached"):
                    runtime._worker(fake, "repair", "TERRA", root)

            begin.assert_called_once()
            self.assertNotIn("worker_completed_response_replays", fake.metrics)

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

            class _CodexConfig:
                def __init__(self, *, config_overrides=()):
                    self.config_overrides = config_overrides

            class _Codex:
                def __init__(self, config=None):
                    calls.append(("config", getattr(config, "config_overrides", None)))

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
                CodexConfig=_CodexConfig,
                Sandbox=types.SimpleNamespace(workspace_write="workspace-write", read_only="read-only"),
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

    def test_health_snapshot_marks_dirty_worktree_active_and_exposes_diff(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            (root / "seed.txt").write_text("seed\nprogress\n")
            with (
                mock.patch.object(runtime, "_ORIGINAL_HEALTH_SNAPSHOT", return_value={}),
                mock.patch.object(runtime, "_progress_stall_seconds", return_value=900),
            ):
                snapshot = runtime.health_snapshot(fake)
            self.assertEqual(snapshot["worker_progress_state"], "ACTIVE")
            self.assertEqual(snapshot["worker_dirty_file_count"], 1)
            self.assertEqual(snapshot["worker_diff_additions"], 1)
            self.assertIsNotNone(snapshot["worker_last_progress_at"] )
            self.assertTrue(snapshot["worker_worktree_fingerprint"] )

    def test_health_snapshot_marks_old_clean_editing_worker_stalled(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            pending = fake.state.data["pending_worker"]
            pending["worker_observed_fingerprint"] = runtime._worktree_fingerprint(fake, root)
            pending["last_worker_attempt_started_at"] = "2026-01-01T00:00:00+00:00"
            pending["last_worker_attempt_result"] = "completed"
            with (
                mock.patch.object(runtime, "_ORIGINAL_HEALTH_SNAPSHOT", return_value={}),
                mock.patch.object(runtime, "_progress_stall_seconds", return_value=900),
            ):
                snapshot = runtime.health_snapshot(fake)
            self.assertEqual(snapshot["worker_progress_state"], "STALLED")
            self.assertEqual(snapshot["worker_dirty_file_count"], 0)
            self.assertGreater(snapshot["worker_stalled_for_seconds"], 900)

    def test_health_snapshot_uses_recent_admitted_turn_as_in_flight_not_active(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            pending = fake.state.data["pending_worker"]
            pending["worker_observed_fingerprint"] = runtime._worktree_fingerprint(fake, root)
            pending["last_worker_attempt_result"] = "in_flight"
            pending["last_worker_model_turn_admitted_at"] = runtime.core._utc_now()
            with (
                mock.patch.object(runtime, "_ORIGINAL_HEALTH_SNAPSHOT", return_value={}),
                mock.patch.object(runtime, "_progress_stall_seconds", return_value=900),
            ):
                snapshot = runtime.health_snapshot(fake)
            self.assertEqual(snapshot["worker_progress_state"], "IN_FLIGHT")
            self.assertIsNone(snapshot["worker_last_progress_at"] )


class HostedWorkerControllerPatchTests(unittest.TestCase):
    def test_hosted_worker_uses_default_config_without_deprecated_landlock(self):
        config = runtime._hosted_worker_codex_config(
            lambda **kwargs: types.SimpleNamespace(**kwargs)
        )
        self.assertFalse(hasattr(config, "config_overrides"))

    def test_extract_controller_patch_accepts_exact_marker_pair(self):
        response = f"""{runtime.PATCH_SUMMARY}
changed one file
{runtime.PATCH_BEGIN}
diff --git a/seed.txt b/seed.txt
--- a/seed.txt
+++ b/seed.txt
@@ -1 +1 @@
-seed
+changed
{runtime.PATCH_END}
"""
        summary, patch = runtime._extract_controller_patch(response)
        self.assertEqual(summary, "changed one file")
        self.assertIn("diff --git a/seed.txt b/seed.txt", patch or "")

    def test_extract_controller_patch_rejects_malformed_markers(self):
        with self.assertRaises(ValueError):
            runtime._extract_controller_patch(
                f"{runtime.PATCH_BEGIN}\ndiff --git a/x b/x\n"
            )

    def test_empty_patch_is_a_genuine_no_change_handoff(self):
        response = f"""{runtime.PATCH_SUMMARY}
blocked by missing authority
{runtime.PATCH_BEGIN}
{runtime.PATCH_END}
"""
        summary, patch = runtime._extract_controller_patch(response)
        self.assertEqual(summary, "blocked by missing authority")
        self.assertIsNone(patch)

    def test_controller_applies_valid_patch_inside_worktree(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            patch = """diff --git a/seed.txt b/seed.txt
--- a/seed.txt
+++ b/seed.txt
@@ -1 +1 @@
-seed
+changed
"""
            changed = runtime._apply_controller_patch(
                fake, root, patch, lane="Implementation", allowed_paths=None
            )
            self.assertEqual(changed, ["seed.txt"])
            self.assertEqual((root / "seed.txt").read_text(), "changed\n")
            self.assertIn("worker_controller_patches_applied", fake.metrics)

    def test_controller_rejects_out_of_scope_patch_before_apply(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            patch = """diff --git a/seed.txt b/seed.txt
--- a/seed.txt
+++ b/seed.txt
@@ -1 +1 @@
-seed
+changed
"""
            with self.assertRaises(runtime.core.SafetyPause):
                runtime._apply_controller_patch(
                    fake, root, patch, lane="Implementation", allowed_paths=["docs/**"]
                )
            self.assertEqual((root / "seed.txt").read_text(), "seed\n")

    def test_controller_rejects_protected_path_before_apply(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _git_repo(root)
            fake = _FakeOrchestrator(root)
            patch = """diff --git a/.git/config b/.git/config
--- a/.git/config
+++ b/.git/config
@@ -1 +1 @@
-old
+new
"""
            with self.assertRaises(runtime.core.SafetyPause):
                runtime._apply_controller_patch(
                    fake, root, patch, lane="Implementation", allowed_paths=None
                )

    def test_patch_path_traversal_is_rejected(self):
        with self.assertRaises(ValueError):
            runtime._patch_paths(
                "diff --git a/../escape.txt b/../escape.txt\n"
            )

    def test_classifier_runtime_is_not_globally_reconfigured(self):
        self.assertNotIn("CODEX_CONFIG", os.environ)


if __name__ == "__main__":
    unittest.main()
