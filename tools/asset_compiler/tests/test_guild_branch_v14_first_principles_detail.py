from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
import sys
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from architectural_detail_math import blue_noise_maximin_select, hungarian_min_cost, minimum_pair_distance
from guild_branch_first_principles_detail import compile_guild_branch_first_principles_detail
from minecraft_structure import encode_structure_nbt
from render import emit_outputs

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.14_first_principles_detail.json"


class GuildBranchFirstPrinciplesDetailTests(unittest.TestCase):
    def _spec(self):
        return json.loads(SPEC.read_text(encoding="utf-8"))

    def _compile(self):
        return compile_guild_branch_first_principles_detail(self._spec())

    def test_hungarian_assignment_matches_known_optimum(self):
        assignment, total = hungarian_min_cost([[4, 1, 3], [2, 0, 5], [3, 2, 2]])
        self.assertEqual(assignment, [1, 0, 2])
        self.assertEqual(total, 5.0)

    def test_discrete_maximin_sampling_is_deterministic_and_spaced(self):
        candidates = [(x, 0) for x in range(20)]
        a = blue_noise_maximin_select(candidates, 4, 4.0, 1401)
        b = blue_noise_maximin_select(candidates, 4, 4.0, 1401)
        self.assertEqual(a, b)
        self.assertEqual(len(a), 4)
        self.assertGreaterEqual(minimum_pair_distance(a), 4.0)

    def test_v14_is_deterministic_valid_and_keeps_first_principles_authority(self):
        a = self._compile()
        b = self._compile()
        self.assertTrue(a.summary["validation"]["passed"], a.summary["validation"]["issues"])
        self.assertEqual(a.summary["digestSha256"], b.summary["digestSha256"])
        self.assertEqual(a.summary["compilerVersion"], "0.14-first-principles-detail")
        layout = a.summary["layout"]
        self.assertEqual(layout["generationMode"], "first_principles")
        self.assertEqual(layout["firstPrinciplesDetail"]["baseCompiler"], "0.13-first-principles")
        self.assertEqual(layout["resolvedParameters"]["generationAuthority"], "semantic_program_constraint_optimization_grammar_fields")

    def test_detail_is_bounded_and_controlled_history_is_sparse(self):
        compiled = self._compile()
        detail = compiled.summary["layout"]["firstPrinciplesDetail"]
        self.assertLessEqual(detail["detailAddedBlockFraction"], self._spec()["detailPass"]["maxAddedBlockFraction"])
        history = detail["controlledHistory"]
        self.assertGreaterEqual(history["placedBlockCount"], 4)
        if history["northMinimumPairDistance"] is not None:
            self.assertGreaterEqual(history["northMinimumPairDistance"], 4.0)
        if history["westMinimumPairDistance"] is not None:
            self.assertGreaterEqual(history["westMinimumPairDistance"], 4.0)

    def test_fixture_assignment_is_unique_and_minimum_cost(self):
        compiled = self._compile()
        assignment = compiled.summary["layout"]["firstPrinciplesDetail"]["fixtureAssignment"]
        self.assertEqual(assignment["algorithm"], "hungarian_minimum_cost_assignment")
        self.assertEqual(len(assignment["publicSites"]), 4)
        self.assertEqual(len({tuple(v) for v in assignment["publicSites"]}), 4)
        self.assertEqual(len(assignment["workingSites"]), 2)
        self.assertEqual(len({tuple(v) for v in assignment["workingSites"]}), 2)
        self.assertGreaterEqual(assignment["publicTotalCost"], 0.0)
        self.assertGreaterEqual(assignment["workingTotalCost"], 0.0)

    def test_detail_pass_preserves_spatial_and_structural_invariants(self):
        compiled = self._compile()
        fp = compiled.summary["layout"]["firstPrinciples"]
        space = fp["spaceGraph"]
        self.assertEqual(space["entranceConnectedFraction"], 1.0)
        self.assertTrue(space["entranceSeesService"])
        self.assertLessEqual(space["entranceToServicePathStretch"], 1.25)
        self.assertEqual(fp["structuralGraph"]["unsupportedTargets"], [])

    def test_detail_modules_are_present_without_replacing_first_principles_geometry(self):
        compiled = self._compile()
        modules = compiled.summary["moduleBlockCounts"]
        for name in (
            "fp14_public_eave_fascia",
            "fp14_counter_cap",
            "fp14_assigned_public_light",
            "fp14_compact_roof_signal",
        ):
            self.assertGreater(modules.get(name, 0), 0, name)
        detail = compiled.summary["layout"]["firstPrinciplesDetail"]
        self.assertIn("shared_roof_field_preserved", detail["authorities"])

    def test_v14_exports_minecraft_structure_and_full_qa(self):
        compiled = self._compile()
        data = encode_structure_nbt(compiled)
        self.assertGreater(len(data), 100)
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            emit_outputs(compiled, out)
            for name in (
                "front.svg", "east.svg", "top.svg", "isometric.svg", "floorplan.svg",
                "interior_plan.svg", "cutaway_isometric.svg", "interior_section.svg", "validation.txt",
            ):
                self.assertTrue((out / name).exists(), name)


if __name__ == "__main__":
    unittest.main()
