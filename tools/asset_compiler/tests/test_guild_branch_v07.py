from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from guild_branch_cohesion import compile_guild_branch_v07
from minecraft_structure import encode_structure_nbt, inspect_export_header
from render import emit_outputs

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.7.json"


class GuildBranchCohesionCompilerTests(unittest.TestCase):
    def spec(self):
        return json.loads(SPEC.read_text(encoding="utf-8"))

    def test_v07_is_deterministic_and_valid(self):
        a = compile_guild_branch_v07(copy.deepcopy(self.spec()))
        b = compile_guild_branch_v07(copy.deepcopy(self.spec()))
        self.assertTrue(a.summary["validation"]["passed"], a.summary["validation"]["issues"])
        self.assertEqual(a.summary["digestSha256"], b.summary["digestSha256"])
        self.assertEqual("0.7", a.summary["compilerVersion"])

    def test_public_window_reveals_are_boxed_and_outer_plane_stays_open(self):
        compiled = compile_guild_branch_v07(self.spec())
        rp = compiled.summary["layout"]["resolvedParameters"]
        hall_z1 = 4 + rp["hallDepth"] - 1
        reveal_z = hall_z1 - 1
        panes = [
            (pos, cell)
            for pos, cell in compiled.model.cells.items()
            if cell.module == "public_window_recess_v06" and cell.role == "window"
        ]
        self.assertTrue(panes)
        for (x, y, z), _cell in panes:
            self.assertEqual(reveal_z, z)
            self.assertIsNone(compiled.model.cells.get((x, y, hall_z1)))
            self.assertEqual("foundation", compiled.model.cells[(x, 2, reveal_z)].role)
            self.assertEqual("structural_frame", compiled.model.cells[(x, 5, reveal_z)].role)
        self.assertTrue(any(
            cell.module.startswith("public_window_reveal_v07_")
            for cell in compiled.model.cells.values()
        ))

    def test_working_portals_share_one_recessed_closure_plane(self):
        compiled = compile_guild_branch_v07(self.spec())
        rp = compiled.summary["layout"]["resolvedParameters"]
        wing_x1 = rp["hallWidth"] + rp["workingWingWidth"] - 1
        inner_x = wing_x1 - 1
        for name, a, b, door_module in (
            ("repair", *rp["repairOpeningZ"], "repair_bay_doors_v06"),
            ("freight", *rp["freightOpeningZ"], "freight_bay_doors_v06"),
        ):
            for z in range(a, b + 1):
                for y in (2, 3):
                    cell = compiled.model.cells.get((inner_x, y, z))
                    self.assertIsNotNone(cell, (name, inner_x, y, z))
                    self.assertEqual("door", cell.role)
                    self.assertEqual(door_module, cell.module)
                transom = compiled.model.cells.get((inner_x, 4, z))
                self.assertIsNotNone(transom, (name, inner_x, 4, z))
                self.assertEqual(f"{name}_transom_v07", transom.module)
                for y in (2, 3, 4):
                    self.assertIsNone(compiled.model.cells.get((wing_x1, y, z)))
                for x in (inner_x, wing_x1):
                    self.assertEqual("foundation", compiled.model.cells[(x, 1, z)].role)
                    self.assertEqual("structural_frame", compiled.model.cells[(x, 5, z)].role)

    def test_plinth_bands_tie_wall_modules_without_blocking_crossings(self):
        compiled = compile_guild_branch_v07(self.spec())
        rp = compiled.summary["layout"]["resolvedParameters"]
        hall_z1 = 4 + rp["hallDepth"] - 1
        door_x0, door_x1 = rp["publicEntranceSpan"]
        for x in range(max(0, door_x0 - 1), min(rp["hallWidth"] - 1, door_x1 + 1) + 1):
            cell = compiled.model.cells.get((x, 2, hall_z1 + 1))
            self.assertFalse(cell is not None and cell.module == "public_plinth_band_v07")
        self.assertTrue(any(cell.module == "public_plinth_band_v07" for cell in compiled.model.cells.values()))
        self.assertTrue(any(cell.module == "working_plinth_band_v07" for cell in compiled.model.cells.values()))

    def test_palette_is_explicitly_provisional_and_geometry_pass_does_not_change_it(self):
        compiled = compile_guild_branch_v07(self.spec())
        review = compiled.summary["layout"]["paletteReview"]
        self.assertEqual("provisional", review["status"])
        self.assertEqual("minecraft:blue_wool", review["current"]["institutionalBlue"])
        self.assertEqual("minecraft:yellow_terracotta", review["current"]["brassAccent"])
        self.assertEqual("provisional", compiled.summary["layout"]["resolvedParameters"]["paletteStatus"])

    def test_v07_exports_minecraft_structure_and_full_qa(self):
        compiled = compile_guild_branch_v07(self.spec())
        data = encode_structure_nbt(compiled)
        self.assertGreater(inspect_export_header(data)["compressedBytes"], 0)
        with tempfile.TemporaryDirectory() as td:
            out = Path(td)
            emit_outputs(compiled, out)
            for name in (
                "front.svg",
                "east.svg",
                "isometric.svg",
                "interior_plan.svg",
                "cutaway_isometric.svg",
                "interior_section.svg",
            ):
                self.assertTrue((out / name).exists(), name)
            self.assertTrue((out / "validation.txt").read_text(encoding="utf-8").startswith("PASS\n"))


if __name__ == "__main__":
    unittest.main()
