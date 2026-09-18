from __future__ import annotations

import unittest

from v2.decision import SourcePRState
from v2.events import DurableEvent
from v2.external import (
    ClaimAdmissionDisposition,
    ClaimReleaseDisposition,
    ClaimRetentionDisposition,
    ControllerIssueOwner,
    ExternalClaimRequest,
    ExternalPREventDisposition,
    ExternalProducerClaim,
    classify_claim_admission,
    classify_claim_release,
    classify_claim_retention,
    classify_external_dispatch_hold,
    classify_external_pr_event,
)


def claim(issue: int = 547, *, pr: int | None = None) -> ExternalProducerClaim:
    return ExternalProducerClaim(
        issue_number=issue,
        claimed_by="ni-da-ba",
        pr_number=pr,
    )


class ExternalClaimAdmissionParityTest(unittest.TestCase):
    # Mirrors test_claim_is_recorded_when_issue_is_unowned.
    def test_trusted_unowned_claim_is_accepted(self) -> None:
        request = ExternalClaimRequest(
            issue_number=492,
            actor="ni-da-ba",
            trusted_actor=True,
            lane="Implementation",
        )
        decision = classify_claim_admission(request, ControllerIssueOwner.NONE)
        self.assertEqual(decision.disposition, ClaimAdmissionDisposition.ACCEPT)

    # Mirrors controller-worker and active-roadmap rejection tests.
    def test_controller_ownership_rejects_claim(self) -> None:
        request = ExternalClaimRequest(
            issue_number=492,
            actor="ni-da-ba",
            trusted_actor=True,
        )
        for owner in (
            ControllerIssueOwner.PENDING_WORKER,
            ControllerIssueOwner.ACTIVE_ROADMAP,
            ControllerIssueOwner.MANAGED_PR,
            ControllerIssueOwner.PENDING_DECISION,
        ):
            with self.subTest(owner=owner):
                decision = classify_claim_admission(request, owner)
                self.assertEqual(
                    decision.disposition,
                    ClaimAdmissionDisposition.REJECT_CONTROLLER_OWNED,
                )

    # Mirrors test_untrusted_claim_is_not_a_control at the pure authority layer.
    def test_untrusted_actor_is_rejected(self) -> None:
        request = ExternalClaimRequest(
            issue_number=492,
            actor="someone-else",
            trusted_actor=False,
        )
        decision = classify_claim_admission(request, ControllerIssueOwner.NONE)
        self.assertEqual(
            decision.disposition,
            ClaimAdmissionDisposition.REJECT_UNTRUSTED,
        )

    def test_admission_digest_is_stable(self) -> None:
        request = ExternalClaimRequest(492, "ni-da-ba", True)
        first = classify_claim_admission(request, ControllerIssueOwner.NONE)
        second = classify_claim_admission(request, ControllerIssueOwner.NONE)
        self.assertEqual(first, second)
        self.assertEqual(first.digest, second.digest)


class ExternalDispatchParityTest(unittest.TestCase):
    # Mirrors test_active_claim_holds_task_before_original_dispatch.
    def test_claim_holds_only_matching_task(self) -> None:
        decision = classify_external_dispatch_hold([claim(492)], [492])
        self.assertTrue(decision.hold)
        self.assertEqual(decision.issue_numbers, (492,))

    # Mirrors test_claim_for_other_issue_does_not_hold_dispatch.
    def test_unrelated_claim_does_not_hold_task(self) -> None:
        decision = classify_external_dispatch_hold([claim(547)], [492])
        self.assertFalse(decision.hold)
        self.assertEqual(decision.issue_numbers, ())

    # Mirrors explicit release clearing the authority hold.
    def test_explicit_release_is_exact_issue_scoped(self) -> None:
        active = claim(547)
        self.assertEqual(
            classify_claim_release(active, 547),
            ClaimReleaseDisposition.RELEASE,
        )
        self.assertEqual(
            classify_claim_release(active, 492),
            ClaimReleaseDisposition.KEEP,
        )


class ExternalClaimRetirementParityTest(unittest.TestCase):
    # Mirrors exact legacy terminal-PR pruning semantics.
    def test_bound_claim_retires_when_exact_pr_is_terminal(self) -> None:
        for state in (SourcePRState.MERGED, SourcePRState.CLOSED):
            with self.subTest(state=state):
                decision = classify_claim_retention(claim(pr=548), state)
                self.assertEqual(decision.disposition, ClaimRetentionDisposition.RETIRE)

    def test_open_or_unknown_claim_is_kept(self) -> None:
        for state in (SourcePRState.OPEN, SourcePRState.UNKNOWN):
            with self.subTest(state=state):
                decision = classify_claim_retention(claim(pr=548), state)
                self.assertEqual(decision.disposition, ClaimRetentionDisposition.KEEP)

    # Mirrors test_remote_failure_keeps_claim_fail_closed.
    def test_remote_failure_keeps_claim_fail_closed(self) -> None:
        decision = classify_claim_retention(
            claim(pr=548),
            SourcePRState.UNKNOWN,
            remote_observation_available=False,
        )
        self.assertEqual(decision.disposition, ClaimRetentionDisposition.KEEP)
        self.assertIn("fail-closed", decision.reason)

    def test_unbound_claim_requires_explicit_release(self) -> None:
        decision = classify_claim_retention(claim())
        self.assertEqual(decision.disposition, ClaimRetentionDisposition.KEEP)


class ExternalPREventFilterParityTest(unittest.TestCase):
    def workflow(self, pr_number) -> DurableEvent:
        return DurableEvent(
            actionable=True,
            reason="CI completed",
            event="workflow_run",
            action="completed",
            head_sha="a" * 40,
            pr_number=pr_number,
        )

    # Mirrors test_unowned_pr_workflow_completion_is_retired_before_classifier.
    def test_unowned_pr_workflow_is_retired(self) -> None:
        decision = classify_external_pr_event(self.workflow(101), [])
        self.assertEqual(
            decision.disposition,
            ExternalPREventDisposition.RETIRE_EXTERNAL,
        )

    # Mirrors test_controller_owned_pr_workflow_completion_is_preserved.
    def test_owned_pr_workflow_is_kept(self) -> None:
        decision = classify_external_pr_event(self.workflow(202), [202])
        self.assertEqual(decision.disposition, ExternalPREventDisposition.KEEP)

    # Mirrors test_non_workflow_repository_event_is_preserved.
    def test_non_workflow_repository_event_is_kept(self) -> None:
        event = DurableEvent(
            actionable=True,
            reason="main advanced",
            event="push",
            head_sha="b" * 40,
        )
        decision = classify_external_pr_event(event, [])
        self.assertEqual(decision.disposition, ExternalPREventDisposition.KEEP)

    # Mirrors test_workflow_without_pr_identity_is_preserved.
    def test_workflow_without_pr_identity_is_kept(self) -> None:
        decision = classify_external_pr_event(self.workflow(None), [])
        self.assertEqual(decision.disposition, ExternalPREventDisposition.KEEP)

    def test_ambiguous_pr_identity_is_kept_fail_closed(self) -> None:
        decision = classify_external_pr_event(self.workflow("not-a-pr"), [])
        self.assertEqual(decision.disposition, ExternalPREventDisposition.KEEP)


class ExternalAuthorityValidationTest(unittest.TestCase):
    def test_malformed_claims_and_requests_fail_closed(self) -> None:
        with self.assertRaises(ValueError):
            ExternalProducerClaim.from_legacy_mapping([])
        with self.assertRaises(ValueError):
            ExternalProducerClaim.from_legacy_mapping(
                {"issue_number": 0, "claimed_by": "ni-da-ba"}
            )
        with self.assertRaises(ValueError):
            ExternalClaimRequest(492, "", True)
        with self.assertRaises(ValueError):
            ExternalClaimRequest(492, "ni-da-ba", "yes")  # type: ignore[arg-type]


if __name__ == "__main__":
    unittest.main()
