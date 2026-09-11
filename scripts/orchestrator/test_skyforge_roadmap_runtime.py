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
runtime = importlib.import_module("skyforge_roadmap_runtime")
core = runtime.core


def manifest_payload(nodes, *, limit=6):
    return {
        "schema_version": 1,
        "roadmap_id": "test-roadmap",
        "enabled": True,
        "max_auto_claims_per_utc_day": limit,
        "nodes": nodes,
    }


def task_node(node_id="task-a", issue=284, priority=100, prerequisites=None, max_runs=1):
    return {
        "id": node_id,
        "kind": "task",
        "lane": "Implementation",
        "issue_number": issue,
        "priority": priority,
        "max_runs": max_runs,
        "prerequisites": prerequisites or [],
        "objective_hint": "Advance the next still-unaccepted machine-verifiable tranche.",
        "stop_boundary": "Stop before any human/product/manual gate.",
    }


class RoadmapPolicyTests(unittest.TestCase):
    def test_manifest_requires_issue_backed_v1_tasks(self):
        raw = task_node()
        raw.pop("issue_number")
        with self.assertRaisesRegex(policy.RoadmapError, "issue-backed"):
            policy.parse_manifest(manifest_payload([raw]))

    def test_selector_respects_priority_and_prerequisites(self):
        manifest = policy.parse_manifest(
            manifest_payload(
                [
                    task_node("first", issue=284, priority=50),
                    task_node("second", issue=466, priority=100, prerequisites=["first"]),
                    task_node("parallel", issue=467, priority=80),
                ]
            )
        )
        self.assertEqual(policy.select_next_node(manifest).node_id, "parallel")
        selected = policy.select_next_node(manifest, completed_runs={"first": 1})
        self.assertEqual(selected.node_id, "second")

    def test_blocked_and_completed_nodes_are_not_selected(self):
        manifest = policy.parse_manifest(
            manifest_payload(
                [
                    task_node("a", issue=284, priority=100),
                    task_node("b", issue=466, priority=90),
                ]
            )
        )
        selected = policy.select_next_node(
            manifest,
            completed_runs={"a": 1},
            blocked_nodes={"b"},
        )
        self.assertIsNone(selected)

    def test_gate_requires_human_message(self):
        with self.assertRaisesRegex(policy.RoadmapError, "human_message"):
            policy.parse_manifest(
                manifest_payload(
                    [
                        {
                            "id": "gate",
                            "kind": "gate",
                            "lane": None,
                            "priority": 1,
                            "prerequisites": [],
                        }
                    ]
                )
            )


class RoadmapRuntimeTests(unittest.TestCase):
    def make_orchestrator(self, root: pathlib.Path):
        o = core.Orchestrator.__new__(core.Orchestrator)
        o.root = root
        o.repo = "ni-da-ba/skyforge"
        o.state = core.LocalState(root / "state.json")
        o._state_lock = threading.RLock()
        o._dispatch_lock = threading.Lock()
        o.auto_merge = True
        o.is_paused = mock.Mock(return_value=False)
        o.enqueue = mock.Mock()
        o._post_gate = mock.Mock(return_value=True)
        return o

    def manifest(self, nodes=None, *, limit=6):
        return policy.parse_manifest(
            manifest_payload(nodes or [task_node()], limit=limit)
        )

    def test_idle_controller_seeds_one_bounded_task(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            manifest = self.manifest()
            with (
                mock.patch.object(runtime, "_roadmap_manifest", return_value=manifest),
                mock.patch.object(runtime, "_roadmap_live_task_prs", return_value=[]),
                mock.patch.object(runtime, "_roadmap_issue_open", return_value=True),
            ):
                seeded = runtime._roadmap_maybe_advance(o, trigger="test")

            self.assertTrue(seeded)
            o.enqueue.assert_called_once()
            event = o.enqueue.call_args.args[0]
            self.assertEqual(event.signal_kind, "task")
            self.assertEqual(event.pr_number, 284)
            self.assertIn("ROADMAP TASK AUTHORITY", event.signal_text)
            state = o.state.data["roadmap"]
            self.assertEqual(state["active"]["node_id"], "task-a")
            self.assertEqual(state["claims_today"], 1)

    def test_existing_open_task_owned_pr_prevents_parallel_seed(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            with mock.patch.object(runtime, "_roadmap_live_task_prs", return_value=[483]):
                seeded = runtime._roadmap_maybe_advance(o, trigger="test")
            self.assertFalse(seeded)
            o.enqueue.assert_not_called()

    def test_closed_issue_is_skipped_as_completed(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            manifest = self.manifest()
            with (
                mock.patch.object(runtime, "_roadmap_manifest", return_value=manifest),
                mock.patch.object(runtime, "_roadmap_live_task_prs", return_value=[]),
                mock.patch.object(runtime, "_roadmap_issue_open", return_value=False),
            ):
                seeded = runtime._roadmap_maybe_advance(o, trigger="test")
            self.assertFalse(seeded)
            self.assertEqual(o.state.data["roadmap"]["completed_runs"]["task-a"], 1)
            o.enqueue.assert_not_called()

    def test_daily_claim_bound_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            manifest = self.manifest(limit=1)
            with o._state_lock:
                state = runtime._roadmap_state_locked(o, manifest)
                state["claims_day"] = core._utc_day()
                state["claims_today"] = 1
                o.state.save()

            with (
                mock.patch.object(runtime, "_roadmap_manifest", return_value=manifest),
                mock.patch.object(runtime, "_roadmap_live_task_prs", return_value=[]),
                mock.patch.object(runtime, "_roadmap_issue_open", return_value=True),
            ):
                seeded = runtime._roadmap_maybe_advance(o, trigger="test")

            self.assertFalse(seeded)
            self.assertEqual(
                o.state.data["roadmap"]["last_error"]["kind"],
                "RoadmapDailyBound",
            )

    def test_active_merged_pr_completes_node_without_duplicate_dispatch(self):
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
                    "pr_number": 490,
                }
                o.state.save()

            with (
                mock.patch.object(runtime, "_roadmap_manifest", return_value=manifest),
                mock.patch.object(runtime, "_roadmap_live_task_prs", return_value=[]),
                mock.patch.object(
                    core,
                    "_json_cmd",
                    return_value={"state": "MERGED", "mergedAt": "2026-09-11T12:00:00Z"},
                ),
            ):
                seeded = runtime._roadmap_maybe_advance(o, trigger="test")

            self.assertFalse(seeded)
            self.assertEqual(o.state.data["roadmap"]["completed_runs"]["task-a"], 1)
            self.assertIsNone(o.state.data["roadmap"]["active"])
            o.enqueue.assert_not_called()

    def test_completed_authority_without_pr_blocks_node_instead_of_looping(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            manifest = self.manifest()
            event = runtime._roadmap_event(manifest, manifest.nodes[0], run_number=1)
            key = core._event_key(event)
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
                o.state.data["completed_authority_event_keys"] = [key]
                o.state.save()

            with (
                mock.patch.object(runtime, "_roadmap_manifest", return_value=manifest),
                mock.patch.object(runtime, "_roadmap_live_task_prs", return_value=[]),
            ):
                seeded = runtime._roadmap_maybe_advance(o, trigger="test")

            self.assertFalse(seeded)
            self.assertIn("task-a", o.state.data["roadmap"]["blocked_nodes"])
            o.enqueue.assert_not_called()

    def test_gate_node_posts_gate_without_model_dispatch(self):
        gate_manifest = policy.parse_manifest(
            manifest_payload(
                [
                    {
                        "id": "human-stop",
                        "kind": "gate",
                        "lane": "Implementation",
                        "priority": 100,
                        "max_runs": 1,
                        "prerequisites": [],
                        "human_message": "Review the representative morphology corpus.",
                    }
                ]
            )
        )
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            with (
                mock.patch.object(runtime, "_roadmap_manifest", return_value=gate_manifest),
                mock.patch.object(runtime, "_roadmap_live_task_prs", return_value=[]),
            ):
                seeded = runtime._roadmap_maybe_advance(o, trigger="test")
            self.assertFalse(seeded)
            o._post_gate.assert_called_once()
            o.enqueue.assert_not_called()
            self.assertIn("human-stop", o.state.data["roadmap"]["blocked_nodes"])

    def test_health_exposes_bounded_roadmap_state(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            with mock.patch.object(runtime, "_ORIGINAL_HEALTH_SNAPSHOT", return_value={"status": "ok"}):
                snap = runtime.health_snapshot(o)
            self.assertTrue(snap["bounded_roadmap"]["enabled"])
            self.assertEqual(snap["bounded_roadmap"]["manifest_path"], runtime.MANIFEST_PATH)

    def test_runtime_files_are_refresh_tracked_and_manifest_is_worker_protected(self):
        self.assertIn(runtime.RUNTIME_PATH, core.CONTROLLER_RUNTIME_PATHS)
        self.assertIn(runtime.POLICY_PATH, core.CONTROLLER_RUNTIME_PATHS)
        self.assertIn(runtime.MANIFEST_PATH, core.PROTECTED_WORKER_PATHS)
        self.assertIn(runtime.PROGRAM_ROADMAP_PATH, core.PROTECTED_WORKER_PATHS)


if __name__ == "__main__":
    unittest.main()
