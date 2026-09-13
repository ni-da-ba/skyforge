from __future__ import annotations

import copy
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from glue_encoding import GlueEncodingError, encode_glue_application


class GlueEncodingTests(unittest.TestCase):
    def fixtures(self):
        fixture = {
            "schemaVersion": "aircraft-assembly-fixture-ir-0.10",
            "assetId": "test.v0_10_assembly_fixture",
            "digestSha256": "fixture",
            "readiness": {
                "assemblyFixtureTopologyPassed": True,
                "mainBodyAdhesionGraphResolved": True,
            },
            "physicsAssemblerPlacement": {
                "lattice": [0, -1, 0],
                "resourceId": "simulated:physics_assembler",
                "blockState": {"face": "ceiling", "facing": "north"},
                "seedLattice": [0, 0, 0],
            },
            "mainBody": {
                "coordinates": [[0, 0, 0], [1, 0, 0], [1, 1, 0]],
            },
            "nestedPropellerChild": {
                "coordinates": [[-1, 0, 0], [-1, 1, 0]],
            },
            "adhesionIntent": {
                "edgeCount": 2,
                "edges": [
                    {"a": [0, 0, 0], "b": [1, 0, 0]},
                    {"a": [1, 0, 0], "b": [1, 1, 0]},
                ],
            },
        }
        profile = {
            "schemaVersion": "aircraft-glue-encoding-profile-0.11",
            "profileId": "glue-test",
            "encoding": {
                "policy": "one_super_glue_entity_per_main_body_tree_edge",
                "commandRoot": "create glue",
                "requiredPermissionLevel": 2,
                "sourceContract": "Create-6.0.10-AllCommands/GlueCommand",
            },
            "forbiddenGlueEdges": [[[0, 0, 0], [-1, 0, 0]]],
            "runtimeObligations": [
                {"id": "create_glue_command_registry_probe"},
                {"id": "super_glue_entity_realization_probe"},
                {"id": "physics_assembler_capture_probe"},
                {"id": "glue_persistence_after_sublevel_move"},
            ],
        }
        return fixture, profile

    def test_edge_exact_commands_are_probe_ready_without_runtime_claims(self):
        result = encode_glue_application(*self.fixtures())
        self.assertTrue(result["validation"]["passed"])
        self.assertEqual(result["metrics"]["glueCommandCount"], 2)
        self.assertEqual(result["physicsAssemblerCommand"], "setblock ~ ~-1 ~ simulated:physics_assembler[face=ceiling,facing=north] replace")
        self.assertEqual(result["glueCommands"], [
            "create glue ~ ~ ~ ~1 ~ ~",
            "create glue ~1 ~ ~ ~1 ~1 ~",
        ])
        self.assertTrue(result["readiness"]["adhesionApplicationEncodingReady"])
        self.assertTrue(result["readiness"]["physicsAssemblyProbeReady"])
        self.assertFalse(result["readiness"]["runtimeQualificationReady"])
        self.assertFalse(result["readiness"]["flightQualified"])
        self.assertIn("control_surface_child_body_topology_unresolved", result["readiness"]["remainingMechanicalBlockers"])

    def test_non_adjacent_edge_is_rejected(self):
        fixture, profile = self.fixtures()
        fixture = copy.deepcopy(fixture)
        fixture["adhesionIntent"]["edges"][0] = {"a": [0, 0, 0], "b": [2, 0, 0]}
        with self.assertRaises(GlueEncodingError):
            encode_glue_application(fixture, profile)

    def test_nested_child_endpoint_is_rejected(self):
        fixture, profile = self.fixtures()
        fixture = copy.deepcopy(fixture)
        fixture["adhesionIntent"]["edges"][0] = {"a": [0, 0, 0], "b": [-1, 0, 0]}
        with self.assertRaises(GlueEncodingError):
            encode_glue_application(fixture, profile)

    def test_forbidden_dynamic_boundary_is_rejected_even_if_misclassified_main(self):
        fixture, profile = self.fixtures()
        fixture = copy.deepcopy(fixture)
        fixture["mainBody"]["coordinates"].append([-1, 0, 0])
        fixture["nestedPropellerChild"]["coordinates"] = [[-1, 1, 0]]
        fixture["adhesionIntent"]["edgeCount"] = 3
        fixture["adhesionIntent"]["edges"].append({"a": [0, 0, 0], "b": [-1, 0, 0]})
        with self.assertRaises(GlueEncodingError):
            encode_glue_application(fixture, profile)


if __name__ == "__main__":
    unittest.main()
