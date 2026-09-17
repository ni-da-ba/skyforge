from __future__ import annotations

import unittest

from v2.effects import (
    EffectKind,
    EffectReconcileDisposition,
    EffectStatus,
    RemoteEffectIdentity,
    RemoteEffectObservation,
    RemoteEffectPresence,
    RemoteEffectRecord,
    reconcile_remote_effect,
)


def pending(kind: EffectKind = EffectKind.CREATE_PR) -> RemoteEffectRecord:
    return RemoteEffectRecord.begin(
        RemoteEffectIdentity.create(
            attempt_id="attempt-1",
            kind=kind,
            subject="subject-1",
        )
    )


class RemoteEffectReconciliationTest(unittest.TestCase):
    # Crash boundary: intent persisted, remote mutation never occurred.
    def test_pending_absent_executes(self) -> None:
        decision = reconcile_remote_effect(
            pending(),
            RemoteEffectObservation(RemoteEffectPresence.ABSENT),
        )
        self.assertEqual(decision.disposition, EffectReconcileDisposition.EXECUTE)

    # Crash boundary: remote mutation succeeded, local completion save did not.
    def test_pending_exact_present_marks_complete_without_reexecution(self) -> None:
        decision = reconcile_remote_effect(
            pending(EffectKind.POST_COMMENT),
            RemoteEffectObservation(
                RemoteEffectPresence.PRESENT_EXACT,
                remote_identity="comment:1234",
            ),
        )
        self.assertEqual(
            decision.disposition,
            EffectReconcileDisposition.MARK_COMPLETE,
        )
        self.assertEqual(decision.remote_identity, "comment:1234")

    def test_pending_remote_conflict_blocks(self) -> None:
        decision = reconcile_remote_effect(
            pending(EffectKind.CREATE_PR),
            RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT),
        )
        self.assertEqual(decision.disposition, EffectReconcileDisposition.BLOCK)

    def test_pending_unknown_remote_truth_blocks(self) -> None:
        decision = reconcile_remote_effect(
            pending(EffectKind.MERGE_PR),
            RemoteEffectObservation(RemoteEffectPresence.UNKNOWN),
        )
        self.assertEqual(decision.disposition, EffectReconcileDisposition.BLOCK)

    def test_complete_effect_never_reexecutes(self) -> None:
        record = pending(EffectKind.CLOSE_ISSUE).complete("issue:754:closed")
        for presence in RemoteEffectPresence:
            observation = (
                RemoteEffectObservation(presence, "issue:754:closed")
                if presence is RemoteEffectPresence.PRESENT_EXACT
                else RemoteEffectObservation(presence)
            )
            with self.subTest(presence=presence):
                decision = reconcile_remote_effect(record, observation)
                self.assertEqual(
                    decision.disposition,
                    EffectReconcileDisposition.NOOP_COMPLETE,
                )
                self.assertEqual(decision.remote_identity, "issue:754:closed")

    def test_representative_effect_kinds_share_same_exactly_once_contract(self) -> None:
        for kind in (
            EffectKind.DISPATCH_WORKER,
            EffectKind.PUSH_BRANCH,
            EffectKind.CREATE_PR,
            EffectKind.UPDATE_PR,
            EffectKind.POST_COMMENT,
            EffectKind.MERGE_PR,
            EffectKind.CLOSE_ISSUE,
        ):
            with self.subTest(kind=kind):
                decision = reconcile_remote_effect(
                    pending(kind),
                    RemoteEffectObservation(RemoteEffectPresence.ABSENT),
                )
                self.assertEqual(
                    decision.disposition,
                    EffectReconcileDisposition.EXECUTE,
                )

    def test_impossible_record_and_observation_shapes_fail_closed(self) -> None:
        identity = RemoteEffectIdentity.create(
            attempt_id="attempt-1",
            kind=EffectKind.CREATE_PR,
            subject="pr",
        )
        with self.assertRaises(ValueError):
            RemoteEffectRecord(identity, EffectStatus.PENDING, "pr:99")
        with self.assertRaises(ValueError):
            RemoteEffectRecord(identity, EffectStatus.COMPLETE, "")
        with self.assertRaises(ValueError):
            RemoteEffectObservation(RemoteEffectPresence.PRESENT_EXACT)
        with self.assertRaises(ValueError):
            RemoteEffectObservation(RemoteEffectPresence.ABSENT, "pr:99")

    def test_reconciliation_digest_is_deterministic(self) -> None:
        record = pending()
        observation = RemoteEffectObservation(RemoteEffectPresence.ABSENT)
        first = reconcile_remote_effect(record, observation)
        second = reconcile_remote_effect(record, observation)
        self.assertEqual(first, second)
        self.assertEqual(first.digest, second.digest)


if __name__ == "__main__":
    unittest.main()
