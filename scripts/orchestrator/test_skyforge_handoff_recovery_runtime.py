import importlib
import pathlib
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest import mock


MODULE_DIR = pathlib.Path(__file__).resolve().parent
if str(MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(MODULE_DIR))

runtime = importlib.import_module("skyforge_handoff_recovery_runtime")
core = runtime.core


def completed(args, stdout=""):
    return subprocess.CompletedProcess(args, 0, stdout=stdout, stderr="")


class HandoffRecoveryRuntimeTests(unittest.TestCase):
    def make_orchestrator(self, root: pathlib.Path):
        o = core.Orchestrator.__new__(core.Orchestrator)
        o.root = root
        o.repo = "ni-da-ba/skyforge"
        o.state = core.LocalState(root / "state.json")
        o._state_lock = threading.RLock()
        o._dispatch_lock = threading.Lock()
        o._metric = mock.Mock()
        o._changed_paths = mock.Mock(return_value=["src/Fixture.java"])
        return o

    def seed(self, o, worktree):
        pending = {
            "branch": "codex/implementation-task-284-test",
            "stage": "handoff",
            "managed_pr": 485,
            "worktree": str(worktree),
        }
        o.state.data["paused"] = True
        o.state.data["pending_worker"] = pending
        o.state.data["pending_decision"] = {"decision": {"decision": "DISPATCH"}}
        o.state.data["blocked_kind"] = "controller_error"
        o.state.data["blocked_until_epoch"] = 9999999999.0
        o.state.data["blocked_reason"] = "stuck handoff"
        o.state.data["managed"] = {
            "Implementation": {
                "branch": pending["branch"],
                "pr_number": 485,
                "authority_key": "task:284",
            }
        }
        o.state.save()
        return pending

    def test_open_handoff_detach_preserves_pr_ownership_and_clears_worker(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            worktree = root / "worker"
            worktree.mkdir()
            o = self.make_orchestrator(root)
            pending = self.seed(o, worktree)
            patch = root / ".skyforge-orchestrator/recovery/recovery.patch"
            meta = root / ".skyforge-orchestrator/recovery/recovery.json"
            patch.parent.mkdir(parents=True)
            patch.write_text("")
            meta.write_text("{}")

            pr = {
                "state": "OPEN",
                "mergedAt": None,
                "headRefName": pending["branch"],
                "headRefOid": "remote-head",
            }
            with (
                mock.patch.object(core, "_json_cmd", return_value=pr),
                mock.patch.object(runtime, "_worktree_path", return_value=worktree),
                mock.patch.object(
                    runtime,
                    "_archive_open_handoff",
                    return_value=(["src/Fixture.java"], patch, meta),
                ),
                mock.patch.object(core, "_run", return_value=completed(["git"])),
            ):
                runtime._detach_open_managed_handoff(o, pending, actor="ni-da-ba")

            self.assertIsNone(o.state.data.get("pending_worker"))
            self.assertIsNone(o.state.data.get("pending_decision"))
            self.assertIsNone(o.state.data.get("blocked_kind"))
            self.assertEqual(o.state.data.get("blocked_until_epoch"), 0.0)
            self.assertEqual(o.state.data["managed"]["Implementation"]["pr_number"], 485)
            self.assertIn("open managed handoff detached", o.state.data["last_worker_discard"]["reason"])
            o._metric.assert_called_with("operator_open_handoff_detachments")

    def test_closed_unmerged_pr_is_not_detached(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            worktree = root / "worker"
            worktree.mkdir()
            o = self.make_orchestrator(root)
            pending = self.seed(o, worktree)
            pr = {
                "state": "CLOSED",
                "mergedAt": None,
                "headRefName": pending["branch"],
                "headRefOid": "remote-head",
            }
            with mock.patch.object(core, "_json_cmd", return_value=pr):
                with self.assertRaisesRegex(RuntimeError, "state is CLOSED"):
                    runtime._detach_open_managed_handoff(o, pending, actor="ni-da-ba")

            self.assertIsNotNone(o.state.data.get("pending_worker"))

    def test_non_handoff_pending_worker_delegates_to_existing_guard(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            o = self.make_orchestrator(root)
            o.state.data["paused"] = True
            o.state.data["pending_worker"] = {
                "branch": "codex/example",
                "stage": "editing",
                "managed_pr": 485,
            }
            o.state.save()

            with mock.patch.object(runtime, "_ORIGINAL_DISCARD_PENDING_WORKER") as delegate:
                runtime.discard_pending_worker(o, actor="ni-da-ba")
            delegate.assert_called_once_with(o, actor="ni-da-ba")


if __name__ == "__main__":
    unittest.main()
