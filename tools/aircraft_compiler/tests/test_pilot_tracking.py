from __future__ import annotations

import copy
import unittest

from pilot_tracking import PilotTrackingError, lower_pilot_tracking


class PilotTrackingTests(unittest.TestCase):
    def setUp(self) -> None:
        self.source = {
            "schemaVersion": "aircraft-pilot-client-binding-ir-0.20",
            "assetId": "guild_utility_monoplane_v0_20_pilot_client_binding",
            "digestSha256": "pilot-client-digest",
            "pilotSeatCoordinate": [5, 3, 0],
            "steeringWheelCoordinate": [5, 3, 1],
            "readiness": {
                "pilotClientBindingStaticContractPassed": True,
                "realClientInteractionProbeReady": True,
                "playerSableTrackingQualified": False,
                "flightQualified": False,
            },
            "validation": {"passed": True},
        }
        self.profile = {
            "schemaVersion": "aircraft-pilot-tracking-profile-0.21",
            "profileId": "test.v0_21",
            "acceptedPrerequisites": {
                "v020ActualClientRuntimeEvidence": True,
                "evidenceBoundary": "accepted v0.20",
            },
            "trackingContract": {
                "acquisition": "natural_client_sublevel_collision_then_movement_packet",
                "preMeasurementInput": "bounded_ordinary_jump_key",
                "inspection": "sable_helper_get_tracking_sub_level",
                "parentIdentityMethod": "sable_persistent_sublevel_uuid",
                "measurementBeginsAfterDismount": True,
                "trackingSetterAllowed": False,
                "playerHarnessMutationDuringMeasurementAllowed": False,
            },
            "motionProbe": {
                "translationVelocityMetersPerSecond": 2.0,
                "minimumParentTranslationBlocks": 0.15,
                "serverPlayerDeltaToleranceBlocks": 0.08,
                "trackingAcquireSettleTicks": 40,
                "translationSettleTicks": 40,
            },
            "runtimeObligations": [
                {"id": "natural_player_sable_tracking", "method": "observe"},
                {"id": "tracking_parent_identity", "method": "persistent Sable UUID"},
                {"id": "post_dismount_inherited_parent_translation", "method": "motion"},
            ],
        }

    def test_contract_is_deterministic_and_fail_closed(self) -> None:
        first = lower_pilot_tracking(self.source, self.profile)
        second = lower_pilot_tracking(self.source, self.profile)
        self.assertEqual(first["digestSha256"], second["digestSha256"])
        self.assertEqual(first["schemaVersion"], "aircraft-pilot-tracking-ir-0.21")
        self.assertEqual(
            first["trackingContract"]["acquisition"],
            "natural_client_sublevel_collision_then_movement_packet",
        )
        self.assertEqual(first["trackingContract"]["preMeasurementInput"], "bounded_ordinary_jump_key")
        self.assertEqual(
            first["trackingContract"]["parentIdentityMethod"],
            "sable_persistent_sublevel_uuid",
        )
        self.assertTrue(first["readiness"]["pilotTrackingStaticContractPassed"])
        self.assertTrue(first["readiness"]["v020ActualClientRuntimePrerequisiteAccepted"])
        self.assertTrue(first["readiness"]["pilotTrackingRuntimeProbeReady"])
        self.assertFalse(first["readiness"]["playerSableTrackingQualified"])
        self.assertFalse(first["readiness"]["inheritedParentTranslationQualified"])
        self.assertFalse(first["readiness"]["completedControlsPersistenceQualified"])
        self.assertFalse(first["readiness"]["pitchRollQualified"])
        self.assertFalse(first["readiness"]["flightQualified"])

    def test_rejects_tracking_setter_as_evidence(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["trackingContract"]["trackingSetterAllowed"] = True
        with self.assertRaisesRegex(PilotTrackingError, "natural tracking contract"):
            lower_pilot_tracking(self.source, profile)

    def test_rejects_nonpersistent_parent_identity(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["trackingContract"]["parentIdentityMethod"] = "runtime_object_identity"
        with self.assertRaisesRegex(PilotTrackingError, "natural tracking contract"):
            lower_pilot_tracking(self.source, profile)

    def test_rejects_player_mutation_during_measurement(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["trackingContract"]["playerHarnessMutationDuringMeasurementAllowed"] = True
        with self.assertRaisesRegex(PilotTrackingError, "natural tracking contract"):
            lower_pilot_tracking(self.source, profile)

    def test_rejects_missing_v020_runtime_acceptance(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["acceptedPrerequisites"]["v020ActualClientRuntimeEvidence"] = False
        with self.assertRaisesRegex(PilotTrackingError, "v0.20 actual-client runtime"):
            lower_pilot_tracking(self.source, profile)

    def test_rejects_unbounded_motion_probe(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["motionProbe"]["translationVelocityMetersPerSecond"] = 12.0
        with self.assertRaisesRegex(PilotTrackingError, "translation velocity"):
            lower_pilot_tracking(self.source, profile)

    def test_rejects_missing_runtime_obligation(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["runtimeObligations"] = profile["runtimeObligations"][:-1]
        with self.assertRaisesRegex(PilotTrackingError, "missing runtime obligations"):
            lower_pilot_tracking(self.source, profile)


if __name__ == "__main__":
    unittest.main()
