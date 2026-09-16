from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from guild_branch_continuity import compile_guild_branch_v11
from minecraft_structure import encode_structure_nbt, inspect_export_header
from render import emit_outputs

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.11.json"


class GuildBranchContinuityCompilerTests(unittest.TestCase):
    def spec(self):
        return json.loads(SPEC.read_text(encoding="utf-8"))

    def test_v11_is_deterministic_and_valid(self):
        a = compile_guild_branch_v11(copy.deepcopy(self.spec()))
        b = compile_guild_branch_v11(copy.deepcopy(self.spec()))
        self.assertTrue(a.summary["validation"]["passed"], a.summary["validation"]["issues"])
        self.assertEqual(a.summary["digestSha256"], b.summary["digestSha256"])
        self.assertEqual("0.11", a.summary["compilerVersion"])

    def test_windows_are_complete_and_furnishings_do_not_occupy_glazing(self):
        compiled = compile_guild_branch_v11(self.spec())
        c = compiled.summary["layout"]["continuityResolution"]["windowsAndWalls"]
        self.assertTrue(c["southWindowGroups"])
        self.assertTrue(c["northWindowGroups"])
        self.assertTrue(c["westWindowGroups"])
        for module in ("south_window_pane_v11", "north_window_pane_v11", "west_window_pane_v11"):
            panes = [(pos, cell) for pos, cell in compiled.model.cells.items() if cell.module == module]
            self.assertTrue(panes, module)
            self.assertTrue(all(cell.role == "window" for _pos, cell in panes))
        forbidden = {"contract_board_frame_v10", "route_info_frame_v10", "records_wall_v10", "clerk_records_v10"}
        self.assertFalse(any(cell.module in forbidden for cell in compiled.model.cells.values()))
        self.assertTrue(any(cell.module == "contract_board_wall_v11" for cell in compiled.model.cells.values()))
        self.assertTrue(any(cell.module == "route_info_wall_v11" for cell in compiled.model.cells.values()))
        self.assertTrue(any(cell.module == "records_wall_v11" for cell in compiled.model.cells.values()))

    def test_structure_connects_bay_posts_to_roof_frame(self):
        compiled = compile_guild_branch_v11(self.spec())
        rp = compiled.summary["layout"]["resolvedParameters"]
        hall_w = rp["hallWidth"]
        bay_w = rp["bayWidth"]
        hall_z0 = 4
        hall_z1 = hall_z0 + rp["hallDepth"] - 1
        wall_h = rp["wallHeight"]
        post_xs = sorted(set(range(0, hall_w, bay_w)) | {hall_w - 1})
        for x in post_xs:
            for z in (hall_z0, hall_z1):
                for y in range(2, wall_h):
                    cell = compiled.model.cells.get((x, y, z))
                    self.assertIsNotNone(cell, (x, y, z))
                    self.assertEqual("continuous_bay_post_v11", cell.module)
        self.assertTrue(any(cell.module == "public_roof_rafter_v11" for cell in compiled.model.cells.values()))
        self.assertTrue(any(cell.module == "public_roof_tie_beam_v11" for cell in compiled.model.cells.values()))

    def test_public_spine_is_full_double_door_width_and_working_lanes_are_open(self):
        compiled = compile_guild_branch_v11(self.spec())
        rp = compiled.summary["layout"]["resolvedParameters"]
        x0, x1 = rp["publicEntranceSpan"]
        hall_z1 = 4 + rp["hallDepth"] - 1
        for x in range(x0, x1 + 1):
            for z in range(rp["counterZ"] + 1, hall_z1):
                for y in (2, 3):
                    cell = compiled.model.cells.get((x, y, z))
                    if cell is not None:
                        self.assertIn(cell.role, {"door"}, (x, y, z, cell.module))
        continuity = compiled.summary["layout"]["continuityResolution"]["structureAndSpace"]
        self.assertEqual(x1 - x0 + 1, continuity["publicCirculationWidth"])
        for x, y, z in continuity["workingLaneCells"]:
            cell = compiled.model.cells.get((x, y, z))
            if cell is not None:
                self.assertEqual("door", cell.role, (x, y, z, cell.module))

    def test_roof_keeps_solid_skin_and_adds_exterior_and_interior_detail(self):
        compiled = compile_guild_branch_v11(self.spec())
        required = {
            "public_roof_eave_north_v11", "public_roof_eave_south_v11", "public_roof_ridge_cap_v11",
            "public_roof_rafter_v11", "public_roof_tie_beam_v11", "working_roof_eave_north_v11",
            "working_roof_eave_south_v11", "working_roof_ridge_cap_v11", "working_roof_rafter_v11",
        }
        present = {cell.module for cell in compiled.model.cells.values() if cell.module}
        self.assertTrue(required <= present)
        eaves = [cell for cell in compiled.model.cells.values() if cell.module in {
            "public_roof_eave_north_v11", "public_roof_eave_south_v11",
            "working_roof_eave_north_v11", "working_roof_eave_south_v11",
        }]
        self.assertTrue(eaves)
        self.assertTrue(all(cell.state.name == "minecraft:deepslate_tile_stairs" for cell in eaves))
        ridge = [cell for cell in compiled.model.cells.values() if cell.module and "roof_ridge_cap_v11" in cell.module]
        self.assertTrue(ridge)
        self.assertTrue(all(cell.state.name == "minecraft:deepslate_tile_slab" for cell in ridge))

    def test_v11_exports_minecraft_structure_and_full_interior_qa(self):
        compiled = compile_guild_branch_v11(self.spec())
        data = encode_structure_nbt(compiled)
        self.assertGreater(inspect_export_header(data)["compressedBytes"], 0)
        with tempfile.TemporaryDirectory() as td:
            out = Path(td)
            emit_outputs(compiled, out)
            for name in (
                "front.svg", "east.svg", "top.svg", "isometric.svg",
                "interior_plan.svg", "cutaway_isometric.svg", "interior_section.svg", "validation.txt",
            ):
                self.assertTrue((out / name).exists(), name)
            self.assertTrue((out / "validation.txt").read_text(encoding="utf-8").startswith("PASS\n"))

    def test_v11_preserves_history_and_provisional_palette(self):
        compiled = compile_guild_branch_v11(self.spec())
        layout = compiled.summary["layout"]
        self.assertEqual(1, len(layout["articulation"]["historyInterventions"]))
        self.assertEqual("provisional", layout["paletteReview"]["status"])
        self.assertEqual("structure_space_roof_continuity", layout["continuityResolution"]["stage"])


if __name__ == "__main__":
    unittest.main()
