from __future__ import annotations

import sys
from pathlib import Path
import subprocess
import tempfile
import types
import unittest

from v2.worker_provider import (
    CodexWorkerProvider,
    _apply_controller_patch,
    _extract_controller_patch,
    FrozenWorkerSpec,
    WorkerAdvanceDisposition,
    WorkerProviderConfig,
    WorkerProviderError,
    WorkerRunStatus,
    WorkerRunStore,
    WorkerTier,
    advance_worker_run,
    deterministic_worker_branch,
    provider_config_for_tier,
)
from v2.worker_workspace import WorkerWorkspaceManager


BASE_SPEC_HASH = "a" * 64
ATTEMPT = "b" * 64


def spec(*, base_sha: str, tier: WorkerTier = WorkerTier.TERRA) -> FrozenWorkerSpec:
    return FrozenWorkerSpec(
        task_id="R5C3-fixture",
        authority_key="issue:856",
        task_spec_hash=BASE_SPEC_HASH,
        attempt_id=ATTEMPT,
        lane="Implementation",
        objective="exercise durable provider boundary",
        stop_boundary="leave one bounded file change and stop",
        base_sha=base_sha,
        allowed_paths=("allowed.txt",),
        protected_paths=("docs/agent-state/PLATFORM_V2_RELEASE5_GATE.json",),
        tier=tier,
        context_text="Fixture context only.",
    )


class FakeProvider:
    def __init__(self, *, summary="worker complete", error=None):
        self.calls = 0
        self.summary = summary
        self.error = error

    def run(self, *, spec, worktree, config):
        self.calls += 1
        if self.error is not None:
            raise self.error
        return self.summary


class InjectedControllerCrash(BaseException):
    pass


class CrashProvider:
    def __init__(self):
        self.calls = 0

    def run(self, *, spec, worktree, config):
        self.calls += 1
        (Path(worktree) / "allowed.txt").write_text("partial provider work\n", encoding="utf-8")
        raise InjectedControllerCrash("process died after workspace mutation")


class WorkerProviderStateTest(unittest.TestCase):
    def test_tier_defaults_match_accepted_legacy_policy(self):
        luna = provider_config_for_tier(WorkerTier.LUNA, {})
        terra = provider_config_for_tier(WorkerTier.TERRA, {})
        self.assertEqual((luna.model, luna.reasoning_effort), ("gpt-5.6-luna", "low"))
        self.assertEqual((terra.model, terra.reasoning_effort), ("gpt-5.6-terra", "medium"))

        custom = provider_config_for_tier(
            WorkerTier.LUNA,
            {
                "SKYFORGE_ORCHESTRATOR_MODEL": "fallback-luna",
                "SKYFORGE_LUNA_WORKER_REASONING": "high",
            },
        )
        self.assertEqual(custom.model, "fallback-luna")
        self.assertEqual(custom.reasoning_effort, "high")

    def test_deterministic_branch_is_stable_and_attempt_bound(self):
        first = deterministic_worker_branch("Implementation", "R5C3 fixture", ATTEMPT)
        again = deterministic_worker_branch("Implementation", "R5C3 fixture", ATTEMPT)
        changed = deterministic_worker_branch("Implementation", "R5C3 fixture", "c" * 64)
        self.assertEqual(first, again)
        self.assertNotEqual(first, changed)
        self.assertTrue(first.startswith("codex/implementation-r5c3-fixture-"))

    def test_provider_success_is_durable_and_never_recalled(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            worktree = root / "worktree"
            worktree.mkdir()
            store = WorkerRunStore.for_root(root / "state")
            s = spec(base_sha="d" * 40)
            provider = FakeProvider()

            first = advance_worker_run(
                spec=s,
                worktree=worktree,
                store=store,
                provider=provider,
                config=WorkerProviderConfig(WorkerTier.TERRA, "model", "medium"),
            )
            self.assertEqual(first.disposition, WorkerAdvanceDisposition.HANDOFF_READY)
            self.assertEqual(first.record.status, WorkerRunStatus.HANDOFF_READY)
            self.assertEqual(provider.calls, 1)

            second = advance_worker_run(
                spec=s,
                worktree=worktree,
                store=store,
                provider=provider,
                config=WorkerProviderConfig(WorkerTier.TERRA, "model", "medium"),
            )
            self.assertEqual(second.disposition, WorkerAdvanceDisposition.ALREADY_READY)
            self.assertEqual(provider.calls, 1)

    def test_crash_while_running_never_automatically_calls_provider_again(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            worktree = root / "worktree"
            worktree.mkdir()
            (worktree / "allowed.txt").write_text("base\n", encoding="utf-8")
            store = WorkerRunStore.for_root(root / "state")
            s = spec(base_sha="d" * 40)
            provider = CrashProvider()
            cfg = WorkerProviderConfig(WorkerTier.TERRA, "model", "medium")

            with self.assertRaises(InjectedControllerCrash):
                advance_worker_run(
                    spec=s,
                    worktree=worktree,
                    store=store,
                    provider=provider,
                    config=cfg,
                )
            durable = store.load().find_attempt(ATTEMPT)
            self.assertEqual(durable.status, WorkerRunStatus.RUNNING)
            self.assertEqual(provider.calls, 1)
            self.assertEqual(
                (worktree / "allowed.txt").read_text(encoding="utf-8"),
                "partial provider work\n",
            )

            restarted = advance_worker_run(
                spec=s,
                worktree=worktree,
                store=store,
                provider=provider,
                config=cfg,
            )
            self.assertEqual(
                restarted.disposition,
                WorkerAdvanceDisposition.RECOVERY_REQUIRED,
            )
            self.assertEqual(restarted.record.status, WorkerRunStatus.INTERRUPTED)
            self.assertEqual(provider.calls, 1)
            self.assertEqual(
                (worktree / "allowed.txt").read_text(encoding="utf-8"),
                "partial provider work\n",
            )

            third = advance_worker_run(
                spec=s,
                worktree=worktree,
                store=store,
                provider=provider,
                config=cfg,
            )
            self.assertEqual(third.disposition, WorkerAdvanceDisposition.RECOVERY_REQUIRED)
            self.assertEqual(provider.calls, 1)

    def test_classified_provider_failure_is_durable_and_not_auto_retried(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            worktree = root / "worktree"
            worktree.mkdir()
            store = WorkerRunStore.for_root(root / "state")
            s = spec(base_sha="d" * 40)
            provider = FakeProvider(
                error=WorkerProviderError("provider_capacity", 900, "capacity")
            )
            cfg = WorkerProviderConfig(WorkerTier.TERRA, "model", "medium")
            first = advance_worker_run(
                spec=s,
                worktree=worktree,
                store=store,
                provider=provider,
                config=cfg,
            )
            self.assertEqual(first.disposition, WorkerAdvanceDisposition.FAILED)
            self.assertEqual(first.record.failure_kind, "provider_capacity")
            self.assertEqual(first.record.retry_after_seconds, 900)
            self.assertEqual(provider.calls, 1)

            again = advance_worker_run(
                spec=s,
                worktree=worktree,
                store=store,
                provider=provider,
                config=cfg,
            )
            self.assertEqual(again.disposition, WorkerAdvanceDisposition.FAILED)
            self.assertEqual(provider.calls, 1)

    def test_identity_drift_rejected_before_provider(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            worktree = root / "worktree"
            worktree.mkdir()
            store = WorkerRunStore.for_root(root / "state")
            first_spec = spec(base_sha="d" * 40)
            cfg = WorkerProviderConfig(WorkerTier.TERRA, "model", "medium")
            provider = FakeProvider()
            advance_worker_run(
                spec=first_spec,
                worktree=worktree,
                store=store,
                provider=provider,
                config=cfg,
            )
            changed_spec = FrozenWorkerSpec(
                task_id=first_spec.task_id,
                authority_key=first_spec.authority_key,
                task_spec_hash="e" * 64,
                attempt_id=first_spec.attempt_id,
                lane=first_spec.lane,
                objective=first_spec.objective,
                stop_boundary=first_spec.stop_boundary,
                base_sha=first_spec.base_sha,
                allowed_paths=first_spec.allowed_paths,
                tier=first_spec.tier,
            )
            with self.assertRaisesRegex(ValueError, "identity drifted"):
                advance_worker_run(
                    spec=changed_spec,
                    worktree=worktree,
                    store=store,
                    provider=provider,
                    config=cfg,
                )
            self.assertEqual(provider.calls, 1)


class WorkerWorkspaceManagerTest(unittest.TestCase):
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
        self.git(root, "config", "user.name", "Skyforge R5C3 Test")
        (root / ".gitignore").write_text(".skyforge-platform-v2/\n", encoding="utf-8")
        (root / "allowed.txt").write_text("base\n", encoding="utf-8")
        self.git(root, "add", ".gitignore", "allowed.txt")
        self.git(root, "commit", "-m", "base")
        return self.git(root, "rev-parse", "HEAD")

    def test_prepare_creates_exact_isolated_worktree(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            base = self.make_repo(root)
            s = spec(base_sha=base)
            manager = WorkerWorkspaceManager(root=root)
            prepared = manager.prepare(s)
            self.assertNotEqual(prepared.worktree, root.resolve())
            self.assertEqual(prepared.branch, s.branch)
            self.assertEqual(prepared.base_sha, base)
            self.assertEqual(
                self.git(prepared.worktree, "branch", "--show-current"),
                s.branch,
            )
            self.assertEqual(self.git(prepared.worktree, "rev-parse", "HEAD"), base)
            self.assertEqual(self.git(prepared.worktree, "status", "--porcelain"), "")

    def test_existing_dirty_worktree_is_preserved_and_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            base = self.make_repo(root)
            s = spec(base_sha=base)
            manager = WorkerWorkspaceManager(root=root)
            prepared = manager.prepare(s)
            (prepared.worktree / "allowed.txt").write_text("partial\n", encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "dirty"):
                manager.prepare(s)
            self.assertEqual(
                (prepared.worktree / "allowed.txt").read_text(encoding="utf-8"),
                "partial\n",
            )

    def test_branch_collision_without_expected_worktree_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            base = self.make_repo(root)
            s = spec(base_sha=base)
            self.git(root, "branch", s.branch, base)
            manager = WorkerWorkspaceManager(root=root)
            with self.assertRaisesRegex(RuntimeError, "branch already exists"):
                manager.prepare(s)

    def test_verify_rejects_noncanonical_worktree_path(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            base = self.make_repo(root)
            s = spec(base_sha=base)
            manager = WorkerWorkspaceManager(root=root)
            manager.prepare(s)
            with self.assertRaisesRegex(RuntimeError, "deterministic identity"):
                manager.verify_for_run(s, root)


class CodexProviderAdapterTest(unittest.TestCase):
    def test_codex_adapter_uses_read_only_patch_transport(self):
        calls = {}
        module = types.ModuleType("openai_codex")

        class Sandbox:
            workspace_write = object()

        class Result:
            final_response = "bounded completion summary"

        class Thread:
            def run(self, prompt, *, sandbox):
                calls["prompt"] = prompt
                calls["run_sandbox"] = sandbox
                return Result()

        class Codex:
            def __enter__(self):
                return self
            def __exit__(self, *args):
                return False
            def thread_start(self, **kwargs):
                calls["start"] = kwargs
                return Thread()

        module.Codex = Codex
        module.Sandbox = Sandbox
        prior = sys.modules.get("openai_codex")
        sys.modules["openai_codex"] = module
        try:
            with tempfile.TemporaryDirectory() as td:
                worktree = Path(td)
                s = spec(base_sha="d" * 40)
                config = WorkerProviderConfig(
                    tier=WorkerTier.TERRA,
                    model="gpt-fixture",
                    reasoning_effort="high",
                )
                summary = CodexWorkerProvider().run(
                    spec=s,
                    worktree=worktree,
                    config=config,
                )
                self.assertEqual(summary, "bounded completion summary")
                self.assertEqual(calls["start"]["cwd"], str(worktree.resolve()))
                self.assertEqual(calls["start"]["model"], "gpt-fixture")
                self.assertEqual(
                    calls["start"]["config"],
                    {"model_reasoning_effort": "high"},
                )
                self.assertTrue(calls["start"]["ephemeral"])
                self.assertIs(calls["start"]["sandbox"], Sandbox.workspace_write)
                self.assertIs(calls["run_sandbox"], Sandbox.workspace_write)
                self.assertIn("Do not commit, push", calls["prompt"])
                self.assertIn("network access", calls["prompt"].lower())
                self.assertIn(
                    "Do not commit, push, create/merge PRs, mutate GitHub, or use network access.",
                    CodexWorkerProvider.DEVELOPER_INSTRUCTIONS,
                )
        finally:
            if prior is None:
                sys.modules.pop("openai_codex", None)
            else:
                sys.modules["openai_codex"] = prior

    def test_hosted_runtime_does_not_import_worker_provider(self):
        root = Path(__file__).resolve().parent
        source = (root / "platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        self.assertNotIn("worker_provider", source)
        self.assertNotIn("worker_workspace", source)


if __name__ == "__main__":
    unittest.main()
