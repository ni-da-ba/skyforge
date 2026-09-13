from __future__ import annotations

import subprocess
import tempfile
import threading
import unittest
from pathlib import Path
from unittest import mock

import skyforge_pre_handoff_validation_runtime as runtime


class _FakeState:
    def __init__(self, data: dict | None = None) -> None:
        self.data = data or {}
        self.saves = 0

    def save(self) -> None:
        self.saves += 1


class _FakeOrchestrator:
    def __init__(self, root: Path, *, worktree: Path | None = None) -> None:
        self.root = root
        self.metrics: list[str] = []
        self._state_lock = threading.RLock()
        self.state = _FakeState(
            {
                "pending_worker": {
                    "stage": "editing",
                    "worktree": str(worktree or (root / "worker")),
                }
            }
        )

    def _metric(self, name: str) -> None:
        self.metrics.append(name)

    def _changed_paths(self, worktree: Path) -> list[str]:
        return ["skyforge-neoforge-1211/src/test/java/example/BrokenTest.java"]


class PreHandoffValidationRuntimeTests(unittest.TestCase):
    def test_java_compile_tasks_are_diff_sensitive_and_deduplicated(self) -> None:
        self.assertEqual(
            runtime._java_compile_tasks(
                [
                    "docs/agent-state/CONTENT_STATE.md",
                    "skyforge-neoforge-1211/src/test/java/a/OneTest.java",
                    "skyforge-neoforge-1211/src/test/java/a/TwoTest.java",
                    "skyforge-world/src/main/java/a/World.java",
                    "scripts/orchestrator/not-a-module/src/test/java/Nope.java",
                ]
            ),
            [
                ":skyforge-neoforge-1211:compileTestJava",
                ":skyforge-world:compileJava",
            ],
        )

    def test_docs_only_preflight_stops_after_diff_check(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fake = _FakeOrchestrator(root)
            ok = subprocess.CompletedProcess(["git"], 0, "", "")
            with mock.patch.object(runtime.core, "_run", return_value=ok) as run:
                runtime._run_pre_handoff_validation(
                    fake,
                    root,
                    ["docs/agent-state/CONTENT_STATE.md"],
                )

            self.assertEqual(run.call_count, 1)
            self.assertEqual(run.call_args.args[0], ["git", "diff", "--check", "HEAD"])
            self.assertIn("pre_handoff_validation_passes", fake.metrics)

    def test_changed_java_runs_only_affected_compile_task(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fake = _FakeOrchestrator(root)
            ok = subprocess.CompletedProcess(["command"], 0, "", "")
            with mock.patch.object(runtime.core, "_run", return_value=ok) as run:
                runtime._run_pre_handoff_validation(
                    fake,
                    root,
                    ["skyforge-neoforge-1211/src/test/java/example/BrokenTest.java"],
                )

            self.assertEqual(run.call_count, 2)
            compile_command = run.call_args_list[1].args[0]
            self.assertIn(":skyforge-neoforge-1211:compileTestJava", compile_command)
            self.assertNotIn(":skyforge-neoforge-1211:test", compile_command)
            self.assertIn("pre_handoff_validation_passes", fake.metrics)

    def test_compile_failure_keeps_worker_in_repairable_editing_state(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            worktree = root / "worker"
            worktree.mkdir()
            fake = _FakeOrchestrator(root, worktree=worktree)
            with (
                mock.patch.object(
                    runtime,
                    "_run_pre_handoff_validation",
                    side_effect=RuntimeError("compile failed"),
                ),
                mock.patch.object(runtime, "_ORIGINAL_MARK_WORKER_HANDOFF") as mark,
            ):
                with self.assertRaisesRegex(RuntimeError, "compile failed"):
                    runtime._mark_worker_handoff(fake, "summary")

            pending = fake.state.data["pending_worker"]
            self.assertEqual(pending["stage"], "editing")
            self.assertEqual(
                pending["last_pre_handoff_validation_error"]["summary"],
                "compile failed",
            )
            self.assertIsNotNone(fake.state.data["last_pre_handoff_validation_error"])
            self.assertGreater(fake.state.saves, 0)
            mark.assert_not_called()

    def test_successful_validation_allows_durable_handoff_mark(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            worktree = root / "worker"
            worktree.mkdir()
            fake = _FakeOrchestrator(root, worktree=worktree)
            with (
                mock.patch.object(runtime, "_run_pre_handoff_validation"),
                mock.patch.object(runtime, "_ORIGINAL_MARK_WORKER_HANDOFF") as mark,
            ):
                runtime._mark_worker_handoff(fake, "summary")
            mark.assert_called_once_with(fake, "summary")


if __name__ == "__main__":
    unittest.main()
