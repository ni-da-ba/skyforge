from __future__ import annotations

import unittest
from pathlib import Path

from runtime_v012_patch import patch_runtime_source as patch_v012
from runtime_v0131_patch import patch_runtime_source as patch_v0131
from runtime_v018_patch import patch_runtime_source as patch_v018
from runtime_v019_patch import patch_runtime_source as patch_v019
from runtime_v020_patch import patch_cockpit_settle_diagnostics as patch_v020_cockpit
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
        self.assertIn("SkyforgeAircraftCompilerPilotClientRuntimeAcceptance.bindAssembledParentSubLevel(subLevel);", patched)
        self.assertIn("SkyforgeAircraftCompilerPilotClientBridge.publish(", patched)
        self.assertIn('v020Probe.get("mouseYawDelta").getAsDouble()', patched)
        self.assertIn('v020Probe.get("seatDismountSettleTicks").getAsInt()', patched)
        self.assertLess(
            patched.index("SkyforgeAircraftCompilerPilotInteractionRuntimeAcceptance.verifyAssembledCockpitPresence("),
            patched.index("SkyforgeAircraftCompilerPilotClientRuntimeAcceptance.bindAssembledParentSubLevel(subLevel);"),
        )
        self.assertLess(
            patched.index("SkyforgeAircraftCompilerPilotClientRuntimeAcceptance.bindAssembledParentSubLevel(subLevel);"),
            patched.index("SkyforgeAircraftCompilerPilotClientBridge.publish("),
        )
        self.assertLess(
            patched.index("SkyforgeAircraftCompilerPilotClientBridge.publish("),
            patched.index("SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance.verify("),
        )

    def test_v020_integrated_client_keeps_physical_thresholds_but_uses_bounded_observation(self) -> None:
        repo_root = Path(__file__).resolve().parents[3]
        source = (
            repo_root / "skyforge-neoforge-1211/src/main/java/io/github/nidaba/skyforge/neoforge1211"
            / "SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance.java"
        ).read_text(encoding="utf-8")
        patched = patch_v020_cockpit(source)

        self.assertIn(
            "int maximumPhysicalSettlePhysicsTicks = Math.max(physicalSettlePhysicsTicks, 1) * 2;",
            patched,
        )
        self.assertIn("while (physicalDeflectTicks < maximumPhysicalSettlePhysicsTicks", patched)
        self.assertIn("runPhysics(physicsSystem, container, 1);", patched)
        self.assertIn(
            "Math.abs(physicalDeflected) >= minimumPhysicalRudderDeflectionDegrees",
            patched,
        )
        self.assertIn("Math.signum(physicalDeflected) == expectedExtraCogSign", patched)
        self.assertIn("while (physicalReturnTicks < maximumPhysicalSettlePhysicsTicks", patched)
        self.assertIn(
            "Math.abs(physicalReturned) <= physicalNeutralToleranceDegrees",
            patched,
        )
        self.assertIn('" observedPhysicsTicks=" + physicalDeflectTicks', patched)
        self.assertIn('" observedPhysicsTicks=" + physicalReturnTicks', patched)


if __name__ == "__main__":
    unittest.main()
