from __future__ import annotations

from pathlib import Path
import unittest

from v2.activation_gate import (
    ProductionActivationDisposition,
    ProductionActivationInput,
    evaluate_production_activation,
)
from v2.cutover import (
    CutoverReadinessDecision,
    CutoverReadinessDisposition,
)


MAIN = "a" * 40
LEGACY = "a" * 40
PROJECTION_DIGEST = "b" * 64


def cutover_ready():
    return CutoverReadinessDecision(
        disposition=CutoverReadinessDisposition.READY_FOR_AUTHORITY_SWITCH,
        blockers=(),
        projection_digest=PROJECTION_DIGEST,
        accepted_main_sha=MAIN,
        legacy_runtime_sha=LEGACY,
    )


def activation(**overrides):
    values = {
        "cutover": cutover_ready(),
        "primary_workstation_preservation_pass": True,
        "dr70_migration_hold_cleared": True,
        "hosted_shadow_parity_accepted": True,
        "hosted_execution_path_accepted": True,
        "remote_effect_path_accepted": True,
        "cutover_rollback_rehearsal_accepted": True,
    }
    values.update(overrides)
    return ProductionActivationInput(**values)


class ProductionActivationEnvelopeTest(unittest.TestCase):
    def test_all_project_and_cutover_evidence_reaches_operator_review_only(self):
        decision = evaluate_production_activation(activation())
        self.assertEqual(
            decision.disposition,
            ProductionActivationDisposition.READY_FOR_OPERATOR_REVIEW,
        )
        self.assertEqual(decision.blockers, ())
        self.assertTrue(decision.operator_action_required)
        self.assertEqual(decision.accepted_main_sha, MAIN)

    def test_each_project_level_hold_fails_closed(self):
        cases = {
            "primary_workstation_preservation_pass": (
                "primary Windows workstation preservation audit is not PASS"
            ),
            "dr70_migration_hold_cleared": (
                "DR-70 migration/human-review hold is not explicitly cleared"
            ),
            "hosted_shadow_parity_accepted": (
                "hosted Platform-v2 shadow/parity evidence is not accepted"
            ),
            "hosted_execution_path_accepted": (
                "hosted Platform-v2 execution path is not accepted"
            ),
            "remote_effect_path_accepted": (
                "Platform-v2 remote-effect path is not accepted for production"
            ),
            "cutover_rollback_rehearsal_accepted": (
                "production cutover/rollback runbook rehearsal is not accepted"
            ),
        }
        for field, blocker in cases.items():
            with self.subTest(field=field):
                decision = evaluate_production_activation(
                    activation(**{field: False})
                )
                self.assertEqual(
                    decision.disposition,
                    ProductionActivationDisposition.BLOCKED,
                )
                self.assertEqual(decision.blockers, (blocker,))
                self.assertTrue(decision.operator_action_required)

    def test_underlying_cutover_blockers_are_preserved_verbatim_with_prefix(self):
        cutover = CutoverReadinessDecision(
            disposition=CutoverReadinessDisposition.BLOCKED,
            blockers=(
                "legacy controller state is not operationally quiescent",
                "pre-v2-cutover rollback checkpoint is not PASS",
            ),
            projection_digest=PROJECTION_DIGEST,
            accepted_main_sha=MAIN,
            legacy_runtime_sha="c" * 40,
        )
        decision = evaluate_production_activation(
            activation(cutover=cutover)
        )
        self.assertEqual(
            decision.disposition,
            ProductionActivationDisposition.BLOCKED,
        )
        self.assertEqual(
            decision.blockers,
            (
                "cutover readiness: legacy controller state is not operationally quiescent",
                "cutover readiness: pre-v2-cutover rollback checkpoint is not PASS",
            ),
        )

    def test_current_preservation_posture_stays_blocked_even_if_lower_cutover_is_green(self):
        decision = evaluate_production_activation(
            activation(
                primary_workstation_preservation_pass=False,
                dr70_migration_hold_cleared=False,
            )
        )
        self.assertEqual(
            decision.disposition,
            ProductionActivationDisposition.BLOCKED,
        )
        self.assertIn(
            "primary Windows workstation preservation audit is not PASS",
            decision.blockers,
        )
        self.assertIn(
            "DR-70 migration/human-review hold is not explicitly cleared",
            decision.blockers,
        )
        self.assertTrue(decision.operator_action_required)

    def test_digest_is_deterministic_and_changes_with_any_hold(self):
        first = evaluate_production_activation(activation())
        again = evaluate_production_activation(activation())
        blocked = evaluate_production_activation(
            activation(hosted_execution_path_accepted=False)
        )
        self.assertEqual(first.digest, again.digest)
        self.assertNotEqual(first.digest, blocked.digest)
        self.assertNotEqual(first.input_digest, blocked.input_digest)

    def test_non_boolean_inputs_fail_closed(self):
        with self.assertRaises(ValueError):
            activation(primary_workstation_preservation_pass=1)

    def test_decision_cannot_disable_operator_action_requirement(self):
        ready = evaluate_production_activation(activation())
        raw = ready.as_dict()
        self.assertTrue(raw["operator_action_required"])


class CapabilityIsolationTest(unittest.TestCase):
    def test_activation_gate_is_pure_and_has_no_authority_switch_surface(self):
        root = Path(__file__).resolve().parent
        source = (root / "v2/activation_gate.py").read_text(encoding="utf-8")
        for token in (
            "advance_writer_authority",
            "WriterFence",
            "subprocess",
            "systemctl",
            "ordinary_remote",
            "git push",
            "gh pr",
            "platform_v2_hosted_runtime",
        ):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main()
