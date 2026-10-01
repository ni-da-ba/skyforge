from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from guild_branch_interior import compile_guild_branch_v04
from guild_branch_runtime import compile_guild_branch_v05, _roof_y
from minecraft_structure import encode_structure_nbt, inspect_export_header
from render import emit_outputs

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.5.json"


class GuildBranchRuntimeCompilerTests(unittest.TestCase):
    def spec(self):
        return json.loads(SPEC.read_text(encoding="utf-8"))

    def test_v05_is_deterministic_and_valid(self):
        a = compile_guild_branch_v05(copy.deepcopy(self.spec()))
        b = compile_guild_branch_v05(copy.deepcopy(self.spec()))
        self.assertTrue(a.summary["validation"]["passed"], a.summary["validation"]["issues"])
        self.assertEqual(a.summary["digestSha256"], b.summary["digestSha256"])
        self.assertEqual("0.5", a.summary["compilerVersion"])

    def test_roof_projection_is_continuous_full_block_skin(self):
        compiled = compile_guild_branch_v05(self.spec())
        s = compiled.summary
        rp = s["layout"]["resolvedParameters"]
        structure = self.spec()["structure"]
        hall_w = rp["hallWidth"]
        hall_z0 = 4
        hall_z1 = hall_z0 + rp["hallDepth"] - 1
        roof_base = rp["wallHeight"] + 1
        rise = structure["roof"]["mainRise"]
        overhang = structure["roof"]["overhang"]
        for z in range(hall_z0 - overhang, hall_z1 + overhang + 1):
            y = _roof_y(z, hall_z0, hall_z1, rise, roof_base)
            for x in range(0, hall_w):
                cell = compiled.model.cells.get((x, y, z))
                self.assertIsNotNone(cell, (x, y, z))
                self.assertEqual("roof", cell.role)
                self.assertEqual("minecraft:deepslate_tiles", cell.state.name)
        self.assertFalse(any(
            cell.role == "roof" and cell.state.name == "minecraft:deepslate_tile_stairs"
            for cell in compiled.model.cells.values()
        ))

    def test_signal_mast_is_small_and_slender(self):
        compiled = compile_guild_branch_v05(self.spec())
        mast = [
            (pos, cell)
            for pos, cell in compiled.model.cells.items()
            if cell.module == "signal_mast_v05"
        ]
        self.assertEqual(4, len(mast))
        ys = [pos[1] for pos, _cell in mast]
        self.assertEqual(3, max(ys) - min(ys))
        self.assertEqual(3, sum(cell.state.name == "minecraft:chain" for _pos, cell in mast))
        self.assertEqual(1, sum(cell.state.name == "minecraft:lantern" for _pos, cell in mast))

    def test_guild_blue_uses_blue_concrete(self):
        compiled = compile_guild_branch_v05(self.spec())
        identity = [cell for cell in compiled.model.cells.values() if cell.module == "guild_identity"]
        self.assertTrue(identity)
        self.assertTrue(any(cell.state.name == "minecraft:blue_concrete" for cell in identity))
        self.assertTrue(any(cell.module == "repair_header_v05" for cell in compiled.model.cells.values()))

    def test_records_density_is_reduced_from_v04(self):
        v05_spec = self.spec()
        v04_spec = copy.deepcopy(v05_spec)
        v04_spec["schemaVersion"] = "0.4"
        v04_spec["assetId"] = "test.guild.branch.v04.comparison"
        v04 = compile_guild_branch_v04(v04_spec)
        v05 = compile_guild_branch_v05(v05_spec)
        v04_records = sum(
            cell.module in {"clerk_backbar", "records_shelves"}
            for cell in v04.model.cells.values()
        )
        v05_records = sum(
            cell.module in {"records_shelves_v05", "ledger_shelf_v05"}
            for cell in v05.model.cells.values()
        )
        self.assertGreater(v04_records, v05_records)

    def test_v05_exports_minecraft_structure_and_qa(self):
        compiled = compile_guild_branch_v05(self.spec())
        data = encode_structure_nbt(compiled)
        header = inspect_export_header(data)
        self.assertGreater(header["compressedBytes"], 0)
        with tempfile.TemporaryDirectory() as td:
            out = Path(td)
            emit_outputs(compiled, out)
            self.assertTrue((out / "cutaway_isometric.svg").exists())
            self.assertTrue((out / "interior_plan.svg").exists())
            self.assertTrue((out / "validation.txt").read_text(encoding="utf-8").startswith("PASS\n"))


if __name__ == "__main__":
    unittest.main()
