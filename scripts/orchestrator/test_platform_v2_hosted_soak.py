from __future__ import annotations

from pathlib import Path
import unittest

from v2.hosted_soak import run_fresh_disposable_soak


# Current-runtime reproducibility sentinel. Historical R5C27 evidence retains its
# original accepted digest under docs/operations/evidence/.
EXPECTED_SOAK_DIGEST = "583534a105d9e059b4f8363146391a17e093a62193d9bf6819d7eaf9fb542a14"
EXPECTED_FAILURE_DIGEST = "f5da73a30607260753386ba2cdd6da44bf076a0dc0c1bd306f978046bce22064"


class DisposableHostedSoakTest(unittest.TestCase):
    def test_full_background_soak_is_reproducible_and_production_is_untouched(self):
        report, failure = run_fresh_disposable_soak()
        self.assertEqual(report.digest, EXPECTED_SOAK_DIGEST)
        self.assertEqual(failure.digest, EXPECTED_FAILURE_DIGEST)

        stable = report.stable
        self.assertEqual(len(stable["tasks"]), 3)
        self.assertEqual(stable["provider_calls"], {"classifier": 3, "worker": 3})
        self.assertEqual(stable["ordinary_effect_execute_counts"], [1] * 6)
        self.assertEqual(stable["lifecycle_execute_counts"], {"ready": 3, "merge": 3})
        self.assertEqual(stable["duplicate_delivery_suppressed"], 1)
        self.assertEqual(stable["semantic_replay_suppressed"], 1)
        self.assertGreaterEqual(stable["restart_count"], 2)
        self.assertEqual(stable["final_pending_event_count"], 0)
        self.assertFalse(stable["final_active_plan"])
        self.assertFalse(stable["production_systemd_touched"])
        self.assertFalse(stable["production_caddy_touched"])
        self.assertFalse(stable["production_writer_authority_touched"])
        self.assertFalse(stable["dr70_consumed_or_passed"])

        self.assertEqual(failure.classifier_calls, 1)
        self.assertEqual(failure.classifier_budget_delta, 1)
        self.assertEqual(failure.worker_calls, 0)
        self.assertEqual(failure.remote_effect_count, 0)
        self.assertEqual(failure.durable_status, "FAILED")

    def test_soak_module_has_no_service_manager_or_live_provider_surface(self):
        root = Path(__file__).resolve().parent
        source = (root / "v2/hosted_soak.py").read_text(encoding="utf-8")
        for token in (
            '"systemctl"',
            "'systemctl'",
            "CodexClassifierProvider",
            "CodexWorkerProvider",
            "GhGitOrdinaryEffectAdapter",
        ):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main()
