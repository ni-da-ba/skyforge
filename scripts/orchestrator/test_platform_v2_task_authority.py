from __future__ import annotations

from dataclasses import replace
import json
import subprocess
import unittest

from v2.task_authority import (
    GhTaskAuthorityHydrator,
    TASK_AUTHORITY_MARKER,
    TaskAuthorityDisposition,
    TaskAuthorityReadCommandValidator,
    TaskAuthorityWakeReference,
    hydrate_task_authority,
    parse_typed_task_directive,
)


BODY = """AUDIT NEW TASK [SKYFORGE TASK AUTHORITY]
{"lane":"Implementation","objective":"Implement bounded feature","stop_boundary":"merge boundary","allowed_paths":["src/main/java/**"],"protected_paths":["scripts/orchestrator/**"],"auto_merge_eligible":false}
"""


def ref(**overrides):
    values = dict(
        repo="ni-da-ba/skyforge",
        issue_number=900,
        comment_id=12345,
        actor="ni-da-ba",
        body=BODY,
        created_at="2026-09-18T03:00:00Z",
        updated_at="2026-09-18T03:00:00Z",
    )
    values.update(overrides)
    return TaskAuthorityWakeReference(**values)


def issue(**overrides):
    values = dict(
        number=900,
        state="open",
        title="Bounded task",
        body="Issue prose is context only and must not widen authority.",
    )
    values.update(overrides)
    return values


def comment(**overrides):
    values = dict(
        id=12345,
        issue_url="https://api.github.com/repos/ni-da-ba/skyforge/issues/900",
        body=BODY,
        user={"login": "ni-da-ba"},
        created_at="2026-09-18T03:00:00Z",
        updated_at="2026-09-18T03:00:00Z",
    )
    values.update(overrides)
    return values


class ParserTest(unittest.TestCase):
    def test_legacy_unstructured_task_is_visible_but_not_executable(self):
        result = parse_typed_task_directive("AUDIT NEW TASK\nDo something bounded.")
        self.assertEqual(result.disposition, TaskAuthorityDisposition.NOT_EXECUTABLE_V2)
        self.assertIsNone(result.directive)

    def test_valid_typed_directive(self):
        result = parse_typed_task_directive(BODY)
        self.assertEqual(result.disposition, TaskAuthorityDisposition.EXECUTABLE_V2)
        self.assertEqual(result.directive.lane, "Implementation")
        self.assertEqual(result.directive.allowed_paths, ("src/main/java/**",))
        self.assertFalse(result.directive.auto_merge_eligible)

    def test_duplicate_marker_rejected(self):
        result = parse_typed_task_directive(BODY + "\n" + TASK_AUTHORITY_MARKER + "{}")
        self.assertEqual(result.disposition, TaskAuthorityDisposition.REJECTED)

    def test_malformed_json_rejected(self):
        result = parse_typed_task_directive(
            "AUDIT NEW TASK [SKYFORGE TASK AUTHORITY]\n{bad"
        )
        self.assertEqual(result.disposition, TaskAuthorityDisposition.REJECTED)

    def test_marker_without_legacy_task_wake_rejected(self):
        result = parse_typed_task_directive(
            '[SKYFORGE TASK AUTHORITY] {"lane":"Implementation","objective":"x","stop_boundary":"y","allowed_paths":["src/**"]}'
        )
        self.assertEqual(result.disposition, TaskAuthorityDisposition.REJECTED)

    def test_path_scope_rejects_absolute_traversal_and_bad_glob(self):
        for path in ("/etc/passwd", "../secrets", "./src/x", "src/../secret", "C:/tmp/x", "src/*.py"):
            body = (
                "AUDIT NEW TASK [SKYFORGE TASK AUTHORITY]\n"
                + json.dumps(
                    {
                        "lane": "Implementation",
                        "objective": "x",
                        "stop_boundary": "y",
                        "allowed_paths": [path],
                    }
                )
            )
            with self.subTest(path=path):
                self.assertEqual(
                    parse_typed_task_directive(body).disposition,
                    TaskAuthorityDisposition.REJECTED,
                )

    def test_unknown_fields_rejected(self):
        body = (
            "AUDIT NEW TASK [SKYFORGE TASK AUTHORITY]\n"
            + json.dumps(
                {
                    "lane": "Implementation",
                    "objective": "x",
                    "stop_boundary": "y",
                    "allowed_paths": ["src/**"],
                    "worker_tier": "TERRA",
                }
            )
        )
        self.assertEqual(
            parse_typed_task_directive(body).disposition,
            TaskAuthorityDisposition.REJECTED,
        )


class HydrationTest(unittest.TestCase):
    def hydrate(self, *, reference=None, issue_value=None, comment_value=None, trusted=("ni-da-ba",)):
        return hydrate_task_authority(
            reference=reference or ref(),
            issue=issue() if issue_value is None else issue_value,
            comment=comment() if comment_value is None else comment_value,
            trusted_actors=trusted,
        )

    def test_valid_hydration_converts_to_r5c4_authority(self):
        result = self.hydrate()
        self.assertEqual(result.disposition, TaskAuthorityDisposition.EXECUTABLE_V2)
        self.assertIsNotNone(result.identity)
        self.assertIsNotNone(result.authority)
        self.assertEqual(result.authority.issue_numbers, (900,))
        self.assertEqual(result.authority.lane, "Implementation")
        self.assertEqual(result.authority.allowed_paths, ("src/main/java/**",))
        self.assertIn("Issue prose is context only", result.authority.context_text)
        self.assertIn(result.identity.digest, result.authority.authority_key)

    def test_identity_and_authority_are_deterministic(self):
        a = self.hydrate()
        b = self.hydrate()
        self.assertEqual(a.identity.digest, b.identity.digest)
        self.assertEqual(a.authority.digest, b.authority.digest)
        self.assertEqual(a.digest, b.digest)

    def test_untrusted_event_actor_rejected(self):
        result = self.hydrate(reference=ref(actor="mallory"))
        self.assertEqual(result.disposition, TaskAuthorityDisposition.REJECTED)

    def test_untrusted_live_actor_rejected(self):
        result = self.hydrate(comment_value=comment(user={"login":"mallory"}))
        self.assertEqual(result.disposition, TaskAuthorityDisposition.REJECTED)

    def test_issue_mismatch_rejected(self):
        result = self.hydrate(issue_value=issue(number=901))
        self.assertEqual(result.disposition, TaskAuthorityDisposition.REJECTED)
        result = self.hydrate(
            comment_value=comment(
                issue_url="https://api.github.com/repos/ni-da-ba/skyforge/issues/901"
            )
        )
        self.assertEqual(result.disposition, TaskAuthorityDisposition.REJECTED)

    def test_closed_issue_rejected(self):
        result = self.hydrate(issue_value=issue(state="closed"))
        self.assertEqual(result.disposition, TaskAuthorityDisposition.REJECTED)

    def test_stale_edited_comment_rejected(self):
        result = self.hydrate(
            comment_value=comment(
                body=BODY.replace("bounded feature", "edited feature"),
                updated_at="2026-09-18T03:01:00Z",
            )
        )
        self.assertEqual(result.disposition, TaskAuthorityDisposition.REJECTED)
        self.assertIn("changed", result.reason)

    def test_deleted_comment_rejected(self):
        result = self.hydrate(comment_value=None)
        # None means default in helper, so invoke directly for deletion.
        result = hydrate_task_authority(
            reference=ref(),
            issue=issue(),
            comment=None,
            trusted_actors=("ni-da-ba",),
        )
        self.assertEqual(result.disposition, TaskAuthorityDisposition.REJECTED)
        self.assertIn("no longer exists", result.reason)

    def test_issue_prose_never_creates_authority(self):
        legacy = ref(
            body="AUDIT NEW TASK\nNo typed marker here.",
        )
        result = hydrate_task_authority(
            reference=legacy,
            issue=issue(
                body=BODY,
            ),
            comment=comment(body=legacy.body),
            trusted_actors=("ni-da-ba",),
        )
        self.assertEqual(result.disposition, TaskAuthorityDisposition.NOT_EXECUTABLE_V2)
        self.assertIsNone(result.authority)


class FakeResult:
    def __init__(self, payload):
        self.stdout = json.dumps(payload)
        self.stderr = ""


class GhHydratorTest(unittest.TestCase):
    def test_exact_read_only_commands_and_hydration(self):
        reference = ref()
        calls = []

        def runner(args, **kwargs):
            calls.append(tuple(args))
            if args[-1].endswith("/900"):
                return FakeResult(issue())
            if args[-1].endswith("/12345"):
                return FakeResult(comment())
            raise AssertionError(args)

        result = GhTaskAuthorityHydrator(
            reference=reference,
            trusted_actors=("ni-da-ba",),
            runner=runner,
        ).hydrate()
        self.assertEqual(result.disposition, TaskAuthorityDisposition.EXECUTABLE_V2)
        self.assertEqual(
            calls,
            [
                ("gh","api","repos/ni-da-ba/skyforge/issues/900"),
                ("gh","api","repos/ni-da-ba/skyforge/issues/comments/12345"),
            ],
        )

    def test_deleted_comment_404_becomes_rejected_not_authority(self):
        reference = ref()

        def runner(args, **kwargs):
            if args[-1].endswith("/900"):
                return FakeResult(issue())
            raise subprocess.CalledProcessError(
                1,
                args,
                stderr="HTTP 404: Not Found",
            )

        result = GhTaskAuthorityHydrator(
            reference=reference,
            trusted_actors=("ni-da-ba",),
            runner=runner,
        ).hydrate()
        self.assertEqual(result.disposition, TaskAuthorityDisposition.REJECTED)
        self.assertIn("no longer exists", result.reason)

    def test_command_validator_rejects_mutation_and_other_reads(self):
        validator = TaskAuthorityReadCommandValidator(ref())
        for command in (
            ["gh","issue","comment","900","--body","x"],
            ["gh","api","repos/ni-da-ba/skyforge/issues/901"],
            ["gh","api","repos/ni-da-ba/skyforge/issues/comments/999"],
            ["gh","api","repos/ni-da-ba/skyforge/issues/900","--method","PATCH"],
        ):
            with self.subTest(command=command), self.assertRaises(ValueError):
                validator.validate(command)


class HostedIsolationTest(unittest.TestCase):
    def test_r5c7_does_not_activate_hosted_runtime(self):
        from pathlib import Path
        root = Path(__file__).resolve().parent
        source = (root / "platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        self.assertNotIn("task_authority", source)
        self.assertNotIn("GhTaskAuthorityHydrator", source)


if __name__ == "__main__":
    unittest.main()
