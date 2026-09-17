from __future__ import annotations

import unittest

from v2.quota import (
    AttemptKind,
    LocalQuotaState,
    ProtectedAuthorityObservation,
    ProviderQuotaDecision,
    QuotaAdmissionDisposition,
    QuotaAdmissionObservation,
    classify_quota_admission,
    is_protected_authority_attempt,
)


class ProviderQuotaAdmissionParityTest(unittest.TestCase):
    # Mirrors authoritative provider allow ignoring separate daily caps.
    def test_authoritative_allow_ignores_local_exhaustion(self) -> None:
        observation = QuotaAdmissionObservation(
            AttemptKind.WORKER,
            ProviderQuotaDecision(True, True, phase="surplus", reason="ok"),
            LocalQuotaState(used=12, limit=12),
        )
        decision = classify_quota_admission(observation)
        self.assertEqual(
            decision.disposition,
            QuotaAdmissionDisposition.ALLOW_PROVIDER,
        )
        self.assertTrue(decision.allowed)
        self.assertTrue(decision.consume_attempt)

    # Mirrors provider pacing block does not consume attempt.
    def test_authoritative_provider_block_preserves_exact_block(self) -> None:
        observation = QuotaAdmissionObservation(
            AttemptKind.WORKER,
            ProviderQuotaDecision(
                True,
                False,
                phase="weekly_catchup",
                block_kind="quota_pacing",
                retry_after_seconds=900,
                reason="ahead of curve",
            ),
            LocalQuotaState(used=3, limit=12),
        )
        decision = classify_quota_admission(observation)
        self.assertEqual(
            decision.disposition,
            QuotaAdmissionDisposition.BLOCK_PROVIDER,
        )
        self.assertFalse(decision.consume_attempt)
        self.assertEqual(decision.block_kind, "quota_pacing")
        self.assertEqual(decision.retry_after_seconds, 900)

    # Mirrors incomplete/failed provider signal falling back to local caps.
    def test_non_authoritative_provider_uses_local_fallback(self) -> None:
        allowed = classify_quota_admission(
            QuotaAdmissionObservation(
                AttemptKind.CLASSIFIER,
                ProviderQuotaDecision.unavailable(),
                LocalQuotaState(used=3, limit=4),
            )
        )
        blocked = classify_quota_admission(
            QuotaAdmissionObservation(
                AttemptKind.CLASSIFIER,
                ProviderQuotaDecision.unavailable(),
                LocalQuotaState(used=4, limit=4),
            )
        )
        self.assertEqual(allowed.disposition, QuotaAdmissionDisposition.ALLOW_LOCAL)
        self.assertTrue(allowed.consume_attempt)
        self.assertEqual(blocked.disposition, QuotaAdmissionDisposition.BLOCK_LOCAL)
        self.assertFalse(blocked.consume_attempt)
        self.assertEqual(blocked.block_kind, "local_budget")

    # Mirrors fresh provider admission clearing stale pacing block.
    def test_fresh_authoritative_allow_clears_stale_pacing_block(self) -> None:
        decision = classify_quota_admission(
            QuotaAdmissionObservation(
                AttemptKind.CLASSIFIER,
                ProviderQuotaDecision(True, True),
                LocalQuotaState(used=99, limit=1),
                stale_block_kind="quota_pacing",
            )
        )
        self.assertTrue(decision.clear_stale_quota_pacing_block)

    def test_non_pacing_block_is_not_cleared(self) -> None:
        decision = classify_quota_admission(
            QuotaAdmissionObservation(
                AttemptKind.CLASSIFIER,
                ProviderQuotaDecision(True, True),
                LocalQuotaState(used=0, limit=1),
                stale_block_kind="provider_capacity",
            )
        )
        self.assertFalse(decision.clear_stale_quota_pacing_block)

    # Mirrors provider denial being authoritative even without window detail.
    def test_provider_denial_does_not_fall_back(self) -> None:
        decision = classify_quota_admission(
            QuotaAdmissionObservation(
                AttemptKind.CLASSIFIER,
                ProviderQuotaDecision(
                    True,
                    False,
                    phase="provider_blocked",
                    block_kind="quota",
                    retry_after_seconds=60,
                ),
                LocalQuotaState(used=0, limit=100),
            )
        )
        self.assertEqual(
            decision.disposition,
            QuotaAdmissionDisposition.BLOCK_PROVIDER,
        )
        self.assertFalse(decision.allowed)

    def test_decision_digest_is_deterministic(self) -> None:
        observation = QuotaAdmissionObservation(
            AttemptKind.WORKER,
            ProviderQuotaDecision(True, True),
            LocalQuotaState(0, 1),
        )
        first = classify_quota_admission(observation)
        second = classify_quota_admission(observation)
        self.assertEqual(first, second)
        self.assertEqual(first.digest, second.digest)


class ProtectedAuthorityParityTest(unittest.TestCase):
    # Mirrors protected task authority using larger burst margin.
    def test_classifier_is_protected_only_for_pending_task_authority(self) -> None:
        self.assertTrue(
            is_protected_authority_attempt(
                ProtectedAuthorityObservation(
                    AttemptKind.CLASSIFIER,
                    protected_pending_task=True,
                )
            )
        )
        self.assertFalse(
            is_protected_authority_attempt(
                ProtectedAuthorityObservation(
                    AttemptKind.CLASSIFIER,
                    protected_pending_task=False,
                )
            )
        )

    # Mirrors task-owned worker protection.
    def test_worker_is_protected_only_for_task_authority_key(self) -> None:
        self.assertTrue(
            is_protected_authority_attempt(
                ProtectedAuthorityObservation(
                    AttemptKind.WORKER,
                    pending_worker_authority_key="task:493",
                )
            )
        )
        self.assertFalse(
            is_protected_authority_attempt(
                ProtectedAuthorityObservation(
                    AttemptKind.WORKER,
                    pending_worker_authority_key="manual:493",
                )
            )
        )

    def test_malformed_inputs_fail_closed(self) -> None:
        with self.assertRaises(ValueError):
            LocalQuotaState(-1, 1)
        with self.assertRaises(ValueError):
            ProviderQuotaDecision(True, True, retry_after_seconds=-1)
        with self.assertRaises(ValueError):
            ProtectedAuthorityObservation("worker")  # type: ignore[arg-type]


if __name__ == "__main__":
    unittest.main()
