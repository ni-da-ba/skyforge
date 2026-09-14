from __future__ import annotations

import subprocess
import threading
import unittest
from pathlib import Path
from unittest import mock

import skyforge_managed_pr_rebase_runtime as runtime


class _FakeState:
    def __init__(self) -> None:
        self.data = {
            "managed": {},
            "pending_worker": None,
            "metrics": {},
        }
        self.saves = 0

    def save(self) -> None:
        self.saves += 1


class _FakeOrchestrator:
    def __init__(self) -> None:
        self._state_lock = threading.RLock()
        self.state = _FakeState()
        self.repo = "ni-da-ba/skyforge"
        self.root = Path("/tmp/root")
        self.debounce_seconds = 20
        self.metrics: dict[str, int] = {}
        self.scheduled: list[int] = []
        self.paused = False
        self.paused_by = None
        self.gates: list[dict] = []
        self.retired: list[Path] = []

    def _metric(self, name: str, amount: int = 1) -> None:
        self.metrics[name] = self.metrics.get(name, 0) + amount

    def _ensure_worker_worktree(self, branch: str, start_ref: str) -> Path:
        return Path("/tmp/worktree")

    def _worktree_clean(self, cwd=None) -> bool:
        return True

    def _retire_worker_worktree(self, worktree: Path) -> None:
        self.retired.append(worktree)

    def _schedule_pending(self, seconds: int) -> None:
        self.scheduled.append(seconds)

    def set_paused(self, paused: bool, *, actor=None) -> None:
        self.paused = paused
        self.paused_by = actor

    def _post_gate(self, decision: dict) -> bool:
        self.gates.append(dict(decision))
        return True


def _managed() -> dict:
    return {
        "pr_number": 600,
        "branch": "codex/implementation-task-492",
        "expected_head": "a" * 40,
        "changed_paths": ["skyforge-neoforge-1211/src/main/java/Example.java"],
        "authority_key": "task:492",
        "authority_issue": 492,
        "auto_merge_eligible": True,
    }


def _completed(args, *, stdout="", stderr="", returncode=0):
    return subprocess.CompletedProcess(args=args, returncode=returncode, stdout=stdout, stderr=stderr)


class ManagedPrRebaseRuntimeTests(unittest.TestCase):
    def test_pending_worker_prevents_managed_base_mutation(self) -> None:
        fake = _FakeOrchestrator()
        fake.state.data["managed"]["Implementation"] = _managed()
        fake.state.data["pending_worker"] = {"stage": "editing"}
        with mock.patch.object(runtime, "_refresh_one_managed_pr") as refresh:
            count = runtime._refresh_managed_pr_bases(fake)
        self.assertEqual(count, 0)
        refresh.assert_not_called()

    def test_clean_managed_pr_needs_no_rebase(self) -> None:
        fake = _FakeOrchestrator()
        managed = _managed()
        with mock.patch.object(
            runtime.core,
            "_json_cmd",
            return_value={
                "state": "OPEN",
                "baseRefName": "main",
                "headRefName": managed["branch"],
                "headRefOid": managed["expected_head"],
                "mergeStateStatus": "CLEAN",
            },
        ):
            changed = runtime._refresh_one_managed_pr(fake, "Implementation", managed)
        self.assertFalse(changed)

    def test_clean_rebase_updates_expected_head_with_force_lease(self) -> None:
        fake = _FakeOrchestrator()
        managed = _managed()
        fake.state.data["managed"]["Implementation"] = dict(managed)
        new_head = "b" * 40
        commands: list[list[str]] = []

        def run(args, cwd=None, check=True, timeout=None):
            commands.append(list(args))
            if args[:3] == ["git", "rev-parse", "HEAD"]:
                # First HEAD read is the expected old branch; second is rebased head.
                count = sum(1 for command in commands if command[:3] == ["git", "rev-parse", "HEAD"])
                return _completed(args, stdout=(managed["expected_head"] if count == 1 else new_head) + "\n")
            if args[:3] == ["git", "diff", "--name-only"]:
                return _completed(args, stdout="skyforge-neoforge-1211/src/main/java/Example.java\n")
            return _completed(args)

        with (
            mock.patch.object(
                runtime.core,
                "_json_cmd",
                return_value={
                    "state": "OPEN",
                    "baseRefName": "main",
                    "headRefName": managed["branch"],
                    "headRefOid": managed["expected_head"],
                    "mergeStateStatus": "BEHIND",
                },
            ),
            mock.patch.object(runtime.core, "_run", side_effect=run),
        ):
            changed = runtime._refresh_one_managed_pr(fake, "Implementation", managed)

        self.assertTrue(changed)
        current = fake.state.data["managed"]["Implementation"]
        self.assertEqual(current["expected_head"], new_head)
        self.assertEqual(current["rebased_from_head"], managed["expected_head"])
        self.assertEqual(fake.metrics["managed_pr_rebases"], 1)
        push = next(command for command in commands if command[:2] == ["git", "push"])
        self.assertIn(
            f"--force-with-lease=refs/heads/{managed['branch']}:{managed['expected_head']}",
            push,
        )

    def test_conflicting_rebase_aborts_and_pauses_before_model_spend(self) -> None:
        fake = _FakeOrchestrator()
        managed = _managed()
        fake.state.data["managed"]["Implementation"] = dict(managed)
        commands: list[list[str]] = []

        def run(args, cwd=None, check=True, timeout=None):
            commands.append(list(args))
            if args[:3] == ["git", "rev-parse", "HEAD"]:
                return _completed(args, stdout=managed["expected_head"] + "\n")
            if args[:3] == ["git", "rebase", "origin/main"]:
                return _completed(args, stderr="CONFLICT Example.java", returncode=1)
            return _completed(args)

        with (
            mock.patch.object(
                runtime.core,
                "_json_cmd",
                return_value={
                    "state": "OPEN",
                    "baseRefName": "main",
                    "headRefName": managed["branch"],
                    "headRefOid": managed["expected_head"],
                    "mergeStateStatus": "DIRTY",
                },
            ),
            mock.patch.object(runtime.core, "_run", side_effect=run),
            self.assertRaises(runtime.core.SafetyPause),
        ):
            runtime._refresh_one_managed_pr(fake, "Implementation", managed)

        self.assertTrue(fake.paused)
        self.assertEqual(fake.paused_by, "managed-pr-rebase-conflict")
        self.assertEqual(fake.metrics["managed_pr_rebase_conflicts"], 1)
        self.assertTrue(any(command[:3] == ["git", "rebase", "--abort"] for command in commands))
        self.assertFalse(any(command[:2] == ["git", "push"] for command in commands))
        self.assertEqual(fake.gates[0]["pr_number"], 600)

    def test_dispatch_defers_classifier_when_rebase_advanced_head(self) -> None:
        fake = _FakeOrchestrator()
        with (
            mock.patch.object(runtime, "_refresh_managed_pr_bases", return_value=1),
            mock.patch.object(runtime, "_ORIGINAL_DISPATCH") as original,
        ):
            runtime._dispatch(fake, [])
        original.assert_not_called()
        self.assertEqual(fake.scheduled, [30])
        self.assertEqual(fake.metrics["managed_pr_rebase_dispatch_deferrals"], 1)


if __name__ == "__main__":
    unittest.main()
