from __future__ import annotations

import copy
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from propulsion import PropulsionRealizationError, realize_propulsion


def assembly():
    return {
        "schemaVersion": "aircraft-assembly-plan-ir-0.3",
        "assetId": "skyforge.aircraft.test.v0_3_assembly",
        "digestSha256": "assembly",
        "validation": {"passed": True},
        "sites": [{"x": 2, "y": 3, "z": 0, "roles": ["fuselage_spine"], "capabilities": ["rigid_physics_member"]}],
        "stations": [{"name": "propeller_axis", "lattice": [0, 3, 0], "capabilities": ["rotational_thrust_producer"]}],
    }


def target():
    return {
        "schemaVersion": "aircraft-minecraft-realization-preflight-ir-0.4",
        "targetProfileId": "target",
        "digestSha256": "target-digest",
        "readiness": {"blockers": ["unsatisfied_contraption_companion_requirements", "unresolved_required_station_providers"]},
    }


def profile():
    return {
        "schemaVersion": "aircraft-propulsion-profile-0.5",
        "profileId": "prop",
        "targetProfileId": "target",
        "stationName": "propeller_axis",
        "bearing": {"resourceId": "aeronautics:propeller_bearing", "facing": "west"},
        "propeller": {
            "hubResourceId": "minecraft:spruce_planks",
            "sailResourceId": "simulated:white_symmetric_sail",
            "sailAxis": "x",
            "sailPowerPerBlock": 1,
            "minimumSailPower": 2,
            "targetSailPower": 8,
            "bladeOffsets": [[1,0],[2,0],[-1,0],[-2,0],[0,1],[0,2],[0,-1],[0,-2]],
        },
        "runtimeObligations": [{"id": "probe", "method": "measure"}],
    }


class PropulsionRealizationTests(unittest.TestCase):
    def test_four_blade_eight_sail_geometry_is_balanced_and_connected(self):
        result = realize_propulsion(assembly(), target(), profile())
        self.assertTrue(result["validation"]["passed"])
        self.assertEqual(result["metrics"]["propellerSailPower"], 8)
        self.assertEqual(result["geometry"]["transverseFirstMoment"], [0, 0])
        self.assertTrue(result["topologyChecks"]["centralSymmetry"])
        self.assertTrue(result["topologyChecks"]["bladeGraphConnectedToHub"])
        self.assertEqual(result["bearing"]["blockState"], {"facing": "west"})
        self.assertTrue(all(s["blockState"] == {"axis": "x"} for s in result["sails"]))

    def test_companion_blocker_is_resolved_but_runtime_is_not_faked(self):
        result = realize_propulsion(assembly(), target(), profile())
        self.assertIn("unsatisfied_contraption_companion_requirements", result["readiness"]["resolvedUpstreamBlockers"])
        self.assertNotIn("unsatisfied_contraption_companion_requirements", result["readiness"]["blockers"])
        self.assertIn("propulsion_runtime_obligations_unverified", result["readiness"]["blockers"])
        self.assertFalse(result["readiness"]["propulsionRuntimeQualified"])
        self.assertFalse(result["readiness"]["flightQualified"])

    def test_missing_sails_fails_target_power(self):
        p = profile()
        p["propeller"]["bladeOffsets"] = p["propeller"]["bladeOffsets"][:-2]
        result = realize_propulsion(assembly(), target(), p)
        self.assertFalse(result["validation"]["passed"])
        self.assertFalse(result["topologyChecks"]["targetSailPowerSatisfied"])

    def test_unbalanced_geometry_fails(self):
        p = profile()
        p["propeller"]["bladeOffsets"][-1] = [0, -3]
        result = realize_propulsion(assembly(), target(), p)
        self.assertFalse(result["validation"]["passed"])
        self.assertFalse(result["topologyChecks"]["zeroTransverseFirstMoment"])

    def test_collision_with_airframe_fails(self):
        a = assembly()
        a["sites"].append({"x": -1, "y": 3, "z": 0, "roles": ["fuselage_spine"], "capabilities": ["rigid_physics_member"]})
        result = realize_propulsion(a, target(), profile())
        self.assertFalse(result["validation"]["passed"])
        self.assertFalse(result["topologyChecks"]["hubCoordinateFree"])

    def test_axis_must_match_bearing_axis(self):
        p = profile()
        p["propeller"]["sailAxis"] = "z"
        with self.assertRaises(PropulsionRealizationError):
            realize_propulsion(assembly(), target(), p)

    def test_deterministic_under_blade_offset_reordering(self):
        p1 = profile()
        p2 = copy.deepcopy(p1)
        p2["propeller"]["bladeOffsets"].reverse()
        self.assertEqual(realize_propulsion(assembly(), target(), p1), realize_propulsion(assembly(), target(), p2))


if __name__ == "__main__":
    unittest.main()
