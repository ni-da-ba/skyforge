from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from guild_branch_detail import compile_guild_branch_v03

SPEC = ROOT / "specimens" / "bootstrap_guild_branch_v0.3.json"
SIBLING = ROOT / "specimens" / "bootstrap_guild_branch_4bay_v0.3.json"


class GuildBranchV03Tests(unittest.TestCase):
    def load(self, path=SPEC):
        return json.loads(path.read_text(encoding="utf-8"))

    def test_v03_is_deterministic_and_valid(self):
        spec = self.load()
        a = compile_guild_branch_v03(copy.deepcopy(spec))
        b = compile_guild_branch_v03(copy.deepcopy(spec))
        self.assertTrue(a.summary["validation"]["passed"])
        self.assertEqual(a.summary["digestSha256"], b.summary["digestSha256"])

    def test_visual_grammar_modules_are_present(self):
        s = compile_guild_branch_v03(self.load()).summary
        volumes = s["layout"]["volumes"]
        self.assertIn("publicApproach", volumes)
        self.assertIn("repairCanopy", volumes)
        self.assertIn("freightCanopy", volumes)
        self.assertGreater(s["moduleBlockCounts"].get("guild_identity", 0), 0)
        self.assertGreater(s["moduleBlockCounts"].get("public_canopy", 0), 0)

    def test_airfield_interface_moves_beyond_working_canopies(self):
        s = compile_guild_branch_v03(self.load()).summary
        x, _y, _z = s["layout"]["anchors"]["AIRFIELD_INTERFACE"]
        outer = s["layout"]["resolvedParameters"]["workingCanopyOuterX"]
        self.assertEqual(outer + 1, x)

    def test_four_bay_v03_uses_same_compiler_path(self):
        base = compile_guild_branch_v03(self.load())
        sibling = compile_guild_branch_v03(self.load(SIBLING))
        self.assertTrue(sibling.summary["validation"]["passed"])
        self.assertGreater(sibling.summary["layout"]["bounds"]["size"][0], base.summary["layout"]["bounds"]["size"][0])
        self.assertNotEqual(sibling.summary["digestSha256"], base.summary["digestSha256"])


if __name__ == "__main__":
    unittest.main()
