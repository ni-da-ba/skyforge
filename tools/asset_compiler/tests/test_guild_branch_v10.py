from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from guild_branch_finish import compile_guild_branch_v10
from minecraft_structure import encode_structure_nbt, inspect_export_header
from render import emit_outputs

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.10.json"


class GuildBranchFinishCompilerTests(unittest.TestCase):
    def spec(self):
        return json.loads(SPEC.read_text(encoding="utf-8"))

    def test_v10_is_deterministic_and_valid(self):
        a = compile_guild_branch_v10(copy.deepcopy(self.spec()))
        b = compile_guild_branch_v10(copy.deepcopy(self.spec()))
        self.assertTrue(a.summary["validation"]["passed"], a.summary["validation"]["issues"])
        self.assertEqual(a.summary["digestSha256"], b.summary["digestSha256"])
        self.assertEqual("0.10", a.summary["compilerVersion"])

    def test_public_and_working_doors_have_explicit_heads(self):
        compiled = compile_guild_branch_v10(self.spec())
        rp = compiled.summary["layout"]["resolvedParameters"]
        hall_z1 = 4 + rp["hallDepth"] - 1
        door_x0, door_x1 = rp["publicEntranceSpan"]
        for x in range(door_x0, door_x1 + 1):
            for y in (2, 3):
                door = compiled.model.cells.get((x, y, hall_z1))
                self.assertIsNotNone(door)
                self.assertEqual("door", door.role)
            header = compiled.model.cells.get((x, 4, hall_z1))
            self.assertIsNotNone(header)
            self.assertEqual("public_door_header_v10", header.module)

        wing_x1 = rp["hallWidth"] + rp["workingWingWidth"] - 1
        inner_x = wing_x1 - 1
        for name, a, b in (
            ("repair", *rp["repairOpeningZ"]),
            ("freight", *rp["freightOpeningZ"]),
        ):
            for z in range(a, b + 1):
                for y in (2, 3):
                    self.assertEqual("door", compiled.model.cells[(inner_x, y, z)].role)
                for x in (inner_x, wing_x1):
                    self.assertEqual(f"{name}_door_header_v10", compiled.model.cells[(x, 4, z)].module)
                self.assertEqual(f"{name}_transom_v10", compiled.model.cells[(inner_x, 5, z)].module)
                self.assertIsNone(compiled.model.cells.get((wing_x1, 5, z)))

    def test_north_rear_elevation_is_articulated(self):
        compiled = compile_guild_branch_v10(self.spec())
        layout = compiled.summary["layout"]
        finish = layout["finishResolution"]
        groups = finish["rearElevation"]["northWindowGroups"]
        self.assertTrue(groups)
        rp = layout["resolvedParameters"]
        hall_z0 = 4
        for x0, x1 in groups:
            for x in range(x0, x1 + 1):
                for y in (3, 4):
                    pane = compiled.model.cells.get((x, y, hall_z0 + 1))
                    self.assertIsNotNone(pane)
                    self.assertEqual("window", pane.role)
                    self.assertIsNone(compiled.model.cells.get((x, y, hall_z0)))
        for module in ("north_plinth_band_v10", "north_eave_band_v10", "north_projected_pilaster_v10"):
            self.assertTrue(any(cell.module == module for cell in compiled.model.cells.values()), module)

    def test_ambiguous_v09_detail_stairs_are_removed(self):
        compiled = compile_guild_branch_v10(self.spec())
        finish = compiled.summary["layout"]["finishResolution"]
        self.assertTrue(finish["stairAudit"]["replacedPositions"])
        for cell in compiled.model.cells.values():
            module = cell.module or ""
            self.assertNotIn(module, {
                "public_plinth_corner_stair_v09",
                "southwest_plinth_corner_v09",
                "public_canopy_corner_stair_v09",
                "working_canopy_corner_stair_v09",
            })
            self.assertNotIn("west_window_sill_stair_v09_", module)
            self.assertNotIn("west_window_hood_stair_v09_", module)

    def test_interior_finish_adds_coherent_modules_and_preserves_aisle(self):
        compiled = compile_guild_branch_v10(self.spec())
        layout = compiled.summary["layout"]
        present = {cell.module for cell in compiled.model.cells.values() if cell.module}
        for module in (
            "service_counter_finish_v10",
            "interior_ceiling_beam_v10",
            "interior_lighting_v10",
            "public_waiting_bench_v10",
            "records_wall_v10",
            "back_office_desk_v10",
            "freight_rack_v10",
            "repair_bench_v10",
        ):
            self.assertIn(module, present)

        rp = layout["resolvedParameters"]
        public_center = sum(rp["publicEntranceSpan"]) // 2
        hall_z1 = 4 + rp["hallDepth"] - 1
        for z in range(rp["counterZ"] + 1, hall_z1):
            for y in (2, 3):
                cell = compiled.model.cells.get((public_center, y, z))
                if cell:
                    self.assertIn(cell.module, {"public_entrance", "service_counter_finish_v10"})

    def test_v10_preserves_history_and_provisional_palette(self):
        compiled = compile_guild_branch_v10(self.spec())
        layout = compiled.summary["layout"]
        self.assertEqual(1, len(layout["articulation"]["historyInterventions"]))
        self.assertEqual("provisional", layout["paletteReview"]["status"])
        self.assertEqual("architectural_finish_resolution", layout["finishResolution"]["stage"])

    def test_v10_exports_minecraft_structure_and_qa(self):
        compiled = compile_guild_branch_v10(self.spec())
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
