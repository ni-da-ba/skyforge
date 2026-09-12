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
fairness = runtime._pending_timer_fairness_runtime


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


class PendingTimerFairnessIntegrationTests(unittest.TestCase):
    class FakeTimer:
        def __init__(self, delay, callback):
            self.delay = delay
            self.callback = callback
            self.daemon = False
            self.started = False
            self.cancelled = False

        def start(self):
            self.started = True

        def cancel(self):
            self.cancelled = True

        def is_alive(self):
            return self.started and not self.cancelled

    def make_orchestrator(self):
        o = core.Orchestrator.__new__(core.Orchestrator)
        o._timer_lock = threading.Lock()
        o._timer = None
        o._timer_due_epoch = None
        o._timer_scheduled_at = None
        o._timer_last_fired_at = None
        o._pending_timer_callback = mock.Mock()
        o._metric = mock.Mock()
        return o

    @staticmethod
    def ordinary_event(event, *, head=None, pr_number=None):
        return core.EventDecision(
            True,
            "test ordinary event",
            event,
            action="completed",
            pr_number=pr_number,
            head_sha=head,
        )

    def test_later_event_cannot_postpone_but_earlier_event_can_pull_forward(self):
        o = self.make_orchestrator()
        with mock.patch.object(fairness.threading, "Timer", self.FakeTimer), mock.patch.object(
            fairness.time, "time", side_effect=[100.0, 101.0, 102.0]
        ):
            o._schedule_pending(25)
            first = o._timer
            first_due = o._timer_due_epoch

            o._schedule_pending(40)
            self.assertIs(o._timer, first)
            self.assertEqual(o._timer_due_epoch, first_due)
            self.assertFalse(first.cancelled)

            o._schedule_pending(5)
            second = o._timer

        self.assertIsNot(second, first)
        self.assertTrue(first.cancelled)
        self.assertEqual(second.delay, 5.0)
        self.assertEqual(o._timer_due_epoch, 107.0)
        o._metric.assert_called_once_with("pending_timer_postpone_suppressed")

    def test_dead_timer_is_replaceable_and_runtime_path_is_refresh_tracked(self):
        o = self.make_orchestrator()
        with mock.patch.object(fairness.threading, "Timer", self.FakeTimer), mock.patch.object(
            fairness.time, "time", side_effect=[100.0, 150.0]
        ):
            o._schedule_pending(25)
            first = o._timer
            first.cancel()
            o._schedule_pending(40)
            second = o._timer

        self.assertIsNot(second, first)
        self.assertEqual(second.delay, 40.0)
        self.assertEqual(o._timer_due_epoch, 190.0)
        self.assertIn(fairness.RUNTIME_PATH, core.CONTROLLER_RUNTIME_PATHS)

    def test_ready_ordinary_heads_are_isolated_from_unrelated_active_heads(self):
        o = self.make_orchestrator()
        active_head = "a" * 40
        ready_head = "b" * 40
        pending = [
            self.ordinary_event("workflow_run", head=active_head, pr_number=489),
            self.ordinary_event("reconcile", head=ready_head),
            self.ordinary_event("workflow_run", head=ready_head, pr_number=507),
            self.ordinary_event("issue_comment"),
        ]
        o.workflows_quiescent = mock.Mock(side_effect=lambda head: head == ready_head)

        selected = fairness.select_dispatch_batch(o, pending)

        self.assertEqual(selected, pending[1:3])
        self.assertEqual(
            o.workflows_quiescent.call_args_list,
            [mock.call(active_head), mock.call(ready_head)],
        )
        o._metric.assert_has_calls(
            [
                mock.call("ordinary_quiescent_batch_splits"),
                mock.call("ordinary_active_events_deferred", 1),
            ]
        )

    def test_each_unique_head_is_checked_once_when_splitting_batch(self):
        o = self.make_orchestrator()
        active_head = "a" * 40
        ready_head = "b" * 40
        pending = [
            self.ordinary_event("workflow_run", head=active_head, pr_number=489),
            self.ordinary_event("pull_request", head=active_head, pr_number=489),
            self.ordinary_event("workflow_run", head=ready_head, pr_number=507),
            self.ordinary_event("pull_request", head=ready_head, pr_number=507),
        ]
        o.workflows_quiescent = mock.Mock(side_effect=lambda head: head == ready_head)

        selected = fairness.select_dispatch_batch(o, pending)

        self.assertEqual(selected, pending[2:])
        self.assertEqual(o.workflows_quiescent.call_count, 2)

    def test_all_ready_or_all_blocked_preserves_original_coalesced_batch(self):
        ready = self.make_orchestrator()
        blocked = self.make_orchestrator()
        first_head = "a" * 40
        second_head = "b" * 40
        pending = [
            self.ordinary_event("workflow_run", head=first_head, pr_number=516),
            self.ordinary_event("workflow_run", head=second_head, pr_number=517),
            self.ordinary_event("issue_comment"),
        ]
        ready.workflows_quiescent = mock.Mock(return_value=True)
        blocked.workflows_quiescent = mock.Mock(return_value=False)

        self.assertEqual(fairness.select_dispatch_batch(ready, pending), pending)
        self.assertEqual(fairness.select_dispatch_batch(blocked, pending), pending)
        ready._metric.assert_not_called()
        blocked._metric.assert_not_called()

    def test_protected_authority_keeps_one_at_a_time_precedence(self):
        o = self.make_orchestrator()
        ordinary = self.ordinary_event("workflow_run", head="a" * 40, pr_number=489)
        task = core.EventDecision(
            True,
            "explicit task",
            "roadmap",
            action="advance",
            pr_number=491,
            source_id="roadmap:test:run:3",
            signal_kind="task",
            signal_text="bounded task authority",
        )
        o.workflows_quiescent = mock.Mock(return_value=True)

        selected = fairness.select_dispatch_batch(o, [ordinary, task])

        self.assertEqual(selected, [task])
        o.workflows_quiescent.assert_not_called()
        o._metric.assert_not_called()


if __name__ == "__main__":
    unittest.main()
