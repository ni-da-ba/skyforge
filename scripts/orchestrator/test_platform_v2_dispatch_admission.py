from __future__ import annotations

import unittest

from v2.decision import (
    ClassifierDecision,
    DecisionFreshnessObservation,
    DecisionKind,
    PendingDecisionRecord,
    SourcePRState,
    WorkerTier as DecisionWorkerTier,
)
from v2.dispatch_admission import (
    DispatchAdmissionDisposition,
    RepositoryTaskAuthority,
    admit_dispatch,
)
from v2.external import ExternalProducerClaim
from v2.quota import LocalBudgetObservation, ProviderQuotaDecision


MAIN = "a" * 40
SOURCE_HEAD = "b" * 40


def authority(**overrides):
    values = dict(
        task_id="r5c4-fixture",
        authority_key="issue:858",
        issue_numbers=(858,),
        lane="Implementation",
        objective="repair bounded controller policy",
        stop_boundary="tests green and bounded scope only",
        allowed_paths=(
            "scripts/orchestrator/v2/**",
            "scripts/orchestrator/test_platform_v2_dispatch_admission.py",
        ),
        protected_paths=("docs/agent-state/PLATFORM_V2_RELEASE5_GATE.json",),
        context_text="Repository-owned fixture context.",
        auto_merge_eligible=True,
    )
    values.update(overrides)
    return RepositoryTaskAuthority(**values)


def record(**decision_overrides):
    decision_values = dict(
        kind=DecisionKind.DISPATCH,
        lane="Implementation",
        pr_number=None,
        objective="repair bounded controller policy",
        stop_boundary="tests green and bounded scope only",
        reusable_evidence="existing unit coverage",
        worker_tier=DecisionWorkerTier.LUNA,
        allowed_paths=None,
        reason="bounded repository work remains",
    )
    decision_values.update(decision_overrides)
    return PendingDecisionRecord(
        decision=ClassifierDecision(**decision_values),
        event_keys=("workflow_run:fixture",),
        authority_event_keys=("issue:858",),
        ordinary_event_keys=("workflow_run:fixture",),
        task_issue_numbers=(858,),
        captured_at="2026-09-18T00:00:00Z",
        snapshot_main=MAIN,
        source_pr_head=SOURCE_HEAD if decision_values.get("pr_number") else None,
    )


def freshness(*, current_main=MAIN, pr_state=SourcePRState.UNKNOWN, source_head=""):
    return DecisionFreshnessObservation(
        current_main=current_main,
        source_pr_state=pr_state,
        source_pr_head=source_head,
    )


def admit(
    *,
    auth=None,
    pending=None,
    fresh=None,
    claims=(),
    provider_quota=None,
    local=None,
    attempt_number=1,
):
    return admit_dispatch(
        authority=auth or authority(),
        record=pending or record(),
        freshness=fresh or freshness(),
        current_main=(fresh.current_main if fresh is not None else MAIN),
        active_external_claims=claims,
        provider_quota=provider_quota,
        local_budget=local or LocalBudgetObservation(calls_used=0, daily_limit=10),
        attempt_number=attempt_number,
    )


class DispatchAdmissionTest(unittest.TestCase):
    def test_repository_authority_supplies_scope_and_freezes_attempt(self):
        result = admit()
        self.assertEqual(result.disposition, DispatchAdmissionDisposition.ADMIT)
        self.assertTrue(result.consume_attempt)
        self.assertIsNotNone(result.frozen_task)
        self.assertIsNotNone(result.attempt)
        self.assertIsNotNone(result.worker_spec)
        self.assertEqual(result.frozen_task.base_sha, MAIN)
        self.assertEqual(result.worker_spec.base_sha, MAIN)
        self.assertEqual(result.worker_spec.lane, "Implementation")
        self.assertEqual(result.worker_spec.objective, authority().objective)
        self.assertEqual(result.worker_spec.stop_boundary, authority().stop_boundary)
        self.assertEqual(result.worker_spec.allowed_paths, authority().allowed_paths)
        self.assertEqual(result.worker_spec.task_spec_hash, result.frozen_task.spec_hash)
        self.assertEqual(result.worker_spec.attempt_id, result.attempt.attempt_id)

    def test_classifier_may_narrow_but_not_widen_repository_scope(self):
        narrowed = admit(
            pending=record(
                allowed_paths=("scripts/orchestrator/v2/decision.py",)
            )
        )
        self.assertEqual(narrowed.disposition, DispatchAdmissionDisposition.ADMIT)
        self.assertEqual(
            narrowed.worker_spec.allowed_paths,
            ("scripts/orchestrator/v2/decision.py",),
        )

        widened = admit(
            pending=record(allowed_paths=("skyforge-core/src/main/java/X.java",))
        )
        self.assertEqual(widened.disposition, DispatchAdmissionDisposition.BLOCK)
        self.assertIn("widens repository authority", widened.reason)
        self.assertFalse(widened.consume_attempt)

    def test_lane_objective_and_stop_boundary_are_repository_owned(self):
        cases = [
            ({"lane": "Authorship"}, "lane"),
            ({"objective": "different objective"}, "objective"),
            ({"stop_boundary": "different stop"}, "stop boundary"),
        ]
        for change, label in cases:
            with self.subTest(change=change):
                result = admit(pending=record(**change))
                self.assertEqual(result.disposition, DispatchAdmissionDisposition.BLOCK)
                self.assertIn(label, result.reason)
                self.assertFalse(result.consume_attempt)

    def test_issue_authority_mismatch_blocks(self):
        bad = record()
        bad = PendingDecisionRecord(
            decision=bad.decision,
            event_keys=bad.event_keys,
            authority_event_keys=bad.authority_event_keys,
            ordinary_event_keys=bad.ordinary_event_keys,
            task_issue_numbers=(858, 999),
            captured_at=bad.captured_at,
            snapshot_main=bad.snapshot_main,
            source_pr_head=bad.source_pr_head,
        )
        result = admit(pending=bad)
        self.assertEqual(result.disposition, DispatchAdmissionDisposition.BLOCK)
        self.assertIn("issue authority", result.reason)

    def test_active_external_producer_claim_blocks_governing_issue(self):
        claim = ExternalProducerClaim(
            issue_number=858,
            claimed_by="ni-da-ba",
            lane="Implementation",
            branch="manual/858",
            pr_number=999,
        )
        result = admit(claims=(claim,))
        self.assertEqual(result.disposition, DispatchAdmissionDisposition.BLOCK)
        self.assertIn("858", result.reason)
        self.assertFalse(result.consume_attempt)

        unrelated = ExternalProducerClaim(
            issue_number=613,
            claimed_by="ni-da-ba",
            lane="Implementation",
            branch="platform/613",
            pr_number=762,
        )
        allowed = admit(claims=(unrelated,))
        self.assertEqual(allowed.disposition, DispatchAdmissionDisposition.ADMIT)

    def test_provider_and_local_quota_blocks_do_not_consume_attempt(self):
        provider_block = admit(
            provider_quota=ProviderQuotaDecision(
                authoritative=True,
                allowed=False,
                phase="capacity",
                block_kind="provider_capacity",
                retry_after_seconds=900,
                reason="provider unavailable",
            )
        )
        self.assertEqual(provider_block.disposition, DispatchAdmissionDisposition.BLOCK)
        self.assertFalse(provider_block.consume_attempt)
        self.assertEqual(provider_block.quota.block_kind, "provider_capacity")

        local_block = admit(
            local=LocalBudgetObservation(calls_used=10, daily_limit=10)
        )
        self.assertEqual(local_block.disposition, DispatchAdmissionDisposition.BLOCK)
        self.assertFalse(local_block.consume_attempt)
        self.assertEqual(local_block.quota.block_kind, "local_budget")

    def test_stale_main_requires_reclassification(self):
        pending = record()
        stale = freshness(current_main="c" * 40)
        result = admit_dispatch(
            authority=authority(),
            record=pending,
            freshness=stale,
            current_main="c" * 40,
            active_external_claims=(),
            provider_quota=None,
            local_budget=LocalBudgetObservation(calls_used=0, daily_limit=10),
            attempt_number=1,
        )
        self.assertEqual(result.disposition, DispatchAdmissionDisposition.RECLASSIFY)
        self.assertFalse(result.consume_attempt)

    def test_source_pr_must_still_be_open_at_exact_head(self):
        pending = record(pr_number=42)
        current = freshness(
            current_main=MAIN,
            pr_state=SourcePRState.OPEN,
            source_head=SOURCE_HEAD,
        )
        self.assertEqual(
            admit(pending=pending, fresh=current).disposition,
            DispatchAdmissionDisposition.ADMIT,
        )

        for state, head in (
            (SourcePRState.CLOSED, SOURCE_HEAD),
            (SourcePRState.OPEN, "d" * 40),
        ):
            with self.subTest(state=state, head=head):
                result = admit(
                    pending=pending,
                    fresh=freshness(
                        current_main=MAIN,
                        pr_state=state,
                        source_head=head,
                    ),
                )
                self.assertEqual(
                    result.disposition,
                    DispatchAdmissionDisposition.RECLASSIFY,
                )

    def test_non_dispatch_classifier_decisions_do_not_create_workers(self):
        for kind in (DecisionKind.NOOP, DecisionKind.HUMAN_GATE, DecisionKind.MERGE):
            with self.subTest(kind=kind):
                kwargs = {"kind": kind}
                if kind is DecisionKind.MERGE:
                    kwargs["lane"] = "Implementation"
                pending = record(**kwargs)
                result = admit(pending=pending)
                self.assertEqual(
                    result.disposition,
                    DispatchAdmissionDisposition.NOT_DISPATCH,
                )
                self.assertIsNone(result.worker_spec)
                self.assertFalse(result.consume_attempt)

    def test_authority_or_execution_change_changes_frozen_identity(self):
        first = admit()
        changed_authority = admit(
            auth=authority(context_text="Changed repository authority context.")
        )
        changed_tier = admit(
            pending=record(worker_tier=DecisionWorkerTier.TERRA)
        )
        changed_scope = admit(
            pending=record(allowed_paths=("scripts/orchestrator/v2/decision.py",))
        )

        identities = {
            first.frozen_task.spec_hash,
            changed_authority.frozen_task.spec_hash,
            changed_tier.frozen_task.spec_hash,
            changed_scope.frozen_task.spec_hash,
        }
        attempts = {
            first.attempt.attempt_id,
            changed_authority.attempt.attempt_id,
            changed_tier.attempt.attempt_id,
            changed_scope.attempt.attempt_id,
        }
        self.assertEqual(len(identities), 4)
        self.assertEqual(len(attempts), 4)

    def test_attempt_number_changes_attempt_not_frozen_task(self):
        first = admit(attempt_number=1)
        second = admit(attempt_number=2)
        self.assertEqual(first.frozen_task.spec_hash, second.frozen_task.spec_hash)
        self.assertNotEqual(first.attempt.attempt_id, second.attempt.attempt_id)

    def test_hosted_runtime_does_not_import_dispatch_admission(self):
        from pathlib import Path
        root = Path(__file__).resolve().parent
        source = (root / "platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        self.assertNotIn("dispatch_admission", source)


if __name__ == "__main__":
    unittest.main()
