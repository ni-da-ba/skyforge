import importlib
import pathlib
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


class SkyforgeQuotaRuntimeTests(unittest.TestCase):
    def trusted_payload(self, body: str):
        return {
            "action": "created",
            "comment": {"body": body, "user": {"login": "ni-da-ba"}},
            "issue": {"number": 378},
        }

    def test_quota_command_requires_trusted_exact_command(self):
        payload = self.trusted_payload("/skyforge-quota")
        self.assertEqual(
            core.classify_control_command("issue_comment", payload),
            "quota",
        )
        payload["comment"]["user"]["login"] = "untrusted-user"
        self.assertIsNone(core.classify_control_command("issue_comment", payload))
        payload["comment"]["user"]["login"] = "ni-da-ba"
        payload["comment"]["body"] = "/skyforge-quota please"
        self.assertIsNone(core.classify_control_command("issue_comment", payload))

    def make_orchestrator(self, root: pathlib.Path):
        o = core.Orchestrator.__new__(core.Orchestrator)
        o.root = root
        o.repo = "ni-da-ba/skyforge"
        o.state = core.LocalState(root / "state.json")
        o._state_lock = threading.RLock()
        return o

    def test_post_quota_persists_and_posts_only_redacted_snapshot(self):
        sample = {
            "captured_at": "2026-09-10T23:00:00+00:00",
            "ordinary_usage_allowed": True,
            "account_id_present": True,
            "available_reset_credits": None,
            "limits": [
                {
                    "limit_id": "codex",
                    "limit_name": None,
                    "normal_model_slug": None,
                    "plan_type": "plus",
                    "rate_limit_reached_type": None,
                    "spend_control_reached": None,
                    "windows": [
                        {
                            "position": "primary",
                            "window_kind": "five_hour",
                            "used_percent": 29.0,
                            "remaining_percent": 71.0,
                            "window_duration_mins": 300,
                            "resets_at": 123,
                        }
                    ],
                }
            ],
        }
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            calls = []

            def fake_run(args, **kwargs):
                calls.append(args)
                return core.subprocess.CompletedProcess(args, 0, stdout="", stderr="")

            with mock.patch.object(quota_runtime.codex_quota, "quota_snapshot", return_value=sample), mock.patch.object(
                core, "_run", side_effect=fake_run
            ):
                o.post_quota(378)

            self.assertEqual(o.state.data["last_codex_quota_snapshot"], sample)
            self.assertIsNone(o.state.data["last_codex_quota_error"])
            gh = [args for args in calls if args[:3] == ["gh", "issue", "comment"]]
            self.assertEqual(len(gh), 1)
            body = gh[0][gh[0].index("--body") + 1]
            self.assertIn("[skyforge-orchestrator] QUOTA", body)
            self.assertIn('"remaining_percent": 71.0', body)
            self.assertNotIn("accountId", body)

    def test_quota_probe_failure_is_redacted_and_does_not_raise(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            calls = []

            def fake_run(args, **kwargs):
                calls.append(args)
                return core.subprocess.CompletedProcess(args, 0, stdout="", stderr="")

            failure = quota_runtime.codex_quota.CodexQuotaError(
                "Codex app-server rejected quota request (code 401)"
            )
            with mock.patch.object(quota_runtime.codex_quota, "quota_snapshot", side_effect=failure), mock.patch.object(
                core, "_run", side_effect=fake_run
            ):
                o.post_quota(378)

            error = o.state.data["last_codex_quota_error"]
            self.assertEqual(error["kind"], "CodexQuotaError")
            gh = [args for args in calls if args[:3] == ["gh", "issue", "comment"]]
            body = gh[0][gh[0].index("--body") + 1]
            self.assertIn("[skyforge-orchestrator] QUOTA_ERROR", body)
            self.assertIn("code 401", body)

    def test_health_snapshot_exposes_last_safe_quota_sample(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            o.state.data["last_codex_quota_snapshot"] = {"ordinary_usage_allowed": True}
            o.state.data["last_codex_quota_error"] = None
            o.state.save()
            with mock.patch.object(
                quota_runtime, "_ORIGINAL_HEALTH_SNAPSHOT", return_value={"status": "ok"}
            ):
                value = quota_runtime.health_snapshot(o)
            self.assertEqual(
                value["last_codex_quota_snapshot"],
                {"ordinary_usage_allowed": True},
            )

    def test_runtime_paths_include_quota_extension(self):
        self.assertIn(
            "scripts/orchestrator/codex_quota.py",
            core.CONTROLLER_RUNTIME_PATHS,
        )
        self.assertIn(
            "scripts/orchestrator/skyforge_quota_runtime.py",
            core.CONTROLLER_RUNTIME_PATHS,
        )


if __name__ == "__main__":
    unittest.main()
