from __future__ import annotations

import unittest

from cockpit_yaw_route import CockpitYawRouteError, lower_cockpit_yaw_route


class CockpitYawRouteTests(unittest.TestCase):
    def fixtures(self):
        manifest = {
            "schemaVersion": "aircraft-probe-placement-manifest-ir-0.9",
            "digestSha256": "manifest",
            "placements": [
                {"kind": "pilot_occupancy_station", "lattice": [5,3,0], "resourceId": "create:brown_seat", "blockState": {"waterlogged": False}},
            ],
        }
        powertrain = {
            "schemaVersion": "aircraft-powertrain-ir-0.12",
            "digestSha256": "powertrain",
            "placements": [],
        }
        yaw = {
            "schemaVersion": "aircraft-yaw-control-ir-0.13.1",
            "digestSha256": "yaw",
            "placements": [
                {"mode": "add_main", "kind": "yaw_swivel_bearing", "lattice": [18,3,0], "resourceId": "simulated:swivel_bearing", "blockState": {"facing":"up"}},
            ],
            "airGapCoordinates": [],
            "metrics": {
                "v0131MovingParentMainBodyPlacementCount": 3,
                "nestedPropellerChildPlacementCount": 0,
                "yawControlChildPlacementCount": 0,
            },
        }
        steering = {
            "schemaVersion": "aircraft-steering-control-ir-0.17",
            "assetId": "skyforge.aircraft.test.v0_17_steering_control",
            "digestSha256": "steering",
            "validation": {"passed": True},
            "driveCogCoordinate": [18,3,1],
            "swivelBearingCoordinate": [18,3,0],
            "runtimeProbe": {"expectedSteeringWheelRpmMagnitude": 16},
        }
        profile = {
            "schemaVersion": "aircraft-cockpit-yaw-route-profile-0.18",
            "profileId": "test",
            "sourceSteeringControlDigestSha256": "steering",
            "resources": {
                "steeringWheel": "simulated:steering_wheel",
                "gearbox": "create:gearbox",
                "shaft": "create:shaft",
                "cogwheel": "create:cogwheel",
            },
            "pilotWheelCandidates": [
                {"name":"starboard","offsetFromPilotSeat":[0,0,1],"blockState":{"facing":"north","on_floor":"true","waterlogged":"false"},"preferencePenalty":0},
                {"name":"port","offsetFromPilotSeat":[0,0,-1],"blockState":{"facing":"south","on_floor":"true","waterlogged":"false"},"preferencePenalty":0},
            ],
            "routeBounds": {"minX":3,"maxX":18,"minZ":-3,"maxZ":3},
            "turnPenalty": 4,
            "pilotDistancePenalty": 3,
            "expectedSwivelExtraCogSignForPositiveWheelCommand": -1,
            "maxGlueSelectionDimensionBlocks": 24,
            "forbiddenCoordinates": [],
            "runtimeObligations": [
                {"id":"cockpit_route_primary_recapture_probe"},
                {"id":"cockpit_steering_network_probe"},
                {"id":"cockpit_steering_command_return_probe"},
                {"id":"post_route_mass_com_probe"},
            ],
        }
        return manifest, powertrain, yaw, steering, profile

    def test_direct_starboard_route_is_selected_and_sign_closes(self):
        args = self.fixtures()
        result = lower_cockpit_yaw_route(*args)
        self.assertEqual(result["selectedCandidate"], "starboard")
        self.assertEqual(result["steeringWheelCoordinate"], [5,3,1])
        self.assertEqual(result["routePath"], [[x,2,1] for x in range(5,19)])
        self.assertEqual(result["metrics"]["routePlacementCount"], 16)
        self.assertEqual(result["metrics"]["gearboxCount"], 2)
        self.assertEqual(result["metrics"]["shaftCount"], 12)
        self.assertEqual(result["metrics"]["routeTurnCount"], 0)
        self.assertEqual(result["signContract"]["positiveWheelCommandGeneratedSign"], -1)
        self.assertEqual(result["signContract"]["expectedTailDriveCogSign"], 1)
        self.assertEqual(result["signContract"]["expectedSwivelExtraCogSign"], -1)
        self.assertEqual(result["metrics"]["resultingMovingParentMainBodyPlacementCount"], 19)
        self.assertEqual(result["metrics"]["expectedPrimarySableTransferCount"], 19)
        self.assertTrue(result["readiness"]["cockpitRouteRuntimeProbeReady"])
        self.assertFalse(result["readiness"]["pilotInteractionBindingReady"])

    def test_search_routes_around_empty_service_plane_obstacle(self):
        manifest, powertrain, yaw, steering, profile = self.fixtures()
        manifest["placements"].append(
            {"kind":"airframe_structure","lattice":[10,2,1],"resourceId":"minecraft:spruce_planks","blockState":{}}
        )
        yaw["metrics"]["v0131MovingParentMainBodyPlacementCount"] = 4
        result = lower_cockpit_yaw_route(manifest, powertrain, yaw, steering, profile)
        self.assertNotIn([10,2,1], result["routePath"])
        self.assertGreaterEqual(result["metrics"]["routeTurnCount"], 2)
        self.assertEqual(result["signContract"]["expectedSwivelExtraCogSign"], -1)

    def test_complete_service_plane_wall_is_compile_visible(self):
        manifest, powertrain, yaw, steering, profile = self.fixtures()
        for z in range(-3,4):
            manifest["placements"].append(
                {"kind":"airframe_structure","lattice":[10,2,z],"resourceId":"minecraft:spruce_planks","blockState":{}}
            )
        yaw["metrics"]["v0131MovingParentMainBodyPlacementCount"] = 10
        with self.assertRaisesRegex(CockpitYawRouteError, "no cockpit-to-rudder route accepted"):
            lower_cockpit_yaw_route(manifest, powertrain, yaw, steering, profile)

    def test_source_steering_digest_mismatch_is_rejected(self):
        manifest, powertrain, yaw, steering, profile = self.fixtures()
        profile["sourceSteeringControlDigestSha256"] = "wrong"
        with self.assertRaisesRegex(CockpitYawRouteError, "source Steering Wheel digest mismatch"):
            lower_cockpit_yaw_route(manifest, powertrain, yaw, steering, profile)


if __name__ == "__main__":
    unittest.main()
