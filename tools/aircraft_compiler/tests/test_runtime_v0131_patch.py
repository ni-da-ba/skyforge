from __future__ import annotations

import unittest
from pathlib import Path

from runtime_v012_patch import patch_runtime_source as patch_v012
from runtime_v0131_patch import patch_runtime_source as patch_v0131


class RuntimeV0131PatchTests(unittest.TestCase):
    def test_real_runtime_harness_patches_cleanly_through_v0131(self) -> None:
        repo_root = Path(__file__).resolve().parents[3]
        source = (
            repo_root
            / "skyforge-neoforge-1211"
            / "src/main/java/io/github/nidaba/skyforge/neoforge1211"
            / "SkyforgeAircraftCompilerRuntimeAcceptance.java"
        ).read_text(encoding="utf-8")
        patched = patch_v0131(patch_v012(source))

        self.assertIn("EXPECTED_V0131_MOVING_PARENT_MAIN_BODY = 118", patched)
        self.assertIn("EXPECTED_RUDDER_PAYLOAD = 4", patched)
        self.assertIn("EXPECTED_PRIMARY_SABLE_PAYLOAD = 131", patched)
        self.assertIn("EXPECTED_GLUE_DOMAINS = 7", patched)
        self.assertIn("aircraftCompilerYawControlCorrection", patched)
        self.assertIn('getAsJsonArray("airGapCoordinates")', patched)
        self.assertIn('getAsJsonArray("runtimeGlueDomains")', patched)
        self.assertIn("SkyforgeAircraftCompilerYawRuntimeAcceptance.verify(", patched)
        self.assertIn('assertTransferred(level, rudderExpected, offset, "rudder-payload")', patched)
        self.assertIn('level.setBlock(BASE.offset(pos), Blocks.AIR.defaultBlockState(), 3)', patched)
        self.assertNotIn("powerplantGlueEntity", patched)
        self.assertLess(
            patched.index("SkyforgeAircraftCompilerYawRuntimeAcceptance.verify("),
            patched.index("SkyforgeAircraftCompilerPropellerRuntimeAcceptance.verify("),
        )


if __name__ == "__main__":
    unittest.main()
