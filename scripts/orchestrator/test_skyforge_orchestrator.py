import importlib.util
import pathlib
import sys
import tempfile
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).with_name("skyforge_orchestrator.py")
SPEC = importlib.util.spec_from_file_location("skyforge_orchestrator", MODULE_PATH)
assert SPEC and SPEC.loader
orch = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = orch
SPEC.loader.exec_module(orch)


class EventFilterTests(unittest.TestCase):
    def test_main_push_is_actionable(self):
        d = orch.classify_event("push", {"ref": "refs/heads/main", "after": "abc123"})
        self.assertTrue(d.actionable)
        self.assertEqual(d.head_sha, "abc123")

    def test_non_main_push_is_ignored(self):
        d = orch.classify_event("push", {"ref": "refs/heads/feature", "after": "abc123"})
        self.assertFalse(d.actionable)

    def test_pr_synchronize_is_ignored_to_wait_for_ci(self):
        d = orch.classify_event(
            "pull_request",
            {
                "action": "synchronize",
                "number": 42,
                "pull_request": {"head": {"sha": "deadbeef"}},
            },
        )
        self.assertFalse(d.actionable)
        self.assertIn("wait for workflow", d.reason)

    def test_pr_closed_is_actionable(self):
        d = orch.classify_event(
            "pull_request",
            {"action": "closed", "number": 42, "pull_request": {"head": {"sha": "deadbeef"}}},
        )
        self.assertTrue(d.actionable)
        self.assertEqual(d.pr_number, 42)

    def test_pr_ready_for_review_is_actionable(self):
        d = orch.classify_event(
            "pull_request",
            {"action": "ready_for_review", "number": 43, "pull_request": {"head": {"sha": "feedface"}}},
        )
        self.assertTrue(d.actionable)

    def test_pr_opened_waits_for_ci(self):
        d = orch.classify_event(
            "pull_request",
            {"action": "opened", "number": 44, "pull_request": {"head": {"sha": "feedface"}}},
        )
        self.assertFalse(d.actionable)

    def test_workflow_completed_is_actionable(self):
        d = orch.classify_event(
            "workflow_run",
            {
                "action": "completed",
                "workflow_run": {
                    "head_sha": "cafebabe",
                    "pull_requests": [{"number": 45}],
                },
            },
        )
        self.assertTrue(d.actionable)
        self.assertEqual(d.head_sha, "cafebabe")
        self.assertEqual(d.pr_number, 45)

    def test_workflow_noncompleted_is_ignored(self):
        d = orch.classify_event(
            "workflow_run",
            {"action": "in_progress", "workflow_run": {"head_sha": "cafebabe"}},
        )
        self.assertFalse(d.actionable)

    def test_audit_comment_wakes(self):
        d = orch.classify_event(
            "issue_comment",
            {
                "action": "created",
                "issue": {"number": 285},
                "comment": {"body": "AUDIT: RESTART RECOMMENDED"},
            },
        )
        self.assertTrue(d.actionable)
        self.assertEqual(d.pr_number, 285)

    def test_manual_command_wakes(self):
        d = orch.classify_event(
            "issue_comment",
            {
                "action": "created",
                "issue": {"number": 349},
                "comment": {"body": "/skyforge-orchestrate"},
            },
        )
        self.assertTrue(d.actionable)

    def test_ordinary_comment_is_ignored(self):
        d = orch.classify_event(
            "issue_comment",
            {
                "action": "created",
                "issue": {"number": 349},
                "comment": {"body": "Looks good to me."},
            },
        )
        self.assertFalse(d.actionable)

    def test_controller_comment_is_ignored_even_with_gate_words(self):
        d = orch.classify_event(
            "issue_comment",
            {
                "action": "created",
                "issue": {"number": 349},
                "comment": {"body": "[skyforge-orchestrator] HUMAN_GATE"},
            },
        )
        self.assertFalse(d.actionable)


class ClassifierJsonTests(unittest.TestCase):
    def test_plain_json(self):
        value = orch._clean_json_object('{"decision":"NOOP","reason":"idle"}')
        self.assertEqual(value["decision"], "NOOP")

    def test_fenced_json(self):
        value = orch._clean_json_object('''```json
{"decision":"DISPATCH","reason":"work"}
```''')
        self.assertEqual(value["decision"], "DISPATCH")


class FailurePolicyTests(unittest.TestCase):
    def test_quota_failure_is_long_backoff(self):
        kind, delay = orch._codex_failure_policy(RuntimeError("Usage limit reached for Codex"))
        self.assertEqual(kind, "quota")
        self.assertGreaterEqual(delay, 60)

    def test_rate_limit_failure_is_shorter_backoff(self):
        kind, delay = orch._codex_failure_policy(RuntimeError("429 rate limit exceeded"))
        self.assertEqual(kind, "rate_limit")
        self.assertGreaterEqual(delay, 30)

    def test_unknown_failure_is_transient(self):
        kind, delay = orch._codex_failure_policy(RuntimeError("socket disappeared"))
        self.assertEqual(kind, "transient")
        self.assertGreaterEqual(delay, 30)


class DurableStateTests(unittest.TestCase):
    def make_orchestrator(self, root: pathlib.Path):
        return orch.Orchestrator(
            root,
            repo="ni-da-ba/skyforge",
            debounce_seconds=999,
            min_dispatch_seconds=0,
            max_parent_turns=24,
            auto_merge=False,
        )

    def test_event_state_round_trip(self):
        event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
        restored = orch.EventDecision.from_state(event.to_state())
        self.assertEqual(restored, event)
        self.assertEqual(orch._event_key(restored), orch._event_key(event))

    def test_pending_events_are_durable_and_deduplicated(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            o = self.make_orchestrator(root)
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([event, event])
            self.assertEqual(o._pending_events(), [event])

            reloaded = self.make_orchestrator(root)
            self.assertEqual(reloaded._pending_events(), [event])

    def test_new_event_invalidates_cached_decision_when_no_worker_active(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            first = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([first])
            o._cache_decision({"decision": "NOOP", "reason": "idle"}, [first])
            self.assertIsNotNone(o._decision_record())

            second = orch.EventDecision(True, "main advanced", "push", head_sha="def456")
            o._persist_pending_events([second])
            self.assertIsNone(o._decision_record())

    def test_new_event_preserves_inflight_worker_decision(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            first = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([first])
            o._cache_decision({"decision": "DISPATCH", "lane": "Audit", "reason": "work"}, [first])
            o.state.data["pending_worker"] = {"branch": "codex/audit-test"}
            o.state.save()

            second = orch.EventDecision(True, "main advanced", "push", head_sha="def456")
            o._persist_pending_events([second])
            self.assertIsNotNone(o._decision_record())

    def test_completed_decision_removes_only_its_event_batch(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            first = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            second = orch.EventDecision(True, "main advanced", "push", head_sha="def456")
            o._persist_pending_events([first])
            o._cache_decision({"decision": "DISPATCH", "lane": "Audit", "reason": "work"}, [first])
            o.state.data["pending_worker"] = {"branch": "codex/audit-test"}
            o.state.save()
            o._persist_pending_events([second])

            o._clear_completed_decision()
            self.assertEqual(o._pending_events(), [second])
            if o._timer is not None:
                o._timer.cancel()

    def test_enqueue_journals_actionable_event_before_dispatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o.enqueue(event)
            try:
                self.assertEqual(o._pending_events(), [event])
                reloaded = self.make_orchestrator(pathlib.Path(tmp))
                self.assertEqual(reloaded._pending_events(), [event])
            finally:
                if o._timer is not None:
                    o._timer.cancel()

    def test_restart_invalidates_cached_nonworker_decision(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([event])
            o._cache_decision({"decision": "NOOP", "reason": "old snapshot"}, [event])
            self.assertIsNotNone(o._decision_record())

            o.resume_pending()
            try:
                self.assertIsNone(o._decision_record())
                self.assertEqual(o._pending_events(), [event])
            finally:
                if o._timer is not None:
                    o._timer.cancel()

    def test_worker_handoff_stage_is_durable(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            o = self.make_orchestrator(root)
            o.state.data["pending_worker"] = {
                "lane": "Audit",
                "branch": "codex/audit-test",
                "stage": "editing",
            }
            o.state.save()

            o._mark_worker_handoff("tests passed")
            reloaded = self.make_orchestrator(root)
            pending = reloaded.state.data["pending_worker"]
            self.assertEqual(pending["stage"], "handoff")
            self.assertEqual(pending["worker_summary"], "tests passed")

    def test_handoff_reuses_existing_open_pr_after_interruption(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.state.data["pending_worker"] = {
                "lane": "Audit",
                "branch": "codex/audit-test",
                "stage": "handoff",
                "worker_summary": "done",
            }
            o.state.save()

            def fake_run(args, **kwargs):
                if args[:3] == ["git", "rev-list", "--count"]:
                    return orch.subprocess.CompletedProcess(args, 0, stdout="1\n", stderr="")
                return orch.subprocess.CompletedProcess(args, 0, stdout="", stderr="")

            with mock.patch.object(o, "_changed_paths", return_value=[]), \
                    mock.patch.object(orch, "_run", side_effect=fake_run), \
                    mock.patch.object(orch, "_json_cmd", return_value=[{"number": 77}]):
                o._handoff_changes("Audit", "durable handoff", "codex/audit-test", None, "done")

            self.assertEqual(o.state.data["managed"]["Audit"]["pr_number"], 77)
            self.assertEqual(o.state.data["managed"]["Audit"]["branch"], "codex/audit-test")

    def test_local_budget_blocks_without_spending_beyond_limit(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            old = orch.os.environ.get("SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY")
            orch.os.environ["SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY"] = "1"
            try:
                o._consume_budget("worker")
                with self.assertRaises(orch.RetryBlocked) as ctx:
                    o._consume_budget("worker")
                self.assertEqual(ctx.exception.kind, "local_budget")
                self.assertEqual(o.state.data["worker_calls_today"], 1)
            finally:
                if old is None:
                    orch.os.environ.pop("SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY", None)
                else:
                    orch.os.environ["SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY"] = old


if __name__ == "__main__":
    unittest.main()
