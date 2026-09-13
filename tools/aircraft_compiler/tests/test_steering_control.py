import copy
import unittest

from steering_control import SteeringControlLoweringError, lower_steering_control


class SteeringControlLoweringTests(unittest.TestCase):
    def setUp(self):
        self.actuation = {
            "schemaVersion": "aircraft-yaw-actuation-ir-0.14",
            "assetId": "skyforge.aircraft.guild_utility_monoplane.v0_14_yaw_actuation",
            "digestSha256": "act",
            "validation": {"passed": True},
            "swivelBearingCoordinate": [18, 3, 0],
            "driveCogCoordinate": [18, 3, 1],
            "creativeMotorCoordinate": [18, 2, 1],
        }
        self.neutral = {
            "schemaVersion": "aircraft-yaw-neutral-return-ir-0.16",
            "assetId": "skyforge.aircraft.guild_utility_monoplane.v0_16_yaw_neutral_return",
            "digestSha256": "neutral",
            "sourceYawActuationDigestSha256": "act",
            "validation": {"passed": True},
        }
        self.profile = {
            "schemaVersion": "aircraft-steering-control-profile-0.17",
            "profileId": "test",
            "sourceYawActuationDigestSha256": "act",
            "sourceYawNeutralReturnDigestSha256": "neutral",
            "steeringWheelResource": "simulated:steering_wheel",
            "steeringWheelBlockState": {"facing": "north", "on_floor": "false", "waterlogged": "false"},
            "expectedSteeringWheelRpmMagnitude": 16,
            "wheelCommandDegrees": 48.0,
            "maximumCommandTicks": 24,
            "physicalSettlePhysicsTicks": 80,
            "minimumSwivelTargetDeflectionDegrees": 20.0,
            "minimumPhysicalRudderDeflectionDegrees": 10.0,
            "targetNeutralToleranceDegrees": 2.0,
            "physicalNeutralToleranceDegrees": 3.0,
            "runtimeObligations": [
                {"id": "steering_wheel_source_registration_probe"},
                {"id": "steering_wheel_swivel_network_probe"},
                {"id": "steering_wheel_rudder_deflection_probe"},
                {"id": "steering_wheel_commanded_neutral_return_probe"},
            ],
        }

    def test_real_steering_wheel_substitution_is_probe_ready(self):
        out = lower_steering_control(self.actuation, self.neutral, self.profile)
        self.assertTrue(out["validation"]["passed"])
        self.assertEqual(out["steeringWheelCoordinate"], [18, 2, 1])
        self.assertEqual(out["steeringWheelResource"], "simulated:steering_wheel")
        self.assertTrue(out["readiness"]["steeringWheelSourceRuntimeProbeReady"])
        self.assertFalse(out["readiness"]["productionCockpitRoutingReady"])
        self.assertFalse(out["readiness"]["pilotInteractionBindingReady"])

    def test_source_coordinate_must_remain_directly_below_cog(self):
        act = copy.deepcopy(self.actuation)
        act["creativeMotorCoordinate"] = [17, 2, 1]
        with self.assertRaises(SteeringControlLoweringError):
            lower_steering_control(act, self.neutral, self.profile)

    def test_exact_resource_and_mount_state_are_fail_closed(self):
        profile = copy.deepcopy(self.profile)
        profile["steeringWheelBlockState"]["on_floor"] = "true"
        with self.assertRaises(SteeringControlLoweringError):
            lower_steering_control(self.actuation, self.neutral, profile)

    def test_neutral_digest_mismatch_is_rejected(self):
        profile = copy.deepcopy(self.profile)
        profile["sourceYawNeutralReturnDigestSha256"] = "wrong"
        with self.assertRaises(SteeringControlLoweringError):
            lower_steering_control(self.actuation, self.neutral, profile)

    def test_missing_obligation_is_rejected(self):
        profile = copy.deepcopy(self.profile)
        profile["runtimeObligations"] = profile["runtimeObligations"][:-1]
        with self.assertRaises(SteeringControlLoweringError):
            lower_steering_control(self.actuation, self.neutral, profile)


if __name__ == "__main__":
    unittest.main()
