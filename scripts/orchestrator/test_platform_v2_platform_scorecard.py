from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

from v2.platform_scorecard import build_compact_scorecard, load_latest_value_report


class PlatformScorecardTest(unittest.TestCase):
    def test_missing_value_telemetry_is_explicitly_unavailable(self):
        with tempfile.TemporaryDirectory() as td:
            value = load_latest_value_report(Path(td))
            self.assertFalse(value["available"])
            self.assertIn("no persisted hosted value report", value["reason"])

    def test_scorecard_counts_existing_evidence_without_inventing_slos(self):
        score = build_compact_scorecard(
            objectives=({"proposal_id": "a"}, {"proposal_id": "b"}),
            objective_controls=(
                {"state": "PAUSED"},
                {"state": "CANCELLED"},
            ),
            workers=(
                {"status": "HANDOFF_READY"},
                {"status": "FAILED"},
                {"status": "INTERRUPTED"},
            ),
            completions=({"completion_id": "c"},),
            scheduler=(
                {"state": "EXECUTING"},
                {"state": "WAIT_LIMIT"},
                {"state": "RECOVERY_REQUIRED"},
            ),
            reviews=(
                {"verdict": "ACCEPTED"},
                {"verdict": "CHANGES_REQUIRED"},
            ),
            external_claims=({"state": "active"}, {"state": "released"}),
            budget={"used": 2, "limit": 6},
            hosted_value={"available": False, "reason": "not reported"},
        )
        self.assertEqual(score["objectives"], {
            "total": 2,
            "paused": 1,
            "cancelled": 1,
            "controlled": 2,
        })
        self.assertEqual(score["workers"]["handoff_ready"], 1)
        self.assertEqual(score["scheduler"]["waiting"], 1)
        self.assertEqual(score["human_reviews"]["accepted"], 1)
        self.assertEqual(score["external_claims"]["active"], 1)
        self.assertTrue(score["budget"]["local"]["available"])
        self.assertEqual(score["budget"]["local"]["usage"]["used"], 2)
        self.assertFalse(score["budget"]["provider"]["available"])
        self.assertIn("not durably persisted", score["budget"]["provider"]["reason"])
        self.assertFalse(score["hosted_value"]["available"])
        self.assertIn("No arbitrary SLO thresholds", score["slo_note"])


if __name__ == "__main__":
    unittest.main()
