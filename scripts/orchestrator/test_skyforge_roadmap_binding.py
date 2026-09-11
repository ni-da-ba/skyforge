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

policy = importlib.import_module("roadmap_policy")
entrypoint = importlib.import_module("skyforge_control_replay_runtime")
runtime = importlib.import_module("skyforge_roadmap_runtime")
core = runtime.core


class RoadmapBindingRaceTests(unittest.TestCase):
    def make_orchestrator(self, root: pathlib.Path):
        o = core.Orchestrator.__new__(core.Orchestrator)
        o.root = root
        o.repo = "ni-da-ba/skyforge"
        o.state = core.LocalState(root / "state.json")
        o._state_lock = threading.RLock()
        o._dispatch_lock = threading.Lock()
        o.is_paused = mock.Mock(return_value=False)
        o.enqueue = mock.Mock()
        return o

    def manifest(self):
        return policy.parse_manifest(
            {
                "schema_version": 1,
                "roadmap_id": "binding-race",
                "enabled": True,
                "max_auto_claims_per_utc_day": 4,
                "nodes": [
                    {
                        "id": "task-a",
                        "kind": "task",
                        "lane": "Implementation",
                        "issue_number": 284,
                        "priority": 100,
                        "max_runs": 1,
                        "prerequisites": [],
                        "objective_hint": "bounded machine work",
                        "stop_boundary": "stop at human gate",
                    }
                ],
            }
        )

    def test_installed_replay_entrypoint_loads_bounded_roadmap_extension(self):
        self.assertIs(entrypoint.core, core)
        self.assertTrue(getattr(core, "_skyforge_bounded_roadmap_extension_installed", False))
        self.assertTrue(hasattr(core.Orchestrator, "_roadmap_maybe_advance"))
        self.assertIs(entrypoint.main, entrypoint._base.main)

    def test_active_node_binds_new_managed_pr_before_global_open_pr_guard(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            manifest = self.manifest()
            event = runtime._roadmap_event(manifest, manifest.nodes[0], run_number=1)
            with o._state_lock:
                state = runtime._roadmap_state_locked(o, manifest)
                state["active"] = {
                    "node_id": "task-a",
                    "issue_number": 284,
                    "lane": "Implementation",
                    "run_number": 1,
                    "seeded_at": core._utc_now(),
                    "source_id": event.source_id,
                    "event": event.to_state(),
                    "pr_number": None,
                }
                o.state.data["managed"] = {
                    "Implementation": {
                        "branch": "codex/implementation-task-284-test",
                        "pr_number": 490,
                        "authority_key": "task:284",
                        "authority_issue": 284,
                    }
                }
                o.state.save()

            def fake_json(args, **kwargs):
                if args[:3] == ["gh", "pr", "view"]:
                    return {"state": "OPEN", "mergedAt": None}
                raise AssertionError(args)

            with (
                mock.patch.object(runtime, "_roadmap_manifest", return_value=manifest),
                mock.patch.object(core, "_json_cmd", side_effect=fake_json),
            ):
                seeded = runtime._roadmap_maybe_advance(o, trigger="post-handoff")

            self.assertFalse(seeded)
            self.assertEqual(o.state.data["roadmap"]["active"]["pr_number"], 490)
            o.enqueue.assert_not_called()


if __name__ == "__main__":
    unittest.main()
