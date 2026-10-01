import unittest

import quota_governor


NOW = 1_000_000
WEEK_SECONDS = 7 * 24 * 60 * 60
FIVE_HOUR_SECONDS = 5 * 60 * 60


def snapshot(*, weekly_remaining=60.0, five_hour_remaining=70.0, weekly_seconds=None,
             five_hour_seconds=None, ordinary_usage_allowed=True):
    weekly_seconds = WEEK_SECONDS // 2 if weekly_seconds is None else weekly_seconds
    five_hour_seconds = FIVE_HOUR_SECONDS // 2 if five_hour_seconds is None else five_hour_seconds
    return {
        "ordinary_usage_allowed": ordinary_usage_allowed,
        "limits": [
            {
                "limit_id": "codex",
                "normal_model_slug": "gpt-5.6",
                "windows": [
                    {
                        "window_kind": "five_hour",
                        "remaining_percent": five_hour_remaining,
                        "used_percent": 100.0 - five_hour_remaining,
                        "window_duration_mins": 300,
                        "resets_at": NOW + five_hour_seconds,
                    },
                    {
                        "window_kind": "weekly",
                        "remaining_percent": weekly_remaining,
                        "used_percent": 100.0 - weekly_remaining,
                        "window_duration_mins": 10080,
                        "resets_at": NOW + weekly_seconds,
                    },
                ],
            }
        ],
    }


class QuotaGovernorTests(unittest.TestCase):
    def test_ahead_of_weekly_curve_is_allowed(self):
        decision = quota_governor.evaluate_quota(snapshot(), now_epoch=NOW)
        self.assertTrue(decision["authoritative"])
        self.assertTrue(decision["allowed"])
        self.assertEqual(decision["phase"], "surplus")
        # Half a week remains: 10% reserve + 90% * .5 - 5% burst = 50% target.
        self.assertAlmostEqual(decision["weekly"]["target_remaining_percent"], 50.0)
        self.assertAlmostEqual(decision["weekly"]["headroom_vs_target_percent"], 10.0)

    def test_weekly_overspend_defers_until_curve_catches_up(self):
        decision = quota_governor.evaluate_quota(
            snapshot(weekly_remaining=40.0),
            now_epoch=NOW,
        )
        self.assertTrue(decision["authoritative"])
        self.assertFalse(decision["allowed"])
        self.assertEqual(decision["phase"], "weekly_catchup")
        self.assertEqual(decision["block_kind"], "quota_pacing")
        self.assertGreater(decision["retry_after_seconds"], 60)
        self.assertLess(decision["retry_after_seconds"], WEEK_SECONDS // 2)

    def test_five_hour_window_is_a_burst_guard(self):
        decision = quota_governor.evaluate_quota(
            snapshot(weekly_remaining=80.0, five_hour_remaining=20.0),
            now_epoch=NOW,
        )
        self.assertTrue(decision["authoritative"])
        self.assertFalse(decision["allowed"])
        self.assertEqual(decision["phase"], "five_hour_reserve")
        self.assertEqual(decision["retry_after_seconds"], FIVE_HOUR_SECONDS // 2)

    def test_weekly_reserve_hard_stops_until_reset(self):
        decision = quota_governor.evaluate_quota(
            snapshot(weekly_remaining=9.0, weekly_seconds=900),
            now_epoch=NOW,
        )
        self.assertTrue(decision["authoritative"])
        self.assertFalse(decision["allowed"])
        self.assertEqual(decision["phase"], "weekly_reserve")
        self.assertEqual(decision["retry_after_seconds"], 900)

    def test_missing_weekly_window_falls_back_instead_of_guessing(self):
        value = snapshot()
        value["limits"][0]["windows"] = [value["limits"][0]["windows"][0]]
        decision = quota_governor.evaluate_quota(value, now_epoch=NOW)
        self.assertFalse(decision["authoritative"])
        self.assertFalse(decision["allowed"])
        self.assertEqual(decision["phase"], "fallback_local")

    def test_past_reset_is_stale_and_falls_back(self):
        value = snapshot(weekly_seconds=-1)
        decision = quota_governor.evaluate_quota(value, now_epoch=NOW)
        self.assertFalse(decision["authoritative"])
        self.assertEqual(decision["phase"], "fallback_local")

    def test_provider_denial_is_authoritative_even_without_complete_windows(self):
        value = snapshot(ordinary_usage_allowed=False)
        value["limits"] = []
        decision = quota_governor.evaluate_quota(value, now_epoch=NOW)
        self.assertTrue(decision["authoritative"])
        self.assertFalse(decision["allowed"])
        self.assertEqual(decision["block_kind"], "quota")
        self.assertEqual(decision["phase"], "provider_blocked")

    def test_preferred_limit_id_wins_when_multiple_buckets_exist(self):
        value = snapshot()
        secondary = {
            "limit_id": "special",
            "windows": value["limits"][0]["windows"],
        }
        value["limits"].append(secondary)
        selected = quota_governor.select_limit(value, preferred_limit_id="special")
        self.assertEqual(selected["limit_id"], "special")


if __name__ == "__main__":
    unittest.main()
