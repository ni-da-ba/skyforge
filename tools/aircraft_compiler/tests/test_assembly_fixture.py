from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from assembly_fixture import plan_assembly_fixture


class AssemblyFixtureTests(unittest.TestCase):
    def fixtures(self):
        manifest = {
            "schemaVersion": "aircraft-probe-placement-manifest-ir-0.9",
            "assetId": "test.v0_9_probe_manifest",
            "digestSha256": "manifest",
            "readiness": {"probePlacementManifestReady": True},
            "placements": [
                {"kind":"airframe_structure","lattice":[0,2,0],"resourceId":"minecraft:spruce_planks","blockState":{}},
                {"kind":"airframe_aerodynamic_surface","lattice":[1,2,0],"resourceId":"create:white_sail","blockState":{"facing":"up"}},
                {"kind":"propeller_bearing","lattice":[0,3,0],"resourceId":"aeronautics:propeller_bearing","blockState":{"facing":"west"}},
                {"kind":"pilot_occupancy_station","lattice":[1,3,0],"resourceId":"create:brown_seat","blockState":{"waterlogged":False}},
                {"kind":"propeller_hub","lattice":[-1,3,0],"resourceId":"minecraft:spruce_planks","blockState":{}},
                {"kind":"propeller_sail","lattice":[-1,4,0],"resourceId":"simulated:white_symmetric_sail","blockState":{"axis":"x"}},
            ],
        }
        profile = {
            "schemaVersion":"aircraft-assembly-fixture-profile-0.10","profileId":"fixture-test",
            "mainBodyKinds":["airframe_structure","airframe_aerodynamic_surface","propeller_bearing","pilot_occupancy_station"],
            "nestedChildKinds":["propeller_hub","propeller_sail"],
            "seedCoordinate":[0,2,0],"assemblerOffsetFromSeed":[0,-1,0],
            "physicsAssembler":{"resourceId":"simulated:physics_assembler","blockState":{"face":"ceiling","facing":"north"}},
            "runtimeObligations":[{"id":"physics_assembler_capture_probe"},{"id":"adhesion_application_probe"},{"id":"nested_propeller_capture_probe"}],
        }
        return manifest, profile

    def test_main_body_spanning_tree_includes_assembler_and_excludes_propeller_child(self):
        result = plan_assembly_fixture(*self.fixtures())
        self.assertTrue(result["validation"]["passed"])
        self.assertEqual(result["physicsAssemblerPlacement"]["lattice"], [0,1,0])
        self.assertEqual(result["metrics"]["mainBodyManifestPlacementCount"], 4)
        self.assertEqual(result["metrics"]["mainBodyPlacementCount"], 5)
        self.assertEqual(result["metrics"]["nestedChildPlacementCount"], 2)
        self.assertEqual(result["metrics"]["adhesionIntentEdgeCount"], 4)
        self.assertTrue(result["topologyChecks"]["physicsAssemblerIncludedInMovingMainBody"])
        self.assertTrue(result["topologyChecks"]["physicsAssemblerHasAdhesionEdgeToSeed"])
        self.assertTrue(result["topologyChecks"]["nestedChildExcludedFromMainAdhesionGraph"])
        self.assertFalse(result["readiness"]["adhesionApplicationEncodingReady"])

    def test_disconnected_main_body_is_compile_visible(self):
        manifest, profile = self.fixtures()
        manifest["placements"].append({"kind":"airframe_structure","lattice":[20,20,20],"resourceId":"minecraft:spruce_planks","blockState":{}})
        result = plan_assembly_fixture(manifest, profile)
        self.assertFalse(result["validation"]["passed"])
        self.assertEqual(result["metrics"]["mainBodyUnreachableCount"], 1)


if __name__ == "__main__":
    unittest.main()
