from __future__ import annotations

import unittest
from pathlib import Path

from runtime_v012_patch import patch_runtime_source as patch_v012
from runtime_v0131_patch import patch_runtime_source as patch_v0131
from runtime_v018_patch import patch_runtime_source as patch_v018


class RuntimeV018PatchTests(unittest.TestCase):
    def test_real_runtime_harness_patches_cleanly_through_v018(self) -> None:
        repo_root = Path(__file__).resolve().parents[3]
        source = (
            repo_root / "skyforge-neoforge-1211/src/main/java/io/github/nidaba/skyforge/neoforge1211"
            / "SkyforgeAircraftCompilerRuntimeAcceptance.java"
        ).read_text(encoding="utf-8")
        patched = patch_v018(patch_v0131(patch_v012(source)))
        self.assertIn("aircraftCompilerCockpitYawRoute", patched)
        self.assertIn('"aircraft-cockpit-yaw-route-ir-0.18"', patched)
        self.assertIn("cockpitRoutePlacements", patched)
        self.assertIn("v0.18 runtime SuperGlueEntity domain insertion count", patched)
        self.assertIn("SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance.verify(", patched)
        self.assertIn(
            "movedRetainedParentMainPositions,\n                expectedCockpitParentMain);",
            patched,
        )
        self.assertNotIn("SkyforgeAircraftCompilerYawActuationRuntimeAcceptance.verify(", patched)
        self.assertNotIn("SkyforgeAircraftCompilerSteeringControlRuntimeAcceptance.verify(", patched)
        self.assertLess(
            patched.index("SkyforgeAircraftCompilerYawRuntimeAcceptance.verify("),
            patched.index("SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance.verify("),
        )
        self.assertLess(
            patched.index("SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance.verify("),
            patched.index("SkyforgeAircraftCompilerPropellerRuntimeAcceptance.verify("),
        )


if __name__ == "__main__":
    unittest.main()
