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

runtime = importlib.import_module("skyforge_managed_pr_no_change_runtime")
core = runtime.core


class ManagedPrNoChangeRuntimeTests(unittest.TestCase):
    def make_orchestrator(self, root: pathlib.Path):
        o = core.Orchestrator.__new__(core.Orchestrator)
        o.root = root
        o.repo = "ni-da-ba/skyforge"
        o.state = core.LocalState(root / "state.json")
        o._state_lock = threading.RLock()
        o._dispatch_lock = threading.Lock()
        o._metric = mock.Mock()
        o.enqueue = mock.Mock()
        o._post_gate = mock.Mock(return_value=True)
        o._pending_events = mock.Mock(return_value=[])
        return o

    def failed_event(self):
        return core.EventDecision(
            actionable=True,
            reason="CI completed with conclusion=failure; controller will require head quiescence",
            event="workflow_run",
            action="completed",
            head_sha="a" * 40,
            pr_number=500,
        )

    def test_single_failed_managed_pr_no_change_gets_one_bounded_retry(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            event = self.failed_event()
            with mock.patch.object(
                runtime,
                "_ORIGINAL_SCHEDULE_NO_CHANGE_FOLLOWUP",
                return_value=False,
            ):
                self.assertTrue(runtime.schedule_no_change_followup(o, [event]))

            o.enqueue.assert_called_once()
            retry = o.enqueue.call_args.args[0]
            self.assertEqual(retry.event, "reconcile")
            self.assertEqual(retry.action, "audit_signal")
            self.assertEqual(retry.pr_number, 500)
            self.assertEqual(retry.head_sha, "a" * 40)
            self.assertEqual(retry.signal_kind, "restart_recommended")
            self.assertTrue(retry.source_id.startswith(runtime.RETRY_SOURCE_PREFIX))
            self.assertIn("Do not silently NOOP", retry.signal_text)
            o._metric.assert_any_call("managed_pr_no_change_retries")

    def test_second_no_change_surfaces_path_local_gate_instead_of_silence(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            o.state.data["managed"] = {
                "Implementation": {"pr_number": 500, "branch": "codex/example"}
            }
            o.state.save()
            retry = runtime._retry_event(self.failed_event())

            with mock.patch.object(
                runtime,
                "_ORIGINAL_SCHEDULE_NO_CHANGE_FOLLOWUP",
                return_value=False,
            ):
                self.assertTrue(runtime.schedule_no_change_followup(o, [retry]))

            o.enqueue.assert_not_called()
            o._post_gate.assert_called_once()
            gate = o._post_gate.call_args.args[0]
            self.assertEqual(gate["decision"], "HUMAN_GATE")
            self.assertEqual(gate["lane"], "Implementation")
            self.assertEqual(gate["pr_number"], 500)
            self.assertIn("two autonomous repair dispatches", gate["human_message"])
            o._metric.assert_any_call("managed_pr_no_change_escalations")

    def test_ordinary_single_event_preserves_generic_no_followup_rule(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            ordinary = core.EventDecision(
                actionable=True,
                reason="main advanced",
                event="push",
                head_sha="b" * 40,
            )
            with mock.patch.object(
                runtime,
                "_ORIGINAL_SCHEDULE_NO_CHANGE_FOLLOWUP",
                return_value=False,
            ):
                self.assertFalse(runtime.schedule_no_change_followup(o, [ordinary]))

            o.enqueue.assert_not_called()
            o._post_gate.assert_not_called()


if __name__ == "__main__":
    unittest.main()
