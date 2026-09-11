from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from guild_branch_articulation import compile_guild_branch_v08
from minecraft_structure import encode_structure_nbt, inspect_export_header
from reference_analysis import aggregate_reference_profile
from render import emit_outputs

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.8.json"


class GuildBranchArticulationCompilerTests(unittest.TestCase):
    def spec(self):
        return json.loads(SPEC.read_text(encoding="utf-8"))

    def test_v08_is_deterministic_and_valid(self):
        a = compile_guild_branch_v08(copy.deepcopy(self.spec()))
        b = compile_guild_branch_v08(copy.deepcopy(self.spec()))
        self.assertTrue(a.summary["validation"]["passed"], a.summary["validation"]["issues"])
        self.assertEqual(a.summary["digestSha256"], b.summary["digestSha256"])
        self.assertEqual("0.8", a.summary["compilerVersion"])

    def test_reference_ratio_report_is_explicit_and_passes(self):
        compiled = compile_guild_branch_v08(self.spec())
        articulation = compiled.summary["layout"]["articulation"]
        self.assertEqual("working_reference", articulation["referenceProfileStatus"])
        self.assertFalse(articulation["referenceProfileProvenance"]["externalMasterBuilderMeasurement"])
        self.assertTrue(articulation["ratioReport"])
        self.assertTrue(all(item["passed"] for item in articulation["ratioReport"].values()))

    def test_depth_plane_contract_matches_public_geometry(self):
        compiled = compile_guild_branch_v08(self.spec())
        articulation = compiled.summary["layout"]["articulation"]
        self.assertEqual(
            {"recessedClosure": -1, "wall": 0, "trim": 1, "canopy": 2},
            articulation["depthPlanes"],
        )
        actual = articulation["actualPublicFacadePlanes"]
        self.assertLess(actual["recessedClosure"], actual["wall"])
        self.assertLess(actual["wall"], actual["trim"])
        self.assertLess(actual["trim"], actual["canopy"])

    def test_joint_detail_follows_structural_rhythm(self):
        compiled = compile_guild_branch_v08(self.spec())
        rp = compiled.summary["layout"]["resolvedParameters"]
        hall_w = rp["hallWidth"]
        bay_w = rp["bayWidth"]
        expected_public = sorted(set(range(0, hall_w, bay_w)) | {hall_w - 1})
        actual_public = sorted({
            x for (x, _y, _z), cell in compiled.model.cells.items()
            if cell.module == "public_eave_bracket_v08"
        })
        self.assertEqual(expected_public, actual_public)
        self.assertTrue(any(cell.module == "working_eave_bracket_v08" for cell in compiled.model.cells.values()))

    def test_controlled_history_is_sparse_deterministic_and_explained(self):
        compiled = compile_guild_branch_v08(self.spec())
        interventions = compiled.summary["layout"]["articulation"]["historyInterventions"]
        self.assertEqual(1, len(interventions))
        self.assertEqual("masonry_repair_patch", interventions[0]["type"])
        self.assertIn("repair", interventions[0]["explanation"])
        cells = [
            pos for pos, cell in compiled.model.cells.items()
            if cell.module == "history_masonry_repair_v08"
        ]
        self.assertEqual(4, len(cells))
        self.assertEqual(2, len({pos[2] for pos in cells}))
        self.assertEqual(2, len({pos[1] for pos in cells}))

    def test_measured_reference_aggregator_marks_external_provenance(self):
        records = [
            {"id": "a", "source": "fixture-a", "measurements": {
                "bayWidth": 5, "wallHeight": 6, "mainDepth": 13, "hallWidth": 16,
                "workingWingWidth": 8, "mainRoofRise": 4, "roofOverhang": 1,
                "publicCanopyDepth": 2, "workingCanopyDepth": 2,
                "publicEntranceWidth": 2, "averagePublicWindowWidth": 3,
            }},
            {"id": "b", "source": "fixture-b", "measurements": {
                "bayWidth": 6, "wallHeight": 7, "mainDepth": 15, "hallWidth": 19,
                "workingWingWidth": 9, "mainRoofRise": 5, "roofOverhang": 1,
                "publicCanopyDepth": 2, "workingCanopyDepth": 3,
                "publicEntranceWidth": 2, "averagePublicWindowWidth": 3,
            }},
        ]
        profile = aggregate_reference_profile(records, "fixture.measured")
        self.assertEqual("measured_reference", profile["status"])
        self.assertTrue(profile["provenance"]["externalMasterBuilderMeasurement"])
        self.assertEqual(2, profile["provenance"]["sampleCount"])

    def test_v08_exports_minecraft_structure_and_qa(self):
        compiled = compile_guild_branch_v08(self.spec())
        data = encode_structure_nbt(compiled)
        self.assertGreater(inspect_export_header(data)["compressedBytes"], 0)
        with tempfile.TemporaryDirectory() as td:
            out = Path(td)
            emit_outputs(compiled, out)
            for name in ("front.svg", "east.svg", "isometric.svg"):
                self.assertTrue((out / name).exists(), name)
            self.assertTrue((out / "validation.txt").read_text(encoding="utf-8").startswith("PASS\n"))


if __name__ == "__main__":
    unittest.main()
