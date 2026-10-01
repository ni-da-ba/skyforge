from __future__ import annotations

import unittest

from v2.pre_handoff import (
    HOSTED_PREFLIGHT,
    PreHandoffDisposition,
    PreHandoffObservation,
    classify_pre_handoff,
)


class PreHandoffParityTest(unittest.TestCase):
    # Mirrors docs-only and Java-changing preflight tests: hosted boundary runs
    # only git diff --check HEAD in both cases.
    def test_hosted_preflight_is_exact_diff_check_only(self) -> None:
        self.assertEqual(HOSTED_PREFLIGHT, ("git", "diff", "--check", "HEAD"))

    # Mirrors test_successful_validation_allows_durable_handoff_mark.
    def test_success_advances_from_editing_to_handoff(self) -> None:
        decision = classify_pre_handoff(
            PreHandoffObservation("editing", True, True)
        )
        self.assertEqual(
            decision.disposition,
            PreHandoffDisposition.ADVANCE_HANDOFF,
        )
        self.assertEqual(decision.next_stage, "handoff")
        self.assertEqual(decision.record_error, "")

    # Mirrors test_preflight_failure_keeps_worker_in_repairable_editing_state.
    def test_failure_keeps_worker_editing_and_records_error(self) -> None:
        decision = classify_pre_handoff(
            PreHandoffObservation(
                "editing",
                True,
                False,
                "diff check failed",
            )
        )
        self.assertEqual(
            decision.disposition,
            PreHandoffDisposition.KEEP_EDITING,
        )
        self.assertEqual(decision.next_stage, "editing")
        self.assertEqual(decision.record_error, "diff check failed")

    def test_missing_preflight_fails_closed(self) -> None:
        decision = classify_pre_handoff(
            PreHandoffObservation("editing", False, False)
        )
        self.assertEqual(decision.disposition, PreHandoffDisposition.BLOCK)
        self.assertEqual(decision.next_stage, "editing")

    def test_non_editing_stage_cannot_advance(self) -> None:
        for stage in ("prepared", "handoff", "done"):
            with self.subTest(stage=stage):
                decision = classify_pre_handoff(
                    PreHandoffObservation(stage, True, True)
                )
                self.assertEqual(
                    decision.disposition,
                    PreHandoffDisposition.BLOCK,
                )

    def test_inconsistent_success_plus_error_fails_closed(self) -> None:
        decision = classify_pre_handoff(
            PreHandoffObservation(
                "editing",
                True,
                True,
                "stale failure",
            )
        )
        self.assertEqual(decision.disposition, PreHandoffDisposition.BLOCK)

    def test_decision_digest_is_deterministic(self) -> None:
        observation = PreHandoffObservation("editing", True, True)
        first = classify_pre_handoff(observation)
        second = classify_pre_handoff(observation)
        self.assertEqual(first, second)
        self.assertEqual(first.digest, second.digest)

    def test_malformed_observation_fails_closed(self) -> None:
        with self.assertRaises(ValueError):
            PreHandoffObservation("", True, True)
        with self.assertRaises(ValueError):
            PreHandoffObservation("editing", "yes", True)  # type: ignore[arg-type]


if __name__ == "__main__":
    unittest.main()
