import importlib
import pathlib
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest import mock


MODULE_DIR = pathlib.Path(__file__).resolve().parent
if str(MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(MODULE_DIR))

quota_runtime = importlib.import_module("skyforge_quota_runtime")
core = quota_runtime.core


class ProviderQuotaGovernorRuntimeTests(unittest.TestCase):
    def make_orchestrator(self, root: pathlib.Path):
        o = core.Orchestrator.__new__(core.Orchestrator)
        o.root = root
        o.repo = "ni-da-ba/skyforge"
        o.state = core.LocalState(root / "state.json")
        o._state_lock = threading.RLock()
        o._dispatch_lock = threading.Lock()
        return o

    def test_authoritative_provider_allowance_ignores_separate_terra_daily_cap(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            o.state.data["budget_day"] = core._utc_day()
            o.state.data["worker_calls_today"] = 12
            o.state.data["terra_daily_limit_override"] = 12
            o.state.save()
            decision = {
                "authoritative": True,
                "allowed": True,
                "phase": "on_pace",
                "reason": "provider quota is within the sustainable pacing envelope",
            }
            with mock.patch.object(quota_runtime, "_provider_decision", return_value=decision):
                quota_runtime._consume_budget(o, "worker")
            self.assertEqual(o.state.data["worker_calls_today"], 13)
            self.assertEqual(
                o.state.data["metrics"]["provider_quota_governed_attempts"],
                1,
            )

    def test_authoritative_provider_allowance_ignores_separate_luna_daily_cap(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            o.state.data["budget_day"] = core._utc_day()
            o.state.data["classifier_calls_today"] = 60
            o.state.data["luna_worker_calls_today"] = 12
            o.state.data["luna_daily_limit_override"] = 72
            o.state.save()
            decision = {
                "authoritative": True,
                "allowed": True,
                "phase": "surplus",
                "reason": "provider quota is within the sustainable pacing envelope",
            }
            with mock.patch.object(quota_runtime, "_provider_decision", return_value=decision):
                quota_runtime._consume_budget(o, "classifier")
            self.assertEqual(o.state.data["classifier_calls_today"], 61)
            self.assertEqual(o.state.data["luna_worker_calls_today"], 12)

    def test_provider_pacing_block_does_not_consume_attempt(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            o.state.data["budget_day"] = core._utc_day()
            o.state.data["worker_calls_today"] = 3
            o.state.save()
            decision = {
                "authoritative": True,
                "allowed": False,
                "block_kind": "quota_pacing",
                "retry_after_seconds": 900,
                "phase": "weekly_catchup",
                "reason": "weekly Codex usage is ahead of the sustainable curve",
            }
            with mock.patch.object(quota_runtime, "_provider_decision", return_value=decision):
                with self.assertRaises(core.RetryBlocked) as raised:
                    quota_runtime._consume_budget(o, "worker")
            self.assertEqual(raised.exception.kind, "quota_pacing")
            self.assertEqual(raised.exception.retry_after_seconds, 900)
            self.assertEqual(o.state.data["worker_calls_today"], 3)

    def test_incomplete_or_failed_provider_signal_falls_back_to_local_caps(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            o.state.data["budget_day"] = core._utc_day()
            o.state.data["worker_calls_today"] = 4
            o.state.data["terra_daily_limit_override"] = 4
            o.state.save()
            with mock.patch.object(quota_runtime, "_provider_decision", return_value=None):
                with self.assertRaises(core.RetryBlocked) as raised:
                    quota_runtime._consume_budget(o, "worker")
            self.assertEqual(raised.exception.kind, "local_budget")
            self.assertEqual(o.state.data["worker_calls_today"], 4)

    def test_provider_probe_persists_decision_and_safe_snapshot(self):
        sample = {
            "captured_at": "2026-09-11T03:00:00+00:00",
            "ordinary_usage_allowed": True,
            "limits": [{
                "limit_id": "codex",
                "windows": [{
                    "window_kind": "weekly",
                    "remaining_percent": 80.0,
                    "used_percent": 20.0,
                    "window_duration_mins": 10080,
                    "resets_at": 2_000_000_000,
                }],
            }],
        }
        allowed = {
            "authoritative": True,
            "allowed": True,
            "phase": "surplus",
            "reason": "ok",
        }
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            with mock.patch.object(quota_runtime.codex_quota, "quota_snapshot", return_value=sample), mock.patch.object(
                quota_runtime, "_evaluate", return_value=allowed
            ):
                decision = quota_runtime._provider_decision(o, "classifier")
            self.assertEqual(decision, allowed)
            self.assertEqual(o.state.data["last_codex_quota_snapshot"], sample)
            self.assertEqual(o.state.data["quota_governor_mode"], "provider")
            self.assertEqual(
                o.state.data["last_quota_governor_decision"]["attempted_kind"],
                "classifier",
            )

    def test_merged_managed_handoff_can_be_retired_with_forensic_archive(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            worktree = root / "worker"
            worktree.mkdir()
            o = self.make_orchestrator(root)
            o.state.data["paused"] = True
            o.state.data["pending_worker"] = {
                "branch": "codex/content-test",
                "stage": "handoff",
                "managed_pr": 470,
                "worktree": str(worktree),
            }
            o.state.data["pending_decision"] = {"decision": {"decision": "DISPATCH", "pr_number": 470}}
            o.state.save()

            pr = {
                "state": "MERGED",
                "mergedAt": "2026-09-11T00:15:56Z",
                "headRefName": "codex/content-test",
                "headRefOid": "pr-head",
                "mergeCommit": {"oid": "merge-head"},
            }

            def fake_run(args, **kwargs):
                stdout = ""
                if args[:3] == ["git", "diff", "--binary"]:
                    stdout = "diff --git a/test b/test\n"
                elif args[:3] == ["git", "rev-parse", "HEAD"]:
                    stdout = "worker-head\n"
                return subprocess.CompletedProcess(args, 0, stdout=stdout, stderr="")

            with mock.patch.object(core, "_json_cmd", return_value=pr), mock.patch.object(
                core, "_run", side_effect=fake_run
            ), mock.patch.object(o, "_changed_paths", return_value=["test"]):
                quota_runtime.discard_pending_worker(o, actor="unit-test")

            self.assertIsNone(o.state.data["pending_worker"])
            record = o.state.data["last_worker_discard"]
            self.assertEqual(record["managed_pr"], 470)
            self.assertEqual(record["reason"], "managed PR already merged; stale local handoff retired with forensic archive")
            self.assertTrue((root / record["preserved_patch"]).exists())
            self.assertTrue((root / record["preserved_metadata"]).exists())
            # Cached decision/events remain for normal closed-PR invalidation/replay.
            self.assertIsNotNone(o.state.data["pending_decision"])

    def test_open_managed_pr_cannot_use_terminal_retirement_path(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            worktree = root / "worker"
            worktree.mkdir()
            o = self.make_orchestrator(root)
            o.state.data["paused"] = True
            o.state.data["pending_worker"] = {
                "branch": "codex/content-test",
                "stage": "handoff",
                "managed_pr": 470,
                "worktree": str(worktree),
            }
            o.state.save()
            with mock.patch.object(
                core,
                "_json_cmd",
                return_value={"state": "OPEN", "mergedAt": None},
            ):
                with self.assertRaisesRegex(RuntimeError, "not merged"):
                    quota_runtime.discard_pending_worker(o, actor="unit-test")
            self.assertIsNotNone(o.state.data["pending_worker"])

    def test_runtime_paths_include_governor(self):
        self.assertIn(
            "scripts/orchestrator/quota_governor.py",
            core.CONTROLLER_RUNTIME_PATHS,
        )


if __name__ == "__main__":
    unittest.main()
