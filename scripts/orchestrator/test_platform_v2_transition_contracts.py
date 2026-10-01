from __future__ import annotations

import unittest

from v2 import (
    CIState,
    EffectKind,
    EffectStatus,
    MechanicalSnapshot,
    PRClass,
    RemoteEffectIdentity,
    RemoteEffectRecord,
    TransitionKind,
    decide_mechanical,
)


HEAD = "a" * 40
SPEC = "b" * 64
ATTEMPT = "c" * 64


def accepted_snapshot(**overrides):
    values = {
        "pr_class": PRClass.DELIVERY,
        "active_pr": True,
        "current_head_sha": HEAD,
        "evidence_sha": HEAD,
        "reviewed_sha": HEAD,
        "task_spec_hash": SPEC,
        "accepted_task_spec_hash": SPEC,
        "ci_state": CIState.PASS,
        "human_gate_pending": False,
        "pending_worker": False,
        "review_required": False,
    }
    values.update(overrides)
    return MechanicalSnapshot(**values)


class RemoteEffectIdentityTest(unittest.TestCase):
    def test_same_authority_produces_same_effect_id(self) -> None:
        first = RemoteEffectIdentity.create(
            attempt_id=ATTEMPT,
            kind=EffectKind.CREATE_PR,
            subject="task:771",
        )
        second = RemoteEffectIdentity.create(
            attempt_id=ATTEMPT,
            kind=EffectKind.CREATE_PR,
            subject="task:771",
        )
        self.assertEqual(first, second)

    def test_authoritative_identity_changes_effect_id(self) -> None:
        baseline = RemoteEffectIdentity.create(
            attempt_id=ATTEMPT,
            kind=EffectKind.CREATE_PR,
            subject="task:771",
        )
        changed_attempt = RemoteEffectIdentity.create(
            attempt_id="d" * 64,
            kind=EffectKind.CREATE_PR,
            subject="task:771",
        )
        changed_kind = RemoteEffectIdentity.create(
            attempt_id=ATTEMPT,
            kind=EffectKind.MERGE_PR,
            subject="task:771",
        )
        changed_subject = RemoteEffectIdentity.create(
            attempt_id=ATTEMPT,
            kind=EffectKind.CREATE_PR,
            subject="task:772",
        )
        self.assertNotEqual(baseline.effect_id, changed_attempt.effect_id)
        self.assertNotEqual(baseline.effect_id, changed_kind.effect_id)
        self.assertNotEqual(baseline.effect_id, changed_subject.effect_id)

    def test_effect_record_requires_remote_identity_to_complete(self) -> None:
        identity = RemoteEffectIdentity.create(
            attempt_id=ATTEMPT,
            kind=EffectKind.CREATE_PR,
            subject="task:771",
        )
        pending = RemoteEffectRecord.begin(identity)
        self.assertIs(pending.status, EffectStatus.PENDING)
        completed = pending.complete("pr:772")
        self.assertIs(completed.status, EffectStatus.COMPLETE)
        self.assertEqual(completed.remote_identity, "pr:772")
        with self.assertRaises(ValueError):
            pending.complete("  ")


class MechanicalDecisionTest(unittest.TestCase):
    def test_exact_head_green_delivery_is_merge_eligible(self) -> None:
        plan = decide_mechanical(accepted_snapshot())
        self.assertIs(plan.kind, TransitionKind.MERGE_ELIGIBLE)

    def test_stale_evidence_invalidates_before_merge(self) -> None:
        plan = decide_mechanical(accepted_snapshot(evidence_sha="d" * 40))
        self.assertIs(plan.kind, TransitionKind.INVALIDATE_EVIDENCE)

    def test_stale_review_invalidates_before_merge(self) -> None:
        plan = decide_mechanical(accepted_snapshot(reviewed_sha="d" * 40))
        self.assertIs(plan.kind, TransitionKind.INVALIDATE_EVIDENCE)

    def test_human_gate_blocks_machine_merge(self) -> None:
        plan = decide_mechanical(accepted_snapshot(human_gate_pending=True))
        self.assertIs(plan.kind, TransitionKind.HUMAN_GATE)

        classified_gate = decide_mechanical(
            accepted_snapshot(pr_class=PRClass.HUMAN_GATE)
        )
        self.assertIs(classified_gate.kind, TransitionKind.HUMAN_GATE)

    def test_pending_worker_blocks_merge(self) -> None:
        plan = decide_mechanical(accepted_snapshot(pending_worker=True))
        self.assertIs(plan.kind, TransitionKind.WAIT)

    def test_ci_pending_waits(self) -> None:
        plan = decide_mechanical(accepted_snapshot(ci_state=CIState.PENDING))
        self.assertIs(plan.kind, TransitionKind.WAIT)

    def test_ci_failure_is_repair_eligible(self) -> None:
        plan = decide_mechanical(accepted_snapshot(ci_state=CIState.FAIL))
        self.assertIs(plan.kind, TransitionKind.REPAIR_ELIGIBLE)

    def test_green_ci_without_exact_acceptance_reconciles(self) -> None:
        plan = decide_mechanical(
            accepted_snapshot(accepted_task_spec_hash="e" * 64)
        )
        self.assertIs(plan.kind, TransitionKind.RECONCILE)

    def test_no_active_pr_never_invents_dispatch(self) -> None:
        plan = decide_mechanical(MechanicalSnapshot(active_pr=False))
        self.assertIs(plan.kind, TransitionKind.NOOP)

    def test_archive_and_prototype_are_outside_delivery_automation(self) -> None:
        for pr_class in (PRClass.ARCHIVE, PRClass.PROTOTYPE):
            with self.subTest(pr_class=pr_class):
                plan = decide_mechanical(accepted_snapshot(pr_class=pr_class))
                self.assertIs(plan.kind, TransitionKind.NOOP)

    def test_unknown_ci_reconciles(self) -> None:
        plan = decide_mechanical(
            accepted_snapshot(ci_state=CIState.UNKNOWN)
        )
        self.assertIs(plan.kind, TransitionKind.RECONCILE)

    def test_transition_digest_is_stable(self) -> None:
        first = decide_mechanical(accepted_snapshot())
        second = decide_mechanical(accepted_snapshot())
        self.assertEqual(first.digest, second.digest)


if __name__ == "__main__":
    unittest.main()
