import importlib
import pathlib
import sys
import tempfile
import threading
import unittest
from unittest import mock


MODULE_DIR = pathlib.Path(__file__).resolve().parent
if str(MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(MODULE_DIR))

runtime = importlib.import_module("skyforge_control_replay_runtime")
core = runtime.core


class ControlReplayRuntimeTests(unittest.TestCase):
    def make_orchestrator(self, root: pathlib.Path):
        o = core.Orchestrator.__new__(core.Orchestrator)
        o.root = root
        o.repo = "ni-da-ba/skyforge"
        o.state = core.LocalState(root / "state.json")
        o._state_lock = threading.RLock()
        o._dispatch_lock = threading.Lock()
        return o

    def test_paused_no_worker_discard_is_idempotent_noop(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            o.state.data["paused"] = True
            o.state.data["pending_worker"] = None
            o.state.save()

            runtime.discard_pending_worker(o, actor="ni-da-ba")

            self.assertIsNone(o.state.data.get("pending_worker"))
            self.assertEqual(
                o.state.data["last_worker_discard_noop"]["actor"],
                "ni-da-ba",
            )
            self.assertEqual(
                o.state.data["metrics"]["operator_discard_noops"],
                1,
            )

    def test_no_worker_discard_still_requires_pause(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            o.state.data["paused"] = False
            o.state.data["pending_worker"] = None
            o.state.save()

            with self.assertRaisesRegex(RuntimeError, "requires the controller to be paused"):
                runtime.discard_pending_worker(o, actor="ni-da-ba")

    def test_existing_worker_delegates_to_guarded_runtime(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            o.state.data["paused"] = True
            o.state.data["pending_worker"] = {"branch": "codex/example", "stage": "editing"}
            o.state.save()

            with mock.patch.object(runtime, "_ORIGINAL_DISCARD_PENDING_WORKER") as delegate:
                runtime.discard_pending_worker(o, actor="ni-da-ba")

            delegate.assert_called_once_with(o, actor="ni-da-ba")

    def test_runtime_path_is_refresh_tracked(self):
        self.assertIn(runtime.RUNTIME_PATH, core.CONTROLLER_RUNTIME_PATHS)


if __name__ == "__main__":
    unittest.main()
