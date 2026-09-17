from __future__ import annotations

import unittest

from v2.quota import (
    LocalBudgetObservation,
    ProviderQuotaObservation,
    QuotaAdmissionDisposition,
    classify_quota_admission,
    select_weekly_burst_margin,
)


class QuotaAdmissionParityTest(unittest.TestCase):
    # Mirrors authoritative provider allowance ignoring local Luna/Terra caps.
    def test_authoritative_provider_allow_supersedes_local_block(self) -> None:
        decision = classify_quota_admission(
            ProviderQuotaObservation(
                authoritative=True,
                allowed=True,
                phase="surplus",
                reason="provider quota within envelope",
            ),
            LocalBudgetObservation(False, "local cap reached", 3600),
        )
        self.assertEqual(
            decision.disposition,
            QuotaAdmissionDisposition.ALLOW_PROVIDER,
        )
        self.assertTrue(decision.consume_attempt)

    # Mirrors provider pacing block not consuming an attempt.
    def test_authoritative_provider_block_does_not_consume_attempt(self) -> None:
        decision = classify_quota_admission(
            ProviderQuotaObservation(
                authoritative=True,
                allowed=False,
                phase="weekly_catchup",
                reason="ahead of sustainable curve",
                block_kind="quota_pacing",
                retry_after_seconds=900,
            ),
            LocalBudgetObservation(True),
        )
        self.assertEqual(
            decision.disposition,
            QuotaAdmissionDisposition.BLOCK_PROVIDER,
        )
        self.assertEqual(decision.block_kind, "quota_pacing")
        self.assertEqual(decision.retry_after_seconds, 900)
        self.assertFalse(decision.consume_attempt)

    # Mirrors incomplete/failed provider signal falling back to local caps.
    def test_non_authoritative_provider_falls_back_to_local_policy(self) -> None:
        decision = classify_quota_admission(
            ProviderQuotaObservation(
                authoritative=False,
                allowed=False,
                phase="fallback_local",
            ),
            LocalBudgetObservation(False, "terra daily cap", 3600),
        )
        self.assertEqual(
            decision.disposition,
            QuotaAdmissionDisposition.BLOCK_LOCAL,
        )
        self.assertEqual(decision.block_kind, "local_budget")
        self.assertFalse(decision.consume_attempt)

    def test_missing_provider_can_allow_via_local_policy(self) -> None:
        decision = classify_quota_admission(
            None,
            LocalBudgetObservation(True, "local budget available"),
        )
        self.assertEqual(
            decision.disposition,
            QuotaAdmissionDisposition.ALLOW_LOCAL,
        )
        self.assertTrue(decision.consume_attempt)

    # Mirrors fresh provider admission clearing stale quota_pacing only.
    def test_provider_allow_clears_only_stale_quota_pacing(self) -> None:
        provider = ProviderQuotaObservation(True, True)
        pacing = classify_quota_admission(
            provider,
            LocalBudgetObservation(False),
            stale_block_kind="quota_pacing",
        )
        other = classify_quota_admission(
            provider,
            LocalBudgetObservation(False),
            stale_block_kind="external_producer",
        )
        self.assertTrue(pacing.clear_stale_quota_pacing)
        self.assertFalse(other.clear_stale_quota_pacing)

    # Mirrors protected authority using a larger configured burst margin.
    def test_protected_authority_selects_protected_margin(self) -> None:
        self.assertEqual(
            select_weekly_burst_margin(
                protected_authority=True,
                ordinary_margin_percent=5,
                protected_margin_percent=10,
            ),
            10.0,
        )
        self.assertEqual(
            select_weekly_burst_margin(
                protected_authority=False,
                ordinary_margin_percent=5,
                protected_margin_percent=10,
            ),
            5.0,
        )

    def test_decision_is_deterministic(self) -> None:
        provider = ProviderQuotaObservation(True, True, phase="surplus")
        local = LocalBudgetObservation(False, "local cap")
        first = classify_quota_admission(provider, local)
        second = classify_quota_admission(provider, local)
        self.assertEqual(first, second)
        self.assertEqual(first.digest, second.digest)

    def test_malformed_inputs_fail_closed(self) -> None:
        with self.assertRaises(ValueError):
            ProviderQuotaObservation(True, False, retry_after_seconds=-1)
        with self.assertRaises(ValueError):
            LocalBudgetObservation("yes")  # type: ignore[arg-type]
        with self.assertRaises(ValueError):
            select_weekly_burst_margin(
                protected_authority=True,
                ordinary_margin_percent=-1,
                protected_margin_percent=10,
            )


if __name__ == "__main__":
    unittest.main()
