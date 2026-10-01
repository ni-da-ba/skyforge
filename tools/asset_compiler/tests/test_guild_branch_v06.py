from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from guild_branch_facade import compile_guild_branch_v06
from minecraft_structure import encode_structure_nbt, inspect_export_header
from render import emit_outputs

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.6.json"


class GuildBranchFacadeCompilerTests(unittest.TestCase):
    def spec(self):
        return json.loads(SPEC.read_text(encoding="utf-8"))

    def test_v06_is_deterministic_and_valid(self):
        a = compile_guild_branch_v06(copy.deepcopy(self.spec()))
        b = compile_guild_branch_v06(copy.deepcopy(self.spec()))
        self.assertTrue(a.summary["validation"]["passed"], a.summary["validation"]["issues"])
        self.assertEqual(a.summary["digestSha256"], b.summary["digestSha256"])
        self.assertEqual("0.6", a.summary["compilerVersion"])

    def test_working_bay_doors_are_contiguous_and_recessed(self):
        compiled = compile_guild_branch_v06(self.spec())
        rp = compiled.summary["layout"]["resolvedParameters"]
        wing_x1 = rp["hallWidth"] + rp["workingWingWidth"] - 1
        inner_x = wing_x1 - 1
        for a, b, module in (
            (*rp["repairOpeningZ"], "repair_bay_doors_v06"),
            (*rp["freightOpeningZ"], "freight_bay_doors_v06"),
        ):
            for z in range(a, b + 1):
                for y in (2, 3):
                    cell = compiled.model.cells.get((inner_x, y, z))
                    self.assertIsNotNone(cell, (inner_x, y, z))
                    self.assertEqual("door", cell.role)
                    self.assertEqual(module, cell.module)
                self.assertIsNone(compiled.model.cells.get((wing_x1, 2, z)))
                self.assertIsNone(compiled.model.cells.get((wing_x1, 3, z)))
                self.assertEqual("window", compiled.model.cells[(wing_x1, 4, z)].role)

    def test_signal_is_low_roof_beacon_not_chain_mast(self):
        compiled = compile_guild_branch_v06(self.spec())
        beacon = [(pos, cell) for pos, cell in compiled.model.cells.items() if cell.module == "signal_beacon_v06"]
        self.assertEqual(2, len(beacon))
        self.assertEqual(1, sum(cell.state.name == "minecraft:polished_andesite_wall" for _pos, cell in beacon))
        self.assertEqual(1, sum(cell.state.name == "minecraft:lantern" for _pos, cell in beacon))
        self.assertFalse(any(cell.module == "signal_mast_v05" for cell in compiled.model.cells.values()))

    def test_public_windows_are_recessed_and_have_projecting_trim(self):
        compiled = compile_guild_branch_v06(self.spec())
        rp = compiled.summary["layout"]["resolvedParameters"]
        hall_z1 = 4 + rp["hallDepth"] - 1
        recessed = [
            (pos, cell)
            for pos, cell in compiled.model.cells.items()
            if cell.module == "public_window_recess_v06"
        ]
        self.assertTrue(recessed)
        self.assertTrue(all(pos[2] == hall_z1 - 1 for pos, _cell in recessed))
        self.assertFalse(any(
            cell.role == "window" and pos[2] == hall_z1 and cell.module == "public_hall"
            for pos, cell in compiled.model.cells.items()
        ))
        self.assertTrue(any(cell.module == "public_window_sill_v06" for cell in compiled.model.cells.values()))
        self.assertTrue(any(cell.module == "public_window_hood_v06" for cell in compiled.model.cells.values()))

    def test_deep_guild_blue_and_continuous_eave_vocabulary(self):
        compiled = compile_guild_branch_v06(self.spec())
        identity = [cell for cell in compiled.model.cells.values() if cell.module == "guild_identity"]
        self.assertTrue(any(cell.state.name == "minecraft:blue_wool" for cell in identity))
        for module in ("public_eave_fascia_v06", "working_eave_fascia_v06"):
            self.assertTrue(any(cell.module == module for cell in compiled.model.cells.values()))

    def test_v06_exports_minecraft_structure_and_interior_qa(self):
        compiled = compile_guild_branch_v06(self.spec())
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
