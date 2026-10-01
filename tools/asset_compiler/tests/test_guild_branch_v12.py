from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from architectural_math import is_four_connected
from guild_branch_math_optimization import compile_guild_branch_v12
from minecraft_structure import encode_structure_nbt, inspect_export_header
from render import emit_outputs

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.12.json"


class GuildBranchMathematicalOptimizationTests(unittest.TestCase):
    def spec(self):
        return json.loads(SPEC.read_text(encoding="utf-8"))

    def test_v12_is_deterministic_and_valid(self):
        a = compile_guild_branch_v12(copy.deepcopy(self.spec()))
        b = compile_guild_branch_v12(copy.deepcopy(self.spec()))
        self.assertTrue(a.summary["validation"]["passed"], a.summary["validation"]["issues"])
        self.assertEqual(a.summary["digestSha256"], b.summary["digestSha256"])
        self.assertEqual("0.12", a.summary["compilerVersion"])

    def test_solver_selects_unique_ratio_feasible_integer_design(self):
        compiled = compile_guild_branch_v12(self.spec())
        solver = compiled.summary["layout"]["mathematicalOptimization"]["solver"]
        self.assertEqual(18, solver["candidateCount"])
        self.assertEqual(1, solver["feasibleCount"])
        self.assertEqual(
            {"mainRoofRise": 4, "publicWindowWidth": 3, "roofOverhang": 1},
            solver["chosen"],
        )
        self.assertGreater(solver["quality"], 0.99)

    def test_shape_grammar_is_authoritative_and_compact(self):
        compiled = compile_guild_branch_v12(self.spec())
        grammar = compiled.summary["layout"]["mathematicalOptimization"]["shapeGrammar"]
        self.assertEqual([[1, 3], [11, 13]], grammar["southWindowGroups"])
        self.assertEqual([[1, 3], [6, 8], [11, 13]], grammar["northWindowGroups"])
        self.assertTrue(grammar["upstreamGeometryMatchesSolvedGrammar"])
        self.assertLess(grammar["descriptionLengthProxy"], 6.0)
        rules = {item["rule"] for item in grammar["trace"]}
        self.assertIn("facade.bay.centered_opening", rules)
        self.assertIn("facade.bay.semantic_interruption", rules)

    def test_facade_layer_contract_has_no_conflicts(self):
        compiled = compile_guild_branch_v12(self.spec())
        layers = compiled.summary["layout"]["mathematicalOptimization"]["facadeLayers"]
        self.assertGreater(layers["claimCount"], 0)
        self.assertEqual([], layers["conflicts"])

    def test_space_graph_quantifies_public_route_and_visibility(self):
        compiled = compile_guild_branch_v12(self.spec())
        graph = compiled.summary["layout"]["mathematicalOptimization"]["spaceGraph"]
        self.assertGreater(graph["nodeCount"], 0)
        self.assertIsNotNone(graph["entranceToServiceDistance"])
        self.assertLessEqual(graph["entranceToServiceStretch"], 1.15)
        self.assertTrue(graph["entranceSeesService"])
        self.assertGreaterEqual(graph["connectedFractionFromEntrance"], 0.80)
        self.assertGreater(graph["entranceCloseness"], 0.0)
        self.assertGreater(graph["entranceVisibilityCount"], 0)

    def test_roof_field_drives_connected_inside_and_outside_geometry(self):
        compiled = compile_guild_branch_v12(self.spec())
        roof = compiled.summary["layout"]["mathematicalOptimization"]["roofField"]
        public_path = [tuple(item) for item in roof["public"]["connectedRafterPath"]]
        working_path = [tuple(item) for item in roof["working"]["connectedRafterPath"]]
        self.assertTrue(is_four_connected(public_path))
        self.assertTrue(is_four_connected(working_path))
        self.assertEqual(4, roof["public"]["rise"])
        self.assertTrue(roof["public"]["ridgeStations"])
        required = {
            "public_roof_eave_v12",
            "public_roof_ridge_cap_v12",
            "public_roof_rafter_v12",
            "public_roof_ridge_beam_v12",
            "public_roof_tie_beam_v12",
            "working_roof_eave_v12",
            "working_roof_ridge_cap_v12",
            "working_roof_rafter_v12",
            "working_roof_ridge_beam_v12",
            "working_roof_tie_beam_v12",
        }
        present = {cell.module for cell in compiled.model.cells.values() if cell.module}
        self.assertTrue(required <= present, required - present)

    def test_structural_graph_connects_roof_frame_to_grounded_support(self):
        compiled = compile_guild_branch_v12(self.spec())
        graph = compiled.summary["layout"]["mathematicalOptimization"]["structuralGraph"]
        self.assertGreater(graph["nodeCount"], 0)
        self.assertGreater(graph["supportCount"], 0)
        self.assertGreater(graph["roofTargetCount"], 0)
        self.assertEqual([], graph["unsupportedRoofTargets"])
        self.assertEqual("structural_legibility_not_engineering_load_capacity", graph["claim"])

    def test_reference_provenance_remains_explicitly_internal(self):
        compiled = compile_guild_branch_v12(self.spec())
        ref = compiled.summary["layout"]["mathematicalOptimization"]["referenceProfile"]
        self.assertEqual("working_reference", ref["status"])
        self.assertFalse(ref["provenance"]["externalMasterBuilderMeasurement"])

    def test_v12_exports_minecraft_structure_and_full_qa(self):
        compiled = compile_guild_branch_v12(self.spec())
        data = encode_structure_nbt(compiled)
        self.assertGreater(inspect_export_header(data)["compressedBytes"], 0)
        with tempfile.TemporaryDirectory() as td:
            out = Path(td)
            emit_outputs(compiled, out)
            for name in (
                "front.svg",
                "east.svg",
                "top.svg",
                "isometric.svg",
                "floorplan.svg",
                "interior_plan.svg",
                "cutaway_isometric.svg",
                "interior_section.svg",
                "validation.txt",
            ):
                self.assertTrue((out / name).exists(), name)
            self.assertTrue((out / "validation.txt").read_text(encoding="utf-8").startswith("PASS\n"))


if __name__ == "__main__":
    unittest.main()
