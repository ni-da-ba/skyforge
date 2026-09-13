from __future__ import annotations

import copy
import unittest

from pilot_interaction import PilotInteractionError, lower_pilot_interaction


class PilotInteractionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.pilot = {
            "schemaVersion": "aircraft-pilot-station-realization-ir-0.8",
            "assetId": "guild_utility_monoplane_v0_8_pilot_station",
            "digestSha256": "pilot-digest",
            "placement": {
                "lattice": [5, 3, 0],
                "resourceId": "create:brown_seat",
            },
            "validation": {"passed": True},
        }
        wheel_state = {"facing": "north", "on_floor": "true", "waterlogged": "false"}
        self.route = {
            "schemaVersion": "aircraft-cockpit-yaw-route-ir-0.18",
            "assetId": "guild_utility_monoplane_v0_18_cockpit_yaw_route",
            "digestSha256": "route-digest",
            "pilotSeatCoordinate": [5, 3, 0],
            "steeringWheelCoordinate": [5, 3, 1],
            "steeringWheelBlockState": wheel_state,
            "placements": [
                {
                    "kind": "cockpit_yaw_control_source",
                    "lattice": [5, 3, 1],
                    "resourceId": "simulated:steering_wheel",
                    "blockState": wheel_state,
                    "role": "cockpit_steering_wheel",
                    "mode": "add_main",
                }
            ],
            "validation": {"passed": True},
        }
        self.profile = {
            "schemaVersion": "aircraft-pilot-interaction-profile-0.19",
            "profileId": "test.v0_19",
            "sourceContract": {
                "seatBehaviorClass": "com.simibubi.create.content.contraptions.actors.seat.SeatBlock",
                "steeringHandlerClass": "dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelHandler",
                "steeringPacketClass": "dev.simulated_team.simulated.network.packets.SteeringWheelPacket",
                "steeringPacketPayloadId": "simulated:steering_wheel_update",
            },
            "steeringPacketContract": {
                "activePacketShouldStop": False,
                "releasePacketShouldStop": True,
                "activePacketServerAction": "startHolding",
                "releasePacketServerAction": "stopHolding",
                "targetAngleField": "targetAngleToUpdate",
                "positionField": "pos",
            },
            "probeModes": {
                "headlessServerCanQualify": ["assembled_cockpit_presence_probe"],
                "realClientRequired": [
                    "player_seat_interaction_probe",
                    "player_sable_tracking_probe",
                    "steering_wheel_client_hold_interaction_probe",
                    "steering_wheel_packet_round_trip_probe",
                ],
                "humanEyeBeforeFlightQualification": True,
            },
            "runtimeObligations": [
                {"id": "assembled_cockpit_presence_probe", "method": "presence"},
                {"id": "player_seat_interaction_probe", "method": "seat"},
                {"id": "player_sable_tracking_probe", "method": "tracking"},
                {"id": "steering_wheel_client_hold_interaction_probe", "method": "hold"},
                {"id": "steering_wheel_packet_round_trip_probe", "method": "packet"},
            ],
        }

    def test_contract_is_deterministic_and_conservative(self) -> None:
        first = lower_pilot_interaction(self.pilot, self.route, self.profile)
        second = lower_pilot_interaction(self.pilot, self.route, self.profile)
        self.assertEqual(first["digestSha256"], second["digestSha256"])
        self.assertEqual(first["pilotSeatCoordinate"], [5, 3, 0])
        self.assertEqual(first["steeringWheelCoordinate"], [5, 3, 1])
        self.assertEqual(first["steeringWheelResource"], "simulated:steering_wheel")
        self.assertEqual(first["steeringWheelBlockState"], self.route["steeringWheelBlockState"])
        self.assertEqual(first["cockpitGeometry"]["horizontalManhattanDistanceBlocks"], 1)
        self.assertTrue(first["readiness"]["pilotInteractionStaticContractPassed"])
        self.assertTrue(first["readiness"]["realClientPilotProbeReady"])
        self.assertFalse(first["readiness"]["pilotOccupancyRuntimeQualified"])
        self.assertFalse(first["readiness"]["pilotInteractionBindingReady"])
        self.assertFalse(first["readiness"]["steeringPacketRoundTripQualified"])
        self.assertFalse(first["readiness"]["flightQualified"])
        self.assertEqual(first["probeModes"]["headlessServerCanQualify"], ["assembled_cockpit_presence_probe"])
        self.assertIn("real client", " ".join(first["validation"]["doesNotProve"]).lower())

    def test_rejects_route_using_different_pilot_station(self) -> None:
        route = copy.deepcopy(self.route)
        route["pilotSeatCoordinate"] = [6, 3, 0]
        with self.assertRaisesRegex(PilotInteractionError, "same station"):
            lower_pilot_interaction(self.pilot, route, self.profile)

    def test_rejects_nonadjacent_wheel(self) -> None:
        route = copy.deepcopy(self.route)
        route["steeringWheelCoordinate"] = [5, 3, 2]
        route["placements"][0]["lattice"] = [5, 3, 2]
        with self.assertRaisesRegex(PilotInteractionError, "horizontally adjacent"):
            lower_pilot_interaction(self.pilot, route, self.profile)

    def test_rejects_headless_overclaim(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["probeModes"]["headlessServerCanQualify"].append("steering_wheel_packet_round_trip_probe")
        with self.assertRaisesRegex(PilotInteractionError, "headless server"):
            lower_pilot_interaction(self.pilot, self.route, profile)

    def test_rejects_source_contract_drift(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["sourceContract"]["steeringPacketPayloadId"] = "simulated:not_the_real_packet"
        with self.assertRaisesRegex(PilotInteractionError, "source-class contract"):
            lower_pilot_interaction(self.pilot, self.route, profile)

    def test_rejects_missing_real_client_obligation(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["runtimeObligations"] = [
            item for item in profile["runtimeObligations"] if item["id"] != "player_sable_tracking_probe"
        ]
        with self.assertRaisesRegex(PilotInteractionError, "missing runtime obligations"):
            lower_pilot_interaction(self.pilot, self.route, profile)


if __name__ == "__main__":
    unittest.main()
