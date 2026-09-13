from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from target_realizer import preflight


class ProviderSpecificityTests(unittest.TestCase):
    def test_minimal_sufficient_capability_provider_beats_lower_priority_superset(self):
        assembly = {
            "schemaVersion": "aircraft-assembly-plan-ir-0.3",
            "assetId": "test.v0_3_assembly",
            "digestSha256": "abc",
            "validation": {"passed": True},
            "sites": [
                {"x": 0, "y": 0, "z": 0, "roles": ["fuselage_spine"], "capabilities": ["rigid_physics_member"]},
                {"x": 1, "y": 0, "z": 0, "roles": ["wing_surface_intent"], "capabilities": ["aerodynamic_lift_surface", "rigid_physics_member"]},
            ],
            "stations": [],
        }
        profile = {
            "schemaVersion": "aircraft-target-profile-0.4",
            "profileId": "specificity-test",
            "target": {"minecraft": "1", "loader": "n", "create": "c", "sable": "s", "simulated": "sim", "aeronautics": "a"},
            "siteProviders": [
                {
                    "providerId": "structure",
                    "resourceId": "minecraft:spruce_planks",
                    "capabilities": ["rigid_physics_member", "rigid_load_path"],
                    "evidenceLevel": "test",
                    "stateRule": "none",
                    "priority": 50,
                },
                {
                    "providerId": "multifunction_sail",
                    "resourceId": "simulated:white_symmetric_sail",
                    "capabilities": ["aerodynamic_lift_surface", "rigid_physics_member", "rigid_load_path"],
                    "evidenceLevel": "test",
                    "stateRule": "unresolved:axis",
                    "priority": 1,
                },
            ],
            "stationProviders": {},
            "requiredStations": [],
            "companionRequirements": [],
            "runtimeObligations": [],
        }
        result = preflight(assembly, profile)
        by_x = {p["lattice"][0]: p for p in result["placements"]}
        self.assertEqual(by_x[0]["providerId"], "structure")
        self.assertEqual(by_x[1]["providerId"], "multifunction_sail")
        self.assertEqual(result["metrics"]["unresolvedStateOrResourceCount"], 1)


if __name__ == "__main__":
    unittest.main()
