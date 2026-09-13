from __future__ import annotations

import unittest
from pathlib import Path

from runtime_v012_patch import patch_runtime_source as patch_v012
from runtime_v0131_patch import patch_runtime_source as patch_v0131
from runtime_v014_patch import patch_runtime_source as patch_v014
from runtime_v015_patch import patch_runtime_source as patch_v015


class RuntimeV015PatchTests(unittest.TestCase):
    def test_real_runtime_harness_patches_cleanly_through_v015(self) -> None:
        repo_root = Path(__file__).resolve().parents[3]
        source = (
            repo_root
            / "skyforge-neoforge-1211"
            / "src/main/java/io/github/nidaba/skyforge/neoforge1211"
            / "SkyforgeAircraftCompilerRuntimeAcceptance.java"
        ).read_text(encoding="utf-8")
        patched = patch_v015(patch_v014(patch_v0131(patch_v012(source))))
        self.assertIn("aircraftCompilerYawAuthority", patched)
        self.assertIn('"aircraft-yaw-authority-ir-0.15"', patched)
        self.assertIn("SkyforgeAircraftCompilerYawAuthorityRuntimeAcceptance.verify(", patched)
        self.assertLess(
            patched.index("SkyforgeAircraftCompilerYawActuationRuntimeAcceptance.verify("),
            patched.index("SkyforgeAircraftCompilerYawAuthorityRuntimeAcceptance.verify("),
        )
        self.assertLess(
            patched.index("SkyforgeAircraftCompilerYawAuthorityRuntimeAcceptance.verify("),
            patched.index("SkyforgeAircraftCompilerPropellerRuntimeAcceptance.verify("),
        )


if __name__ == "__main__":
    unittest.main()
