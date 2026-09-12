from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
import sys
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from guild_branch_first_principles import compile_guild_branch_first_principles
from minecraft_structure import encode_structure_nbt
from render import emit_outputs


SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.13_first_principles.json"


class GuildBranchFirstPrinciplesTests(unittest.TestCase):
    def _spec(self):
        return json.loads(SPEC.read_text(encoding="utf-8"))

    def test_v13_is_deterministic_valid_and_independent(self):
        a = compile_guild_branch_first_principles(self._spec())
        b = compile_guild_branch_first_principles(self._spec())
        self.assertTrue(a.summary["validation"]["passed"], a.summary["validation"]["issues"])
        self.assertEqual(a.summary["digestSha256"], b.summary["digestSha256"])
        self.assertEqual(a.summary["compilerVersion"], "0.13-first-principles")
        self.assertEqual(a.summary["layout"]["generationMode"], "first_principles")
        source = (ROOT / "guild_branch_first_principles.py").read_text(encoding="utf-8")
        for prior in (
            "compile_guild_branch_v11",
            "compile_guild_branch_v12",
            "guild_branch_continuity",
            "guild_branch_math_optimization",
        ):
            self.assertNotIn(prior, source)

    def test_design_is_solved_from_program_and_candidate_domains(self):
        compiled = compile_guild_branch_first_principles(self._spec())
        fp = compiled.summary["layout"]["firstPrinciples"]
        opt = fp["optimization"]
        self.assertGreater(opt["candidateCount"], 1000)
        self.assertGreater(opt["feasibleCount"], 0)
        chosen = opt["chosen"]
        rp = compiled.summary["layout"]["resolvedParameters"]
        self.assertEqual(rp["bayCount"], chosen["bayCount"])
        self.assertEqual(rp["bayWidth"], chosen["bayWidth"])
        self.assertEqual(rp["hallDepth"], chosen["hallDepth"])
        self.assertEqual(rp["mainRoofRise"], chosen["roofRise"])
        self.assertEqual(rp["publicWindowWidth"], chosen["windowWidth"])
        self.assertEqual(rp["generationAuthority"], "semantic_program_constraint_optimization_grammar_fields")
        self.assertFalse(fp["referenceProfile"]["provenance"]["externalMasterBuilderMeasurement"])

    def test_facade_grammar_and_layers_are_authoritative(self):
        compiled = compile_guild_branch_first_principles(self._spec())
        fp = compiled.summary["layout"]["firstPrinciples"]
        grammar = fp["facadeGrammar"]
        self.assertTrue(grammar["southWindowGroups"])
        self.assertTrue(grammar["northWindowGroups"])
        self.assertTrue(grammar["westWindowGroups"])
        self.assertGreater(grammar["descriptionLengthProxy"], 0.0)
        self.assertEqual(fp["facadeLayers"]["conflicts"], [])
        entrance_bay = compiled.summary["layout"]["resolvedParameters"]["bayCount"] // 2
        interruptions = [t for t in grammar["trace"] if t.get("exception")]
        self.assertTrue(any(t.get("bay") == entrance_bay for t in interruptions))

    def test_space_graph_is_connected_and_service_is_visible(self):
        compiled = compile_guild_branch_first_principles(self._spec())
        space = compiled.summary["layout"]["firstPrinciples"]["spaceGraph"]
        self.assertEqual(space["entranceConnectedFraction"], 1.0)
        self.assertIsNotNone(space["entranceToServiceDistance"])
        self.assertLessEqual(space["entranceToServicePathStretch"], 1.25)
        self.assertTrue(space["entranceSeesService"])
        self.assertGreater(space["entranceVisibilityCount"], 0)

    def test_roof_field_and_structure_are_connected(self):
        compiled = compile_guild_branch_first_principles(self._spec())
        fp = compiled.summary["layout"]["firstPrinciples"]
        roof = fp["roofFields"]
        self.assertEqual(roof["public"]["type"], "two_eave_wavefront")
        self.assertTrue(roof["public"]["ridgeStations"])
        self.assertTrue(roof["working"]["ridgeStations"])
        self.assertTrue(roof["publicRafterCells"])
        structural = fp["structuralGraph"]
        self.assertGreater(structural["roofTargetCount"], 0)
        self.assertEqual(structural["unsupportedTargets"], [])
        self.assertEqual(structural["claim"], "structural_legibility_only_not_load_capacity")

    def test_semantic_program_produces_required_volumes_and_anchors(self):
        compiled = compile_guild_branch_first_principles(self._spec())
        layout = compiled.summary["layout"]
        for name in ("publicHall", "administrative", "workingWing", "lightRepair", "warehouse"):
            self.assertIn(name, layout["volumes"])
        for name in self._spec()["requestedAnchors"]:
            self.assertIn(name, layout["anchors"])
        self.assertEqual(layout["paletteReview"]["status"], "provisional")

    def test_v13_exports_minecraft_structure_and_full_qa(self):
        compiled = compile_guild_branch_first_principles(self._spec())
        data = encode_structure_nbt(compiled)
        self.assertGreater(len(data), 100)
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
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


if __name__ == "__main__":
    unittest.main()
