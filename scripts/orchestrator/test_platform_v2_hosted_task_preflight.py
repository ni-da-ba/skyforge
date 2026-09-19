from __future__ import annotations

import hashlib
import hmac
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

import platform_v2_hosted_runtime as hosted
from test_platform_v2_hosted_runtime import SECRET, write_legacy
from v2.hosted_task_preflight import (
    AcceptedMainReadCommandValidator,
    HostedTaskPreflightDisposition,
)
from v2.state_store import StateStoreError
from v2.task_event_composition import TaskAuthorityEventStore


MAIN = "a" * 40
BODY = """AUDIT NEW TASK [SKYFORGE TASK AUTHORITY]
{"lane":"Implementation","objective":"Implement bounded feature","stop_boundary":"merge boundary","allowed_paths":["docs/operations/**"],"protected_paths":["scripts/orchestrator/**"],"auto_merge_eligible":false}
"""


def task_payload(*, body=BODY, actor="ni-da-ba"):
    return {
        "action": "created",
        "repository": {"full_name": "ni-da-ba/skyforge"},
        "issue": {
            "number": 900,
            "title": "Bounded task",
            "body": "Issue prose is context only.",
        },
        "comment": {
            "id": 12345,
            "body": body,
            "created_at": "2026-09-18T03:00:00Z",
            "updated_at": "2026-09-18T03:00:00Z",
            "user": {"login": actor},
        },
    }


def signed(payload, *, delivery="r5c9-delivery"):
    raw = json.dumps(payload, separators=(",", ":")).encode()
    digest = hmac.new(SECRET.encode(), raw, hashlib.sha256).hexdigest()
    return raw, {
        "X-GitHub-Event": "issue_comment",
        "X-GitHub-Delivery": delivery,
        "X-Hub-Signature-256": "sha256=" + digest,
    }


def runtime(root: Path):
    write_legacy(root)
    return hosted.HostedV2Substrate(
        root,
        repo="ni-da-ba/skyforge",
        require_webhook_secret=True,
        startup_reconcile=True,
        webhook_secret=SECRET,
        trusted_actors=("ni-da-ba",),
    )


class FakeResult:
    def __init__(self, stdout):
        self.stdout = stdout
        self.stderr = ""


class LiveTruthRunner:
    def __init__(
        self,
        *,
        issue_state="open",
        actor="ni-da-ba",
        comment_body=BODY,
        comment_status="present",
        comment_updated_at="2026-09-18T03:00:00Z",
        main=MAIN,
    ):
        self.issue_state = issue_state
        self.actor = actor
        self.comment_body = comment_body
        self.comment_status = comment_status
        self.comment_updated_at = comment_updated_at
        self.main = main
        self.calls = []

    def __call__(self, args, **kwargs):
        command = tuple(args)
        self.calls.append(command)
        path = args[-1]
        if path == "repos/ni-da-ba/skyforge/issues/900":
            return FakeResult(
                json.dumps(
                    {
                        "number": 900,
                        "state": self.issue_state,
                        "title": "Bounded task",
                        "body": "Issue prose is context only.",
                    }
                )
            )
        if path == "repos/ni-da-ba/skyforge/issues/comments/12345":
            if self.comment_status == "deleted":
                raise subprocess.CalledProcessError(
                    1, args, stderr="HTTP 404: Not Found"
                )
            return FakeResult(
                json.dumps(
                    {
                        "id": 12345,
                        "issue_url": "https://api.github.com/repos/ni-da-ba/skyforge/issues/900",
                        "body": self.comment_body,
                        "user": {"login": self.actor},
                        "created_at": "2026-09-18T03:00:00Z",
                        "updated_at": self.comment_updated_at,
                    }
                )
            )
        if command == (
            "gh",
            "api",
            "repos/ni-da-ba/skyforge/commits/main",
            "--jq",
            ".sha",
        ):
            return FakeResult(self.main + "\n")
        raise AssertionError(command)


class HostedTaskCaptureTest(unittest.TestCase):
    def test_signed_task_persists_authority_before_event_and_is_read_only(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = runtime(root)
            raw, headers = signed(task_payload())

            status, response = app.handle_webhook(headers=headers, raw=raw)

            self.assertEqual(status, 202)
            self.assertTrue(response["accepted"])
            self.assertTrue(response["task_authority_recorded"])
            self.assertFalse(response["mutation_authority"])

            self.assertEqual(len(app.state.inbox.pending_events), 1)
            event = app.state.inbox.pending_events[0]
            record = TaskAuthorityEventStore.for_root(root).load().get(event.event_id)
            self.assertIsNotNone(record)
            self.assertEqual(record.reference.actor, "ni-da-ba")
            self.assertEqual(record.reference.comment_id, 12345)

            health = app.health_snapshot()
            self.assertTrue(health["task_authority_capture_enabled"])
            self.assertEqual(health["task_authority_record_count"], 1)
            self.assertTrue(health["task_preflight_enabled"])
            self.assertFalse(health["mutation_authority"])
            self.assertFalse(health["worker_dispatch_enabled"])
            self.assertFalse(health["remote_effect_execution_enabled"])

    def test_capture_persistence_failure_prevents_event_enqueue(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = runtime(root)
            raw, headers = signed(task_payload())
            with mock.patch.object(
                TaskAuthorityEventStore,
                "capture",
                side_effect=StateStoreError("fixture persistence failure"),
            ):
                status, response = app.handle_webhook(headers=headers, raw=raw)

            self.assertEqual(status, 503)
            self.assertFalse(response["accepted"])
            self.assertEqual(len(app.state.inbox.pending_events), 0)

    def test_duplicate_delivery_and_restart_are_idempotent(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = runtime(root)
            raw, headers = signed(task_payload())

            first_status, _ = app.handle_webhook(headers=headers, raw=raw)
            second_status, second = app.handle_webhook(headers=headers, raw=raw)
            self.assertEqual(first_status, 202)
            self.assertEqual(second_status, 200)
            self.assertTrue(second["duplicate"])

            reloaded = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                trusted_actors=("ni-da-ba",),
            )
            self.assertEqual(len(reloaded.state.inbox.pending_events), 1)
            self.assertEqual(
                len(reloaded.task_authority_store.load().records),
                1,
            )

    def test_non_task_webhook_does_not_create_authority_record(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = runtime(root)
            payload = {
                "ref": "refs/heads/main",
                "after": MAIN,
                "repository": {"full_name": "ni-da-ba/skyforge"},
            }
            raw = json.dumps(payload, separators=(",", ":")).encode()
            digest = hmac.new(SECRET.encode(), raw, hashlib.sha256).hexdigest()
            status, response = app.handle_webhook(
                headers={
                    "X-GitHub-Event": "push",
                    "X-GitHub-Delivery": "push-1",
                    "X-Hub-Signature-256": "sha256=" + digest,
                },
                raw=raw,
            )
            self.assertEqual(status, 202)
            self.assertFalse(response["task_authority_recorded"])
            self.assertEqual(
                len(TaskAuthorityEventStore.for_root(root).load().records),
                0,
            )


class HostedDryPreflightTest(unittest.TestCase):
    def prepare(self, root: Path, *, body=BODY):
        app = runtime(root)
        raw, headers = signed(task_payload(body=body))
        status, _ = app.handle_webhook(headers=headers, raw=raw)
        self.assertEqual(status, 202)
        event = app.state.inbox.pending_events[0]
        return app, event

    def test_exact_live_truth_builds_seed_without_side_effect_files(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app, event = self.prepare(root)
            runner = LiveTruthRunner()

            result = app.preflight_task_event(event.event_id, runner=runner)

            self.assertEqual(
                result.disposition,
                HostedTaskPreflightDisposition.READY_FOR_CLASSIFIER,
            )
            self.assertEqual(result.seed.classifier_request.current_main, MAIN)
            self.assertEqual(
                result.seed.classifier_request.semantic_input["event_keys"],
                [event.event_id],
            )
            self.assertEqual(
                runner.calls,
                [
                    ("gh","api","repos/ni-da-ba/skyforge/issues/900"),
                    ("gh","api","repos/ni-da-ba/skyforge/issues/comments/12345"),
                    ("gh","api","repos/ni-da-ba/skyforge/commits/main","--jq",".sha"),
                ],
            )
            files = sorted(
                str(path.relative_to(root))
                for path in (root / ".skyforge-platform-v2").glob("*")
                if path.is_file()
            )
            self.assertEqual(
                files,
                [
                    ".skyforge-platform-v2/external-claims.json",
                    ".skyforge-platform-v2/external-claims.json.bak",
                    ".skyforge-platform-v2/hosted-state.json",
                    ".skyforge-platform-v2/hosted-state.json.bak",
                    ".skyforge-platform-v2/task-authority-events.json",
                    ".skyforge-platform-v2/task-authority-events.json.bak",
                ],
            )

    def test_unstructured_task_stays_non_executable_and_skips_main_read(self):
        body = "AUDIT NEW TASK\nLegacy task without typed authority."
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app, event = self.prepare(root, body=body)
            runner = LiveTruthRunner(comment_body=body)

            result = app.preflight_task_event(event.event_id, runner=runner)

            self.assertEqual(
                result.disposition,
                HostedTaskPreflightDisposition.NOT_EXECUTABLE_V2,
            )
            self.assertEqual(len(runner.calls), 2)

    def test_edited_deleted_closed_and_untrusted_live_truth_fail_closed(self):
        cases = [
            LiveTruthRunner(
                comment_body=BODY + " ",
                comment_updated_at="2026-09-18T03:01:00Z",
            ),
            LiveTruthRunner(comment_status="deleted"),
            LiveTruthRunner(issue_state="closed"),
            LiveTruthRunner(actor="mallory"),
        ]
        for runner in cases:
            with self.subTest(runner=runner.__dict__), tempfile.TemporaryDirectory() as td:
                root = Path(td)
                app, event = self.prepare(root)
                result = app.preflight_task_event(event.event_id, runner=runner)
                self.assertEqual(
                    result.disposition,
                    HostedTaskPreflightDisposition.REJECTED,
                )
                self.assertLessEqual(len(runner.calls), 2)

    def test_missing_capture_fails_closed_without_remote_read(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = runtime(root)
            runner = LiveTruthRunner()
            result = app.preflight_task_event(
                "sha256:" + "0" * 64,
                runner=runner,
            )
            self.assertEqual(
                result.disposition,
                HostedTaskPreflightDisposition.REJECTED,
            )
            self.assertEqual(runner.calls, [])

    def test_accepted_main_validator_has_no_mutation_escape(self):
        validator = AcceptedMainReadCommandValidator("ni-da-ba/skyforge")
        self.assertEqual(
            validator.validate(
                ["gh","api","repos/ni-da-ba/skyforge/commits/main","--jq",".sha"]
            ),
            validator.allowed(),
        )
        for command in (
            ["gh","api","repos/ni-da-ba/skyforge/commits/main","--method","PATCH"],
            ["git","push","origin","main"],
            ["gh","issue","comment","900","--body","x"],
        ):
            with self.subTest(command=command), self.assertRaises(ValueError):
                validator.validate(command)


if __name__ == "__main__":
    unittest.main()