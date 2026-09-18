from __future__ import annotations

from dataclasses import replace
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

import platform_v2_canary_executor as cli
from v2.canary import CanaryTaskContract, MutationGateRecord
from v2.canary_executor import (
    CanaryExecutionDisposition,
    CanaryPRSnapshot,
    CanaryRemoteUnavailable,
    InjectedCanaryCrash,
    advance_canary,
    canary_state_store,
)
from v2.domain import CIState
from v2.fence import FenceBusyError, WriterFence
from v2.ownership import OwnershipToken


BASE = "a" * 40
HEAD = "b" * 40
BRANCH = "platform/v2-canary/900-doc"
PATH = "docs/operations/platform-v2-canary/canary-900.md"


def task() -> CanaryTaskContract:
    return CanaryTaskContract(
        task_id="platform-v2-r4-canary-900",
        issue_number=900,
        base_sha=BASE,
        head_branch=BRANCH,
        expected_head_sha=HEAD,
        documentation_path=PATH,
        pr_title="Platform v2 canary 900",
        pr_body="Exact bounded canary.",
    )


def gate() -> MutationGateRecord:
    return MutationGateRecord.from_mapping(
        {
            "schema_version": 1,
            "release3_accepted": True,
            "release3_acceptance_main": "5" * 40,
            "workstation_preservation_passed": True,
            "workstation_evidence": "audit pass",
            "canary_enabled": True,
            "canary_issue_number": 900,
        }
    )


def legacy_state() -> dict:
    return {
        "external_producer_claims": {
            "900": {
                "state": "active",
                "issue_number": 900,
                "claimed_by": "ni-da-ba",
                "branch": BRANCH,
            }
        }
    }


def snapshot(*, state="OPEN", ci=CIState.PENDING, head=HEAD, paths=(PATH,)) -> CanaryPRSnapshot:
    return CanaryPRSnapshot(
        pr_number=901,
        state=state,
        head_branch=BRANCH,
        head_sha=head,
        base_branch="main",
        title="Platform v2 canary 900",
        body="Exact bounded canary.",
        changed_paths=tuple(paths),
        ci_state=ci,
    )


class FakeRemote:
    def __init__(self):
        self.main = BASE
        self.branch = HEAD
        self.pr = None
        self.create_calls = 0
        self.merge_calls = 0
        self.fail_reads = False
        self.fail_create = False
        self.fail_merge = False

    def current_main_sha(self):
        if self.fail_reads:
            raise CanaryRemoteUnavailable("offline")
        return self.main

    def branch_head_sha(self, branch):
        if self.fail_reads:
            raise CanaryRemoteUnavailable("offline")
        return self.branch

    def observe_pr(self, task):
        if self.fail_reads:
            raise CanaryRemoteUnavailable("offline")
        return self.pr

    def create_pr(self, task):
        self.create_calls += 1
        if self.fail_create:
            raise CanaryRemoteUnavailable("create unknown")
        self.pr = snapshot()
        return self.pr

    def merge_pr(self, pr_number, expected_head_sha):
        self.merge_calls += 1
        if self.fail_merge:
            raise CanaryRemoteUnavailable("merge unknown")
        self.pr = replace(self.pr, state="MERGED")


class CanaryExecutorTest(unittest.TestCase):
    def advance(self, root, remote, **kwargs):
        return advance_canary(
            root=root,
            legacy_state=legacy_state(),
            gate=gate(),
            task=task(),
            ownership_token=OwnershipToken("canary-test", 1),
            attempt_number=1,
            remote=remote,
            **kwargs,
        )

    def test_create_effect_is_persisted_before_remote_and_restart_reconciles(self):
        remote = FakeRemote()
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            with self.assertRaises(InjectedCanaryCrash):
                self.advance(root, remote, crash_after_create=True)
            durable = canary_state_store(root).load().as_dict()
            self.assertEqual(durable["create_pr_effect"]["status"], "PENDING")
            self.assertEqual(remote.create_calls, 1)

            result = self.advance(root, remote)
            self.assertEqual(result.disposition, CanaryExecutionDisposition.PR_RECONCILED)
            self.assertEqual(remote.create_calls, 1)
            self.assertEqual(result.state.create_pr_effect.status.value, "COMPLETE")

    def test_ci_pass_establishes_exact_acceptance_and_merges(self):
        remote = FakeRemote()
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            first = self.advance(root, remote)
            self.assertEqual(first.disposition, CanaryExecutionDisposition.PR_CREATED)

            remote.pr = replace(remote.pr, ci_state=CIState.PASS)
            second = self.advance(root, remote)
            self.assertEqual(second.disposition, CanaryExecutionDisposition.MERGED)
            self.assertEqual(remote.merge_calls, 1)
            self.assertTrue(second.state.completed)
            self.assertEqual(second.state.evidence_sha, HEAD)
            self.assertEqual(second.state.reviewed_sha, HEAD)
            self.assertEqual(second.state.accepted_task_spec_hash, task().frozen_spec().spec_hash)

    def test_merge_crash_reconciles_without_duplicate(self):
        remote = FakeRemote()
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            self.advance(root, remote)
            remote.pr = replace(remote.pr, ci_state=CIState.PASS)
            with self.assertRaises(InjectedCanaryCrash):
                self.advance(root, remote, crash_after_merge=True)
            durable = canary_state_store(root).load().as_dict()
            self.assertEqual(durable["merge_pr_effect"]["status"], "PENDING")
            self.assertEqual(remote.merge_calls, 1)

            result = self.advance(root, remote)
            self.assertEqual(result.disposition, CanaryExecutionDisposition.MERGE_RECONCILED)
            self.assertEqual(remote.merge_calls, 1)
            self.assertTrue(result.state.completed)

    def test_github_unavailable_does_not_mutate(self):
        remote = FakeRemote()
        remote.fail_reads = True
        with tempfile.TemporaryDirectory() as td:
            result = self.advance(Path(td), remote)
        self.assertEqual(result.disposition, CanaryExecutionDisposition.BLOCKED)
        self.assertEqual(remote.create_calls, 0)
        self.assertEqual(remote.merge_calls, 0)

    def test_unknown_create_outcome_preserves_pending_effect(self):
        remote = FakeRemote()
        remote.fail_create = True
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            result = self.advance(root, remote)
            self.assertEqual(result.disposition, CanaryExecutionDisposition.BLOCKED)
            durable = canary_state_store(root).load().as_dict()
            self.assertEqual(durable["create_pr_effect"]["status"], "PENDING")

    def test_head_movement_blocks_and_never_merges(self):
        remote = FakeRemote()
        remote.branch = "c" * 40
        with tempfile.TemporaryDirectory() as td:
            result = self.advance(Path(td), remote)
        self.assertEqual(result.disposition, CanaryExecutionDisposition.BLOCKED)
        self.assertEqual(remote.create_calls, 0)

    def test_base_movement_blocks_new_attempt(self):
        remote = FakeRemote()
        remote.main = "c" * 40
        with tempfile.TemporaryDirectory() as td:
            result = self.advance(Path(td), remote)
        self.assertEqual(result.disposition, CanaryExecutionDisposition.BLOCKED)
        self.assertIn("base SHA", result.reason)

    def test_extra_changed_path_blocks_merge(self):
        remote = FakeRemote()
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            self.advance(root, remote)
            remote.pr = snapshot(ci=CIState.PASS, paths=(PATH, "README.md"))
            result = self.advance(root, remote)
        self.assertEqual(result.disposition, CanaryExecutionDisposition.BLOCKED)
        self.assertEqual(remote.merge_calls, 0)

    def test_gate_false_blocks_before_remote_create(self):
        remote = FakeRemote()
        blocked_gate = MutationGateRecord.from_mapping(
            {
                "schema_version": 1,
                "release3_accepted": True,
                "release3_acceptance_main": "5" * 40,
                "workstation_preservation_passed": True,
                "workstation_evidence": "audit",
                "canary_enabled": False,
                "canary_issue_number": None,
            }
        )
        with tempfile.TemporaryDirectory() as td:
            result = advance_canary(
                root=Path(td),
                legacy_state=legacy_state(),
                gate=blocked_gate,
                task=task(),
                ownership_token=OwnershipToken("canary-test", 1),
                attempt_number=1,
                remote=remote,
            )
        self.assertEqual(result.disposition, CanaryExecutionDisposition.BLOCKED)
        self.assertEqual(remote.create_calls, 0)

    def test_duplicate_executor_is_fenced(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            fence_path = root / ".skyforge-platform-v2/writer.lock"
            with WriterFence(fence_path, "owner-a", 1):
                with self.assertRaises(FenceBusyError):
                    self.advance(root, FakeRemote())


class CanaryGhAllowlistTest(unittest.TestCase):
    def test_allowlist_accepts_only_exact_canary_shapes(self):
        t = task()
        valid = [
            ["gh","api","repos/ni-da-ba/skyforge/commits/main","--jq",".sha"],
            ["gh","api",f"repos/ni-da-ba/skyforge/commits/{BRANCH}","--jq",".sha"],
            ["gh","pr","list","--repo","ni-da-ba/skyforge","--head",BRANCH,"--base","main","--state","all","--json",cli.PR_LIST_FIELDS,"--jq=."],
            ["gh","pr","create","--repo","ni-da-ba/skyforge","--base","main","--head",BRANCH,"--title",t.pr_title,"--body",t.pr_body],
            ["gh","pr","view","901","--repo","ni-da-ba/skyforge","--json",cli.PR_VIEW_FIELDS,"--jq=."],
            ["gh","pr","merge","901","--repo","ni-da-ba/skyforge","--merge","--match-head-commit",HEAD],
        ]
        for command in valid:
            with self.subTest(command=command):
                self.assertEqual(
                    cli.validate_gh_command(
                        command,
                        repo="ni-da-ba/skyforge",
                        task=t,
                        pr_number=901 if command[1]=="pr" and command[2] in {"view","merge"} else None,
                    ),
                    tuple(command),
                )

        forbidden = [
            ["git","push"],
            ["gh","issue","close","900"],
            ["gh","issue","comment","900","--body","x"],
            ["gh","workflow","run","CI"],
            ["gh","api","repos/ni-da-ba/skyforge/issues/900","--method","PATCH"],
            ["gh","pr","merge","901","--repo","ni-da-ba/skyforge","--merge"],
        ]
        for command in forbidden:
            with self.subTest(command=command), self.assertRaises(ValueError):
                cli.validate_gh_command(
                    command,
                    repo="ni-da-ba/skyforge",
                    task=t,
                    pr_number=901,
                )

    def test_cli_source_has_no_branch_push_file_edit_issue_or_codex_surface(self):
        source = Path(cli.__file__).read_text(encoding="utf-8")
        for token in (
            "git push",
            "gh issue",
            "workflow run",
            "openai_codex",
            "write_text(",
            "write_bytes(",
        ):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main()
