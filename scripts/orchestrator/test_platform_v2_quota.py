from __future__ import annotations

import unittest

from v2.quota import (
    LocalBudgetObservation,
    ProviderQuotaDecision,
    QuotaAdmissionDisposition,
    QuotaPolicySettings,
    classify_quota_admission,
)


class ProviderQuotaAdmissionParityTest(unittest.TestCase):
    # Mirrors authoritative provider allowance ignoring separate Terra/Luna daily caps.
    def test_authoritative_provider_allowance_wins_over_exhausted_local_cap(self) -> None:
        provider = ProviderQuotaDecision(
            authoritative=True,
            allowed=True,
            phase="on_pace",
            reason="provider quota is within sustainable pacing envelope",
        )
        local = LocalBudgetObservation(calls_used=12, daily_limit=12)
        decision = classify_quota_admission(provider, local)
        self.assertEqual(
            decision.disposition,
            QuotaAdmissionDisposition.ALLOW_PROVIDER,
        )
        self.assertTrue(decision.consume_attempt)
        self.assertTrue(decision.clear_stale_quota_pacing_block)

    # Mirrors provider pacing block not consuming an attempt.
    def test_authoritative_provider_denial_blocks_without_consumption(self) -> None:
        provider = ProviderQuotaDecision(
            authoritative=True,
            allowed=False,
            phase="weekly_catchup",
            block_kind="quota_pacing",
            retry_after_seconds=900,
            reason="weekly usage is ahead of sustainable curve",
        )
        local = LocalBudgetObservation(calls_used=3, daily_limit=12)
        decision = classify_quota_admission(provider, local)
        self.assertEqual(
            decision.disposition,
            QuotaAdmissionDisposition.BLOCK_PROVIDER,
        )
        self.assertFalse(decision.consume_attempt)
        self.assertEqual(decision.block_kind, "quota_pacing")
        self.assertEqual(decision.retry_after_seconds, 900)

    # Mirrors incomplete/failed provider signal falling back to local caps.
    def test_missing_provider_uses_local_budget(self) -> None:
        allowed = classify_quota_admission(
            None,
            LocalBudgetObservation(calls_used=3, daily_limit=4),
        )
        blocked = classify_quota_admission(
            None,
            LocalBudgetObservation(calls_used=4, daily_limit=4),
        )
        self.assertEqual(allowed.disposition, QuotaAdmissionDisposition.ALLOW_LOCAL)
        self.assertEqual(blocked.disposition, QuotaAdmissionDisposition.BLOCK_LOCAL)
        self.assertFalse(blocked.consume_attempt)
        self.assertEqual(blocked.block_kind, "local_budget")

    def test_non_authoritative_provider_falls_back_to_local(self) -> None:
        provider = ProviderQuotaDecision(
            authoritative=False,
            allowed=False,
            phase="fallback_local",
        )
        decision = classify_quota_admission(
            provider,
            LocalBudgetObservation(calls_used=0, daily_limit=1),
        )
        self.assertEqual(decision.disposition, QuotaAdmissionDisposition.ALLOW_LOCAL)

    # Mirrors protected task/worker authority using the larger burst margin.
    def test_protected_authority_selects_protected_margin(self) -> None:
        settings = QuotaPolicySettings(5.0, 10.0)
        self.assertEqual(settings.burst_margin(protected_authority=False), 5.0)
        self.assertEqual(settings.burst_margin(protected_authority=True), 10.0)

    def test_malformed_provider_mapping_never_invents_allowance(self) -> None:
        malformed = ProviderQuotaDecision.from_mapping(
            {"authoritative": "yes", "allowed": True}
        )
        self.assertIsNone(malformed)
        decision = classify_quota_admission(
            malformed,
            LocalBudgetObservation(calls_used=2, daily_limit=2),
        )
        self.assertEqual(decision.disposition, QuotaAdmissionDisposition.BLOCK_LOCAL)

    def test_decision_digest_is_deterministic(self) -> None:
        provider = ProviderQuotaDecision(True, True, phase="surplus")
        local = LocalBudgetObservation(99, 1)
        first = classify_quota_admission(provider, local)
        second = classify_quota_admission(provider, local)
        self.assertEqual(first, second)
        self.assertEqual(first.digest, second.digest)


if __name__ == "__main__":
    unittest.main()
