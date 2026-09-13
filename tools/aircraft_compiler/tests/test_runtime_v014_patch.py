from __future__ import annotations

import unittest
from pathlib import Path

from runtime_v012_patch import patch_runtime_source as patch_v012
from runtime_v0131_patch import patch_runtime_source as patch_v0131
from runtime_v014_patch import patch_runtime_source as patch_v014


class RuntimeV014PatchTests(unittest.TestCase):
    def test_real_runtime_harness_patches_cleanly_through_v014(self) -> None:
        repo_root = Path(__file__).resolve().parents[3]
        source = (
            repo_root
            / "skyforge-neoforge-1211"
            / "src/main/java/io/github/nidaba/skyforge/neoforge1211"
            / "SkyforgeAircraftCompilerRuntimeAcceptance.java"
        ).read_text(encoding="utf-8")
        patched = patch_v014(patch_v0131(patch_v012(source)))

        self.assertIn("aircraftCompilerYawActuation", patched)
        self.assertIn('"aircraft-yaw-actuation-ir-0.14"', patched)
        self.assertIn("SkyforgeAircraftCompilerYawActuationRuntimeAcceptance.verify(", patched)
        self.assertIn('getAsJsonArray("driveCogCoordinate")', patched)
        self.assertIn('getAsJsonArray("creativeMotorCoordinate")', patched)
        self.assertIn('get("commandTicks").getAsInt()', patched)
        self.assertLess(
            patched.index("SkyforgeAircraftCompilerYawRuntimeAcceptance.verify("),
            patched.index("SkyforgeAircraftCompilerYawActuationRuntimeAcceptance.verify("),
        )
        self.assertLess(
            patched.index("SkyforgeAircraftCompilerYawActuationRuntimeAcceptance.verify("),
            patched.index("SkyforgeAircraftCompilerPropellerRuntimeAcceptance.verify("),
        )


if __name__ == "__main__":
    unittest.main()
