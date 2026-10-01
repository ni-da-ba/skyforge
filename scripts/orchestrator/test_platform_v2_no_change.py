from __future__ import annotations

import unittest

from v2.no_change import (
    ManagedNoChangeObservation,
    NoChangeDisposition,
    classify_managed_no_change,
)


class ManagedNoChangeParityTest(unittest.TestCase):
    # Mirrors test_single_failed_managed_pr_no_change_gets_one_bounded_retry.
    def test_first_failed_managed_no_change_gets_one_retry(self) -> None:
        observation = ManagedNoChangeObservation(
            pr_number=500,
            head_sha="a" * 40,
            managed_pr_owned=True,
            ci_failed=True,
            retry_already_used=False,
        )
        decision = classify_managed_no_change(observation)
        self.assertEqual(decision.disposition, NoChangeDisposition.RETRY_ONCE)
        self.assertIsNotNone(decision.retry_plan)
        assert decision.retry_plan is not None
        self.assertEqual(decision.retry_plan.pr_number, 500)
        self.assertEqual(decision.retry_plan.head_sha, "a" * 40)

    # Mirrors test_second_no_change_surfaces_path_local_gate_instead_of_silence.
    def test_second_no_change_escalates_to_human_gate(self) -> None:
        observation = ManagedNoChangeObservation(
            pr_number=500,
            head_sha="a" * 40,
            managed_pr_owned=True,
            ci_failed=True,
            retry_already_used=True,
        )
        decision = classify_managed_no_change(observation)
        self.assertEqual(decision.disposition, NoChangeDisposition.HUMAN_GATE)
        self.assertIsNone(decision.retry_plan)

    # Mirrors test_ordinary_single_event_preserves_generic_no_followup_rule.
    def test_ordinary_event_has_no_special_followup(self) -> None:
        observation = ManagedNoChangeObservation(
            pr_number=None,
            head_sha="b" * 40,
            managed_pr_owned=False,
            ci_failed=False,
            retry_already_used=False,
            ordinary_event=True,
        )
        decision = classify_managed_no_change(observation)
        self.assertEqual(decision.disposition, NoChangeDisposition.NONE)

    def test_unowned_or_nonfailed_pr_does_not_receive_retry(self) -> None:
        cases = [
            ManagedNoChangeObservation(500, "a" * 40, False, True, False),
            ManagedNoChangeObservation(500, "a" * 40, True, False, False),
            ManagedNoChangeObservation(None, "a" * 40, True, True, False),
            ManagedNoChangeObservation(500, "", True, True, False),
        ]
        for observation in cases:
            with self.subTest(observation=observation):
                self.assertEqual(
                    classify_managed_no_change(observation).disposition,
                    NoChangeDisposition.NONE,
                )

    def test_retry_identity_is_pr_and_head_scoped(self) -> None:
        first = classify_managed_no_change(
            ManagedNoChangeObservation(500, "a" * 40, True, True, False)
        )
        repeat = classify_managed_no_change(
            ManagedNoChangeObservation(500, "a" * 40, True, True, False)
        )
        other_pr = classify_managed_no_change(
            ManagedNoChangeObservation(501, "a" * 40, True, True, False)
        )
        other_head = classify_managed_no_change(
            ManagedNoChangeObservation(500, "b" * 40, True, True, False)
        )
        assert first.retry_plan and repeat.retry_plan and other_pr.retry_plan and other_head.retry_plan
        self.assertEqual(first.retry_plan.retry_key, repeat.retry_plan.retry_key)
        self.assertNotEqual(first.retry_plan.retry_key, other_pr.retry_plan.retry_key)
        self.assertNotEqual(first.retry_plan.retry_key, other_head.retry_plan.retry_key)

    def test_malformed_observation_fails_closed(self) -> None:
        with self.assertRaises(ValueError):
            ManagedNoChangeObservation(0, "a" * 40, True, True, False)
        with self.assertRaises(ValueError):
            ManagedNoChangeObservation(500, "a" * 40, "yes", True, False)  # type: ignore[arg-type]


if __name__ == "__main__":
    unittest.main()
