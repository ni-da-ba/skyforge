import copy
import unittest

from yaw_neutral_return import YawNeutralReturnLoweringError, lower_yaw_neutral_return


class YawNeutralReturnLoweringTests(unittest.TestCase):
    def setUp(self):
        self.actuation = {
            "schemaVersion": "aircraft-yaw-actuation-ir-0.14",
            "assetId": "skyforge.aircraft.guild_utility_monoplane.v0_14_yaw_actuation",
            "digestSha256": "act",
            "validation": {"passed": True},
            "runtimeProbe": {
                "expectedCreativeMotorRpmMagnitude": 16,
                "networkSettleTicks": 8,
                "commandTicks": 5,
                "stopSettleTicks": 8,
            },
        }
        self.authority = {
            "schemaVersion": "aircraft-yaw-authority-ir-0.15",
            "assetId": "skyforge.aircraft.guild_utility_monoplane.v0_15_yaw_authority",
            "digestSha256": "auth",
            "sourceYawActuationDigestSha256": "act",
            "validation": {"passed": True},
        }
        self.profile = {
            "schemaVersion": "aircraft-yaw-neutral-return-profile-0.16",
            "profileId": "test",
            "sourceYawActuationDigestSha256": "act",
            "sourceYawAuthorityDigestSha256": "auth",
            "reverseMotorSign": -1,
            "physicalSettlePhysicsTicks": 80,
            "minimumStartingDeflectionDegrees": 5.0,
            "targetNeutralToleranceDegrees": 0.1,
            "physicalNeutralToleranceDegrees": 2.0,
            "runtimeObligations": [
                {"id": "real_kinetic_reverse_command_probe"},
                {"id": "swivel_target_neutral_return_probe"},
                {"id": "rudder_physical_neutral_return_probe"},
            ],
        }

    def test_inverse_real_kinetic_command_contract_is_probe_ready(self):
        out = lower_yaw_neutral_return(self.actuation, self.authority, self.profile)
        self.assertTrue(out["validation"]["passed"])
        self.assertTrue(out["readiness"]["neutralReturnProbeReady"])
        self.assertFalse(out["readiness"]["commandedNeutralReturnQualified"])
        self.assertEqual(out["runtimeProbe"]["inverseDriveTicks"], 13)
        self.assertEqual(out["runtimeProbe"]["reverseMotorSign"], -1)

    def test_authority_must_chain_to_actuation(self):
        authority = copy.deepcopy(self.authority)
        authority["sourceYawActuationDigestSha256"] = "wrong"
        with self.assertRaises(YawNeutralReturnLoweringError):
            lower_yaw_neutral_return(self.actuation, authority, self.profile)

    def test_reverse_sign_is_fail_closed(self):
        profile = copy.deepcopy(self.profile)
        profile["reverseMotorSign"] = 1
        with self.assertRaises(YawNeutralReturnLoweringError):
            lower_yaw_neutral_return(self.actuation, self.authority, profile)

    def test_missing_runtime_obligation_is_rejected(self):
        profile = copy.deepcopy(self.profile)
        profile["runtimeObligations"] = profile["runtimeObligations"][:-1]
        with self.assertRaises(YawNeutralReturnLoweringError):
            lower_yaw_neutral_return(self.actuation, self.authority, profile)


if __name__ == "__main__":
    unittest.main()
