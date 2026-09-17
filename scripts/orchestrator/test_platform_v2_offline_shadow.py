from __future__ import annotations

import unittest

from v2.core import ControllerState, ManagedPRObservation, ManagedPRState
from v2.domain import CIState
from v2.effects import (
    EffectKind,
    RemoteEffectIdentity,
    RemoteEffectObservation,
    RemoteEffectPresence,
    RemoteEffectRecord,
)
from v2.fairness import PendingTimerObservation
from v2.quota import LocalBudgetObservation, ProviderQuotaDecision
from v2.shadow import (
    OfflineShadowInput,
    ShadowEntry,
    build_shadow_report,
    evaluate_offline_shadow,
)


def managed_inputs():
    head = "a" * 40
    spec = "b" * 64
    state = ControllerState(
        managed=(
            ManagedPRState(
                lane="Implementation",
                pr_number=900,
                branch="codex/task-900",
                authority_key="task:900",
                expected_head=head,
                auto_merge_eligible=True,
            ),
        )
    )
    observation = ManagedPRObservation(
        lane="Implementation",
        pr_number=900,
        active_pr=True,
        base_branch="main",
        head_branch="codex/task-900",
        current_head_sha=head,
        evidence_sha=head,
        reviewed_sha=head,
        task_spec_hash=spec,
        accepted_task_spec_hash=spec,
        ci_state=CIState.PASS,
    )
    return state, observation


def effect_inputs():
    identity = RemoteEffectIdentity.create(
        attempt_id="attempt-900",
        kind=EffectKind.CREATE_PR,
        subject="task:900",
    )
    return (
        RemoteEffectRecord.begin(identity),
        RemoteEffectObservation(RemoteEffectPresence.ABSENT),
    )


class OfflineShadowIntegrationTest(unittest.TestCase):
    def full_input(self) -> OfflineShadowInput:
        state, managed = managed_inputs()
        record, effect = effect_inputs()
        return OfflineShadowInput(
            managed_state=state,
            managed_observation=managed,
            quota_provider=ProviderQuotaDecision(True, True, phase="surplus"),
            quota_local=LocalBudgetObservation(99, 1),
            effect_record=record,
            effect_observation=effect,
            timer_observation=PendingTimerObservation(100.0, 25.0),
        )

    def test_same_observations_produce_identical_report(self) -> None:
        first = evaluate_offline_shadow(self.full_input())
        second = evaluate_offline_shadow(self.full_input())
        self.assertEqual(first, second)
        self.assertEqual(first.digest, second.digest)
        self.assertEqual(
            tuple(entry.policy for entry in first.entries),
            ("managed_pr", "pending_timer", "quota", "remote_effect"),
        )

    def test_changed_observation_changes_report_digest(self) -> None:
        first = evaluate_offline_shadow(self.full_input())
        state, managed = managed_inputs()
        changed = evaluate_offline_shadow(
            OfflineShadowInput(
                managed_state=state,
                managed_observation=managed,
                quota_provider=ProviderQuotaDecision(
                    True,
                    False,
                    phase="weekly_catchup",
                    block_kind="quota_pacing",
                    retry_after_seconds=900,
                ),
                quota_local=LocalBudgetObservation(0, 100),
            )
        )
        self.assertNotEqual(first.digest, changed.digest)

    def test_component_order_cannot_change_report_identity(self) -> None:
        a = ShadowEntry("z", "i1", "d1", "ALLOW")
        b = ShadowEntry("a", "i2", "d2", "BLOCK")
        first = build_shadow_report([a, b])
        second = build_shadow_report([b, a])
        self.assertEqual(first.entries, second.entries)
        self.assertEqual(first.digest, second.digest)

    def test_duplicate_policy_keys_fail_closed(self) -> None:
        a = ShadowEntry("quota", "i1", "d1", "ALLOW")
        b = ShadowEntry("quota", "i2", "d2", "BLOCK")
        with self.assertRaises(ValueError):
            build_shadow_report([a, b])

    def test_omitted_observation_makes_no_authoritative_choice(self) -> None:
        report = evaluate_offline_shadow(OfflineShadowInput())
        self.assertEqual(report.entries, ())

    def test_local_quota_fallback_is_allowed_without_provider_observation(self) -> None:
        report = evaluate_offline_shadow(
            OfflineShadowInput(quota_local=LocalBudgetObservation(0, 1))
        )
        self.assertEqual(len(report.entries), 1)
        self.assertEqual(report.entries[0].policy, "quota")
        self.assertEqual(report.entries[0].disposition, "ALLOW_LOCAL")

    def test_partial_managed_or_effect_inputs_fail_closed(self) -> None:
        state, _ = managed_inputs()
        record, _ = effect_inputs()
        with self.assertRaises(ValueError):
            OfflineShadowInput(managed_state=state)
        with self.assertRaises(ValueError):
            OfflineShadowInput(effect_record=record)

    def test_shadow_module_has_no_production_runtime_dependency(self) -> None:
        import inspect
        import v2.shadow as shadow

        source = inspect.getsource(shadow)
        self.assertNotIn("skyforge_orchestrator", source)
        self.assertNotIn("_runtime", source)
        self.assertNotIn("subprocess", source)
        self.assertNotIn("requests", source)
        lowered = source.lower()
        self.assertNotIn("import github", lowered)
        self.assertNotIn("from github", lowered)

if __name__ == "__main__":
    unittest.main()
