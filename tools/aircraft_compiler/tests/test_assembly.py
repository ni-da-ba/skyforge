from __future__ import annotations

import copy
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from assembly import AssemblyPlanError, plan_assembly


def specimen():
    return {
        "schemaVersion": "aircraft-blockspace-ir-0.2",
        "assetId": "skyforge.aircraft.test.v0_2_blockspace",
        "digestSha256": "abc123",
        "coordinateSystem": {"x": "nose_to_tail", "y": "up", "z": "starboard_positive"},
        "cells": [
            {"x": 1, "y": 2, "z": 0, "role": "fuselage_spine"},
            {"x": 1, "y": 2, "z": 0, "role": "wing_attach_intent"},
            {"x": 2, "y": 2, "z": 0, "role": "fuselage_spine"},
        ],
        "anchors": {
            "propeller_axis": {"continuousM": [0.0, 1.0, 0.0], "lattice": [0, 2, 0]},
        },
        "capabilityContract": {
            "roles": {
                "fuselage_spine": ["rigid_physics_member"],
                "wing_attach_intent": ["rigid_load_path"],
                "propeller_axis": ["rotational_thrust_producer", "shaft_power_input"],
            }
        },
        "validation": {"passed": True},
    }


class AssemblyPlanTests(unittest.TestCase):
    def test_coalesces_roles_without_losing_semantics(self):
        result = plan_assembly(specimen())
        self.assertTrue(result["validation"]["passed"])
        self.assertEqual(result["metrics"]["inputCellRecordCount"], 3)
        self.assertEqual(result["metrics"]["uniqueAssemblySiteCount"], 2)
        self.assertEqual(result["metrics"]["collapsedRoleRecordCount"], 1)
        self.assertEqual(result["metrics"]["multiRoleSiteCount"], 1)
        self.assertEqual(result["sites"][0]["roles"], ["fuselage_spine", "wing_attach_intent"])
        self.assertEqual(result["sites"][0]["capabilities"], ["rigid_load_path", "rigid_physics_member"])

    def test_is_deterministic_under_source_record_reordering(self):
        a = specimen()
        b = copy.deepcopy(a)
        b["cells"].reverse()
        self.assertEqual(plan_assembly(a), plan_assembly(b))

    def test_anchor_is_requirement_not_fake_occupied_site(self):
        result = plan_assembly(specimen())
        self.assertEqual(result["stations"][0]["name"], "propeller_axis")
        self.assertEqual(result["stations"][0]["placementSemantics"], "anchor_requirement_not_occupied_site")
        self.assertEqual(result["metrics"]["uniqueAssemblySiteCount"], 2)

    def test_unknown_role_is_rejected(self):
        bad = specimen()
        bad["cells"].append({"x": 9, "y": 9, "z": 9, "role": "invented_role"})
        with self.assertRaises(AssemblyPlanError):
            plan_assembly(bad)

    def test_invalid_source_is_rejected(self):
        bad = specimen()
        bad["validation"]["passed"] = False
        with self.assertRaises(AssemblyPlanError):
            plan_assembly(bad)


if __name__ == "__main__":
    unittest.main()
