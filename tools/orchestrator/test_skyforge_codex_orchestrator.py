import hashlib
import hmac
import unittest

from app_server_controller import (
    build_orchestrator_prompt,
    classify_webhook,
    verify_github_signature,
)


class SignatureTests(unittest.TestCase):
    def test_valid_signature(self):
        secret = b"test-secret"
        body = b'{"hello":"world"}'
        signature = "sha256=" + hmac.new(secret, body, hashlib.sha256).hexdigest()
        self.assertTrue(verify_github_signature(secret, body, signature))

    def test_invalid_signature(self):
        self.assertFalse(verify_github_signature(b"test-secret", b"{}", "sha256=bad"))


class ClassificationTests(unittest.TestCase):
    def base_repo(self):
        return {"repository": {"full_name": "ni-da-ba/skyforge"}}

    def test_non_skyforge_ignored(self):
        payload = {"repository": {"full_name": "someone/else"}, "action": "opened"}
        self.assertIsNone(classify_webhook("pull_request", payload, "d1"))

    def test_pr_synchronize_actionable(self):
        payload = self.base_repo()
        payload.update({
            "action": "synchronize",
            "number": 42,
            "pull_request": {
                "number": 42,
                "title": "Test PR",
                "head": {"sha": "abcdef0123456789"},
            },
        })
        event = classify_webhook("pull_request", payload, "d2")
        self.assertIsNotNone(event)
        self.assertIn("PR #42", event.summary)

    def test_pr_label_noise_ignored(self):
        payload = self.base_repo()
        payload.update({"action": "labeled", "pull_request": {"number": 42}})
        self.assertIsNone(classify_webhook("pull_request", payload, "d3"))

    def test_main_push_actionable(self):
        payload = self.base_repo()
        payload.update({"ref": "refs/heads/main", "after": "1234567890abcdef", "commits": [{}, {}]})
        event = classify_webhook("push", payload, "d4")
        self.assertIsNotNone(event)
        self.assertIn("2 commit", event.summary)

    def test_feature_push_ignored(self):
        payload = self.base_repo()
        payload.update({"ref": "refs/heads/feature", "after": "123"})
        self.assertIsNone(classify_webhook("push", payload, "d5"))

    def test_completed_workflow_actionable(self):
        payload = self.base_repo()
        payload.update({
            "action": "completed",
            "workflow_run": {
                "name": "CI",
                "conclusion": "success",
                "head_sha": "cafebabedeadbeef",
            },
        })
        event = classify_webhook("workflow_run", payload, "d6")
        self.assertIsNotNone(event)
        self.assertIn("conclusion=success", event.summary)

    def test_bot_pr_comment_ignored(self):
        payload = self.base_repo()
        payload.update({
            "action": "created",
            "issue": {"number": 12, "pull_request": {"url": "x"}},
            "comment": {"body": "status", "user": {"login": "dependabot[bot]"}},
        })
        self.assertIsNone(classify_webhook("issue_comment", payload, "d7"))

    def test_human_pr_comment_actionable(self):
        payload = self.base_repo()
        payload.update({
            "action": "created",
            "issue": {"number": 12, "pull_request": {"url": "x"}},
            "comment": {"body": "please recheck", "user": {"login": "ni-da-ba"}},
        })
        event = classify_webhook("issue_comment", payload, "d8")
        self.assertIsNotNone(event)
        self.assertIn("please recheck", event.summary)

    def test_prompt_is_bounded(self):
        payload = self.base_repo()
        payload.update({"ref": "refs/heads/main", "after": "1234567890abcdef", "commits": []})
        event = classify_webhook("push", payload, "d9")
        prompt = build_orchestrator_prompt([event])
        self.assertIn("at most one bounded worker", prompt)
        self.assertIn("RUNNING_EXTERNAL", prompt)
        self.assertIn("Do not poll CI", prompt)


if __name__ == "__main__":
    unittest.main()
