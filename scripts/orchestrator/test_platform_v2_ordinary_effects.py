from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from v2.effects import (
    RemoteEffectObservation,
    RemoteEffectPresence,
)
from v2.ordinary_effect_executor import (
    OrdinaryEffectExecutionDisposition,
    OrdinaryRemoteUnavailable,
    advance_remote_effect,
)
from v2.ordinary_effects import (
    OrdinaryEffectStore,
    OrdinaryMutationScope,
)
from v2.ordinary_remote import (
    GhGitOrdinaryEffectAdapter,
    OrdinaryCommandValidator,
    OrdinaryEffectBinding,
    comment_payload,
)
from v2.workspace_commit import WorkspaceCommitAdapter, WorkspaceCommitScope


ATTEMPT = "a" * 64
BASE = "b" * 40
HEAD = "c" * 40
BRANCH = "codex/implementation-r5c1"


def scope(*, issue_number=852) -> OrdinaryMutationScope:
    return OrdinaryMutationScope(
        attempt_id=ATTEMPT,
        repo="ni-da-ba/skyforge",
        base_sha=BASE,
        branch=BRANCH,
        expected_head_sha=HEAD,
        pr_title="CODEX Implementation: bounded adapter test",
        pr_body="Bounded ordinary task handoff.",
        issue_number=issue_number,
    )


class FakeRemote:
    def __init__(self) -> None:
        self.present = False
        self.conflict = False
        self.execute_calls = 0
        self.fail_execute = False

    def observe(self, identity):
        if self.conflict:
            return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
        if self.present:
            return RemoteEffectObservation(
                RemoteEffectPresence.PRESENT_EXACT,
                remote_identity=f"remote:{identity.effect_id}",
            )
        return RemoteEffectObservation(RemoteEffectPresence.ABSENT)

    def execute(self, identity):
        self.execute_calls += 1
        if self.fail_execute:
            raise OrdinaryRemoteUnavailable("unknown outcome")
        self.present = True
        return f"remote:{identity.effect_id}"


class OrdinaryEffectExecutorTest(unittest.TestCase):
    def test_pending_is_durable_before_execute_and_restart_reconciles(self):
        with tempfile.TemporaryDirectory() as td:
            store = OrdinaryEffectStore.for_root(Path(td))
            identity = scope().push_identity()
            remote = FakeRemote()

            with self.assertRaisesRegex(RuntimeError, "injected crash"):
                advance_remote_effect(
                    store=store,
                    identity=identity,
                    adapter=remote,
                    crash_after_execute=True,
                )

            record = store.load().get(identity)
            self.assertIsNotNone(record)
            self.assertEqual(record.status.value, "PENDING")
            self.assertEqual(remote.execute_calls, 1)
            self.assertTrue(remote.present)

            result = advance_remote_effect(
                store=store,
                identity=identity,
                adapter=remote,
            )
            self.assertEqual(
                result.disposition,
                OrdinaryEffectExecutionDisposition.RECONCILED,
            )
            self.assertEqual(remote.execute_calls, 1)
            self.assertEqual(result.record.status.value, "COMPLETE")
            self.assertTrue(
                (Path(td) / ".skyforge-platform-v2/ordinary-effects.json.bak").is_file()
            )

    def test_unknown_mutation_outcome_stays_pending(self):
        with tempfile.TemporaryDirectory() as td:
            store = OrdinaryEffectStore.for_root(Path(td))
            identity = scope().push_identity()
            remote = FakeRemote()
            remote.fail_execute = True

            result = advance_remote_effect(
                store=store,
                identity=identity,
                adapter=remote,
            )
            self.assertEqual(
                result.disposition,
                OrdinaryEffectExecutionDisposition.BLOCKED,
            )
            self.assertEqual(store.load().get(identity).status.value, "PENDING")
            self.assertEqual(remote.execute_calls, 1)

    def test_conflicting_remote_identity_blocks_without_execution(self):
        with tempfile.TemporaryDirectory() as td:
            store = OrdinaryEffectStore.for_root(Path(td))
            identity = scope().push_identity()
            remote = FakeRemote()
            remote.conflict = True

            result = advance_remote_effect(
                store=store,
                identity=identity,
                adapter=remote,
            )
            self.assertEqual(
                result.disposition,
                OrdinaryEffectExecutionDisposition.BLOCKED,
            )
            self.assertEqual(remote.execute_calls, 0)

    def test_completed_effect_never_executes_again(self):
        with tempfile.TemporaryDirectory() as td:
            store = OrdinaryEffectStore.for_root(Path(td))
            identity = scope().push_identity()
            remote = FakeRemote()
            first = advance_remote_effect(
                store=store,
                identity=identity,
                adapter=remote,
            )
            self.assertEqual(
                first.disposition,
                OrdinaryEffectExecutionDisposition.EXECUTED,
            )
            again = advance_remote_effect(
                store=store,
                identity=identity,
                adapter=remote,
            )
            self.assertEqual(
                again.disposition,
                OrdinaryEffectExecutionDisposition.ALREADY_COMPLETE,
            )
            self.assertEqual(remote.execute_calls, 1)


class CommandAllowlistTest(unittest.TestCase):
    def test_push_allowlist_is_exact(self):
        binding = OrdinaryEffectBinding(
            scope=scope(),
            identity=scope().push_identity(),
        )
        validator = OrdinaryCommandValidator(binding)
        exact = (
            "git",
            "push",
            "origin",
            f"{HEAD}:refs/heads/{BRANCH}",
        )
        self.assertEqual(validator.validate(exact), exact)
        for forbidden in (
            ("git", "push", "--force", "origin", BRANCH),
            ("gh", "issue", "close", "852"),
            ("gh", "api", "--method", "DELETE", "repos/ni-da-ba/skyforge"),
            ("gh", "workflow", "run", "CI"),
        ):
            with self.subTest(command=forbidden):
                with self.assertRaisesRegex(ValueError, "allowlist"):
                    validator.validate(forbidden)

    def test_merge_is_bound_to_exact_head(self):
        s = scope()
        identity = s.merge_identity(99)
        binding = OrdinaryEffectBinding(
            scope=s,
            identity=identity,
            pr_number=99,
        )
        validator = OrdinaryCommandValidator(binding)
        exact = (
            "gh", "pr", "merge", "99", "--repo", s.repo,
            "--merge", "--match-head-commit", HEAD,
        )
        self.assertEqual(validator.validate(exact), exact)
        with self.assertRaises(ValueError):
            validator.validate(
                (
                    "gh", "pr", "merge", "99", "--repo", s.repo, "--merge"
                )
            )

    def test_comment_contains_effect_id_marker(self):
        s = scope()
        identity = s.comment_identity("handoff summary")
        body = comment_payload(identity, "handoff summary")
        self.assertIn(identity.effect_id, body)
        self.assertTrue(body.endswith("handoff summary"))


class FakeProcess:
    def __init__(self, stdout="", stderr="", returncode=0):
        self.stdout = stdout
        self.stderr = stderr
        self.returncode = returncode


class OrdinaryRemoteObservationTest(unittest.TestCase):
    def test_push_observation_distinguishes_absent_exact_and_conflict(self):
        s = scope()
        binding = OrdinaryEffectBinding(
            scope=s,
            identity=s.push_identity(),
        )

        def exact_runner(args, **kwargs):
            self.assertEqual(args[0:2], ["gh", "api"])
            return FakeProcess(stdout=HEAD + "\n")

        adapter = GhGitOrdinaryEffectAdapter(
            root=Path("."),
            binding=binding,
            runner=exact_runner,
        )
        observation = adapter.observe(binding.identity)
        self.assertEqual(observation.presence, RemoteEffectPresence.PRESENT_EXACT)

        def conflict_runner(args, **kwargs):
            return FakeProcess(stdout=("d" * 40) + "\n")

        adapter = GhGitOrdinaryEffectAdapter(
            root=Path("."),
            binding=binding,
            runner=conflict_runner,
        )
        observation = adapter.observe(binding.identity)
        self.assertEqual(observation.presence, RemoteEffectPresence.PRESENT_CONFLICT)

        def absent_runner(args, **kwargs):
            raise subprocess.CalledProcessError(
                1,
                args,
                stderr="HTTP 404: Not Found",
            )

        adapter = GhGitOrdinaryEffectAdapter(
            root=Path("."),
            binding=binding,
            runner=absent_runner,
        )
        observation = adapter.observe(binding.identity)
        self.assertEqual(observation.presence, RemoteEffectPresence.ABSENT)

    def test_create_pr_requires_single_exact_identity(self):
        s = scope()
        binding = OrdinaryEffectBinding(
            scope=s,
            identity=s.create_pr_identity(),
        )
        exact = [{
            "number": 42,
            "state": "OPEN",
            "mergedAt": None,
            "headRefName": BRANCH,
            "headRefOid": HEAD,
            "baseRefName": "main",
            "title": s.pr_title,
            "body": s.pr_body,
        }]

        def runner(args, **kwargs):
            return FakeProcess(stdout=json.dumps(exact))

        adapter = GhGitOrdinaryEffectAdapter(
            root=Path("."),
            binding=binding,
            runner=runner,
        )
        observation = adapter.observe(binding.identity)
        self.assertEqual(observation.presence, RemoteEffectPresence.PRESENT_EXACT)
        self.assertEqual(observation.remote_identity, f"pr:42:OPEN@{HEAD}")

        exact.append(dict(exact[0], number=43))
        observation = adapter.observe(binding.identity)
        self.assertEqual(observation.presence, RemoteEffectPresence.PRESENT_CONFLICT)

    def test_exact_comment_marker_reconciles(self):
        s = scope()
        identity = s.comment_identity("summary")
        binding = OrdinaryEffectBinding(
            scope=s,
            identity=identity,
            comment_body="summary",
        )
        comment = {
            "id": 1234,
            "body": comment_payload(identity, "summary"),
        }

        def runner(args, **kwargs):
            return FakeProcess(stdout=json.dumps([[comment]]))

        adapter = GhGitOrdinaryEffectAdapter(
            root=Path("."),
            binding=binding,
            runner=runner,
        )
        observation = adapter.observe(identity)
        self.assertEqual(observation.presence, RemoteEffectPresence.PRESENT_EXACT)
        self.assertEqual(observation.remote_identity, "comment:1234")


class WorkspaceCommitAdapterTest(unittest.TestCase):
    def git(self, root: Path, *args: str) -> str:
        result = subprocess.run(
            ["git", *args],
            cwd=root,
            check=True,
            text=True,
            capture_output=True,
        )
        return result.stdout.strip()

    def make_repo(self, root: Path) -> str:
        self.git(root, "init", "-b", "main")
        self.git(root, "config", "user.email", "test@example.invalid")
        self.git(root, "config", "user.name", "Skyforge Test")
        (root / "allowed.txt").write_text("base\n")
        self.git(root, "add", "allowed.txt")
        self.git(root, "commit", "-m", "base")
        start = self.git(root, "rev-parse", "HEAD")
        self.git(root, "checkout", "-b", BRANCH)
        return start

    def test_commit_is_bounded_and_restart_idempotent(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            start = self.make_repo(root)
            (root / "allowed.txt").write_text("changed\n")
            adapter = WorkspaceCommitAdapter(
                worktree=root,
                scope=WorkspaceCommitScope(
                    attempt_id=ATTEMPT,
                    branch=BRANCH,
                    start_head=start,
                    allowed_paths=("allowed.txt",),
                ),
            )
            first = adapter.commit(
                lane="Implementation",
                objective="bounded adapter test",
            )
            self.assertTrue(first.commit_created)
            self.assertEqual(first.changed_paths, ("allowed.txt",))
            self.assertNotEqual(first.head_sha, start)
            message = self.git(root, "log", "-1", "--format=%B")
            self.assertIn(f"Skyforge-Attempt: {ATTEMPT}", message)

            second = adapter.commit(
                lane="Implementation",
                objective="bounded adapter test",
            )
            self.assertFalse(second.commit_created)
            self.assertEqual(second.head_sha, first.head_sha)

    def test_out_of_scope_or_protected_path_blocks_before_commit(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            start = self.make_repo(root)
            (root / "other.txt").write_text("nope\n")
            adapter = WorkspaceCommitAdapter(
                worktree=root,
                scope=WorkspaceCommitScope(
                    attempt_id=ATTEMPT,
                    branch=BRANCH,
                    start_head=start,
                    allowed_paths=("allowed.txt",),
                ),
            )
            with self.assertRaisesRegex(RuntimeError, "outside frozen scope"):
                adapter.commit(lane="Implementation", objective="bounded")

            (root / "other.txt").unlink()
            state_dir = root / ".skyforge-platform-v2"
            state_dir.mkdir()
            (state_dir / "x").write_text("nope\n")
            with self.assertRaisesRegex(RuntimeError, "protected path"):
                adapter.commit(lane="Implementation", objective="bounded")


if __name__ == "__main__":
    unittest.main()
