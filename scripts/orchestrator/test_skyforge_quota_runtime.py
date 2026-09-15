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

    def test_protected_task_authority_uses_larger_burst_margin(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            event = core.EventDecision(
                True,
                "explicit task",
                "issue_comment",
                action="audit_signal",
                pr_number=493,
                signal_kind="task",
                signal_text="AUDIT — NEW IMPLEMENTATION TASK",
            )
            o.state.data["pending_events"] = [event.to_state()]
            o.state.save()

            with mock.patch.dict(
                quota_runtime.os.environ,
                {
                    "SKYFORGE_WEEKLY_BURST_MARGIN_PERCENT": "5",
                    "SKYFORGE_PROTECTED_WEEKLY_BURST_MARGIN_PERCENT": "10",
                },
                clear=False,
            ):
                self.assertTrue(quota_runtime._protected_authority_attempt(o, "classifier"))
                self.assertEqual(
                    quota_runtime._governor_settings(protected_authority=True)[
                        "weekly_burst_margin_percent"
                    ],
                    10.0,
                )
                self.assertEqual(
                    quota_runtime._governor_settings(protected_authority=False)[
                        "weekly_burst_margin_percent"
                    ],
                    5.0,
                )

    def test_runtime_refresh_invalidates_stale_quota_pacing_timer(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            o.state.data["blocked_kind"] = "quota_pacing"
            o.state.data["blocked_until_epoch"] = 9999999999.0
            o.state.data["blocked_reason"] = "old pacing policy"
            o.state.save()
            with mock.patch.object(quota_runtime, "_ORIGINAL_REFRESH_RUNTIME") as refresh:
                quota_runtime.refresh_runtime(o, actor="operator")

            refresh.assert_called_once_with(o, actor="operator")
            self.assertIsNone(o.state.data["blocked_kind"])
            self.assertEqual(o.state.data["blocked_until_epoch"], 0.0)
            self.assertIsNone(o.state.data["blocked_reason"])

    def test_fresh_provider_admission_clears_stale_quota_pacing_block(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            o.state.data["blocked_kind"] = "quota_pacing"
            o.state.data["blocked_until_epoch"] = 9999999999.0
            o.state.data["blocked_reason"] = "old policy"
            o.state.save()
            with mock.patch.object(
                quota_runtime,
                "_provider_decision",
                return_value={"authoritative": True, "allowed": True},
            ), mock.patch.object(quota_runtime, "_record_governed_attempt") as record:
                quota_runtime._consume_budget(o, "classifier")

            self.assertIsNone(o.state.data["blocked_kind"])
            self.assertEqual(o.state.data["blocked_until_epoch"], 0.0)
            self.assertIsNone(o.state.data["blocked_reason"])
            record.assert_called_once_with(o, "classifier")

    def test_task_owned_worker_is_protected_quota_attempt(self):
        with tempfile.TemporaryDirectory() as td:
            o = self.make_orchestrator(pathlib.Path(td))
            o.state.data["pending_worker"] = {
                "stage": "editing",
                "authority_key": "task:493",
            }
            o.state.save()
            self.assertTrue(quota_runtime._protected_authority_attempt(o, "worker"))

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
