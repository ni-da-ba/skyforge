from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from guild_branch_detail_resolution import compile_guild_branch_v09
from minecraft_structure import encode_structure_nbt, inspect_export_header
from render import emit_outputs

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.9.json"


class GuildBranchDetailResolutionCompilerTests(unittest.TestCase):
    def spec(self):
        return json.loads(SPEC.read_text(encoding="utf-8"))

    def test_v09_is_deterministic_and_valid(self):
        a = compile_guild_branch_v09(copy.deepcopy(self.spec()))
        b = compile_guild_branch_v09(copy.deepcopy(self.spec()))
        self.assertTrue(a.summary["validation"]["passed"], a.summary["validation"]["issues"])
        self.assertEqual(a.summary["digestSha256"], b.summary["digestSha256"])
        self.assertEqual("0.9", a.summary["compilerVersion"])

    def test_west_secondary_elevation_has_depth_and_complete_reveals(self):
        compiled = compile_guild_branch_v09(self.spec())
        detail = compiled.summary["layout"]["detailResolution"]
        self.assertTrue(detail["secondaryElevation"]["westWindowGroups"])
        self.assertTrue(any(cell.module == "west_projected_pilaster_v09" for cell in compiled.model.cells.values()))
        self.assertTrue(any(cell.module == "west_plinth_band_v09" for cell in compiled.model.cells.values()))
        self.assertTrue(any(cell.module == "west_eave_band_v09" for cell in compiled.model.cells.values()))

        panes = [
            (pos, cell)
            for pos, cell in compiled.model.cells.items()
            if cell.module == "west_window_recess_v09"
        ]
        self.assertTrue(panes)
        for (x, y, z), cell in panes:
            self.assertEqual(1, x)
            self.assertEqual("window", cell.role)
            self.assertIsNone(compiled.model.cells.get((0, y, z)))
        self.assertTrue(any(
            cell.module and cell.module.startswith("west_window_sill_stair_v09_")
            for cell in compiled.model.cells.values()
        ))
        self.assertTrue(any(
            cell.module and cell.module.startswith("west_window_hood_stair_v09_")
            for cell in compiled.model.cells.values()
        ))

    def test_public_lanterns_move_off_structural_wall_cells(self):
        compiled = compile_guild_branch_v09(self.spec())
        rp = compiled.summary["layout"]["resolvedParameters"]
        hall_z1 = 4 + rp["hallDepth"] - 1
        door_x0, door_x1 = rp["publicEntranceSpan"]
        expected_restored_x = {
            max(1, door_x0 - 2),
            min(rp["hallWidth"] - 2, door_x1 + 2),
        }
        for x in expected_restored_x:
            cell = compiled.model.cells.get((x, 5, hall_z1))
            self.assertIsNotNone(cell, (x, 5, hall_z1))
            self.assertEqual("structural_frame", cell.role)

        lights = [
            (pos, cell)
            for pos, cell in compiled.model.cells.items()
            if cell.module == "public_lighting_under_canopy_v09"
        ]
        self.assertEqual(2, len(lights))
        for (x, y, z), cell in lights:
            self.assertEqual("lighting", cell.role)
            self.assertGreater(z, hall_z1)
            self.assertIsNotNone(compiled.model.cells.get((x, y + 1, z)))

    def test_working_portal_is_doors_rail_transom_head(self):
        compiled = compile_guild_branch_v09(self.spec())
        rp = compiled.summary["layout"]["resolvedParameters"]
        wing_x1 = rp["hallWidth"] + rp["workingWingWidth"] - 1
        inner_x = wing_x1 - 1
        for name, a, b, door_module in (
            ("repair", *rp["repairOpeningZ"], "repair_bay_doors_v06"),
            ("freight", *rp["freightOpeningZ"], "freight_bay_doors_v06"),
        ):
            for z in range(a, b + 1):
                for y in (2, 3):
                    door = compiled.model.cells.get((inner_x, y, z))
                    self.assertIsNotNone(door, (name, inner_x, y, z))
                    self.assertEqual("door", door.role)
                    self.assertEqual(door_module, door.module)
                rail = compiled.model.cells.get((inner_x, 4, z))
                self.assertIsNotNone(rail)
                self.assertEqual(f"{name}_transom_rail_v09", rail.module)
                pane = compiled.model.cells.get((inner_x, 5, z))
                self.assertIsNotNone(pane)
                self.assertEqual("window", pane.role)
                self.assertEqual(f"{name}_transom_v09", pane.module)
                self.assertIsNone(compiled.model.cells.get((wing_x1, 4, z)))
                self.assertIsNone(compiled.model.cells.get((wing_x1, 5, z)))
                for x in (inner_x, wing_x1):
                    head = compiled.model.cells.get((x, 6, z))
                    self.assertIsNotNone(head)
                    self.assertEqual(f"{name}_portal_head_v09", head.module)

    def test_corner_smoothing_uses_bounded_stair_vocabulary(self):
        compiled = compile_guild_branch_v09(self.spec())
        stair_modules = {
            "public_plinth_corner_stair_v09",
            "southwest_plinth_corner_v09",
            "public_canopy_corner_stair_v09",
            "working_canopy_corner_stair_v09",
        }
        cells = [cell for cell in compiled.model.cells.values() if cell.module in stair_modules]
        self.assertTrue(cells)
        names = {cell.state.name for cell in cells}
        self.assertTrue(names <= {"minecraft:dark_oak_stairs", "minecraft:stone_brick_stairs"})
        self.assertIn("minecraft:dark_oak_stairs", names)
        self.assertIn("minecraft:stone_brick_stairs", names)

    def test_v09_preserves_articulation_history_and_provisional_palette(self):
        compiled = compile_guild_branch_v09(self.spec())
        layout = compiled.summary["layout"]
        self.assertEqual("architectural_articulation", layout["articulation"]["stage"])
        self.assertEqual(1, len(layout["articulation"]["historyInterventions"]))
        self.assertEqual("provisional", layout["paletteReview"]["status"])
        self.assertEqual("minecraft_detail_resolution", layout["detailResolution"]["stage"])

    def test_v09_exports_minecraft_structure_and_qa(self):
        compiled = compile_guild_branch_v09(self.spec())
        data = encode_structure_nbt(compiled)
        self.assertGreater(inspect_export_header(data)["compressedBytes"], 0)
        with tempfile.TemporaryDirectory() as td:
            out = Path(td)
            emit_outputs(compiled, out)
            for name in ("front.svg", "east.svg", "top.svg", "isometric.svg", "validation.txt"):
                self.assertTrue((out / name).exists(), name)
            self.assertTrue((out / "validation.txt").read_text(encoding="utf-8").startswith("PASS\n"))


if __name__ == "__main__":
    unittest.main()
