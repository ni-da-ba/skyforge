from __future__ import annotations

import copy
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from target_realizer import TargetRealizerError, preflight


def assembly():
    return {
        "schemaVersion": "aircraft-assembly-plan-ir-0.3",
        "assetId": "skyforge.aircraft.test.v0_3_assembly",
        "digestSha256": "abc",
        "validation": {"passed": True},
        "sites": [
            {"x": 0, "y": 0, "z": 0, "roles": ["fuselage_spine"], "capabilities": ["rigid_physics_member"]},
            {"x": 1, "y": 0, "z": 0, "roles": ["wing_surface_intent", "wing_attach_intent"], "capabilities": ["aerodynamic_lift_surface", "rigid_load_path", "rigid_physics_member"]}
        ],
        "stations": [
            {"name": "propeller_axis", "lattice": [0, 0, 0], "capabilities": ["rotational_thrust_producer"]},
            {"name": "pilot_station", "lattice": [1, 0, 0], "capabilities": ["vehicle_control_station"]}
        ]
    }


def profile():
    return {
        "schemaVersion": "aircraft-target-profile-0.4",
        "profileId": "test",
        "target": {"minecraft": "1", "loader": "n", "create": "c", "sable": "s", "simulated": "sim", "aeronautics": "a"},
        "siteProviders": [
            {"providerId": "solid", "resourceId": "minecraft:stone", "capabilities": ["rigid_physics_member", "rigid_load_path"], "evidenceLevel": "test", "stateRule": "none", "priority": 50},
            {"providerId": "sail", "resourceId": "simulated:white_symmetric_sail", "capabilities": ["aerodynamic_lift_surface", "rigid_physics_member", "rigid_load_path"], "evidenceLevel": "test", "stateRule": "unresolved:axis", "priority": 10}
        ],
        "stationProviders": {
            "propeller_axis": {"status": "source_verified", "resourceId": "aeronautics:propeller_bearing"},
            "pilot_station": {"status": "unresolved"}
        },
        "requiredStations": ["propeller_axis", "pilot_station"],
        "companionRequirements": [{"id": "prop", "role": "propeller_sail_intent", "minimumCount": 2}],
        "runtimeObligations": [{"id": "probe", "method": "measure"}]
    }


class TargetRealizerTests(unittest.TestCase):
    def test_joint_capability_provider_is_selected_without_fallback(self):
        result = preflight(assembly(), profile())
        self.assertTrue(result["validation"]["passed"])
        self.assertEqual(result["metrics"]["mappedSiteCount"], 2)
        wing = [p for p in result["placements"] if "aerodynamic_lift_surface" in p["requiredCapabilities"]][0]
        self.assertEqual(wing["providerId"], "sail")

    def test_static_coverage_does_not_imply_schematic_or_flight_readiness(self):
        result = preflight(assembly(), profile())
        self.assertTrue(result["readiness"]["staticCapabilityCoveragePassed"])
        self.assertFalse(result["readiness"]["schematicEmissionReady"])
        self.assertFalse(result["readiness"]["flightQualified"])
        self.assertIn("unsatisfied_contraption_companion_requirements", result["readiness"]["blockers"])
        self.assertIn("unresolved_required_station_providers", result["readiness"]["blockers"])

    def test_missing_joint_provider_is_compile_visible(self):
        p = profile()
        p["siteProviders"][1]["capabilities"].remove("rigid_load_path")
        result = preflight(assembly(), p)
        self.assertFalse(result["validation"]["passed"])
        self.assertEqual(result["metrics"]["unresolvedSiteCount"], 1)

    def test_companion_requirement_can_pass_when_geometry_exists(self):
        a = assembly()
        a["sites"].extend([
            {"x": 2, "y": 0, "z": 0, "roles": ["propeller_sail_intent"], "capabilities": ["aerodynamic_lift_surface", "rigid_physics_member"]},
            {"x": 3, "y": 0, "z": 0, "roles": ["propeller_sail_intent"], "capabilities": ["aerodynamic_lift_surface", "rigid_physics_member"]},
        ])
        result = preflight(a, profile())
        self.assertEqual(result["metrics"]["companionFailureCount"], 0)

    def test_invalid_target_tuple_is_rejected(self):
        p = profile()
        del p["target"]["sable"]
        with self.assertRaises(TargetRealizerError):
            preflight(assembly(), p)

    def test_deterministic_under_provider_reordering(self):
        p1 = profile()
        p2 = copy.deepcopy(p1)
        p2["siteProviders"].reverse()
        self.assertEqual(preflight(assembly(), p1), preflight(assembly(), p2))


if __name__ == "__main__":
    unittest.main()
