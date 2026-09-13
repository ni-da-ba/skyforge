from __future__ import annotations

import copy
import unittest

from yaw_authority import YawAuthorityLoweringError, lower_yaw_authority


class YawAuthorityLoweringTests(unittest.TestCase):
    def setUp(self) -> None:
        self.actuation = {
            "schemaVersion": "aircraft-yaw-actuation-ir-0.14",
            "assetId": "skyforge.aircraft.test.v0_14_yaw_actuation",
            "digestSha256": "act-digest",
            "validation": {"passed": True},
            "readiness": {"actuationRuntimeProbeReady": True},
        }
        self.profile = {
            "schemaVersion": "aircraft-yaw-authority-profile-0.15",
            "profileId": "test",
            "sourceYawActuationDigestSha256": "act-digest",
            "forwardSpeedMps": 10.0,
            "servoSettlePhysicsTicks": 80,
            "minimumPhysicalDeflectionDegrees": 5.0,
            "minimumLateralImpulseMagnitude": 0.0001,
            "minimumYawImpulseMagnitude": 0.0001,
            "runtimeObligations": [
                {"id": "rudder_physical_pose_response_probe"},
                {"id": "rudder_lateral_drag_probe"},
                {"id": "rudder_yaw_moment_probe"},
            ],
        }

    def test_probe_contract_is_ready_without_overclaim(self) -> None:
        out = lower_yaw_authority(self.actuation, self.profile)
        self.assertTrue(out["validation"]["passed"])
        self.assertTrue(out["readiness"]["yawAuthorityProbeReady"])
        self.assertFalse(out["readiness"]["yawAuthorityQualified"])
        self.assertFalse(out["readiness"]["neutralReturnQualified"])
        self.assertEqual([-1.0, 0.0, 0.0], out["runtimeProbe"]["flowDirectionAircraftLocal"])

    def test_source_digest_mismatch_is_rejected(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["sourceYawActuationDigestSha256"] = "wrong"
        with self.assertRaisesRegex(YawAuthorityLoweringError, "source actuation digest mismatch"):
            lower_yaw_authority(self.actuation, profile)

    def test_nonpositive_probe_parameter_is_rejected(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["forwardSpeedMps"] = 0
        with self.assertRaisesRegex(YawAuthorityLoweringError, "must be positive"):
            lower_yaw_authority(self.actuation, profile)

    def test_missing_runtime_obligation_is_rejected(self) -> None:
        profile = copy.deepcopy(self.profile)
        profile["runtimeObligations"].pop()
        with self.assertRaisesRegex(YawAuthorityLoweringError, "missing required runtime obligations"):
            lower_yaw_authority(self.actuation, profile)


if __name__ == "__main__":
    unittest.main()
