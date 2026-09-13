from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from pilot_station import PilotStationError, realize_pilot_station


class PilotStationTests(unittest.TestCase):
    def fixtures(self):
        target = {
            "schemaVersion": "aircraft-minecraft-realization-preflight-ir-0.4",
            "digestSha256": "target",
            "stations": [{"name": "pilot_station", "lattice": [5,2,0]}],
            "placements": [
                {"lattice": [5,2,0], "providerId": "structure", "resourceId": "minecraft:spruce_planks"},
                {"lattice": [7,4,0], "providerId": "sail", "resourceId": "create:white_sail"},
            ],
        }
        propulsion = {
            "schemaVersion": "aircraft-propulsion-realization-ir-0.5", "digestSha256": "prop",
            "bearing": {"lattice": [0,3,0]}, "hub": {"lattice": [-1,3,0]}, "sails": []
        }
        tail = {
            "schemaVersion": "aircraft-tail-lowering-ir-0.7", "assetId": "test.v0_7_tail_lowering", "digestSha256": "tail",
            "validation": {"passed": True},
            "resolvedAerodynamicPlacements": [{"lattice": [7,4,0]}],
            "readiness": {"tailJunctionLoweringPassed": True, "surfaceStateResolutionComplete": True, "runtimeBlockers": ["aircraft_runtime_obligations_unverified"]},
        }
        profile = {
            "schemaVersion": "aircraft-pilot-station-profile-0.8", "profileId": "pilot-test", "stationName": "pilot_station",
            "installationOffsetBlocks": [0,1,0], "airframeAerodynamicProviderId": "sail",
            "seat": {"resourceId": "create:brown_seat", "blockState": {"waterlogged": False}},
            "controlBindingContract": {"status": "runtime_unverified", "requiredForProbeEmission": False, "requiredForFlightQualification": True},
            "runtimeObligations": [{"id": "pilot_occupancy_probe"}, {"id": "control_binding_probe"}],
        }
        return target, propulsion, tail, profile

    def test_seat_is_installed_above_structural_anchor_without_claiming_controls(self):
        result = realize_pilot_station(*self.fixtures())
        self.assertTrue(result["validation"]["passed"])
        self.assertEqual(result["placement"]["lattice"], [5,3,0])
        self.assertEqual(result["placement"]["resourceId"], "create:brown_seat")
        self.assertTrue(result["readiness"]["probeSchematicEmissionReady"])
        self.assertFalse(result["readiness"]["runtimeQualificationReady"])
        self.assertFalse(result["readiness"]["flightQualified"])
        self.assertIn("control_binding_runtime_unverified", result["readiness"]["runtimeBlockers"])

    def test_collision_at_seat_location_fails_static_gate(self):
        target, propulsion, tail, profile = self.fixtures()
        target["placements"].append({"lattice": [5,3,0], "providerId": "structure", "resourceId": "minecraft:spruce_planks"})
        result = realize_pilot_station(target, propulsion, tail, profile)
        self.assertFalse(result["validation"]["passed"])
        self.assertFalse(result["topologyChecks"]["seatCoordinateFree"])

    def test_control_binding_cannot_be_marked_static_verified(self):
        target, propulsion, tail, profile = self.fixtures()
        profile["controlBindingContract"]["status"] = "source_verified"
        with self.assertRaises(PilotStationError):
            realize_pilot_station(target, propulsion, tail, profile)


if __name__ == "__main__":
    unittest.main()
