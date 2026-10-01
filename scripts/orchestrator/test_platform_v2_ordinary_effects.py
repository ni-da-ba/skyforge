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
    OrdinaryFrozenBaseMoved,
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


def scope(*, issue_number=852, base_ref="main") -> OrdinaryMutationScope:
    return OrdinaryMutationScope(
        attempt_id=ATTEMPT,
        repo="ni-da-ba/skyforge",
        base_sha=BASE,
        branch=BRANCH,
        expected_head_sha=HEAD,
        pr_title="CODEX Implementation: bounded adapter test",
        pr_body="Bounded ordinary task handoff.",
        issue_number=issue_number,
        base_ref=base_ref,
    )


class FakeRemote:
    def __init__(self) -> None:
        self.present = False
        self.conflict = False
        self.execute_calls = 0
        self.fail_execute = False
        self.stale_base = False

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
        if self.stale_base:
            raise OrdinaryFrozenBaseMoved("current main moved from frozen ordinary-task base")
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

    def test_frozen_base_move_abandons_effect_and_never_reexecutes(self):
        with tempfile.TemporaryDirectory() as td:
            store = OrdinaryEffectStore.for_root(Path(td))
            identity = scope().create_pr_identity()
            remote = FakeRemote()
            remote.stale_base = True

            result = advance_remote_effect(
                store=store,
                identity=identity,
                adapter=remote,
            )
            self.assertEqual(
                result.disposition,
                OrdinaryEffectExecutionDisposition.STALE_BASE,
            )
            self.assertIn("frozen ordinary-task base", result.reason)
            self.assertEqual(store.load().get(identity).status.value, "ABANDONED")
            self.assertEqual(remote.execute_calls, 1)

            again = advance_remote_effect(
                store=store,
                identity=identity,
                adapter=remote,
            )
            self.assertEqual(
                again.disposition,
                OrdinaryEffectExecutionDisposition.STALE_BASE,
            )
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


class BaseRefCompatibilityTest(unittest.TestCase):
    def test_default_main_serialization_and_effect_subjects_remain_legacy_compatible(self):
        s = scope()
        raw = s.as_dict()
        self.assertNotIn("base_ref", raw)
        self.assertEqual(
            OrdinaryMutationScope.from_mapping(raw).as_dict(),
            raw,
        )
        self.assertEqual(
            s.create_pr_identity().subject,
            f"{BRANCH}->{BASE}",
        )
        self.assertEqual(
            s.merge_identity(99).subject,
            f"pr:99@{HEAD}",
        )

    def test_non_main_scope_binds_target_ref_into_serialization_and_effect_identity(self):
        s = scope(base_ref="rehearsal/r5c22-base")
        self.assertEqual(s.as_dict()["base_ref"], "rehearsal/r5c22-base")
        self.assertEqual(
            s.create_pr_identity().subject,
            f"{BRANCH}->rehearsal/r5c22-base@{BASE}",
        )
        self.assertEqual(
            s.merge_identity(99).subject,
            f"pr:99:rehearsal/r5c22-base@{HEAD}",
        )
        self.assertNotEqual(
            s.create_pr_identity(),
            scope(base_ref="rehearsal/other-base").create_pr_identity(),
        )

    def test_non_main_allowlist_targets_only_exact_frozen_base(self):
        s = scope(base_ref="rehearsal/r5c22-base")
        binding = OrdinaryEffectBinding(
            scope=s,
            identity=s.create_pr_identity(),
        )
        validator = OrdinaryCommandValidator(binding)
        list_command = (
            "gh", "pr", "list", "--repo", s.repo,
            "--head", s.branch, "--base", s.base_ref, "--state", "all",
            "--json", "number,state,mergedAt,headRefName,headRefOid,baseRefName,title,body",
            "--jq=.",
        )
        create_command = (
            "gh", "pr", "create", "--repo", s.repo,
            "--draft", "--base", s.base_ref, "--head", s.branch,
            "--title", s.pr_title, "--body", s.pr_body,
        )
        self.assertEqual(validator.validate(list_command), list_command)
        self.assertEqual(validator.validate(create_command), create_command)
        with self.assertRaises(ValueError):
            validator.validate(
                tuple("main" if part == s.base_ref else part for part in create_command)
            )

    def test_unsafe_base_ref_is_rejected(self):
        for value in ("../main", "/main", "main..other", "main@{1}", "main~1"):
            with self.subTest(base_ref=value), self.assertRaises(ValueError):
                scope(base_ref=value)


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

        def absent_422_runner(args, **kwargs):
            raise subprocess.CalledProcessError(
                1,
                args,
                stderr="gh: No commit found for SHA: rehearsal/missing (HTTP 422)",
            )

        adapter = GhGitOrdinaryEffectAdapter(
            root=Path("."),
            binding=binding,
            runner=absent_422_runner,
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

    def test_non_main_pr_observation_requires_exact_base_ref(self):
        s = scope(base_ref="rehearsal/r5c22-base")
        binding = OrdinaryEffectBinding(
            scope=s,
            identity=s.create_pr_identity(),
        )
        value = [{
            "number": 77,
            "state": "OPEN",
            "mergedAt": None,
            "headRefName": BRANCH,
            "headRefOid": HEAD,
            "baseRefName": s.base_ref,
            "title": s.pr_title,
            "body": s.pr_body,
        }]

        calls = []
        def runner(args, **kwargs):
            calls.append(tuple(args))
            return FakeProcess(stdout=json.dumps(value))

        adapter = GhGitOrdinaryEffectAdapter(
            root=Path("."),
            binding=binding,
            runner=runner,
        )
        exact = adapter.observe(binding.identity)
        self.assertEqual(exact.presence, RemoteEffectPresence.PRESENT_EXACT)
        self.assertIn(("--base", s.base_ref), tuple(zip(calls[0], calls[0][1:])))

        value[0]["baseRefName"] = "main"
        conflict = adapter.observe(binding.identity)
        self.assertEqual(conflict.presence, RemoteEffectPresence.PRESENT_CONFLICT)

    def test_comment_observation_parses_multiple_line_delimited_pages(self):
        s = scope()
        identity = s.comment_identity("summary")
        binding = OrdinaryEffectBinding(
            scope=s,
            identity=identity,
            comment_body="summary",
        )
        exact = {
            "id": 1234,
            "body": comment_payload(identity, "summary"),
        }
        unrelated = {"id": 2222, "body": "unrelated"}

        def runner(args, **kwargs):
            return FakeProcess(
                stdout=json.dumps(unrelated) + "\n" + json.dumps(exact) + "\n"
            )

        adapter = GhGitOrdinaryEffectAdapter(
            root=Path("."),
            binding=binding,
            runner=runner,
        )
        observation = adapter.observe(identity)
        self.assertEqual(observation.presence, RemoteEffectPresence.PRESENT_EXACT)
        self.assertEqual(observation.remote_identity, "comment:1234")

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
            self.assertEqual(
                tuple(args[-3:]),
                ("--paginate", "--jq", ".[]"),
            )
            return FakeProcess(stdout=json.dumps(comment) + "\n")

        adapter = GhGitOrdinaryEffectAdapter(
            root=Path("."),
            binding=binding,
            runner=runner,
        )
        observation = adapter.observe(identity)
        self.assertEqual(observation.presence, RemoteEffectPresence.PRESENT_EXACT)
        self.assertEqual(observation.remote_identity, "comment:1234")


class OrdinaryPreMutationIdentityTest(unittest.TestCase):
    def test_create_pr_blocks_when_frozen_base_moved(self):
        s = scope()
        binding = OrdinaryEffectBinding(
            scope=s,
            identity=s.create_pr_identity(),
        )

        def runner(args, **kwargs):
            self.assertEqual(
                args,
                ["gh", "api", f"repos/{s.repo}/commits/main", "--jq", ".sha"],
            )
            return FakeProcess(stdout=("d" * 40) + "\n")

        adapter = GhGitOrdinaryEffectAdapter(
            root=Path("."),
            binding=binding,
            runner=runner,
        )
        with self.assertRaisesRegex(OrdinaryRemoteUnavailable, "main moved"):
            adapter.execute(binding.identity)

    def test_non_main_create_pr_rechecks_exact_base_ref_and_uses_it_for_create(self):
        s = scope(base_ref="rehearsal/r5c22-base")
        binding = OrdinaryEffectBinding(
            scope=s,
            identity=s.create_pr_identity(),
        )
        calls = []

        def runner(args, **kwargs):
            command = tuple(args)
            calls.append(command)
            if command[:2] == ("gh", "api") and command[-2:] == ("--jq", ".sha"):
                if f"/commits/rehearsal%2Fr5c22-base" in command[2]:
                    return FakeProcess(stdout=BASE + "\n")
                if f"/commits/{BRANCH.replace('/', '%2F')}" in command[2]:
                    return FakeProcess(stdout=HEAD + "\n")
            if command[:3] == ("gh", "pr", "create"):
                return FakeProcess(stdout="https://github.com/ni-da-ba/skyforge/pull/999\n")
            raise AssertionError(command)

        adapter = GhGitOrdinaryEffectAdapter(
            root=Path("."),
            binding=binding,
            runner=runner,
        )
        adapter.execute(binding.identity)
        self.assertIn(
            (
                "gh", "pr", "create", "--repo", s.repo,
                "--draft", "--base", s.base_ref, "--head", s.branch,
                "--title", s.pr_title, "--body", s.pr_body,
            ),
            calls,
        )

    def test_non_main_create_pr_blocks_if_frozen_target_ref_moved(self):
        s = scope(base_ref="rehearsal/r5c22-base")
        binding = OrdinaryEffectBinding(
            scope=s,
            identity=s.create_pr_identity(),
        )
        calls = []

        def runner(args, **kwargs):
            calls.append(tuple(args))
            return FakeProcess(stdout=("d" * 40) + "\n")

        adapter = GhGitOrdinaryEffectAdapter(
            root=Path("."),
            binding=binding,
            runner=runner,
        )
        with self.assertRaisesRegex(OrdinaryRemoteUnavailable, "base ref moved"):
            adapter.execute(binding.identity)
        self.assertEqual(len(calls), 1)
        self.assertIn("rehearsal%2Fr5c22-base", calls[0][2])

    def test_hosted_runtime_does_not_import_ordinary_mutation_surface(self):
        root = Path(__file__).resolve().parent
        source = (root / "platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        for forbidden in (
            "ordinary_remote",
            "ordinary_effect_executor",
            "ordinary_effects",
            "workspace_commit",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, source)


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
