from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from probe_manifest import emit_probe_manifest


class ProbeManifestTests(unittest.TestCase):
    def fixtures(self):
        target = {"schemaVersion": "aircraft-minecraft-realization-preflight-ir-0.4", "digestSha256": "target", "placements": [{"lattice": [0,0,0], "providerId": "structure", "resourceId": "minecraft:spruce_planks"}, {"lattice": [1,0,0], "providerId": "sail", "resourceId": "create:white_sail"}]}
        propulsion = {"schemaVersion": "aircraft-propulsion-realization-ir-0.5", "digestSha256": "prop", "bearing": {"lattice": [-1,1,0], "resourceId": "aeronautics:propeller_bearing", "blockState": {"facing": "west"}}, "hub": {"lattice": [-2,1,0], "resourceId": "minecraft:spruce_planks", "blockState": {}}, "sails": [{"lattice": [-2,2,0], "resourceId": "simulated:white_symmetric_sail", "blockState": {"axis": "x"}}], "metrics": {"generatedPlacementCount": 3}}
        tail = {"schemaVersion": "aircraft-tail-lowering-ir-0.7", "digestSha256": "tail", "resolvedAerodynamicPlacements": [{"lattice": [1,0,0], "resourceId": "create:white_sail", "blockState": {"facing": "up"}}], "metrics": {"v07AerodynamicPlacementCount": 1}}
        pilot = {"schemaVersion": "aircraft-pilot-station-realization-ir-0.8", "assetId": "test.v0_8_pilot_station", "digestSha256": "pilot", "placement": {"lattice": [0,1,0], "resourceId": "create:brown_seat", "blockState": {"waterlogged": False}}, "readiness": {"probeSchematicEmissionReady": True, "runtimeBlockers": ["control_binding_runtime_unverified"]}}
        profile = {"schemaVersion": "aircraft-probe-manifest-profile-0.9", "profileId": "manifest-test", "airframeAerodynamicProviderId": "sail", "originContract": {"coordinateFrame": "test"}}
        return target, propulsion, tail, pilot, profile

    def test_manifest_is_coordinate_unique_and_runtime_conservative(self):
        result = emit_probe_manifest(*self.fixtures())
        self.assertTrue(result["validation"]["passed"])
        self.assertEqual(len(result["placements"]), 6)
        self.assertTrue(result["readiness"]["probePlacementManifestReady"])
        self.assertFalse(result["readiness"]["physicsAssemblyProbeReady"])
        self.assertIn("airframe_adhesion_graph_unresolved", result["readiness"]["mechanicalBlockers"])

    def test_duplicate_cross_layer_coordinate_fails(self):
        target, propulsion, tail, pilot, profile = self.fixtures()
        pilot["placement"]["lattice"] = [0,0,0]
        result = emit_probe_manifest(target, propulsion, tail, pilot, profile)
        self.assertFalse(result["validation"]["passed"])
        self.assertEqual(result["duplicateCoordinates"], [[0,0,0]])


if __name__ == "__main__":
    unittest.main()
