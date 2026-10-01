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
roadmap_runtime = importlib.import_module("skyforge_roadmap_runtime")
recovery = importlib.import_module("skyforge_roadmap_closed_issue_recovery_runtime")
core = roadmap_runtime.core


def manifest(max_runs=2):
    return policy.parse_manifest(
        {
            "schema_version": 1,
            "roadmap_id": "test-roadmap",
            "enabled": True,
            "max_auto_claims_per_utc_day": 4,
            "nodes": [
                {
                    "id": "phase1-closeout",
                    "kind": "task",
                    "lane": "Implementation",
                    "issue_number": 284,
                    "priority": 100,
                    "max_runs": max_runs,
                    "prerequisites": [],
                    "objective_hint": "Finish bounded machine closeout.",
                    "stop_boundary": "Stop at the human gate.",
                }
            ],
        }
    )


class ClosedActiveIssueRecoveryTests(unittest.TestCase):
    def make_orchestrator(self, root: pathlib.Path):
        o = core.Orchestrator.__new__(core.Orchestrator)
        o.root = root
        o.repo = "ni-da-ba/skyforge"
        o.state = core.LocalState(root / "state.json")
        o._state_lock = threading.RLock()
        o._dispatch_lock = threading.Lock()
        o._metric = mock.Mock()
        return o

    def test_closed_active_issue_without_pr_completes_full_node(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            m = manifest(max_runs=2)
            state = {
                "roadmap_id": m.roadmap_id,
                "manifest_fingerprint": m.fingerprint,
                "completed_runs": {},
                "blocked_nodes": {},
                "active": {
                    "node_id": "phase1-closeout",
                    "issue_number": 284,
                    "pr_number": None,
                },
            }
            o.state.data["roadmap"] = state
            o.state.save()

            with mock.patch.object(
                roadmap_runtime, "_roadmap_issue_open", return_value=False
            ):
                owns_work = recovery._resolve_active_closed_issue(o, m, state)

            self.assertFalse(owns_work)
            self.assertIsNone(state["active"])
            self.assertEqual(state["completed_runs"]["phase1-closeout"], 2)
            self.assertEqual(state["last_closed_active_issue"]["issue_number"], 284)
            o._metric.assert_called_with("roadmap_closed_active_issues_skipped")

    def test_open_active_issue_delegates_to_existing_resolution(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            m = manifest()
            state = {
                "active": {
                    "node_id": "phase1-closeout",
                    "issue_number": 284,
                    "pr_number": None,
                }
            }
            with (
                mock.patch.object(roadmap_runtime, "_roadmap_issue_open", return_value=True),
                mock.patch.object(
                    recovery, "_ORIGINAL_RESOLVE_ACTIVE", return_value=True
                ) as delegate,
            ):
                owns_work = recovery._resolve_active_closed_issue(o, m, state)

            self.assertTrue(owns_work)
            delegate.assert_called_once_with(o, m, state)

    def test_bound_pr_always_delegates_to_existing_pr_lifecycle(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            m = manifest()
            state = {
                "active": {
                    "node_id": "phase1-closeout",
                    "issue_number": 284,
                    "pr_number": 490,
                }
            }
            with mock.patch.object(
                recovery, "_ORIGINAL_RESOLVE_ACTIVE", return_value=True
            ) as delegate:
                owns_work = recovery._resolve_active_closed_issue(o, m, state)

            self.assertTrue(owns_work)
            delegate.assert_called_once_with(o, m, state)


if __name__ == "__main__":
    unittest.main()
