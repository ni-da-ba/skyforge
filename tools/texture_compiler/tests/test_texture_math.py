from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path: sys.path.insert(0, str(ROOT))
from color_math import delta_e_2000_lab
from model import Seam
from optimization import optimize_labels


class TextureMathTests(unittest.TestCase):
    def test_ciede2000_matches_sharma_reference_pair(self):
        got = delta_e_2000_lab((50.0, 2.6772, -79.7751), (50.0, 0.0, -82.7485)); self.assertAlmostEqual(got, 2.0425, places=4)
    def test_hard_seam_is_exact_equivalence_constraint(self):
        samples={("a",0,0):((20,30,40,255),1.0),("b",0,0):((210,190,170,255),1.0)}; seam=Seam("hard",(("a",0,0),),(("b",0,0),),True,1.0)
        labels,info=optimize_labels(samples,((20,30,40,255),(210,190,170,255)),{"a":(1,1),"b":(1,1)},(seam,),1.0,0.01)
        self.assertEqual(labels[("a",0,0)],labels[("b",0,0)]); self.assertEqual(info["seamMismatches"],0); self.assertEqual(info["variableCount"],1)

if __name__ == "__main__": unittest.main()
