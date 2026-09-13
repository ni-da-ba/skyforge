from __future__ import annotations

import copy
import unittest

from yaw_actuation import YawActuationLoweringError, lower_yaw_actuation


class YawActuationLoweringTests(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = {
            "schemaVersion": "aircraft-probe-placement-manifest-ir-0.9",
            "placements": [
                {"lattice": [0, 2, 0], "kind": "airframe_structure", "resourceId": "minecraft:spruce_planks", "blockState": {}},
                {"lattice": [17, 4, 0], "kind": "airframe_aerodynamic_surface", "resourceId": "create:white_sail", "blockState": {"facing": "south"}},
            ],
        }
        self.powertrain = {
            "schemaVersion": "aircraft-powertrain-ir-0.12",
            "placements": [],
        }
        self.yaw = {
            "schemaVersion": "aircraft-yaw-control-ir-0.13.1",
            "assetId": "skyforge.aircraft.test.v0_13_1_yaw_control",
            "digestSha256": "yaw-digest",
            "validation": {"passed": True},
            "readiness": {"rudderChildCaptureProbeReady": True},
            "swivelBearingCoordinate": [18, 3, 0],
            "rudderChildCoordinates": [[18,4,0],[18,5,0],[18,6,0],[18,7,0]],
            "airGapCoordinates": [[17,4,0],[17,5,0],[17,6,0],[17,7,0]],
            "placements": [
                {"role":"yaw_swivel_bearing","mode":"add_main","lattice":[18,3,0],"resourceId":"simulated:swivel_bearing","blockState":{"assembled":"false","facing":"up","powered":"false"}},
                {"role":"yaw_air_gap","mode":"remove_main","lattice":[17,4,0],"resourceId":"minecraft:air","blockState":{}},
                {"role":"rudder_seed","mode":"add_control_child","lattice":[18,4,0],"resourceId":"simulated:white_symmetric_sail","blockState":{"axis":"z"}},
                {"role":"rudder_1","mode":"add_control_child","lattice":[18,5,0],"resourceId":"simulated:white_symmetric_sail","blockState":{"axis":"z"}},
                {"role":"rudder_2","mode":"add_control_child","lattice":[18,6,0],"resourceId":"simulated:white_symmetric_sail","blockState":{"axis":"z"}},
                {"role":"rudder_3","mode":"add_control_child","lattice":[18,7,0],"resourceId":"simulated:white_symmetric_sail","blockState":{"axis":"z"}},
            ],
        }
        self.profile = {
            "schemaVersion": "aircraft-yaw-actuation-profile-0.14",
            "profileId": "test",
            "sourceYawControlDigestSha256": "yaw-digest",
            "runtimeFixturePlacements": [
                {"role":"yaw_test_drive_cog","offsetFromSwivel":[0,0,1],"kind":"runtime_test_actuator_transfer","resourceId":"create:cogwheel","blockState":{"axis":"y"}},
                {"role":"yaw_test_creative_motor","offsetFromSwivel":[0,-1,1],"kind":"runtime_test_actuator_source","resourceId":"create:creative_motor","blockState":{"facing":"up"}},
            ],
            "expectedCreativeMotorRpm": 16,
            "networkSettleTicks": 8,
            "commandTicks": 5,
            "stopSettleTicks": 8,
            "holdTicks": 4,
            "runtimeObligations": [
                {"id":"yaw_test_fixture_network_probe"},
                {"id":"rudder_signed_target_angle_probe"},
                {"id":"rudder_command_stop_hold_probe"},
            ],
        }

    def test_source_backed_two_block_fixture_is_probe_ready(self) -> None:
        out = lower_yaw_actuation(self.manifest, self.powertrain, self.yaw, self.profile)
        self.assertTrue(out["validation"]["passed"])
        self.assertEqual([18,3,1], out["driveCogCoordinate"])
        self.assertEqual([18,2,1], out["creativeMotorCoordinate"])
        self.assertEqual(16, out["runtimeProbe"]["expectedCreativeMotorRpmMagnitude"])
        self.assertTrue(out["readiness"]["actuationRuntimeProbeReady"])
        self.assertFalse(out["readiness"]["productionControlBindingReady"])
        self.assertFalse(out["readiness"]["neutralReturnQualified"])
        self.assertFalse(out["readiness"]["yawForceQualified"])

    def test_source_yaw_digest_mismatch_is_rejected(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["sourceYawControlDigestSha256"] = "wrong"
        with self.assertRaisesRegex(YawActuationLoweringError, "source yaw digest mismatch"):
            lower_yaw_actuation(self.manifest, self.powertrain, self.yaw, profile)

    def test_fixture_collision_is_rejected(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        manifest["placements"].append({"lattice":[18,3,1],"kind":"airframe_structure","resourceId":"minecraft:spruce_planks","blockState":{}})
        with self.assertRaisesRegex(YawActuationLoweringError, "runtime fixture collides"):
            lower_yaw_actuation(manifest, self.powertrain, self.yaw, self.profile)

    def test_wrong_drive_cog_axis_fails_static_gate(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["runtimeFixturePlacements"][0]["blockState"]["axis"] = "x"
        out = lower_yaw_actuation(self.manifest, self.powertrain, self.yaw, profile)
        self.assertFalse(out["validation"]["passed"])
        self.assertFalse(out["readiness"]["actuationRuntimeProbeReady"])


if __name__ == "__main__":
    unittest.main()
