from __future__ import annotations

import unittest

import platform_v2_cutover_readiness as cli
from v2.cutover import (
    CutoverReadinessDisposition,
    CutoverReadinessInput,
    LegacyOperationalProjection,
    WriterAuthority,
    WriterAuthorityAction,
    advance_writer_authority,
    evaluate_cutover_readiness,
)


MAIN = "a" * 40
OLD = "b" * 40


def legacy_state(**overrides):
    value = {
        "paused": False,
        "blocked_kind": None,
        "pending_worker": None,
        "pending_decision": None,
        "managed": {},
        "pending_events": [],
        "external_producer_claims": {
            "613": {
                "issue_number": 613,
                "claimed_by": "ni-da-ba",
                "state": "active",
                "lane": "Implementation",
                "branch": "platform/613-aero-moment-contract",
                "pr_number": 762,
            },
            "754": {
                "issue_number": 754,
                "claimed_by": "ni-da-ba",
                "state": "active",
                "lane": "Implementation",
                "branch": "implementation/754-dr70-human-visible-repair",
                "pr_number": 769,
            },
        },
        "human_gate_records": {
            "issue:349:implementation": {
                "token": "human-gate-token",
                "target": "349",
                "seeded_from_github": False,
            }
        },
        "roadmap": {
            "roadmap_id": "skyforge-dressed-region-convergence-v3",
            "active": None,
            "blocked_nodes": {
                "dr-human-exploration-rereview": {
                    "reason": "Machines must not self-pass this re-review."
                }
            },
            "manifest_fingerprint": "f" * 64,
        },
    }
    value.update(overrides)
    return value


def readiness(projection, **overrides):
    values = {
        "release4_accepted": True,
        "mutation_gate_disabled": True,
        "accepted_main_sha": MAIN,
        "legacy_runtime_sha": MAIN,
        "legacy_service_active": True,
        "pre_cutover_checkpoint_pass": True,
        "hosted_v2_runtime_accepted": True,
        "ingress_handoff_defined": True,
        "writer_revocation_plan_defined": True,
        "legacy_revocation_mechanism_ready": True,
        "state_projection_complete": True,
        "projection": projection,
    }
    values.update(overrides)
    return CutoverReadinessInput(**values)


class LegacyProjectionTest(unittest.TestCase):
    def test_active_external_authority_and_human_gate_are_preserved(self):
        projection = LegacyOperationalProjection.from_legacy_mapping(legacy_state())
        self.assertTrue(projection.quiescent)
        self.assertEqual(
            [claim.issue_number for claim in projection.external_claims],
            [613, 754],
        )
        self.assertEqual(
            projection.external_claims[1].branch,
            "implementation/754-dr70-human-visible-repair",
        )
        self.assertIn(
            "dr-human-exploration-rereview",
            projection.roadmap.blocked_node_ids,
        )
        self.assertIn(
            "Machines must not self-pass",
            projection.roadmap.blocked_nodes[
                "dr-human-exploration-rereview"
            ]["reason"],
        )

    def test_projection_digest_changes_if_authority_changes(self):
        first = LegacyOperationalProjection.from_legacy_mapping(legacy_state())
        changed = legacy_state()
        changed["external_producer_claims"].pop("613")
        second = LegacyOperationalProjection.from_legacy_mapping(changed)
        self.assertNotEqual(first.digest, second.digest)

    def test_non_quiescent_worker_decision_managed_event_or_block_fails_quiescence(self):
        variants = [
            {"pending_worker": {"branch": "work"}},
            {"pending_decision": {"kind": "CLASSIFIER"}},
            {"managed": {"Implementation": {"pr_number": 1}}},
            {"pending_events": [{"event": "workflow_run"}]},
            {"blocked_kind": "provider_capacity"},
        ]
        for change in variants:
            with self.subTest(change=change):
                projection = LegacyOperationalProjection.from_legacy_mapping(
                    legacy_state(**change)
                )
                self.assertFalse(projection.quiescent)


class CutoverReadinessTest(unittest.TestCase):
    def test_all_readiness_contracts_allow_authority_switch(self):
        projection = LegacyOperationalProjection.from_legacy_mapping(legacy_state())
        decision = evaluate_cutover_readiness(readiness(projection))
        self.assertEqual(
            decision.disposition,
            CutoverReadinessDisposition.READY_FOR_AUTHORITY_SWITCH,
        )
        self.assertEqual(decision.blockers, ())

    def test_missing_hosted_runtime_blocks(self):
        projection = LegacyOperationalProjection.from_legacy_mapping(legacy_state())
        decision = evaluate_cutover_readiness(
            readiness(projection, hosted_v2_runtime_accepted=False)
        )
        self.assertIn(
            "hosted Platform-v2 production runtime is not accepted",
            decision.blockers,
        )

    def test_legacy_checkout_mismatch_blocks(self):
        projection = LegacyOperationalProjection.from_legacy_mapping(legacy_state())
        decision = evaluate_cutover_readiness(
            readiness(projection, legacy_runtime_sha=OLD)
        )
        self.assertIn(
            "legacy runtime checkout does not match accepted cutover main",
            decision.blockers,
        )

    def test_missing_checkpoint_blocks(self):
        projection = LegacyOperationalProjection.from_legacy_mapping(legacy_state())
        decision = evaluate_cutover_readiness(
            readiness(projection, pre_cutover_checkpoint_pass=False)
        )
        self.assertIn(
            "pre-v2-cutover rollback checkpoint is not PASS",
            decision.blockers,
        )

    def test_active_mutation_gate_blocks(self):
        projection = LegacyOperationalProjection.from_legacy_mapping(legacy_state())
        decision = evaluate_cutover_readiness(
            readiness(projection, mutation_gate_disabled=False)
        )
        self.assertIn(
            "bounded canary mutation gate is still enabled",
            decision.blockers,
        )

    def test_missing_privileged_revocation_mechanism_blocks(self):
        projection = LegacyOperationalProjection.from_legacy_mapping(legacy_state())
        decision = evaluate_cutover_readiness(
            readiness(projection, legacy_revocation_mechanism_ready=False)
        )
        self.assertIn(
            "privileged legacy-writer revocation mechanism is not operational",
            decision.blockers,
        )

    def test_non_quiescent_state_blocks(self):
        projection = LegacyOperationalProjection.from_legacy_mapping(
            legacy_state(pending_events=[{"event": "x"}])
        )
        decision = evaluate_cutover_readiness(readiness(projection))
        self.assertIn(
            "legacy controller state is not operationally quiescent",
            decision.blockers,
        )

    def test_report_never_grants_mutation_authority(self):
        observation = {
            "release4_accepted": True,
            "mutation_gate_disabled": True,
            "accepted_main_sha": MAIN,
            "legacy_runtime_sha": OLD,
            "legacy_service_active": True,
            "pre_cutover_checkpoint_pass": False,
            "hosted_v2_runtime_accepted": False,
            "ingress_handoff_defined": False,
            "writer_revocation_plan_defined": True,
            "legacy_revocation_mechanism_ready": False,
            "state_projection_complete": True,
        }
        report = cli.build_report(
            legacy_state=legacy_state(),
            observation=observation,
        )
        self.assertEqual(report["disposition"], "BLOCKED")
        self.assertFalse(report["ordinary_v2_mutation_authority"])


class WriterAuthorityHandoffTest(unittest.TestCase):
    def test_v2_cannot_activate_directly_from_legacy(self):
        with self.assertRaisesRegex(ValueError, "visibly revoked"):
            advance_writer_authority(
                WriterAuthority.LEGACY,
                WriterAuthorityAction.ACTIVATE_V2,
                readiness_accepted=True,
            )

    def test_v2_activation_requires_accepted_readiness(self):
        revoked = advance_writer_authority(
            WriterAuthority.LEGACY,
            WriterAuthorityAction.REVOKE_LEGACY,
            readiness_accepted=False,
        )
        self.assertEqual(revoked.after, WriterAuthority.NONE)
        with self.assertRaisesRegex(ValueError, "accepted cutover readiness"):
            advance_writer_authority(
                revoked.after,
                WriterAuthorityAction.ACTIVATE_V2,
                readiness_accepted=False,
            )

    def test_safe_cutover_and_rollback_require_revoked_window(self):
        revoked_legacy = advance_writer_authority(
            WriterAuthority.LEGACY,
            WriterAuthorityAction.REVOKE_LEGACY,
            readiness_accepted=True,
        )
        activated_v2 = advance_writer_authority(
            revoked_legacy.after,
            WriterAuthorityAction.ACTIVATE_V2,
            readiness_accepted=True,
        )
        self.assertEqual(activated_v2.after, WriterAuthority.V2)

        revoked_v2 = advance_writer_authority(
            activated_v2.after,
            WriterAuthorityAction.REVOKE_V2,
            readiness_accepted=True,
        )
        restored_legacy = advance_writer_authority(
            revoked_v2.after,
            WriterAuthorityAction.ACTIVATE_LEGACY,
            readiness_accepted=True,
        )
        self.assertEqual(restored_legacy.after, WriterAuthority.LEGACY)


if __name__ == "__main__":
    unittest.main()
