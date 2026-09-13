from __future__ import annotations

import copy
import unittest

from pilot_client_binding import PilotClientBindingError, lower_pilot_client_binding


class PilotClientBindingTests(unittest.TestCase):
    def setUp(self) -> None:
        self.pilot = {
            "schemaVersion": "aircraft-pilot-interaction-ir-0.19",
            "assetId": "guild_utility_monoplane_v0_19_pilot_interaction",
            "digestSha256": "pilot-interaction-digest",
            "pilotSeatCoordinate": [5, 3, 0],
            "pilotSeatResource": "create:brown_seat",
            "steeringWheelCoordinate": [5, 3, 1],
            "steeringWheelResource": "simulated:steering_wheel",
            "steeringWheelBlockState": {"facing": "north", "on_floor": "true", "waterlogged": "false"},
            "readiness": {
                "pilotInteractionStaticContractPassed": True,
                "realClientPilotProbeReady": True,
                "pilotOccupancyRuntimeQualified": False,
                "playerSableTrackingQualified": False,
                "steeringPacketRoundTripQualified": False,
                "flightQualified": False,
            },
            "validation": {"passed": True},
        }
        self.profile = {
            "schemaVersion": "aircraft-pilot-client-binding-profile-0.20",
            "profileId": "test.v0_20",
            "clientEntryContract": {
                "blockUseEntryPoint": "net.minecraft.client.multiplayer.MultiPlayerGameMode#useItemOn",
                "quietUseMixinClass": "dev.simulated_team.simulated.mixin.quiet_use.MultiPlayerGameModeMixin",
                "steeringHandlerClass": "dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelHandler",
                "mouseMoveDispatch": "dev.simulated_team.simulated.events.SimulatedCommonClientEvents#onMouseMove",
                "useReleaseDispatch": "dev.simulated_team.simulated.events.SimulatedCommonClientEvents#onBeforeMouseInput",
                "packetClass": "dev.simulated_team.simulated.network.packets.SteeringWheelPacket",
                "packetPayloadId": "simulated:steering_wheel_update",
                "seatBehaviorClass": "com.simibubi.create.content.contraptions.actors.seat.SeatBlock",
                "seatEntityClass": "com.simibubi.create.content.contraptions.actors.seat.SeatEntity",
            },
            "commandProbe": {
                "mouseYawDelta": 480.0,
                "minimumAbsoluteTargetDegrees": 30.0,
                "maximumAbsoluteTargetDegrees": 70.0,
                "activePacketSettleTicks": 40,
                "releasePacketSettleTicks": 40,
                "seatMountSettleTicks": 40,
                "seatDismountSettleTicks": 40,
            },
            "probeModes": {
                "actualClientRequired": [
                    "real_client_steering_hold_acquire",
                    "real_client_steering_active_packet_round_trip",
                    "real_client_steering_release_packet_round_trip",
                    "real_player_seat_mount",
                    "real_player_seat_dismount",
                ],
                "headlessServerCanQualify": [],
                "deferredRealClient": ["player_sable_tracking_probe"],
                "humanEyeBeforeFlightQualification": True,
            },
            "runtimeObligations": [
                {"id": "real_client_steering_hold_acquire", "method": "hold"},
                {"id": "real_client_steering_active_packet_round_trip", "method": "active packet"},
                {"id": "real_client_steering_release_packet_round_trip", "method": "release packet"},
                {"id": "real_player_seat_mount", "method": "mount"},
                {"id": "real_player_seat_dismount", "method": "dismount"},
            ],
        }

    def test_contract_is_deterministic_and_fail_closed(self) -> None:
        first = lower_pilot_client_binding(self.pilot, self.profile)
        second = lower_pilot_client_binding(self.pilot, self.profile)
        self.assertEqual(first["digestSha256"], second["digestSha256"])
        self.assertEqual(first["schemaVersion"], "aircraft-pilot-client-binding-ir-0.20")
        self.assertEqual(first["pilotSeatCoordinate"], [5, 3, 0])
        self.assertEqual(first["steeringWheelCoordinate"], [5, 3, 1])
        self.assertTrue(first["readiness"]["pilotClientBindingStaticContractPassed"])
        self.assertTrue(first["readiness"]["realClientInteractionProbeReady"])
        self.assertFalse(first["readiness"]["steeringPacketRoundTripQualified"])
        self.assertFalse(first["readiness"]["pilotOccupancyRuntimeQualified"])
        self.assertFalse(first["readiness"]["playerSableTrackingQualified"])
        self.assertFalse(first["readiness"]["flightQualified"])
        self.assertEqual(first["probeModes"]["deferredRealClient"], ["player_sable_tracking_probe"])

    def test_rejects_headless_qualification(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["probeModes"]["headlessServerCanQualify"] = ["real_player_seat_mount"]
        with self.assertRaisesRegex(PilotClientBindingError, "headlessly"):
            lower_pilot_client_binding(self.pilot, profile)

    def test_rejects_source_overclaim(self) -> None:
        pilot = copy.deepcopy(self.pilot)
        pilot["readiness"]["steeringPacketRoundTripQualified"] = True
        with self.assertRaisesRegex(PilotClientBindingError, "fail-closed"):
            lower_pilot_client_binding(pilot, self.profile)

    def test_rejects_client_entry_contract_drift(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["clientEntryContract"]["packetPayloadId"] = "simulated:not_the_real_packet"
        with self.assertRaisesRegex(PilotClientBindingError, "entry contract"):
            lower_pilot_client_binding(self.pilot, profile)

    def test_rejects_unbounded_command_probe(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["commandProbe"]["maximumAbsoluteTargetDegrees"] = 120.0
        with self.assertRaisesRegex(PilotClientBindingError, "target-angle bounds"):
            lower_pilot_client_binding(self.pilot, profile)

    def test_rejects_missing_runtime_obligation(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["runtimeObligations"] = [
            item for item in profile["runtimeObligations"] if item["id"] != "real_player_seat_dismount"
        ]
        with self.assertRaisesRegex(PilotClientBindingError, "missing runtime obligations"):
            lower_pilot_client_binding(self.pilot, profile)

    def test_rejects_implicit_moving_body_tracking_claim(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["probeModes"]["deferredRealClient"] = []
        with self.assertRaisesRegex(PilotClientBindingError, "defer moving-body"):
            lower_pilot_client_binding(self.pilot, profile)


if __name__ == "__main__":
    unittest.main()
