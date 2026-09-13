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
                "movingBodyMembership": "required",
            },
            "mainBody": {
                "coordinates": [[0, -1, 0], [0, 0, 0], [1, 0, 0], [1, 1, 0]],
            },
            "nestedPropellerChild": {
                "coordinates": [[-1, 0, 0], [-1, 1, 0]],
            },
            "adhesionIntent": {
                "edgeCount": 3,
                "edges": [
                    {"a": [0, -1, 0], "b": [0, 0, 0]},
                    {"a": [0, 0, 0], "b": [1, 0, 0]},
                    {"a": [1, 0, 0], "b": [1, 1, 0]},
                ],
            },
        }
        profile = {
            "schemaVersion": "aircraft-glue-encoding-profile-0.11",
            "profileId": "glue-test",
            "encoding": {
                "policy": "bounded_super_glue_domain_cover_of_main_body_tree",
                "commandRoot": "create glue",
                "requiredPermissionLevel": 2,
                "maxSelectionDimensionBlocks": 24,
                "sourceContract": "Create-6.0.10-AllCommands/GlueCommand+SuperGlueEntity.span",
            },
            "glueDomains": [
                {"name": "main", "from": [0, -1, 0], "to": [1, 1, 0]},
            ],
            "forbiddenGlueEdges": [[[0, 0, 0], [-1, 0, 0]]],
            "runtimeObligations": [
                {"id": "create_glue_command_registry_probe"},
                {"id": "super_glue_entity_realization_probe"},
                {"id": "physics_assembler_capture_probe"},
                {"id": "glue_persistence_after_sublevel_move"},
            ],
        }
        return fixture, profile

    def test_bounded_domain_cover_is_probe_ready_without_runtime_claims(self):
        result = encode_glue_application(*self.fixtures())
        self.assertTrue(result["validation"]["passed"])
        self.assertEqual(result["metrics"]["mainBodyPlacementCount"], 4)
        self.assertEqual(result["metrics"]["adhesionIntentEdgeCount"], 3)
        self.assertEqual(result["metrics"]["coveredAdhesionIntentEdgeCount"], 3)
        self.assertEqual(result["metrics"]["glueDomainCount"], 1)
        self.assertEqual(result["metrics"]["glueCommandCount"], 1)
        self.assertEqual(result["physicsAssemblerCommand"], "setblock ~ ~-1 ~ simulated:physics_assembler[face=ceiling,facing=north] replace")
        self.assertEqual(result["glueCommands"], ["create glue ~ ~-1 ~ ~1 ~1 ~"])
        self.assertEqual(result["glueDomains"][0]["coveredAdhesionEdgeCount"], 3)
        self.assertTrue(result["checks"]["domainCoverEncoding"])
        self.assertTrue(result["checks"]["allAdhesionIntentEdgesCovered"])
        self.assertTrue(result["checks"]["noGlueDomainContainsNestedChild"])
        self.assertTrue(result["readiness"]["adhesionApplicationEncodingReady"])
        self.assertTrue(result["readiness"]["physicsAssemblyProbeReady"])
        self.assertFalse(result["readiness"]["runtimeQualificationReady"])
        self.assertFalse(result["readiness"]["flightQualified"])
        self.assertIn("control_surface_child_body_topology_unresolved", result["readiness"]["remainingMechanicalBlockers"])

    def test_non_adjacent_proof_edge_is_rejected(self):
        fixture, profile = self.fixtures()
        fixture = copy.deepcopy(fixture)
        fixture["adhesionIntent"]["edges"][1] = {"a": [0, 0, 0], "b": [2, 0, 0]}
        with self.assertRaises(GlueEncodingError):
            encode_glue_application(fixture, profile)

    def test_nested_child_proof_endpoint_is_rejected(self):
        fixture, profile = self.fixtures()
        fixture = copy.deepcopy(fixture)
        fixture["adhesionIntent"]["edges"][1] = {"a": [0, 0, 0], "b": [-1, 0, 0]}
        with self.assertRaises(GlueEncodingError):
            encode_glue_application(fixture, profile)

    def test_uncovered_proof_edge_is_rejected(self):
        fixture, profile = self.fixtures()
        profile = copy.deepcopy(profile)
        profile["glueDomains"] = [{"name": "partial", "from": [0, -1, 0], "to": [0, 0, 0]}]
        with self.assertRaises(GlueEncodingError):
            encode_glue_application(fixture, profile)

    def test_domain_exceeding_selection_limit_is_rejected(self):
        fixture, profile = self.fixtures()
        profile = copy.deepcopy(profile)
        profile["glueDomains"] = [{"name": "oversize", "from": [0, -1, 0], "to": [24, 1, 0]}]
        with self.assertRaises(GlueEncodingError):
            encode_glue_application(fixture, profile)

    def test_domain_containing_nested_child_is_rejected(self):
        fixture, profile = self.fixtures()
        profile = copy.deepcopy(profile)
        profile["glueDomains"] = [{"name": "bad", "from": [-1, -1, 0], "to": [1, 1, 0]}]
        with self.assertRaises(GlueEncodingError):
            encode_glue_application(fixture, profile)

    def test_domain_crossing_forbidden_dynamic_boundary_is_rejected(self):
        fixture, profile = self.fixtures()
        profile = copy.deepcopy(profile)
        fixture = copy.deepcopy(fixture)
        fixture["nestedPropellerChild"]["coordinates"] = [[-1, 1, 0]]
        profile["glueDomains"] = [{"name": "bad", "from": [-1, -1, 0], "to": [1, 0, 0]}]
        with self.assertRaises(GlueEncodingError):
            encode_glue_application(fixture, profile)


if __name__ == "__main__":
    unittest.main()
