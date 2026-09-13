from __future__ import annotations

import unittest
from pathlib import Path

from runtime_v012_patch import patch_runtime_source as patch_v012
from runtime_v0131_patch import patch_runtime_source as patch_v0131
from runtime_v018_patch import patch_runtime_source as patch_v018
from runtime_v019_patch import patch_runtime_source as patch_v019


class RuntimeV019PatchTests(unittest.TestCase):
    def test_real_runtime_harness_patches_cleanly_through_v019(self) -> None:
        repo_root = Path(__file__).resolve().parents[3]
        source = (
            repo_root / "skyforge-neoforge-1211/src/main/java/io/github/nidaba/skyforge/neoforge1211"
            / "SkyforgeAircraftCompilerRuntimeAcceptance.java"
        ).read_text(encoding="utf-8")
        patched = patch_v019(patch_v018(patch_v0131(patch_v012(source))))
        self.assertIn("aircraftCompilerPilotInteraction", patched)
        self.assertIn('"aircraft-pilot-interaction-ir-0.19"', patched)
        self.assertIn("SkyforgeAircraftCompilerPilotInteractionRuntimeAcceptance.verifyAssembledCockpitPresence(", patched)
        self.assertIn("assembledCockpitPresenceProbeReady", patched)
        self.assertIn("!pilotInteraction.getAsJsonObject(\"readiness\").get(\"pilotOccupancyRuntimeQualified\")", patched)
        self.assertIn("!pilotInteraction.getAsJsonObject(\"readiness\").get(\"steeringPacketRoundTripQualified\")", patched)
        self.assertIn("BlockPos v018WheelCoordinate", patched)
        self.assertIn("v018WheelCoordinate.equals(v019WheelCoordinate)", patched)
        self.assertNotIn(
            'assertEquals(\n                "v0.19 Steering Wheel coordinate chains to v0.18",',
            patched,
        )
        self.assertLess(
            patched.index("SkyforgeAircraftCompilerPilotInteractionRuntimeAcceptance.verifyAssembledCockpitPresence("),
            patched.index("SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance.verify("),
        )


if __name__ == "__main__":
    unittest.main()
