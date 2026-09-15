from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from guild_branch import compile_guild_branch
from model import SpecError
from render import emit_outputs

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.2.json"


class GuildBranchCompilerTests(unittest.TestCase):
    def spec(self):
        return json.loads(SPEC.read_text(encoding="utf-8"))

    def test_deterministic(self):
        spec = self.spec()
        a = compile_guild_branch(copy.deepcopy(spec))
        b = compile_guild_branch(copy.deepcopy(spec))
        self.assertTrue(a.summary["validation"]["passed"])
        self.assertEqual(a.summary["digestSha256"], b.summary["digestSha256"])

    def test_four_bay_sibling_needs_no_compiler_change(self):
        base_spec = self.spec()
        base = compile_guild_branch(copy.deepcopy(base_spec))
        base_spec["assetId"] = "test.4bay"
        base_spec["structure"]["bayCount"] = 4
        sibling = compile_guild_branch(base_spec)
        self.assertTrue(sibling.summary["validation"]["passed"])
        self.assertGreater(sibling.summary["layout"]["bounds"]["size"][0], base.summary["layout"]["bounds"]["size"][0])
        self.assertNotEqual(sibling.summary["digestSha256"], base.summary["digestSha256"])

    def test_functional_anchors_match_volumes(self):
        s = compile_guild_branch(self.spec()).summary
        for anchor, volume in (("FREIGHT_PICKUP","warehouse"), ("FREIGHT_DROPOFF","warehouse"), ("REPAIR_BAY","lightRepair")):
            p = s["layout"]["anchors"][anchor]
            v = s["layout"]["volumes"][volume]
            self.assertTrue(all(v["min"][i] <= p[i] <= v["max"][i] for i in range(3)))

    def test_unsupported_orientation_fails_closed(self):
        spec = self.spec()
        spec["orientation"]["publicAccess"] = "north"
        with self.assertRaises(SpecError):
            compile_guild_branch(spec)

    def test_outputs_cover_visual_and_semantic_qa(self):
        compiled = compile_guild_branch(self.spec())
        with tempfile.TemporaryDirectory() as td:
            out = Path(td)
            emit_outputs(compiled, out)
            expected = {"resolved.json","blocks.json","anchors.json","validation.txt","front.svg","east.svg","top.svg","floorplan.svg","isometric.svg"}
            self.assertEqual(expected, {p.name for p in out.iterdir()})
            self.assertTrue((out / "validation.txt").read_text().startswith("PASS\n"))


if __name__ == "__main__":
    unittest.main()
