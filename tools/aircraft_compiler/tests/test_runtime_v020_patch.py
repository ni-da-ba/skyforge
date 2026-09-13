from __future__ import annotations

import unittest
from pathlib import Path

from runtime_v012_patch import patch_runtime_source as patch_v012
from runtime_v0131_patch import patch_runtime_source as patch_v0131
from runtime_v018_patch import patch_runtime_source as patch_v018
from runtime_v019_patch import patch_runtime_source as patch_v019
from runtime_v020_patch import patch_runtime_source as patch_v020


class RuntimeV020PatchTests(unittest.TestCase):
    def test_real_runtime_harness_patches_cleanly_through_v020(self) -> None:
        repo_root = Path(__file__).resolve().parents[3]
        source = (
            repo_root / "skyforge-neoforge-1211/src/main/java/io/github/nidaba/skyforge/neoforge1211"
            / "SkyforgeAircraftCompilerRuntimeAcceptance.java"
        ).read_text(encoding="utf-8")
        patched = patch_v020(patch_v019(patch_v018(patch_v0131(patch_v012(source)))))
        self.assertIn("aircraftCompilerPilotClientBinding", patched)
        self.assertIn('"aircraft-pilot-client-binding-ir-0.20"', patched)
        self.assertIn("pilotClientBindingStaticContractPassed", patched)
        self.assertIn("realClientInteractionProbeReady", patched)
        self.assertIn("SkyforgeAircraftCompilerPilotClientBridge.publish(", patched)
        self.assertIn('v020Probe.get("mouseYawDelta").getAsDouble()', patched)
        self.assertIn('v020Probe.get("seatDismountSettleTicks").getAsInt()', patched)
        self.assertLess(
            patched.index("SkyforgeAircraftCompilerPilotInteractionRuntimeAcceptance.verifyAssembledCockpitPresence("),
            patched.index("SkyforgeAircraftCompilerPilotClientBridge.publish("),
        )
        self.assertLess(
            patched.index("SkyforgeAircraftCompilerPilotClientBridge.publish("),
            patched.index("SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance.verify("),
        )


if __name__ == "__main__":
    unittest.main()
