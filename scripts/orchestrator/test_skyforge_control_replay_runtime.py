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

runtime = importlib.import_module("skyforge_control_replay_runtime")
core = runtime.core


def completed(args, stdout=""):
    return subprocess.CompletedProcess(args, 0, stdout=stdout, stderr="")


class ControlReplayRuntimeTests(unittest.TestCase):
    def make_orchestrator(self, root: pathlib.Path):
        o = core.Orchestrator.__new__(core.Orchestrator)
        o.root = root
        o.repo = "ni-da-ba/skyforge"
        o.state = core.LocalState(root / "state.json")
        o._state_lock = threading.RLock()
        o._dispatch_lock = threading.Lock()
        o.auto_merge = False
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

    def test_new_task_cannot_reuse_unrelated_lane_pr(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            o = self.make_orchestrator(root)
            old = {
                "branch": "codex/implementation-old",
                "pr_number": 465,
            }
            o.state.data["managed"] = {"Implementation": old}
            o.state.data["pending_decision"] = {
                "task_issue_numbers": [467],
                "decision": {"decision": "DISPATCH", "lane": "Implementation"},
            }
            o.state.save()

            with (
                mock.patch.object(o, "_validated_managed_branch", return_value=old),
                mock.patch.object(
                    o,
                    "_ensure_worker_worktree",
                    return_value=root / "new-worker",
                ),
            ):
                branch, managed_pr, worktree = runtime._prepare_worker_branch(
                    o,
                    "Implementation",
                    None,
                )

            self.assertIsNone(managed_pr)
            self.assertIn("implementation-task-467-", branch)
            self.assertEqual(worktree, root / "new-worker")
            self.assertEqual(
                o.state.data["metrics"]["cross_authority_pr_reuse_prevented"],
                1,
            )

    def test_same_task_authority_reuses_its_managed_pr(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            o = self.make_orchestrator(root)
            managed = {
                "branch": "codex/implementation-task-467-existing",
                "pr_number": 481,
                "authority_key": "task:467",
            }
            o.state.data["managed"] = {"Implementation": managed}
            o.state.data["pending_decision"] = {
                "task_issue_numbers": [467],
                "decision": {"decision": "DISPATCH", "lane": "Implementation"},
            }
            o.state.save()

            with (
                mock.patch.object(o, "_validated_managed_branch", return_value=managed),
                mock.patch.object(
                    o,
                    "_ensure_worker_worktree",
                    return_value=root / "existing-worker",
                ),
                mock.patch.object(core, "_run", return_value=completed(["git"])),
            ):
                branch, managed_pr, _ = runtime._prepare_worker_branch(
                    o,
                    "Implementation",
                    None,
                )

            self.assertEqual(branch, managed["branch"])
            self.assertEqual(managed_pr, 481)

    def test_explicit_ci_repair_target_reuses_matching_managed_pr(self):
        self.assertTrue(
            runtime._managed_record_matches_authority(
                {"pr_number": 481, "authority_key": "task:467"},
                authority_key="pr:481",
                source_pr=481,
            )
        )

    def test_machine_only_implementation_task_is_auto_merge_candidate(self):
        self.assertTrue(
            runtime._machine_only_auto_merge_candidate(
                lane="Implementation",
                decision={
                    "objective": "Implement deterministic persistence and exactly-once cargo custody",
                    "stop_boundary": "Focused tests and ordinary CI green",
                },
                paths=["skyforge-neoforge-1211/src/main/java/example/A.java"],
                authority_key="task:467",
            )
        )

    def test_human_or_visual_gate_is_never_auto_merge_candidate(self):
        self.assertFalse(
            runtime._machine_only_auto_merge_candidate(
                lane="Implementation",
                decision={
                    "objective": "Prepare exact owner review specimen",
                    "stop_boundary": "Stop for visual review",
                },
                paths=["skyforge-neoforge-1211/src/main/java/example/A.java"],
                authority_key="task:214",
            )
        )

    def test_review_assets_and_nonimplementation_lanes_are_not_auto_mergeable(self):
        self.assertFalse(
            runtime._machine_only_auto_merge_candidate(
                lane="Implementation",
                decision={"objective": "Update packet", "stop_boundary": "CI green"},
                paths=["docs/reviews/HS-03.md"],
                authority_key="task:214",
            )
        )
        self.assertFalse(
            runtime._machine_only_auto_merge_candidate(
                lane="Content",
                decision={"objective": "Machine-edit text", "stop_boundary": "CI green"},
                paths=["docs/content.md"],
                authority_key="task:999",
            )
        )

    def seed_managed_merge(self, o, *, eligible=True, expected_head="abc123"):
        o.auto_merge = True
        o.state.data["pending_worker"] = None
        o.state.data["managed"] = {
            "Implementation": {
                "branch": "codex/implementation-task-467-test",
                "pr_number": 481,
                "authority_key": "task:467",
                "authority_issue": 467,
                "expected_head": expected_head,
                "changed_paths": ["src/A.java"],
                "auto_merge_eligible": eligible,
            }
        }
        o.state.save()

    def green_pr(self, *, head="abc123", draft=False):
        return {
            "state": "OPEN",
            "isDraft": draft,
            "mergeStateStatus": "CLEAN",
            "headRefOid": head,
            "headRefName": "codex/implementation-task-467-test",
            "baseRefName": "main",
            "reviewDecision": "",
            "statusCheckRollup": [
                {
                    "name": "CI",
                    "status": "COMPLETED",
                    "conclusion": "SUCCESS",
                }
            ],
        }

    def test_safe_auto_merge_uses_exact_head_compare_and_swap(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            self.seed_managed_merge(o)
            calls = []

            def fake_run(args, **kwargs):
                calls.append(list(args))
                if args[:3] == ["gh", "pr", "diff"]:
                    return completed(args, "src/A.java\n")
                return completed(args)

            with (
                mock.patch.object(core, "_json_cmd", return_value=self.green_pr()),
                mock.patch.object(core, "_run", side_effect=fake_run),
            ):
                runtime._merge_managed(o, "Implementation", 481)

            merge_calls = [
                args
                for args in calls
                if args[:3] == ["gh", "pr", "merge"]
            ]
            self.assertEqual(len(merge_calls), 1)
            self.assertIn("--match-head-commit", merge_calls[0])
            self.assertIn("abc123", merge_calls[0])
            self.assertNotIn("Implementation", o.state.data.get("managed", {}))
            self.assertEqual(o.state.data["metrics"]["safe_auto_merges"], 1)

    def test_auto_merge_rejects_unexpected_head_movement(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            self.seed_managed_merge(o, expected_head="abc123")

            with mock.patch.object(
                core,
                "_json_cmd",
                return_value=self.green_pr(head="def456"),
            ):
                with self.assertRaisesRegex(RuntimeError, "unexpected PR head movement"):
                    runtime._merge_managed(o, "Implementation", 481)

    def test_ineligible_managed_pr_becomes_human_gate_not_merge(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            self.seed_managed_merge(o, eligible=False)

            with mock.patch.object(o, "_post_gate", return_value=True) as gate:
                runtime._merge_managed(o, "Implementation", 481)

            gate.assert_called_once()
            self.assertIn("Implementation", o.state.data["managed"])

    def test_changed_path_drift_blocks_auto_merge(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            self.seed_managed_merge(o)

            def fake_run(args, **kwargs):
                if args[:3] == ["gh", "pr", "diff"]:
                    return completed(args, "src/A.java\nsrc/Unexpected.java\n")
                return completed(args)

            with (
                mock.patch.object(core, "_json_cmd", return_value=self.green_pr()),
                mock.patch.object(core, "_run", side_effect=fake_run),
            ):
                with self.assertRaisesRegex(RuntimeError, "changed-path drift"):
                    runtime._merge_managed(o, "Implementation", 481)


if __name__ == "__main__":
    unittest.main()
