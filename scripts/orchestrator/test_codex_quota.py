import importlib.util
import json
import pathlib
import sys
import tempfile
import textwrap
import unittest


MODULE_PATH = pathlib.Path(__file__).with_name("codex_quota.py")
SPEC = importlib.util.spec_from_file_location("skyforge_codex_quota", MODULE_PATH)
quota = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(quota)


class CodexQuotaNormalizationTests(unittest.TestCase):
    def test_normalizes_five_hour_and_weekly_by_duration(self):
        raw = {
            "ordinaryUsageAllowed": True,
            "accountId": "acct-secret-ish-identifier",
            "rateLimitUpsell": {"doNotExpose": "opaque"},
            "rateLimitResetCredits": {
                "availableCount": 2,
                "credits": [{"id": "opaque-credit"}],
            },
            "rateLimits": {
                "limitId": "codex",
                "limitName": "Codex",
                "normalModelSlug": "gpt-test",
                "planType": "plus",
                "primary": {
                    "usedPercent": 29,
                    "windowDurationMins": 300,
                    "resetsAt": 1789087200,
                },
                "secondary": {
                    "usedPercent": 9,
                    "windowDurationMins": 10080,
                    "resetsAt": 1789500000,
                },
            },
        }
        result = quota.normalize_rate_limits(raw, captured_at="2026-09-10T23:00:00+00:00")
        self.assertTrue(result["ordinary_usage_allowed"])
        self.assertTrue(result["account_id_present"])
        self.assertEqual(result["available_reset_credits"], 2)
        self.assertEqual(len(result["limits"]), 1)
        windows = result["limits"][0]["windows"]
        self.assertEqual(windows[0]["window_kind"], "five_hour")
        self.assertEqual(windows[0]["remaining_percent"], 71.0)
        self.assertEqual(windows[1]["window_kind"], "weekly")
        self.assertEqual(windows[1]["remaining_percent"], 91.0)
        serialized = json.dumps(result)
        self.assertNotIn("acct-secret-ish-identifier", serialized)
        self.assertNotIn("opaque-credit", serialized)
        self.assertNotIn("doNotExpose", serialized)

    def test_preserves_multiple_limit_ids_and_unknown_window_durations(self):
        raw = {
            "ordinaryUsageAllowed": True,
            "rateLimits": {
                "limitId": "codex",
                "primary": {"usedPercent": 25, "windowDurationMins": 300, "resetsAt": 10},
            },
            "rateLimitsByLimitId": {
                "codex": {
                    "limitId": "codex",
                    "primary": {"usedPercent": 25, "windowDurationMins": 300, "resetsAt": 10},
                },
                "codex_other": {
                    "limitId": "codex_other",
                    "primary": {"usedPercent": 40, "windowDurationMins": 1440, "resetsAt": 20},
                },
            },
        }
        result = quota.normalize_rate_limits(raw)
        self.assertEqual([item["limit_id"] for item in result["limits"]], ["codex", "codex_other"])
        other = result["limits"][1]["windows"][0]
        self.assertEqual(other["window_kind"], "rolling_1440m")
        self.assertEqual(other["remaining_percent"], 60.0)

    def test_clamps_backend_percentages_to_safe_display_range(self):
        self.assertEqual(
            quota.normalize_window(
                {"usedPercent": 105, "windowDurationMins": 300, "resetsAt": None},
                position="primary",
            )["remaining_percent"],
            0.0,
        )
        self.assertEqual(
            quota.normalize_window(
                {"usedPercent": -2, "windowDurationMins": 300, "resetsAt": None},
                position="primary",
            )["remaining_percent"],
            100.0,
        )


class CodexQuotaTransportTests(unittest.TestCase):
    def _fake_server(self, directory: pathlib.Path, response: dict) -> pathlib.Path:
        path = directory / "fake_codex_app_server.py"
        path.write_text(
            textwrap.dedent(
                f"""
                import json
                import sys

                response = {response!r}
                initialized = False
                for line in sys.stdin:
                    message = json.loads(line)
                    if message.get("method") == "initialize":
                        print(json.dumps({{"method": "server/notification", "params": {{"noise": True}}}}), flush=True)
                        print(json.dumps({{"id": message["id"], "result": {{"serverInfo": {{"name": "fake", "version": "1"}}}}}}), flush=True)
                    elif message.get("method") == "initialized":
                        initialized = True
                    elif message.get("method") == "account/rateLimits/read":
                        assert initialized
                        assert message.get("params", {{}}).get("excludeResetCreditDetails") is True
                        print(json.dumps({{"id": message["id"], "result": response}}), flush=True)
                        break
                """
            )
        )
        return path

    def test_reads_quota_over_stdio_without_model_turn(self):
        fixture = {
            "ordinaryUsageAllowed": True,
            "rateLimits": {
                "limitId": "codex",
                "primary": {"usedPercent": 12, "windowDurationMins": 300, "resetsAt": 100},
                "secondary": {"usedPercent": 34, "windowDurationMins": 10080, "resetsAt": 200},
            },
        }
        with tempfile.TemporaryDirectory() as td:
            server = self._fake_server(pathlib.Path(td), fixture)
            raw = quota.read_codex_rate_limits(
                command=[sys.executable, str(server)],
                timeout_seconds=5,
            )
        self.assertEqual(raw, fixture)

    def test_backend_error_surfaces_code_without_backend_message(self):
        with tempfile.TemporaryDirectory() as td:
            server = pathlib.Path(td) / "fake_error_server.py"
            server.write_text(
                textwrap.dedent(
                    """
                    import json
                    import sys
                    initialized = False
                    for line in sys.stdin:
                        message = json.loads(line)
                        if message.get("method") == "initialize":
                            print(json.dumps({"id": message["id"], "result": {}}), flush=True)
                        elif message.get("method") == "initialized":
                            initialized = True
                        elif message.get("method") == "account/rateLimits/read":
                            print(json.dumps({"id": message["id"], "error": {"code": 401, "message": "Bearer SECRET_TOKEN"}}), flush=True)
                            break
                    """
                )
            )
            with self.assertRaises(quota.CodexQuotaError) as ctx:
                quota.read_codex_rate_limits(
                    command=[sys.executable, str(server)],
                    timeout_seconds=5,
                )
        self.assertIn("code 401", str(ctx.exception))
        self.assertNotIn("SECRET_TOKEN", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
