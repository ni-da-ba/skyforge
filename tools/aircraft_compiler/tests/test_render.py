from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path: sys.path.insert(0,str(ROOT))
from solver import solve
from render import emit_outputs

SPEC=ROOT/"specimens"/"guild_utility_monoplane_v0.1.json"


class RenderTests(unittest.TestCase):
    def test_evidence_bundle(self):
        r=solve(json.loads(SPEC.read_text(encoding="utf-8")))
        with tempfile.TemporaryDirectory() as td:
            out=Path(td); emit_outputs(r,out)
            for name in ["resolved.json","validation.txt","top.svg","side.svg","front.svg","mass_balance.svg","lift_check.svg","mobile_review.svg"]:
                self.assertTrue((out/name).exists(),name)
            self.assertIn("PASS",(out/"validation.txt").read_text())
            self.assertIn("<svg",(out/"mobile_review.svg").read_text())


if __name__ == "__main__": unittest.main()
