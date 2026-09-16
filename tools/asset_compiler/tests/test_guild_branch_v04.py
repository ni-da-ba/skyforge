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
from render import emit_outputs

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.4.json"


class GuildBranchInteriorCompilerTests(unittest.TestCase):
    def spec(self):
        return json.loads(SPEC.read_text(encoding="utf-8"))

    def test_v04_is_deterministic_and_valid(self):
        a = compile_guild_branch_v04(copy.deepcopy(self.spec()))
        b = compile_guild_branch_v04(copy.deepcopy(self.spec()))
        self.assertTrue(a.summary["validation"]["passed"], a.summary["validation"]["issues"])
        self.assertEqual(a.summary["digestSha256"], b.summary["digestSha256"])
        self.assertEqual("0.4", a.summary["compilerVersion"])

    def test_counter_sides_are_corrected(self):
        s = compile_guild_branch_v04(self.spec()).summary
        counter_z = s["layout"]["resolvedParameters"]["counterZ"]
        self.assertGreater(s["layout"]["anchors"]["SERVICE_COUNTER"][2], counter_z)
        self.assertLess(s["layout"]["anchors"]["NPC_WORK_POINT"][2], counter_z)
        back = s["layout"]["volumes"]["backOffice"]
        point = s["layout"]["anchors"]["BACK_OFFICE_WORK"]
        self.assertTrue(all(back["min"][i] <= point[i] <= back["max"][i] for i in range(3)))

    def test_required_interior_modules_exist(self):
        compiled = compile_guild_branch_v04(self.spec())
        modules = {cell.module for cell in compiled.model.cells.values() if cell.module}
        expected = {
            "contract_board",
            "route_info_panel",
            "waiting_bench",
            "clerk_backbar",
            "back_office_desk",
            "records_shelves",
            "freight_stack",
            "repair_workbench",
            "repair_tools",
        }
        self.assertTrue(expected <= modules)

    def test_public_centerline_is_clear(self):
        compiled = compile_guild_branch_v04(self.spec())
        s = compiled.summary
        center = s["layout"]["anchors"]["PUBLIC_ENTRANCE"][0]
        counter_z = s["layout"]["resolvedParameters"]["counterZ"]
        entrance_z = s["layout"]["anchors"]["PUBLIC_ENTRANCE"][2]
        for z in range(counter_z + 1, entrance_z):
            for y in (2, 3):
                cell = compiled.model.cells.get((center, y, z))
                if cell:
                    self.assertIn(cell.module, {"public_entrance", "service_counter"})

    def test_four_bay_interior_sibling_requires_no_compiler_change(self):
        base = compile_guild_branch_v04(copy.deepcopy(self.spec()))
        sibling_spec = self.spec()
        sibling_spec["assetId"] = "test.guild.branch.4bay.interior"
        sibling_spec["structure"]["bayCount"] = 4
        sibling = compile_guild_branch_v04(sibling_spec)
        self.assertTrue(sibling.summary["validation"]["passed"], sibling.summary["validation"]["issues"])
        self.assertGreater(sibling.summary["layout"]["bounds"]["size"][0], base.summary["layout"]["bounds"]["size"][0])
        self.assertNotEqual(sibling.summary["digestSha256"], base.summary["digestSha256"])

    def test_v04_outputs_include_interior_qa(self):
        compiled = compile_guild_branch_v04(self.spec())
        with tempfile.TemporaryDirectory() as td:
            out = Path(td)
            emit_outputs(compiled, out)
            expected = {
                "resolved.json",
                "blocks.json",
                "anchors.json",
                "validation.txt",
                "front.svg",
                "east.svg",
                "top.svg",
                "floorplan.svg",
                "isometric.svg",
                "interior_plan.svg",
                "cutaway_isometric.svg",
                "interior_section.svg",
            }
            self.assertEqual(expected, {p.name for p in out.iterdir()})
            self.assertTrue((out / "validation.txt").read_text(encoding="utf-8").startswith("PASS\n"))


if __name__ == "__main__":
    unittest.main()
