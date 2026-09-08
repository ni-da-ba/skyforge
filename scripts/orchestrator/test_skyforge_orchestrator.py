import importlib.util
import pathlib
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("skyforge_orchestrator.py")
SPEC = importlib.util.spec_from_file_location("skyforge_orchestrator", MODULE_PATH)
assert SPEC and SPEC.loader
orch = importlib.util.module_from_spec(SPEC)
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


if __name__ == "__main__":
    unittest.main()
